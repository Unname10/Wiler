package com.wiler.gamepad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wiler.gamepad.model.GamepadState;
import com.wiler.gamepad.model.PreferencesManager;
import com.wiler.gamepad.service.BluetoothHidService;
import com.wiler.gamepad.ui.GamepadView;
import com.wiler.gamepad.ui.SettingsBottomSheet;

import java.util.Locale;

public class GamepadActivity extends AppCompatActivity implements BluetoothHidService.ServiceCallback, GamepadView.GamepadListener {

    private GamepadView gamepadView;
    private LinearLayout layoutDisconnectedOverlay;
    private TextView tvDisconnectTitle;
    private TextView tvDisconnectSubtitle;
    private Button btnOverlayExit;
    private Button btnOverlayRetry;

    private BluetoothHidService hidService;
    private boolean isBound = false;

    private BluetoothDevice targetDevice;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Dialog quickMenuDialog;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothHidService.LocalBinder binder = (BluetoothHidService.LocalBinder) service;
            hidService = binder.getService();
            isBound = true;
            hidService.registerCallback(GamepadActivity.this);

            gamepadView.setGamepadState(hidService.getGamepadState());

            // Check currently connected device
            BluetoothDevice dev = hidService.getHidManager().getConnectedDevice();
            if (dev != null) {
                targetDevice = dev;
                layoutDisconnectedOverlay.setVisibility(View.GONE);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            hidService = null;
            isBound = false;
        }
    };

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gamepad);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemUI();

        gamepadView = findViewById(R.id.gamepadView);
        layoutDisconnectedOverlay = findViewById(R.id.layoutDisconnectedOverlay);
        tvDisconnectTitle = findViewById(R.id.tvDisconnectTitle);
        tvDisconnectSubtitle = findViewById(R.id.tvDisconnectSubtitle);
        btnOverlayExit = findViewById(R.id.btnOverlayExit);
        btnOverlayRetry = findViewById(R.id.btnOverlayRetry);

        gamepadView.setListener(this);

        btnOverlayExit.setOnClickListener(v -> finish());
        btnOverlayRetry.setOnClickListener(v -> retryConnection());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showQuickMenuDialog();
            }
        });

        // Start service first so bind won't fail if service was killed
        Intent intent = new Intent(this, BluetoothHidService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gamepadView != null) {
            PreferencesManager prefs = PreferencesManager.getInstance(this);
            gamepadView.setDeadzone(prefs.getDeadzone());
            gamepadView.setDynamicJoystick(prefs.isDynamicJoystick());
            gamepadView.setButtonScale(prefs.getButtonScale());
            gamepadView.setVibrationEnabled(prefs.isVibrationEnabled());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gamepadView != null) {
            gamepadView.resetAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (quickMenuDialog != null && quickMenuDialog.isShowing()) {
            quickMenuDialog.dismiss();
        }
        if (isBound) {
            if (hidService != null) {
                hidService.unregisterCallback(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    // --- GamepadView.GamepadListener ---

    @Override
    public void onInputChanged() {
        if (hidService != null) {
            hidService.notifyInputChanged();
        }
    }

    @Override
    public void onGuideLongPress() {
        // Triggered when Guide held >= 1s -> Open quick menu without sending Guide key to PC
        runOnUiThread(this::showQuickMenuDialog);
    }

    @Override
    public void onGuideShortPress() {
        // Short tap (<1s) -> Send Guide button press & release
        if (hidService != null) {
            GamepadState state = hidService.getGamepadState();
            state.setButton(GamepadState.BTN_GUIDE, true);
            hidService.notifyInputChanged();

            handler.postDelayed(() -> {
                state.setButton(GamepadState.BTN_GUIDE, false);
                hidService.notifyInputChanged();
            }, 60L);
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        showQuickMenuDialog();
    }

    private void showQuickMenuDialog() {
        if (isFinishing() || isDestroyed()) return;

        if (quickMenuDialog == null) {
            quickMenuDialog = new Dialog(this);
            quickMenuDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            quickMenuDialog.setContentView(R.layout.dialog_quick_menu);
            if (quickMenuDialog.getWindow() != null) {
                quickMenuDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            quickMenuDialog.findViewById(R.id.btnCloseQuickMenu).setOnClickListener(v -> quickMenuDialog.dismiss());
            quickMenuDialog.findViewById(R.id.btnQuickResume).setOnClickListener(v -> quickMenuDialog.dismiss());

            quickMenuDialog.findViewById(R.id.btnQuickSettings).setOnClickListener(v -> {
                quickMenuDialog.dismiss();
                SettingsBottomSheet bottomSheet = new SettingsBottomSheet();
                bottomSheet.setListener(new SettingsBottomSheet.OnSettingsChangeListener() {
                    @Override
                    public void onDeadzoneChanged(float deadzone) {
                        gamepadView.setDeadzone(deadzone);
                    }

                    @Override
                    public void onDynamicJoystickChanged(boolean enabled) {
                        gamepadView.setDynamicJoystick(enabled);
                    }

                    @Override
                    public void onButtonScaleChanged(float scale) {
                        gamepadView.setButtonScale(scale);
                    }

                    @Override
                    public void onVibrationChanged(boolean enabled) {
                        gamepadView.setVibrationEnabled(enabled);
                    }

                    @Override
                    public void onProfileChanged(int profile) {
                        if (hidService != null) {
                            hidService.getHidManager().switchProfile(profile);
                        }
                    }
                });
                bottomSheet.show(getSupportFragmentManager(), "settings_sheet");
            });

            quickMenuDialog.findViewById(R.id.btnQuickDisconnect).setOnClickListener(v -> {
                quickMenuDialog.dismiss();
                if (hidService != null) {
                    hidService.disconnect();
                }
                finish();
            });

            quickMenuDialog.findViewById(R.id.btnQuickExit).setOnClickListener(v -> {
                quickMenuDialog.dismiss();
                finish();
            });
        }

        quickMenuDialog.show();
    }

    // --- ServiceCallback Implementations ---

    @Override
    public void onHidProxyStatusChanged(boolean available) {
    }

    @Override
    public void onAppRegistrationChanged(boolean registered) {
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {
        runOnUiThread(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                targetDevice = device;
                reconnectAttempts = 0;
                layoutDisconnectedOverlay.setVisibility(View.GONE);
                Toast.makeText(this, "Đã kết nối với " + (device != null ? device.getName() : "Host"), Toast.LENGTH_SHORT).show();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                layoutDisconnectedOverlay.setVisibility(View.VISIBLE);
                String name = (targetDevice != null) ? targetDevice.getName() : "Máy tính";
                tvDisconnectTitle.setText("Mất kết nối với " + name);

                PreferencesManager prefs = PreferencesManager.getInstance(this);
                if (prefs.isAutoReconnect() && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    tvDisconnectSubtitle.setText(String.format(Locale.US, "Đang tự động kết nối lại... (Lần %d/%d)", reconnectAttempts, MAX_RECONNECT_ATTEMPTS));
                    handler.postDelayed(this::retryConnection, 2000L);
                } else {
                    tvDisconnectSubtitle.setText("Không có kết nối đến thiết bị.");
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void retryConnection() {
        if (hidService == null) return;
        if (targetDevice == null) {
            String lastAddr = PreferencesManager.getInstance(this).getLastDeviceAddress();
            if (lastAddr != null) {
                targetDevice = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(lastAddr);
            }
        }

        if (targetDevice != null) {
            tvDisconnectSubtitle.setText("Đang gửi yêu cầu kết nối...");
            boolean initiated = hidService.getHidManager().connect(targetDevice);
            if (!initiated) {
                tvDisconnectSubtitle.setText("Kết nối thất bại. Hãy kiểm tra lại PC.");
            }
        } else {
            tvDisconnectSubtitle.setText("Không tìm thấy thông tin thiết bị đã ghép đôi.");
        }
    }

    @Override
    public void onScanModeChanged(int scanMode) {
    }
}
