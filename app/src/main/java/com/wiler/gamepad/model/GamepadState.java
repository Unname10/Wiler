package com.wiler.gamepad.model;

/**
 * Thread-safe container representing the instantaneous state of the gamepad controls.
 */
public class GamepadState {

    // Button bitmasks
    public static final int BTN_A            = 1 << 0;
    public static final int BTN_B            = 1 << 1;
    public static final int BTN_X            = 1 << 2;
    public static final int BTN_Y            = 1 << 3;
    public static final int BTN_LB           = 1 << 4;
    public static final int BTN_RB           = 1 << 5;
    public static final int BTN_LT_BTN       = 1 << 6;
    public static final int BTN_RT_BTN       = 1 << 7;
    public static final int BTN_VIEW_BACK    = 1 << 8;
    public static final int BTN_MENU_START   = 1 << 9;
    public static final int BTN_LS_CLICK     = 1 << 10;
    public static final int BTN_RS_CLICK     = 1 << 11;
    public static final int BTN_GUIDE        = 1 << 12;

    // Hat switch directions
    public static final byte HAT_UP          = 0;
    public static final byte HAT_UP_RIGHT    = 1;
    public static final byte HAT_RIGHT       = 2;
    public static final byte HAT_DOWN_RIGHT  = 3;
    public static final byte HAT_DOWN        = 4;
    public static final byte HAT_DOWN_LEFT   = 5;
    public static final byte HAT_LEFT        = 6;
    public static final byte HAT_UP_LEFT     = 7;
    public static final byte HAT_CENTER      = 8; // Neutral / Released

    private int buttons = 0;
    private byte hat = HAT_CENTER;

    private float leftStickX = 0.0f;   // -1.0 to 1.0
    private float leftStickY = 0.0f;   // -1.0 to 1.0
    private float rightStickX = 0.0f;  // -1.0 to 1.0
    private float rightStickY = 0.0f;  // -1.0 to 1.0

    private float triggerLT = 0.0f;    // 0.0 to 1.0
    private float triggerRT = 0.0f;    // 0.0 to 1.0

    public GamepadState() {
    }

    public synchronized void setButton(int buttonMask, boolean pressed) {
        if (pressed) {
            buttons |= buttonMask;
        } else {
            buttons &= ~buttonMask;
        }
    }

    public synchronized boolean isButtonPressed(int buttonMask) {
        return (buttons & buttonMask) != 0;
    }

    public synchronized int getButtons() {
        return buttons;
    }

    public synchronized void setHat(byte hatDirection) {
        this.hat = hatDirection;
    }

    public synchronized byte getHat() {
        return hat;
    }

    public synchronized void setLeftStick(float x, float y) {
        this.leftStickX = Math.max(-1.0f, Math.min(x, 1.0f));
        this.leftStickY = Math.max(-1.0f, Math.min(y, 1.0f));
    }

    public synchronized float getLeftStickX() {
        return leftStickX;
    }

    public synchronized float getLeftStickY() {
        return leftStickY;
    }

    public synchronized void setRightStick(float x, float y) {
        this.rightStickX = Math.max(-1.0f, Math.min(x, 1.0f));
        this.rightStickY = Math.max(-1.0f, Math.min(y, 1.0f));
    }

    public synchronized float getRightStickX() {
        return rightStickX;
    }

    public synchronized float getRightStickY() {
        return rightStickY;
    }

    public synchronized void setTriggers(float lt, float rt) {
        this.triggerLT = Math.max(0.0f, Math.min(lt, 1.0f));
        this.triggerRT = Math.max(0.0f, Math.min(rt, 1.0f));
    }

    public synchronized float getTriggerLT() {
        return triggerLT;
    }

    public synchronized float getTriggerRT() {
        return triggerRT;
    }

    public synchronized void resetAll() {
        buttons = 0;
        hat = HAT_CENTER;
        leftStickX = 0.0f;
        leftStickY = 0.0f;
        rightStickX = 0.0f;
        rightStickY = 0.0f;
        triggerLT = 0.0f;
        triggerRT = 0.0f;
    }

    public synchronized boolean isNeutral() {
        return buttons == 0
                && hat == HAT_CENTER
                && leftStickX == 0.0f
                && leftStickY == 0.0f
                && rightStickX == 0.0f
                && rightStickY == 0.0f
                && triggerLT == 0.0f
                && triggerRT == 0.0f;
    }

    public synchronized GamepadState copy() {
        GamepadState copy = new GamepadState();
        copy.buttons = this.buttons;
        copy.hat = this.hat;
        copy.leftStickX = this.leftStickX;
        copy.leftStickY = this.leftStickY;
        copy.rightStickX = this.rightStickX;
        copy.rightStickY = this.rightStickY;
        copy.triggerLT = this.triggerLT;
        copy.triggerRT = this.triggerRT;
        return copy;
    }
}
