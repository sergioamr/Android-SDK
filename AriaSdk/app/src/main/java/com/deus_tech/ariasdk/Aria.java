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

import com.deus_tech.aria.AriaConnectionEvents.ConnectedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DisconnectedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DiscoveryFinishedEvent;
import com.deus_tech.aria.AriaConnectionEvents.DiscoveryStartedEvent;
import com.deus_tech.ariasdk.ariaBleService.AriaBleService;
import com.deus_tech.ariasdk.ariaBleService.ArsInitListener;
import com.deus_tech.ariasdk.ble.BluetoothBroadcastListener;
import com.deus_tech.ariasdk.ble.BluetoothGattCallback;
import com.deus_tech.ariasdk.ble.BluetoothScan;
import com.deus_tech.ariasdk.ble.ConnectionGattListener;
import com.deus_tech.ariasdk.calibrationBleService.CalibrationBleService;
import com.deus_tech.ariasdk.calibrationBleService.CasInitListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;


public class Aria extends BroadcastReceiver implements BluetoothBroadcastListener, ConnectionGattListener, CasInitListener, ArsInitListener {
    private String TAG = "Aria";

    public final static String DEVICE_NAME = "Aria";
    public static String DEVICE_PROTOCOL = "7";

    public final static int STATUS_NONE = 1;
    public final static int STATUS_DISCOVERING = 2;
    public final static int STATUS_FOUND = 3;
    public final static int STATUS_CONNECTING = 4;
    public final static int STATUS_CONNECTED = 5;
    public final static int STATUS_READY = 6;


    private static Aria instance;
    private Context context;
    //bluetooth
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private final BluetoothScan btScan;
    private BluetoothDevice device;
    //gatt
    private BluetoothGatt btGatt;
    private BluetoothGattCallback btGattCallback;

    //status
    private int status;
    //services
    private CalibrationBleService cas;
    private AriaBleService ars;

    public static Aria getInstance(Context _context) {
        if (Aria.instance == null) {
            Aria.instance = new Aria(_context);
        }
        return Aria.instance;
    }

    public CalibrationBleService getCas() {
        Log.d(TAG, "getCas: ");
        return cas;
    }

    public AriaBleService getArs() {
        Log.d(TAG, "getArs: ");
        return ars;
    }

    public int getStatus() {
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
        return status;
    }

    public void writeStatus_Sleep() {
        Log.d(TAG, "writeStatus_Sleep: ");
        if (cas != null)
            cas.writeStatus_Sleep();
    }

    public void writeStatus_Exec() {
        Log.d(TAG, "writeStatus_Exec: ");
        if (cas != null)
            cas.writeStatus_Exec();
    }

    public void startDiscovery() {
        Log.d(TAG, "startDiscovery: ");
        device = null;

        if (btAdapter != null && btAdapter.isEnabled() == false) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(this, filter);

            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableIntent);
        } else {
            //btAdapter.startDiscovery();
            btScan.startLeScan(
                    new BluetoothScan.DiscoveryListener() {
                        @Override
                        public void onDeviceFound(BluetoothDevice device) {
                            if (device.getName() != null &&
                                    device.getName().startsWith(Aria.DEVICE_NAME) &&
                                    device.getName().endsWith(Aria.DEVICE_PROTOCOL)) {
                                Aria.this.onDeviceFound(device);
                            }
                        }

                        @Override
                        public void onDeviceLost(BluetoothDevice device) {
                        }

                        @Override
                        public void onStarted() {
                            Aria.this.onDiscoveryStarted();
                        }

                        @Override
                        public void onFinished(ArrayList<BluetoothDevice> deviceArray) {
                            Aria.this.onDiscoveryFinished();
                        }
                    }
            );
        }
    }

    public void stopDiscovery() {
        Log.d(TAG, "stopDiscovery: ");
        disconnect();
        if (btAdapter != null)
            btAdapter.cancelDiscovery();
        btScan.stopLeScan();
    }

    public void connect() {
        Log.d(TAG, "connect: ");
        if (device != null) {
            status = Aria.STATUS_CONNECTING;
            btGatt = device.connectGatt(context, false, btGattCallback);
        }
    }

    public void disconnect() {
        Log.d(TAG, "disconnect: ");
        if (btGatt != null) {
            btGatt.disconnect();
        }

        if (cas != null) {
            cas.removeInitListener(this);
        }

        if (ars != null) {
            ars.removeInitListener(this);
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

    //ConnectionGattListener
    public void onDiscoveryStarted() {
        Log.d(TAG, "onDiscoveryStarted: ");
        status = Aria.STATUS_DISCOVERING;

        EventBus.getDefault().post(new DiscoveryStartedEvent());
    }

    public void onDiscoveryFinished() {
        Log.d(TAG, "onDiscoveryFinished: ");
        if (device == null) {
            status = Aria.STATUS_NONE;
        } else {
            status = Aria.STATUS_FOUND;
        }

        EventBus.getDefault().post(new DiscoveryFinishedEvent(device != null));
    }

    public void onDeviceFound(BluetoothDevice device) {
        Log.d(TAG, "onDeviceFound: ");
        this.device = device;
        stopDiscovery();
    }

    public void onDeviceConnected(List<BluetoothGattService> services) {
        Log.d(TAG, "onDeviceConnected: ");
        status = Aria.STATUS_CONNECTED;

        EventBus.getDefault().post(new ConnectedEvent());

        for (int i = 0; i < services.size(); i++) {
            BluetoothGattService service = services.get(i);
            Log.v(TAG, "+ Service " + service.getUuid());

            if (service.getUuid().equals(CalibrationBleService.CALIBRATION_SERVICE_UUID)) {
                Log.v(TAG, "+ Service Found CALIBRATION_SERVICE_UUID");
                if (cas != null) {
                    cas.removeInitListener(this);
                }

                cas = new CalibrationBleService(context, btGatt, service);
                cas.addInitListener(this);
                btGattCallback.setCalibrationListener(cas);
            } else if (service.getUuid().equals(AriaBleService.ARIA_SERVICE_UUID)) {
                Log.v(TAG, "+ Service Found ARIA_SERVICE_UUID");
                if (ars != null) {
                    ars.removeInitListener(this);
                }

                ars = new AriaBleService(context, btGatt, service);
                ars.addInitListener(this);
                btGattCallback.setArsListener(ars);
            }
        }

        if (cas != null) {
            cas.init();
        } else {
            Log.v(TAG, "Missing services");
        }
    }

    public void onDeviceDisconnected() {
        Log.d(TAG, "onDeviceDisconnected: ");
        status = Aria.STATUS_NONE;
        EventBus.getDefault().post(new DisconnectedEvent());
    }

    public void onCalibrationInit() {
        Log.d(TAG, "onCalibrationInit: ");
        ars.init();
    }

    public void onArsInit() {
        Log.d(TAG, "onArsInit: Aria.STATUS_READY");
        status = Aria.STATUS_READY;
    }
}
