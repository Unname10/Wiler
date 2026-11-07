package com.wiler.gamepad.hid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.wiler.gamepad.model.GamepadState;

import java.util.concurrent.Executors;

/**
 * Handles communication with Android's BluetoothHidDevice proxy, SDP registration,
 * connection callbacks, replyReport for host enumeration, and report dispatching.
 */
public class BluetoothHidManager {

    private static final String TAG = "BluetoothHidManager";

    public interface Listener {
        void onHidProxyStatusChanged(boolean available);
        void onAppRegistrationChanged(boolean registered);
        void onConnectionStateChanged(BluetoothDevice device, int state);
    }

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private Listener listener;
    private GamepadState currentState;

    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private boolean isProxyBound = false;
    private boolean isAppRegistered = false;

    public BluetoothHidManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setGamepadState(GamepadState state) {
        this.currentState = state;
    }

    public synchronized boolean isAvailable() {
        return isProxyBound && isAppRegistered;
    }

    public synchronized BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public synchronized boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean init() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device");
            if (listener != null) listener.onHidProxyStatusChanged(false);
            return false;
        }

        if (!hasPermissions()) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not yet granted; postponing init");
            return false;
        }

        if (isProxyBound && hidDevice != null) {
            Log.d(TAG, "HID proxy already bound");
            return true;
        }

        Log.d(TAG, "Calling getProfileProxy for HID_DEVICE...");
        boolean success = bluetoothAdapter.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE);
        if (!success) {
            Log.e(TAG, "getProfileProxy returned false! This device ROM may have disabled HID Device profile.");
            if (listener != null) listener.onHidProxyStatusChanged(false);
            return false;
        }

        return true;
    }

    @SuppressLint("MissingPermission")
    private void registerHidApp() {
        if (hidDevice == null || !hasPermissions()) {
            Log.e(TAG, "Cannot registerApp: hidDevice is null or missing permissions");
            return;
        }

        Log.i(TAG, "Registering Bluetooth HID application SDP record with SUBCLASS2_GAMEPAD (0x02)...");

        try {
            // Use Android's official constant SUBCLASS2_GAMEPAD = 0x02
            byte subclass = BluetoothHidDevice.SUBCLASS2_GAMEPAD;

            BluetoothHidDeviceAppSdpSettings sdpSettings = new BluetoothHidDeviceAppSdpSettings(
                    "Wiler Gamepad",
                    "Wiler Bluetooth Gamepad",
                    "Wiler",
                    subclass,
                    StandardHidDescriptor.DESCRIPTOR
            );

            // Passing null for inQos and outQos uses standard Bluetooth default QoS
            boolean registered = hidDevice.registerApp(
                    sdpSettings,
                    null,
                    null,
                    Executors.newSingleThreadExecutor(),
                    hidCallback
            );

            Log.i(TAG, "registerApp call returned: " + registered);
            if (!registered && listener != null) {
                listener.onAppRegistrationChanged(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during registerApp", e);
            if (listener != null) {
                listener.onAppRegistrationChanged(false);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean sendReport(int reportId, byte[] data) {
        if (hidDevice == null || connectedDevice == null || !hasPermissions()) {
            return false;
        }
        try {
            return hidDevice.sendReport(connectedDevice, reportId, data);
        } catch (Exception e) {
            Log.e(TAG, "Error in sendReport", e);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean connect(BluetoothDevice device) {
        if (hidDevice == null || device == null || !hasPermissions()) {
            return false;
        }
        return hidDevice.connect(device);
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean disconnect() {
        if (hidDevice == null || !hasPermissions()) {
            return false;
        }
        boolean success = false;
        if (connectedDevice != null) {
            try {
                success = hidDevice.disconnect(connectedDevice);
                Log.i(TAG, "hidDevice.disconnect(connectedDevice) result: " + success);
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting connectedDevice", e);
            }
        }
        try {
            java.util.List<BluetoothDevice> devs = hidDevice.getConnectedDevices();
            if (devs != null) {
                for (BluetoothDevice d : devs) {
                    hidDevice.disconnect(d);
                    success = true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting all connected devices", e);
        }
        connectedDevice = null;
        return success;
    }

    @SuppressLint("MissingPermission")
    public synchronized void cleanup() {
        if (hidDevice != null) {
            try {
                if (isAppRegistered && hasPermissions()) {
                    hidDevice.unregisterApp();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering HID app", e);
            }
            try {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice);
            } catch (Exception e) {
                Log.w(TAG, "Error closing HID profile proxy", e);
            }
            hidDevice = null;
        }
        isProxyBound = false;
        isAppRegistered = false;
        connectedDevice = null;
    }

    private final BluetoothProfile.ServiceListener profileServiceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.i(TAG, "BluetoothProfile.HID_DEVICE connected");
                synchronized (BluetoothHidManager.this) {
                    hidDevice = (BluetoothHidDevice) proxy;
                    isProxyBound = true;
                }
                if (listener != null) listener.onHidProxyStatusChanged(true);
                registerHidApp();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.i(TAG, "BluetoothProfile.HID_DEVICE disconnected");
                synchronized (BluetoothHidManager.this) {
                    hidDevice = null;
                    isProxyBound = false;
                    isAppRegistered = false;
                    connectedDevice = null;
                }
                if (listener != null) listener.onHidProxyStatusChanged(false);
            }
        }
    };

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            Log.i(TAG, "onAppStatusChanged: registered = " + registered + ", pluggedDevice = " + pluggedDevice);
            synchronized (BluetoothHidManager.this) {
                isAppRegistered = registered;
                if (registered && pluggedDevice != null) {
                    connectedDevice = pluggedDevice;
                }
            }
            if (listener != null) listener.onAppRegistrationChanged(registered);
            if (registered && pluggedDevice != null && listener != null) {
                listener.onConnectionStateChanged(pluggedDevice, BluetoothProfile.STATE_CONNECTED);
            }
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            Log.i(TAG, "onConnectionStateChanged: device = " + device + ", state = " + state);
            synchronized (BluetoothHidManager.this) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device;
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (connectedDevice != null && connectedDevice.equals(device)) {
                        connectedDevice = null;
                    }
                }
            }
            if (listener != null) listener.onConnectionStateChanged(device, state);
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            Log.i(TAG, "onGetReport requested by host: type=" + type + ", id=" + id + ", bufferSize=" + bufferSize);
            if (hidDevice != null && hasPermissions()) {
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && (id == StandardHidDescriptor.REPORT_ID || id == 0)) {
                    // Host is requesting current input report during enumeration.
                    // Always reply with our actual REPORT_ID (1), regardless of whether the
                    // host passed id=0 (meaning "any report"). Replying with id=0 confuses
                    // the Windows HID driver and causes it to reject the response.
                    byte[] report;
                    if (currentState != null) {
                        report = StandardHidDescriptor.buildReport(currentState);
                    } else {
                        report = StandardHidDescriptor.buildNeutralReport();
                    }
                    boolean replied = hidDevice.replyReport(device, type, StandardHidDescriptor.REPORT_ID, report);
                    Log.i(TAG, "replyReport(id=" + StandardHidDescriptor.REPORT_ID + ") result: " + replied);
                } else {
                    Log.w(TAG, "onGetReport: unsupported type=" + type + " id=" + id + ", responding with INVALID_RPT_ID");
                    hidDevice.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
                }
            }
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            Log.i(TAG, "onSetReport from host: type=" + type + ", id=" + id);
            if (hidDevice != null && hasPermissions()) {
                hidDevice.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS);
            }
        }

        @Override
        public void onSetProtocol(BluetoothDevice device, byte protocol) {
            Log.i(TAG, "onSetProtocol: protocol = " + protocol);
            // Accept protocol switch without error
        }

        @Override
        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
            Log.i(TAG, "onInterruptData: reportId = " + reportId);
        }
    };
}
