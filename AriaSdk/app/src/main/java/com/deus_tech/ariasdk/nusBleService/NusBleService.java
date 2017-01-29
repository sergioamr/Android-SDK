package com.deus_tech.ariasdk.nusBleService;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.deus_tech.aria.ArsEvents.GestureEvent;
import com.deus_tech.aria.CasEvents.OnCalibrationWritten;
import com.deus_tech.ariasdk.R;
import com.deus_tech.ariasdk.ariaBleService.AriaBleService;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.UUID;

import static android.os.Debug.isDebuggerConnected;

public class NusBleService implements NusGattListener {
    private final String TAG = "NusBleService";

    //UUID-s
    public final static UUID NUS_CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb");
    public static final UUID TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    //settings command values

    public final static int SET_NUMBER_GESTURE = 0xC0;
    public final static int SET_NUMBER_REPETITION = 0xC1;

    //calibration attribute values

    public final static int CALIBRATION_IS_PRESENT = 1;
    public final static int CALIBRATION_IS_NOT_PRESENT = 0;

    //calibration mode values

    public final static int CALIBRATION_MODE_NONE = 0;
    public final static int STATUS_CALIB = 1;
    public final static int STATUS_EXEC = 2;
    public final static int STATUS_SLEEP = 3;
    public final static int STATUS_IDLE = 0;
    public final static int STATUS_PRECALIB_AMP = 4;
    public final static int STATUS_PRECALIB_CAD = 5;
    public final static int STATUS_PRECALIB_SIM = 6;
    public final static int STATUS_PRECALIB_DEB = 7;

    //gesture status values
    public final static int GESTURE_STATUS_NONE = 0;
    public final static int GESTURE_STATUS_STARTED = 1;
    public final static int GESTURE_STATUS_RECORDING = 2;
    public final static int GESTURE_STATUS_OK = 3;
    public final static int GESTURE_STATUS_ERROR1 = 4;
    public final static int GESTURE_STATUS_ERROR2 = 5;
    public final static int GESTURE_STATUS_ERROR3 = 6;
    public final static int GESTURE_STATUS_OKREPETITION = 7;
    public final static int GESTURE_STATUS_OKGESTURE = 8;
    public final static int GESTURE_STATUS_OKCALIBRATION = 9;
    public final static int GESTURE_STATUS_OKCAMP = 10;
    public final static int GESTURE_STATUS_OKCAD = 11;
    public final static int GESTURE_STATUS_OKCSIM = 12;

    public final static int OLD_PROTOCOL = 0;
    public final static int NEW_PROTOCOL = 1;

    public final static char COMMAND_START = '{';
    public final static char COMMAND_END = '}';

    public final static char COMMAND_GESTURE = 'G';
    public final static char COMMAND_CAS_GESTURE_STATUS = 'S';
    public final static char COMMAND_CAS_ERROR = 'E';
    public final static char COMMAND_CAS_GESTURE_QUALITY = 'Q';

    public final static char COMMAND_CAS_WRITE = 'W';
    public final static char COMMAND_SETTING = 'T';
    public final static char COMMAND_SETTING_DATA = 'D';
    public final static char COMMAND_PING = 'P';
    public final static char COMMAND_DEBUG = 'd';

    private Context context;

    //bluetooth
    private BluetoothGatt btGatt;
    private BluetoothGattService btGattService;

    //characteristics
    private BluetoothGattCharacteristic uartRxChar;

    //listeners
    private ArrayList<NusInitListener> initListeners;
    private ArrayList<CasListener> casListeners;

    //calibration
    private int gestureProtocol;
    private int numGestures;
    private int numRepetitions;
    private int calibrationStatus;
    private int gestureStatus;
    private int currentGestureIndex;
    private int currentGestureIteration;
    private int settingsCommand;
    private int settingsData;

    public NusBleService(Context _context, BluetoothGatt _btGatt, BluetoothGattService _btGattService) {
        Log.d(TAG, "NusBleService: ");
        context = _context;

        initListeners = new ArrayList<NusInitListener>();
        casListeners = new ArrayList<CasListener>();

        btGatt = _btGatt;
        btGattService = _btGattService;

        numGestures = context.getResources().getInteger(R.integer.calibration_gestures);
        numRepetitions = context.getResources().getInteger(R.integer.calibration_iterations);

        enableTXNotification();
    }

    public void addCasListener(CasListener _listener) {
        casListeners.clear();
        casListeners.add(_listener);
    }

    public void removeCasListener(CasListener _listener) {
        //Log.d(TAG, "removeCasListener: ");
        casListeners.remove(_listener);
    }

    public void addInitListener(NusInitListener _listener) {
        //Log.d(TAG, "addInitListener: ");
        initListeners.add(_listener);
    }

    public void removeInitListener(NusInitListener _listener) {
        //Log.d(TAG, "removeInitListener: ");
        initListeners.remove(_listener);
    }

    public void init() {
        Log.d(TAG, "init: ");
    }

    /**
     * Enable Notification on TX characteristic
     *
     * @return
     */
    public void enableTXNotification() {
        Log.v(TAG, "BluetoothGattService RX_SERVICE_UUID");
        if (btGattService == null) {
            Log.v(TAG, "Rx service not found!");
            return;
        }

        BluetoothGattCharacteristic TxChar = btGattService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            Log.v(TAG, "Tx charateristic not found!");
            return;
        }
        btGatt.setCharacteristicNotification(TxChar, true);

        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(NUS_CCCD_UUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        btGatt.writeDescriptor(descriptor);
        Log.v(TAG, "setCharacteristicNotification TxChar");
    }

    public void writeRXCharacteristic(byte[] value) {
        if (btGattService == null) {
            Log.v(TAG, "Rx service not found!");
            return;
        }

        BluetoothGattCharacteristic RxChar = btGattService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            Log.v(TAG, "Rx charateristic not found!");
            return;
        }
        RxChar.setValue(value);
        boolean status = btGatt.writeCharacteristic(RxChar);

        String str = new String(value);
        if (!status)
            Log.d(TAG, "Fail TX! [" + str + "]");
        else
            Log.d(TAG, "Sent TX: [" + str + "]");
    }

    @Override
    public void onNusNotifyEnabled() {
        writeSingleCommand(COMMAND_PING, 1);
    }

    @Override
    public void onNusNotifyDisabled() {

    }

    public void onGestureChanged(int _value) {
        Log.d(TAG, "onGestureChanged: ");

        switch (_value) {
            case 0x4F:
                _value = AriaBleService.GESTURE_ENTER;
                break;
            case 0x50:
                _value = AriaBleService.GESTURE_HOME;
                break;
            case 0x52:
                _value = AriaBleService.GESTURE_UP;
                break;
            case 0x51:
                _value = AriaBleService.GESTURE_DOWN;
                break;
            case 0x28:
                _value = AriaBleService.GESTURE_BACK;
                break;
        }

        EventBus.getDefault().post(new GestureEvent(_value));
    }

    @Override
    public void onDataArrived(byte[] buf_str) {
        String str = new String(buf_str);
        Log.v(TAG, "Data RX: [" + str + "]");
        onCommandArrived(buf_str);
    }

    //---------- Write commands -----------------------------------------------------
    public byte buf[] = new byte[4];

    public void writeSingleCommand(char command, int value) {
        buf[0] = COMMAND_START;
        buf[1] = (byte) command;

        if (value < '0')
            value += '0';

        buf[2] = (byte) value;
        buf[3] = COMMAND_END;
        writeRXCharacteristic(buf);
    }

    public void writeStatus_Ping() {
        Log.v(TAG, "writeStatus_Ping");
        writeSingleCommand(COMMAND_PING, 1);
    }

    public void writeStatus_Sleep() {
        Log.v(TAG, "writeStatus_Sleep");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_SLEEP);
    }

    public void writeStatus_Exec() {
        Log.v(TAG, "writeStatus_Exec");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_EXEC);
    }

    public void writeStatus_Calib() {
        Log.v(TAG, "writeStatus_Calib");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_CALIB);
    }

    public void writeGestureStatus(int status) {
        Log.v(TAG, "writeGestureStatus " + status);
        writeSingleCommand(COMMAND_CAS_GESTURE_STATUS, status);
    }

    public void writeSettingsCommand(int value) {
        Log.v(TAG, "writeSettingsCommand " + value);
        switch (value) {
            case SET_NUMBER_GESTURE:
                writeSingleCommand(COMMAND_SETTING_DATA, numGestures);
                break;
            case SET_NUMBER_REPETITION:
                writeSingleCommand(COMMAND_SETTING_DATA, numRepetitions);
                break;
        }
        writeSingleCommand(COMMAND_SETTING, value);
    }

    public void writeGestureStatusStart() {
        Log.v(TAG, "writeGestureStatusStart");
        writeSingleCommand(COMMAND_CAS_GESTURE_STATUS, GESTURE_STATUS_STARTED);
    }

    public void writeStatus_Pre_Amp() {
        Log.v(TAG, "writeStatus_Pre_Amp");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_PRECALIB_AMP);
    }

    public void writeStatus_Pre_Deb() {
        Log.v(TAG, "writeStatus_Pre_Deb");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_PRECALIB_DEB);
    }

    public void writeStatus_Pre_Cad() {
        Log.v(TAG, "writeStatus_Pre_Cad");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_PRECALIB_CAD);
    }

    public void writeStatus_Pre_Sim() {
        Log.v(TAG, "writeStatus_Pre_Sim");
        writeSingleCommand(COMMAND_CAS_WRITE, STATUS_PRECALIB_SIM);
    }

    public void readCalibrationAttribute() {
        Log.v(TAG, "readCalibrationAttribute");
    }

    public void readCalibrationError() {
        Log.d(TAG, "readCalibrationError: ");
        //btGatt.readCharacteristic(calibrationErrorChar);
    }

    private void readCalibrationMode() {
        Log.d(TAG, "readCalibrationMode: ");
        //btGatt.readCharacteristic(calibrationModeChar);
    }

    //----------------------------------------------------------------------------

    public void setGesture(int value) {
        Log.d(TAG, "setGesture: ");
        setGesturesNumber(value);
        writeSettingsCommand(SET_NUMBER_GESTURE);
    }

    public void setRepetition(Integer value) {
        Log.d(TAG, "setRepetition: " + value.toString());
        setIterationsNumber(value);
        writeSettingsCommand(SET_NUMBER_REPETITION);
    }

    public int getGesturesNumber() {
        Log.d(TAG, "getGesturesNumber: " + Integer.toString(numGestures));
        return numGestures;
    }

    public void setGesturesNumber(int val) {
        Log.d(TAG, "setGesturesNumber: " + Integer.toString(val));
        numGestures = val;
    }

    public int getIterationsNumber() {
        Log.d(TAG, "getIterationsNumber: " + Integer.toString(numRepetitions));
        return numRepetitions;
    }

    public void setIterationsNumber(int val) {
        Log.d(TAG, "setIterationsNumber: " + Integer.toString(val));
        numRepetitions = val;
    }

    public void startCalibration() {
        Log.d(TAG, "startCalibration: ");
        calibrationStatus = NusBleService.CALIBRATION_MODE_NONE;
        gestureStatus = NusBleService.GESTURE_STATUS_NONE;
        currentGestureIndex = 1;
        currentGestureIteration = 1;
        writeStatus_Calib();

        if (isDebuggerConnected()) {
            Log.d(TAG, "------- DEBUG ACTIVE --------");
            writeSingleCommand(COMMAND_DEBUG, 1);
        }
    }

    public void nextCalibrationStep() {
        Log.d(TAG, "nextCalibrationStep: ");
        writeGestureStatus(GESTURE_STATUS_STARTED);
    }

    public void onCalibrationModeWritten(int _value) {
        Log.d(TAG, "onCalibrationModeWritten " + _value);
        //This info is only used so far for the calibration therefore I move it to the calibration status only
        calibrationStatus = _value;
        if (_value == NusBleService.STATUS_CALIB) {
            EventBus.getDefault().post(new OnCalibrationWritten(_value));
            Log.d(TAG, "onCalibrationModeWritten: calibration mode scritto correttamente " + _value);
            for (int i = 0; i < casListeners.size(); i++) {
                casListeners.get(i).onCalibrationStarted();
            }
        }

        if (_value == NusBleService.STATUS_EXEC) {
            Log.d(TAG, "onCalibrationModeWritten: execution mode scritto correttamente " + _value);
        }
    }

    public void repeatCalibrationStep() {
        Log.d(TAG, "repeatCalibrationStep: EMPTY ");
    }

    public void stopCalibration() {
        Log.d(TAG, "stopCalibration: ");
        //TODO: implement the control on the Calibration_Attribute
        writeStatus_Exec();
    }

    public int getCalibrationStatus() {
        Log.d(TAG, "getCalibrationStatus: ");
        return calibrationStatus;
    }

    public int getGestureStatus() {
        Log.d(TAG, "getGestureStatus: ");
        return gestureStatus;
    }

    public int getGestureIndex() {
        Log.d(TAG, "getGestureIndex: ");
        return currentGestureIndex;
    }

    public int getGestureIteration() {
        Log.d(TAG, "getGestureIteration: ");
        return currentGestureIteration;
    }

    public void onGestureStatusWritten(int _value) {
        Log.d(TAG, "onGestureStatusWritten: ");
        if (_value == NusBleService.GESTURE_STATUS_STARTED) {
            gestureStatus = NusBleService.GESTURE_STATUS_STARTED;
            for (int i = 0; i < casListeners.size(); i++) {
                casListeners.get(i).onCalibrationStepStarted(currentGestureIndex, currentGestureIteration);
            }
        }
    }

    public void onCommandArrived(byte[] buf_str) {
        // Found single value command
        if (buf_str[0] == COMMAND_START && buf_str[3] == COMMAND_END) {
            int cmd = buf_str[1];
            int value = buf_str[2];
            switch (cmd) {
                case COMMAND_GESTURE:
                    onGestureChanged(value);
                    return;
            }
            return;
        }

        // Value written correctly
        if (buf_str[0] == 'A' && buf_str[1] == 'C' && buf_str[2] == 'K') {
            int cmd = buf_str[4];
            int value = buf_str[5] - '0';

            switch (cmd) {
                case COMMAND_CAS_WRITE:
                    if (value == STATUS_CALIB) {
                        onCalibrationModeWritten(value);
                    }
                return;
                default:
                    break;
            }

            return;
        }

        //EventBus.getDefault().post(new CharacterEvent(_value));
    }
}
