# Kế hoạch phát triển tổng thể ứng dụng Wiler (Master Implementation Plan)

Tài liệu này là bản kế hoạch tổng thể duy nhất, hợp nhất toàn bộ thiết kế giao diện (bám sát 4 ảnh trong `assets/designs`), giải pháp kiến trúc Backend Bluetooth HID chuyên sâu, các quy tắc xử lý ngắt kết nối/đa điểm triệt để, và lộ trình chia theo **Milestone 1 (Prototype tối thiểu)** và **Milestone 2 (Ráp giao diện hoàn chỉnh)**.

---

## 1. Giới hạn Kỹ thuật Thực tế & Bảng Quyền Hệ thống

### 1.1. Bản chất Profile Gamepad & Windows XInput
- `BluetoothHidDevice` của Android hoạt động trên nền **Bluetooth Classic (BR/EDR)**. Windows nhận diện Wiler là một **DirectInput HID Gamepad**.
- **Quy ước Profile:**
  - **`StandardHidDescriptor` (Mặc định / Chính):** Tương thích cao nhất trên mọi nền tảng (Windows DirectInput, Android, Linux, macOS).
  - **`XboxLikeHidDescriptor` (Tùy chọn):** Bố cục trục và nút bấm mô phỏng Xbox.
  - **Chơi game XInput trên Windows:** Người dùng sử dụng tính năng **Steam Input** (Steam tự map mọi HID Gamepad thành XInput cho mọi game) hoặc phần mềm **x360ce** / **HidHide + vJoy**.

### 1.2. Bảng kê khai Quyền hạn (Permissions Matrix)
- Không cần xin quyền `BLUETOOTH_SCAN` hay `ACCESS_FINE_LOCATION`: Ứng dụng đọc trực tiếp giá trị `EXTRA_SCAN_MODE` từ Intent của BroadcastReceiver `ACTION_SCAN_MODE_CHANGED`.

| Quyền | Cấp độ API | Khai báo Manifest | Xin quyền Runtime? | Mục đích |
| :--- | :--- | :--- | :---: | :--- |
| `BLUETOOTH` | API 28 - 30 | `android:maxSdkVersion="30"` | Không | Quản lý kết nối Bluetooth cũ |
| `BLUETOOTH_ADMIN` | API 28 - 30 | `android:maxSdkVersion="30"` | Không | Bật chế độ tìm kiếm cũ |
| `BLUETOOTH_CONNECT` | API 31+ (Android 12+) | Bình thường | **Có (Bắt buộc)** | Kết nối & gửi gói tin HID |
| `BLUETOOTH_ADVERTISE` | API 31+ (Android 12+) | Bình thường | **Có (Bắt buộc)** | Phát tín hiệu ghép đôi với PC |
| `FOREGROUND_SERVICE` | API 28+ | Bình thường | Không | Duy trì dịch vụ khi trong Gamepad |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34+ (Android 14+) | Bình thường | Không | Bắt buộc cho service dạng thiết bị |
| `POST_NOTIFICATIONS` | API 33+ (Android 13+) | Bình thường | **Có (Tùy chọn)** | Hiện notification thông báo kết nối |

- Khởi tạo Bluetooth HID (`getProfileProxy` / `registerApp`) và gọi `startForeground` với loại `connectedDevice` bắt buộc phải diễn ra **sau khi đã có quyền `BLUETOOTH_CONNECT`**, tránh gây `SecurityException`.

---

## 2. Kiến trúc Hệ thống & Luồng Dữ liệu

```
[ Frontend Activities & Sheets ]
  ├── HomeActivity (Portrait)           <-- Màn hình Home (Home.png)
  ├── GamepadActivity (Landscape)      <-- Màn hình tay cầm (Gamepad.png), FLAG_KEEP_SCREEN_ON
  ├── ConnectionBottomSheet            <-- Quản lý ghép đôi (Connection.png)
  ├── SettingsBottomSheet              <-- Cài đặt & Deadzone Preview (Settings.png)
  └── InGameMenuDialog                 <-- Menu nhanh khi giữ nút Guide 1s

[ Backend Service & Lifecycle ]
  └── BluetoothHidService (Started + Bound Service)
        ├── startForeground            <-- Chỉ bật khi vào GamepadActivity
        ├── Notification               <-- "Wiler Gamepad: Đang kết nối" + Nút Ngắt kết nối
        ├── BluetoothStateReceiver     <-- Lắng nghe ACTION_STATE_CHANGED & ACTION_SCAN_MODE_CHANGED
        └── Auto stopSelf()            <-- Tự hủy khi ở Home và không còn kết nối

[ Bluetooth HID Engine ]
  ├── BluetoothHidManager              <-- Quản lý BluetoothHidDevice proxy, registerApp
  ├── ReconnectEngine                  <-- Thử kết nối lại tối đa 3 lần dựa trên callback
  └── InputDispatcher                  <-- HandlerThread + Trailing Send (8-10ms), State-based, Cap = 3

[ Models & Math Utils ]
  ├── GamepadState                     <-- Trạng thái nút bấm và trục analog thread-safe
  ├── InputMapper                      <-- Clamped Radial Deadzone (rc = min(raw,1), s = r'/raw, x'=x*s, y'=y*s)
  └── GamepadPreferences               <-- SharedPreferences: MAC, Profile, Deadzone, AutoReconnect, GuideMode
```

---

## 3. Đặc tả Kỹ thuật Chi tiết

### 3.1. Vòng đời Dịch vụ & Xử lý Trạng thái Bluetooth
- **Thời điểm gọi `initHid()`:** Nếu mở app mà quyền `BLUETOOTH_CONNECT` đã có sẵn thì gọi `initHid()` ngay. Nếu chưa có thì xin quyền xong mới gọi.
- **Chuyển đổi màn hình an toàn:** Khi chuyển từ Home sang Gamepad, Service sống liên tục, giữ nguyên kết nối Bluetooth.
- **Tự giải phóng khi ở nền (Home):** Tự động `stopSelf()` khi không còn client bind và không có kết nối Bluetooth nào active.
- **Lắng nghe tắt/bật Bluetooth của máy (`ACTION_STATE_CHANGED`):**
  - Khi `STATE_OFF`: Không gọi `unregisterApp()`. Chỉ xóa tham chiếu, gọi `closeProfileProxy()`.
  - Khi `STATE_ON`: Tự động gọi lại `getProfileProxy()` và `registerApp()`.
- **Đồng bộ trạng thái Ghép đôi:** Đọc `intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, ...)` từ `ACTION_SCAN_MODE_CHANGED` để cập nhật đếm ngược và trạng thái, không cần quyền `BLUETOOTH_SCAN`.
- **Notification của Foreground Service:** *"Wiler Gamepad: Đang kết nối với [Tên PC]"* kèm nút *"Ngắt kết nối"*.

### 3.2. Bộ điều phối Gói tin đơn luồng (Single-Threaded InputDispatcher)
- Chạy trên một **`HandlerThread`** với cờ `dirty` và cơ chế **Trailing Send** (8-10ms).
- **Gói tin Neutral:** Luôn được thử lại liên tục cho đến khi gửi thành công hoặc mất kết nối hoàn toàn, đảm bảo không bao giờ kẹt phím.
- **Gói tin thông thường:** Thử lại tối đa 3 lần nếu `sendReport() == false`, sau đó giữ `dirty = true`.
- Dispatcher ngủ khi idle (Zero polling). Gửi lại trạng thái hiện tại một lần ngay khi kết nối mới thành công.
- `reportData` **không chứa byte Report ID**. SDP subclass: Kiểm tra cả `SUBCLASS2_GAMEPAD` và `(byte) 0x08` ở M1. QoS latency: `11250` us. Stub rỗng cho `onSetReport` và `onInterruptData`.
- Độ trễ thực tế qua Bluetooth Classic: **10 - 30ms**.

### 3.3. Xử lý Cảm ứng Đa điểm, Dynamic Stick & Focus
- **Dynamic Stick:** Điểm chạm đầu tiên trong nửa màn hình sẽ làm tâm joystick.
- **Multi-touch:** `ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE` (duyệt tất cả pointer qua `getPointerId(i)`), `ACTION_POINTER_UP` (chỉ nhả đúng ngón vừa nhấc), `ACTION_CANCEL` (reset toàn bộ về Neutral).
- `GamepadActivity.onPause()` gọi `gamepadView.resetAll()`.
- `GamepadView` lắng nghe `onWindowFocusChanged(false)` và `onDetachedFromWindow()` để reset về Neutral.
- Màn hình Gamepad: `sensorLandscape` và `FLAG_KEEP_SCREEN_ON`.

### 3.4. Xử lý Nút Guide và Menu Nhanh
- Chạm nút Guide: Bắt đầu tính giờ 1 giây (chưa gửi phím lên PC).
- Nhả tay trước 1 giây (nhấn ngắn): Gửi Guide Down -> Guide Up lên PC.
- Giữ quá 1 giây (nhấn giữ): Mở Menu Wiler và **không gửi phím Guide lên PC**.

### 3.5. Thuật toán Vùng chết (`InputMapper.java`)
- $r_{\text{raw}} = \sqrt{x^2 + y^2}$. Nếu $r_{\text{raw}} \le \text{deadzone}$ hoặc $r_{\text{raw}} == 0$: Trả về $(0, 0)$.
- $r_c = \min(r_{\text{raw}}, 1.0f)$, $r' = \frac{r_c - \text{deadzone}}{1.0f - \text{deadzone}}$, $s = \frac{r'}{r_{\text{raw}}}$.
- $x' = x \cdot s, \quad y' = y \cdot s$. (Không đảo dấu Y).

### 3.6. Chuỗi Đổi Profile Bất đồng bộ
1. `disconnect(currentDevice)` -> Đợi callback `STATE_DISCONNECTED`.
2. `unregisterApp()` -> Đợi callback `registered = false`.
3. `registerApp(newDescriptor)` -> Đợi callback `registered = true`.
4. Hướng dẫn người dùng xóa thiết bị và ghép đôi lại trên Windows.

### 3.7. Phạm vi Công tắc Tự động kết nối lại (Auto-reconnect)
Điều khiển cả 2 luồng: Tự động kết nối lại khi vừa mở app VÀ tự động thử kết nối lại tối đa 3 lần khi rớt kết nối đột ngột trong game. Nếu bấm "Ngắt kết nối" chủ động: Ngắt dứt khoát.

---

## 4. Kế hoạch Kiểm thử Toàn diện (Test Matrix)

1. Nhả một ngón khi các ngón khác đang giữ phím/cần.
2. Xoay màn hình (recreate Activity) không làm rớt kết nối hay mất đăng ký HID.
3. Logcat xác nhận Dispatcher dừng hoàn toàn khi idle.
4. Tắt Bluetooth trên PC -> Chuyển trạng thái sang ngắt kết nối và tự động thử lại 3 lần.
5. Tắt Bluetooth điện thoại -> Bắt `ACTION_STATE_CHANGED`, không crash. Bật lại -> Tự động kết nối lại proxy.
6. Từ chối quyền -> Thông báo cần quyền, không crash SecurityException.
7. Thiết bị không hỗ trợ HID -> Báo lỗi phần cứng rõ ràng.

---

## 5. Lộ trình Triển khai (Milestones)

### 🚀 Milestone 1: Prototype Tối thiểu (Xác thực Phần cứng, Nút bấm & Analog)
1. Cấu hình `build.gradle` (minSdk 28, compileSdk 34, namespace `com.wiler.gamepad`).
2. Khai báo đầy đủ manifest permissions và `BluetoothHidService` (`connectedDevice`).
3. Tạo lớp `StandardHidDescriptor`, `BluetoothHidManager`, `InputMapper`.
4. **Tích hợp `InputDispatcher` đơn luồng** (HandlerThread + throttle + trailing send).
5. Tạo `MainActivity` kiểm tra và xin quyền runtime (`BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`), khởi tạo HID khi đã có quyền.
6. Giao diện Prototype M1 gồm:
   - Nút *"Xin quyền Bluetooth"*.
   - Badge trạng thái `registerApp` (Đang kiểm tra / Sẵn sàng / Lỗi).
   - Nút *"Bật tìm kiếm (Discoverable)"* kèm trạng thái scan mode (đọc từ `EXTRA_SCAN_MODE`).
   - Trạng thái kết nối PC (Chưa kết nối / Đang kết nối / Đã kết nối với [Tên PC]).
   - **Nút test phím A (nhấn/nhả tức thì qua Dispatcher).**
   - **Một cần gạt Joystick ảo đơn giản để rê thử trục Analog X/Y qua InputMapper & Dispatcher.**
   - Nút *"Ngắt kết nối"*.
7. **Kiểm thử trên máy thật:** Nạp APK lên điện thoại thật, ghép đôi với PC, mở [gamepad-tester.com](https://gamepad-tester.com) xác nhận:
   - PC nhận Gamepad.
   - Bấm nút A: nút A sáng đèn.
   - Rê Joystick ảo: trục X, Y di chuyển mượt mà đúng hướng.

### 🎮 Milestone 2: Tích hợp Giao diện Hoàn chỉnh theo Thiết kế
1. Xây dựng `HomeActivity` (`Home.png`).
2. Xây dựng `GamepadActivity` và `GamepadView` (`Gamepad.png`).
3. Dựng `ConnectionBottomSheet` (`Connection.png`).
4. Dựng `SettingsBottomSheet` (`Settings.png`) kèm chuỗi state machine đổi Profile bất đồng bộ.
5. Dựng `InGameMenuDialog` và Overlay xử lý mất kết nối trong game.
6. Hoàn thiện thông báo Foreground Service kèm nút Ngắt kết nối.
7. Đóng gói hoàn chỉnh, kiểm thử toàn diện theo Test Matrix.
