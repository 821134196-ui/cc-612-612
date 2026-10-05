package com.renovation.bag.api.dto;

import com.renovation.bag.domain.BagRecord;
import com.renovation.bag.domain.BagStatus;
import java.time.Instant;

public class BagDto {
    public Long id;
    public String bagCode;
    public String ownerCode;
    public String ownerName;
    public String room;
    public BagStatus status;
    public String issueBatch;
    public Instant issuedAt;
    public Instant returnedAt;
    public Instant collectedAt;
    public String collectedMonth;
    public Instant markedLostAt;
    public String lostMonth;
    public String collector;
    public String note;

    public static BagDto of(BagRecord b) {
        BagDto d = new BagDto();
        d.id = b.getId();
        d.bagCode = b.getBagCode();
        d.ownerCode = b.getOwner().getCode();
        d.ownerName = b.getOwner().getName();
        d.room = b.getOwner().getRoom();
        d.status = b.getStatus();
        d.issueBatch = b.getIssueBatch();
        d.issuedAt = b.getIssuedAt();
        d.returnedAt = b.getReturnedAt();
        d.collectedAt = b.getCollectedAt();
        d.collectedMonth = b.getCollectedMonth();
        d.markedLostAt = b.getMarkedLostAt();
        d.lostMonth = b.getLostMonth();
        d.collector = b.getCollector();
        d.note = b.getNote();
        return d;
    }
}
