package org.example.wastebag.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.example.wastebag.model.Adjustment;
import org.example.wastebag.model.BagEvent;
import org.example.wastebag.model.Bill;
import org.example.wastebag.model.BillItem;
import org.example.wastebag.model.BillStatus;
import org.example.wastebag.model.EventType;
import org.example.wastebag.model.ItemType;
import org.example.wastebag.model.Owner;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费核心：
 *  - 费用只从「清运」「报失」两类事件派生；发放/退回/破损永不计费；
 *  - 每个事件只计费一次（billed 标记 + 明细回指事件）；
 *  - 账单结算后冻结，后续差错（含补录的跨月事件）一律生成补差单。
 */
@ApplicationScoped
public class BillingService {

    @Inject
    FeeConfig fees;

    // ---------- 出账 ----------

    public record GenerateResult(List<Bill> bills, List<Adjustment> adjustments) {}

    /**
     * 生成（或补充）某账期账单。可重复调用，结果不变：
     * 未入账事件 -> OPEN 账单补明细；已结算 -> 自动生成补差单。
     */
    @Transactional
    public GenerateResult generate(String month, Long ownerId) {
        validateMonth(month);
        List<Owner> owners = ownerId != null
                ? List.of(requireOwner(ownerId))
                : Owner.listAll();
        List<Bill> bills = new ArrayList<>();
        List<Adjustment> adjustments = new ArrayList<>();
        for (Owner owner : owners) {
            List<BagEvent> pending = new ArrayList<>();
            pending.addAll(BagEvent.unbilledOfType(owner.id, month, EventType.CLEAR));
            pending.addAll(BagEvent.unbilledOfType(owner.id, month, EventType.LOST));
            if (pending.isEmpty()) {
                continue;
            }
            Bill bill = Bill.findByOwnerAndMonth(owner.id, month);
            if (bill == null) {
                bill = new Bill();
                bill.owner = owner;
                bill.month = month;
                bill.status = BillStatus.OPEN;
                bill.createdAt = LocalDateTime.now();
                bill.persist();
            }
            if (bill.status == BillStatus.OPEN) {
                for (BagEvent e : pending) {
                    addEventItem(bill, null, e);
                }
                bills.add(bill);
            } else {
                // 已结算/已支付：原账单不动，差额进补差单
                Adjustment adj = createAdjustmentForEvents(bill, "结算后补录" + month + "费用", pending);
                adjustments.add(adj);
            }
        }
        return new GenerateResult(bills, adjustments);
    }

    /** 把事件转成费用明细并标记已入账（同一事件不会第二次进入这里） */
    BillItem addEventItem(Bill bill, Adjustment adjustment, BagEvent event) {
        if (event.billed) {
            throw new IllegalStateException("事件已入账，禁止重复计费: event=" + event.id);
        }
        BillItem item = new BillItem();
        item.bill = bill;
        item.adjustment = adjustment;
        item.event = event;
        item.bagNo = event.bagNo;
        if (event.type == EventType.CLEAR) {
            item.type = ItemType.CLEARANCE_FEE;
            item.amount = fees.clearance();
            item.description = "清运费 袋号 " + event.bagNo;
        } else if (event.type == EventType.LOST) {
            item.type = ItemType.LOST_FEE;
            item.amount = fees.lost();
            item.description = "遗失赔付 袋号 " + event.bagNo;
        } else {
            throw new IllegalArgumentException("事件类型不产生费用: " + event.type);
        }
        item.persist();
        event.billed = true;
        return item;
    }

    // ---------- 结算 ----------

    @Transactional
    public Bill settle(long billId) {
        Bill bill = requireBill(billId);
        if (bill.status != BillStatus.OPEN) {
            throw new BadRequestException("只有出账中的账单可以结算，当前状态: " + bill.status);
        }
        bill.status = BillStatus.SETTLED;
        bill.settledAt = LocalDateTime.now();
        return bill;
    }

    // ---------- 补差单 ----------

    public record ManualItem(ItemType type, String bagNo, BigDecimal amount, String description) {}

    /** 已结算账单的差错更正：生成补差单，原账单保持不变 */
    @Transactional
    public Adjustment createAdjustment(long billId, String reason, List<ManualItem> items) {
        Bill bill = requireBill(billId);
        if (bill.status == BillStatus.OPEN) {
            throw new BadRequestException("账单尚未结算，可直接重新出账，无需补差单");
        }
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("补差单至少包含一条明细");
        }
        Adjustment adj = newAdjustment(bill, reason);
        for (ManualItem mi : items) {
            if (mi.type() != ItemType.ADJUST_DEBIT && mi.type() != ItemType.ADJUST_CREDIT) {
                throw new BadRequestException("手工补差明细类型须为 ADJUST_DEBIT / ADJUST_CREDIT");
            }
            if (mi.amount() == null || mi.amount().signum() <= 0) {
                throw new BadRequestException("补差金额须为正数（方向由类型决定）");
            }
            BillItem item = new BillItem();
            item.adjustment = adj;
            item.type = mi.type();
            item.bagNo = mi.bagNo();
            // CREDIT 以负数金额入账，冲减应收
            item.amount = mi.type() == ItemType.ADJUST_CREDIT ? mi.amount().negate() : mi.amount();
            item.description = mi.description();
            item.persist();
        }
        return adj;
    }

    /** 由事件自动生成补差单（结算后补录的清运/报失） */
    @Transactional
    public Adjustment createAdjustmentForEvents(Bill bill, String reason, List<BagEvent> events) {
        Adjustment adj = newAdjustment(bill, reason);
        for (BagEvent e : events) {
            addEventItem(null, adj, e);
        }
        return adj;
    }

    Adjustment newAdjustment(Bill bill, String reason) {
        Adjustment adj = new Adjustment();
        adj.bill = bill;
        adj.reason = reason;
        adj.status = Adjustment.AdjustmentStatus.OPEN;
        adj.createdAt = LocalDateTime.now();
        adj.persist();
        return adj;
    }

    // ---------- 查询 ----------

    public BigDecimal totalOfBill(long billId) {
        return BillItem.ofBill(billId).stream()
                .map(i -> i.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalOfAdjustment(long adjustmentId) {
        return BillItem.ofAdjustment(adjustmentId).stream()
                .map(i -> i.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Adjustment> adjustmentsOf(long billId) {
        return Adjustment.list("bill.id = ?1 order by id", billId);
    }

    /** 业主对账单：领用 / 退袋 / 清运 / 报失 并列，费用来源可逐条核对 */
    public Map<String, Object> statement(long ownerId, String month) {
        Owner owner = requireOwner(ownerId);
        validateMonth(month);
        List<BagEvent> events = BagEvent.ofMonth(ownerId, month);
        List<Map<String, Object>> issued = new ArrayList<>();
        List<Map<String, Object>> returned = new ArrayList<>();
        List<Map<String, Object>> cleared = new ArrayList<>();
        List<Map<String, Object>> lost = new ArrayList<>();
        BigDecimal clearanceFees = BigDecimal.ZERO;
        BigDecimal lostFees = BigDecimal.ZERO;
        for (BagEvent e : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bagNo", e.bagNo);
            row.put("at", e.occurredAt.toString());
            row.put("operator", e.operator);
            switch (e.type) {
                case ISSUE -> issued.add(row);
                case RETURN -> returned.add(row);
                case CLEAR -> {
                    row.put("fee", fees.clearance());
                    cleared.add(row);
                    clearanceFees = clearanceFees.add(fees.clearance());
                }
                case LOST -> {
                    row.put("fee", fees.lost());
                    lost.add(row);
                    lostFees = lostFees.add(fees.lost());
                }
                case DAMAGE -> { /* 破损沿用原袋号，不单独列示、不计费 */ }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerId", owner.id);
        result.put("ownerName", owner.name);
        result.put("unit", owner.unit);
        result.put("month", month);
        result.put("issued", issued);
        result.put("returned", returned);
        result.put("cleared", cleared);
        result.put("lost", lost);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("issuedCount", issued.size());
        totals.put("returnedCount", returned.size());
        totals.put("clearedCount", cleared.size());
        totals.put("lostCount", lost.size());
        totals.put("clearanceFees", clearanceFees);
        totals.put("lostFees", lostFees);
        totals.put("total", clearanceFees.add(lostFees));
        result.put("totals", totals);
        Bill bill = Bill.findByOwnerAndMonth(ownerId, month);
        if (bill != null) {
            result.put("billId", bill.id);
            result.put("billStatus", bill.status.name());
        }
        return result;
    }

    // ---------- 工具 ----------

    public Owner requireOwner(long ownerId) {
        Owner owner = Owner.findById(ownerId);
        if (owner == null) {
            throw new NotFoundException("业主不存在: " + ownerId);
        }
        return owner;
    }

    public Bill requireBill(long billId) {
        Bill bill = Bill.findById(billId);
        if (bill == null) {
            throw new NotFoundException("账单不存在: " + billId);
        }
        return bill;
    }

    static void validateMonth(String month) {
        try {
            YearMonth.parse(month);
        } catch (Exception e) {
            throw new BadRequestException("账期格式须为 yyyy-MM: " + month);
        }
    }
}
