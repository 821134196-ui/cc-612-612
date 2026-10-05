package org.example.wastebag.model;

/** 袋号事件类型（领用/退袋/清运/报失/破损） */
public enum EventType {
    ISSUE,
    RETURN,
    CLEAR,
    LOST,
    DAMAGE
}
