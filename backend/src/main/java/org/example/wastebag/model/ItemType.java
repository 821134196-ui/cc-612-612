package org.example.wastebag.model;

/** 账单/补差单明细类型 */
public enum ItemType {
    /** 清运费（按实际清运袋数） */
    CLEARANCE_FEE,
    /** 遗失赔付（按约定） */
    LOST_FEE,
    /** 补差单手工补收 */
    ADJUST_DEBIT,
    /** 补差单手工退回（负数金额也可直接用 CREDIT 正数表示冲减） */
    ADJUST_CREDIT
}
