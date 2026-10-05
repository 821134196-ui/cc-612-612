package org.example.wastebag.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 业主 */
@Entity
@Table(name = "owner")
public class Owner extends PanacheEntityBase {

    /** SQLite 用 IDENTITY（rowid）自增，避免序列表的隔离事务与业务事务互锁 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    /** 房号，如 3-2-501 */
    @Column(nullable = false, unique = true)
    public String unit;

    public String phone;
}
