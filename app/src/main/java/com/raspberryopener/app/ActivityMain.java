package com.raspberryopener.app;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class ActivityMain extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static boolean bluetoothEnabledByApplication = false;

    private static boolean isRotating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();

        turnOnBluetooth();

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

    private void turnOnBluetooth(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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
}
