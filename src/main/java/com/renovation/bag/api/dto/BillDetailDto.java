package com.renovation.bag.api.dto;

import java.util.List;

/**
 * 账单核对视图：并排展示领用 / 退袋 / 清运 三类记录与计费来源。
 */
public class BillDetailDto {
    public BillDto bill;
    /** 收费明细（每袋一次） */
    public List<BillItemDto> chargedItems;
    /** 补差明细（仅补差单） */
    public List<AdjustmentLineDto> adjustmentLines;
    /** 该业主全部袋号记录（领用/退袋/清运/遗失并排） */
    public List<BagDto> bags;

    public long issuedCount;
    public long returnedCount;
    public long collectedInMonth;
    public long lostInMonth;
    public long chargedCount;
    public long chargeTotalFen;
    public long unitPriceFen;
    public String currency;
}
