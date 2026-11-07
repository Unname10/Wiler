package com.wiler.gamepad.hid;

import com.wiler.gamepad.model.GamepadState;

/**
 * Xbox-like HID Gamepad descriptor and report serializer.
 * Uses consecutive DirectInput button numbering:
 * Button 1: A, Button 2: B, Button 3: X, Button 4: Y,
 * Button 5: LB, Button 6: RB, Button 7: Back, Button 8: Start,
 * Button 9: L3 (LS Click), Button 10: R3 (RS Click), Button 11: Guide,
 * Button 12: LT Button, Button 13: RT Button.
 */
public final class XboxLikeHidDescriptor {

    public static final byte REPORT_ID = 1;
    public static final int REPORT_DATA_SIZE = 9;

    /**
     * HID Descriptor for Xbox-like Controller:
     * - Left Stick X, Y
     * - Right Stick Z, Rz
     * - Triggers Rx, Ry
     * - Hat Switch (4 bits) + 4 bits padding
     * - 16 Buttons (consecutive DirectInput layout)
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

    private XboxLikeHidDescriptor() {
    }

    public static byte[] buildReport(GamepadState state) {
        byte[] data = new byte[REPORT_DATA_SIZE];

        data[0] = (byte) clampToByte((state.getLeftStickX() + 1.0f) * 127.5f);
        data[1] = (byte) clampToByte((state.getLeftStickY() + 1.0f) * 127.5f);
        data[2] = (byte) clampToByte((state.getRightStickX() + 1.0f) * 127.5f);
        data[3] = (byte) clampToByte((state.getRightStickY() + 1.0f) * 127.5f);

        data[4] = (byte) clampToByte(state.getTriggerLT() * 255.0f);
        data[5] = (byte) clampToByte(state.getTriggerRT() * 255.0f);

        byte hat = state.getHat();
        data[6] = (hat < 8) ? (byte) ((hat + 1) & 0x0F) : 0;

        // Consecutive DirectInput Xbox button layout:
        // Button 1: A, Button 2: B, Button 3: X, Button 4: Y
        // Button 5: LB, Button 6: RB, Button 7: Back, Button 8: Start
        // Button 9: L3 (LS Click), Button 10: R3 (RS Click), Button 11: Guide
        // Button 12: LT Button, Button 13: RT Button
        int outButtons = 0;
        if (state.isButtonPressed(GamepadState.BTN_A)) outButtons |= (1 << 0);
        if (state.isButtonPressed(GamepadState.BTN_B)) outButtons |= (1 << 1);
        if (state.isButtonPressed(GamepadState.BTN_X)) outButtons |= (1 << 2);
        if (state.isButtonPressed(GamepadState.BTN_Y)) outButtons |= (1 << 3);
        if (state.isButtonPressed(GamepadState.BTN_LB)) outButtons |= (1 << 4);
        if (state.isButtonPressed(GamepadState.BTN_RB)) outButtons |= (1 << 5);
        if (state.isButtonPressed(GamepadState.BTN_VIEW_BACK)) outButtons |= (1 << 6);
        if (state.isButtonPressed(GamepadState.BTN_MENU_START)) outButtons |= (1 << 7);
        if (state.isButtonPressed(GamepadState.BTN_LS_CLICK)) outButtons |= (1 << 8);
        if (state.isButtonPressed(GamepadState.BTN_RS_CLICK)) outButtons |= (1 << 9);
        if (state.isButtonPressed(GamepadState.BTN_GUIDE)) outButtons |= (1 << 10);
        if (state.isButtonPressed(GamepadState.BTN_LT_BTN)) outButtons |= (1 << 11);
        if (state.isButtonPressed(GamepadState.BTN_RT_BTN)) outButtons |= (1 << 12);

        data[7] = (byte) (outButtons & 0xFF);
        data[8] = (byte) ((outButtons >> 8) & 0xFF);

        return data;
    }

    public static byte[] buildNeutralReport() {
        byte[] data = new byte[REPORT_DATA_SIZE];
        data[0] = (byte) 128;
        data[1] = (byte) 128;
        data[2] = (byte) 128;
        data[3] = (byte) 128;
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 0;
        data[8] = 0;
        return data;
    }

    private static int clampToByte(float value) {
        int rounded = Math.round(value);
        if (rounded < 0) return 0;
        if (rounded > 255) return 255;
        return rounded;
    }
}
