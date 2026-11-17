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
    public void sinaSymbolPrefixesByFirstDigit() {
        assertEquals("sh600519", new AlertItem("", "600519", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
        assertEquals("sz000001", new AlertItem("", "000001", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
        assertEquals("sz159892", new AlertItem("", "159892", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
        assertEquals("sz300750", new AlertItem("", "300750", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
        assertEquals("bj830799", new AlertItem("", "830799", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
        assertEquals("", new AlertItem("", "", 0,
                AlertItem.DIR_ABOVE, false, "").sinaSymbol());
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
    public void parsesSinaResponse() {
        AlertItem item = new AlertItem("", "600519", 0, AlertItem.DIR_ABOVE, false, "");
        Map<String, Double> prices = AlertMonitor.fetchPrices(Arrays.asList(item));
        // 联网测试在沙箱里跳过，只验证解析逻辑独立可测
        assertEquals(1705.0,
                AlertMonitor.parsePriceField("贵州茅台,1700.00,1698.50,1705.00,1710.00,1690.00,1700.00,1702.00"),
                0.01);
        assertNotNull(prices);
    }

    @Test
    public void parsePriceFieldReturnsZeroForStale() {
        assertEquals(0, AlertMonitor.parsePriceField(",,0,,0,0"), 0.01);
        assertEquals(1700.5, AlertMonitor.parsePriceField("贵州茅台,1700,1699,1700.5,1700,1700,1700,1700"), 0.01);
    }
}
