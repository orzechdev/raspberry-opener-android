package com.raspberryopener.app;

import android.arch.lifecycle.ViewModel;
import android.util.Log;

public class MainViewModel extends ViewModel {
    private final String TAG = "MainViewModel";
    private BluetoothService bluetoothService;
    private boolean isLogin = false;

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    public void setBluetoothService(BluetoothService BluetoothService) {
        this.bluetoothService = BluetoothService;
    }

    public boolean isLogin() {
        return isLogin;
    }

    public void setLogin(boolean isLogin) {
        this.isLogin = isLogin;
    }

    public void login(String username, String password){
        Log.i(TAG,"login");
        String msg = "login=" + username + "&pass=" + password;
        byte[] msgByte = msg.getBytes();
        bluetoothService.write(msgByte);
    }

    public void openGate(){
        Log.i(TAG,"openGate");
        String msg = "openGate";
        byte[] msgByte = msg.getBytes();
        bluetoothService.write(msgByte);
    }

    public void closeGate(){
        Log.i(TAG,"closeGate");
        String msg = "closeGate";
        byte[] msgByte = msg.getBytes();
        bluetoothService.write(msgByte);
    }
}
