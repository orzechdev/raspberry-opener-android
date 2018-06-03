package com.raspberryopener.app;

import android.Manifest;
import android.app.ActivityManager;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
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
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.UUID;

public class ActivityMain extends AppCompatActivity {
    private final String TAG = "ActivityMain";

    private static final int PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static boolean bluetoothEnabledByApplication = false;

    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTED = 6;

    private int stateUI = BluetoothService.STATE_NONE;
    public static final int THEME_GREEN = 1;
    public static final int THEME_RED = 2;
    public static final int THEME_GRAY = 3;
    private int themeColor = THEME_GRAY;

    public static final String DEVICE_NAME = "device_name";
    private String mConnectedDeviceName = null;

    private MainViewModel viewModel;

    private BluetoothAdapter mBluetoothAdapter;
    private DeviceReceiver deviceReceiver;
    private final ReceiverHandler mReceiverHandler = new ReceiverHandler(this);
    private final ServiceHandler mServiceHandler = new ServiceHandler(this);
    private BluetoothService mBluetoothService;
    private BluetoothDevice pairedBluetoothDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);

        boolean pairedDeviceNotExists = mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && !findPairedDevice();

        // Register receiver to obtain nearby bluetooth devices during searching for them
        deviceReceiver = new DeviceReceiver(mReceiverHandler, pairedDeviceNotExists);
        IntentFilter intentFilter = new IntentFilter();

        if(pairedBluetoothDevice == null) {
            Log.i(TAG, "pairedBluetoothDevice == null");
            intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        }
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
            intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        }
        registerReceiver(deviceReceiver, intentFilter);

        mBluetoothService = viewModel.getBluetoothService();
        if(mBluetoothService == null) {
            mBluetoothService = new BluetoothService(mServiceHandler);
            viewModel.setBluetoothService(mBluetoothService);
        }else{
            mBluetoothService.setServiceHandler(mServiceHandler);
        }

        setupButtons();
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

        int bluetoothState = mBluetoothService.getState();

        if(bluetoothState != stateUI) {
            setUI(mBluetoothService.getState());
        }

        if(bluetoothState == BluetoothService.STATE_NONE || bluetoothState == BluetoothService.STATE_BLUETOOTH_OFF) {
            if (mBluetoothAdapter != null) {
                // Device support Bluetooth
                if (!mBluetoothAdapter.isEnabled()) {
                    turnOnBluetooth();
                } else {
                    // There is need to start discovery now, because we do not wait for broadcast receiver to start discovery when bluetooth will turned on
                    if (pairedBluetoothDevice == null) {
                        startFindBluetoothDevice();
                        mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_ON_SEARCH);
                    } else {
                        initConnectToDevice(pairedBluetoothDevice);
                    }
                    bluetoothEnabledByApplication = false;
                }
            } else {
                // Device doesn't support Bluetooth
                mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_NOT_SUPPORTED);
            }
        }
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

        if(!isChangingConfigurations() || !isScreenOn){

            if(mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();

            mBluetoothService.stop();

            turnOffBluetooth();
        }
    }

    private boolean findPairedDevice(){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPref.contains("device_address")) {
            String requiredHardwareAddress = sharedPref.getString("device_address", "").toUpperCase();

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0)
                Log.i(TAG, "pairedDevices.size() > 0");

            while (pairedDevices.iterator().hasNext()) {
                BluetoothDevice nextDevice = pairedDevices.iterator().next();
                if (nextDevice.getAddress().toUpperCase().equals(requiredHardwareAddress)) {
                    pairedBluetoothDevice = nextDevice;
                    return true;
                }
            }
        }
        return false;
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
        Button openButton = findViewById(R.id.buttonOpen);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"openButton click");
                if(viewModel.isLogin()) {
                    viewModel.openGate();
                }
            }
        });
        Button closeButton = findViewById(R.id.buttonClose);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"closeButton click");
                if(viewModel.isLogin()) {
                    viewModel.closeGate();
                }
            }
        });
        TextView connectionTextView = findViewById(R.id.textConnection);
        connectionTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"connectionTextView click");
                if(stateUI == BluetoothService.STATE_CONNECTION_FAILED || stateUI == BluetoothService.STATE_CONNECTION_LOST){
                    if(pairedBluetoothDevice != null)
                        initConnectToDevice(pairedBluetoothDevice);
                }
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
        // We do not ask user to turn on bluetooth, we will turn it on without user interaction (without dialog)
        mBluetoothAdapter.enable();
        bluetoothEnabledByApplication = true;
        mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_ON_SEARCH);
    }

    private void turnOffBluetooth(){
        if (mBluetoothAdapter != null && bluetoothEnabledByApplication && mBluetoothAdapter.isEnabled()) {
            // Turn off bluetooth
            mBluetoothAdapter.disable();
            mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_OFF);
        }
    }

    private void startFindBluetoothDevice(){
        mBluetoothAdapter.startDiscovery();
    }

    private void stopFindBluetoothDevice(){
        mBluetoothAdapter.cancelDiscovery();
    }

    private void pairDevice(BluetoothDevice device) {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                Log.d(TAG, "Start Pairing... with: " + device.getName());
                device.createBond();
                Log.d(TAG, "Pairing finished.");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }else{
            Toast.makeText(this,"You must pair Your bluetooth device with bluetooth adapter before connect", Toast.LENGTH_LONG).show();
        }
    }

    private void initConnectToDevice(@NonNull BluetoothDevice device){
        if(PreferenceManager.getDefaultSharedPreferences(this).contains("uuid_service")){
            Log.i(TAG, "initConnectToDevice");
            String uuidStr = PreferenceManager.getDefaultSharedPreferences(this).getString("uuid_service", "").toUpperCase();
            if(uuidStr.length() == 36) {
                UUID uuid = Helpers.makeUuid(uuidStr);
                mBluetoothService.connect(device, uuid);
            }else{
                Log.i(TAG, "initConnectToDevice wrong UUID");
            }
        }
    }

    private void setUI(int bluetoothServiceState){
        stateUI = bluetoothServiceState;
        View parentLayout = findViewById(R.id.parent_layout);
        TextView connectionTextView = parentLayout.findViewById(R.id.textConnection);
        String connectionInfo;
        Window window;
        parentLayout.findViewById(R.id.buttonOpen).setEnabled(false);
        parentLayout.findViewById(R.id.buttonClose).setEnabled(false);
        switch (bluetoothServiceState) {
            case BluetoothService.STATE_BLUETOOTH_NOT_SUPPORTED:
                connectionInfo = getResources().getString(R.string.bluetooth_not_supported);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_BLUETOOTH_ON_SEARCH:
                connectionInfo = getResources().getString(R.string.bluetooth_on);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_GRAY;
                break;
            case BluetoothService.STATE_BLUETOOTH_OFF:
                connectionInfo = getResources().getString(R.string.bluetooth_off);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_GRAY;
                break;
            case BluetoothService.STATE_CONNECTED:
                connectionInfo = getResources().getString(R.string.connected);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_CONNECTING:
                connectionInfo = getResources().getString(R.string.connecting_to_device);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_GRAY;
                break;
            case BluetoothService.STATE_CONNECTION_FAILED:
                connectionInfo = getResources().getString(R.string.connecting_failed);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_LOGGED_IN:
                connectionInfo = getResources().getString(R.string.logged_in);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_GATE_OPENING:
                connectionInfo = getResources().getString(R.string.logged_in_opening);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_GATE_OPENED:
                connectionInfo = getResources().getString(R.string.logged_in_opened);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_GATE_CLOSING:
                connectionInfo = getResources().getString(R.string.logged_in_closing);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_GATE_CLOSED:
                connectionInfo = getResources().getString(R.string.logged_in_closed);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                themeColor = THEME_GREEN;
                break;
            case BluetoothService.STATE_WRONG_DATA:
                connectionInfo = getResources().getString(R.string.wrong_data);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_WRONG_USERNAME:
                connectionInfo = getResources().getString(R.string.wrong_username);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_WRONG_PASSWORD:
                connectionInfo = getResources().getString(R.string.wrong_password);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_CONNECTION_LOST:
                connectionInfo = getResources().getString(R.string.connecting_lost);
                connectionTextView.setText(connectionInfo);
                themeColor = THEME_RED;
                break;
            case BluetoothService.STATE_LISTEN:
            case BluetoothService.STATE_NONE:
                themeColor = THEME_GRAY;
                break;
        }
        if(themeColor == THEME_GREEN){
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimary));
                this.setTaskDescription(taskDescription);
            }
        }else if(themeColor == THEME_RED){
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryRed));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryRed));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryRed));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryRed));
            window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryRedDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimaryRed));
                this.setTaskDescription(taskDescription);
            }
        }else if(themeColor == THEME_GRAY){
            parentLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            connectionTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            parentLayout.findViewById(R.id.buttonsContainer).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            parentLayout.findViewById(R.id.buttonsContainerLinear).setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryBlack));
            window = this.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryBlackDark));
                ActivityManager.TaskDescription taskDescription = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.colorPrimaryBlack));
                this.setTaskDescription(taskDescription);
            }
        }
    }

    // The Handler that gets information back from the BluetoothService
    private static class ServiceHandler extends Handler {
        private final String TAG = "ActivityMain SerHandler";
        private final WeakReference<ActivityMain> mActivity;

        public ServiceHandler(ActivityMain activity) {
            mActivity = new WeakReference<ActivityMain>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ActivityMain activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                        activity.setUI(msg.arg1);
                        break;
                    case MESSAGE_WRITE:
                        String writeStr = (String) msg.obj;
                        Log.i(TAG, "MESSAGE_WRITE: " + writeStr);
                        break;
                    case MESSAGE_READ:
                        String readStr = (String) msg.obj;
                        Log.i(TAG, "MESSAGE_READ: " + readStr);
                        if (readStr.startsWith("loginStatus=")) {
                            String dataGateState = readStr.substring(12);
                            if(dataGateState.equals("1&gateIsOpened")){
                                Log.i(TAG, "MESSAGE_READ - Password is correct, gateIsOpened");
                                activity.mBluetoothService.setState(BluetoothService.STATE_GATE_OPENED);
                                activity.viewModel.setLogin(true);
                            }else if(dataGateState.equals("1&gateIsClosed")){
                                Log.i(TAG, "MESSAGE_READ - Password is correct, gateIsClosed");
                                activity.mBluetoothService.setState(BluetoothService.STATE_GATE_CLOSED);
                                activity.viewModel.setLogin(true);
                            }else{
                                Integer loginStatus = Integer.parseInt(dataGateState);
                                if (loginStatus == 1) {
                                    Log.i(TAG, "MESSAGE_READ - Password is correct");
                                    activity.mBluetoothService.setState(BluetoothService.STATE_LOGGED_IN);
                                    activity.viewModel.setLogin(true);
                                } else if (loginStatus == -1) {
                                    Log.i(TAG, "MESSAGE_READ - Password is wrong");
                                    activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_PASSWORD);
                                } else if (loginStatus == -2) {
                                    Log.i(TAG, "MESSAGE_READ - Username do not exists");
                                    activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_USERNAME);
                                }
                            }
                        }else if(readStr.equals("wrongUserData")){
                            Log.i(TAG, "MESSAGE_READ - Username and/or password not accepted");
                            activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_DATA);
                        }else if(readStr.equals("openingGate")){
                            Log.i(TAG, "MESSAGE_READ - openingGate");
                            activity.mBluetoothService.setState(BluetoothService.STATE_GATE_OPENING);
                        }else if(readStr.equals("closingGate")){
                            Log.i(TAG, "MESSAGE_READ - closingGate");
                            activity.mBluetoothService.setState(BluetoothService.STATE_GATE_CLOSING);
                        }else if(readStr.equals("gateIsOpened")){
                            Log.i(TAG, "MESSAGE_READ - gateIsOpened");
                            activity.mBluetoothService.setState(BluetoothService.STATE_GATE_OPENED);
                        }else if(readStr.equals("gateIsClosed")){
                            Log.i(TAG, "MESSAGE_READ - gateIsClosed");
                            activity.mBluetoothService.setState(BluetoothService.STATE_GATE_CLOSED);
                        }
                        break;
                    case MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        activity.mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                        Toast.makeText(activity, "Connected to "
                                + activity.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        break;
//                    case MESSAGE_TOAST:
//                        Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
//                        break;
                    case MESSAGE_CONNECTED:
                        Log.i(TAG, "MESSAGE_CONNECTED");
                        // Device is connected with bluetooth adapter, try to log in to adapter
                        String username = PreferenceManager.getDefaultSharedPreferences(activity).getString("username", "");
                        String password = PreferenceManager.getDefaultSharedPreferences(activity).getString("password", "");
                        activity.viewModel.login(username, password);
                        break;
                }
            }
        }
    }

    private static class ReceiverHandler extends Handler {
        private final String TAG = "ActivityMain RecHandler";
        private final WeakReference<ActivityMain> mActivity;

        public ReceiverHandler(ActivityMain activity) {
            mActivity = new WeakReference<ActivityMain>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ActivityMain activity = mActivity.get();
            if (activity != null) {
                if(msg.what == DeviceReceiver.MSG_STATE_CHANGED) {
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
                            if(bluetoothEnabledByApplication)
                                activity.deviceReceiver.pairedDeviceNotExists = !activity.findPairedDevice();
                            if(activity.pairedBluetoothDevice == null)
                                activity.startFindBluetoothDevice();
                            else
                                activity.initConnectToDevice(activity.pairedBluetoothDevice);
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            Log.i(TAG, "Turning Bluetooth on...");
                            break;
                    }
                }else if(msg.what == DeviceReceiver.MSG_BLUETOOTH_DEVICE){
                    Log.i(TAG, "Found required bluetooth device");
                    // First pair device
                    BluetoothDevice device = (BluetoothDevice)msg.obj;
//                    activity.initConnectToDevice(device);
                    activity.pairDevice(device);
                }else if(msg.what == DeviceReceiver.MSG_BLUETOOTH_DEVICE_PAIRED){
                    // Second connect to paired device
                    activity.pairedBluetoothDevice = (BluetoothDevice)msg.obj;
                    activity.initConnectToDevice(activity.pairedBluetoothDevice);
                }
            }
        }
    }
}
