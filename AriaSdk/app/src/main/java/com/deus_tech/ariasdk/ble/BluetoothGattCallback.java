package com.deus_tech.ariasdk.ble;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.deus_tech.ariasdk.ariaBleService.ArsGattListener;
import com.deus_tech.ariasdk.nusBleService.NusBleService;
import com.deus_tech.ariasdk.nusBleService.NusGattListener;

import java.util.List;
import java.util.UUID;

public class BluetoothGattCallback extends android.bluetooth.BluetoothGattCallback {
    private static final String TAG = "BluetoothGattCallback";
    private ConnectionGattListener connectionListener;
    private ArsGattListener arsListener;
    private NusGattListener nusListener;

    public void setConnectionListener(ConnectionGattListener _connectionListener) {
        connectionListener = _connectionListener;
    }

    public void setNusListener(NusGattListener _nusListener) {
        nusListener = _nusListener;
    }

    public void setArsListener(ArsGattListener _arsListener) {
        arsListener = _arsListener;
    }

    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            BluetoothDevice device = gatt.getDevice();
            Log.d(TAG, "-------------------------------------------------------");
            Log.d(TAG, "Connected to (" + device.getName() + ") Address (" + device.getAddress() + ")");
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            gatt.close();
            if (connectionListener != null) {
                connectionListener.onDeviceDisconnected();
            }
        }
    }

    public void getSupportedGattServices(BluetoothGatt gatt) {
        BluetoothDevice device = gatt.getDevice();
        List<BluetoothGattService> services = gatt.getServices();

        if (connectionListener != null)
            connectionListener.onDeviceConnected(services);
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Discovered services");
            getSupportedGattServices(gatt);
        }
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Error on Gatt");
            return;
        }

        UUID uuid = characteristic.getUuid();
        if (uuid.equals(NusBleService.TX_CHAR_UUID)) {
            if (nusListener != null)
                nusListener.onDataArrived(characteristic.getValue());
            return;
        }

        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        Log.v(TAG, "Read [" + uuid + "]=" + value);
    }

    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Error writing on Gatt");
            return;
        }

        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        UUID uuid = characteristic.getUuid();
    }

    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
    }

    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        UUID uuid = characteristic.getUuid();

        if (nusListener != null) {
            if (uuid.equals(NusBleService.TX_CHAR_UUID)) {
                Log.d(TAG, characteristic.getValue().toString());
                nusListener.onDataArrived(characteristic.getValue());
                return;
            } else {
                Log.v(TAG, "Changed [" + uuid + "]=" + value);
            }
        }
    }

    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    }

    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        Log.v(TAG, "onDescriptorWrite");
        if (status == BluetoothGatt.GATT_SUCCESS) {
        }
    }

    //mtu = maximum transmission unit
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
    }

    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    }
}