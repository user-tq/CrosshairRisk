package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class RiskCalculatorTest {
    @Test
    public void calculatesScreenshotEtfExample() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("0.752", "0.770");

        assertEquals("0.018", result.risk);
        assertEquals("0.036", result.profit);
        assertEquals("0.806", result.target);
    }

    @Test
    public void keepsStockPricePrecision() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("9.50", "10.00");

        assertEquals("0.50", result.risk);
        assertEquals("1.00", result.profit);
        assertEquals("11.00", result.target);
    }

    @Test
    public void keepsGreatestInputScale() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("0.75", "0.770");

        assertEquals("0.020", result.risk);
        assertEquals("0.810", result.target);
    }

    @Test
    public void rejectsStopAtOrAboveEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.770", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.800", "0.770"));
    }

    @Test
    public void rejectsMissingInvalidAndNonPositivePrices() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("abc", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.752", "-0.770"));
    }
}
