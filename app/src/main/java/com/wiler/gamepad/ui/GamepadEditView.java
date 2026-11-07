package com.wiler.gamepad.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.wiler.gamepad.R;
import com.wiler.gamepad.model.GamepadLayoutConfig;

/**
 * Interactive Canvas View for visualizing and modifying the Gamepad layout:
 * - Drag-and-drop to position buttons and sticks
 * - Visual selection highlight box with dashed accent stroke
 * - Alignment grid background
 * - Supports cluster locking/unlocking and cluster spacing
 */
public class GamepadEditView extends View {

    public interface EditListener {
        void onItemSelected(int itemId, String displayName, float currentScale, float currentSpacing, boolean isClusterLocked, boolean isCluster);
        void onItemMoved();
    }

    public static final int ITEM_NONE = 0;
    public static final int ITEM_STICK_LEFT = 1;
    public static final int ITEM_STICK_RIGHT = 2;
    public static final int ITEM_DPAD_CLUSTER = 3;
    public static final int ITEM_DPAD_UP = 4;
    public static final int ITEM_DPAD_RIGHT = 5;
    public static final int ITEM_DPAD_DOWN = 6;
    public static final int ITEM_DPAD_LEFT = 7;
    public static final int ITEM_ABXY_CLUSTER = 8;
    public static final int ITEM_BTN_Y = 9;
    public static final int ITEM_BTN_B = 10;
    public static final int ITEM_BTN_A = 11;
    public static final int ITEM_BTN_X = 12;
    public static final int ITEM_BTN_LT = 13;
    public static final int ITEM_BTN_LB = 14;
    public static final int ITEM_BTN_RB = 15;
    public static final int ITEM_BTN_RT = 16;
    public static final int ITEM_BTN_BACK = 17;
    public static final int ITEM_BTN_GUIDE = 18;
    public static final int ITEM_BTN_START = 19;
    public static final int ITEM_BTN_L3 = 20;
    public static final int ITEM_BTN_R3 = 21;

    private GamepadLayoutConfig layoutConfig = new GamepadLayoutConfig();
    private EditListener listener;

    private int selectedItemId = ITEM_NONE;
    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
    private boolean previewMode = false;

    // Paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Drawable icSettings;

    // Temporary rects for drawing and hit testing
    private final RectF tempRect = new RectF();
    private final RectF highlightRect = new RectF();

    public GamepadEditView(Context context) {
        super(context);
        init();
    }

    public GamepadEditView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamepadEditView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#080B08"));
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(Color.parseColor("#152015"));
        gridPaint.setStrokeWidth(1.5f);

        btnBasePaint.setColor(Color.parseColor("#1B201B"));
        btnBasePaint.setStyle(Paint.Style.FILL);

        btnStrokePaint.setStyle(Paint.Style.STROKE);
        btnStrokePaint.setStrokeWidth(3f);
        btnStrokePaint.setColor(Color.parseColor("#2E3B2E"));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);

        stickBasePaint.setColor(Color.parseColor("#111811"));
        stickBasePaint.setStyle(Paint.Style.FILL);

        stickThumbPaint.setColor(Color.parseColor("#16A34A"));
        stickThumbPaint.setStyle(Paint.Style.FILL);

        dashedRingPaint.setColor(Color.parseColor("#22C55E"));
        dashedRingPaint.setStyle(Paint.Style.STROKE);
        dashedRingPaint.setStrokeWidth(2f);
        dashedRingPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(3.5f);
        highlightPaint.setColor(Color.parseColor("#22C55E"));
        highlightPaint.setPathEffect(new DashPathEffect(new float[]{12, 8}, 0));

        icSettings = ContextCompat.getDrawable(getContext(), R.drawable.ic_settings);
    }

    public void setEditListener(EditListener listener) {
        this.listener = listener;
    }

    public void setLayoutConfig(GamepadLayoutConfig config) {
        if (config != null) {
            this.layoutConfig = config.copy();
            syncClusters();
            invalidate();
        }
    }

    public GamepadLayoutConfig getLayoutConfig() {
        return layoutConfig;
    }

    public int getSelectedItemId() {
        return selectedItemId;
    }

    public void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        invalidate();
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    private void syncClusters() {
        float aspect = getWidth() > 0 && getHeight() > 0 ? (float) getWidth() / getHeight() : 2.0f;
        layoutConfig.syncDpadCluster(aspect);
        layoutConfig.syncAbxyCluster(aspect);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        syncClusters();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Background & alignment grid
        canvas.drawRect(0, 0, w, h, bgPaint);
        if (!previewMode) {
            drawGrid(canvas, w, h);
        }

        float baseRadius = Math.min(w, h) * 0.075f;
        float baseStickRadius = Math.min(w, h) * 0.17f;

        // 2. Sticks
        float lStickR = baseStickRadius * layoutConfig.leftStick.scale;
        drawStick(canvas, layoutConfig.leftStick.normX * w, layoutConfig.leftStick.normY * h, lStickR, "L-Stick", !previewMode && selectedItemId == ITEM_STICK_LEFT);

        float rStickR = baseStickRadius * layoutConfig.rightStick.scale;
        drawStick(canvas, layoutConfig.rightStick.normX * w, layoutConfig.rightStick.normY * h, rStickR, "R-Stick", !previewMode && selectedItemId == ITEM_STICK_RIGHT);

        // 3. Shoulder buttons
        drawCircleButton(canvas, layoutConfig.btnLT, w, h, baseRadius, "LT", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_LT);
        drawCircleButton(canvas, layoutConfig.btnLB, w, h, baseRadius, "LB", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_LB);
        drawCircleButton(canvas, layoutConfig.btnRB, w, h, baseRadius, "RB", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_RB);
        drawCircleButton(canvas, layoutConfig.btnRT, w, h, baseRadius, "RT", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_RT);

        // 4. Center Cluster
        drawPillButton(canvas, layoutConfig.btnBack, w, h, baseRadius, "◀", !previewMode && selectedItemId == ITEM_BTN_BACK);
        drawGuideButton(canvas, layoutConfig.btnGuide, w, h, baseRadius, !previewMode && selectedItemId == ITEM_BTN_GUIDE);
        drawPillButton(canvas, layoutConfig.btnStart, w, h, baseRadius, "▶", !previewMode && selectedItemId == ITEM_BTN_START);

        // 5. Corners L3, R3
        drawCircleButton(canvas, layoutConfig.btnL3, w, h, baseRadius * 0.85f, "L3", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_L3);
        drawCircleButton(canvas, layoutConfig.btnR3, w, h, baseRadius * 0.85f, "R3", Color.WHITE, !previewMode && selectedItemId == ITEM_BTN_R3);

        // 6. D-Pad (Up, Right, Down, Left)
        drawCircleButton(canvas, layoutConfig.dpadUp, w, h, baseRadius, "▲", Color.WHITE, !previewMode && selectedItemId == ITEM_DPAD_UP);
        drawCircleButton(canvas, layoutConfig.dpadRight, w, h, baseRadius, "▶", Color.WHITE, !previewMode && selectedItemId == ITEM_DPAD_RIGHT);
        drawCircleButton(canvas, layoutConfig.dpadDown, w, h, baseRadius, "▼", Color.WHITE, !previewMode && selectedItemId == ITEM_DPAD_DOWN);
        drawCircleButton(canvas, layoutConfig.dpadLeft, w, h, baseRadius, "◀", Color.WHITE, !previewMode && selectedItemId == ITEM_DPAD_LEFT);

        // If Dpad cluster locked and selected, draw bounding highlight
        if (!previewMode && selectedItemId == ITEM_DPAD_CLUSTER) {
            drawClusterBoundingBox(canvas, w, h, baseRadius,
                    layoutConfig.dpadLeft, layoutConfig.dpadRight, layoutConfig.dpadUp, layoutConfig.dpadDown, "Cụm D-Pad");
        }

        // 7. ABXY Diamond (Y, B, A, X)
        drawCircleButton(canvas, layoutConfig.btnY, w, h, baseRadius, "Y", Color.parseColor("#F59E0B"), !previewMode && selectedItemId == ITEM_BTN_Y);
        drawCircleButton(canvas, layoutConfig.btnB, w, h, baseRadius, "B", Color.parseColor("#EF4444"), !previewMode && selectedItemId == ITEM_BTN_B);
        drawCircleButton(canvas, layoutConfig.btnA, w, h, baseRadius, "A", Color.parseColor("#22C55E"), !previewMode && selectedItemId == ITEM_BTN_A);
        drawCircleButton(canvas, layoutConfig.btnX, w, h, baseRadius, "X", Color.parseColor("#3B82F6"), !previewMode && selectedItemId == ITEM_BTN_X);

        // If ABXY cluster locked and selected, draw bounding highlight
        if (!previewMode && selectedItemId == ITEM_ABXY_CLUSTER) {
            drawClusterBoundingBox(canvas, w, h, baseRadius,
                    layoutConfig.btnX, layoutConfig.btnB, layoutConfig.btnY, layoutConfig.btnA, "Cụm ABXY");
        }
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        int step = Math.round(Math.min(w, h) * 0.08f);
        for (int x = step; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = step; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }
    }

    private void drawStick(Canvas canvas, float cx, float cy, float radius, String label, boolean selected) {
        canvas.drawCircle(cx, cy, radius, stickBasePaint);
        btnStrokePaint.setColor(selected ? Color.parseColor("#22C55E") : Color.parseColor("#263826"));
        btnStrokePaint.setStrokeWidth(selected ? 5f : 3f);
        canvas.drawCircle(cx, cy, radius, btnStrokePaint);

        // Thumb inner
        canvas.drawCircle(cx, cy, radius * 0.45f, stickThumbPaint);

        textPaint.setColor(Color.parseColor("#A0CFA0"));
        textPaint.setTextSize(radius * 0.28f);
        canvas.drawText(label, cx, cy + (textPaint.getTextSize() / 3f), textPaint);

        if (selected) {
            highlightRect.set(cx - radius - 16, cy - radius - 16, cx + radius + 16, cy + radius + 16);
            canvas.drawRoundRect(highlightRect, 20, 20, highlightPaint);
        }
    }

    private void drawCircleButton(Canvas canvas, GamepadLayoutConfig.Element el, int w, int h, float baseR, String text, int textColor, boolean selected) {
        float cx = el.normX * w;
        float cy = el.normY * h;
        float r = baseR * el.scale;

        btnBasePaint.setColor(selected ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawCircle(cx, cy, r, btnBasePaint);

        btnStrokePaint.setColor(selected ? Color.parseColor("#22C55E") : Color.parseColor("#2E3B2E"));
        btnStrokePaint.setStrokeWidth(selected ? 5f : 3f);
        canvas.drawCircle(cx, cy, r, btnStrokePaint);

        textPaint.setColor(textColor);
        textPaint.setTextSize(r * 0.5f);
        float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(text, cx, textY, textPaint);

        if (selected) {
            highlightRect.set(cx - r - 12, cy - r - 12, cx + r + 12, cy + r + 12);
            canvas.drawRoundRect(highlightRect, 16, 16, highlightPaint);
        }
    }

    private void drawPillButton(Canvas canvas, GamepadLayoutConfig.Element el, int w, int h, float baseR, String text, boolean selected) {
        float cx = el.normX * w;
        float cy = el.normY * h;
        float halfW = Math.max(52f, baseR * 0.95f) * el.scale;
        float halfH = Math.max(34f, baseR * 0.55f) * el.scale;

        tempRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);

        btnBasePaint.setColor(selected ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawRoundRect(tempRect, halfH, halfH, btnBasePaint);

        btnStrokePaint.setColor(selected ? Color.parseColor("#22C55E") : Color.parseColor("#2E3B2E"));
        btnStrokePaint.setStrokeWidth(selected ? 5f : 3f);
        canvas.drawRoundRect(tempRect, halfH, halfH, btnStrokePaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(tempRect.height() * 0.52f);
        float textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2);
        canvas.drawText(text, cx, textY, textPaint);

        if (selected) {
            highlightRect.set(tempRect.left - 10, tempRect.top - 10, tempRect.right + 10, tempRect.bottom + 10);
            canvas.drawRoundRect(highlightRect, 16, 16, highlightPaint);
        }
    }

    private void drawGuideButton(Canvas canvas, GamepadLayoutConfig.Element el, int w, int h, float baseR, boolean selected) {
        float cx = el.normX * w;
        float cy = el.normY * h;
        float r = baseR * 0.95f * el.scale;

        btnBasePaint.setColor(selected ? Color.parseColor("#2E442E") : Color.parseColor("#171D17"));
        canvas.drawCircle(cx, cy, r, btnBasePaint);

        btnStrokePaint.setColor(selected ? Color.parseColor("#22C55E") : Color.parseColor("#2E3B2E"));
        btnStrokePaint.setStrokeWidth(selected ? 5f : 3f);
        canvas.drawCircle(cx, cy, r, btnStrokePaint);

        if (icSettings != null) {
            float iconSize = r * 1.05f;
            int half = Math.round(iconSize / 2f);
            int icx = Math.round(cx);
            int icy = Math.round(cy);
            icSettings.setBounds(icx - half, icy - half, icx + half, icy + half);
            icSettings.setTint(Color.WHITE);
            icSettings.draw(canvas);
        }

        if (selected) {
            highlightRect.set(cx - r - 12, cy - r - 12, cx + r + 12, cy + r + 12);
            canvas.drawRoundRect(highlightRect, 16, 16, highlightPaint);
        }
    }

    private void drawClusterBoundingBox(Canvas canvas, int w, int h, float baseR,
                                        GamepadLayoutConfig.Element leftEl, GamepadLayoutConfig.Element rightEl,
                                        GamepadLayoutConfig.Element topEl, GamepadLayoutConfig.Element bottomEl,
                                        String clusterName) {
        float left = (leftEl.normX * w) - (baseR * leftEl.scale) - 20;
        float right = (rightEl.normX * w) + (baseR * rightEl.scale) + 20;
        float top = (topEl.normY * h) - (baseR * topEl.scale) - 20;
        float bottom = (bottomEl.normY * h) + (baseR * bottomEl.scale) + 20;

        highlightRect.set(left, top, right, bottom);
        canvas.drawRoundRect(highlightRect, 24, 24, highlightPaint);

        // Draw cluster label badge
        textPaint.setColor(Color.parseColor("#22C55E"));
        textPaint.setTextSize(26f);
        canvas.drawText("🔒 " + clusterName, highlightRect.centerX(), highlightRect.top - 12, textPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (previewMode) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = x;
                lastTouchY = y;
                int hitItem = hitTest(x, y, w, h);
                if (hitItem != ITEM_NONE) {
                    selectedItemId = hitItem;
                    isDragging = true;
                    notifySelection();
                    invalidate();
                } else {
                    // Clicked empty area -> Deselect
                    selectedItemId = ITEM_NONE;
                    notifySelection();
                    invalidate();
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isDragging && selectedItemId != ITEM_NONE) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    lastTouchX = x;
                    lastTouchY = y;

                    moveSelectedItem(dx / w, dy / h);
                    if (listener != null) listener.onItemMoved();
                    invalidate();
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                isDragging = false;
                break;
            }
        }
        return true;
    }

    private void moveSelectedItem(float dNormX, float dNormY) {
        switch (selectedItemId) {
            case ITEM_STICK_LEFT:
                layoutConfig.leftStick.normX = clampNorm(layoutConfig.leftStick.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.leftStick.normY = clampNorm(layoutConfig.leftStick.normY + dNormY, 0.08f, 0.92f);
                break;
            case ITEM_STICK_RIGHT:
                layoutConfig.rightStick.normX = clampNorm(layoutConfig.rightStick.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.rightStick.normY = clampNorm(layoutConfig.rightStick.normY + dNormY, 0.08f, 0.92f);
                break;

            case ITEM_DPAD_CLUSTER:
                layoutConfig.dpadCenter.normX = clampNorm(layoutConfig.dpadCenter.normX + dNormX, 0.08f, 0.92f);
                layoutConfig.dpadCenter.normY = clampNorm(layoutConfig.dpadCenter.normY + dNormY, 0.12f, 0.88f);
                syncClusters();
                break;
            case ITEM_DPAD_UP:
                layoutConfig.dpadUp.normX = clampNorm(layoutConfig.dpadUp.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.dpadUp.normY = clampNorm(layoutConfig.dpadUp.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_DPAD_RIGHT:
                layoutConfig.dpadRight.normX = clampNorm(layoutConfig.dpadRight.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.dpadRight.normY = clampNorm(layoutConfig.dpadRight.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_DPAD_DOWN:
                layoutConfig.dpadDown.normX = clampNorm(layoutConfig.dpadDown.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.dpadDown.normY = clampNorm(layoutConfig.dpadDown.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_DPAD_LEFT:
                layoutConfig.dpadLeft.normX = clampNorm(layoutConfig.dpadLeft.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.dpadLeft.normY = clampNorm(layoutConfig.dpadLeft.normY + dNormY, 0.05f, 0.95f);
                break;

            case ITEM_ABXY_CLUSTER:
                layoutConfig.abxyCenter.normX = clampNorm(layoutConfig.abxyCenter.normX + dNormX, 0.08f, 0.92f);
                layoutConfig.abxyCenter.normY = clampNorm(layoutConfig.abxyCenter.normY + dNormY, 0.12f, 0.88f);
                syncClusters();
                break;
            case ITEM_BTN_Y:
                layoutConfig.btnY.normX = clampNorm(layoutConfig.btnY.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnY.normY = clampNorm(layoutConfig.btnY.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_B:
                layoutConfig.btnB.normX = clampNorm(layoutConfig.btnB.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnB.normY = clampNorm(layoutConfig.btnB.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_A:
                layoutConfig.btnA.normX = clampNorm(layoutConfig.btnA.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnA.normY = clampNorm(layoutConfig.btnA.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_X:
                layoutConfig.btnX.normX = clampNorm(layoutConfig.btnX.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnX.normY = clampNorm(layoutConfig.btnX.normY + dNormY, 0.05f, 0.95f);
                break;

            case ITEM_BTN_LT:
                layoutConfig.btnLT.normX = clampNorm(layoutConfig.btnLT.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnLT.normY = clampNorm(layoutConfig.btnLT.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_LB:
                layoutConfig.btnLB.normX = clampNorm(layoutConfig.btnLB.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnLB.normY = clampNorm(layoutConfig.btnLB.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_RB:
                layoutConfig.btnRB.normX = clampNorm(layoutConfig.btnRB.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnRB.normY = clampNorm(layoutConfig.btnRB.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_RT:
                layoutConfig.btnRT.normX = clampNorm(layoutConfig.btnRT.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnRT.normY = clampNorm(layoutConfig.btnRT.normY + dNormY, 0.05f, 0.95f);
                break;

            case ITEM_BTN_BACK:
                layoutConfig.btnBack.normX = clampNorm(layoutConfig.btnBack.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnBack.normY = clampNorm(layoutConfig.btnBack.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_GUIDE:
                layoutConfig.btnGuide.normX = clampNorm(layoutConfig.btnGuide.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnGuide.normY = clampNorm(layoutConfig.btnGuide.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_START:
                layoutConfig.btnStart.normX = clampNorm(layoutConfig.btnStart.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnStart.normY = clampNorm(layoutConfig.btnStart.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_L3:
                layoutConfig.btnL3.normX = clampNorm(layoutConfig.btnL3.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnL3.normY = clampNorm(layoutConfig.btnL3.normY + dNormY, 0.05f, 0.95f);
                break;
            case ITEM_BTN_R3:
                layoutConfig.btnR3.normX = clampNorm(layoutConfig.btnR3.normX + dNormX, 0.05f, 0.95f);
                layoutConfig.btnR3.normY = clampNorm(layoutConfig.btnR3.normY + dNormY, 0.05f, 0.95f);
                break;
        }
    }

    private float clampNorm(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

    private int hitTest(float x, float y, int w, int h) {
        float baseR = Math.min(w, h) * 0.075f;
        float baseStickR = Math.min(w, h) * 0.17f;

        // Check ABXY Cluster first if locked
        if (layoutConfig.abxyLocked) {
            float distCenter = (float) Math.hypot(x - layoutConfig.abxyCenter.normX * w, y - layoutConfig.abxyCenter.normY * h);
            if (distCenter <= baseR * layoutConfig.abxyCenter.scale * 2.5f) {
                return ITEM_ABXY_CLUSTER;
            }
        } else {
            if (isHitCircle(x, y, layoutConfig.btnY, w, h, baseR)) return ITEM_BTN_Y;
            if (isHitCircle(x, y, layoutConfig.btnB, w, h, baseR)) return ITEM_BTN_B;
            if (isHitCircle(x, y, layoutConfig.btnA, w, h, baseR)) return ITEM_BTN_A;
            if (isHitCircle(x, y, layoutConfig.btnX, w, h, baseR)) return ITEM_BTN_X;
        }

        // Check D-Pad Cluster if locked
        if (layoutConfig.dpadLocked) {
            float distCenter = (float) Math.hypot(x - layoutConfig.dpadCenter.normX * w, y - layoutConfig.dpadCenter.normY * h);
            if (distCenter <= baseR * layoutConfig.dpadCenter.scale * 2.5f) {
                return ITEM_DPAD_CLUSTER;
            }
        } else {
            if (isHitCircle(x, y, layoutConfig.dpadUp, w, h, baseR)) return ITEM_DPAD_UP;
            if (isHitCircle(x, y, layoutConfig.dpadRight, w, h, baseR)) return ITEM_DPAD_RIGHT;
            if (isHitCircle(x, y, layoutConfig.dpadDown, w, h, baseR)) return ITEM_DPAD_DOWN;
            if (isHitCircle(x, y, layoutConfig.dpadLeft, w, h, baseR)) return ITEM_DPAD_LEFT;
        }

        // Center cluster
        if (isHitCircle(x, y, layoutConfig.btnGuide, w, h, baseR * 0.95f)) return ITEM_BTN_GUIDE;
        if (isHitPill(x, y, layoutConfig.btnBack, w, h, baseR)) return ITEM_BTN_BACK;
        if (isHitPill(x, y, layoutConfig.btnStart, w, h, baseR)) return ITEM_BTN_START;

        // Shoulder buttons
        if (isHitCircle(x, y, layoutConfig.btnLT, w, h, baseR)) return ITEM_BTN_LT;
        if (isHitCircle(x, y, layoutConfig.btnLB, w, h, baseR)) return ITEM_BTN_LB;
        if (isHitCircle(x, y, layoutConfig.btnRB, w, h, baseR)) return ITEM_BTN_RB;
        if (isHitCircle(x, y, layoutConfig.btnRT, w, h, baseR)) return ITEM_BTN_RT;

        // Corners
        if (isHitCircle(x, y, layoutConfig.btnL3, w, h, baseR * 0.85f)) return ITEM_BTN_L3;
        if (isHitCircle(x, y, layoutConfig.btnR3, w, h, baseR * 0.85f)) return ITEM_BTN_R3;

        // Sticks
        float distLeftStick = (float) Math.hypot(x - layoutConfig.leftStick.normX * w, y - layoutConfig.leftStick.normY * h);
        if (distLeftStick <= baseStickR * layoutConfig.leftStick.scale) return ITEM_STICK_LEFT;

        float distRightStick = (float) Math.hypot(x - layoutConfig.rightStick.normX * w, y - layoutConfig.rightStick.normY * h);
        if (distRightStick <= baseStickR * layoutConfig.rightStick.scale) return ITEM_STICK_RIGHT;

        return ITEM_NONE;
    }

    private boolean isHitCircle(float x, float y, GamepadLayoutConfig.Element el, int w, int h, float baseR) {
        float cx = el.normX * w;
        float cy = el.normY * h;
        float r = baseR * el.scale * 1.15f; // Extra margin for touch detection
        float dist = (float) Math.hypot(x - cx, y - cy);
        return dist <= r;
    }

    private boolean isHitPill(float x, float y, GamepadLayoutConfig.Element el, int w, int h, float baseR) {
        float cx = el.normX * w;
        float cy = el.normY * h;
        float halfW = Math.max(52f, baseR * 0.95f) * el.scale * 1.15f;
        float halfH = Math.max(34f, baseR * 0.55f) * el.scale * 1.15f;
        return (x >= cx - halfW && x <= cx + halfW && y >= cy - halfH && y <= cy + halfH);
    }

    public void setSelectedScale(float scale) {
        GamepadLayoutConfig.Element el = getSelectedElement();
        if (el != null) {
            el.scale = Math.max(0.6f, Math.min(scale, 1.6f));
            syncClusters();
            invalidate();
        }
    }

    public void setSelectedSpacing(float spacing) {
        if (selectedItemId == ITEM_DPAD_CLUSTER) {
            layoutConfig.dpadSpacing = Math.max(0.6f, Math.min(spacing, 1.6f));
            syncClusters();
            invalidate();
        } else if (selectedItemId == ITEM_ABXY_CLUSTER) {
            layoutConfig.abxySpacing = Math.max(0.6f, Math.min(spacing, 1.6f));
            syncClusters();
            invalidate();
        }
    }

    public void toggleClusterLock() {
        if (selectedItemId == ITEM_DPAD_CLUSTER || isDpadItem(selectedItemId)) {
            layoutConfig.dpadLocked = !layoutConfig.dpadLocked;
            if (layoutConfig.dpadLocked) {
                selectedItemId = ITEM_DPAD_CLUSTER;
            }
            syncClusters();
            notifySelection();
            invalidate();
        } else if (selectedItemId == ITEM_ABXY_CLUSTER || isAbxyItem(selectedItemId)) {
            layoutConfig.abxyLocked = !layoutConfig.abxyLocked;
            if (layoutConfig.abxyLocked) {
                selectedItemId = ITEM_ABXY_CLUSTER;
            }
            syncClusters();
            notifySelection();
            invalidate();
        }
    }

    public void resetSelectedItem() {
        GamepadLayoutConfig def = GamepadLayoutConfig.createDefaultConfig();
        switch (selectedItemId) {
            case ITEM_STICK_LEFT: layoutConfig.leftStick = def.leftStick.copy(); break;
            case ITEM_STICK_RIGHT: layoutConfig.rightStick = def.rightStick.copy(); break;
            case ITEM_DPAD_CLUSTER:
                layoutConfig.dpadCenter = def.dpadCenter.copy();
                layoutConfig.dpadSpacing = def.dpadSpacing;
                layoutConfig.dpadLocked = true;
                break;
            case ITEM_DPAD_UP: layoutConfig.dpadUp = def.dpadUp.copy(); break;
            case ITEM_DPAD_RIGHT: layoutConfig.dpadRight = def.dpadRight.copy(); break;
            case ITEM_DPAD_DOWN: layoutConfig.dpadDown = def.dpadDown.copy(); break;
            case ITEM_DPAD_LEFT: layoutConfig.dpadLeft = def.dpadLeft.copy(); break;

            case ITEM_ABXY_CLUSTER:
                layoutConfig.abxyCenter = def.abxyCenter.copy();
                layoutConfig.abxySpacing = def.abxySpacing;
                layoutConfig.abxyLocked = true;
                break;
            case ITEM_BTN_Y: layoutConfig.btnY = def.btnY.copy(); break;
            case ITEM_BTN_B: layoutConfig.btnB = def.btnB.copy(); break;
            case ITEM_BTN_A: layoutConfig.btnA = def.btnA.copy(); break;
            case ITEM_BTN_X: layoutConfig.btnX = def.btnX.copy(); break;

            case ITEM_BTN_LT: layoutConfig.btnLT = def.btnLT.copy(); break;
            case ITEM_BTN_LB: layoutConfig.btnLB = def.btnLB.copy(); break;
            case ITEM_BTN_RB: layoutConfig.btnRB = def.btnRB.copy(); break;
            case ITEM_BTN_RT: layoutConfig.btnRT = def.btnRT.copy(); break;

            case ITEM_BTN_BACK: layoutConfig.btnBack = def.btnBack.copy(); break;
            case ITEM_BTN_GUIDE: layoutConfig.btnGuide = def.btnGuide.copy(); break;
            case ITEM_BTN_START: layoutConfig.btnStart = def.btnStart.copy(); break;
            case ITEM_BTN_L3: layoutConfig.btnL3 = def.btnL3.copy(); break;
            case ITEM_BTN_R3: layoutConfig.btnR3 = def.btnR3.copy(); break;
        }
        syncClusters();
        notifySelection();
        invalidate();
    }

    public void resetAllToDefault() {
        this.layoutConfig = GamepadLayoutConfig.createDefaultConfig();
        syncClusters();
        notifySelection();
        invalidate();
    }

    private GamepadLayoutConfig.Element getSelectedElement() {
        switch (selectedItemId) {
            case ITEM_STICK_LEFT: return layoutConfig.leftStick;
            case ITEM_STICK_RIGHT: return layoutConfig.rightStick;
            case ITEM_DPAD_CLUSTER: return layoutConfig.dpadCenter;
            case ITEM_DPAD_UP: return layoutConfig.dpadUp;
            case ITEM_DPAD_RIGHT: return layoutConfig.dpadRight;
            case ITEM_DPAD_DOWN: return layoutConfig.dpadDown;
            case ITEM_DPAD_LEFT: return layoutConfig.dpadLeft;
            case ITEM_ABXY_CLUSTER: return layoutConfig.abxyCenter;
            case ITEM_BTN_Y: return layoutConfig.btnY;
            case ITEM_BTN_B: return layoutConfig.btnB;
            case ITEM_BTN_A: return layoutConfig.btnA;
            case ITEM_BTN_X: return layoutConfig.btnX;
            case ITEM_BTN_LT: return layoutConfig.btnLT;
            case ITEM_BTN_LB: return layoutConfig.btnLB;
            case ITEM_BTN_RB: return layoutConfig.btnRB;
            case ITEM_BTN_RT: return layoutConfig.btnRT;
            case ITEM_BTN_BACK: return layoutConfig.btnBack;
            case ITEM_BTN_GUIDE: return layoutConfig.btnGuide;
            case ITEM_BTN_START: return layoutConfig.btnStart;
            case ITEM_BTN_L3: return layoutConfig.btnL3;
            case ITEM_BTN_R3: return layoutConfig.btnR3;
            default: return null;
        }
    }

    private void notifySelection() {
        if (listener == null) return;
        if (selectedItemId == ITEM_NONE) {
            listener.onItemSelected(ITEM_NONE, "Chưa chọn phần tử nào", 1.0f, 1.0f, false, false);
            return;
        }

        String name = getItemDisplayName(selectedItemId);
        GamepadLayoutConfig.Element el = getSelectedElement();
        float scale = (el != null) ? el.scale : 1.0f;
        float spacing = 1.0f;
        boolean isCluster = false;
        boolean isLocked = false;

        if (selectedItemId == ITEM_DPAD_CLUSTER || isDpadItem(selectedItemId)) {
            isCluster = true;
            isLocked = layoutConfig.dpadLocked;
            spacing = layoutConfig.dpadSpacing;
        } else if (selectedItemId == ITEM_ABXY_CLUSTER || isAbxyItem(selectedItemId)) {
            isCluster = true;
            isLocked = layoutConfig.abxyLocked;
            spacing = layoutConfig.abxySpacing;
        }

        listener.onItemSelected(selectedItemId, name, scale, spacing, isLocked, isCluster);
    }

    private boolean isDpadItem(int id) {
        return id == ITEM_DPAD_UP || id == ITEM_DPAD_RIGHT || id == ITEM_DPAD_DOWN || id == ITEM_DPAD_LEFT;
    }

    private boolean isAbxyItem(int id) {
        return id == ITEM_BTN_Y || id == ITEM_BTN_B || id == ITEM_BTN_A || id == ITEM_BTN_X;
    }

    private String getItemDisplayName(int id) {
        switch (id) {
            case ITEM_STICK_LEFT: return "Cần Left Stick";
            case ITEM_STICK_RIGHT: return "Cần Right Stick";
            case ITEM_DPAD_CLUSTER: return "Cụm phím D-Pad";
            case ITEM_DPAD_UP: return "Phím D-Pad Lên (▲)";
            case ITEM_DPAD_RIGHT: return "Phím D-Pad Phải (▶)";
            case ITEM_DPAD_DOWN: return "Phím D-Pad Xuống (▼)";
            case ITEM_DPAD_LEFT: return "Phím D-Pad Trái (◀)";
            case ITEM_ABXY_CLUSTER: return "Cụm phím ABXY";
            case ITEM_BTN_Y: return "Nút Y (Vàng)";
            case ITEM_BTN_B: return "Nút B (Đỏ)";
            case ITEM_BTN_A: return "Nút A (Xanh lá)";
            case ITEM_BTN_X: return "Nút X (Xanh dương)";
            case ITEM_BTN_LT: return "Nút Trigger LT";
            case ITEM_BTN_LB: return "Nút Bumper LB";
            case ITEM_BTN_RB: return "Nút Bumper RB";
            case ITEM_BTN_RT: return "Nút Trigger RT";
            case ITEM_BTN_BACK: return "Nút Back (◀)";
            case ITEM_BTN_GUIDE: return "Nút Guide / Settings (⚙)";
            case ITEM_BTN_START: return "Nút Start (▶)";
            case ITEM_BTN_L3: return "Nút L3 (Left Stick Click)";
            case ITEM_BTN_R3: return "Nút R3 (Right Stick Click)";
            default: return "Phím Gamepad";
        }
    }
}
