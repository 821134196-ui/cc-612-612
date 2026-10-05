package com.renovation.bag.domain;

import jakarta.persistence.*;

/**
 * 月度账单。一个业主一个月份至多一张正式账单 + 若干补差单。
 */
@Entity
@Table(name = "bill",
        uniqueConstraints = @UniqueConstraint(name = "uk_bill_owner_month_type",
                columnNames = {"owner_id", "month", "billType"}))
public class Bill {

    public enum Type {
        /** 正式月账单，结算后冻结，不可修改 */
        REGULAR,
        /** 已结算月份发现差错时生成的补差单，保留原账单不变 */
        ADJUSTMENT
    }

    public enum Status {
        /** 草稿：按月随时可重算 */
        OPEN,
        /** 已结算（已支付），冻结 */
        SETTLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String billNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    /** 账单归属月份 yyyy-MM（REGULAR=清运/遗失发生月；ADJUSTMENT=被修正月份） */
    @Column(nullable = false, length = 7)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Type billType = Type.REGULAR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private long amountFen;

    /** 补差单指向原账单 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_bill_id")
    private Bill originalBill;

    @Column(length = 200)
    private String reason;

    @Column(nullable = false)
    private long createdAt;

    private Long settledAt;

    /** 支付流水号（本地模拟支付） */
    @Column(length = 64)
    private String paymentRef;

    public Bill() {
    }

    public Long getId() { return id; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public Owner getOwner() { return owner; }
    public void setOwner(Owner owner) { this.owner = owner; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public Type getBillType() { return billType; }
    public void setBillType(Type billType) { this.billType = billType; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getAmountFen() { return amountFen; }
    public void setAmountFen(long amountFen) { this.amountFen = amountFen; }
    public Bill getOriginalBill() { return originalBill; }
    public void setOriginalBill(Bill originalBill) { this.originalBill = originalBill; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Long getSettledAt() { return settledAt; }
    public void setSettledAt(Long settledAt) { this.settledAt = settledAt; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
}
