package com.raspberryopener.app;

import android.arch.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
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
}
