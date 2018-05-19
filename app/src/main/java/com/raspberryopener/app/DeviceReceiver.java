package com.raspberryopener.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class DeviceReceiver extends BroadcastReceiver {
    private final String TAG = "DeviceReceiver";

    private Handler mhandle;
    public static final int MSG_BLUETOOTH_DEVICE = 1;
    public static final int MSG_STATE_CHANGED = 2;

    public DeviceReceiver(Handler mhandle) {
        this.mhandle = mhandle;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, TAG);
        if(BluetoothDevice.ACTION_FOUND.equals(action)){
            // Discovery has found a device. Get the BluetoothDevice
            // object and its info from the Intent.
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            if(sharedPref.contains("device_address")) {
                String requiredHardwareAddress = sharedPref.getString("device_address", "").toUpperCase();
                String requiredOk = "WRONG";
                if (deviceHardwareAddress.equals(requiredHardwareAddress)) {
                    Message msg = new Message();
                    msg.obj = device;
                    msg.what = MSG_BLUETOOTH_DEVICE;
                    mhandle.sendMessage(msg);
                    requiredOk = "OK";
                }
                Log.i(TAG, deviceName + ", " + deviceHardwareAddress + " - " + requiredOk);
            }
        }else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            // Bluetooth was turned on, turned off...
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            Message msg = new Message();
            msg.obj = state;
            msg.what = MSG_STATE_CHANGED;
            mhandle.sendMessage(msg);
        }
    }
}
