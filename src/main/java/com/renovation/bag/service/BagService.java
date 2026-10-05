package com.renovation.bag.service;

import com.renovation.bag.api.dto.BagDto;
import com.renovation.bag.api.dto.ScanResultDto;
import com.renovation.bag.domain.*;
import com.renovation.bag.repo.BagRepository;
import com.renovation.bag.repo.OwnerRepository;
import com.renovation.bag.support.AppClock;
import com.renovation.bag.support.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BagService {

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Inject BagRepository bags;
    @Inject OwnerRepository owners;
    @Inject ScanLogService scanLogs;
    @Inject AppClock clock;

    /** 物业登记发袋：可为指定袋号列表，或按数量自动生成袋号 */
    @Transactional
    public List<BagDto> issue(String ownerCode, List<String> bagCodes, Integer count, String prefix) {
        Owner owner = owners.findByCode(ownerCode);
        if (owner == null) {
            throw new BusinessException("业主不存在: " + ownerCode);
        }
        List<String> codes = new ArrayList<>();
        if (bagCodes != null && !bagCodes.isEmpty()) {
            codes.addAll(bagCodes);
        } else if (count != null && count > 0) {
            String pfx = (prefix != null && !prefix.isBlank()) ? prefix : "B" + ownerCode + "-";
            // 从该前缀当前最大序号续号
            long maxSeq = bags.list("select b from BagRecord b where b.bagCode like ?1", pfx + "%")
                    .stream()
                    .map(b -> {
                        try {
                            return Long.parseLong(b.getBagCode().substring(pfx.length()));
                        } catch (NumberFormatException e) {
                            return 0L;
                        }
                    }).max(Long::compareTo).orElse(0L);
            for (int i = 1; i <= count; i++) {
                codes.add(String.format("%s%04d", pfx, maxSeq + i));
            }
        } else {
            throw new BusinessException("请提供袋号列表或发放数量");
        }

        String batch = "IS-" + BATCH_FMT.format(clock.now().atOffset(ZoneOffset.UTC));
        Instant now = clock.now();
        List<BagDto> result = new ArrayList<>();
        for (String code : codes) {
            if (bags.findByCode(code) != null) {
                throw new BusinessException("袋号已存在，不能重复发放: " + code);
            }
            BagRecord b = new BagRecord(code.trim(), owner, batch, now);
            bags.persist(b);
            result.add(BagDto.of(b));
        }
        return result;
    }

    /** 未使用的袋子退回。只有 ISSUED 可退；退回后永不产生清运费 */
    @Transactional
    public List<BagDto> returnBags(List<String> bagCodes) {
        List<BagDto> result = new ArrayList<>();
        for (String raw : bagCodes) {
            String code = raw.trim();
            BagRecord b = bags.findByCode(code);
            if (b == null) {
                throw new BusinessException("袋号不存在: " + code);
            }
            if (b.getStatus() == BagStatus.RETURNED) {
                result.add(BagDto.of(b)); // 幂等：重复退袋不报错
                continue;
            }
            if (b.getStatus() != BagStatus.ISSUED) {
                throw new BusinessException("袋号 " + code
                        + " 当前状态为 " + b.getStatus() + "，已计费，不能退回");
            }
            b.setStatus(BagStatus.RETURNED);
            b.setReturnedAt(clock.now());
            result.add(BagDto.of(b));
        }
        return result;
    }

    /**
     * 清运员扫码。关键幂等规则：
     * 同一袋号重复扫码只登记一次有效清运（ACCEPTED），
     * 之后的扫码回执为 DUPLICATE，不产生第二次费用。
     * 清运月份以实际扫码时刻为准（跨月沿用原袋号，计入实际清运月）。
     */
    @Transactional
    public ScanResultDto scan(String bagCode, String collector) {
        Instant now = clock.now();
        BagRecord b = bags.findByCode(bagCode.trim());
        if (b == null) {
            scanLogs.log(bagCode, collector, now, ScanLog.REJECTED, "袋号不存在");
            throw new BusinessException("袋号不存在: " + bagCode);
        }
        switch (b.getStatus()) {
            case COLLECTED: {
                String detail = "重复扫码：该袋已于 " + b.getCollectedAt()
                        + " 由 " + nz(b.getCollector()) + " 清运，不重复收费";
                scanLogs.log(bagCode, collector, now, ScanLog.DUPLICATE, detail);
                return new ScanResultDto(ScanLog.DUPLICATE, BagDto.of(b), detail, false);
            }
            case LOST: {
                String detail = "该袋已按约定遗失计费（一次），现场补扫不重复收费";
                scanLogs.log(bagCode, collector, now, ScanLog.DUPLICATE, detail);
                return new ScanResultDto(ScanLog.DUPLICATE, BagDto.of(b), detail, false);
            }
            case RETURNED: {
                String detail = "该袋已作未使用退回，不得清运收费";
                scanLogs.log(bagCode, collector, now, ScanLog.REJECTED, detail);
                return new ScanResultDto(ScanLog.REJECTED, BagDto.of(b), detail, false);
            }
            case ISSUED:
            default: {
                b.setStatus(BagStatus.COLLECTED);
                b.setCollectedAt(now);
                b.setCollectedMonth(AppClock.monthOf(now));
                b.setCollector(collector);
                String detail = "清运成功，袋号 " + bagCode + " 计入 " + b.getCollectedMonth() + " 月账单（一次）";
                scanLogs.log(bagCode, collector, now, ScanLog.ACCEPTED, detail);
                return new ScanResultDto(ScanLog.ACCEPTED, BagDto.of(b), detail, true);
            }
        }
    }

    /** 双方约定遗失：视同清运计一次费，可指定归属月份 */
    @Transactional
    public BagDto markLost(String bagCode, String month, String reason) {
        BagRecord b = bags.findByCode(bagCode.trim());
        if (b == null) {
            throw new BusinessException("袋号不存在: " + bagCode);
        }
        if (b.getStatus() != BagStatus.ISSUED) {
            throw new BusinessException("仅已发放未处理的袋子可约定遗失，当前状态: " + b.getStatus());
        }
        Instant now = clock.now();
        b.setStatus(BagStatus.LOST);
        b.setMarkedLostAt(now);
        b.setLostMonth(month != null ? month : clock.currentMonth());
        b.setNote(appendNote(b.getNote(), reason));
        return BagDto.of(b);
    }

    /** 袋子破损：沿用原袋号，仅追加备注，绝不另建记录 */
    @Transactional
    public BagDto note(String bagCode, String note) {
        BagRecord b = bags.findByCode(bagCode.trim());
        if (b == null) {
            throw new BusinessException("袋号不存在: " + bagCode);
        }
        b.setNote(appendNote(b.getNote(), note));
        return BagDto.of(b);
    }

    @Transactional
    public List<BagDto> listByOwner(String ownerCode) {
        Owner owner = owners.findByCode(ownerCode);
        if (owner == null) {
            throw new BusinessException("业主不存在: " + ownerCode);
        }
        return bags.findByOwner(owner.getId()).stream().map(BagDto::of).toList();
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    private static String appendNote(String oldNote, String add) {
        if (add == null || add.isBlank()) {
            return oldNote;
        }
        return oldNote == null || oldNote.isBlank() ? add : oldNote + "；" + add;
    }
}
