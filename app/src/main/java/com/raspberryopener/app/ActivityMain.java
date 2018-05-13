package com.raspberryopener.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class ActivityMain extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupButtons();

        turnOnBluetooth();
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
        String snackbarInfo;
        boolean bluetoothIsOn = false;
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            snackbarInfo = getResources().getString(R.string.bluetooth_not_supported);
            bluetoothIsOn = false;
        }else{
            // Device support Bluetooth
            if (!mBluetoothAdapter.isEnabled()) {
//                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                // We do not ask user to turn on bluetooth, we will turn it on without user interaction (without dialog)
                mBluetoothAdapter.enable();
            }
            snackbarInfo = getResources().getString(R.string.bluetooth_on);
            bluetoothIsOn = true;
        }
        // Showing information
        View parentLayout = findViewById(R.id.parent_layout);
        TextView connectionTextView = parentLayout.findViewById(R.id.textConnection);
        connectionTextView.setText(snackbarInfo);
        if(bluetoothIsOn) {
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            Window window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimary));
                ((Activity)this).setTaskDescription(taskDescription);
            }
        }

//            Snackbar snackbar = Snackbar.make(parentLayout, snackbarInfo, Snackbar.LENGTH_LONG);
//            View sbView = snackbar.getView();
//            sbView.setBackgroundColor(ContextCompat.getColor(this.getApplicationContext(), R.color.snackbar_background));
//            TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
//            textView.setBackgroundColor(ContextCompat.getColor(this.getApplicationContext(), R.color.snackbar_background));
//            snackbar.show();
    }
}
