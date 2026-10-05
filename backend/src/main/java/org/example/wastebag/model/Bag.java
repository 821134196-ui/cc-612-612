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
 * 垃圾袋。袋号全局唯一，全生命周期（含破损、跨月清运）只沿用这一个袋号，
 * 不另建袋记录，避免重复计费。
 */
@Entity
@Table(name = "bag")
public class Bag extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String bagNo;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    public Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BagStatus status;

    public LocalDateTime issuedAt;
    public LocalDateTime returnedAt;
    public LocalDateTime clearedAt;

    /** 破损标记：破损袋仍用原袋号清运，仅作标记 */
    public boolean damaged;

    public static Bag findByBagNo(String bagNo) {
        return find("bagNo", bagNo).firstResult();
    }
}
