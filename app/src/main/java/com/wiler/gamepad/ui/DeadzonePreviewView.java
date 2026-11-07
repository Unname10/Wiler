package com.wiler.gamepad.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom View showing a radar-style joystick deadzone preview.
 * Renders the crosshairs, boundary circle, and interactive glowing deadzone circle.
 */
public class DeadzonePreviewView extends View {

    private float deadzonePercent = 0.10f; // 0.0 to 0.25

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deadzonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deadzoneGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DeadzonePreviewView(Context context) {
        super(context);
        init();
    }

    public DeadzonePreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DeadzonePreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#151A15"));
        bgPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(Color.parseColor("#263226"));
        linePaint.setStrokeWidth(2f);
        linePaint.setStyle(Paint.Style.STROKE);

        dashedRingPaint.setColor(Color.parseColor("#22C55E"));
        dashedRingPaint.setStyle(Paint.Style.STROKE);
        dashedRingPaint.setStrokeWidth(2f);
        dashedRingPaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));

        deadzonePaint.setColor(Color.parseColor("#22C55E"));
        deadzonePaint.setStyle(Paint.Style.FILL);

        deadzoneGlowPaint.setColor(Color.parseColor("#3322C55E"));
        deadzoneGlowPaint.setStyle(Paint.Style.FILL);
    }

    public void setDeadzonePercent(float percent) {
        this.deadzonePercent = Math.max(0.0f, Math.min(percent, 0.50f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) - 8f;

        if (maxRadius <= 0) return;

        // Background circle
        canvas.drawCircle(cx, cy, maxRadius, bgPaint);

        // Crosshairs
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, linePaint);
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, linePaint);

        // Outer ring
        canvas.drawCircle(cx, cy, maxRadius, linePaint);

        // Outer dashed guide at 75%
        canvas.drawCircle(cx, cy, maxRadius * 0.75f, dashedRingPaint);

        // Deadzone inner circle + glow
        float dzRadius = Math.max(6f, maxRadius * (deadzonePercent / 0.25f) * 0.5f);

        // Outer glow
        canvas.drawCircle(cx, cy, dzRadius + 8f, deadzoneGlowPaint);
        // Core deadzone circle
        canvas.drawCircle(cx, cy, dzRadius, deadzonePaint);
    }
}
