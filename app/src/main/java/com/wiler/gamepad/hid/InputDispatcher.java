package com.wiler.gamepad.hid;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.wiler.gamepad.model.GamepadState;

import java.util.Arrays;

/**
 * Single-threaded dispatcher that handles rate limiting, trailing sends,
 * and guaranteed neutral packet delivery to the Bluetooth HID stack.
 */
public class InputDispatcher {

    private static final String TAG = "InputDispatcher";
    private static final long THROTTLE_MS = 8; // ~125Hz max rate
    private static final int MAX_NORMAL_RETRIES = 3;

    private final BluetoothHidManager hidManager;
    private final GamepadState gamepadState;

    private HandlerThread handlerThread;
    private Handler handler;

    private long lastSentTime = 0;
    private boolean isTrailingScheduled = false;
    private boolean dirty = false;
    private int normalRetryCount = 0;

    private byte[] lastSentData = null;

    private final Runnable dispatchRunnable = this::dispatchInternal;

    public InputDispatcher(BluetoothHidManager hidManager, GamepadState gamepadState) {
        this.hidManager = hidManager;
        this.gamepadState = gamepadState;
    }

    public synchronized void start() {
        if (handlerThread == null) {
            handlerThread = new HandlerThread("Wiler-InputDispatcher");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
            Log.d(TAG, "InputDispatcher started");
        }
    }

    public synchronized void stop() {
        if (handlerThread != null) {
            handler.removeCallbacksAndMessages(null);
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
            Log.d(TAG, "InputDispatcher stopped");
        }
    }

    /**
     * Called whenever UI / touch state has updated.
     */
    public void onInputChanged() {
        Handler h = handler;
        if (h != null) {
            h.post(dispatchRunnable);
        }
    }

    /**
     * Forces sending the current state immediately (e.g. after connection is established).
     */
    public void sendCurrentState() {
        Handler h = handler;
        if (h != null) {
            h.post(() -> {
                lastSentData = null; // Invalidate cache
                dispatchInternal();
            });
        }
    }

    private void dispatchInternal() {
        BluetoothDevice target = hidManager.getConnectedDevice();
        if (target == null) {
            dirty = false;
            isTrailingScheduled = false;
            return;
        }

        long now = SystemClock.uptimeMillis();
        long elapsed = now - lastSentTime;

        if (elapsed < THROTTLE_MS) {
            dirty = true;
            if (!isTrailingScheduled) {
                isTrailingScheduled = true;
                long delay = THROTTLE_MS - elapsed;
                handler.postDelayed(trailingRunnable, delay);
            }
            return;
        }

        // Snapshot current state
        GamepadState snapshot = gamepadState.copy();
        byte[] data = StandardHidDescriptor.buildReport(snapshot);

        // Skip duplicate packets if state has not changed
        if (lastSentData != null && Arrays.equals(data, lastSentData)) {
            dirty = false;
            return;
        }

        boolean isNeutral = snapshot.isNeutral();
        boolean success = hidManager.sendReport(StandardHidDescriptor.REPORT_ID, data);
        lastSentTime = SystemClock.uptimeMillis();

        if (success) {
            lastSentData = data;
            normalRetryCount = 0;
            dirty = false;
            if (isNeutral) {
                Log.d(TAG, "Neutral report successfully sent - entering idle sleep");
            }
        } else {
            Log.w(TAG, "sendReport returned false");
            if (isNeutral) {
                // Guaranteed delivery: neutral report must be retried until sent or disconnected
                handler.postDelayed(this::dispatchInternal, 5);
            } else {
                if (normalRetryCount < MAX_NORMAL_RETRIES) {
                    normalRetryCount++;
                    handler.postDelayed(this::dispatchInternal, 5);
                } else {
                    normalRetryCount = 0;
                    dirty = true; // Retain dirty flag so next event pushes fresh state
                }
            }
        }
    }

    private final Runnable trailingRunnable = new Runnable() {
        @Override
        public void run() {
            isTrailingScheduled = false;
            if (dirty) {
                dispatchInternal();
            }
        }
    };
}
