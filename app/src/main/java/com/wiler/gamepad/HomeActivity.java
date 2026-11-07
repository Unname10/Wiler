package com.wiler.gamepad;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.wiler.gamepad.model.PreferencesManager;
import com.wiler.gamepad.service.BluetoothHidService;
import com.wiler.gamepad.ui.ConnectionBottomSheet;
import com.wiler.gamepad.ui.SettingsBottomSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends AppCompatActivity implements BluetoothHidService.ServiceCallback, ConnectionBottomSheet.ConnectionSheetListener {

    private ImageView btnSettings;
    private TextView tvBluetoothStatusBadge;
    private TextView tvHidStatusBadge;
    private TextView tvGraphicStatus;
    private LinearLayout cardDiscoverableActionHome;
    private ImageView ivDiscoverableIconHome;
    private TextView tvDiscoverableTitleHome;
    private TextView tvDiscoverableSubtitleHome;
    private TextView tvDiscoverableTimerHome;
    private TextView tvRecentCount;
    private LinearLayout containerRecentDevices;

    private BluetoothHidService hidService;
    private boolean isBound = false;

    private BluetoothDevice connectedDevice;
    private boolean isDiscoverable = false;
    private CountDownTimer discoverableTimer;
    private String originalBluetoothName = null;

    private ConnectionBottomSheet activeConnectionSheet;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> result) -> {
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
                }
            });

    private final ActivityResultLauncher<Intent> discoverableLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_CANCELED) {
                    setDiscoverableState(true, 120);
                } else {
                    setDiscoverableState(false, 0);
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothHidService.LocalBinder binder = (BluetoothHidService.LocalBinder) service;
            hidService = binder.getService();
            isBound = true;
            hidService.setCallback(HomeActivity.this);

            if (hasRequiredPermissions()) {
                hidService.initHid();
            }

            // Sync currently connected device
            connectedDevice = hidService.getHidManager().getConnectedDevice();
            updateConnectionVisuals();
            populateRecentDevices();

            // Auto-reconnect if enabled and not currently connected
            checkAutoReconnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            hidService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        setupListeners();

        // Start & bind service
        Intent intent = new Intent(this, BluetoothHidService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        checkInitialPermissions();
    }

    private void initViews() {
        btnSettings = findViewById(R.id.btnSettings);
        tvBluetoothStatusBadge = findViewById(R.id.tvBluetoothStatusBadge);
        tvHidStatusBadge = findViewById(R.id.tvHidStatusBadge);
        tvGraphicStatus = findViewById(R.id.tvGraphicStatus);
        cardDiscoverableActionHome = findViewById(R.id.cardDiscoverableActionHome);
        ivDiscoverableIconHome = findViewById(R.id.ivDiscoverableIconHome);
        tvDiscoverableTitleHome = findViewById(R.id.tvDiscoverableTitleHome);
        tvDiscoverableSubtitleHome = findViewById(R.id.tvDiscoverableSubtitleHome);
        tvDiscoverableTimerHome = findViewById(R.id.tvDiscoverableTimerHome);
        tvRecentCount = findViewById(R.id.tvRecentCount);
        containerRecentDevices = findViewById(R.id.containerRecentDevices);
    }

    private void setupListeners() {
        btnSettings.setOnClickListener(v -> {
            SettingsBottomSheet sheet = new SettingsBottomSheet();
            sheet.show(getSupportFragmentManager(), "settings_sheet");
        });

        cardDiscoverableActionHome.setOnClickListener(v -> {
            if (!isDiscoverable) {
                makeDiscoverable();
            } else {
                setDiscoverableState(false, 0);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasRequiredPermissions()) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            boolean btOn = adapter != null && adapter.isEnabled();
            tvBluetoothStatusBadge.setText(btOn ? "● Bluetooth On" : "● Bluetooth Off");
            tvBluetoothStatusBadge.setTextColor(ContextCompat.getColor(this, btOn ? R.color.status_connected : R.color.status_disconnected));
        }
        populateRecentDevices();
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
        if (!granted) {
            tvHidStatusBadge.setText("● Cần cấp quyền Bluetooth");
            tvHidStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
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

    @SuppressLint("MissingPermission")
    private void makeDiscoverable() {
        if (!hasRequiredPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        if (originalBluetoothName == null) {
            originalBluetoothName = adapter.getName();
        }
        adapter.setName("Wiler Gamepad");

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        discoverableLauncher.launch(discoverableIntent);
    }

    private void setDiscoverableState(boolean discoverable, int durationSec) {
        this.isDiscoverable = discoverable;
        if (discoverable) {
            tvDiscoverableTitleHome.setText("Đang phát tín hiệu");
            tvDiscoverableSubtitleHome.setText("Chạm để dừng phát");
            tvDiscoverableSubtitleHome.setVisibility(View.VISIBLE);
            tvDiscoverableTimerHome.setVisibility(View.VISIBLE);

            if (discoverableTimer != null) discoverableTimer.cancel();
            discoverableTimer = new CountDownTimer(durationSec * 1000L, 1000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long sec = millisUntilFinished / 1000;
                    tvDiscoverableTimerHome.setText(String.format(Locale.US, "%d\ngiây", sec));
                }

                @Override
                public void onFinish() {
                    setDiscoverableState(false, 0);
                }
            }.start();
        } else {
            if (discoverableTimer != null) {
                discoverableTimer.cancel();
                discoverableTimer = null;
            }
            tvDiscoverableTitleHome.setText("Bắt đầu ghép đôi với PC / Thiết bị mới");
            tvDiscoverableSubtitleHome.setVisibility(View.GONE);
            tvDiscoverableTimerHome.setVisibility(View.GONE);
        }

        if (activeConnectionSheet != null) {
            activeConnectionSheet.setDiscoverable(discoverable);
        }
    }

    @SuppressLint("MissingPermission")
    private void populateRecentDevices() {
        if (containerRecentDevices == null) return;
        containerRecentDevices.removeAllViews();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !hasRequiredPermissions()) {
            tvRecentCount.setText("0 thiết bị");
            return;
        }

        Set<BluetoothDevice> bondedDevices;
        try {
            bondedDevices = adapter.getBondedDevices();
        } catch (SecurityException e) {
            tvRecentCount.setText("0 thiết bị");
            return;
        }

        if (bondedDevices == null || bondedDevices.isEmpty()) {
            tvRecentCount.setText("0 thiết bị");
            return;
        }

        tvRecentCount.setText(String.format(Locale.US, "%d thiết bị", bondedDevices.size()));
        LayoutInflater inflater = LayoutInflater.from(this);

        for (BluetoothDevice device : bondedDevices) {
            View itemView = inflater.inflate(R.layout.item_bonded_device, containerRecentDevices, false);

            ImageView ivIcon = itemView.findViewById(R.id.ivDeviceType);
            TextView tvName = itemView.findViewById(R.id.tvDeviceName);
            TextView tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
            Button btnAction = itemView.findViewById(R.id.btnDeviceAction);

            String name = device.getName();
            if (name == null || name.isEmpty()) name = "Thiết bị Bluetooth";
            tvName.setText(name);
            tvAddress.setText(device.getAddress());

            BluetoothClass btc = device.getBluetoothClass();
            int major = (btc != null) ? btc.getMajorDeviceClass() : BluetoothClass.Device.Major.UNCATEGORIZED;
            if (major == BluetoothClass.Device.Major.COMPUTER) {
                ivIcon.setImageResource(R.drawable.ic_pc);
            } else if (major == BluetoothClass.Device.Major.PHONE) {
                ivIcon.setImageResource(R.drawable.ic_phone);
            } else if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                ivIcon.setImageResource(R.drawable.ic_tv);
            } else {
                ivIcon.setImageResource(R.drawable.ic_pc);
            }

            boolean isThisConnected = (connectedDevice != null && connectedDevice.getAddress().equalsIgnoreCase(device.getAddress()));
            if (isThisConnected) {
                btnAction.setText("🎮 Vào Gamepad");
                btnAction.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_button_green));
                btnAction.setTextColor(ContextCompat.getColor(this, R.color.white));
                btnAction.setOnClickListener(v -> launchGamepad());
            } else {
                btnAction.setText("Kết nối");
                btnAction.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_button_green));
                btnAction.setTextColor(ContextCompat.getColor(this, R.color.white));
                btnAction.setOnClickListener(v -> onConnectDevice(device));
            }

            containerRecentDevices.addView(itemView);
        }
    }

    private void updateConnectionVisuals() {
        if (connectedDevice != null) {
            String name = connectedDevice.getName();
            if (name == null || name.isEmpty()) name = "PC Windows";
            tvGraphicStatus.setText("● Đã kết nối với " + name);
            tvGraphicStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
        } else {
            tvGraphicStatus.setText("● Sẵn sàng kết nối");
            tvGraphicStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
        }
    }

    private void launchGamepad() {
        Intent intent = new Intent(this, GamepadActivity.class);
        startActivity(intent);
    }

    private void checkAutoReconnect() {
        PreferencesManager prefs = PreferencesManager.getInstance(this);
        if (connectedDevice == null && prefs.isAutoReconnect()) {
            String lastAddr = prefs.getLastDeviceAddress();
            if (lastAddr != null) {
                try {
                    BluetoothDevice lastDev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(lastAddr);
                    if (lastDev != null && hidService != null) {
                        hidService.getHidManager().connect(lastDev);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalBluetoothName != null && hasRequiredPermissions()) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                adapter.setName(originalBluetoothName);
            }
        }
        if (discoverableTimer != null) {
            discoverableTimer.cancel();
            discoverableTimer = null;
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
                tvHidStatusBadge.setText("● HID Service Ready");
                tvHidStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            } else {
                tvHidStatusBadge.setText("● HID Service Error");
                tvHidStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            }
        });
    }

    @Override
    public void onAppRegistrationChanged(boolean registered) {
        runOnUiThread(() -> {
            if (registered) {
                tvHidStatusBadge.setText("● HID Service Ready");
                tvHidStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            } else {
                tvHidStatusBadge.setText("● Đăng ký HID thất bại");
                tvHidStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {
        runOnUiThread(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device;
                String name = device != null ? device.getName() : "Host";
                PreferencesManager.getInstance(this).setLastConnectedDevice(device != null ? device.getAddress() : null, name);
                updateConnectionVisuals();
                populateRecentDevices();
                Toast.makeText(this, "Đã kết nối với " + name, Toast.LENGTH_SHORT).show();

                if (hidService != null) {
                    hidService.startForegroundNotification(name);
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null;
                updateConnectionVisuals();
                populateRecentDevices();

                if (hidService != null) {
                    hidService.stopForegroundNotification();
                }
            }

            if (activeConnectionSheet != null) {
                activeConnectionSheet.setConnectedDevice(connectedDevice);
            }
        });
    }

    @Override
    public void onScanModeChanged(int scanMode) {
        runOnUiThread(() -> {
            boolean discoverable = (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
            if (discoverable != isDiscoverable) {
                setDiscoverableState(discoverable, 120);
            }
        });
    }

    // --- ConnectionBottomSheet.ConnectionSheetListener ---

    @Override
    public void onConnectDevice(BluetoothDevice device) {
        if (hidService != null) {
            Toast.makeText(this, "Đang kết nối tới " + device.getName() + "...", Toast.LENGTH_SHORT).show();
            hidService.getHidManager().connect(device);
        }
    }

    @Override
    public void onDisconnectDevice() {
        if (hidService != null) {
            hidService.disconnect();
            Toast.makeText(this, "Đã ngắt kết nối", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestDiscoverable() {
        makeDiscoverable();
    }
}
