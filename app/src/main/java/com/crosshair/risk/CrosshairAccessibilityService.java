package com.crosshair.risk;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrosshairAccessibilityService extends AccessibilityService {
    private static volatile CrosshairAccessibilityService instance;
    private static volatile boolean running;

    private SharedPreferences preferences;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private LinearLayout overlayBody;
    private TextView stockValue;
    private TextView stopValue;
    private TextView buyValue;
    private TextView targetValue;
    private TextView breakEvenValue;
    private TextView statusValue;
    private boolean collapsed;
    private boolean captureInFlight;
    private OfflineScreenshotRecognizer offlineRecognizer;
    private String lastForegroundPackage = "";

    public static CrosshairAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        running = true;
        preferences = getSharedPreferences(State.PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        applyOverlayVisibility();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        String packageName = event.getPackageName().toString();
        if (getPackageName().equals(packageName) || packageName.equals(lastForegroundPackage)) {
            return;
        }
        lastForegroundPackage = packageName;
        if (preferences != null) {
            preferences.edit().putString(State.KEY_PACKAGE, packageName).apply();
        }
    }

    @Override
    public void onInterrupt() {
        // No continuous capture is performed, so there is nothing to interrupt.
    }

    public void applyOverlayVisibility() {
        if (preferences == null || windowManager == null) {
            return;
        }
        if (preferences.getBoolean(State.KEY_VISIBLE, true)) {
            showOverlay();
        } else {
            removeOverlay();
        }
    }

    public void refreshFromPreferences() {
        if (overlayView != null) {
            renderPrices();
        }
    }

    private void showOverlay() {
        if (overlayView != null) {
            return;
        }
        buildOverlay();
        try {
            windowManager.addView(overlayView, overlayParams);
            renderPrices();
        } catch (RuntimeException error) {
            overlayView = null;
            preferences.edit().putString(State.KEY_MESSAGE,
                    "悬浮窗创建失败，请重新启用无障碍服务").apply();
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
                // The window may already have been removed by the system.
            }
            overlayView = null;
        }
    }

    private void buildOverlay() {
        LinearLayout container = Ui.vertical(this);
        container.setPadding(Ui.dp(this, 8), Ui.dp(this, 5),
                Ui.dp(this, 8), Ui.dp(this, 5));
        container.setBackground(Ui.outlined(Color.argb(196, 20, 26, 32),
                Color.argb(150, 67, 83, 91), 14, this));
        container.setElevation(Ui.dp(this, 10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        TextView dragHandle = Ui.text(this, "⠿", 12, Ui.MUTED);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setContentDescription("拖动悬浮窗");
        header.addView(dragHandle, new LinearLayout.LayoutParams(Ui.dp(this, 18),
                ViewGroup.LayoutParams.MATCH_PARENT));

        stockValue = Ui.bold(this, "股票未识别", 12, Ui.WHITE);
        stockValue.setSingleLine(true);
        stockValue.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(stockValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView copy = Ui.text(this, "复制", 11, Ui.ACCENT);
        copy.setGravity(Gravity.CENTER);
        copy.setContentDescription("复制交易信息");
        copy.setBackground(Ui.rounded(Color.argb(110, 85, 214, 190), 8, this));
        header.addView(copy, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 24)));

        TextView collapse = Ui.bold(this, collapsed ? "＋" : "－", 17, Ui.MUTED);
        collapse.setGravity(Gravity.CENTER);
        collapse.setContentDescription(collapsed ? "展开悬浮窗" : "收起悬浮窗");
        header.addView(collapse, new LinearLayout.LayoutParams(Ui.dp(this, 28),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = Ui.bold(this, "×", 19, Ui.MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("隐藏悬浮窗");
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 26),
                ViewGroup.LayoutParams.MATCH_PARENT));

        overlayBody = Ui.vertical(this);
        container.addView(overlayBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayBody.setVisibility(collapsed ? View.GONE : View.VISIBLE);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        overlayBody.addView(body, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 50), 4, this));

        TextView[] holder = new TextView[1];
        LinearLayout stopCell = priceCell("止损", Ui.MUTED, Ui.WHITE, 15, holder);
        stopValue = holder[0];
        body.addView(stopCell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout buyCell = priceCell("买入", Ui.MUTED, Ui.WHITE, 15, holder);
        buyValue = holder[0];
        body.addView(buyCell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout targetCell = Ui.vertical(this);
        targetCell.setGravity(Gravity.CENTER);
        TextView targetLabel = Ui.text(this, "2R卖出 / 1R保本", 9, Ui.ACCENT);
        targetLabel.setGravity(Gravity.CENTER);
        targetCell.addView(targetLabel);
        LinearLayout targetValues = Ui.vertical(this);
        targetValues.setGravity(Gravity.CENTER);
        targetValue = Ui.bold(this, "--", 15, Ui.ACCENT);
        targetValue.setGravity(Gravity.CENTER);
        targetValue.setSingleLine(true);
        targetValues.addView(targetValue);
        breakEvenValue = Ui.text(this, "", 9, Ui.MUTED);
        breakEvenValue.setGravity(Gravity.CENTER);
        breakEvenValue.setSingleLine(true);
        targetValues.addView(breakEvenValue);
        targetCell.addView(targetValues);
        body.addView(targetCell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.3f));

        Button captureStop = Ui.button(this, "捕获止损", false);
        Button captureBuy = Ui.button(this, "捕获买入", true);
        for (Button button : new Button[]{captureStop, captureBuy}) {
            button.setTextSize(11);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(0, 0, 0, 0);
        }
        LinearLayout.LayoutParams stopButtonParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 76), Ui.dp(this, 36));
        stopButtonParams.leftMargin = Ui.dp(this, 4);
        body.addView(captureStop, stopButtonParams);
        LinearLayout.LayoutParams buyButtonParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 76), Ui.dp(this, 36));
        buyButtonParams.leftMargin = Ui.dp(this, 5);
        body.addView(captureBuy, buyButtonParams);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        overlayBody.addView(footer, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 16), 2, this));

        statusValue = Ui.text(this, "请先在通达信中调出十字光标", 10, Ui.MUTED);
        statusValue.setSingleLine(true);
        statusValue.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(statusValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView diagnose = Ui.text(this, "诊断", 10, Ui.ACCENT);
        diagnose.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);
        footer.addView(diagnose, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView clear = Ui.text(this, "清空", 10, Ui.MUTED);
        clear.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 2), 0);
        footer.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        captureStop.setOnClickListener(view -> captureInto(true));
        captureBuy.setOnClickListener(view -> captureInto(false));
        copy.setOnClickListener(view -> copyTradeInfo());
        diagnose.setOnClickListener(view -> diagnoseCurrentPage());
        clear.setOnClickListener(view -> {
            preferences.edit().remove(State.KEY_STOP).remove(State.KEY_BUY)
                    .remove(State.KEY_NAME).remove(State.KEY_CODE)
                    .remove(State.KEY_BUY_DATE).remove(State.KEY_HEADER_TEXT)
                    .putString(State.KEY_MESSAGE, "交易信息已清空").apply();
            renderPrices();
        });
        collapse.setOnClickListener(view -> {
            collapsed = !collapsed;
            overlayBody.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            collapse.setText(collapsed ? "＋" : "－");
            collapse.setContentDescription(collapsed ? "展开悬浮窗" : "收起悬浮窗");
        });
        close.setOnClickListener(view -> {
            preferences.edit().putBoolean(State.KEY_VISIBLE, false).apply();
            removeOverlay();
        });
        enableDragging(container);

        int width = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 12);
        overlayParams = new WindowManager.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.BOTTOM | Gravity.START;
        overlayParams.x = Ui.dp(this, 6);
        overlayParams.y = Ui.dp(this, 12);
        overlayView = container;
    }

    private LinearLayout priceCell(String label, int labelColor, int valueColor,
                                   float valueSize, TextView[] holder) {
        LinearLayout cell = Ui.vertical(this);
        cell.setGravity(Gravity.CENTER);
        TextView labelView = Ui.text(this, label, 9, labelColor);
        labelView.setGravity(Gravity.CENTER);
        cell.addView(labelView);
        TextView value = Ui.bold(this, "--", valueSize, valueColor);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        holder[0] = value;
        cell.addView(value);
        return cell;
    }

    private void enableDragging(View handle) {
        final float[] initialTouch = new float[2];
        final int[] initialPosition = new int[2];
        final boolean[] dragging = new boolean[1];
        handle.setOnTouchListener((view, event) -> {
            if (overlayParams == null || overlayView == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouch[0] = event.getRawX();
                    initialTouch[1] = event.getRawY();
                    initialPosition[0] = overlayParams.x;
                    initialPosition[1] = overlayParams.y;
                    dragging[0] = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        return false;
                    }
                    moveOverlay(initialPosition[0] + Math.round(event.getRawX() - initialTouch[0]),
                            initialPosition[1] - Math.round(event.getRawY() - initialTouch[1]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean wasDragging = dragging[0];
                    dragging[0] = false;
                    return wasDragging;
                default:
                    return dragging[0];
            }
        });
    }

    private void moveOverlay(int x, int y) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxX = Math.max(0, metrics.widthPixels - overlayView.getWidth());
        int maxY = Math.max(0, metrics.heightPixels - overlayView.getHeight());
        overlayParams.x = Math.max(0, Math.min(x, maxX));
        overlayParams.y = Math.max(0, Math.min(y, maxY));
        windowManager.updateViewLayout(overlayView, overlayParams);
    }

    private void captureInto(boolean stop) {
        if (captureInFlight) {
            saveMessage("正在识别，请稍候");
            renderPrices();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveMessage("读取失败：未取得前台页面，请保持通达信在前台");
            renderPrices();
            return;
        }
        try {
            String packageName = root.getPackageName() == null
                    ? lastForegroundPackage : root.getPackageName().toString();
            if (!isTongDaXin(packageName)) {
                saveMessage("当前应用未识别为通达信：" + packageName + "，未读取价格");
                renderPrices();
                return;
            }
            List<PriceCaptureEngine.Node> nodes = snapshot(root);
            StockInfoEngine.Result info = StockInfoEngine.find(nodes);
            saveStockInfo(info.name, info.code);
            PriceCaptureEngine.Result result = PriceCaptureEngine.capture(nodes);
            saveDiagnostics(packageName, nodes.size(), result.diagnostics);
            if (result.isSuccess()) {
                applyCapturedPrice(stop, result.price, "页面元素");
                if (!info.hasName() || !info.hasCode()) {
                    startOfflineInfoCapture();
                } else {
                    renderPrices();
                }
                return;
            }
            startOfflineCapture(stop);
        } catch (RuntimeException error) {
            startOfflineCapture(stop);
        } finally {
            root.recycle();
        }
    }

    private void startOfflineCapture(boolean stop) {
        if (!prepareOfflineCapture("正在本地离线识别价格和股票信息…")) {
            return;
        }
        offlineRecognizer.capture(new OfflineScreenshotRecognizer.Callback() {
            @Override
            public void onResult(String price, String name, String code, String headerText,
                                 String errorMessage) {
                captureInFlight = false;
                saveStockInfo(name, code);
                saveHeaderText(headerText);
                if (price != null) {
                    applyCapturedPrice(stop, price, "离线图像识别");
                } else {
                    saveMessage(errorMessage);
                }
                renderPrices();
            }
        });
    }

    private void startOfflineInfoCapture() {
        if (Build.VERSION.SDK_INT < 30) {
            renderPrices();
            return;
        }
        if (!prepareOfflineCapture("价格已捕获，正在离线识别股票名称和代码…")) {
            renderPrices();
            return;
        }
        offlineRecognizer.capture(new OfflineScreenshotRecognizer.Callback() {
            @Override
            public void onResult(String price, String name, String code, String headerText,
                                 String errorMessage) {
                captureInFlight = false;
                saveStockInfo(name, code);
                saveHeaderText(headerText);
                if (name == null && code == null) {
                    saveMessage("未识别到股票信息，请保持顶部标题可见后重试");
                } else {
                    saveMessage("股票信息已更新");
                }
                renderPrices();
            }
        });
    }

    private boolean prepareOfflineCapture(String message) {
        if (Build.VERSION.SDK_INT < 30) {
            saveMessage("元素未识别；离线截图识别需要 Android 11 及以上");
            return false;
        }
        try {
            if (offlineRecognizer == null) {
                offlineRecognizer = new OfflineScreenshotRecognizer(this);
            }
        } catch (RuntimeException error) {
            saveMessage("离线识别模型启动失败，请重新安装应用后再试");
            return false;
        }
        captureInFlight = true;
        saveMessage(message);
        renderPrices();
        return true;
    }

    private void saveHeaderText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String value = text.trim();
        if (value.length() > 600) {
            value = value.substring(0, 600) + "…";
        }
        preferences.edit().putString(State.KEY_HEADER_TEXT, value).apply();
    }

    private void applyCapturedPrice(boolean stop, String price, String source) {
        String key = stop ? State.KEY_STOP : State.KEY_BUY;
        String label = stop ? "止损价" : "买入价";
        SharedPreferences.Editor editor = preferences.edit().putString(key, price)
                .putString(State.KEY_MESSAGE,
                        label + "已捕获 " + price + " · " + source);
        if (!stop) {
            editor.putString(State.KEY_BUY_DATE, LocalDate.now().toString());
        }
        editor.apply();
    }

    private void saveStockInfo(String name, String code) {
        if ((name == null || name.isEmpty()) && (code == null || code.isEmpty())) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (name != null && !name.isEmpty()) {
            editor.putString(State.KEY_NAME, name);
        }
        if (code != null && !code.isEmpty()) {
            editor.putString(State.KEY_CODE, code);
        }
        editor.apply();
    }

    private void copyTradeInfo() {
        String stop = preferences.getString(State.KEY_STOP, "");
        String buy = preferences.getString(State.KEY_BUY, "");
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        String date = preferences.getString(State.KEY_BUY_DATE, "");
        if (date.isEmpty()) {
            date = LocalDate.now().toString();
        }
        String text = TradeRecordFormatter.format(stop, buy, name, code, date);
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("交易信息", text));
            }
            Toast.makeText(this, "交易信息已复制", Toast.LENGTH_SHORT).show();
            saveMessage("交易信息已复制到剪贴板");
        } catch (RuntimeException error) {
            saveMessage("复制失败，请重试");
        }
        renderPrices();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlayView != null && overlayParams != null && windowManager != null) {
            overlayParams.width = getResources().getDisplayMetrics().widthPixels
                    - Ui.dp(this, 12);
            overlayParams.y = Ui.dp(this, 12);
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (IllegalArgumentException ignored) {
                // The overlay may have been removed while the configuration changed.
            }
        }
    }

    private void diagnoseCurrentPage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveMessage("诊断失败：未取得前台页面");
            renderPrices();
            return;
        }
        try {
            String packageName = root.getPackageName() == null
                    ? "未知" : root.getPackageName().toString();
            List<PriceCaptureEngine.Node> nodes = snapshot(root);
            saveDiagnostics(packageName, nodes.size(), PriceCaptureEngine.diagnostics(nodes));
            saveMessage("诊断完成，请回主界面查看结果");
        } catch (RuntimeException error) {
            saveMessage("诊断失败：系统未允许读取该页面");
        } finally {
            root.recycle();
        }
        renderPrices();
    }

    private List<PriceCaptureEngine.Node> snapshot(AccessibilityNodeInfo root) {
        List<PriceCaptureEngine.Node> nodes = new ArrayList<>();
        appendNode(root, nodes, 0);
        return nodes;
    }

    private void appendNode(AccessibilityNodeInfo node, List<PriceCaptureEngine.Node> output,
                            int depth) {
        if (node == null || depth > 80 || output.size() >= 4000) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        output.add(new PriceCaptureEngine.Node(
                string(node.getText()),
                string(node.getContentDescription()),
                safeViewId(node),
                string(node.getClassName()),
                bounds.toShortString(),
                node.isVisibleToUser(),
                node.isSelected(),
                node.isAccessibilityFocused()
        ));
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                try {
                    appendNode(child, output, depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private String safeViewId(AccessibilityNodeInfo node) {
        try {
            return node.getViewIdResourceName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void saveDiagnostics(String packageName, int nodeCount, String details) {
        preferences.edit().putString(State.KEY_PACKAGE, packageName)
                .putString(State.KEY_DIAGNOSTICS,
                        "前台包名：" + packageName + "\n遍历节点：" + nodeCount
                                + "（含无文本容器）\n" + details)
                .apply();
    }

    private boolean isTongDaXin(String packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toLowerCase(Locale.ROOT);
        return value.contains("tdx") || value.contains("tongdaxin");
    }

    private void saveMessage(String message) {
        preferences.edit().putString(State.KEY_MESSAGE, message).apply();
    }

    private void renderPrices() {
        if (stopValue == null) {
            return;
        }
        String stop = preferences.getString(State.KEY_STOP, "");
        String buy = preferences.getString(State.KEY_BUY, "");
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        String message = preferences.getString(State.KEY_MESSAGE, "等待捕获");
        stockValue.setText(name.isEmpty() && code.isEmpty()
                ? "股票未识别"
                : (name + (name.isEmpty() || code.isEmpty() ? "" : " ") + code));
        stopValue.setText(stop.isEmpty() ? "--" : stop);
        buyValue.setText(buy.isEmpty() ? "--" : buy);

        if (!stop.isEmpty() && !buy.isEmpty()) {
            try {
                RiskCalculator.Calculation result = RiskCalculator.calculate(stop, buy);
                targetValue.setText(result.target);
                targetValue.setTextColor(Ui.ACCENT);
                breakEvenValue.setText("保本 " + result.breakEven);
                breakEvenValue.setTextColor(Ui.MUTED);
            } catch (IllegalArgumentException error) {
                targetValue.setText("--");
                targetValue.setTextColor(Ui.DANGER);
                breakEvenValue.setText("");
                message = error.getMessage();
            }
        } else {
            targetValue.setText("--");
            targetValue.setTextColor(Ui.ACCENT);
            breakEvenValue.setText("");
        }
        statusValue.setText(message);
        statusValue.setTextColor(message.contains("失败") || message.contains("无法")
                || message.contains("未识别") || message.contains("未找到") ? Ui.DANGER : Ui.MUTED);
    }

    private static String string(CharSequence value) {
        return value == null ? null : value.toString();
    }

    @Override
    public void onDestroy() {
        captureInFlight = false;
        if (offlineRecognizer != null) {
            offlineRecognizer.close();
            offlineRecognizer = null;
        }
        removeOverlay();
        running = false;
        instance = null;
        super.onDestroy();
    }
}
