package com.wiler.gamepad.model;

/**
 * Utility class for normalizing raw touch coordinates and applying
 * smooth clamped radial deadzone calculations without trigonometry.
 */
public final class InputMapper {

    private InputMapper() {
        // Utility class
    }

    /**
     * Applies radial deadzone with smooth rescaling.
     *
     * @param x        Raw X coordinate in range [-1.0, 1.0]
     * @param y        Raw Y coordinate in range [-1.0, 1.0] (matching screen Y, positive down)
     * @param deadzone Deadzone threshold ratio (typically 0.0 to 0.25, max clamped to 0.5)
     * @return float array [mappedX, mappedY] scaled between -1.0 and 1.0
     */
    public static float[] applyRadialDeadzone(float x, float y, float deadzone) {
        // Clamp deadzone to safe range [0.0, 0.5] to prevent division by zero
        float dz = Math.max(0.0f, Math.min(deadzone, 0.5f));

        float rawDistance = (float) Math.sqrt(x * x + y * y);

        // Within deadzone or exactly zero
        if (rawDistance <= dz || rawDistance == 0.0f) {
            return new float[]{0.0f, 0.0f};
        }

        // Clamp maximum raw radius to 1.0 to avoid values exceeding boundary
        float clampedRadius = Math.min(rawDistance, 1.0f);

        // Smoothly rescale radius from [dz, 1.0] to [0.0, 1.0]
        float rescaledRadius = (clampedRadius - dz) / (1.0f - dz);

        // Scaling factor
        float scale = rescaledRadius / rawDistance;

        float mappedX = x * scale;
        float mappedY = y * scale;

        // Double check bounds [-1.0, 1.0]
        mappedX = Math.max(-1.0f, Math.min(mappedX, 1.0f));
        mappedY = Math.max(-1.0f, Math.min(mappedY, 1.0f));

        return new float[]{mappedX, mappedY};
    }
}
