package com.wiler.gamepad.model;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages user preferences for Wiler Gamepad:
 * - Analog stick deadzone percentage
 * - Dynamic joystick toggle
 * - Auto-reconnect toggle
 * - Gamepad profile selection (Xbox-like vs Standard HID)
 * - Last connected device MAC and name
 */
public class PreferencesManager {

    private static final String PREF_NAME = "wiler_settings";

    private static final String KEY_DEADZONE = "key_deadzone";
    private static final String KEY_DYNAMIC_JOYSTICK = "key_dynamic_joystick";
    private static final String KEY_AUTO_RECONNECT = "key_auto_reconnect";
    private static final String KEY_PROFILE = "key_profile";
    private static final String KEY_LAST_DEVICE_ADDR = "key_last_device_addr";
    private static final String KEY_LAST_DEVICE_NAME = "key_last_device_name";

    public static final int PROFILE_XBOX_LIKE = 0;
    public static final int PROFILE_STANDARD_HID = 1;

    private static PreferencesManager instance;
    private final SharedPreferences prefs;

    private PreferencesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesManager(context);
        }
        return instance;
    }

    /**
     * Deadzone as a float between 0.0f and 0.25f (default: 0.10f = 10%)
     */
    public float getDeadzone() {
        return prefs.getFloat(KEY_DEADZONE, 0.10f);
    }

    public void setDeadzone(float deadzone) {
        prefs.edit().putFloat(KEY_DEADZONE, Math.max(0.0f, Math.min(deadzone, 0.25f))).apply();
    }

    /**
     * Whether dynamic stick placement on touch-down is enabled (default: true)
     */
    public boolean isDynamicJoystick() {
        return prefs.getBoolean(KEY_DYNAMIC_JOYSTICK, true);
    }

    public void setDynamicJoystick(boolean enabled) {
        prefs.edit().putBoolean(KEY_DYNAMIC_JOYSTICK, enabled).apply();
    }

    /**
     * Auto-reconnect to last bonded host (default: true)
     */
    public boolean isAutoReconnect() {
        return prefs.getBoolean(KEY_AUTO_RECONNECT, true);
    }

    public void setAutoReconnect(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply();
    }

    /**
     * Selected profile: PROFILE_XBOX_LIKE or PROFILE_STANDARD_HID
     */
    public int getProfile() {
        return prefs.getInt(KEY_PROFILE, PROFILE_XBOX_LIKE);
    }

    public void setProfile(int profile) {
        prefs.edit().putInt(KEY_PROFILE, profile).apply();
    }

    public String getLastDeviceAddress() {
        return prefs.getString(KEY_LAST_DEVICE_ADDR, null);
    }

    public String getLastDeviceName() {
        return prefs.getString(KEY_LAST_DEVICE_NAME, null);
    }

    public void setLastConnectedDevice(String address, String name) {
        prefs.edit()
                .putString(KEY_LAST_DEVICE_ADDR, address)
                .putString(KEY_LAST_DEVICE_NAME, name)
                .apply();
    }
}
