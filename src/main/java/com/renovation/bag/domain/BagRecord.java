package com.renovation.bag.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 一个袋号一条记录，贯穿"发放 -> 退回 / 清运 / 遗失"整个生命周期。
 * 袋子破损换新袋或跨月清运，仍然沿用同一袋号，因此不允许另建记录、
 * 也就不可能重复收费。
 */
@Entity
@Table(name = "bag_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_bag_code", columnNames = "bagCode"))
public class BagRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 印在袋子上的唯一编号，扫码即扫此码 */
    @Column(nullable = false, length = 40)
    private String bagCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BagStatus status = BagStatus.ISSUED;

    /** 发放批次（申领单号） */
    @Column(nullable = false, length = 40)
    private String issueBatch;

    @Column(nullable = false)
    private Instant issuedAt;

    private Instant returnedAt;

    private Instant collectedAt;

    /** 实际扫码清运发生的月份 yyyy-MM，可能与发放月不同（跨月沿用袋号） */
    @Column(length = 7)
    private String collectedMonth;

    private Instant markedLostAt;

    /** 约定遗失计费所属月份 */
    @Column(length = 7)
    private String lostMonth;

    /** 清运员 */
    @Column(length = 40)
    private String collector;

    /** 备注：破损换新、跨月说明等 */
    @Column(length = 200)
    private String note;

    public BagRecord() {
    }

    public BagRecord(String bagCode, Owner owner, String issueBatch, Instant issuedAt) {
        this.bagCode = bagCode;
        this.owner = owner;
        this.issueBatch = issueBatch;
        this.issuedAt = issuedAt;
        this.status = BagStatus.ISSUED;
    }

    public Long getId() { return id; }
    public String getBagCode() { return bagCode; }
    public Owner getOwner() { return owner; }
    public BagStatus getStatus() { return status; }
    public void setStatus(BagStatus status) { this.status = status; }
    public String getIssueBatch() { return issueBatch; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant collectedAt) { this.collectedAt = collectedAt; }
    public String getCollectedMonth() { return collectedMonth; }
    public void setCollectedMonth(String collectedMonth) { this.collectedMonth = collectedMonth; }
    public Instant getMarkedLostAt() { return markedLostAt; }
    public void setMarkedLostAt(Instant markedLostAt) { this.markedLostAt = markedLostAt; }
    public String getLostMonth() { return lostMonth; }
    public void setLostMonth(String lostMonth) { this.lostMonth = lostMonth; }
    public String getCollector() { return collector; }
    public void setCollector(String collector) { this.collector = collector; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
