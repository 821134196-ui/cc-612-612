package org.example.wastebag.service;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;

/** 费用标准：清运费按实际清运袋数计，遗失赔付按约定计 */
@ConfigMapping(prefix = "app.fees")
public interface FeeConfig {

    /** 每袋清运费 */
    @WithDefault("5.00")
    BigDecimal clearance();

    /** 每袋遗失赔付 */
    @WithDefault("15.00")
    BigDecimal lost();
}
