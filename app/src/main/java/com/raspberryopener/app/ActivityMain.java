package com.raspberryopener.app;

import android.Manifest;
import android.app.ActivityManager;
import android.arch.lifecycle.ViewModelProviders;
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

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
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

    public static final String DEVICE_NAME = "device_name";
    private String mConnectedDeviceName = null;

    private MainViewModel viewModel;

    private BluetoothAdapter mBluetoothAdapter;
    private DeviceReceiver deviceReceiver;
    private final ReceiverHandler mReceiverHandler = new ReceiverHandler(this);
    private final ServiceHandler mServiceHandler = new ServiceHandler(this);
    private BluetoothService mBluetoothService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        deviceReceiver = new DeviceReceiver(mReceiverHandler);

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

        mBluetoothService = viewModel.getBluetoothService();
        if(mBluetoothService == null) {
            mBluetoothService = new BluetoothService(mServiceHandler);
            viewModel.setBluetoothService(mBluetoothService);
        }else{
            mBluetoothService.setServiceHandler(mServiceHandler);
        }

        Button openButton = (Button) findViewById(R.id.buttonOpen);
        Button closeButton = (Button) findViewById(R.id.buttonClose);

        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"openButton click");
                if(viewModel.isLogin()) {
                    viewModel.openGate();
                }
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"closeButton click");
                if(viewModel.isLogin()) {
                    viewModel.closeGate();
                }
            }
        });
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
            turnOnBluetooth();
        }

        initConnectToDevice(null);
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
        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_NOT_SUPPORTED);
        }else{
            // Device support Bluetooth
            if (!mBluetoothAdapter.isEnabled()) {
                // We do not ask user to turn on bluetooth, we will turn it on without user interaction (without dialog)
                mBluetoothAdapter.enable();
                bluetoothEnabledByApplication = true;
            } else {
                // There is need to start discovery now, because we do not wait for broadcast receiver to start discovery when bluetooth will turned on
                startFindBluetoothDevice();
                bluetoothEnabledByApplication = false;
            }
            mBluetoothService.setState(BluetoothService.STATE_BLUETOOTH_ON);
        }
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

    private void initConnectToDevice(BluetoothDevice device){
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
                break;
            case BluetoothService.STATE_BLUETOOTH_ON:
                connectionInfo = getResources().getString(R.string.bluetooth_on);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_BLUETOOTH_OFF:
                connectionInfo = getResources().getString(R.string.bluetooth_off);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_CONNECTED:
                connectionInfo = getResources().getString(R.string.connected);
                connectionTextView.setText(connectionInfo);
                break;
            case BluetoothService.STATE_CONNECTING:
                connectionInfo = getResources().getString(R.string.connecting_to_device);
                connectionTextView.setText(connectionInfo);
                break;
            case BluetoothService.STATE_CONNECTION_FAILED:
                connectionInfo = getResources().getString(R.string.connecting_failed);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_LOGGED_IN:
                connectionInfo = getResources().getString(R.string.logged_in);
                connectionTextView.setText(connectionInfo);
                parentLayout.findViewById(R.id.buttonOpen).setEnabled(true);
                parentLayout.findViewById(R.id.buttonClose).setEnabled(true);
                break;
            case BluetoothService.STATE_WRONG_DATA:
                connectionInfo = getResources().getString(R.string.wrong_data);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_WRONG_USERNAME:
                connectionInfo = getResources().getString(R.string.wrong_username);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_WRONG_PASSWORD:
                connectionInfo = getResources().getString(R.string.wrong_password);
                connectionTextView.setText(connectionInfo);
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
                break;
            case BluetoothService.STATE_CONNECTION_LOST:
            case BluetoothService.STATE_LISTEN:
            case BluetoothService.STATE_NONE:
                break;
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
                            Integer loginStatus = Integer.parseInt(readStr.substring(12));
                            if(loginStatus == 1){
                                Log.i(TAG, "MESSAGE_READ - Password is correct");
                                activity.mBluetoothService.setState(BluetoothService.STATE_LOGGED_IN);
                                activity.viewModel.setLogin(true);
                            }else if(loginStatus == -1){
                                Log.i(TAG, "MESSAGE_READ - Password is wrong");
                                activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_PASSWORD);
                            }else if(loginStatus == -2){
                                Log.i(TAG, "MESSAGE_READ - Username do not exists");
                                activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_USERNAME);
                            }
                        }else if(readStr.equals("wrongUserData")){
                            Log.i(TAG, "MESSAGE_READ - Username and/or password not accepted");
                            activity.mBluetoothService.setState(BluetoothService.STATE_WRONG_DATA);
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
                            activity.startFindBluetoothDevice();
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            Log.i(TAG, "Turning Bluetooth on...");
                            break;
                    }
                }else if(msg.what == DeviceReceiver.MSG_BLUETOOTH_DEVICE){
                    Log.i(TAG, "Found required bluetooth device");
                    BluetoothDevice device = (BluetoothDevice)msg.obj;
                    activity.initConnectToDevice(device);
                }
            }
        }
    }
}
