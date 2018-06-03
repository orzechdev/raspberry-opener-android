package com.raspberryopener.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.UUID;

public class DeviceReceiver extends BroadcastReceiver {
    private final String TAG = "DeviceReceiver";

    private Handler mhandle;
    public boolean pairedDeviceNotExists;
    public static final int MSG_BLUETOOTH_DEVICE = 1;
    public static final int MSG_STATE_CHANGED = 2;
    public static final int MSG_BLUETOOTH_DEVICE_PAIRED = 3;

    public DeviceReceiver(Handler mhandle, boolean pairedDeviceNotExists) {
        this.mhandle = mhandle;
        this.pairedDeviceNotExists = pairedDeviceNotExists;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, TAG);
        if(BluetoothDevice.ACTION_FOUND.equals(action) && pairedDeviceNotExists){
            // Discovery has found a device. Get the BluetoothDevice
            // object and its info from the Intent.
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String deviceName = device.getName();
            String deviceHardwareAddress = device.getAddress(); // MAC address
            ParcelUuid[] deviceUUIDs = device.getUuids(); // MAC address
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            if(sharedPref.contains("device_address")) {
                String requiredHardwareAddress = sharedPref.getString("device_address", "").toUpperCase();
                String requiredOk = "WRONG";
                if (deviceHardwareAddress.equals(requiredHardwareAddress)) {
                    Message msg = new Message();
                    msg.obj = device;
                    msg.what = MSG_BLUETOOTH_DEVICE;
                    mhandle.sendMessage(msg);
                    requiredOk = "OK, UUIDs: " + deviceUUIDs;
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
        }else if(BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)){
            Log.d(TAG, "ACTION_PAIRING_REQUEST");
            BluetoothDevice pairedDevice = null;
            try {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 1234);
                //the pin in case you need to accept for an specific pin
                Log.d(TAG, "Start Auto Pairing. PIN = " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY",1234));
                byte[] pinBytes;
                pinBytes = (""+pin).getBytes("UTF-8");
                Log.d(TAG, "Try to set the PIN");
                Method m = device.getClass().getMethod("setPin", byte[].class);
                m.invoke(device, pinBytes);
                Log.d(TAG, "Success to add the PIN.");
                try {
                    device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
                    Log.d(TAG, "Success to setPairingConfirmation.");
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            Log.d(TAG, "ACTION_BOND_STATE_CHANGED: " + Integer.toString(state));
            if(state == BluetoothDevice.BOND_BONDED) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Message msg = new Message();
                msg.obj = device;
                msg.what = MSG_BLUETOOTH_DEVICE_PAIRED;
                mhandle.sendMessage(msg);
            }
        }
    }
}
