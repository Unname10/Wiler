package com.wiler.gamepad.hid;

import com.wiler.gamepad.model.GamepadState;

/**
 * Standard Generic Desktop HID Gamepad descriptor and report serializer.
 * Universally recognized by Windows (DirectInput / Gamepad Tester), Android, Linux, and macOS.
 */
public final class StandardHidDescriptor {

    public static final byte REPORT_ID = 1;
    public static final int REPORT_DATA_SIZE = 9;

    /**
     * Clean HID Report Descriptor for Generic Gamepad:
     * - Report ID 1
     * - Left Stick X, Y (0 to 255, Center 128) -> 2 bytes
     * - Right Stick Z, Rz (0 to 255, Center 128) -> 2 bytes
     * - Triggers Rx, Ry (0 to 255, Neutral 0) -> 2 bytes
     * - Hat Switch 8-way (1-8, 0 for Centered / Null) + 4-bit padding -> 1 byte
     * - 16 Action Buttons -> 2 bytes
     * Total = 9 bytes payload.
     */
    public static final byte[] DESCRIPTOR = new byte[]{
            (byte) 0x05, (byte) 0x01,        // Usage Page (Generic Desktop Ctrls)
            (byte) 0x09, (byte) 0x05,        // Usage (Game Pad)
            (byte) 0xA1, (byte) 0x01,        // Collection (Application)
            (byte) 0x85, REPORT_ID,          //   Report ID (1)

            // --- Left Stick: X, Y (0 to 255, Center 128) ---
            (byte) 0x05, (byte) 0x01,        //   Usage Page (Generic Desktop Ctrls)
            (byte) 0x09, (byte) 0x30,        //   Usage (X) - Left Stick X
            (byte) 0x09, (byte) 0x31,        //   Usage (Y) - Left Stick Y
            (byte) 0x15, (byte) 0x00,        //   Logical Minimum (0)
            (byte) 0x26, (byte) 0xFF, (byte) 0x00, // Logical Maximum (255)
            (byte) 0x75, (byte) 0x08,        //   Report Size (8 bits)
            (byte) 0x95, (byte) 0x02,        //   Report Count (2)
            (byte) 0x81, (byte) 0x02,        //   Input (Data,Var,Abs)

            // --- Right Stick: Z, Rz (0 to 255, Center 128) ---
            (byte) 0x09, (byte) 0x32,        //   Usage (Z) - Right Stick X
            (byte) 0x09, (byte) 0x35,        //   Usage (Rz) - Right Stick Y
            (byte) 0x15, (byte) 0x00,        //   Logical Minimum (0)
            (byte) 0x26, (byte) 0xFF, (byte) 0x00, // Logical Maximum (255)
            (byte) 0x75, (byte) 0x08,        //   Report Size (8 bits)
            (byte) 0x95, (byte) 0x02,        //   Report Count (2)
            (byte) 0x81, (byte) 0x02,        //   Input (Data,Var,Abs)

            // --- Triggers: Rx (LT), Ry (RT) (0 to 255) ---
            (byte) 0x09, (byte) 0x33,        //   Usage (Rx) - Trigger LT
            (byte) 0x09, (byte) 0x34,        //   Usage (Ry) - Trigger RT
            (byte) 0x15, (byte) 0x00,        //   Logical Minimum (0)
            (byte) 0x26, (byte) 0xFF, (byte) 0x00, // Logical Maximum (255)
            (byte) 0x75, (byte) 0x08,        //   Report Size (8 bits)
            (byte) 0x95, (byte) 0x02,        //   Report Count (2)
            (byte) 0x81, (byte) 0x02,        //   Input (Data,Var,Abs)

            // --- Hat Switch (D-Pad): 1-8 directions, 0 = Centered / Null ---
            (byte) 0x09, (byte) 0x39,        //   Usage (Hat switch)
            (byte) 0x15, (byte) 0x01,        //   Logical Minimum (1)
            (byte) 0x25, (byte) 0x08,        //   Logical Maximum (8)
            (byte) 0x75, (byte) 0x04,        //   Report Size (4 bits)
            (byte) 0x95, (byte) 0x01,        //   Report Count (1)
            (byte) 0x81, (byte) 0x42,        //   Input (Data,Var,Abs,Null)

            // --- 4 bits padding for Hat to byte boundary ---
            (byte) 0x75, (byte) 0x04,        //   Report Size (4 bits)
            (byte) 0x95, (byte) 0x01,        //   Report Count (1)
            (byte) 0x81, (byte) 0x03,        //   Input (Const,Var,Abs)

            // --- 16 Action Buttons ---
            (byte) 0x05, (byte) 0x09,        //   Usage Page (Button)
            (byte) 0x19, (byte) 0x01,        //   Usage Minimum (Button 1)
            (byte) 0x29, (byte) 0x10,        //   Usage Maximum (Button 16)
            (byte) 0x15, (byte) 0x00,        //   Logical Minimum (0)
            (byte) 0x25, (byte) 0x01,        //   Logical Maximum (1)
            (byte) 0x75, (byte) 0x01,        //   Report Size (1 bit)
            (byte) 0x95, (byte) 0x10,        //   Report Count (16)
            (byte) 0x81, (byte) 0x02,        //   Input (Data,Var,Abs)

            (byte) 0xC0                      // End Collection
    };

    private StandardHidDescriptor() {
        // Utility
    }

    /**
     * Serializes GamepadState into a 9-byte HID report buffer.
     * Axes are mapped to unsigned 0..255 (Center = 128).
     * Note: Does NOT include the Report ID byte.
     */
    public static byte[] buildReport(GamepadState state) {
        byte[] data = new byte[REPORT_DATA_SIZE];

        // 1. Left Stick X (-1.0..1.0 -> 0..255, center 128)
        data[0] = (byte) clampToByte((state.getLeftStickX() + 1.0f) * 127.5f);
        // 2. Left Stick Y (-1.0..1.0 -> 0..255, center 128)
        data[1] = (byte) clampToByte((state.getLeftStickY() + 1.0f) * 127.5f);
        // 3. Right Stick X (-1.0..1.0 -> 0..255, center 128)
        data[2] = (byte) clampToByte((state.getRightStickX() + 1.0f) * 127.5f);
        // 4. Right Stick Y (-1.0..1.0 -> 0..255, center 128)
        data[3] = (byte) clampToByte((state.getRightStickY() + 1.0f) * 127.5f);

        // 5. Trigger LT (0.0..1.0 -> 0..255)
        data[4] = (byte) clampToByte(state.getTriggerLT() * 255.0f);
        // 6. Trigger RT (0.0..1.0 -> 0..255)
        data[5] = (byte) clampToByte(state.getTriggerRT() * 255.0f);

        // 7. Hat Switch: 1-8 for directions, 0 for Centered / Null
        byte hat = state.getHat();
        if (hat < 8) {
            // GamepadState hat: 0 = Up -> HID hat: 1
            // 1 = Up-Right -> HID: 2, etc.
            data[6] = (byte) ((hat + 1) & 0x0F);
        } else {
            // 0 = Centered / Null (NEVER 0x08, so never triggers 'e')
            data[6] = 0;
        }

        // 8 & 9. 16 Buttons mapped to standard Linux evdev / Android Gamepad specification:
        // Button 1 (bit 0): BTN_A (0x130)
        // Button 2 (bit 1): BTN_B (0x131)
        // Button 3 (bit 2): Unused / BTN_C (0x132)
        // Button 4 (bit 3): BTN_X (0x133)
        // Button 5 (bit 4): BTN_Y (0x134)
        // Button 6 (bit 5): Unused / BTN_Z (0x135)
        // Button 7 (bit 6): BTN_TL / L1 / LB (0x136)
        // Button 8 (bit 7): BTN_TR / R1 / RB (0x137)
        // Button 9 (bit 8): BTN_TL2 / L2 / LT Button (0x138)
        // Button 10 (bit 9): BTN_TR2 / R2 / RT Button (0x139)
        // Button 11 (bit 10): BTN_SELECT / Back (0x13a)
        // Button 12 (bit 11): BTN_START / Start (0x13b)
        // Button 13 (bit 12): BTN_MODE / Guide (0x13c)
        // Button 14 (bit 13): BTN_THUMBL / L3 (0x13d)
        // Button 15 (bit 14): BTN_THUMBR / R3 (0x13e)
        // Button 16 (bit 15): Unused
        int outButtons = 0;
        if (state.isButtonPressed(GamepadState.BTN_A)) outButtons |= (1 << 0);
        if (state.isButtonPressed(GamepadState.BTN_B)) outButtons |= (1 << 1);
        // bit 2 is intentionally left 0 for BTN_C compatibility
        if (state.isButtonPressed(GamepadState.BTN_X)) outButtons |= (1 << 3);
        if (state.isButtonPressed(GamepadState.BTN_Y)) outButtons |= (1 << 4);
        // bit 5 is intentionally left 0 for BTN_Z compatibility
        if (state.isButtonPressed(GamepadState.BTN_LB)) outButtons |= (1 << 6);
        if (state.isButtonPressed(GamepadState.BTN_RB)) outButtons |= (1 << 7);
        if (state.isButtonPressed(GamepadState.BTN_LT_BTN)) outButtons |= (1 << 8);
        if (state.isButtonPressed(GamepadState.BTN_RT_BTN)) outButtons |= (1 << 9);
        if (state.isButtonPressed(GamepadState.BTN_VIEW_BACK)) outButtons |= (1 << 10);
        if (state.isButtonPressed(GamepadState.BTN_MENU_START)) outButtons |= (1 << 11);
        if (state.isButtonPressed(GamepadState.BTN_GUIDE)) outButtons |= (1 << 12);
        if (state.isButtonPressed(GamepadState.BTN_LS_CLICK)) outButtons |= (1 << 13);
        if (state.isButtonPressed(GamepadState.BTN_RS_CLICK)) outButtons |= (1 << 14);

        data[7] = (byte) (outButtons & 0xFF);
        data[8] = (byte) ((outButtons >> 8) & 0xFF);

        return data;
    }

    /**
     * Generates a neutral (all keys released, axes centered at 128, hat = 0) 9-byte report.
     */
    public static byte[] buildNeutralReport() {
        byte[] data = new byte[REPORT_DATA_SIZE];
        data[0] = (byte) 128; // Stick X = Center (128)
        data[1] = (byte) 128; // Stick Y = Center (128)
        data[2] = (byte) 128; // Stick Z = Center (128)
        data[3] = (byte) 128; // Stick Rz = Center (128)
        data[4] = 0;          // LT = 0
        data[5] = 0;          // RT = 0
        data[6] = 0;          // Hat = 0 (Centered / Null)
        data[7] = 0;          // Buttons 1-8 = 0
        data[8] = 0;          // Buttons 9-16 = 0
        return data;
    }

    private static int clampToByte(float value) {
        int rounded = Math.round(value);
        if (rounded < 0) return 0;
        if (rounded > 255) return 255;
        return rounded;
    }
}
