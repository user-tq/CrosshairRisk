package com.crosshair.risk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 价格监控：从腾讯行情接口拉取实时价，对已启用的监控条目判断触发条件，
 * 满足则发系统通知，并将该条目标记为已触发（enabled=false）。
 */
final class AlertMonitor {
    static final String CHANNEL_ID = "price_alert";
    static final String CHANNEL_NAME = "价格监控";
    /** 轮询间隔（毫秒）。 */
    static final long POLL_INTERVAL_MS = 30_000L;
    private static final String QUOTE_URL = "https://qt.gtimg.cn/q=";
    private static final String QUOTE_REFERER = "https://gu.qq.com/";
    /** 5 分钟K线周期（毫秒）。 */
    static final long BAR_INTERVAL_MS = 5 * 60 * 1000L;
    /** 收盘后再等 2 秒取价，确保行情已更新到该根K线的收盘价。 */
    private static final long BAR_CLOSE_DELAY_MS = 2_000L;
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "v_([a-z]{2}\\d+)=\"(.*?)\";", Pattern.DOTALL);
    private static final Charset GBK = Charset.forName("GBK");

    private AlertMonitor() {
    }

    /** 创建通知通道（Android 8+），重复调用安全。 */
    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(true);
        channel.setLightColor(Color.rgb(85, 214, 190));
        channel.enableVibration(true);
        nm.createNotificationChannel(channel);
    }

    /** 联网判断：有活跃网络接口且已连接。 */
    static boolean hasNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /** 是否只按 5 分钟K线收盘价判断。 */
    static boolean fiveMinCloseOnly(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(State.KEY_FIVE_MIN_CLOSE_ONLY, false);
    }

    /** 距离下一根 5 分钟K线收盘（含 2 秒缓冲）的毫秒数。 */
    static long millisUntilNextBarClose(long nowMillis) {
        long floor = Math.floorDiv(nowMillis, BAR_INTERVAL_MS) * BAR_INTERVAL_MS;
        long next = floor + BAR_INTERVAL_MS + BAR_CLOSE_DELAY_MS;
        long delay = next - nowMillis;
        if (delay <= 0) {
            delay = BAR_INTERVAL_MS;
        }
        return delay;
    }

    /** 是否处于 A 股交易时段（周一至周五 09:30–11:30、13:00–15:00）。 */
    static boolean isTradingSession(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
            return false;
        }
        int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        return (minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30)
                || (minutes >= 13 * 60 && minutes <= 15 * 60);
    }

    static boolean isTradingSession(long nowMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        return isTradingSession(cal);
    }

    /**
     * 执行一次监控扫描。返回本轮处理过程中尝试发送的通知数。
     */
    static int runOnce(Context context, SharedPreferences prefs) {
        if (!hasNetwork(context)) {
            return 0;
        }
        boolean barClose = fiveMinCloseOnly(prefs);
        if (barClose && !isTradingSession(System.currentTimeMillis())) {
            // 5 分钟K线模式只在交易时段判断，避免用停盘旧价误触发。
            return 0;
        }
        List<AlertItem> items = AlertItem.parse(prefs.getString(State.KEY_ALERTS, ""));
        List<AlertItem> active = new ArrayList<>();
        for (AlertItem item : items) {
            if (item.enabled && !item.code.isEmpty()) {
                active.add(item);
            }
        }
        if (active.isEmpty()) {
            return 0;
        }

        // 批量拉取
        Map<String, Double> prices = fetchPrices(active);

        int notified = 0;
        List<AlertItem> updated = new ArrayList<>();
        for (AlertItem item : items) {
            if (!item.enabled) {
                updated.add(item);
                continue;
            }
            Double price = prices.get(item.marketSymbol());
            if (price == null || price <= 0) {
                updated.add(item);
                continue;
            }
            if (item.triggers(price)) {
                sendNotification(context, item, price, barClose);
                notified++;
                // 触发后退出勾选
                updated.add(item.withEnabled(false));
            } else {
                updated.add(item);
            }
        }

        if (notified > 0) {
            prefs.edit().putString(State.KEY_ALERTS, AlertItem.serialize(updated)).apply();
        }
        return notified;
    }

    /** 批量拉取行情，返回 symbol → price 的映射。 */
    static Map<String, Double> fetchPrices(List<AlertItem> items) {
        Map<String, Double> prices = new HashMap<>();
        List<String> symbols = new ArrayList<>();
        for (AlertItem item : items) {
            String sym = item.marketSymbol();
            if (!sym.isEmpty() && !symbols.contains(sym)) {
                symbols.add(sym);
            }
        }
        if (symbols.isEmpty()) {
            return prices;
        }
        String joined = String.join(",", symbols);
        String body = httpGet(QUOTE_URL + joined, QUOTE_REFERER);
        return parseQuotes(body);
    }

    /** 解析腾讯行情响应，返回 symbol → price 的映射。 */
    static Map<String, Double> parseQuotes(String body) {
        Map<String, Double> prices = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return prices;
        }
        Matcher matcher = PRICE_PATTERN.matcher(body);
        while (matcher.find()) {
            prices.put(matcher.group(1), parsePriceField(matcher.group(2)));
        }
        return prices;
    }

    /** 腾讯行情以 ~ 分割字段，第 4 个字段（index 3）为最新价。 */
    static double parsePriceField(String fields) {
        if (fields == null || fields.isEmpty()) {
            return 0;
        }
        String[] parts = fields.split("~", -1);
        if (parts.length <= 3) {
            return 0;
        }
        try {
            double value = Double.parseDouble(parts[3].trim());
            return value > 0 ? value : 0;
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    static void sendNotification(Context context, AlertItem item, double price,
                                 boolean barClose) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        String condition = AlertItem.DIR_ABOVE.equals(item.direction)
                ? "≥" : "≤";
        String source = barClose ? "5分钟收盘" : "当前";
        String text = String.format(Locale.ROOT,
                "%s %.3f %s 目标 %.3f",
                source, price, condition, item.targetPrice);
        if (!item.note.isEmpty()) {
            text = text + "  ·  " + item.note;
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle((barClose ? "5分K提醒 · " : "价格提醒 · ") + item.display())
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .build();
        int id = Math.abs(item.id.hashCode());
        nm.notify(id, notification);
    }

    /** HTTP GET，返回响应体字符串（GBK 解码），失败返回 null。 */
    static String httpGet(String urlString, String referer) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Referer", referer);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 CrosshairRisk/1.0");
            connection.setRequestProperty("Accept-Charset", "GBK,utf-8");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), GBK))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
