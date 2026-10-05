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

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用明细。挂在账单或补差单上（二选一）。
 * 由事件派生的明细带 event 引用；手工补差明细 event 为空。
 */
@Entity
@Table(name = "bill_item")
public class BillItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bill_id")
    public Bill bill;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "adjustment_id")
    public Adjustment adjustment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ItemType type;

    /** 关联袋号（手工调整可为空） */
    public String bagNo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    public BagEvent event;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;

    public String description;

    public static List<BillItem> ofBill(Long billId) {
        return list("bill.id = ?1 order by id", billId);
    }

    public static List<BillItem> ofAdjustment(Long adjustmentId) {
        return list("adjustment.id = ?1 order by id", adjustmentId);
    }
}
