package com.example.zenboshop;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.asus.robotframework.API.RobotAPI;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "BeaconScanner";

    private static final String TARGET_ADDRESS = "12:3B:6A:1A:D2:21"; // Beacon MAC address
    private static final int RSSI_THRESHOLD = -85; // RSSI threshold for distance estimation

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private TextToSpeech textToSpeech;
    private TextView scanStatus;
    private TextView detectedDevices;

    private RobotAPI robotAPI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        scanStatus = findViewById(R.id.scan_status);
        detectedDevices = findViewById(R.id.detected_devices);

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        // Initialize RobotAPI
        robotAPI = new RobotAPI(getApplicationContext(), null);

        // Check and request permissions
        checkPermissions();

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initialize BLE scanner
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE Scanner is not available.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission denied. App cannot scan for Beacons.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startScanning();
        } else {
            Toast.makeText(this, "Please enable Bluetooth to scan for Beacons.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            scanStatus.setText("Scan Status: Scanning...");
            detectedDevices.setText("");
            Toast.makeText(this, "Starting BLE scan...", Toast.LENGTH_SHORT).show();
            try {
                bluetoothLeScanner.startScan(scanCallback);
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException: Missing required permissions for BLE scan", e);
            }
        } else {
            Toast.makeText(this, "Missing permissions for BLE scan.", Toast.LENGTH_SHORT).show();
            checkPermissions(); // Request permissions again if missing
        }
    }

    private void stopScanning() {
        try {
            bluetoothLeScanner.stopScan(scanCallback);
            scanStatus.setText("Scan Status: Idle");
            Toast.makeText(this, "BLE scan stopped.", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Missing required permissions to stop BLE scan", e);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            int rssi = result.getRssi();

            Log.d(TAG, "Detected Device: " + deviceAddress + " RSSI: " + rssi);

            if (deviceAddress.equalsIgnoreCase(TARGET_ADDRESS)) {
                try {
                    bluetoothLeScanner.stopScan(this);
                    scanStatus.setText("Scan Status: Target Found");
                    Log.d(TAG, "Target Beacon Found: " + deviceAddress);

                    // 假設 Tx Power 為 -59 dBm，計算距離
                    double txPower = -59; // 可根據實際 Beacon 設置調整
                    double distance = Math.pow(10.0, (txPower - rssi) / 20.0);

                    Log.d(TAG, "Calculated Distance: " + distance + " meters");

                    // 自動移動至目標
                    if (distance > 1.0) {
                        moveZenbo(1.0f, 0, 0); // 向前移動 1 公尺
                    } else {
                        Log.d(TAG, "Zenbo is already within 1 meter.");
                    }

                    // 播報距離資訊
                    String message = "Target beacon found. Moving forward one meter.";
                    textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);

                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException: Error while stopping scan", e);
                }
            }
        }

        @Override
        public void onBatchScanResults(@NonNull java.util.List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                onScanResult(0, result); // Use 0 as callbackType
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            scanStatus.setText("Scan Status: Failed");
            Log.e(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    private void moveZenbo(float x, float y, float theta) {
        if (robotAPI != null) {
            robotAPI.motion.moveBody(x, y, theta);
            Log.d(TAG, "Zenbo is moving to position: X=" + x + ", Y=" + y + ", Theta=" + theta);
        } else {
            Log.e(TAG, "RobotAPI is not initialized.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();

        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        if (robotAPI != null) {
            robotAPI.release();
        }
        super.onDestroy();
    }
}
