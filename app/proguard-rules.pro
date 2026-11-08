# ProGuard rules for Wiler Gamepad

# Keep all custom UI views used in XML layouts
-keep public class com.wiler.gamepad.ui.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep models used for JSON serialization and state
-keep class com.wiler.gamepad.model.** { *; }

# Keep HID Service, callbacks, and native Bluetooth wrappers
-keep class com.wiler.gamepad.service.BluetoothHidService** { *; }
-keep class com.wiler.gamepad.hid.** { *; }

