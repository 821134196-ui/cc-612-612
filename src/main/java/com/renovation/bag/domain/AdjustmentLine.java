package com.renovation.bag.domain;

import jakarta.persistence.*;

/**
 * 补差单行：已结算月份发现差错时，只对差额计费/退费，不动原账单。
 * 唯一约束 (original_bill_id, bag_code, correction_type) 保证同一种更正
 * 对同一袋号只能提交一次，重复提交不会重复收费。
 */
@Entity
@Table(name = "adjustment_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_adjust_once",
                columnNames = {"original_bill_id", "bag_code", "correction_type"}))
public class AdjustmentLine {

    /** 漏收：袋子当月实际清运/遗失但未计入 -> 补收 +fee */
    public static final String MISSED_COLLECTION = "MISSED_COLLECTION";
    /** 错收：并未清运却被收费 -> 退费 -fee */
    public static final String WRONG_COLLECTION = "WRONG_COLLECTION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_bill_id")
    private Bill originalBill;

    @Column(nullable = false, length = 40)
    private String bagCode;

    @Column(nullable = false, length = 24)
    private String correctionType;

    /** 正为补收，负为退费 */
    @Column(nullable = false)
    private long deltaFen;

    @Column(length = 200)
    private String reason;

    @Column(nullable = false)
    private long createdAt;

    public AdjustmentLine() {
    }

    public AdjustmentLine(Bill bill, Bill originalBill, String bagCode,
                          String correctionType, long deltaFen, String reason, long createdAt) {
        this.bill = bill;
        this.originalBill = originalBill;
        this.bagCode = bagCode;
        this.correctionType = correctionType;
        this.deltaFen = deltaFen;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Bill getBill() { return bill; }
    public Bill getOriginalBill() { return originalBill; }
    public String getBagCode() { return bagCode; }
    public String getCorrectionType() { return correctionType; }
    public long getDeltaFen() { return deltaFen; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
}
