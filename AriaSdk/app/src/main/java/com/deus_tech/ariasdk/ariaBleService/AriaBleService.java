package com.deus_tech.ariasdk.ariaBleService;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.deus_tech.aria.ArsEvents.BatteryEvent;
import com.deus_tech.aria.ArsEvents.GestureEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.UUID;

public class AriaBleService implements ArsGattListener{
    private final String TAG="AriaBleService";


    //UUID-s
    public final static UUID ARIA_SERVICE_UUID = UUID.fromString("e95d0000-b0de-1051-43b0-c7ab0ceffe1a");
    public final static UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public final static UUID ARIA_GESTURE_UUID = UUID.fromString("e95d0001-b0de-1051-43b0-c7ab0ceffe1a");
    public final static UUID ARIA_BATTERY_UUID = UUID.fromString("e95d0002-b0de-1051-43b0-c7ab0ceffe1a");

    //gestures
    public final static int GESTURE_ENTER= 1; //right - 4F
    public final static int GESTURE_HOME= 2; //enter - 28
    public final static int GESTURE_UP= 3; //down - 51
    public final static int GESTURE_DOWN= 4; //up - 52
    public final static int GESTURE_BACK= 5; //left - 50

    private Context context;

    //bluetooth-
    private BluetoothGatt btGatt;
    private BluetoothGattService btGattService;

    //characteristics
    private BluetoothGattCharacteristic ariaGestureChar;
    private BluetoothGattCharacteristic ariaBatteryChar;

    //listeners
    private ArrayList<ArsInitListener> initListeners;

    public AriaBleService(Context _context, BluetoothGatt _btGatt, BluetoothGattService _btGattService){
        Log.d(TAG, "AriaBleService: ");
        context = _context;

        initListeners = new ArrayList<ArsInitListener>();
        btGatt = _btGatt;
        btGattService = _btGattService;
    }

    public void addInitListener(ArsInitListener _listener){
        Log.d(TAG, "addInitListener: ");
        initListeners.add(_listener);
    }

    public void removeInitListener(ArsInitListener _listener){
        Log.d(TAG, "removeInitListener: ");
        initListeners.remove(_listener);
    }

    public void init(){
        Log.d(TAG, "init: ");

        ariaGestureChar = btGattService.getCharacteristic(AriaBleService.ARIA_GESTURE_UUID);
        ariaBatteryChar = btGattService.getCharacteristic(AriaBleService.ARIA_BATTERY_UUID);

        if(ariaGestureChar != null){
            enableGestureNotify(true);
        } else {
            Log.d(TAG, "Missing gesture ");
        }
    }

    public void readBattery(){
        Log.d(TAG, "readBattery: ");
        btGatt.readCharacteristic(ariaBatteryChar);
    }

    //private
    private void enableGestureNotify(boolean _isEnabled){
        Log.d(TAG, "enableGestureNotify: ");
        btGatt.setCharacteristicNotification(ariaGestureChar, true);

        BluetoothGattDescriptor cccd = ariaGestureChar.getDescriptor(AriaBleService.CCCD_UUID);

        if(_isEnabled){
            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }else{
            cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        btGatt.writeDescriptor(cccd);
    }

    private void enableBatteryNotify(boolean _isEnabled){
        Log.d(TAG, "enableBatteryNotify: ");
        /*
        // This doesn't exist anymore and breaks our configuration

        btGatt.setCharacteristicNotification(ariaBatteryChar, true);

        BluetoothGattDescriptor cccd = ariaBatteryChar.getDescriptor(AriaBleService.CCCD_UUID);

        if(_isEnabled){
            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }else{
            cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        btGatt.writeDescriptor(cccd);
        */
    }

    //ArsGattListener - gesture
    public void onGestureNotifyEnabled(){
        Log.d(TAG, "onGestureNotifyEnabled: è attivo");
        if(ariaBatteryChar != null){
            enableBatteryNotify(true);
        }
    }

    public void onGestureNotifyDisabled(){
        Log.d(TAG, "onGestureNotifyDisabled: ");
    }

    public void onGestureChanged(int _value){
        Log.d(TAG, "onGestureChanged: ");
        if (_value == 0x4F) _value = AriaBleService.GESTURE_ENTER;
        else if (_value == 0x50) _value = AriaBleService.GESTURE_HOME;
        else if (_value == 0x52) _value = AriaBleService.GESTURE_UP;
        else if (_value == 0x51) _value = AriaBleService.GESTURE_DOWN;
        else if (_value == 0x28) _value = AriaBleService.GESTURE_BACK;
        EventBus.getDefault().post(new GestureEvent(_value));
    }

    //ArsGattListener - battery
    public void onBatteryRead(int _value){
        Log.d(TAG, "onBatteryRead: ");
        EventBus.getDefault().post(new BatteryEvent(_value));
    }

    public void onBatteryNotifyEnabled(){
        Log.d(TAG, "onBatteryNotifyEnabled: ");
        for(int i=0 ; i<initListeners.size() ; i++){
            initListeners.get(i).onArsInit();
        }
    }

    public void onBatteryNotifyDisabled(){
        Log.d(TAG, "onBatteryNotifyDisabled: ");
    }

    public void onBatteryChanged(int _value){
        Log.d(TAG, "onBatteryChanged: ");
        EventBus.getDefault().post(new BatteryEvent(_value));
    }
}

