package com.wiler.gamepad.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulates the normalized positions (normX = x / w, normY = y / h),
 * scales, and cluster locking/spacing for the gamepad layout.
 */
public class GamepadLayoutConfig {

    public static class Element {
        public float normX;
        public float normY;
        public float scale;

        public Element() {
            this(0.5f, 0.5f, 1.0f);
        }

        public Element(float normX, float normY, float scale) {
            this.normX = normX;
            this.normY = normY;
            this.scale = scale;
        }

        public Element copy() {
            return new Element(normX, normY, scale);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("normX", (double) normX);
            obj.put("normY", (double) normY);
            obj.put("scale", (double) scale);
            return obj;
        }

        public static Element fromJson(JSONObject obj, float defX, float defY, float defScale) {
            if (obj == null) return new Element(defX, defY, defScale);
            float x = (float) obj.optDouble("normX", defX);
            float y = (float) obj.optDouble("normY", defY);
            float s = (float) obj.optDouble("scale", defScale);
            return new Element(x, y, s);
        }
    }

    // --- Left & Right Stick ---
    public Element leftStick = new Element(0.32f, 0.72f, 1.0f);
    public Element rightStick = new Element(0.68f, 0.72f, 1.0f);

    // --- D-Pad Cluster ---
    public boolean dpadLocked = true;
    public float dpadSpacing = 1.0f; // Scale multiplier for distance from center
    public Element dpadCenter = new Element(0.14f, 0.50f, 1.0f);
    public Element dpadUp = new Element(0.14f, 0.36f, 1.0f);
    public Element dpadRight = new Element(0.21f, 0.50f, 1.0f);
    public Element dpadDown = new Element(0.14f, 0.64f, 1.0f);
    public Element dpadLeft = new Element(0.07f, 0.50f, 1.0f);

    // --- ABXY Cluster ---
    public boolean abxyLocked = true;
    public float abxySpacing = 1.0f; // Scale multiplier for distance from center
    public Element abxyCenter = new Element(0.86f, 0.50f, 1.0f);
    public Element btnY = new Element(0.86f, 0.36f, 1.0f);
    public Element btnB = new Element(0.93f, 0.50f, 1.0f);
    public Element btnA = new Element(0.86f, 0.64f, 1.0f);
    public Element btnX = new Element(0.79f, 0.50f, 1.0f);

    // --- Shoulder Buttons ---
    public Element btnLT = new Element(0.08f, 0.15f, 1.0f);
    public Element btnLB = new Element(0.22f, 0.15f, 1.0f);
    public Element btnRB = new Element(0.78f, 0.15f, 1.0f);
    public Element btnRT = new Element(0.92f, 0.15f, 1.0f);

    // --- Center & Corner Buttons ---
    public Element btnBack = new Element(0.39f, 0.11f, 1.0f);
    public Element btnGuide = new Element(0.50f, 0.11f, 1.0f);
    public Element btnStart = new Element(0.61f, 0.11f, 1.0f);
    public Element btnL3 = new Element(0.07f, 0.88f, 1.0f);
    public Element btnR3 = new Element(0.93f, 0.88f, 1.0f);

    public GamepadLayoutConfig() {
    }

    public static GamepadLayoutConfig createDefaultConfig() {
        return new GamepadLayoutConfig();
    }

    public GamepadLayoutConfig copy() {
        GamepadLayoutConfig c = new GamepadLayoutConfig();
        c.leftStick = this.leftStick.copy();
        c.rightStick = this.rightStick.copy();

        c.dpadLocked = this.dpadLocked;
        c.dpadSpacing = this.dpadSpacing;
        c.dpadCenter = this.dpadCenter.copy();
        c.dpadUp = this.dpadUp.copy();
        c.dpadRight = this.dpadRight.copy();
        c.dpadDown = this.dpadDown.copy();
        c.dpadLeft = this.dpadLeft.copy();

        c.abxyLocked = this.abxyLocked;
        c.abxySpacing = this.abxySpacing;
        c.abxyCenter = this.abxyCenter.copy();
        c.btnY = this.btnY.copy();
        c.btnB = this.btnB.copy();
        c.btnA = this.btnA.copy();
        c.btnX = this.btnX.copy();

        c.btnLT = this.btnLT.copy();
        c.btnLB = this.btnLB.copy();
        c.btnRB = this.btnRB.copy();
        c.btnRT = this.btnRT.copy();

        c.btnBack = this.btnBack.copy();
        c.btnGuide = this.btnGuide.copy();
        c.btnStart = this.btnStart.copy();
        c.btnL3 = this.btnL3.copy();
        c.btnR3 = this.btnR3.copy();
        return c;
    }

    /**
     * Recalculates child button positions of D-pad cluster from dpadCenter and dpadSpacing.
     */
    public void syncDpadCluster(float screenAspect) {
        if (!dpadLocked) return;
        // Typical offset in normalized coordinates
        float offsetVertical = 0.135f * dpadSpacing * dpadCenter.scale;
        float offsetHorizontal = (offsetVertical / Math.max(1.0f, screenAspect));

        dpadUp.normX = dpadCenter.normX;
        dpadUp.normY = dpadCenter.normY - offsetVertical;
        dpadUp.scale = dpadCenter.scale;

        dpadDown.normX = dpadCenter.normX;
        dpadDown.normY = dpadCenter.normY + offsetVertical;
        dpadDown.scale = dpadCenter.scale;

        dpadLeft.normX = dpadCenter.normX - offsetHorizontal;
        dpadLeft.normY = dpadCenter.normY;
        dpadLeft.scale = dpadCenter.scale;

        dpadRight.normX = dpadCenter.normX + offsetHorizontal;
        dpadRight.normY = dpadCenter.normY;
        dpadRight.scale = dpadCenter.scale;
    }

    /**
     * Recalculates child button positions of ABXY cluster from abxyCenter and abxySpacing.
     */
    public void syncAbxyCluster(float screenAspect) {
        if (!abxyLocked) return;
        float offsetVertical = 0.135f * abxySpacing * abxyCenter.scale;
        float offsetHorizontal = (offsetVertical / Math.max(1.0f, screenAspect));

        btnY.normX = abxyCenter.normX;
        btnY.normY = abxyCenter.normY - offsetVertical;
        btnY.scale = abxyCenter.scale;

        btnA.normX = abxyCenter.normX;
        btnA.normY = abxyCenter.normY + offsetVertical;
        btnA.scale = abxyCenter.scale;

        btnX.normX = abxyCenter.normX - offsetHorizontal;
        btnX.normY = abxyCenter.normY;
        btnX.scale = abxyCenter.scale;

        btnB.normX = abxyCenter.normX + offsetHorizontal;
        btnB.normY = abxyCenter.normY;
        btnB.scale = abxyCenter.scale;
    }

    public String toJsonString() {
        try {
            JSONObject root = new JSONObject();

            root.put("leftStick", leftStick.toJson());
            root.put("rightStick", rightStick.toJson());

            root.put("dpadLocked", dpadLocked);
            root.put("dpadSpacing", (double) dpadSpacing);
            root.put("dpadCenter", dpadCenter.toJson());
            root.put("dpadUp", dpadUp.toJson());
            root.put("dpadRight", dpadRight.toJson());
            root.put("dpadDown", dpadDown.toJson());
            root.put("dpadLeft", dpadLeft.toJson());

            root.put("abxyLocked", abxyLocked);
            root.put("abxySpacing", (double) abxySpacing);
            root.put("abxyCenter", abxyCenter.toJson());
            root.put("btnY", btnY.toJson());
            root.put("btnB", btnB.toJson());
            root.put("btnA", btnA.toJson());
            root.put("btnX", btnX.toJson());

            root.put("btnLT", btnLT.toJson());
            root.put("btnLB", btnLB.toJson());
            root.put("btnRB", btnRB.toJson());
            root.put("btnRT", btnRT.toJson());

            root.put("btnBack", btnBack.toJson());
            root.put("btnGuide", btnGuide.toJson());
            root.put("btnStart", btnStart.toJson());
            root.put("btnL3", btnL3.toJson());
            root.put("btnR3", btnR3.toJson());

            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static GamepadLayoutConfig fromJsonString(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return createDefaultConfig();
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            GamepadLayoutConfig config = new GamepadLayoutConfig();

            config.leftStick = Element.fromJson(root.optJSONObject("leftStick"), 0.32f, 0.72f, 1.0f);
            config.rightStick = Element.fromJson(root.optJSONObject("rightStick"), 0.68f, 0.72f, 1.0f);

            config.dpadLocked = root.optBoolean("dpadLocked", true);
            config.dpadSpacing = (float) root.optDouble("dpadSpacing", 1.0);
            config.dpadCenter = Element.fromJson(root.optJSONObject("dpadCenter"), 0.14f, 0.50f, 1.0f);
            config.dpadUp = Element.fromJson(root.optJSONObject("dpadUp"), 0.14f, 0.36f, 1.0f);
            config.dpadRight = Element.fromJson(root.optJSONObject("dpadRight"), 0.21f, 0.50f, 1.0f);
            config.dpadDown = Element.fromJson(root.optJSONObject("dpadDown"), 0.14f, 0.64f, 1.0f);
            config.dpadLeft = Element.fromJson(root.optJSONObject("dpadLeft"), 0.07f, 0.50f, 1.0f);

            config.abxyLocked = root.optBoolean("abxyLocked", true);
            config.abxySpacing = (float) root.optDouble("abxySpacing", 1.0);
            config.abxyCenter = Element.fromJson(root.optJSONObject("abxyCenter"), 0.86f, 0.50f, 1.0f);
            config.btnY = Element.fromJson(root.optJSONObject("btnY"), 0.86f, 0.36f, 1.0f);
            config.btnB = Element.fromJson(root.optJSONObject("btnB"), 0.93f, 0.50f, 1.0f);
            config.btnA = Element.fromJson(root.optJSONObject("btnA"), 0.86f, 0.64f, 1.0f);
            config.btnX = Element.fromJson(root.optJSONObject("btnX"), 0.79f, 0.50f, 1.0f);

            config.btnLT = Element.fromJson(root.optJSONObject("btnLT"), 0.08f, 0.15f, 1.0f);
            config.btnLB = Element.fromJson(root.optJSONObject("btnLB"), 0.22f, 0.15f, 1.0f);
            config.btnRB = Element.fromJson(root.optJSONObject("btnRB"), 0.78f, 0.15f, 1.0f);
            config.btnRT = Element.fromJson(root.optJSONObject("btnRT"), 0.92f, 0.15f, 1.0f);

            config.btnBack = Element.fromJson(root.optJSONObject("btnBack"), 0.39f, 0.11f, 1.0f);
            config.btnGuide = Element.fromJson(root.optJSONObject("btnGuide"), 0.50f, 0.11f, 1.0f);
            config.btnStart = Element.fromJson(root.optJSONObject("btnStart"), 0.61f, 0.11f, 1.0f);
            config.btnL3 = Element.fromJson(root.optJSONObject("btnL3"), 0.07f, 0.88f, 1.0f);
            config.btnR3 = Element.fromJson(root.optJSONObject("btnR3"), 0.93f, 0.88f, 1.0f);

            return config;
        } catch (JSONException e) {
            return createDefaultConfig();
        }
    }
}
