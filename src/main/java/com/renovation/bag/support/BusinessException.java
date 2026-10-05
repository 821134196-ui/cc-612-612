package com.renovation.bag.support;

/** 业务规则校验失败 -> 400 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
