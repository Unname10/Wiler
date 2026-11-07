package com.wiler.gamepad.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wiler.gamepad.R;
import com.wiler.gamepad.hid.BluetoothHidManager;
import com.wiler.gamepad.hid.InputDispatcher;
import com.wiler.gamepad.model.GamepadState;

/**
 * Started and Bound Service hosting the Bluetooth HID proxy, InputDispatcher,
 * and BroadcastReceivers for adapter state.
 */
public class BluetoothHidService extends Service implements BluetoothHidManager.Listener {

    private static final String TAG = "BluetoothHidService";
    private static final String CHANNEL_ID = "wiler_gamepad_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_DISCONNECT = "com.wiler.gamepad.ACTION_DISCONNECT";

    public interface ServiceCallback {
        void onHidProxyStatusChanged(boolean available);
        void onAppRegistrationChanged(boolean registered);
        void onConnectionStateChanged(BluetoothDevice device, int state);
        void onScanModeChanged(int scanMode);
    }

    private final IBinder binder = new LocalBinder();
    private BluetoothHidManager hidManager;
    private GamepadState gamepadState;
    private InputDispatcher inputDispatcher;
    private ServiceCallback callback;

    public class LocalBinder extends Binder {
        public BluetoothHidService getService() {
            return BluetoothHidService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BluetoothHidService onCreate");

        gamepadState = new GamepadState();
        hidManager = new BluetoothHidManager(this);
        hidManager.setGamepadState(gamepadState);
        hidManager.setListener(this);

        inputDispatcher = new InputDispatcher(hidManager, gamepadState);
        inputDispatcher.start();

        createNotificationChannel();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            Log.d(TAG, "ACTION_DISCONNECT received from notification");
            disconnect();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    public GamepadState getGamepadState() {
        return gamepadState;
    }

    public BluetoothHidManager getHidManager() {
        return hidManager;
    }

    public InputDispatcher getInputDispatcher() {
        return inputDispatcher;
    }

    public void initHid() {
        hidManager.init();
    }

    public void disconnect() {
        hidManager.disconnect();
    }

    public void notifyInputChanged() {
        inputDispatcher.onInputChanged();
    }

    public void startForegroundNotification(String deviceName) {
        Intent disconnectIntent = new Intent(this, BluetoothHidService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent disconnectPending = PendingIntent.getService(
                this,
                0,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Wiler Gamepad";
        String content = (deviceName != null && !deviceName.isEmpty())
                ? "Đang kết nối với: " + deviceName
                : "Đang duy trì kết nối tay cầm";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ngắt kết nối", disconnectPending)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    public void stopForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Wiler Gamepad Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Duy trì kết nối tay cầm Bluetooth Wiler");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "BluetoothHidService onDestroy");
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {
        }
        inputDispatcher.stop();
        hidManager.cleanup();
    }

    @Override
    public void onHidProxyStatusChanged(boolean available) {
        if (callback != null) callback.onHidProxyStatusChanged(available);
    }

    @Override
    public void onAppRegistrationChanged(boolean registered) {
        if (callback != null) callback.onAppRegistrationChanged(registered);
    }

    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {
        if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
            inputDispatcher.sendCurrentState();
        }
        if (callback != null) callback.onConnectionStateChanged(device, state);
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.d(TAG, "Bluetooth turned OFF; cleaning up proxy");
                    hidManager.cleanup();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth turned ON; reinitializing proxy");
                    hidManager.init();
                }
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE);
                Log.d(TAG, "ACTION_SCAN_MODE_CHANGED: " + scanMode);
                if (callback != null) {
                    callback.onScanModeChanged(scanMode);
                }
            }
        }
    };
}
