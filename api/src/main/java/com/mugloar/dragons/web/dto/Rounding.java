package com.mugloar.dragons.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One rounding rule for every estimate we publish. Four decimals is already far beyond what the
 * measurements underneath support; it is here to keep the payload readable and to stop binary
 * fractions leaking into the wire as 0.30000000000000004.
 */
final class Rounding {

    static double estimate(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private Rounding() {
    }
}
