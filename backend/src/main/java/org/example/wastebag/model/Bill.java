package org.example.wastebag.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 月度账单。每个业主每个账期最多一张；结算后内容冻结，
 * 之后的差错一律通过补差单（Adjustment）修正，原账单不变。
 */
@Entity
@Table(name = "bill", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "month"}))
public class Bill extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    public Owner owner;

    /** 账期 yyyy-MM */
    @Column(nullable = false, length = 7)
    public String month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BillStatus status = BillStatus.OPEN;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public LocalDateTime settledAt;

    public static Bill findByOwnerAndMonth(Long ownerId, String month) {
        return find("owner.id = ?1 and month = ?2", ownerId, month).firstResult();
    }
}
