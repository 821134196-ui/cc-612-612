package org.example.wastebag.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付记录（本地模拟支付网关的回执） */
@Entity
@Table(name = "payment")
public class Payment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bill_id")
    public Bill bill;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "adjustment_id")
    public Adjustment adjustment;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, unique = true)
    public String txnId;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public LocalDateTime paidAt;
}
