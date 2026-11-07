package com.wiler.gamepad;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import com.wiler.gamepad.model.GamepadLayoutConfig;
import com.wiler.gamepad.model.PreferencesManager;
import com.wiler.gamepad.ui.GamepadEditView;

import java.util.Locale;

public class GamepadEditActivity extends AppCompatActivity implements GamepadEditView.EditListener {

    private GamepadEditView gamepadEditView;

    private View layoutTopHeader;
    private Button btnCancelEdit;
    private Button btnPresetDefault;
    private Button btnPreset1;
    private Button btnPreset2;
    private Button btnPreset3;
    private Button btnResetAll;
    private Button btnTogglePreview;
    private Button btnSaveLayout;
    private TextView btnFloatingShowUi;

    private LinearLayout layoutBottomToolbar;
    private View layoutDragHeader;
    private TextView btnToggleCollapse;
    private TextView tvSelectedElementName;
    private TextView btnToggleClusterLock;
    private TextView btnResetElement;
    private LinearLayout containerSliders;
    private Slider sliderElementScale;
    private TextView tvElementScaleBadge;
    private LinearLayout rowElementSpacing;
    private Slider sliderElementSpacing;
    private TextView tvElementSpacingBadge;

    private boolean isToolbarCollapsed = false;
    private boolean isPreviewMode = false;
    private int currentSelectedItemId = GamepadEditView.ITEM_NONE;

    private PreferencesManager prefs;
    private int currentPresetId = PreferencesManager.PRESET_1;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gamepad_edit);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemUI();

        prefs = PreferencesManager.getInstance(this);
        currentPresetId = prefs.getActiveLayoutPreset();
        if (currentPresetId == PreferencesManager.PRESET_DEFAULT) {
            currentPresetId = PreferencesManager.PRESET_1;
        }

        initViews();
        setupListeners();

        selectPreset(currentPresetId);
    }

    private void hideSystemUI() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = getWindow();
            window.setDecorFitsSystemWindows(false);
            if (window.getInsetsController() != null) {
                window.getInsetsController().hide(
                        android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars()
                );
                window.getInsetsController().setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private void initViews() {
        gamepadEditView = findViewById(R.id.gamepadEditView);
        gamepadEditView.setEditListener(this);

        layoutTopHeader = findViewById(R.id.layoutTopHeader);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
        btnPresetDefault = findViewById(R.id.btnPresetDefault);
        btnPreset1 = findViewById(R.id.btnPreset1);
        btnPreset2 = findViewById(R.id.btnPreset2);
        btnPreset3 = findViewById(R.id.btnPreset3);
        btnResetAll = findViewById(R.id.btnResetAll);
        btnTogglePreview = findViewById(R.id.btnTogglePreview);
        btnSaveLayout = findViewById(R.id.btnSaveLayout);
        btnFloatingShowUi = findViewById(R.id.btnFloatingShowUi);

        layoutBottomToolbar = findViewById(R.id.layoutBottomToolbar);
        layoutDragHeader = findViewById(R.id.layoutDragHeader);
        btnToggleCollapse = findViewById(R.id.btnToggleCollapse);

        tvSelectedElementName = findViewById(R.id.tvSelectedElementName);
        btnToggleClusterLock = findViewById(R.id.btnToggleClusterLock);
        btnResetElement = findViewById(R.id.btnResetElement);
        containerSliders = findViewById(R.id.containerSliders);
        sliderElementScale = findViewById(R.id.sliderElementScale);
        tvElementScaleBadge = findViewById(R.id.tvElementScaleBadge);
        rowElementSpacing = findViewById(R.id.rowElementSpacing);
        sliderElementSpacing = findViewById(R.id.sliderElementSpacing);
        tvElementSpacingBadge = findViewById(R.id.tvElementSpacingBadge);
    }

    private void setupListeners() {
        btnCancelEdit.setOnClickListener(v -> finish());

        btnPresetDefault.setOnClickListener(v -> selectPreset(PreferencesManager.PRESET_DEFAULT));
        btnPreset1.setOnClickListener(v -> selectPreset(PreferencesManager.PRESET_1));
        btnPreset2.setOnClickListener(v -> selectPreset(PreferencesManager.PRESET_2));
        btnPreset3.setOnClickListener(v -> selectPreset(PreferencesManager.PRESET_3));

        btnResetAll.setOnClickListener(v -> {
            gamepadEditView.resetAllToDefault();
            Toast.makeText(this, "Đã khôi phục bố cục chuẩn mặc định", Toast.LENGTH_SHORT).show();
        });

        btnSaveLayout.setOnClickListener(v -> saveCurrentLayout());

        btnTogglePreview.setOnClickListener(v -> enterPreviewMode());
        btnFloatingShowUi.setOnClickListener(v -> exitPreviewMode());

        btnResetElement.setOnClickListener(v -> gamepadEditView.resetSelectedItem());
        btnToggleClusterLock.setOnClickListener(v -> gamepadEditView.toggleClusterLock());
        btnToggleCollapse.setOnClickListener(v -> toggleToolbarCollapse());

        setupDraggableToolbar();

        sliderElementScale.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int percent = Math.round(value);
                tvElementScaleBadge.setText(String.format(Locale.US, "%d%%", percent));
                gamepadEditView.setSelectedScale(percent / 100f);
            }
        });

        sliderElementSpacing.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int percent = Math.round(value);
                tvElementSpacingBadge.setText(String.format(Locale.US, "%d%%", percent));
                gamepadEditView.setSelectedSpacing(percent / 100f);
            }
        });
    }

    private void selectPreset(int presetId) {
        this.currentPresetId = presetId;
        updatePresetButtonsUi();

        GamepadLayoutConfig config = prefs.getLayoutConfig(presetId);
        gamepadEditView.setLayoutConfig(config);
    }

    private void updatePresetButtonsUi() {
        setPresetButtonState(btnPresetDefault, currentPresetId == PreferencesManager.PRESET_DEFAULT);
        setPresetButtonState(btnPreset1, currentPresetId == PreferencesManager.PRESET_1);
        setPresetButtonState(btnPreset2, currentPresetId == PreferencesManager.PRESET_2);
        setPresetButtonState(btnPreset3, currentPresetId == PreferencesManager.PRESET_3);
    }

    private void setPresetButtonState(Button btn, boolean selected) {
        if (selected) {
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_card_dark_selected));
            btn.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_card_dark));
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void saveCurrentLayout() {
        int targetPreset = currentPresetId;
        if (targetPreset == PreferencesManager.PRESET_DEFAULT) {
            targetPreset = PreferencesManager.PRESET_1;
        }

        GamepadLayoutConfig currentConfig = gamepadEditView.getLayoutConfig();
        prefs.saveLayoutConfig(targetPreset, currentConfig);
        prefs.setActiveLayoutPreset(targetPreset);

        String presetName = (targetPreset == 1) ? "Preset 1" : (targetPreset == 2) ? "Preset 2" : "Preset 3";
        Toast.makeText(this, "Đã lưu bố cục vào " + presetName + " thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void enterPreviewMode() {
        isPreviewMode = true;
        layoutTopHeader.setVisibility(View.GONE);
        layoutBottomToolbar.setVisibility(View.GONE);
        btnFloatingShowUi.setVisibility(View.VISIBLE);
        gamepadEditView.setPreviewMode(true);
        Toast.makeText(this, "Chế độ xem toàn cảnh (Bấm 'Hiện giao diện' để tiếp tục)", Toast.LENGTH_SHORT).show();
    }

    private void exitPreviewMode() {
        isPreviewMode = false;
        layoutTopHeader.setVisibility(View.VISIBLE);
        layoutBottomToolbar.setVisibility(View.VISIBLE);
        btnFloatingShowUi.setVisibility(View.GONE);
        gamepadEditView.setPreviewMode(false);
    }

    @Override
    public void onBackPressed() {
        if (isPreviewMode) {
            exitPreviewMode();
            return;
        }
        super.onBackPressed();
    }

    private void toggleToolbarCollapse() {
        isToolbarCollapsed = !isToolbarCollapsed;
        updateCollapseState();
    }

    private void updateCollapseState() {
        if (isToolbarCollapsed) {
            containerSliders.setVisibility(View.GONE);
            btnToggleCollapse.setText("▲");
        } else {
            if (currentSelectedItemId != GamepadEditView.ITEM_NONE) {
                containerSliders.setVisibility(View.VISIBLE);
            }
            btnToggleCollapse.setText("▼");
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableToolbar() {
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private float startRawX, startRawY;
            private float startViewX, startViewY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        startViewX = layoutBottomToolbar.getX();
                        startViewY = layoutBottomToolbar.getY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - startRawX;
                        float deltaY = event.getRawY() - startRawY;

                        View parent = (View) layoutBottomToolbar.getParent();
                        if (parent != null) {
                            float newX = startViewX + deltaX;
                            float newY = startViewY + deltaY;

                            float maxX = parent.getWidth() - layoutBottomToolbar.getWidth();
                            float maxY = parent.getHeight() - layoutBottomToolbar.getHeight();

                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));

                            layoutBottomToolbar.setX(newX);
                            layoutBottomToolbar.setY(newY);
                        }
                        return true;
                }
                return false;
            }
        };

        layoutDragHeader.setOnTouchListener(dragListener);
    }

    // --- GamepadEditView.EditListener ---

    @Override
    public void onItemSelected(int itemId, String displayName, float currentScale, float currentSpacing, boolean isClusterLocked, boolean isCluster) {
        this.currentSelectedItemId = itemId;
        tvSelectedElementName.setText(displayName);

        if (itemId == GamepadEditView.ITEM_NONE) {
            containerSliders.setVisibility(View.GONE);
            btnResetElement.setVisibility(View.GONE);
            btnToggleClusterLock.setVisibility(View.GONE);
            btnToggleCollapse.setVisibility(View.GONE);
            return;
        }

        btnToggleCollapse.setVisibility(View.VISIBLE);
        if (!isToolbarCollapsed) {
            containerSliders.setVisibility(View.VISIBLE);
        } else {
            containerSliders.setVisibility(View.GONE);
        }
        btnResetElement.setVisibility(View.VISIBLE);

        int scalePercent = Math.max(60, Math.min(160, Math.round(currentScale * 100f)));
        sliderElementScale.setValue(scalePercent);
        tvElementScaleBadge.setText(String.format(Locale.US, "%d%%", scalePercent));

        if (isCluster) {
            btnToggleClusterLock.setVisibility(View.VISIBLE);
            btnToggleClusterLock.setText(isClusterLocked ? "🔒 Khóa cụm" : "🔓 Mở khóa lẻ");
            btnToggleClusterLock.setTextColor(isClusterLocked
                    ? ContextCompat.getColor(this, R.color.xbox_green)
                    : ContextCompat.getColor(this, R.color.status_disconnected));

            if (isClusterLocked) {
                rowElementSpacing.setVisibility(View.VISIBLE);
                int spacingPercent = Math.max(60, Math.min(160, Math.round(currentSpacing * 100f)));
                sliderElementSpacing.setValue(spacingPercent);
                tvElementSpacingBadge.setText(String.format(Locale.US, "%d%%", spacingPercent));
            } else {
                rowElementSpacing.setVisibility(View.GONE);
            }
        } else {
            btnToggleClusterLock.setVisibility(View.GONE);
            rowElementSpacing.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemMoved() {
        // Optional live tracking
    }
}
