package com.raspberryopener.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class DeviceReceiver extends BroadcastReceiver {
    private final String TAG = "DeviceReceiver";

    private Handler mhandle;

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
            Log.i(TAG, deviceName + ", " + deviceHardwareAddress);
        }else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            // Bluetooth was turned on, turned off...
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            Message msg = new Message();
            msg.obj = state;
            mhandle.sendMessage(msg);
        }
    }
}
