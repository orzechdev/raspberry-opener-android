package com.raspberryopener.app;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class ActivityMain extends AppCompatActivity {
    private final String TAG = "ActivityMain";

    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static boolean bluetoothEnabledByApplication = false;

    private static boolean isRotating = false;

    private BluetoothAdapter mBluetoothAdapter;
    private DeviceReceiver deviceReceiver;
    private final ReceiverHandler mHandler = new ReceiverHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceReceiver = new DeviceReceiver(mHandler);

        setupButtons();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);

        // Register receiver to obtain nearby bluetooth devices during searching for them
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(deviceReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister receiver to not care about searched nearby bluetooth devices after application end
        unregisterReceiver(deviceReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        turnOnBluetooth();

        if(!isRotating) {
            if(mBluetoothAdapter.isEnabled())
                mBluetoothAdapter.startDiscovery();
        }

        if(isRotating)
            isRotating = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = true;
        if(pm != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                isScreenOn = pm.isScreenOn();
            else
                isScreenOn = pm.isInteractive();
        }

        if(isChangingConfigurations() && isScreenOn){
            isRotating = true;
        }else{
            isRotating = false;

            mBluetoothAdapter.cancelDiscovery();

            turnOffBluetooth();
        }
    }

    private void setupButtons(){
        Button settingsButton = findViewById(R.id.buttonSettings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ActivityMain.this, ActivitySettings.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, do the tasks you need to do.
                } else {
                    // permission denied, Disable the functionality that depends on this permission.
                    finish();
                }
                return;
            }
            // other 'switch' lines to check for other
            // permissions this app might request
        }
    }

    private void turnOnBluetooth(){
        String connectionInfo;
        boolean bluetoothIsOn;
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            connectionInfo = getResources().getString(R.string.bluetooth_not_supported);
            bluetoothIsOn = false;
        }else{
            if(!isRotating) {
                // Device support Bluetooth
                if (!mBluetoothAdapter.isEnabled()) {
                    // We do not ask user to turn on bluetooth, we will turn it on without user interaction (without dialog)
                    mBluetoothAdapter.enable();
                    bluetoothEnabledByApplication = true;
                } else {
                    bluetoothEnabledByApplication = false;
                }
            }
            connectionInfo = getResources().getString(R.string.bluetooth_on);
            bluetoothIsOn = true;
        }
        // Showing information
        View parentLayout = findViewById(R.id.parent_layout);
        TextView connectionTextView = parentLayout.findViewById(R.id.textConnection);
        connectionTextView.setText(connectionInfo);
        if(bluetoothIsOn) {
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            Window window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimary));
                (this).setTaskDescription(taskDescription);
            }
        }
    }

    private void turnOffBluetooth(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String connectionInfo = "";
        boolean bluetoothIsOff;
        if (mBluetoothAdapter != null && bluetoothEnabledByApplication && mBluetoothAdapter.isEnabled()) {
            // Turn off bluetooth
            mBluetoothAdapter.disable();
            connectionInfo = getResources().getString(R.string.bluetooth_off);
            bluetoothIsOff = true;
        }else{
            bluetoothIsOff = false;
        }
        // Showing information
        if(bluetoothIsOff) {
            View parentLayout = findViewById(R.id.parent_layout);
            TextView connectionTextView = parentLayout.findViewById(R.id.textConnection);
            connectionTextView.setText(connectionInfo);
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            Window window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryBlackDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimaryBlack));
                (this).setTaskDescription(taskDescription);
            }
        }
    }

    private void startFindBluetoothDevice(){
        mBluetoothAdapter.startDiscovery();
    }

    private void stopFindBluetoothDevice(){
        mBluetoothAdapter.cancelDiscovery();
    }

    private static class ReceiverHandler extends Handler {
        private final String TAG = "ActivityMain Handler";
        private final WeakReference<ActivityMain> mActivity;

        public ReceiverHandler(ActivityMain activity) {
            mActivity = new WeakReference<ActivityMain>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ActivityMain activity = mActivity.get();
            if (activity != null) {
                final int state = (int)msg.obj;
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(TAG, "Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i(TAG, "Turning Bluetooth off...");
                        activity.stopFindBluetoothDevice();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG, "Bluetooth on");
                        activity.startFindBluetoothDevice();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i(TAG, "Turning Bluetooth on...");
                        break;
                }
            }
        }
    }
}
