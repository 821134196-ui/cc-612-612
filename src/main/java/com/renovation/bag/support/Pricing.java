package com.renovation.bag.support;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** 每袋清运费（分） */
@ApplicationScoped
public class Pricing {

    private final long perBagFen;
    public final String currency;

    public Pricing(@ConfigProperty(name = "app.pricing.per-bag", defaultValue = "8.00") String perBag,
                   @ConfigProperty(name = "app.pricing.currency", defaultValue = "CNY") String currency) {
        this.perBagFen = Math.round(Double.parseDouble(perBag) * 100);
        this.currency = currency;
    }

    public long perBagFen() {
        return perBagFen;
    }
}
