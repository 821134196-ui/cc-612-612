package org.example.wastebag.model;

/** 垃圾袋生命周期状态 */
public enum BagStatus {
    /** 已发放给业主 */
    ISSUED,
    /** 未使用已退回物业 */
    RETURNED,
    /** 已被清运员扫码收走 */
    CLEARED,
    /** 业主报失（按约定赔付） */
    LOST
}
