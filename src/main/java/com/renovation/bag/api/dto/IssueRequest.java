package com.renovation.bag.api.dto;

import java.util.List;

public class IssueRequest {
    public String ownerCode;
    /** 直接指定袋号 */
    public List<String> bagCodes;
    /** 或按数量自动生成 */
    public Integer count;
    public String prefix;
}
