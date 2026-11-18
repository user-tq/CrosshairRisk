package com.crosshair.risk;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AlertItemTest {
    @Test
    public void serializesAndParsesRoundTrip() {
        AlertItem item = new AlertItem("贵州茅台", "600519", 1700.0,
                AlertItem.DIR_ABOVE, true, "突破后清仓");

        String serialized = AlertItem.serialize(Arrays.asList(item));
        List<AlertItem> parsed = AlertItem.parse(serialized);

        assertEquals(1, parsed.size());
        AlertItem round = parsed.get(0);
        assertEquals(item.id, round.id);
        assertEquals("贵州茅台", round.name);
        assertEquals("600519", round.code);
        assertEquals(1700.0, round.targetPrice, 1e-6);
        assertEquals(AlertItem.DIR_ABOVE, round.direction);
        assertTrue(round.enabled);
        assertEquals("突破后清仓", round.note);
    }

    @Test
    public void marketSymbolPrefixesByFirstDigit() {
        assertEquals("sh600519", new AlertItem("", "600519", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
        assertEquals("sz000001", new AlertItem("", "000001", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
        assertEquals("sz159892", new AlertItem("", "159892", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
        assertEquals("sz300750", new AlertItem("", "300750", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
        assertEquals("bj830799", new AlertItem("", "830799", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
        assertEquals("", new AlertItem("", "", 0,
                AlertItem.DIR_ABOVE, false, "").marketSymbol());
    }

    @Test
    public void triggersAboveCondition() {
        AlertItem item = new AlertItem("", "600519", 1700.0,
                AlertItem.DIR_ABOVE, true, "");
        assertTrue(item.triggers(1700.0));
        assertTrue(item.triggers(1705.0));
        assertFalse(item.triggers(1699.9));
        assertFalse(item.triggers(0));
        assertFalse(item.triggers(-1));
    }

    @Test
    public void triggersBelowCondition() {
        AlertItem item = new AlertItem("", "600519", 1700.0,
                AlertItem.DIR_BELOW, true, "");
        assertTrue(item.triggers(1700.0));
        assertTrue(item.triggers(1690.0));
        assertFalse(item.triggers(1700.1));
    }

    @Test
    public void parsesTencentResponse() {
        String body = "v_sh600519=\"1~贵州茅台~600519~1705.00~1700.00~1698.50~100\";\n"
                + "v_sz000001=\"51~平安银行~000001~12.19~12.25~12.24~100\";\n"
                + "v_bj920002=\"62~万达轴承~920002~45.56~46.16~46.16~100\";";
        Map<String, Double> prices = AlertMonitor.parseQuotes(body);
        assertEquals(1705.0, prices.get("sh600519"), 0.01);
        assertEquals(12.19, prices.get("sz000001"), 0.01);
        assertEquals(45.56, prices.get("bj920002"), 0.01);
    }

    @Test
    public void parsePriceFieldReturnsZeroForStale() {
        assertEquals(0, AlertMonitor.parsePriceField("1~名称~代码~0.00~0.00"), 0.01);
        assertEquals(1700.5, AlertMonitor.parsePriceField("1~名称~代码~1700.5~1700"), 0.01);
        assertEquals(0, AlertMonitor.parsePriceField(""), 0.01);
    }
}
