package com.example.circadian.fragments;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.circadian.CustomBluetoothManager;
import com.example.circadian.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


// This fragment should NOT contain any bluetooth code other than what is needed to initialize the BLE process (calls functions from the CustomBluetoothManager)
// TODO: Implement a check so that record data's text does not change if no device is connected via BLE

// TODO: Fix alarm issues

// TODO: Add methods to start and stop process of recording data (will be a runnable that adds data to an array on same intervals as the accelerometer data); Stopping it will calculate the score and display it in accelerometer data text view and stop the runnable
public class TrackerFragment extends Fragment {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1;
    private static final String TAG = "";
    private static final int ACCESS_COARSE_LOCATION_REQUEST = 1;

    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();

    // Creates a bluetoothManager object (called customBluetoothManager since the name BluetoothManager conflicts with Android)
    private CustomBluetoothManager customBluetoothManager;

    private List<Double> recordedData;
    private Runnable recordDataRunnable;

    private Button searchButton;

    private TextView accelerometerDataTextView;

    private TextView SleepScoreText;

    private Runnable accelerometerRunnable;
    private Handler accelerometerHandler = new Handler(Looper.getMainLooper());

    private TextView ArduinoStatus;

    private Runnable connectionRunnable;
    private Handler ConnectionHandler = new Handler(Looper.getMainLooper());


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        checkConnection();
        accelerometerUpdate();

        View view = inflater.inflate(R.layout.fragment_tracker, container, false);
        ArduinoStatus = view.findViewById(R.id.ArduinoStatus);
        accelerometerDataTextView = view.findViewById(R.id.accelerometerDataTextView);
        SleepScoreText = view.findViewById(R.id.SleepScore);

        Button recordButton = view.findViewById(R.id.Recorddata);
        searchButton = view.findViewById(R.id.search_button);
        TextView accelerometerDataTextView = view.findViewById(R.id.accelerometerDataTextView);

        customBluetoothManager = new CustomBluetoothManager(getContext(), this);

        // Initialize the recordedData list
        recordedData = new ArrayList<>();

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (customBluetoothManager.getConnection() == "Unconnected") {
                    customBluetoothManager.startScan(); // startScan method is called on customBluetoothManager
                }
                // Disconnects the device and returns everything to default state (all we need is to actually disconnect the device with gatt)
                else {
                    customBluetoothManager.stopScan();
                    customBluetoothManager.stopPeriodicCharacteristicReading();
                    customBluetoothManager.setAccelerometerValue("Arduino Data Will Appear Here");
                    customBluetoothManager.setConnection("Unconnected");
                    customBluetoothManager.closeGatt();
                }
            }
        });

        // add a new text view for the recording status
        recordButton.setOnClickListener(new View.OnClickListener() { // listener for recordButton
            @Override
            public void onClick(View v) { // changes recording data button text
                if (customBluetoothManager.getConnection() == "Connected") {
                    if (recordButton.getText().toString().equals("Record Data")) {
                        recordButton.setText("Stop Recording");
                        startRecording();
                    } else {
                        recordButton.setText("Record Data");
                        stopRecording();
                    }
                } else {
                    Toast.makeText(getContext(), "No device connected, cannot record data.", Toast.LENGTH_SHORT).show();
                }

            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove any pending tasks from the Handler
        ConnectionHandler.removeCallbacks(connectionRunnable);
        accelerometerHandler.removeCallbacks(accelerometerRunnable);
        // Set ArduinoStatus to null to prevent memory leaks
        ArduinoStatus = null;
        accelerometerDataTextView = null;
    }

    public void startRecording() {
        final long RECORD_INTERVAL = 1000; // Interval in milliseconds for recording data

        recordDataRunnable = new Runnable() {
            @Override
            public void run() {
                // Add accelerometer data to the recordedData list
                String accelerometerValueStr = customBluetoothManager.getAccelerometerValue();

                // Convert the accelerometer value string to a double
                double accelerometerValue = Double.parseDouble(accelerometerValueStr);;
                recordedData.add(accelerometerValue);

                // Reschedule the task to run after RECORD_INTERVAL milliseconds
                accelerometerHandler.postDelayed(this, RECORD_INTERVAL);
            }
        };

        // Start the recording task
        accelerometerHandler.postDelayed(recordDataRunnable, RECORD_INTERVAL);

    }

    public void stopRecording() {
        accelerometerHandler.removeCallbacks(recordDataRunnable); // kills runnable

        // Calculate the average value
        double sum = 0.0;
        for (Double value : recordedData) {
            sum += value;
        }
        double averageValue = sum / recordedData.size();

        // Calculate the absolute difference between the average value and 1
        double deviation = Math.abs(1.0 - averageValue);

        // Calculate the percentage of how close it was to being 1
        int percentage = (int) ((1.0 - deviation) * 100);
        percentage = Math.abs(percentage);

        recordedData = new ArrayList<>(); // clears the recordedData list
        SleepScoreText.setText(String.valueOf(percentage) + " / 100");
    }

    public void checkConnection() {
        // Create a new task to read the characteristic value
        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (ArduinoStatus != null) { // Check if ArduinoStatus is not null
                    ArduinoStatus.setText(customBluetoothManager.getConnection());
                }

                // modify search button text based on connectivity status
                if (customBluetoothManager.getConnection() == "Connected") {
                    searchButton.setText("Disconnect");
                }
                else {
                    searchButton.setText("Search For Devices");
                }

                // Reschedule the task to run after READ_INTERVAL milliseconds
                ConnectionHandler.postDelayed(this, 500);
            }
        };

        // Start the periodic task
        ConnectionHandler.postDelayed(connectionRunnable, 500);
    }

    // TODO: add check so that it returns to a default value when disconnected
    public void accelerometerUpdate() {
        // Create a new task to read the characteristic value
        accelerometerRunnable = new Runnable() {
            @Override
            public void run() {
                if (accelerometerDataTextView != null) { // Check if ArduinoStatus is not null
                    accelerometerDataTextView.setText(customBluetoothManager.getAccelerometerValue());
                }
                // Reschedule the task to run after READ_INTERVAL milliseconds
                accelerometerHandler.postDelayed(this, 50);
            }
        };

        // Start the periodic task
        accelerometerHandler.postDelayed(accelerometerRunnable, 50);
    }

}