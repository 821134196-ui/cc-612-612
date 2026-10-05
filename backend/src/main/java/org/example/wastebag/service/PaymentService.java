package org.example.wastebag.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.example.wastebag.model.Adjustment;
import org.example.wastebag.model.Bill;
import org.example.wastebag.model.BillStatus;
import org.example.wastebag.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 本地模拟支付：不调用任何外部网关，直接生成成功回执。
 * 账单须先结算才能支付；补差单独立支付。重复支付被拒绝。
 */
@ApplicationScoped
public class PaymentService {

    @Inject
    BillingService billingService;

    public record PayResult(String txnId, String status, BigDecimal amount, String target) {}

    @Transactional
    public PayResult payBill(long billId, BigDecimal amount) {
        Bill bill = billingService.requireBill(billId);
        if (bill.status == BillStatus.OPEN) {
            throw new BadRequestException("账单尚未结算，不能支付");
        }
        if (bill.status == BillStatus.PAID) {
            throw new BadRequestException("账单已支付，请勿重复支付");
        }
        BigDecimal total = billingService.totalOfBill(billId);
        checkAmount(amount, total);
        Payment p = persistPayment(amount, bill, null);
        bill.status = BillStatus.PAID;
        return new PayResult(p.txnId, "SUCCESS", amount, "BILL:" + billId);
    }

    @Transactional
    public PayResult payAdjustment(long adjustmentId, BigDecimal amount) {
        Adjustment adj = Adjustment.findById(adjustmentId);
        if (adj == null) {
            throw new BadRequestException("补差单不存在: " + adjustmentId);
        }
        if (adj.status == Adjustment.AdjustmentStatus.PAID) {
            throw new BadRequestException("补差单已支付，请勿重复支付");
        }
        BigDecimal total = billingService.totalOfAdjustment(adjustmentId);
        checkAmount(amount, total);
        Payment p = persistPayment(amount, null, adj);
        adj.status = Adjustment.AdjustmentStatus.PAID;
        return new PayResult(p.txnId, "SUCCESS", amount, "ADJUSTMENT:" + adjustmentId);
    }

    private Payment persistPayment(BigDecimal amount, Bill bill, Adjustment adj) {
        Payment p = new Payment();
        p.bill = bill;
        p.adjustment = adj;
        p.amount = amount;
        // 模拟网关交易号：本地生成，无外部调用
        p.txnId = "MOCK-" + UUID.randomUUID();
        p.status = "SUCCESS";
        p.paidAt = LocalDateTime.now();
        p.persist();
        return p;
    }

    private void checkAmount(BigDecimal amount, BigDecimal total) {
        if (amount == null || amount.compareTo(total) != 0) {
            throw new BadRequestException("支付金额须等于应付金额 " + total);
        }
    }
}
