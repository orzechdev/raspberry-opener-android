package com.raspberryopener.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";
    private Handler mHandler; // handler that gets info from Bluetooth service

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_BLUETOOTH_NOT_SUPPORTED = -1;  // Device doesn't support bluetooth
    public static final int STATE_BLUETOOTH_OFF = 0;             // Bluetooth is turned off
    public static final int STATE_BLUETOOTH_ON_SEARCH = 1;       // Bluetooth is turned on
    public static final int STATE_NONE = 100;                    // we're doing nothing
    public static final int STATE_LISTEN = 101;                  // now listening for incoming connections
    public static final int STATE_CONNECTING = 102;              // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 103;               // now connected to a remote device
    public static final int STATE_CONNECTION_FAILED = 104;       // we're unable to connect to the device
    public static final int STATE_CONNECTION_LOST = 105;         // we're unable to connect to the device
    public static final int STATE_LOGGED_IN = 106;               // user is logged in on a remote device
    public static final int STATE_WRONG_DATA = 107;              // user cannot log in, wrong username and password
    public static final int STATE_WRONG_USERNAME = 108;          // user cannot log in, wrong username
    public static final int STATE_WRONG_PASSWORD = 109;          // user cannot log in, wrong password
    public static final int STATE_GATE_OPENING = 120;            // user cannot log in, wrong password
    public static final int STATE_GATE_CLOSING = 121;            // user cannot log in, wrong password
    public static final int STATE_GATE_OPENED = 122;             // user cannot log in, wrong password
    public static final int STATE_GATE_CLOSED = 123;             // user cannot log in, wrong password

    public BluetoothService(Handler handler) {
        mHandler = handler;
        mState = STATE_NONE;
    }

    public void setState(int state) {
        Log.i(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(ActivityMain.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public int getState() {
        return mState;
    }

    public void setServiceHandler(Handler serviceHandler) {
        this.mHandler = serviceHandler;
    }

    public void connect(BluetoothDevice device, UUID deviceUUID) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, deviceUUID);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    private void manageConnectedSocket(BluetoothSocket socket, BluetoothDevice device){
        Log.i(TAG, "ConnectThread 1");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
//            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(ActivityMain.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(ActivityMain.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);

        Message msgConnected = mHandler.obtainMessage(ActivityMain.MESSAGE_CONNECTED);
        msgConnected.sendToTarget();
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    private void connectionFailed() {
        setState(STATE_CONNECTION_FAILED);
    }

    private void connectionLost() {
        setState(STATE_CONNECTION_LOST);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED && mState != STATE_LOGGED_IN && mState != STATE_WRONG_DATA
                    && mState != STATE_WRONG_PASSWORD && mState != STATE_WRONG_USERNAME
                    && mState != STATE_GATE_OPENING && mState != STATE_GATE_CLOSING
                    && mState != STATE_GATE_OPENED && mState != STATE_GATE_CLOSED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    private class ConnectThread extends Thread {
        private final String TAG = "BS ConnectThread";
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;
        private final BluetoothAdapter mBluetoothAdapter;

        public ConnectThread(BluetoothDevice device, UUID deviceUUID) {
            Log.i(TAG, "ConnectThread 1");
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothSocket tmp = null;
            mmDevice = device;

//            try {
                Log.i(TAG, "ConnectThread try 1");
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.

                try {
                    Log.d(TAG, "Start Pairing...");
//                    (BluetoothSocket)mmDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class }).invoke(mmDevice, 1);
                    Method m = mmDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
                    tmp = (BluetoothSocket)m.invoke(mmDevice, 1);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }

//                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"));
                Log.i(TAG, "ConnectThread try 2");
//            } catch (IOException e) {
//                Log.e(TAG, "Socket's create() method failed", e);
//            }
            mmSocket = tmp;
            Log.i(TAG, "ConnectThread 2");
        }

        public void run() {
            Log.i(TAG, "run");
            // Cancel discovery because it otherwise slows down the connection.
            if(mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                Log.i(TAG, "run try 1");
                mmSocket.connect();

//                mmSocket = (BluetoothSocket)mmDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class }).invoke(mmDevice, 1);

                Log.i(TAG, "run try 2");
            } catch (Exception exception){//IOException connectException) {
                Log.i(TAG, "run catch 1");
                // Unable to connect; close the socket and return.
                connectionFailed();
                Log.i(TAG, "run catch 2");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                Log.i(TAG, "The connection attempt failed.");
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            Log.i(TAG, "The connection attempt succeeded.");
            manageConnectedSocket(mmSocket, mmDevice);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            Log.i(TAG, "cancel");
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
            Log.i(TAG, "Client socket closed.");
        }
    }

    private class ConnectedThread extends Thread {
        private final String TAG = "BS ConnectedThread";
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothAdapter mBluetoothAdapter;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            Log.i(TAG, "ConnectedThread 1");
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            Log.i(TAG, "ConnectedThread 2");
        }

        public void run() {
            Log.i(TAG, "run 1");
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()
            mBluetoothAdapter.cancelDiscovery();

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    String str = new String(mmBuffer, 0, numBytes).trim();
                    Message readMsg = mHandler.obtainMessage(
                            ActivityMain.MESSAGE_READ, numBytes, -1,
                            str);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    connectionLost();
                    break;
                }
            }
            Log.i(TAG, "run 2");
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            Log.i(TAG, "write 1");
            try {
                mmOutStream.write(bytes);
                String str = new String(bytes).trim();

                // Share the sent message with the UI activity.
                Message writtenMsg = mHandler.obtainMessage(
                        ActivityMain.MESSAGE_WRITE, -1, -1, str);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(ActivityMain.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
            Log.i(TAG, "write 2");
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            Log.i(TAG, "cancel 1");
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
            Log.i(TAG, "cancel 2");
        }
    }
}
