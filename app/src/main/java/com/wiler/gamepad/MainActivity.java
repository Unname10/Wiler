package com.wiler.gamepad;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.wiler.gamepad.model.GamepadState;
import com.wiler.gamepad.service.BluetoothHidService;
import com.wiler.gamepad.ui.SimpleJoystickView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Milestone 1 Prototype Activity.
 * Allows quick hardware verification of Bluetooth HID registration,
 * pairing, Button A presses, and analog joystick movement.
 */
public class MainActivity extends AppCompatActivity implements BluetoothHidService.ServiceCallback {

    private TextView tvPermissionStatus;
    private TextView tvHidStatus;
    private TextView tvConnectionStatus;
    private TextView tvScanModeStatus;
    private TextView tvJoystickValues;

    private MaterialButton btnRequestPerm;
    private MaterialButton btnDiscoverable;
    private MaterialButton btnDisconnect;
    private MaterialButton btnConnectHost;
    private MaterialButton btnTestA;
    private SimpleJoystickView joystickView;

    private BluetoothHidService hidService;
    private boolean isBound = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                updatePermissionUi(allGranted);
                if (allGranted && hidService != null) {
                    hidService.initHid();
                } else if (!allGranted) {
                    Toast.makeText(this, "Cần cấp đủ quyền Bluetooth để hoạt động!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        // Start and bind the service
        Intent intent = new Intent(this, BluetoothHidService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        checkInitialPermissions();
    }

    private void initViews() {
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvHidStatus = findViewById(R.id.tvHidStatus);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvScanModeStatus = findViewById(R.id.tvScanModeStatus);
        tvJoystickValues = findViewById(R.id.tvJoystickValues);

        btnRequestPerm = findViewById(R.id.btnRequestPerm);
        btnDiscoverable = findViewById(R.id.btnDiscoverable);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnConnectHost = findViewById(R.id.btnConnectHost);
        btnTestA = findViewById(R.id.btnTestA);
        joystickView = findViewById(R.id.joystickView);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnRequestPerm.setOnClickListener(v -> requestBluetoothPermissions());

        btnDiscoverable.setOnClickListener(v -> makeDiscoverable());

        btnDisconnect.setOnClickListener(v -> {
            if (hidService != null) {
                hidService.disconnect();
                Toast.makeText(this, "Đã gửi lệnh ngắt kết nối", Toast.LENGTH_SHORT).show();
            }
        });

        btnConnectHost.setOnClickListener(v -> showBondedDevicesDialog());

        // Button A touch listener (Instant press & release via Dispatcher)
        btnTestA.setOnTouchListener((v, event) -> {
            if (hidService == null) return false;
            GamepadState state = hidService.getGamepadState();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    state.setButton(GamepadState.BTN_A, true);
                    hidService.notifyInputChanged();
                    btnTestA.setAlpha(0.7f);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    state.setButton(GamepadState.BTN_A, false);
                    hidService.notifyInputChanged();
                    btnTestA.setAlpha(1.0f);
                    return true;
            }
            return false;
        });

        // Analog Joystick move listener
        joystickView.setOnJoystickMoveListener((x, y) -> {
            if (hidService != null) {
                GamepadState state = hidService.getGamepadState();
                state.setLeftStick(x, y);
                hidService.notifyInputChanged();
            }
            tvJoystickValues.setText(String.format(Locale.US, "X: %.2f  Y: %.2f", x, y));
        });
    }

    private void checkInitialPermissions() {
        boolean granted = hasRequiredPermissions();
        updatePermissionUi(granted);
        if (!granted) {
            requestBluetoothPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean connect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            boolean advertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED;
            return connect && advertise;
        }
        return true;
    }

    private void updatePermissionUi(boolean granted) {
        if (granted) {
            tvPermissionStatus.setText("Quyền Bluetooth: ĐÃ CẤP (HỢP LỆ)");
            tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            btnRequestPerm.setEnabled(false);
        } else {
            tvPermissionStatus.setText("Quyền Bluetooth: CHƯA CẤP (Cần cấp quyền)");
            tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            btnRequestPerm.setEnabled(true);
        }
    }

    private void requestBluetoothPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!perms.isEmpty()) {
            permissionLauncher.launch(perms.toArray(new String[0]));
        }
    }

    private String originalBluetoothName = null;

    @SuppressLint("MissingPermission")
    private void makeDiscoverable() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Thiết bị không có Bluetooth!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set adapter name to "Wiler Gamepad" so searching devices clearly see it
        if (hasRequiredPermissions()) {
            if (originalBluetoothName == null) {
                originalBluetoothName = adapter.getName();
            }
            adapter.setName("Wiler Gamepad");
        }

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(discoverableIntent);
    }

    @SuppressLint("MissingPermission")
    private void showBondedDevicesDialog() {
        if (hidService == null || !hasRequiredPermissions()) {
            Toast.makeText(this, "Chưa cấp quyền hoặc service chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        java.util.Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "Chưa có thiết bị nào được ghép đôi! Vui lòng ghép đôi qua Windows trước.", Toast.LENGTH_LONG).show();
            return;
        }

        List<BluetoothDevice> deviceList = new ArrayList<>(bonded);
        String[] names = new String[deviceList.size()];
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice d = deviceList.get(i);
            names[i] = (d.getName() != null ? d.getName() : "Unknown Device") + " (" + d.getAddress() + ")";
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Chọn PC/Host đã ghép đôi để kết nối")
                .setItems(names, (dialog, which) -> {
                    BluetoothDevice target = deviceList.get(which);
                    Toast.makeText(this, "Đang kết nối tới " + target.getName() + "...", Toast.LENGTH_SHORT).show();
                    hidService.getHidManager().connect(target);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            BluetoothHidService.LocalBinder localBinder = (BluetoothHidService.LocalBinder) binder;
            hidService = localBinder.getService();
            hidService.setCallback(MainActivity.this);
            isBound = true;

            // If permissions are already granted, initialize HID right away
            if (hasRequiredPermissions()) {
                hidService.initHid();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            hidService = null;
            isBound = false;
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBluetoothName != null && hasRequiredPermissions()) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                adapter.setName(originalBluetoothName);
            }
        }
        if (isBound) {
            if (hidService != null) {
                hidService.setCallback(null);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    // --- ServiceCallback Implementations ---

    @Override
    public void onHidProxyStatusChanged(boolean available) {
        runOnUiThread(() -> {
            if (available) {
                tvHidStatus.setText("HID Proxy: Đã kết nối với Bluetooth Profile");
                tvHidStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else {
                tvHidStatus.setText("HID Service: LỖI - ROM không hỗ trợ hoặc bị chiếm dụng!");
                tvHidStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            }
        });
    }

    @Override
    public void onAppRegistrationChanged(boolean registered) {
        runOnUiThread(() -> {
            if (registered) {
                tvHidStatus.setText("HID App: ĐÃ ĐĂNG KÝ THÀNH CÔNG (Sẵn sàng)");
                tvHidStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            } else {
                tvHidStatus.setText("HID App: Đăng ký thất bại");
                tvHidStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {
        runOnUiThread(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                String name = device != null ? device.getName() : "Unknown Host";
                tvConnectionStatus.setText("Kết nối Host: ĐÃ KẾT NỐI (" + name + ")");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
                Toast.makeText(this, "Đã kết nối với " + name, Toast.LENGTH_SHORT).show();
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                tvConnectionStatus.setText("Kết nối Host: Đang kết nối...");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else {
                tvConnectionStatus.setText("Kết nối Host: Chưa kết nối");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        });
    }

    @Override
    public void onScanModeChanged(int scanMode) {
        runOnUiThread(() -> {
            if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                tvScanModeStatus.setText("Chế độ quét: Đang phát tín hiệu tìm kiếm (Discoverable)");
                tvScanModeStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            } else {
                tvScanModeStatus.setText("Chế độ quét: Bình thường");
                tvScanModeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        });
    }
}
