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

import java.time.LocalDateTime;

/**
 * 补差单：已结算账单的差错更正载体。原账单保持不变，
 * 补收/退回金额都体现在补差单明细里，单独支付。
 */
@Entity
@Table(name = "adjustment")
public class Adjustment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "bill_id")
    public Bill bill;

    @Column(nullable = false)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdjustmentStatus status = AdjustmentStatus.OPEN;

    @Column(nullable = false)
    public LocalDateTime createdAt;

    public enum AdjustmentStatus {
        OPEN, PAID
    }
}
