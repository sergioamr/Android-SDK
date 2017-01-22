package com.deus_tech.ariasdk.ble;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.deus_tech.ariasdk.ariaBleService.AriaBleService;
import com.deus_tech.ariasdk.ariaBleService.ArsGattListener;
import com.deus_tech.ariasdk.calibrationBleService.CalibrationBleService;
import com.deus_tech.ariasdk.calibrationBleService.CasGattListener;

import java.util.List;
import java.util.UUID;

public class BluetoothGattCallback extends android.bluetooth.BluetoothGattCallback {
    private static final String TAG = "BluetoothGattCallback";
    private ConnectionGattListener connectionListener;
    private CasGattListener calibrationListener;
    private ArsGattListener arsListener;

    public void setConnectionListener(ConnectionGattListener _connectionListener) {
        connectionListener = _connectionListener;
    }

    public void setCalibrationListener(CasGattListener _calibrationListener) {
        calibrationListener = _calibrationListener;
    }

    public void setArsListener(ArsGattListener _arsListener) {
        arsListener = _arsListener;
    }

    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            gatt.close();
            if (connectionListener != null) {
                connectionListener.onDeviceDisconnected();
            }
        }
    }

    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            List<BluetoothGattService> services = gatt.getServices();
//            List<BluetoothGattService> services = new ArrayList<>();
//	        BluetoothGattService service = gatt.getService(AriaBleService.ARIA_SERVICE_UUID);
//	        if (service != null) {
//		        services.add(service);
//	        }
//	        service = gatt.getService(CalibrationBleService.CALIBRATION_SERVICE_UUID);
//	        if (service != null) {
//		        services.add(service);
//	        }

            if (connectionListener != null) {
                connectionListener.onDeviceConnected(services);
            }
        }
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (calibrationListener == null)
            return;

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Error on Gatt");
            return;
        }

        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        UUID uuid = characteristic.getUuid();

        if (uuid.equals(CalibrationBleService.CALIBRATION_ATTRIBUTE_UUID)) {
            calibrationListener.onCalibrationAttributeRead(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.CALIBRATION_ERROR_UUID)) {
            calibrationListener.onCalibrationDatetimeRead(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.CALIBRATION_MODE_UUID)) {
            calibrationListener.onCalibrationModeRead(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.SETTINGS_COMMAND_UUID)) {
            calibrationListener.onSettingsCommandRead(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.SETTINGS_DATA_UUID)) {
            calibrationListener.onSettingsDataRead(value);
        }

        if (uuid.equals(CalibrationBleService.GESTURE_STATUS_UUID)) {
            calibrationListener.onGestureStatusRead(value);
            return;
        }

        if (uuid.equals(AriaBleService.ARIA_BATTERY_UUID) && arsListener != null) {
            arsListener.onBatteryRead(value);
            return;
        }
    }

    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (calibrationListener == null)
            return;

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Error writing on Gatt");
            return;
        }

        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        UUID uuid = characteristic.getUuid();

        if (uuid.equals(CalibrationBleService.CALIBRATION_ERROR_UUID)) {
            calibrationListener.onCalibrationDatetimeWritten(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.CALIBRATION_MODE_UUID)) {
            calibrationListener.onCalibrationModeWritten(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.SETTINGS_COMMAND_UUID)) {
            calibrationListener.onSettingsCommandWritten(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.SETTINGS_DATA_UUID)) {
            calibrationListener.onSettingsDataWritten(value);
            return;
        }

        if (uuid.equals(CalibrationBleService.GESTURE_STATUS_UUID)) {
            calibrationListener.onGestureStatusWritten(value);
            return;
        }
    }


    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
    }

    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        UUID uuid = characteristic.getUuid();

        if (calibrationListener != null) {
            if (uuid.equals(CalibrationBleService.CALIBRATION_ATTRIBUTE_UUID)) {
                calibrationListener.onCalibrationAttributeChanged(value);
                return;
            }

            if (uuid.equals(CalibrationBleService.GESTURE_STATUS_UUID)) {
                calibrationListener.onGestureStatusNotifyChanged(value);
                return;
            }
        }

        if (arsListener != null) {
            if (uuid.equals(AriaBleService.ARIA_GESTURE_UUID)) {
                arsListener.onGestureChanged(value);
                return;
            }

            if (uuid.equals(AriaBleService.ARIA_BATTERY_UUID)) {
                arsListener.onBatteryChanged(value);
                return;
            }

            if (uuid.equals(CalibrationBleService.CALIBRATION_MODE_UUID)) {
                calibrationListener.onCalibrationModeChanged(value);
                return;
            }
        }
    }

    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    }//onDescriptorRead


    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        if (status == BluetoothGatt.GATT_SUCCESS) {

            if (descriptor.getCharacteristic().getUuid().equals(CalibrationBleService.CALIBRATION_ATTRIBUTE_UUID)) {

                if (descriptor.getValue().equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                    calibrationListener.onCalibrationAttributeNotifyEnabled();

                } else if (descriptor.getValue().equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {

                    calibrationListener.onCalibrationAttributeNotifyDisabled();

                }

            } else if (descriptor.getCharacteristic().getUuid().equals(CalibrationBleService.GESTURE_STATUS_UUID)) {

                if (descriptor.getValue().equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                    calibrationListener.onGestureStatusNotifyEnabled();

                } else if (descriptor.getValue().equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {

                    calibrationListener.onGestureStatusNotifyDisabled();

                }

            } else if (descriptor.getCharacteristic().getUuid().equals(AriaBleService.ARIA_GESTURE_UUID) && arsListener != null) {

                if (descriptor.getUuid().equals(AriaBleService.CCCD_UUID)) {

                    if (descriptor.getValue().equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                        arsListener.onGestureNotifyEnabled();

                    } else if (descriptor.getValue().equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {

                        arsListener.onGestureNotifyDisabled();

                    }

                }

            } else if (descriptor.getCharacteristic().getUuid().equals(AriaBleService.ARIA_BATTERY_UUID) && arsListener != null) {

                if (descriptor.getUuid().equals(AriaBleService.CCCD_UUID)) {

                    if (descriptor.getValue().equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {

                        arsListener.onBatteryNotifyEnabled();

                    } else if (descriptor.getValue().equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {

                        arsListener.onBatteryNotifyDisabled();

                    }

                }

            }

        }

    }//onDescriptorWrite

    //mtu = maximum transmission unit
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
    }//onMtuChanged


    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    }//onReadRemoteRssi


}//WeesBluetoothGattCallback