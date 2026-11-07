package com.wiler.gamepad.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wiler.gamepad.R;
import com.wiler.gamepad.model.PreferencesManager;

import java.util.Locale;
import java.util.Set;

public class ConnectionBottomSheet extends BottomSheetDialogFragment {

    public interface ConnectionSheetListener {
        void onConnectDevice(BluetoothDevice device);
        void onDisconnectDevice();
        void onRequestDiscoverable();
    }

    private ImageView btnCloseConnection;
    private TextView tvDriverBadge;
    private LinearLayout cardDiscoverableAction;
    private TextView tvDiscoverableTitle;
    private TextView tvDiscoverableSubtitle;
    private TextView tvDiscoverableTimer;
    private TextView tvBondedCount;
    private LinearLayout containerBondedDevices;
    private TextView tvEmptyDevices;
    private SwitchMaterial switchAutoReconnect;

    private ConnectionSheetListener listener;
    private BluetoothDevice connectedDevice;
    private boolean isDiscoverable = false;
    private CountDownTimer discoverableTimer;

    public void setListener(ConnectionSheetListener listener) {
        this.listener = listener;
    }

    public void setConnectedDevice(BluetoothDevice device) {
        this.connectedDevice = device;
        if (isAdded()) {
            populateBondedDevices();
        }
    }

    public void setDiscoverable(boolean discoverable) {
        this.isDiscoverable = discoverable;
        if (isAdded()) {
            updateDiscoverableUi();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnCloseConnection = view.findViewById(R.id.btnCloseConnection);
        tvDriverBadge = view.findViewById(R.id.tvDriverBadge);
        cardDiscoverableAction = view.findViewById(R.id.cardDiscoverableAction);
        tvDiscoverableTitle = view.findViewById(R.id.tvDiscoverableTitle);
        tvDiscoverableSubtitle = view.findViewById(R.id.tvDiscoverableSubtitle);
        tvDiscoverableTimer = view.findViewById(R.id.tvDiscoverableTimer);
        tvBondedCount = view.findViewById(R.id.tvBondedCount);
        containerBondedDevices = view.findViewById(R.id.containerBondedDevices);
        tvEmptyDevices = view.findViewById(R.id.tvEmptyDevices);
        switchAutoReconnect = view.findViewById(R.id.switchAutoReconnect);

        btnCloseConnection.setOnClickListener(v -> dismiss());

        cardDiscoverableAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRequestDiscoverable();
            }
        });

        PreferencesManager prefs = PreferencesManager.getInstance(requireContext());
        switchAutoReconnect.setChecked(prefs.isAutoReconnect());
        switchAutoReconnect.setOnCheckedChangeListener((bv, isChecked) -> prefs.setAutoReconnect(isChecked));

        updateDiscoverableUi();
        populateBondedDevices();
    }

    private void updateDiscoverableUi() {
        if (isDiscoverable) {
            tvDiscoverableTitle.setText("Đang phát tín hiệu");
            tvDiscoverableSubtitle.setText("Chạm để dừng hoặc kiểm tra kết nối");
            startDiscoverableCountdown(120);
        } else {
            if (discoverableTimer != null) {
                discoverableTimer.cancel();
                discoverableTimer = null;
            }
            tvDiscoverableTitle.setText("Bật chế độ tìm kiếm");
            tvDiscoverableSubtitle.setText("Make Discoverable (120 giây)");
            tvDiscoverableTimer.setText("120\ngiây");
        }
    }

    private void startDiscoverableCountdown(int totalSeconds) {
        if (discoverableTimer != null) {
            discoverableTimer.cancel();
        }
        discoverableTimer = new CountDownTimer(totalSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = millisUntilFinished / 1000;
                tvDiscoverableTimer.setText(String.format(Locale.US, "%d\ngiây", sec));
            }

            @Override
            public void onFinish() {
                isDiscoverable = false;
                updateDiscoverableUi();
            }
        }.start();
    }

    @SuppressLint("MissingPermission")
    private void populateBondedDevices() {
        if (containerBondedDevices == null || getContext() == null) return;
        containerBondedDevices.removeAllViews();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            tvEmptyDevices.setVisibility(View.VISIBLE);
            tvBondedCount.setText("0 thiết bị");
            return;
        }

        Set<BluetoothDevice> bondedDevices;
        try {
            bondedDevices = adapter.getBondedDevices();
        } catch (SecurityException e) {
            tvEmptyDevices.setVisibility(View.VISIBLE);
            tvBondedCount.setText("0 thiết bị");
            return;
        }

        if (bondedDevices == null || bondedDevices.isEmpty()) {
            tvEmptyDevices.setVisibility(View.VISIBLE);
            tvBondedCount.setText("0 thiết bị");
            return;
        }

        tvEmptyDevices.setVisibility(View.GONE);
        tvBondedCount.setText(String.format(Locale.US, "%d thiết bị", bondedDevices.size()));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (BluetoothDevice device : bondedDevices) {
            View itemView = inflater.inflate(R.layout.item_bonded_device, containerBondedDevices, false);

            ImageView ivIcon = itemView.findViewById(R.id.ivDeviceType);
            TextView tvName = itemView.findViewById(R.id.tvDeviceName);
            TextView tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
            Button btnAction = itemView.findViewById(R.id.btnDeviceAction);

            String name = device.getName();
            if (name == null || name.isEmpty()) name = "Thiết bị Bluetooth";
            tvName.setText(name);
            tvAddress.setText(device.getAddress());

            // Icon by device class
            BluetoothClass btc = device.getBluetoothClass();
            int major = (btc != null) ? btc.getMajorDeviceClass() : BluetoothClass.Device.Major.UNCATEGORIZED;
            if (major == BluetoothClass.Device.Major.COMPUTER) {
                ivIcon.setImageResource(R.drawable.ic_pc);
            } else if (major == BluetoothClass.Device.Major.PHONE) {
                ivIcon.setImageResource(R.drawable.ic_phone);
            } else if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                ivIcon.setImageResource(R.drawable.ic_tv);
            } else {
                ivIcon.setImageResource(R.drawable.ic_pc);
            }

            // Connection state button
            boolean isThisConnected = (connectedDevice != null && connectedDevice.getAddress().equalsIgnoreCase(device.getAddress()));
            if (isThisConnected) {
                btnAction.setText("Ngắt kết nối");
                btnAction.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_button_danger_outline));
                btnAction.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onDisconnectDevice();
                });
            } else {
                btnAction.setText("Kết nối");
                btnAction.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_button_green));
                btnAction.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                btnAction.setOnClickListener(v -> {
                    if (listener != null) listener.onConnectDevice(device);
                });
            }

            containerBondedDevices.addView(itemView);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (discoverableTimer != null) {
            discoverableTimer.cancel();
            discoverableTimer = null;
        }
    }
}
