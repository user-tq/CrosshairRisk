package com.crosshair.risk;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshState();
            handler.postDelayed(this, 750);
        }
    };

    private TextView serviceStatus;
    private TextView quote;
    private TextView detail;
    private TextView foregroundPackage;
    private TextView diagnostics;
    private TextView headerOcr;
    private Button overlayButton;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(State.PREFS, MODE_PRIVATE);
        buildContent();
    }

    private void buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 24));

        LinearLayout content = Ui.vertical(this);
        int padding = Ui.dp(this, 22);
        content.setPadding(padding, Ui.dp(this, 30), padding, Ui.dp(this, 36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(Ui.bold(this, "十字光标 2R 助手", 28, Ui.WHITE));
        TextView subtitle = Ui.text(this, "元素 + 本地离线识别 · 无联网权限", 14, Ui.MUTED);
        content.addView(subtitle, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));

        LinearLayout statusCard = card();
        content.addView(statusCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 22, this));
        statusCard.addView(Ui.bold(this, "运行状态", 16, Ui.WHITE));
        serviceStatus = Ui.text(this, "", 14, Ui.MUTED);
        statusCard.addView(serviceStatus, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        foregroundPackage = Ui.text(this, "", 12, Ui.MUTED);
        foregroundPackage.setTextIsSelectable(true);
        statusCard.addView(foregroundPackage, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 5, this));

        Button accessibility = Ui.button(this, "1  启用无障碍读取服务", true);
        accessibility.setOnClickListener(view -> openAccessibilitySettings());
        statusCard.addView(accessibility, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));

        overlayButton = Ui.button(this, "", false);
        overlayButton.setOnClickListener(view -> sendControl(State.ACTION_TOGGLE));
        statusCard.addView(overlayButton, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        LinearLayout quoteCard = card();
        content.addView(quoteCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        quoteCard.addView(Ui.bold(this, "当前 2R 卖出价", 15, Ui.MUTED));
        quote = Ui.bold(this, "--", 34, Ui.ACCENT);
        quoteCard.addView(quote, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));
        detail = Ui.text(this, "尚未捕获完整价格", 14, Ui.WHITE);
        detail.setTextIsSelectable(true);
        quoteCard.addView(detail, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));
        TextView formula = Ui.text(this,
                "公式：卖出价 = 买入价 + 2 × (买入价 − 止损价)\n仅适用于止损价低于买入价的做多交易。",
                13, Ui.MUTED);
        quoteCard.addView(formula, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        Button clear = Ui.button(this, "清空已捕获价格", false);
        clear.setOnClickListener(view -> sendControl(State.ACTION_CLEAR));
        quoteCard.addView(clear, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        LinearLayout helpCard = card();
        content.addView(helpCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        helpCard.addView(Ui.bold(this, "使用方法", 16, Ui.WHITE));
        TextView steps = Ui.text(this,
                "1. 启用服务并显示悬浮窗。\n"
                        + "2. 打开通达信，长按 K 线调出十字光标。\n"
                        + "3. 分别点击“捕获止损”和“捕获买入”，浮窗会同时识别股票名称和代码。\n"
                        + "4. 核对两个价格后，查看实时计算的 2R 卖出价。\n"
                        + "5. 点击浮窗右上角“复制”，即可得到包含股票名称、代码、止损价、买入价和买入日期的记录。\n\n"
                        + "若提示无法识别，点击浮窗右下角“诊断”，再回本页查看元素结果。",
                14, Ui.MUTED);
        helpCard.addView(steps, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        Button copyDiagnostics = Ui.button(this, "复制完整诊断结果", false);
        copyDiagnostics.setOnClickListener(view -> copyDiagnostics());
        helpCard.addView(copyDiagnostics, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        diagnostics = Ui.text(this, "暂无诊断结果", 12, Ui.MUTED);
        diagnostics.setTextIsSelectable(true);
        helpCard.addView(diagnostics, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        headerOcr = Ui.text(this, "标题区识别：暂无", 12, Ui.MUTED);
        headerOcr.setTextIsSelectable(true);
        helpCard.addView(headerOcr, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        TextView privacy = Ui.text(this,
                "说明：优先读取页面元素；失败时，Android 11 及以上会通过无障碍截图定位左侧蓝色标签和顶部股票标题，并使用 APK 内置模型离线识别价格、股票名称与代码。截图仅在内存中处理，不保存；应用没有联网权限。请避免浮窗遮挡蓝色价格标签。",
                12, Ui.MUTED);
        content.addView(privacy, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 20, this));

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 18));
        card.setBackground(Ui.outlined(Ui.CARD, Color.rgb(43, 53, 62), 16, this));
        return card;
    }

    private void copyDiagnostics() {
        String value = preferences.getString(State.KEY_DIAGNOSTICS, "");
        String headerText = preferences.getString(State.KEY_HEADER_TEXT, "");
        if (value.isEmpty() && headerText.isEmpty()) {
            Toast.makeText(this, "请先在通达信十字光标页面执行诊断", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder report = new StringBuilder();
        if (!value.isEmpty()) {
            report.append(value);
        }
        if (!headerText.isEmpty()) {
            if (report.length() > 0) {
                report.append("\n\n");
            }
            report.append("标题区 OCR 原文：\n").append(headerText);
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("通达信页面元素诊断", report.toString()));
        Toast.makeText(this, "完整诊断结果已复制", Toast.LENGTH_SHORT).show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开系统无障碍设置，请手动进入系统设置", Toast.LENGTH_LONG).show();
        }
    }

    private void sendControl(String action) {
        Intent intent = new Intent(this, ControlReceiver.class);
        intent.setAction(action);
        sendBroadcast(intent);
        handler.postDelayed(this::refreshState, 150);
    }

    private boolean isAccessibilityEnabled() {
        ComponentName expected = new ComponentName(this, CrosshairAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        for (String component : enabled.split(":")) {
            ComponentName name = ComponentName.unflattenFromString(component);
            if (expected.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void refreshState() {
        if (serviceStatus == null) {
            return;
        }
        boolean enabled = isAccessibilityEnabled();
        boolean running = CrosshairAccessibilityService.isRunning();
        serviceStatus.setText(enabled
                ? (running ? "● 服务已连接，可以捕获" : "● 服务已授权，等待系统连接")
                : "○ 尚未启用无障碍服务");
        serviceStatus.setTextColor(enabled ? Ui.ACCENT : Ui.DANGER);

        boolean visible = preferences.getBoolean(State.KEY_VISIBLE, true);
        overlayButton.setText(visible ? "2  隐藏悬浮窗" : "2  显示悬浮窗");

        String stop = preferences.getString(State.KEY_STOP, "");
        String buy = preferences.getString(State.KEY_BUY, "");
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        String message = preferences.getString(State.KEY_MESSAGE, "尚未捕获完整价格");
        String packageName = preferences.getString(State.KEY_PACKAGE, "");
        String stockLine = name.isEmpty() && code.isEmpty()
                ? "" : name + (name.isEmpty() || code.isEmpty() ? "" : " ") + code + "\n";
        foregroundPackage.setText(packageName.isEmpty()
                ? "前台应用：等待识别" : "最近前台包名：" + packageName);

        if (!stop.isEmpty() && !buy.isEmpty()) {
            try {
                RiskCalculator.Calculation result = RiskCalculator.calculate(stop, buy);
                quote.setText(result.target);
                quote.setTextColor(Ui.ACCENT);
                detail.setText(stockLine + "止损 " + result.stop + "  ·  买入 " + result.buy
                        + "\n每股风险 " + result.risk + "  ·  2R 收益 " + result.profit
                        + "\n" + message);
            } catch (IllegalArgumentException error) {
                quote.setText("--");
                quote.setTextColor(Ui.DANGER);
                detail.setText(stockLine + "止损 " + stop + "  ·  买入 " + buy
                        + "\n" + error.getMessage());
            }
        } else {
            quote.setText("--");
            quote.setTextColor(Ui.ACCENT);
            detail.setText(stockLine + (stop.isEmpty() ? "止损 --" : "止损 " + stop)
                    + "  ·  " + (buy.isEmpty() ? "买入 --" : "买入 " + buy)
                    + "\n" + message);
        }

        String diagnosticText = preferences.getString(State.KEY_DIAGNOSTICS, "");
        diagnostics.setText(diagnosticText.isEmpty() ? "暂无诊断结果" : diagnosticText);
        String headerText = preferences.getString(State.KEY_HEADER_TEXT, "");
        headerOcr.setText(headerText.isEmpty()
                ? "标题区识别：暂无" : "标题区识别原文：\n" + headerText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
