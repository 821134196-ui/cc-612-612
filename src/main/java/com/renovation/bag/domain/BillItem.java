package com.renovation.bag.domain;

import jakarta.persistence.*;

/**
 * 账单生成时对计费事件的快照。账单结算后即使袋记录后来被修正，
 * 已结算账单及其明细保持不变（补差单另计）。
 */
@Entity
@Table(name = "bill_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_bill_item_bag",
                columnNames = {"bill_id", "bag_code"}))
public class BillItem {

    public static final String COLLECTED = "COLLECTED";
    public static final String LOST = "LOST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id")
    private Bill bill;

    @Column(nullable = false, length = 40)
    private String bagCode;

    /** COLLECTED（扫码清运）或 LOST（约定遗失） */
    @Column(nullable = false, length = 16)
    private String eventType;

    @Column(nullable = false)
    private long amountFen;

    @Column(nullable = false)
    private long eventAt;

    @Column(length = 7)
    private String eventMonth;

    public BillItem() {
    }

    public BillItem(Bill bill, String bagCode, String eventType, long amountFen,
                    long eventAt, String eventMonth) {
        this.bill = bill;
        this.bagCode = bagCode;
        this.eventType = eventType;
        this.amountFen = amountFen;
        this.eventAt = eventAt;
        this.eventMonth = eventMonth;
    }

    public Long getId() { return id; }
    public Bill getBill() { return bill; }
    public String getBagCode() { return bagCode; }
    public String getEventType() { return eventType; }
    public long getAmountFen() { return amountFen; }
    public long getEventAt() { return eventAt; }
    public String getEventMonth() { return eventMonth; }
}
