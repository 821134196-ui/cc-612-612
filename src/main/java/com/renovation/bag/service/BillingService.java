package com.renovation.bag.service;

import com.renovation.bag.api.dto.*;
import com.renovation.bag.domain.*;
import com.renovation.bag.repo.*;
import com.renovation.bag.support.AppClock;
import com.renovation.bag.support.BusinessException;
import com.renovation.bag.support.Pricing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 计费核心。收费口径只有两种计费事件：
 *   COLLECTED（扫码清运）与 LOST（约定遗失），每袋至多一次。
 * 仅发放（ISSUED）不收费，退袋（RETURNED）永不收费。
 */
@ApplicationScoped
public class BillingService {

    @Inject BillRepository bills;
    @Inject BillItemRepository items;
    @Inject AdjustmentLineRepository adjustments;
    @Inject BagRepository bags;
    @Inject OwnerRepository owners;
    @Inject Pricing pricing;
    @Inject AppClock clock;

    /**
     * 取（或重算）业主某月正式账单。
     * OPEN 账单每次按袋记录实时重算；SETTLED 账单冻结，原样返回。
     */
    @Transactional
    public BillDetailDto monthlyBill(String ownerCode, String month) {
        Owner owner = requireOwner(ownerCode);
        String m = month != null ? month : clock.currentMonth();
        Bill bill = bills.findRegular(owner.getId(), m);
        if (bill == null) {
            bill = new Bill();
            bill.setBillNo("REG-" + m.replace("-", "") + "-" + owner.getCode());
            bill.setOwner(owner);
            bill.setMonth(m);
            bill.setBillType(Bill.Type.REGULAR);
            bill.setStatus(Bill.Status.OPEN);
            bill.setCreatedAt(clock.now().toEpochMilli());
            bills.persist(bill);
        }
        if (bill.getStatus() == Bill.Status.OPEN) {
            rebuildRegularBill(bill, owner, m);
        }
        return detail(bill);
    }

    /**
     * 按实际计费事件重建月账单：
     * collectedMonth/lostMonth 落在本月且状态为 COLLECTED/LOST 的袋子各计一次。
     * 跨月清运的袋子计入"实际清运月"，袋号沿用不变，发放月不收费。
     */
    private void rebuildRegularBill(Bill bill, Owner owner, String month) {
        for (BillItem old : items.findByBill(bill.getId())) {
            items.delete(old);
        }
        items.flush();
        long total = 0;
        for (BagRecord b : bags.findByOwner(owner.getId())) {
            if (b.getStatus() == BagStatus.COLLECTED && month.equals(b.getCollectedMonth())) {
                items.persist(new BillItem(bill, b.getBagCode(), BillItem.COLLECTED,
                        pricing.perBagFen(), b.getCollectedAt().toEpochMilli(), b.getCollectedMonth()));
                total += pricing.perBagFen();
            } else if (b.getStatus() == BagStatus.LOST && month.equals(b.getLostMonth())) {
                items.persist(new BillItem(bill, b.getBagCode(), BillItem.LOST,
                        pricing.perBagFen(), b.getMarkedLostAt().toEpochMilli(), b.getLostMonth()));
                total += pricing.perBagFen();
            }
        }
        bill.setAmountFen(total);
    }

    /**
     * 已结算月份发现差错 -> 生成/追加补差单，原账单保持不变。
     * MISSED_COLLECTION 漏收补收 +fee；WRONG_COLLECTION 错收退费 -fee。
     * 同一原账单、同一袋号、同一更正类型只允许一次（幂等，防重复收费）。
     */
    @Transactional
    public BillDetailDto adjust(String ownerCode, String month, List<AdjustmentRequest.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("请提供补差明细");
        }
        Owner owner = requireOwner(ownerCode);
        String m = month != null ? month : clock.currentMonth();

        Bill original = bills.findRegular(owner.getId(), m);
        if (original == null || original.getStatus() != Bill.Status.SETTLED) {
            throw new BusinessException(m + " 月账单尚未结算；未结算月份的差错直接重算当月账单即可，无需补差单");
        }

        Bill adj = bills.find("owner.id = ?1 and month = ?2 and billType = ?3",
                owner.getId(), m, Bill.Type.ADJUSTMENT).firstResult();
        if (adj == null) {
            adj = new Bill();
            adj.setBillNo("ADJ-" + m.replace("-", "") + "-" + owner.getCode());
            adj.setOwner(owner);
            adj.setMonth(m);
            adj.setBillType(Bill.Type.ADJUSTMENT);
            adj.setStatus(Bill.Status.OPEN);
            adj.setOriginalBill(original);
            adj.setCreatedAt(clock.now().toEpochMilli());
            bills.persist(adj);
        } else if (adj.getStatus() == Bill.Status.SETTLED) {
            throw new BusinessException(m + " 月补差单已结算，不能再追加更正");
        }

        for (AdjustmentRequest.Line line : lines) {
            String code = line.bagCode.trim();
            BagRecord bag = bags.findByCode(code);
            if (bag == null || !bag.getOwner().getId().equals(owner.getId())) {
                throw new BusinessException("袋号不存在或不属于该业主: " + code);
            }
            if (adjustments.exists(original.getId(), code, line.correctionType)) {
                // 重复提交同一更正：幂等跳过，绝不重复收费/退费
                continue;
            }
            long delta;
            switch (line.correctionType) {
                case AdjustmentLine.MISSED_COLLECTION -> {
                    if (!items.findByBillAndBag(original, code).isEmpty()) {
                        throw new BusinessException("袋号 " + code + " 已在原账单中收费，不能补收");
                    }
                    if (bag.getStatus() == BagStatus.RETURNED) {
                        throw new BusinessException("袋号 " + code + " 已退回，从不计费，不能补收");
                    }
                    if (bag.getStatus() == BagStatus.COLLECTED
                            && !m.equals(bag.getCollectedMonth())) {
                        throw new BusinessException("袋号 " + code + " 实际清运月为 "
                                + bag.getCollectedMonth() + "，应在该月账单核对（跨月沿用袋号）");
                    }
                    if (bag.getStatus() == BagStatus.LOST
                            && !m.equals(bag.getLostMonth())) {
                        throw new BusinessException("袋号 " + code + " 约定遗失月为 "
                                + bag.getLostMonth() + "，应在该月账单核对");
                    }
                    // 从未扫码但当月实际清运：沿用原袋号补登，计入被修正月份
                    if (bag.getStatus() == BagStatus.ISSUED) {
                        bag.setStatus(BagStatus.COLLECTED);
                        bag.setCollectedAt(clock.now());
                        bag.setCollectedMonth(m);
                        bag.setCollector("ADJUSTMENT");
                    }
                    delta = pricing.perBagFen();
                }
                case AdjustmentLine.WRONG_COLLECTION -> {
                    if (items.findByBillAndBag(original, code).isEmpty()) {
                        throw new BusinessException("袋号 " + code + " 不在原账单中，无费可退");
                    }
                    // 撤销错误的计费状态，保留原账单快照；袋子之后可正常清运并在当月计一次费
                    bag.setStatus(BagStatus.ISSUED);
                    bag.setCollectedAt(null);
                    bag.setCollectedMonth(null);
                    bag.setCollector(null);
                    bag.setMarkedLostAt(null);
                    bag.setLostMonth(null);
                    delta = -pricing.perBagFen();
                }
                default -> throw new BusinessException("未知更正类型: " + line.correctionType);
            }
            adjustments.persist(new AdjustmentLine(adj, original, code,
                    line.correctionType, delta, line.reason, clock.now().toEpochMilli()));
        }
        adjustments.flush();
        long total = adjustments.list("bill.id", adj.getId()).stream()
                .mapToLong(AdjustmentLine::getDeltaFen).sum();
        adj.setAmountFen(total);
        adj.setReason("针对原账单 " + original.getBillNo() + " 的差错更正");
        return detail(adj);
    }

    @Transactional
    public BillDetailDto settle(Long billId, String paidVia) {
        Bill bill = bills.findById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在: " + billId);
        }
        if (bill.getStatus() == Bill.Status.SETTLED) {
            // 重复支付：幂等返回，不重复扣款
            return detail(bill);
        }
        String ref = MockGateway.charge(bill.getAmountFen(), paidVia);
        bill.setStatus(Bill.Status.SETTLED);
        bill.setSettledAt(clock.now().toEpochMilli());
        bill.setPaymentRef(ref);
        return detail(bill);
    }

    @Transactional
    public List<BillDto> listBills(String ownerCode) {
        Owner owner = requireOwner(ownerCode);
        List<BillDto> out = new ArrayList<>();
        for (Bill b : bills.findByOwner(owner.getId())) {
            out.add(BillDto.of(b));
        }
        return out;
    }

    @Transactional
    public BillDetailDto detailById(Long id) {
        Bill bill = bills.findById(id);
        if (bill == null) {
            throw new BusinessException("账单不存在: " + id);
        }
        return detail(bill);
    }

    private BillDetailDto detail(Bill bill) {
        BillDetailDto d = new BillDetailDto();
        d.bill = BillDto.of(bill);
        d.chargedItems = items.findByBill(bill.getId()).stream()
                .map(BillItemDto::of).toList();
        if (bill.getBillType() == Bill.Type.ADJUSTMENT) {
            d.adjustmentLines = adjustments.list("bill.id", bill.getId()).stream()
                    .map(AdjustmentLineDto::of).toList();
        } else {
            d.adjustmentLines = List.of();
        }
        List<BagRecord> ownerBags = bags.findByOwner(bill.getOwner().getId());
        d.bags = ownerBags.stream().map(BagDto::of).toList();
        d.issuedCount = ownerBags.size();
        d.returnedCount = ownerBags.stream().filter(b -> b.getStatus() == BagStatus.RETURNED).count();
        d.collectedInMonth = ownerBags.stream()
                .filter(b -> b.getStatus() == BagStatus.COLLECTED && bill.getMonth().equals(b.getCollectedMonth()))
                .count();
        d.lostInMonth = ownerBags.stream()
                .filter(b -> b.getStatus() == BagStatus.LOST && bill.getMonth().equals(b.getLostMonth()))
                .count();
        d.chargedCount = (long) d.chargedItems.size();
        d.chargeTotalFen = d.chargedItems.stream().mapToLong(i -> i.amountFen).sum();
        d.unitPriceFen = pricing.perBagFen();
        d.currency = pricing.currency;
        return d;
    }

    private Owner requireOwner(String code) {
        Owner owner = owners.findByCode(code);
        if (owner == null) {
            throw new BusinessException("业主不存在: " + code);
        }
        return owner;
    }
}
