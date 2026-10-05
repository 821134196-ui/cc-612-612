package com.renovation.bag.domain;

import jakarta.persistence.*;

/**
 * 清运员每次扫码的回执日志（包括重复扫码尝试）。
 * 重复扫码只产生 DUPLICATE 回执，不会产生第二次有效清运。
 */
@Entity
@Table(name = "scan_log")
public class ScanLog {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String bagCode;

    @Column(length = 40)
    private String collector;

    @Column(nullable = false)
    private long scannedAt;

    /** ACCEPTED / DUPLICATE / REJECTED */
    @Column(nullable = false, length = 12)
    private String result;

    @Column(length = 200)
    private String detail;

    public ScanLog() {
    }

    public ScanLog(String bagCode, String collector, long scannedAt, String result, String detail) {
        this.bagCode = bagCode;
        this.collector = collector;
        this.scannedAt = scannedAt;
        this.result = result;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public String getBagCode() { return bagCode; }
    public String getCollector() { return collector; }
    public long getScannedAt() { return scannedAt; }
    public String getResult() { return result; }
    public String getDetail() { return detail; }
}
