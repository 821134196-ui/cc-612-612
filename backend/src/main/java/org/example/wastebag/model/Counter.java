package org.example.wastebag.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 发袋序号计数器（单写库的 SQLite 下事务内自增即可） */
@Entity
@Table(name = "counter")
public class Counter extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public long value;

    public static Counter byName(String name) {
        return find("name", name).firstResult();
    }
}
