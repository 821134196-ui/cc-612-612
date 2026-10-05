package com.renovation.bag.api.dto;

import com.renovation.bag.domain.BillItem;
import java.time.Instant;

public class BillItemDto {
    public Long id;
    public String bagCode;
    public String eventType;
    public long amountFen;
    public Instant eventAt;
    public String eventMonth;

    public static BillItemDto of(BillItem i) {
        BillItemDto d = new BillItemDto();
        d.id = i.getId();
        d.bagCode = i.getBagCode();
        d.eventType = i.getEventType();
        d.amountFen = i.getAmountFen();
        d.eventAt = Instant.ofEpochMilli(i.getEventAt());
        d.eventMonth = i.getEventMonth();
        return d;
    }
}
