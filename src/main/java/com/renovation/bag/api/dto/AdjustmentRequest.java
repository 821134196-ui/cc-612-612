package com.renovation.bag.api.dto;

import java.util.List;

public class AdjustmentRequest {
    public String ownerCode;
    public String month;
    public List<Line> lines;

    public static class Line {
        public String bagCode;
        /** MISSED_COLLECTION / WRONG_COLLECTION */
        public String correctionType;
        public String reason;
    }
}
