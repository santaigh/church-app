package com.church.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Bound from {@code app.payment.*}. */
@Component
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class AppPaymentProperties {

    /** A family's monthly contribution may not be set below this. */
    private BigDecimal minMonthlyAmount = new BigDecimal("50.00");

    /** Prefix for generated receipt numbers, e.g. the R in {@code R-2026-0042}. */
    private String receiptPrefix = "R";
}
