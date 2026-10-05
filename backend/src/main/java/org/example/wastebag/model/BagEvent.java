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
import java.util.List;

/**
 * 袋号事件流水：领用、退袋、清运、报失、破损。
 * 账单与补差单的费用都从这里派生；billed 标记保证同一事件只计费一次。
 */
@Entity
@Table(name = "bag_event")
public class BagEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String bagNo;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    public Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EventType type;

    @Column(nullable = false)
    public LocalDateTime occurredAt;

    /** 事件归属账期 yyyy-MM（按发生时间），跨月清运时费用落在清运当月 */
    @Column(nullable = false, length = 7)
    public String month;

    public String operator;

    public String note;

    /** 是否已计入账单或补差单（防重复计费的落库标记） */
    @Column(nullable = false)
    public boolean billed = false;

    public static BagEvent findClearEvent(String bagNo) {
        return find("bagNo = ?1 and type = ?2", bagNo, EventType.CLEAR).firstResult();
    }

    public static List<BagEvent> unbilledOfType(Long ownerId, String month, EventType type) {
        return list("owner.id = ?1 and month = ?2 and type = ?3 and billed = false",
                ownerId, month, type);
    }

    public static List<BagEvent> ofMonth(Long ownerId, String month) {
        return list("owner.id = ?1 and month = ?2 order by occurredAt, id", ownerId, month);
    }
}
