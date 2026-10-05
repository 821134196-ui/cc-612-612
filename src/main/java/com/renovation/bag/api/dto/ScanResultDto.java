package com.renovation.bag.api.dto;

/** 清运员扫码后的即时回执 */
public class ScanResultDto {
    /** ACCEPTED / DUPLICATE / REJECTED */
    public String result;
    public BagDto bag;
    public String detail;
    /** 本次扫码是否产生了有效清运（只在首次为 true） */
    public boolean charged;

    public ScanResultDto() {
    }

    public ScanResultDto(String result, BagDto bag, String detail, boolean charged) {
        this.result = result;
        this.bag = bag;
        this.detail = detail;
        this.charged = charged;
    }
}
