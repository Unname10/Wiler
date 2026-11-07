# 📋 Danh sách Lỗi & Kế hoạch Theo dõi (Bug Tracking & Implementation Checklist)

Dự án: **Wiler Gamepad**  
Mục tiêu: Khắc phục các lỗi về kết nối, giao diện và điều khiển cảm ứng gamepad phát hiện trong quá trình kiểm thử thực tế.

---

## 📌 Checklist Tiến độ Xử lý

### 1. Đồng bộ Trạng thái Kết nối & Nút "Ngắt kết nối" (Bluetooth Connection & State Sync)
- [x] **1.1. Sửa nút "Ngắt kết nối" trên Notification của Service**
  - **Vấn đề:** Nút "Ngắt kết nối" trên Notification không hoạt động (do PendingIntent gọi Service bị hạn chế trên Android 12-14 hoặc handler `ACTION_DISCONNECT` chưa dứt điểm).
  - **Giải pháp:** Chuyển sang dùng `PendingIntent.getBroadcast` kết hợp đăng ký BroadcastReceiver nội bộ trong `BluetoothHidService`, xử lý ngắt kết nối dứt điểm, xóa Foreground Notification và phát thông báo `STATE_DISCONNECTED` đến mọi listener.
- [x] **1.2. Hỗ trợ Đa Callback (`ServiceCallback`) trong `BluetoothHidService`**
  - **Vấn đề:** `BluetoothHidService` chỉ có một biến callback duy nhất `private ServiceCallback callback;`. Khi mở `GamepadActivity`, callback của `HomeActivity` bị ghi đè, và khi `GamepadActivity` đóng thì set `callback = null`, khiến `HomeActivity` và `ConnectionBottomSheet` không nhận được bất kỳ sự kiện kết nối/ngắt kết nối nào nữa.
  - **Giải pháp:** Đã chuyển sang cơ chế `registerCallback(cb)` và `unregisterCallback(cb)` sử dụng thread-safe `CopyOnWriteArrayList<ServiceCallback>`, đảm bảo cả `HomeActivity` và `GamepadActivity` duy trì listener độc lập, không bị ghi đè hay mất đăng ký.
- [x] **1.3. Lắng nghe Intent hệ thống `BluetoothHidDevice.ACTION_CONNECTION_STATE_CHANGED`**
  - **Vấn đề:** Khi PC tắt Bluetooth hoặc ngắt kết nối từ Windows, proxy callback đôi khi bị miss hoặc không gọi đến listener.
  - **Giải pháp:** Đăng ký nhận `android.bluetooth.hiddevice.profile.action.CONNECTION_STATE_CHANGED` trực tiếp trong `bluetoothReceiver` của Service làm kênh bắt sự kiện ngắt kết nối song song, đảm bảo 100% không bị sót.
- [x] **1.4. Cập nhật tức thì trạng thái UI khi bấm "Ngắt kết nối"**
  - **Vấn đề:** Bấm "Ngắt kết nối" trong `ConnectionBottomSheet` không đổi nút sang "Kết nối", nút "Vào Gamepad" ở `HomeActivity` không đổi lại thành "Kết nối".
  - **Giải pháp:** Cải tiến `hidManager.disconnect()` ngắt toàn bộ danh sách `getConnectedDevices()`; cập nhật ngay UI state (optimistic UI update) cả trong `ConnectionBottomSheet` và `HomeActivity` ngay khi bấm nút.

---

### 2. Tùy chỉnh Kích thước Nút, Nút Mũi tên & Center Icon Cài đặt (Gamepad UI & Controls)
- [x] **2.1. Thêm Tùy chỉnh Kích thước Nút bấm trong Cài đặt (`SettingsBottomSheet`)**
  - Đã thêm `buttonScale` (từ 80% đến 140%, mặc định 115%) vào `PreferencesManager`.
  - Đã thêm Card điều chỉnh kích thước nút với thanh kéo Slider kèm badge hiển thị phần trăm trực quan trong `bottom_sheet_settings.xml`.
  - Kết nối callback `onButtonScaleChanged` giữa `SettingsBottomSheet`, `GamepadActivity`, và `GamepadView`.
- [x] **2.2. Tăng kích thước mặc định của các nút trên `GamepadView`**
  - Tăng hệ số bán kính cơ sở các nút từ `0.07f` lên `0.075f * buttonScale` (to hơn mặc định khoảng 25%).
  - **Tăng mạnh kích thước 2 nút mũi tên** (`rectBack` ◀ và `rectStart` ▶) cạnh nút Setting từ kích thước nhỏ (80x50px) lên dạng viên thuốc lớn (chiều ngang ~110-150px, chiều cao ~70-90px), khoảng cách nhấn rộng rãi, dễ bấm trúng.
- [x] **2.3. Sửa Icon nút Setting bị lệch tâm (Center Alignment)**
  - **Vấn đề:** Nút Setting ở giữa trước đây vẽ bằng ký tự text `"☼"`, bị lệch tâm do font metrics (ascent/descent) của Android.
  - **Giải pháp:** Đã chuyển sang vẽ icon bánh răng chuẩn từ VectorDrawable `ic_settings` bằng Canvas `setBounds(cx - half, cy - half, cx + half, cy + half)` và `drawable.draw(canvas)`, đảm bảo icon nằm chính xác 100% tại tâm hình tròn.

---

### 3. Giới hạn Vùng Hiệu quả của Joycon (Joystick Active Touch Zone)
- [x] **3.1. Giới hạn Vùng Chạm kích hoạt Joystick (Touch Bounding Zone)**
  - **Vấn đề:** Trước đây khi nhấn trượt các nút, ứng dụng chia đôi toàn bộ nửa màn hình trái/phải cho 2 Joycon, khiến việc bấm trượt ABXY, D-pad hoặc nút vai làm Joycon bị teleport và nhận tín hiệu điều khiển ngoài ý muốn.
  - **Giải pháp:** 
    - Lưu vị trí anchor tâm mặc định của Left Stick (`defaultLeftStickBaseX, defaultLeftStickBaseY`) và Right Stick (`defaultRightStickBaseX, defaultRightStickBaseY`).
    - Giới hạn bán kính hiệu quả tối đa quanh tâm joystick: `dist <= stickRadius * 1.65f`.
    - Loại trừ dải phía trên của màn hình (`y < h * 0.30f` - nơi chứa nút vai và cụm phím menu giữa).
    - Khi điểm chạm nằm ngoài vùng hiệu quả của joystick và không trúng nút nào, bỏ qua không kích hoạt hay di chuyển Joycon.
    - Khi nhấc tay khỏi Joycon, tâm stick tự động trở về vị trí anchor mặc định, không lưu vị trí lệch.

---

## 🧪 Ma trận Kiểm thử Thực tế Đã Sẵn Sàng (Verification Checklist)
- [x] Build APK Debug hoàn thành thành công: `app\build\outputs\apk\debug\app-debug.apk`.
- [ ] Bấm nút "Ngắt kết nối" trên Notification -> Notification biến mất, các màn hình đổi về "Kết nối".
- [ ] Bấm nút "Ngắt kết nối" trong `ConnectionBottomSheet` -> Nút đổi ngay thành "Kết nối", nút ngoài `HomeActivity` đổi thành "Kết nối".
- [ ] Tắt Bluetooth từ PC -> App lập tức nhận diện trạng thái mất kết nối, chuyển nút về "Kết nối".
- [ ] Vào Settings đổi kích thước nút -> Gamepad cập nhật kích thước phím to/nhỏ thời gian thực.
- [ ] Kiểm tra 2 nút mũi tên và nút Setting ở giữa: icon bánh răng nằm ngay chính giữa tâm, 2 nút mũi tên to rõ, dễ bấm.
- [ ] Bấm trượt các nút ABXY, D-pad hoặc chạm vùng trống ở xa -> Joycon giữ nguyên vị trí, không bị nhảy góc.
