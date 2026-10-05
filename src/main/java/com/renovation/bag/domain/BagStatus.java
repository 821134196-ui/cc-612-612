package com.renovation.bag.domain;

/**
 * 装修垃圾袋生命周期状态。
 * ISSUED   已发放（在业主手中，未计费）
 * RETURNED 未使用已退回（永远不计清运费）
 * COLLECTED 清运员扫码收走（计一次清运费）
 * LOST     双方约定遗失（视同清运，计一次费）
 */
public enum BagStatus {
    ISSUED,
    RETURNED,
    COLLECTED,
    LOST
}
