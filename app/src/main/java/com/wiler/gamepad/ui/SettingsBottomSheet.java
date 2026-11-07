package com.wiler.gamepad.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.wiler.gamepad.R;
import com.wiler.gamepad.model.PreferencesManager;

import java.util.Locale;

public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public interface OnSettingsChangeListener {
        void onDeadzoneChanged(float deadzone);
        void onDynamicJoystickChanged(boolean enabled);
        void onProfileChanged(int profile);
    }

    private LinearLayout cardProfileXbox;
    private LinearLayout cardProfileStandard;
    private ImageView ivCheckXbox;
    private ImageView ivCheckStandard;
    private TextView tvDeadzoneBadge;
    private DeadzonePreviewView deadzonePreviewView;
    private Slider sliderDeadzone;
    private SwitchMaterial switchDynamicJoystick;
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
        switchDynamicJoystick = view.findViewById(R.id.switchDynamicJoystick);
        btnCloseSettings = view.findViewById(R.id.btnCloseSettings);

        btnCloseSettings.setOnClickListener(v -> dismiss());

        // Setup Profile selection
        int currentProfile = prefs.getProfile();
        updateProfileCards(currentProfile);

        cardProfileXbox.setOnClickListener(v -> selectProfile(PreferencesManager.PROFILE_XBOX_LIKE));
        cardProfileStandard.setOnClickListener(v -> selectProfile(PreferencesManager.PROFILE_STANDARD_HID));

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

        // Setup Dynamic Joystick
        switchDynamicJoystick.setChecked(prefs.isDynamicJoystick());
        switchDynamicJoystick.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setDynamicJoystick(isChecked);
            if (listener != null) {
                listener.onDynamicJoystickChanged(isChecked);
            }
        });
    }

    private void selectProfile(int profile) {
        int previousProfile = prefs.getProfile();
        if (previousProfile != profile) {
            prefs.setProfile(profile);
            updateProfileCards(profile);
            Toast.makeText(requireContext(), "Đã chọn hồ sơ. Cần khởi động lại kết nối để áp dụng.", Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onProfileChanged(profile);
            }
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
