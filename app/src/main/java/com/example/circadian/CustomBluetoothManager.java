package com.example.circadian;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.circadian.fragments.TrackerFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// This class should contain all BLE scan functions and info (to be used in the TrackerFragment)

public class CustomBluetoothManager {
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    ScanSettings scanSettings;
    List<ScanFilter> filters;
    ScanCallback scanCallback;
    BluetoothDevice device;

    private BluetoothGatt gatt;

    private static final String TAG = "CustomBluetoothManager";
    private Runnable discoverServicesRunnable;
    private Handler bleHandler = new Handler(Looper.getMainLooper()); // Declare and initialize bleHandler

    private final long READ_INTERVAL = 50; // Interval in milliseconds
    private Handler periodicHandler = new Handler(Looper.getMainLooper()); // Looper to read characteristic on a regular interval
    private Runnable readCharacteristicTask;

    String connection = "Unconnected";
    String accelerometerValue = "Arduino Data Will Appear Here";

    public String getConnection() {
        return connection;
    }

    public void setConnection(String input) {
        connection = input;
    }

    public String getAccelerometerValue() {
        return accelerometerValue;
    }

    public void setAccelerometerValue(String input) { accelerometerValue = input; }

    // Method to initiate periodic reading of the characteristic
    public void startPeriodicCharacteristicReading(BluetoothGatt gatt, UUID characteristicUUID) {
        // Create a new task to read the characteristic value
        readCharacteristicTask = new Runnable() {
            @Override
            public void run() {
                readCharacteristic(gatt, characteristicUUID);
                // Reschedule the task to run after READ_INTERVAL milliseconds
                periodicHandler.postDelayed(this, READ_INTERVAL);
            }
        };

        // Start the periodic task
        periodicHandler.postDelayed(readCharacteristicTask, READ_INTERVAL);
    }

    // Method to stop periodic reading of the characteristic
    public void stopPeriodicCharacteristicReading() {
        // Remove any pending callbacks to stop the periodic task
        periodicHandler.removeCallbacksAndMessages(null);
    }

    public Object getDevice() {
        return device;
    }

    @SuppressLint("MissingPermission")
    public void readCharacteristic(BluetoothGatt gatt, UUID characteristicUUID) {
        this.gatt = gatt;
        if (gatt == null) {
            Log.e(TAG, "BluetoothGatt is null. Cannot read characteristic.");
            return;
        }

        BluetoothGattCharacteristic characteristic = findCharacteristic(gatt, characteristicUUID);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found.");
            return;
        }

        gatt.readCharacteristic(characteristic); // reads the characteristic (refer to on characteristic read method)
    }

    // Helper method to find a characteristic by UUID
    private BluetoothGattCharacteristic findCharacteristic(BluetoothGatt gatt, UUID characteristicUUID) {
        BluetoothGattService service = gatt.getService(UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")); // Replace YOUR_SERVICE_UUID with the UUID of the service containing the characteristic
        if (service != null) {
            return service.getCharacteristic(characteristicUUID);
        } else {
            Log.e(TAG, "Service not found.");
            return null;
        }
    }

    @SuppressLint("MissingPermission")
    public void closeGatt() {
        if (gatt != null) {
            gatt.disconnect(); // Disconnect the GATT connection
            gatt.close(); // Close the GATT connection
            gatt = null; // Reset the reference to null
        }
    }



    public CustomBluetoothManager(Context context, TrackerFragment trackerFragment) { // Constructor
        adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter.getBluetoothLeScanner();


        // Check Bluetooth availability and enable if needed
        if (adapter == null || !adapter.isEnabled()) {
            // Handle Bluetooth not available or not enabled
            Toast.makeText(context, "Bluetooth is not enabled.", Toast.LENGTH_SHORT);
            return;
        }

        // scan settings here
        this.scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Low latency uses the most power, but is best for finding the device quickly; switch to low power once working
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH) // Switch to CALLBACK_TYPE_FIRST_MATCH once working
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // Aggressive is the best choice here since it should help you find the device you need quicker
                .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT) // One advertisement is enough for a match (try other options if this doesn't work)
                .setReportDelay(0L) // if delay is >0, Android will collect all the scan results it finds and send them in a batch after the delay.
                .build();


        String[] names = new String[]{"Nano33BLE"};

        if (names != null) {
            filters = new ArrayList<>();
            for (String name : names) {
                ScanFilter filter = new ScanFilter.Builder()
                        .setDeviceName(name)
                        .build();
                filters.add(filter);
            }
        }

        // define what to do with the device once connected here
        BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

            @Override
            @SuppressLint("MissingPermission")
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                device = gatt.getDevice(); // the correct, not null device is declared as a variable here

                if (status == GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        connection = "Connected";

                        int bondstate = device.getBondState();

                        // Take action depending on the bond state
                        if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {

                            // Connected to device, now proceed to discover it's services but delay a bit if needed
                            int delayWhenBonded = 0;
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                                delayWhenBonded = 1000;
                            }
                            final int delay = bondstate == BOND_BONDED ? delayWhenBonded : 0;
                            discoverServicesRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", device.getName(), delay));

                                    boolean result = gatt.discoverServices();

                                    if (!result) {
                                        Log.e(TAG, "discoverServices failed to start");
                                    }
                                    discoverServicesRunnable = null;
                                }
                            };
                            bleHandler.postDelayed(discoverServicesRunnable, delay);
                        } else if (bondstate == BOND_BONDING) {
                            connection = "Connecting";
                            // Bonding process in progress, let it complete
                            Log.i(TAG, "waiting for bonding to complete");
                        }
                    }

                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        connection = "Unconnected";
                        gatt.close(); // Close the BluetoothGatt object when disconnected
                    }

                }

            }

            // seems to fix service problem, but when stored in a list, breaks the program for some odd reason
            // Also, the device name is now null for some strange reason?
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                List<BluetoothGattService> services = gatt.getServices();
                Log.d(TAG, "Number of Services: " + services.size());
                UUID characteristicUUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214");
                // readCharacteristic(gatt, characteristicUUID); // once the service has been discovered, it should be read and then we will get its value
                startPeriodicCharacteristicReading(gatt, characteristicUUID);
            }

            // Reads the value of the characteristic
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == GATT_SUCCESS) {
                    accelerometerValue = characteristic.getStringValue(0);
                    Log.d(TAG, "Characteristic value: " + accelerometerValue);
                }
            }

        };



        // define what to do with device once found (ScanCallback) here
        this.scanCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice(); // device is set to the device found in the result (weirdly now null?)
                String deviceName = device.getName();
                if (deviceName != null) {
                    Toast.makeText(context, "This device was found: " + deviceName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "This device was found: " + device.getAddress(), Toast.LENGTH_SHORT).show(); // Showing device address if name is null
                }
                BluetoothGatt gatt = device.connectGatt(context, false, bluetoothGattCallback, TRANSPORT_LE); // allows device to connect
                stopScan(); // the scan stops once a device is found (initally the cause of the constant "scan failed issue" was because of not stopping the scan once a device was found)
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                // Ignore for now
            }

            @Override
            public void onScanFailed(int errorCode) {
                String errorMessage;

                // All possible error messages for a scan gone wrong
                switch (errorCode) {
                    case SCAN_FAILED_ALREADY_STARTED:
                        errorMessage = "Scan already started";
                        break;
                    case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        errorMessage = "Application registration failed";
                        break;
                    case SCAN_FAILED_FEATURE_UNSUPPORTED:
                        errorMessage = "Feature unsupported";
                        break;
                    case SCAN_FAILED_INTERNAL_ERROR:
                        errorMessage = "Internal error";
                        break;
                    case SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                        errorMessage = "Out of hardware resources";
                        break;
                    default:
                        errorMessage = "Scan failed";
                        break;
                }

                // Displays the error message
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }


        };
    }

    // Start scanning
    @SuppressLint("MissingPermission") // permission not required on android 11, so we suppress it for now
    public void startScan() {
        if (scanner != null && scanCallback != null) {
            scanner.startScan(filters, scanSettings, scanCallback); // permission check only necessary for android 12 and up.
        }
    }

    // Stop scanning
    @SuppressLint("MissingPermission") // permission not required on android 11, so we suppress it for now
    public void stopScan() {
        if (scanner != null && scanCallback != null) {
            scanner.stopScan(scanCallback);
        }
    }


}
