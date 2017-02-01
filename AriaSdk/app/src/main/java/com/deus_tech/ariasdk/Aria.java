package com.deus_tech.ariasdk;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.deus_tech.ariasdk.ble.BluetoothGattCallback;
import com.deus_tech.ariasdk.ble.BluetoothScan;
import com.deus_tech.ariasdk.ble.ConnectionGattListener;
import com.deus_tech.aria.AriaConnectionEvents.ConnectedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DisconnectedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DiscoveryFinishedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DiscoveryStartedEvent;
import com.deus_tech.aria.AriaConnectionEvents.ReadyEvent;
import com.deus_tech.aria.AriaConnectionEvents.ReconnectEvent;
import com.deus_tech.ariasdk.nusBleService.NusBleService;
import com.deus_tech.ariasdk.nusBleService.NusInitListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;


public class Aria extends BroadcastReceiver implements
        ConnectionGattListener, NusInitListener {
    private String TAG = "AriaService";

    public final static int GESTURE_ENTER = 1; //right - 4F
    public final static int GESTURE_HOME = 2;  //enter - 28
    public final static int GESTURE_UP = 3;    //down - 51
    public final static int GESTURE_DOWN = 4;  //up - 52
    public final static int GESTURE_BACK = 5;  //left - 50

    public final static String FLICKTEK_DEVICE_NAME = "FlickTek Clip";

    public final static int STATUS_NONE = 1;
    public final static int STATUS_DISCOVERING = 2;
    public final static int STATUS_FOUND = 3;
    public final static int STATUS_CONNECTING = 4;
    public final static int STATUS_CONNECTED = 5;
    public final static int STATUS_READY = 6;
    public final static int STATUS_DISCONNECTED = 7;

    public final static int MAX_RECONNECT_ATTEMPTS = 5;

    private static Aria instance;
    private Context context;
    //bluetooth
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private final BluetoothScan btScan;
    private BluetoothDevice device;
    private String address = "";
    //gatt
    private BluetoothGatt btGatt;
    private BluetoothGattCallback btGattCallback;

    //status
    private int status;

    private int reconnect_attemps = 0;

    //services
    private NusBleService nus;

    public static Aria getInstance(Context _context) {
        if (Aria.instance == null) {
            Aria.instance = new Aria(_context);
        }
        return Aria.instance;
    }

    public void setDeviceAddress(String address) {
        if (address == null) {
            Log.v(TAG, "Empty device address");
            return;
        }

        if (this.address != null) {
            disconnect(false);
        }

        this.address = address;
    }

    private int oldStatus = -1;

    public int getStatus() {

        if (oldStatus != status) {
            switch (status) {
                case Aria.STATUS_NONE:
                    Log.d(TAG, "getStatus: STATUS_NONE");
                    break;
                case Aria.STATUS_DISCOVERING:
                    Log.d(TAG, "getStatus: STATUS_DISCOVERING");
                    break;
                case Aria.STATUS_FOUND:
                    Log.d(TAG, "getStatus: STATUS_FOUND");
                    break;
                case Aria.STATUS_CONNECTING:
                    Log.d(TAG, "getStatus: STATUS_CONNECTING");
                    break;
                case Aria.STATUS_CONNECTED:
                    Log.d(TAG, "getStatus: STATUS_CONNECTED");
                    break;
                case Aria.STATUS_READY:
                    Log.d(TAG, "getStatus: STATUS_READY");
                    break;
                default:
                    Log.d(TAG, "getStatus: UNKNOWN");
                    break;
            }
            oldStatus = status;
        }
        return status;
    }

    public void writeStatus_Sleep() {
        Log.d(TAG, "writeStatus_Sleep: ");
        if (nus != null)
            nus.writeStatus_Sleep();
    }

    public void writeStatus_Exec() {
        Log.d(TAG, "writeStatus_Exec: ");
        if (nus != null)
            nus.writeStatus_Exec();
    }

    public void startDiscovery() {
        Log.d(TAG, "startDiscovery: ");
        device = null;
        status = STATUS_NONE;

        if (btAdapter != null && btAdapter.isEnabled() == false) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(this, filter);

            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableIntent);
        } else {
            btScan.startLeScan(
                    new BluetoothScan.DiscoveryListener() {
                        @Override
                        public void onDeviceFound(BluetoothDevice device) {
                            Log.v(TAG, "Found device " + device.getName() + " " + device.getAddress());
                            if (device.getName() != null && device.getAddress().equals(address)) {
                                Log.v(TAG, "Stop discovery and report device found");
                                stopDiscovery();
                                Aria.instance.device = device;
                                connect(device);
                            }
                        }

                        @Override
                        public void onDeviceLost(BluetoothDevice device) {
                        }

                        @Override
                        public void onStarted() {
                            Log.d(TAG, "onDiscoveryStarted: ");
                            status = Aria.STATUS_DISCOVERING;
                            EventBus.getDefault().post(new DiscoveryStartedEvent());
                        }

                        @Override
                        public void onFinished(ArrayList<BluetoothDevice> deviceArray) {
                            Log.d(TAG, "onDiscoveryFinished: ");

                            if (status == Aria.STATUS_DISCONNECTED) {
                                Log.d(TAG, "Aria wants to be disconnected! ");
                                status = Aria.STATUS_NONE;
                                device = null;
                                EventBus.getDefault().post(new DiscoveryFinishedEvent(false));
                                return;
                            }

                            if (device == null) {
                                status = Aria.STATUS_NONE;
                            } else {
                                status = Aria.STATUS_FOUND;
                            }

                            EventBus.getDefault().post(new DiscoveryFinishedEvent(device != null));
                        }
                    }
            );
        }
    }

    public void stopDiscovery() {
        Log.d(TAG, "stopDiscovery: ");

        if (btAdapter != null)
            btAdapter.cancelDiscovery();
        btScan.stopLeScan();
    }

    public void connect(BluetoothDevice device) {
        if (this.device != device) {
            disconnect(true);
            this.device = device;
        }
        Log.d(TAG, "connect: ");
        if (this.device != null) {
            status = Aria.STATUS_CONNECTING;
            btGatt = this.device.connectGatt(context, false, btGattCallback);
        }
    }

    public void disconnect(boolean force_disconnect) {
        Log.d(TAG, "disconnect: ");
        if (force_disconnect) {
            Log.v(TAG, "STATUS_DISCONNECTED");
            status = Aria.STATUS_DISCONNECTED;
            reconnect_attemps = MAX_RECONNECT_ATTEMPTS;
        }

        if (btGatt != null) {
            btGatt.disconnect();
        }

        if (nus != null) {
            nus.removeInitListener(this);
        }
    }

    //private
    private Aria(Context _context) {
        Log.d(TAG, "Aria: ");
        context = _context;

        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScan = new BluetoothScan(btAdapter);
        initBtGattCallback();

        status = Aria.STATUS_NONE;
    }

    private void initBtGattCallback() {
        Log.d(TAG, "initBtGattCallback: ");
        btGattCallback = new BluetoothGattCallback();
        btGattCallback.setConnectionListener(this);
    }

    //BluetoothBroadcastListener
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: ");
        String action = intent.getAction();
        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (state == BluetoothAdapter.STATE_ON) {
                context.unregisterReceiver(this);
                btAdapter.startDiscovery();
            }
        }
    }

    public void onDeviceConnected(List<BluetoothGattService> services) {
        Log.d(TAG, "onDeviceConnected: ");
        status = Aria.STATUS_CONNECTED;
        EventBus.getDefault().post(new ConnectedEvent());

        for (int i = 0; i < services.size(); i++) {
            BluetoothGattService service = services.get(i);
            Log.v(TAG, "+ Service " + service.getUuid());

            if (service.getUuid().equals(NusBleService.RX_SERVICE_UUID)) {
                Log.v(TAG, "+ Service Found RX_SERVICE_UUID");
                if (nus != null) {
                    nus.removeInitListener(this);
                }

                nus = new NusBleService(context, btGatt, service);
                nus.addInitListener(this);
                btGattCallback.setNusListener(nus);
            }
        }

        reconnect_attemps = 0;
    }

    public void onDeviceDisconnected() {
        Log.d(TAG, "onDeviceDisconnected: " + status);

        if (status != Aria.STATUS_DISCONNECTED && reconnect_attemps < MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "---------- RECONNECT -------- ");
            EventBus.getDefault().post(new ReconnectEvent());
            status = Aria.STATUS_CONNECTING;
            reconnect_attemps++;
            connect(device);
        } else {
            status = Aria.STATUS_NONE;
            EventBus.getDefault().post(new DisconnectedEvent());
            reconnect_attemps = 0;
        }
    }

    public void onCalibrationInit() {
        Log.d(TAG, "onCalibrationInit: ");
    }

    @Override
    public void onNusInit() {
        Log.d(TAG, "onNusInit: ");
        status = Aria.STATUS_READY;
        EventBus.getDefault().post(new ReadyEvent());
    }

    public NusBleService getNus() {
        return nus;
    }
}
