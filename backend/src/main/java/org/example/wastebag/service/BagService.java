package org.example.wastebag.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.wastebag.model.Bag;
import org.example.wastebag.model.BagEvent;
import org.example.wastebag.model.BagStatus;
import org.example.wastebag.model.Bill;
import org.example.wastebag.model.BillStatus;
import org.example.wastebag.model.Counter;
import org.example.wastebag.model.EventType;
import org.example.wastebag.model.Owner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 袋号生命周期：发放 -> （退回 | 扫码清运 | 报失），中途可登记破损。
 * 核心约束：
 *  - 发放、退回、破损都不产生任何费用；
 *  - 扫码清运幂等：同一袋号只登记一次有效清运，重复扫码返回原回执；
 *  - 破损、跨月清运都沿用原袋号，不新建袋记录。
 */
@ApplicationScoped
public class BagService {

    @Inject
    FeeConfig fees;

    @Inject
    BillingService billingService;

    // ---------- 发放 ----------

    public record IssuedBag(String bagNo) {}

    @Transactional
    public List<IssuedBag> issue(long ownerId, int count, LocalDateTime at) {
        if (count < 1 || count > 500) {
            throw new BadRequestException("发袋数量须在 1~500 之间");
        }
        Owner owner = requireOwner(ownerId);
        LocalDateTime now = at != null ? at : LocalDateTime.now();
        Counter counter = Counter.byName("bag");
        if (counter == null) {
            counter = new Counter();
            counter.name = "bag";
            counter.value = 0;
            counter.persist();
        }
        List<IssuedBag> issued = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            counter.value++;
            String bagNo = String.format("B%06d", counter.value);
            Bag bag = new Bag();
            bag.bagNo = bagNo;
            bag.owner = owner;
            bag.status = BagStatus.ISSUED;
            bag.issuedAt = now;
            bag.persist();
            record(bagNo, owner, EventType.ISSUE, now, "物业", null);
            issued.add(new IssuedBag(bagNo));
        }
        return issued;
    }

    // ---------- 退回（不产生任何费用） ----------

    @Transactional
    public Bag returnBag(String bagNo, LocalDateTime at) {
        Bag bag = requireBag(bagNo);
        if (bag.status != BagStatus.ISSUED) {
            throw new BadRequestException("只有未使用的袋子才能退回，当前状态: " + bag.status);
        }
        LocalDateTime now = at != null ? at : LocalDateTime.now();
        bag.status = BagStatus.RETURNED;
        bag.returnedAt = now;
        record(bagNo, bag.owner, EventType.RETURN, now, "物业", null);
        return bag;
    }

    // ---------- 报失（按约定赔付，费用在出账时计入） ----------

    @Transactional
    public Bag markLost(String bagNo, LocalDateTime at) {
        Bag bag = requireBag(bagNo);
        if (bag.status != BagStatus.ISSUED) {
            throw new BadRequestException("只有已发放未处理的袋子才能报失，当前状态: " + bag.status);
        }
        LocalDateTime now = at != null ? at : LocalDateTime.now();
        bag.status = BagStatus.LOST;
        record(bagNo, bag.owner, EventType.LOST, now, "物业", null);
        return bag;
    }

    // ---------- 破损登记（沿用原袋号，不产生费用、不改变计费） ----------

    @Transactional
    public Bag markDamaged(String bagNo, String note, LocalDateTime at) {
        Bag bag = requireBag(bagNo);
        if (bag.status != BagStatus.ISSUED) {
            throw new BadRequestException("只有已发放未处理的袋子才能登记破损，当前状态: " + bag.status);
        }
        LocalDateTime now = at != null ? at : LocalDateTime.now();
        bag.damaged = true;
        record(bagNo, bag.owner, EventType.DAMAGE, now, "物业", note);
        return bag;
    }

    // ---------- 扫码清运（幂等） ----------

    public record ScanResult(
            String result,      // CLEARED | DUPLICATE | REJECTED
            String bagNo,
            BigDecimal fee,     // 本次有效清运的费用；重复扫码时为首次费用
            LocalDateTime clearedAt,
            String month,       // 费用归属账期
            String routedTo,    // PENDING_BILL | ADJUSTMENT:<id> | NONE
            String message) {}

    @Transactional
    public ScanResult scan(String bagNo, String operator, LocalDateTime at) {
        Bag bag = Bag.findByBagNo(bagNo);
        if (bag == null) {
            return new ScanResult("REJECTED", bagNo, null, null, null, "NONE", "袋号不存在，未登记");
        }
        // 幂等：同一袋号只认第一次有效清运
        BagEvent existing = BagEvent.findClearEvent(bagNo);
        if (existing != null) {
            return new ScanResult("DUPLICATE", bagNo, fees.clearance(), existing.occurredAt,
                    existing.month, "NONE", "该袋已于 " + existing.occurredAt + " 清运，重复扫码不再计费");
        }
        if (bag.status == BagStatus.RETURNED) {
            return new ScanResult("REJECTED", bagNo, null, null, null, "NONE", "该袋已退回物业，不能清运");
        }
        if (bag.status == BagStatus.LOST) {
            return new ScanResult("REJECTED", bagNo, null, null, null, "NONE", "该袋已报失，不能清运");
        }
        LocalDateTime now = at != null ? at : LocalDateTime.now();
        bag.status = BagStatus.CLEARED;
        bag.clearedAt = now;
        // 跨月清运：事件仍用原袋号，费用落在清运当月账期
        BagEvent event = record(bagNo, bag.owner, EventType.CLEAR, now,
                operator != null ? operator : "清运员", null);

        // 若该账期账单已结算，立即生成补差单，避免改动原账单
        Bill bill = Bill.findByOwnerAndMonth(bag.owner.id, event.month);
        if (bill != null && bill.status != BillStatus.OPEN) {
            var adj = billingService.createAdjustmentForEvents(bill, "已结算月份补录清运", List.of(event));
            return new ScanResult("CLEARED", bagNo, fees.clearance(), now, event.month,
                    "ADJUSTMENT:" + adj.id, "清运成功；该月账单已结算，费用计入补差单 #" + adj.id);
        }
        return new ScanResult("CLEARED", bagNo, fees.clearance(), now, event.month,
                "PENDING_BILL", "清运成功，费用 " + fees.clearance() + " 元计入 " + event.month + " 账单");
    }

    // ---------- 内部 ----------

    BagEvent record(String bagNo, Owner owner, EventType type, LocalDateTime at, String operator, String note) {
        BagEvent e = new BagEvent();
        e.bagNo = bagNo;
        e.owner = owner;
        e.type = type;
        e.occurredAt = at;
        e.month = YearMonth.from(at).toString();
        e.operator = operator;
        e.note = note;
        e.persist();
        return e;
    }

    Owner requireOwner(long ownerId) {
        Owner owner = Owner.findById(ownerId);
        if (owner == null) {
            throw new NotFoundException("业主不存在: " + ownerId);
        }
        return owner;
    }

    Bag requireBag(String bagNo) {
        Bag bag = Bag.findByBagNo(bagNo);
        if (bag == null) {
            throw new NotFoundException("袋号不存在: " + bagNo);
        }
        return bag;
    }
}
