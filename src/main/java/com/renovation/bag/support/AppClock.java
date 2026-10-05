package com.renovation.bag.support;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 应用时钟。默认取系统时间；测试可固定到任意时刻，
 * 以便验证"跨月清运沿用原袋号"。
 */
@ApplicationScoped
public class AppClock {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private volatile Instant fixed;

    public Instant now() {
        return fixed != null ? fixed : Instant.now();
    }

    public String currentMonth() {
        return MONTH.format(now().atOffset(ZoneOffset.UTC));
    }

    public static String monthOf(Instant instant) {
        return MONTH.format(instant.atOffset(ZoneOffset.UTC));
    }

    public void setFixed(Instant instant) {
        this.fixed = instant;
    }

    public void reset() {
        this.fixed = null;
    }
}
