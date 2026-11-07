package com.wiler.gamepad.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.wiler.gamepad.model.GamepadState;
import com.wiler.gamepad.model.InputMapper;
import com.wiler.gamepad.model.PreferencesManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Ultra-low latency, custom canvas-rendered Gamepad View.
 * Matches assets/designs/Gamepad.png layout with full multi-touch support,
 * analog radial deadzone, dynamic stick repositioning, and Guide long-press quick menu.
 */
public class GamepadView extends View {

    public interface GamepadListener {
        void onInputChanged();
        void onGuideLongPress();
        void onGuideShortPress();
    }

    private GamepadState gamepadState;
    private GamepadListener listener;

    private float deadzone = 0.10f;
    private boolean dynamicJoystick = true;

    // Control Type IDs
    private static final int CONTROL_NONE = 0;
    private static final int CONTROL_STICK_LEFT = 1;
    private static final int CONTROL_STICK_RIGHT = 2;
    private static final int CONTROL_BTN_A = 3;
    private static final int CONTROL_BTN_B = 4;
    private static final int CONTROL_BTN_X = 5;
    private static final int CONTROL_BTN_Y = 6;
    private static final int CONTROL_BTN_LB = 7;
    private static final int CONTROL_BTN_RB = 8;
    private static final int CONTROL_BTN_LT = 9;
    private static final int CONTROL_BTN_RT = 10;
    private static final int CONTROL_BTN_L3 = 11;
    private static final int CONTROL_BTN_R3 = 12;
    private static final int CONTROL_BTN_BACK = 13;
    private static final int CONTROL_BTN_START = 14;
    private static final int CONTROL_BTN_GUIDE = 15;
    private static final int CONTROL_DPAD_UP = 16;
    private static final int CONTROL_DPAD_RIGHT = 17;
    private static final int CONTROL_DPAD_DOWN = 18;
    private static final int CONTROL_DPAD_LEFT = 19;

    // Multi-touch tracking
    private final Map<Integer, Integer> pointerToControl = new HashMap<>();

    // Guide button handling
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean guideLongPressTriggered = false;
    private final Runnable guideLongPressRunnable = () -> {
        guideLongPressTriggered = true;
        if (listener != null) {
            listener.onGuideLongPress();
        }
    };

    // Stick positions
    private float stickRadius = 140f;
    private float thumbRadius = 60f;

    // Left Stick coordinates
    private float leftStickBaseX, leftStickBaseY;
    private float leftStickThumbX, leftStickThumbY;
    private boolean leftStickActive = false;

    // Right Stick coordinates
    private float rightStickBaseX, rightStickBaseY;
    private float rightStickThumbX, rightStickThumbY;
    private boolean rightStickActive = false;

    // Geometry targets
    private final RectF rectLT = new RectF();
    private final RectF rectLB = new RectF();
    private final RectF rectRT = new RectF();
    private final RectF rectRB = new RectF();
    private final RectF rectL3 = new RectF();
    private final RectF rectR3 = new RectF();
    private final RectF rectBack = new RectF();
    private final RectF rectGuide = new RectF();
    private final RectF rectStart = new RectF();

    private final RectF rectDpadUp = new RectF();
    private final RectF rectDpadRight = new RectF();
    private final RectF rectDpadDown = new RectF();
    private final RectF rectDpadLeft = new RectF();

    private final RectF rectBtnA = new RectF();
    private final RectF rectBtnB = new RectF();
    private final RectF rectBtnX = new RectF();
    private final RectF rectBtnY = new RectF();

    // Paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickThumbInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GamepadView(Context context) {
        super(context);
        init();
    }

    public GamepadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamepadView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#080B08"));
        bgPaint.setStyle(Paint.Style.FILL);

        btnBasePaint.setColor(Color.parseColor("#1B201B"));
        btnBasePaint.setStyle(Paint.Style.FILL);

        btnStrokePaint.setStyle(Paint.Style.STROKE);
        btnStrokePaint.setStrokeWidth(3f);
        btnStrokePaint.setColor(Color.parseColor("#2E3B2E"));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setFakeBoldText(true);

        stickBasePaint.setColor(Color.parseColor("#111811"));
        stickBasePaint.setStyle(Paint.Style.FILL);

        stickThumbPaint.setColor(Color.parseColor("#16A34A"));
        stickThumbPaint.setStyle(Paint.Style.FILL);

        stickThumbInnerPaint.setColor(Color.parseColor("#22C55E"));
        stickThumbInnerPaint.setStyle(Paint.Style.STROKE);
        stickThumbInnerPaint.setStrokeWidth(4f);

        dashedRingPaint.setColor(Color.parseColor("#22C55E"));
        dashedRingPaint.setStyle(Paint.Style.STROKE);
        dashedRingPaint.setStrokeWidth(2f);
        dashedRingPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        PreferencesManager prefs = PreferencesManager.getInstance(getContext());
        this.deadzone = prefs.getDeadzone();
        this.dynamicJoystick = prefs.isDynamicJoystick();
    }

    public void setGamepadState(GamepadState state) {
        this.gamepadState = state;
    }

    public void setListener(GamepadListener listener) {
        this.listener = listener;
    }

    public void setDeadzone(float deadzone) {
        this.deadzone = deadzone;
        invalidate();
    }

    public void setDynamicJoystick(boolean dynamicJoystick) {
        this.dynamicJoystick = dynamicJoystick;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateLayout(w, h);
    }

    private void calculateLayout(int w, int h) {
        float btnRadius = Math.min(w, h) * 0.07f;
        float btnDiameter = btnRadius * 2f;
        stickRadius = Math.min(w, h) * 0.18f;
        thumbRadius = stickRadius * 0.45f;

        // --- Left side ---
        // LT / LB
        float topMargin = h * 0.08f;
        setCircleRect(rectLT, w * 0.08f, topMargin + btnRadius, btnRadius);
        setCircleRect(rectLB, w * 0.22f, topMargin + btnRadius, btnRadius);

        // D-Pad cluster
        float dpadCenterX = w * 0.14f;
        float dpadCenterY = h * 0.50f;
        float dpadSpacing = btnDiameter * 0.95f;
        setCircleRect(rectDpadUp, dpadCenterX, dpadCenterY - dpadSpacing, btnRadius);
        setCircleRect(rectDpadRight, dpadCenterX + dpadSpacing, dpadCenterY, btnRadius);
        setCircleRect(rectDpadDown, dpadCenterX, dpadCenterY + dpadSpacing, btnRadius);
        setCircleRect(rectDpadLeft, dpadCenterX - dpadSpacing, dpadCenterY, btnRadius);

        // Left Stick
        leftStickBaseX = w * 0.32f;
        leftStickBaseY = h * 0.72f;
        leftStickThumbX = leftStickBaseX;
        leftStickThumbY = leftStickBaseY;

        // L3 (corner)
        setCircleRect(rectL3, w * 0.07f, h * 0.88f, btnRadius * 0.85f);

        // --- Center Cluster ---
        float centerTopY = h * 0.10f;
        rectBack.set(w * 0.38f - 40f, centerTopY - 25f, w * 0.38f + 40f, centerTopY + 25f);
        setCircleRect(rectGuide, w * 0.50f, centerTopY, btnRadius * 0.85f);
        rectStart.set(w * 0.62f - 40f, centerTopY - 25f, w * 0.62f + 40f, centerTopY + 25f);

        // --- Right side ---
        // RB / RT
        setCircleRect(rectRB, w * 0.78f, topMargin + btnRadius, btnRadius);
        setCircleRect(rectRT, w * 0.92f, topMargin + btnRadius, btnRadius);

        // ABXY Diamond
        float abxyCenterX = w * 0.86f;
        float abxyCenterY = h * 0.50f;
        float abxySpacing = btnDiameter * 0.95f;
        setCircleRect(rectBtnY, abxyCenterX, abxyCenterY - abxySpacing, btnRadius);
        setCircleRect(rectBtnB, abxyCenterX + abxySpacing, abxyCenterY, btnRadius);
        setCircleRect(rectBtnA, abxyCenterX, abxyCenterY + abxySpacing, btnRadius);
        setCircleRect(rectBtnX, abxyCenterX - abxySpacing, abxyCenterY, btnRadius);

        // Right Stick
        rightStickBaseX = w * 0.68f;
        rightStickBaseY = h * 0.72f;
        rightStickThumbX = rightStickBaseX;
        rightStickThumbY = rightStickBaseY;

        // R3 (corner)
        setCircleRect(rectR3, w * 0.93f, h * 0.88f, btnRadius * 0.85f);
    }

    private void setCircleRect(RectF rect, float cx, float cy, float radius) {
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Left Top Triggers & Bumpers
        drawButton(canvas, rectLT, "LT", isPressed(CONTROL_BTN_LT), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectLB, "LB", isPressed(CONTROL_BTN_LB), Color.WHITE, Color.parseColor("#334433"));

        // D-Pad (1 = Up, 2 = Right, 3 = Down, 4 = Left)
        drawButton(canvas, rectDpadUp, "▲", isPressed(CONTROL_DPAD_UP), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectDpadRight, "▶", isPressed(CONTROL_DPAD_RIGHT), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectDpadDown, "▼", isPressed(CONTROL_DPAD_DOWN), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectDpadLeft, "◀", isPressed(CONTROL_DPAD_LEFT), Color.WHITE, Color.parseColor("#334433"));

        // L3 & R3
        drawButton(canvas, rectL3, "L3", isPressed(CONTROL_BTN_L3), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectR3, "R3", isPressed(CONTROL_BTN_R3), Color.WHITE, Color.parseColor("#334433"));

        // Center cluster: Back, Guide, Start
        drawPillButton(canvas, rectBack, "◀", isPressed(CONTROL_BTN_BACK));
        drawGuideButton(canvas, rectGuide, isPressed(CONTROL_BTN_GUIDE));
        drawPillButton(canvas, rectStart, "▶", isPressed(CONTROL_BTN_START));

        // Right Top Bumpers & Triggers
        drawButton(canvas, rectRB, "RB", isPressed(CONTROL_BTN_RB), Color.WHITE, Color.parseColor("#334433"));
        drawButton(canvas, rectRT, "RT", isPressed(CONTROL_BTN_RT), Color.WHITE, Color.parseColor("#334433"));

        // ABXY Diamond
        drawButton(canvas, rectBtnY, "Y", isPressed(CONTROL_BTN_Y), Color.parseColor("#F59E0B"), Color.parseColor("#F59E0B"));
        drawButton(canvas, rectBtnX, "X", isPressed(CONTROL_BTN_X), Color.parseColor("#3B82F6"), Color.parseColor("#3B82F6"));
        drawButton(canvas, rectBtnB, "B", isPressed(CONTROL_BTN_B), Color.parseColor("#EF4444"), Color.parseColor("#EF4444"));
        drawButton(canvas, rectBtnA, "A", isPressed(CONTROL_BTN_A), Color.parseColor("#22C55E"), Color.parseColor("#22C55E"));

        // Sticks
        drawStick(canvas, leftStickBaseX, leftStickBaseY, leftStickThumbX, leftStickThumbY);
        drawStick(canvas, rightStickBaseX, rightStickBaseY, rightStickThumbX, rightStickThumbY);
    }

    private boolean isPressed(int controlId) {
        return pointerToControl.containsValue(controlId);
    }

    private void drawButton(Canvas canvas, RectF rect, String text, boolean pressed, int textColor, int strokeColor) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float radius = rect.width() / 2f;

        // Base circle
        btnBasePaint.setColor(pressed ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawCircle(cx, cy, radius, btnBasePaint);

        // Stroke
        btnStrokePaint.setColor(pressed ? Color.parseColor("#22C55E") : strokeColor);
        canvas.drawCircle(cx, cy, radius, btnStrokePaint);

        // Text
        textPaint.setColor(textColor);
        textPaint.setTextSize(radius * 0.9f);
        float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(text, cx, textY, textPaint);
    }

    private void drawPillButton(Canvas canvas, RectF rect, String text, boolean pressed) {
        btnBasePaint.setColor(pressed ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, btnBasePaint);

        btnStrokePaint.setColor(pressed ? Color.parseColor("#22C55E") : Color.parseColor("#2E3B2E"));
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, btnStrokePaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(rect.height() * 0.55f);
        float textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(text, rect.centerX(), textY, textPaint);
    }

    private void drawGuideButton(Canvas canvas, RectF rect, boolean pressed) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float radius = rect.width() / 2f;

        btnBasePaint.setColor(pressed ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawCircle(cx, cy, radius, btnBasePaint);

        btnStrokePaint.setColor(pressed ? Color.parseColor("#22C55E") : Color.parseColor("#2E3B2E"));
        canvas.drawCircle(cx, cy, radius, btnStrokePaint);

        // Sun / Xbox icon glyph
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(radius * 1.0f);
        float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText("☼", cx, textY, textPaint);
    }

    private void drawStick(Canvas canvas, float baseX, float baseY, float thumbX, float thumbY) {
        // Outer base
        canvas.drawCircle(baseX, baseY, stickRadius, stickBasePaint);

        // Outer boundary stroke
        btnStrokePaint.setColor(Color.parseColor("#263826"));
        canvas.drawCircle(baseX, baseY, stickRadius, btnStrokePaint);

        // Deadzone dashed guide
        float dzRadius = Math.max(12f, stickRadius * deadzone);
        canvas.drawCircle(baseX, baseY, dzRadius, dashedRingPaint);

        // Thumb inner circle
        canvas.drawCircle(thumbX, thumbY, thumbRadius, stickThumbPaint);
        canvas.drawCircle(thumbX, thumbY, thumbRadius * 0.55f, stickThumbInnerPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerId = event.getPointerId(actionIndex);
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);
                handlePointerDown(pointerId, x, y);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pointerId = event.getPointerId(i);
                    float x = event.getX(i);
                    float y = event.getY(i);
                    handlePointerMove(pointerId, x, y);
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(actionIndex);
                handlePointerUp(pointerId);
                break;
            }

            case MotionEvent.ACTION_UP: {
                int pointerId = event.getPointerId(0);
                handlePointerUp(pointerId);
                resetAll();
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                resetAll();
                break;
            }
        }

        invalidate();
        return true;
    }

    private void handlePointerDown(int pointerId, float x, float y) {
        // Hit test buttons first
        int control = hitTestButtons(x, y);

        if (control == CONTROL_NONE) {
            // Check sticks
            if (x < getWidth() * 0.5f) {
                // Left half -> Left stick
                control = CONTROL_STICK_LEFT;
                leftStickActive = true;
                if (dynamicJoystick) {
                    leftStickBaseX = x;
                    leftStickBaseY = y;
                }
                updateStick(true, x, y);
            } else {
                // Right half -> Right stick
                control = CONTROL_STICK_RIGHT;
                rightStickActive = true;
                if (dynamicJoystick) {
                    rightStickBaseX = x;
                    rightStickBaseY = y;
                }
                updateStick(false, x, y);
            }
        } else {
            activateControl(control);
        }

        pointerToControl.put(pointerId, control);
    }

    private void handlePointerMove(int pointerId, float x, float y) {
        Integer control = pointerToControl.get(pointerId);
        if (control == null) return;

        if (control == CONTROL_STICK_LEFT) {
            updateStick(true, x, y);
        } else if (control == CONTROL_STICK_RIGHT) {
            updateStick(false, x, y);
        }
    }

    private void handlePointerUp(int pointerId) {
        Integer control = pointerToControl.remove(pointerId);
        if (control != null) {
            deactivateControl(control);
        }
    }

    private int hitTestButtons(float x, float y) {
        if (rectLT.contains(x, y)) return CONTROL_BTN_LT;
        if (rectLB.contains(x, y)) return CONTROL_BTN_LB;
        if (rectRT.contains(x, y)) return CONTROL_BTN_RT;
        if (rectRB.contains(x, y)) return CONTROL_BTN_RB;

        if (rectDpadUp.contains(x, y)) return CONTROL_DPAD_UP;
        if (rectDpadRight.contains(x, y)) return CONTROL_DPAD_RIGHT;
        if (rectDpadDown.contains(x, y)) return CONTROL_DPAD_DOWN;
        if (rectDpadLeft.contains(x, y)) return CONTROL_DPAD_LEFT;

        if (rectBtnY.contains(x, y)) return CONTROL_BTN_Y;
        if (rectBtnX.contains(x, y)) return CONTROL_BTN_X;
        if (rectBtnB.contains(x, y)) return CONTROL_BTN_B;
        if (rectBtnA.contains(x, y)) return CONTROL_BTN_A;

        if (rectL3.contains(x, y)) return CONTROL_BTN_L3;
        if (rectR3.contains(x, y)) return CONTROL_BTN_R3;

        if (rectBack.contains(x, y)) return CONTROL_BTN_BACK;
        if (rectGuide.contains(x, y)) return CONTROL_BTN_GUIDE;
        if (rectStart.contains(x, y)) return CONTROL_BTN_START;

        return CONTROL_NONE;
    }

    private void activateControl(int control) {
        if (gamepadState == null) return;

        switch (control) {
            case CONTROL_BTN_A: gamepadState.setButton(GamepadState.BTN_A, true); break;
            case CONTROL_BTN_B: gamepadState.setButton(GamepadState.BTN_B, true); break;
            case CONTROL_BTN_X: gamepadState.setButton(GamepadState.BTN_X, true); break;
            case CONTROL_BTN_Y: gamepadState.setButton(GamepadState.BTN_Y, true); break;
            case CONTROL_BTN_LB: gamepadState.setButton(GamepadState.BTN_LB, true); break;
            case CONTROL_BTN_RB: gamepadState.setButton(GamepadState.BTN_RB, true); break;
            case CONTROL_BTN_LT:
                gamepadState.setButton(GamepadState.BTN_LT_BTN, true);
                gamepadState.setTriggers(1.0f, gamepadState.getTriggerRT());
                break;
            case CONTROL_BTN_RT:
                gamepadState.setButton(GamepadState.BTN_RT_BTN, true);
                gamepadState.setTriggers(gamepadState.getTriggerLT(), 1.0f);
                break;
            case CONTROL_BTN_L3: gamepadState.setButton(GamepadState.BTN_LS_CLICK, true); break;
            case CONTROL_BTN_R3: gamepadState.setButton(GamepadState.BTN_RS_CLICK, true); break;
            case CONTROL_BTN_BACK: gamepadState.setButton(GamepadState.BTN_VIEW_BACK, true); break;
            case CONTROL_BTN_START: gamepadState.setButton(GamepadState.BTN_MENU_START, true); break;

            case CONTROL_BTN_GUIDE:
                // Start 1s timer for Guide long-press
                guideLongPressTriggered = false;
                mainHandler.removeCallbacks(guideLongPressRunnable);
                mainHandler.postDelayed(guideLongPressRunnable, 1000L);
                break;

            case CONTROL_DPAD_UP: gamepadState.setHat(GamepadState.HAT_UP); break;
            case CONTROL_DPAD_RIGHT: gamepadState.setHat(GamepadState.HAT_RIGHT); break;
            case CONTROL_DPAD_DOWN: gamepadState.setHat(GamepadState.HAT_DOWN); break;
            case CONTROL_DPAD_LEFT: gamepadState.setHat(GamepadState.HAT_LEFT); break;
        }

        if (control != CONTROL_BTN_GUIDE && listener != null) {
            listener.onInputChanged();
        }
    }

    private void deactivateControl(int control) {
        if (gamepadState == null) return;

        switch (control) {
            case CONTROL_BTN_A: gamepadState.setButton(GamepadState.BTN_A, false); break;
            case CONTROL_BTN_B: gamepadState.setButton(GamepadState.BTN_B, false); break;
            case CONTROL_BTN_X: gamepadState.setButton(GamepadState.BTN_X, false); break;
            case CONTROL_BTN_Y: gamepadState.setButton(GamepadState.BTN_Y, false); break;
            case CONTROL_BTN_LB: gamepadState.setButton(GamepadState.BTN_LB, false); break;
            case CONTROL_BTN_RB: gamepadState.setButton(GamepadState.BTN_RB, false); break;
            case CONTROL_BTN_LT:
                gamepadState.setButton(GamepadState.BTN_LT_BTN, false);
                gamepadState.setTriggers(0.0f, gamepadState.getTriggerRT());
                break;
            case CONTROL_BTN_RT:
                gamepadState.setButton(GamepadState.BTN_RT_BTN, false);
                gamepadState.setTriggers(gamepadState.getTriggerLT(), 0.0f);
                break;
            case CONTROL_BTN_L3: gamepadState.setButton(GamepadState.BTN_LS_CLICK, false); break;
            case CONTROL_BTN_R3: gamepadState.setButton(GamepadState.BTN_RS_CLICK, false); break;
            case CONTROL_BTN_BACK: gamepadState.setButton(GamepadState.BTN_VIEW_BACK, false); break;
            case CONTROL_BTN_START: gamepadState.setButton(GamepadState.BTN_MENU_START, false); break;

            case CONTROL_BTN_GUIDE:
                mainHandler.removeCallbacks(guideLongPressRunnable);
                if (!guideLongPressTriggered) {
                    // Short tap (<1s) -> Send Guide button press & release
                    if (listener != null) {
                        listener.onGuideShortPress();
                    }
                }
                break;

            case CONTROL_DPAD_UP:
            case CONTROL_DPAD_RIGHT:
            case CONTROL_DPAD_DOWN:
            case CONTROL_DPAD_LEFT:
                gamepadState.setHat(GamepadState.HAT_CENTER);
                break;

            case CONTROL_STICK_LEFT:
                leftStickActive = false;
                leftStickThumbX = leftStickBaseX;
                leftStickThumbY = leftStickBaseY;
                gamepadState.setLeftStick(0f, 0f);
                break;

            case CONTROL_STICK_RIGHT:
                rightStickActive = false;
                rightStickThumbX = rightStickBaseX;
                rightStickThumbY = rightStickBaseY;
                gamepadState.setRightStick(0f, 0f);
                break;
        }

        if (listener != null) {
            listener.onInputChanged();
        }
    }

    private void updateStick(boolean isLeft, float touchX, float touchY) {
        float baseX = isLeft ? leftStickBaseX : rightStickBaseX;
        float baseY = isLeft ? leftStickBaseY : rightStickBaseY;

        float dx = touchX - baseX;
        float dy = touchY - baseY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Clamp visual thumb position to stick circle
        if (distance > stickRadius) {
            float scale = stickRadius / distance;
            dx *= scale;
            dy *= scale;
        }

        if (isLeft) {
            leftStickThumbX = baseX + dx;
            leftStickThumbY = baseY + dy;
        } else {
            rightStickThumbX = baseX + dx;
            rightStickThumbY = baseY + dy;
        }

        // Normalize to -1.0 .. 1.0
        float normX = dx / stickRadius;
        float normY = dy / stickRadius;

        // Apply clamped radial deadzone algorithm
        float[] mapped = InputMapper.applyRadialDeadzone(normX, normY, deadzone);

        if (gamepadState != null) {
            if (isLeft) {
                gamepadState.setLeftStick(mapped[0], mapped[1]);
            } else {
                gamepadState.setRightStick(mapped[0], mapped[1]);
            }
        }

        if (listener != null) {
            listener.onInputChanged();
        }
    }

    public void resetAll() {
        mainHandler.removeCallbacks(guideLongPressRunnable);
        pointerToControl.clear();
        leftStickActive = false;
        rightStickActive = false;
        leftStickThumbX = leftStickBaseX;
        leftStickThumbY = leftStickBaseY;
        rightStickThumbX = rightStickBaseX;
        rightStickThumbY = rightStickBaseY;

        if (gamepadState != null) {
            gamepadState.resetAll();
        }
        if (listener != null) {
            listener.onInputChanged();
        }
        invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            resetAll();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetAll();
    }
}
