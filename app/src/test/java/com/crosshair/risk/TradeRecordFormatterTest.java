package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TradeRecordFormatterTest {
    @Test
    public void formatsCapturedTradeRecordInRequestedOrder() {
        String record = TradeRecordFormatter.format(
                "4.114", "4.150", "沪深300ETF华泰柏瑞", "510300", "2026-11-12");

        assertEquals("止损价：4.114\n"
                + "买入价：4.150\n"
                + "股票名称：沪深300ETF华泰柏瑞\n"
                + "股票代码：510300\n"
                + "买入日期：2026-11-12", record);
    }

    @Test
    public void usesPlaceholdersForMissingValues() {
        String record = TradeRecordFormatter.format("", null, "", " ", "");

        assertEquals("止损价：未捕获\n"
                + "买入价：未捕获\n"
                + "股票名称：未识别\n"
                + "股票代码：未识别\n"
                + "买入日期：未记录", record);
    }
}
