package com.raspberryopener.app;

import android.arch.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private BluetoothService bluetoothService;

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    public void setBluetoothService(BluetoothService BluetoothService) {
        this.bluetoothService = BluetoothService;
    }
}
