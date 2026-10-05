package com.renovation.bag.api.dto;

import com.renovation.bag.domain.AdjustmentLine;
import java.time.Instant;

public class AdjustmentLineDto {
    public Long id;
    public String bagCode;
    public String correctionType;
    public long deltaFen;
    public String reason;
    public Instant createdAt;

    public static AdjustmentLineDto of(AdjustmentLine l) {
        AdjustmentLineDto d = new AdjustmentLineDto();
        d.id = l.getId();
        d.bagCode = l.getBagCode();
        d.correctionType = l.getCorrectionType();
        d.deltaFen = l.getDeltaFen();
        d.reason = l.getReason();
        d.createdAt = Instant.ofEpochMilli(l.getCreatedAt());
        return d;
    }
}
