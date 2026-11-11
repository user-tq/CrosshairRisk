package com.crosshair.risk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int WHITE = Color.rgb(239, 244, 245);
    static final int MUTED = Color.rgb(147, 160, 166);
    static final int ACCENT = Color.rgb(85, 214, 190);
    static final int DANGER = Color.rgb(255, 113, 113);
    static final int CARD = Color.rgb(25, 31, 38);
    static final int BUTTON = Color.rgb(42, 52, 61);

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable outlined(int color, int strokeColor, float radiusDp, Context context) {
        GradientDrawable drawable = rounded(color, radiusDp, context);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    static TextView bold(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.rgb(8, 35, 31) : WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 46));
        button.setMinimumHeight(dp(context, 46));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        GradientDrawable background = rounded(primary ? ACCENT : BUTTON, 12, context);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x2255D6BE), background, background));
        return button;
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout.LayoutParams params(int width, int height, int topMargin, Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(context, topMargin);
        return params;
    }
}
