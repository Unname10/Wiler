package com.wiler.gamepad.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.wiler.gamepad.model.InputMapper;

/**
 * A lightweight virtual joystick view used for testing analog sticks in Milestone 1.
 */
public class SimpleJoystickView extends View {

    public interface OnJoystickMoveListener {
        void onJoystickMoved(float x, float y);
    }

    private Paint basePaint;
    private Paint deadzonePaint;
    private Paint knobPaint;
    private Paint linePaint;

    private float centerX = 0;
    private float centerY = 0;
    private float baseRadius = 0;
    private float knobRadius = 0;

    private float knobX = 0;
    private float knobY = 0;

    private float deadzoneRatio = 0.10f; // 10% default
    private OnJoystickMoveListener listener;

    public SimpleJoystickView(Context context) {
        super(context);
        init();
    }

    public SimpleJoystickView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimpleJoystickView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(0xFF222222);
        basePaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF333333);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);

        deadzonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deadzonePaint.setColor(0x22107C10); // Translucent green
        deadzonePaint.setStyle(Paint.Style.FILL);

        knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        knobPaint.setColor(0xFF107C10); // Xbox Green
        knobPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnJoystickMoveListener(OnJoystickMoveListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f * 0.85f;
        knobRadius = baseRadius * 0.35f;
        knobX = centerX;
        knobY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Outer base
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius, linePaint);

        // Deadzone visual circle
        canvas.drawCircle(centerX, centerY, baseRadius * deadzoneRatio, deadzonePaint);

        // Center crosshair lines
        canvas.drawLine(centerX - 15, centerY, centerX + 15, centerY, linePaint);
        canvas.drawLine(centerX, centerY - 15, centerX, centerY + 15, linePaint);

        // Draggable Knob
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - centerX;
            float dy = event.getY() - centerY;

            // Raw normalized coordinates [-1.0, 1.0]
            float rawX = dx / baseRadius;
            float rawY = dy / baseRadius;

            // Apply smooth clamped radial deadzone
            float[] mapped = InputMapper.applyRadialDeadzone(rawX, rawY, deadzoneRatio);

            // Clamp knob visually within base bounds
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > baseRadius) {
                knobX = centerX + (dx / dist) * baseRadius;
                knobY = centerY + (dy / dist) * baseRadius;
            } else {
                knobX = event.getX();
                knobY = event.getY();
            }

            invalidate();

            if (listener != null) {
                listener.onJoystickMoved(mapped[0], mapped[1]);
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            knobX = centerX;
            knobY = centerY;
            invalidate();

            if (listener != null) {
                listener.onJoystickMoved(0.0f, 0.0f);
            }
            return true;
        }

        return super.onTouchEvent(event);
    }
}
