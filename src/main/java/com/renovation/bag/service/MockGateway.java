package com.renovation.bag.service;

/**
 * 本地模拟支付网关：不接外部渠道，始终成功。
 * 正额扣款返回 PAY- 流水；负额（退费）返回 REF- 流水。
 */
public final class MockGateway {

    private MockGateway() {
    }

    public static String charge(long amountFen, String via) {
        String prefix = amountFen < 0 ? "REF" : "PAY";
        return prefix + "-MOCK-" + Long.toHexString(System.currentTimeMillis())
                + "-" + Long.toHexString(Double.doubleToLongBits(amountFen) & 0xffff);
    }
}
