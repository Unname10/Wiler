package com.wiler.gamepad.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wiler.gamepad.GamepadEditActivity;
import com.wiler.gamepad.R;
import com.wiler.gamepad.model.PreferencesManager;

import java.util.Locale;

public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public interface OnSettingsChangeListener {
        void onDeadzoneChanged(float deadzone);
        void onDynamicJoystickChanged(boolean enabled);
        void onButtonScaleChanged(float scale);
        void onVibrationChanged(boolean enabled);
        void onProfileChanged(int profile);
        default void onLayoutChanged() {}
    }

    private LinearLayout cardProfileXbox;
    private LinearLayout cardProfileStandard;
    private ImageView ivCheckXbox;
    private ImageView ivCheckStandard;
    private Button btnSheetPresetDefault;
    private Button btnSheetPreset1;
    private Button btnSheetPreset2;
    private Button btnSheetPreset3;
    private Button btnOpenLayoutEditor;
    private int activeLayoutPreset = 0;
    private TextView tvDeadzoneBadge;
    private DeadzonePreviewView deadzonePreviewView;
    private Slider sliderDeadzone;
    private TextView tvButtonScaleBadge;
    private Slider sliderButtonScale;
    private SwitchMaterial switchDynamicJoystick;
    private SwitchMaterial switchVibration;
    private SwitchMaterial switchAutoDisconnectBg;
    private ImageView btnCloseSettings;

    private PreferencesManager prefs;
    private OnSettingsChangeListener listener;

    public void setListener(OnSettingsChangeListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = PreferencesManager.getInstance(requireContext());

        cardProfileXbox = view.findViewById(R.id.cardProfileXbox);
        cardProfileStandard = view.findViewById(R.id.cardProfileStandard);
        ivCheckXbox = view.findViewById(R.id.ivCheckXbox);
        ivCheckStandard = view.findViewById(R.id.ivCheckStandard);
        tvDeadzoneBadge = view.findViewById(R.id.tvDeadzoneBadge);
        deadzonePreviewView = view.findViewById(R.id.deadzonePreviewView);
        sliderDeadzone = view.findViewById(R.id.sliderDeadzone);
        tvButtonScaleBadge = view.findViewById(R.id.tvButtonScaleBadge);
        sliderButtonScale = view.findViewById(R.id.sliderButtonScale);
        switchDynamicJoystick = view.findViewById(R.id.switchDynamicJoystick);
        switchVibration = view.findViewById(R.id.switchVibration);
        switchAutoDisconnectBg = view.findViewById(R.id.switchAutoDisconnectBg);
        btnCloseSettings = view.findViewById(R.id.btnCloseSettings);
        btnSheetPresetDefault = view.findViewById(R.id.btnSheetPresetDefault);
        btnSheetPreset1 = view.findViewById(R.id.btnSheetPreset1);
        btnSheetPreset2 = view.findViewById(R.id.btnSheetPreset2);
        btnSheetPreset3 = view.findViewById(R.id.btnSheetPreset3);
        btnOpenLayoutEditor = view.findViewById(R.id.btnOpenLayoutEditor);

        btnCloseSettings.setOnClickListener(v -> dismiss());

        // Setup Profile selection
        int currentProfile = prefs.getProfile();
        updateProfileCards(currentProfile);

        cardProfileXbox.setOnClickListener(v -> selectProfile(PreferencesManager.PROFILE_XBOX_LIKE));
        cardProfileStandard.setOnClickListener(v -> selectProfile(PreferencesManager.PROFILE_STANDARD_HID));

        // Setup Layout Preset selection
        activeLayoutPreset = prefs.getActiveLayoutPreset();
        updatePresetButtonsUi();

        btnSheetPresetDefault.setOnClickListener(v -> selectLayoutPreset(PreferencesManager.PRESET_DEFAULT));
        btnSheetPreset1.setOnClickListener(v -> selectLayoutPreset(PreferencesManager.PRESET_1));
        btnSheetPreset2.setOnClickListener(v -> selectLayoutPreset(PreferencesManager.PRESET_2));
        btnSheetPreset3.setOnClickListener(v -> selectLayoutPreset(PreferencesManager.PRESET_3));

        btnOpenLayoutEditor.setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), GamepadEditActivity.class));
        });

        // Setup Deadzone
        float deadzone = prefs.getDeadzone();
        int deadzonePercent = Math.round(deadzone * 100f);
        sliderDeadzone.setValue(deadzonePercent);
        tvDeadzoneBadge.setText(String.format(Locale.US, "%d%%", deadzonePercent));
        deadzonePreviewView.setDeadzonePercent(deadzone);

        sliderDeadzone.addOnChangeListener((slider, value, fromUser) -> {
            int intVal = Math.round(value);
            float dzFloat = intVal / 100f;
            tvDeadzoneBadge.setText(String.format(Locale.US, "%d%%", intVal));
            deadzonePreviewView.setDeadzonePercent(dzFloat);
            prefs.setDeadzone(dzFloat);
            if (listener != null) {
                listener.onDeadzoneChanged(dzFloat);
            }
        });

        // Setup Button Scale
        float buttonScale = prefs.getButtonScale();
        int scalePercent = Math.round(buttonScale * 100f);
        sliderButtonScale.setValue(scalePercent);
        tvButtonScaleBadge.setText(String.format(Locale.US, "%d%%", scalePercent));

        sliderButtonScale.addOnChangeListener((slider, value, fromUser) -> {
            int intVal = Math.round(value);
            float scaleFloat = intVal / 100f;
            tvButtonScaleBadge.setText(String.format(Locale.US, "%d%%", intVal));
            prefs.setButtonScale(scaleFloat);
            if (listener != null) {
                listener.onButtonScaleChanged(scaleFloat);
            }
        });

        // Setup Dynamic Joystick
        switchDynamicJoystick.setChecked(prefs.isDynamicJoystick());
        switchDynamicJoystick.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setDynamicJoystick(isChecked);
            if (listener != null) {
                listener.onDynamicJoystickChanged(isChecked);
            }
        });

        // Setup Vibration
        switchVibration.setChecked(prefs.isVibrationEnabled());
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setVibrationEnabled(isChecked);
            if (listener != null) {
                listener.onVibrationChanged(isChecked);
            }
        });

        // Setup Auto-disconnect on background
        switchAutoDisconnectBg.setChecked(prefs.isAutoDisconnectOnBackground());
        switchAutoDisconnectBg.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setAutoDisconnectOnBackground(isChecked);
        });
    }

    private void selectLayoutPreset(int presetId) {
        if (this.activeLayoutPreset != presetId) {
            this.activeLayoutPreset = presetId;
            prefs.setActiveLayoutPreset(presetId);
            updatePresetButtonsUi();
            if (listener != null) {
                listener.onLayoutChanged();
            }
        }
    }

    private void updatePresetButtonsUi() {
        if (btnSheetPresetDefault == null || !isAdded()) return;

        btnSheetPresetDefault.setBackgroundResource(activeLayoutPreset == 0 ? R.drawable.bg_card_dark_selected : R.drawable.bg_card_dark);
        btnSheetPreset1.setBackgroundResource(activeLayoutPreset == 1 ? R.drawable.bg_card_dark_selected : R.drawable.bg_card_dark);
        btnSheetPreset2.setBackgroundResource(activeLayoutPreset == 2 ? R.drawable.bg_card_dark_selected : R.drawable.bg_card_dark);
        btnSheetPreset3.setBackgroundResource(activeLayoutPreset == 3 ? R.drawable.bg_card_dark_selected : R.drawable.bg_card_dark);

        int activeColor = ContextCompat.getColor(requireContext(), R.color.xbox_green);
        int inactiveColor = Color.parseColor("#889988");

        btnSheetPresetDefault.setTextColor(activeLayoutPreset == 0 ? activeColor : inactiveColor);
        btnSheetPreset1.setTextColor(activeLayoutPreset == 1 ? activeColor : inactiveColor);
        btnSheetPreset2.setTextColor(activeLayoutPreset == 2 ? activeColor : inactiveColor);
        btnSheetPreset3.setTextColor(activeLayoutPreset == 3 ? activeColor : inactiveColor);
    }

    private void selectProfile(int profile) {
        int previousProfile = prefs.getProfile();
        if (previousProfile != profile) {
            prefs.setProfile(profile);
            updateProfileCards(profile);
            if (listener != null) {
                listener.onProfileChanged(profile);
            }
            String name = (profile == PreferencesManager.PROFILE_XBOX_LIKE) ? "Xbox Wireless Controller (Windows PC)" : "Standard HID Gamepad (Android/Universal)";
            Toast.makeText(requireContext(), "Đã chuyển sang: " + name + ".\nHãy hủy ghép đôi và ghép nối lại trên thiết bị nhận để áp dụng!", Toast.LENGTH_LONG).show();
        }
    }

    private void updateProfileCards(int profile) {
        if (profile == PreferencesManager.PROFILE_XBOX_LIKE) {
            cardProfileXbox.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_dark_selected));
            ivCheckXbox.setVisibility(View.VISIBLE);
            cardProfileStandard.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_dark));
            ivCheckStandard.setVisibility(View.GONE);
        } else {
            cardProfileStandard.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_dark_selected));
            ivCheckStandard.setVisibility(View.VISIBLE);
            cardProfileXbox.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_card_dark));
            ivCheckXbox.setVisibility(View.GONE);
        }
    }
}
