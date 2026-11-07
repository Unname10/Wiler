// Wiler Standard Gamepad Shim (v0.2)
// Chạy trong MAIN world của play.geforcenow.com, trước mã của trang (document_start).
// Bọc tay cầm Wiler thật thành một đối tượng Gamepad "standard" (W3C) mà GeForce NOW kỳ vọng.
// Khác v0.1: không tạo Proxy/đối tượng mới mỗi lần gọi getGamepads(); dùng 1 wrapper cố định
// cho mỗi tay cầm, cập nhật tại chỗ và bỏ qua khi timestamp không đổi.
(() => {
  'use strict';

  // ===================== CẤU HÌNH =====================
  const TAG = '[WilerShim]';
  const MATCH_ID = /wiler/i; // khớp với pad.id của tay cầm thật (không dùng cờ g/y)
  const FAKE_ID = 'Xbox 360 Controller (XInput STANDARD GAMEPAD)';
  const SKIP_UNCHANGED = true; // bỏ qua tính lại khi raw.timestamp không đổi
  const ARM_EPS = 0.1;         // trục trigger chỉ được tin sau khi từng chạm đáy (min + ARM_EPS)

  // Standard Gamepad (W3C), chỉ số nút:
  //  0 A, 1 B, 2 X, 3 Y, 4 LB, 5 RB, 6 LT, 7 RT, 8 Back, 9 Start,
  //  10 L3, 11 R3, 12 DUp, 13 DDown, 14 DLeft, 15 DRight, 16 Guide
  // Mỗi phần tử BUTTON_MAP là một trong:
  //   số n                         -> nút thô thứ n
  //   { axis, min, max, btn? }     -> trục thô quy về 0..1 (LT/RT); btn = nút thô dự phòng
  //   { axis, gt } | { axis, lt }  -> nút bấm khi trục vượt ngưỡng
  //   { hat, dirs }                -> hat switch trên trục thô `hat`; dirs: 0 N,1 NE,2 E,3 SE,4 S,5 SW,6 W,7 NW
  //   null                         -> không dùng
  const HAT = 9;
  const BUTTON_MAP = [
    0, 1, 2, 3,                                  // A B X Y
    4, 5,                                        // LB RB
    { axis: 3, min: -1, max: 1, btn: 6 },        // LT
    { axis: 4, min: -1, max: 1, btn: 7 },        // RT
    8, 9,                                        // Back Start
    10, 11,                                      // L3 R3
    { hat: HAT, dirs: [7, 0, 1] },               // D-pad lên
    { hat: HAT, dirs: [3, 4, 5] },               // D-pad xuống
    { hat: HAT, dirs: [5, 6, 7] },               // D-pad trái
    { hat: HAT, dirs: [1, 2, 3] },               // D-pad phải
    null,                                        // Guide
  ];

  // Trục chuẩn: 0 LX, 1 LY, 2 RX, 3 RY. Số n = trục thô thứ n.
  const AXIS_MAP = [0, 1, 2, 5];

  // ===================== BIÊN DỊCH CẤU HÌNH (làm 1 lần) =====================
  const HAT_MASK = BUTTON_MAP.map((s) =>
    s && typeof s === 'object' && 'hat' in s ? s.dirs.reduce((m, d) => m | (1 << d), 0) : 0
  );
  const HATS = [...new Set(BUTTON_MAP.filter((s) => s && typeof s === 'object' && 'hat' in s).map((s) => s.hat))];

  // Hat 8 hướng: -1 (N), -0.714, -0.429, -0.143, 0.143, 0.429, 0.714, 1 (NW); nghỉ = ngoài [-1, 1].
  // Chỉ chấp nhận giá trị sát một bước rời rạc, để giá trị lạ như 0 (chưa có dữ liệu) không bị hiểu là "Nam".
  function hatDir(h) {
    if (typeof h !== 'number' || h < -1.05 || h > 1.05) return -1;
    const k = Math.round((h + 1) * 3.5);
    return Math.abs(h - (k / 3.5 - 1)) < 0.08 ? k : -1;
  }

  const setBtn = (o, value, pressed, touched) => {
    o.value = value;
    o.pressed = pressed;
    o.touched = touched;
  };

  // ===================== WRAPPER =====================
  const realGetGamepads = navigator.getGamepads.bind(navigator);
  const states = []; // theo raw.index
  const PASSTHROUGH = ['index', 'connected', 'timestamp', 'vibrationActuator', 'hapticActuators'];

  function createState(raw) {
    const st = {
      id: raw.id,
      raw,
      ts: undefined,
      hat: {},
      armed: BUTTON_MAP.map(() => false),
      buttons: BUTTON_MAP.map(() => ({ pressed: false, touched: false, value: 0 })),
      axes: AXIS_MAP.map(() => 0),
      pad: Object.create(Gamepad.prototype), // giữ instanceof Gamepad
    };
    const props = {
      id: { value: FAKE_ID, enumerable: true },
      mapping: { value: 'standard', enumerable: true },
      buttons: { value: st.buttons, enumerable: true },
      axes: { get: () => st.axes, enumerable: true },
    };
    for (const name of PASSTHROUGH) {
      if (name in raw) props[name] = { get: () => st.raw[name], enumerable: true };
    }
    Object.defineProperties(st.pad, props);
    return st;
  }

  function update(st, raw) {
    st.raw = raw;
    if (SKIP_UNCHANGED && raw.timestamp === st.ts) return;
    st.ts = raw.timestamp;
    const rb = raw.buttons;
    const ra = raw.axes;

    for (let i = 0; i < HATS.length; i++) st.hat[HATS[i]] = hatDir(ra[HATS[i]]);

    for (let i = 0; i < BUTTON_MAP.length; i++) {
      const s = BUTTON_MAP[i];
      const o = st.buttons[i];
      if (s == null) continue;

      if (typeof s === 'number') {
        const b = rb[s];
        if (b) setBtn(o, b.value, !!b.pressed, !!(b.touched || b.pressed));
        else setBtn(o, 0, false, false);
      } else if ('hat' in s) {
        const k = st.hat[s.hat];
        const on = k >= 0 && ((HAT_MASK[i] >> k) & 1) === 1;
        setBtn(o, on ? 1 : 0, on, on);
      } else {
        const a = ra[s.axis];
        let v = 0;
        if ('gt' in s || 'lt' in s) {
          if (a !== undefined) v = ('gt' in s ? a > s.gt : a < s.lt) ? 1 : 0;
        } else {
          const min = s.min ?? -1;
          const max = s.max ?? 1;
          if (a !== undefined) {
            // Một số tay cầm báo trục trigger = 0 trước lần di chuyển đầu tiên -> tưởng bấm nửa chừng.
            if (!st.armed[i] && a <= min + ARM_EPS) st.armed[i] = true;
            if (st.armed[i]) v = Math.min(1, Math.max(0, (a - min) / (max - min)));
          }
          if (s.btn != null) {
            const b = rb[s.btn];
            if (b && b.value > v) v = b.value;
          }
        }
        setBtn(o, v, v >= 0.5, v > 0);
      }
    }

    // Mảng mới mỗi lần dữ liệu đổi (như trình duyệt thật): GFN có thể so sánh axes theo tham chiếu.
    const axes = new Array(AXIS_MAP.length);
    for (let i = 0; i < AXIS_MAP.length; i++) {
      const v = ra[AXIS_MAP[i]];
      axes[i] = v === undefined ? 0 : v;
    }
    st.axes = axes;
  }

  function wrap(raw) {
    let st = states[raw.index];
    if (!st || st.id !== raw.id) st = states[raw.index] = createState(raw);
    update(st, raw);
    return st.pad;
  }

  const matchCache = new Map();
  const isWilerId = (id) => {
    let r = matchCache.get(id);
    if (r === undefined) matchCache.set(id, (r = MATCH_ID.test(id)));
    return r;
  };
  const isWiler = (p) => p && p.connected && isWilerId(p.id);

  Object.defineProperty(navigator, 'getGamepads', {
    configurable: true,
    writable: true,
    value: function getGamepads() {
      const src = realGetGamepads();
      const out = new Array(src.length);
      for (let i = 0; i < src.length; i++) {
        const p = src[i];
        out[i] = isWiler(p) ? wrap(p) : p;
      }
      return out;
    },
  });

  // Sự kiện connected/disconnected: trả về đúng wrapper dùng chung (giữ nguyên danh tính đối tượng).
  for (const type of ['gamepadconnected', 'gamepaddisconnected']) {
    window.addEventListener(
      type,
      (e) => {
        const raw = e.gamepad;
        if (raw && isWilerId(raw.id)) {
          try {
            const w = wrap(raw);
            Object.defineProperty(e, 'gamepad', { configurable: true, get: () => w });
          } catch (_) {
            /* bỏ qua nếu trình duyệt không cho ghi đè */
          }
        }
      },
      true
    );
  }

  // ===================== DEBUG =====================
  window.__wilerDump = () => {
    const p = Array.from(realGetGamepads()).find(isWiler);
    if (!p) return console.warn(TAG, 'chưa thấy Wiler - bấm một nút rồi thử lại');
    console.log(TAG, 'id:', p.id, '| mapping thật:', JSON.stringify(p.mapping));
    console.log(TAG, 'axes thô:', p.axes.map((a) => +a.toFixed(2)));
    console.log(
      TAG,
      'nút đang nhấn (chỉ số thô):',
      p.buttons.map((b, i) => (b.pressed ? i : null)).filter((i) => i !== null)
    );
  };

  window.__wilerWatch = (on = true) => {
    clearInterval(window.__wilerWatchTimer);
    if (!on) return console.log(TAG, 'đã dừng watch');
    let base = null;
    let last = '';
    window.__wilerWatchTimer = setInterval(() => {
      const p = Array.from(realGetGamepads()).find(isWiler);
      if (!p) return;
      base = base || p.axes.slice();
      const ax = p.axes
        .map((a, i) => (Math.abs(a - base[i]) > 0.15 ? `a${i}=${a.toFixed(2)}` : null))
        .filter(Boolean);
      const bt = p.buttons.map((b, i) => (b.pressed ? `b${i}` : null)).filter(Boolean);
      const s = [...ax, ...bt].join(' ');
      if (s !== last) {
        last = s;
        if (s) console.log(TAG, s);
      }
    }, 50);
    console.log(TAG, 'đang watch (trạng thái nghỉ đã được ghi lại)');
  };

  console.log(TAG, 'đã nạp. Gõ __wilerWatch() để dò chỉ số thô, __wilerDump() để xem tức thời.');
})();
