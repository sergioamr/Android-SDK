package com.deus_tech.ariasdk.nusBleService;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.deus_tech.aria.ArsEvents.GestureEvent;
import com.deus_tech.aria.CasEvents.GestureStatusEvent;
import com.deus_tech.aria.CasEvents.OnCalibrationWritten;
import com.deus_tech.ariasdk.Aria;
import com.deus_tech.ariasdk.R;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.LinkedList;
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
    public final static char COMMAND_CAS_GESTURE_FEEDBACK = 'F';
    public final static char COMMAND_CAS_IS_CALIBRATED = 'C';
    public final static char COMMAND_OK = 'O';

    public final static char COMMAND_CAS_WRITE = 'W';
    public final static char COMMAND_SETTING = 'T';
    public final static char COMMAND_SETTING_DATA = 'D';
    public final static char COMMAND_PING = 'P';
    public final static char COMMAND_DEBUG = 'd';
    public final static char COMMAND_VERSION = 'V';

    public final static int VERSION_COMPILATION = 0;
    public final static int VERSION_REVISION = 1;

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

    private LinkedList<byte[]> messages;

    public NusBleService(Context _context, BluetoothGatt _btGatt, BluetoothGattService _btGattService) {
        Log.d(TAG, "NusBleService: ");
        context = _context;

        initListeners = new ArrayList<NusInitListener>();
        casListeners = new ArrayList<CasListener>();
        messages = new LinkedList<byte[]>();

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
        messages.clear();
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

    public void dumpPendingMessages() {
        Log.d(TAG, "\n\n ------------------------ " + messages.size());
        for (int t = 0; t < messages.size(); t++) {
            byte[] b = messages.get(t);
            Log.d(TAG, t + " [" + new String(b) + "]");
        }
    }

    public void writeRXCharacteristic(byte[] value) {
        if (btGattService == null) {
            Log.v(TAG, "Rx service not found!");
            return;
        }

        String str;

        if (messages.size() > 0) {
            if (value != null) {
                str = new String(value);
                Log.d(TAG, "TX! [" + str + "]");
                messages.addLast(value);
            }

            value = messages.removeFirst();
        }

        BluetoothGattCharacteristic RxChar = btGattService.getCharacteristic(RX_CHAR_UUID);
        if (RxChar == null) {
            Log.v(TAG, "Rx charateristic not found!");
            return;
        }
        RxChar.setValue(value);
        boolean status = btGatt.writeCharacteristic(RxChar);

        str = new String(value);
        if (status) {
            Log.d(TAG, "TX: [" + str + "]");
            return;
        }

        messages.addFirst(value);

        Log.d(TAG, "TX! [" + str + "] Pending = " + messages.size());
    }

    @Override
    public void onWriteRXCharacteristic() {
        if (messages.size() > 0) {
            writeRXCharacteristic(null);
        }
    }

    public void onQueryForCalibration() {
        Log.v(TAG, "-------------- IS CALIBRATED -------------------");
        writeSingleCommand(COMMAND_CAS_IS_CALIBRATED, 0);
    }

    public void onDeviceRespondedToConnection() {
        for (int i = 0; i < initListeners.size(); i++) {
            initListeners.get(i).onNusInit();
        }

        Log.v(TAG, "------------- REQUEST VERSION ------------------");
        writeSingleCommand(COMMAND_VERSION, VERSION_COMPILATION);
        writeSingleCommand(COMMAND_VERSION, VERSION_REVISION);
    }

    @Override
    public void onNusNotifyEnabled() {
        Log.v(TAG, "------------- onNusNotifyEnabled ------------------");
        //onDeviceRespondedToConnection();
    }

    @Override
    public void onNusNotifyDisabled() {
        Log.d(TAG, "onNusNotifyDisabled");
    }

    public void onGestureChanged(int value) {
        int _value = value;
        switch (_value) {
            case 1:
                _value = Aria.GESTURE_ENTER;
                break;
            case 2:
                _value = Aria.GESTURE_HOME;
                break;
            case 3:
                _value = Aria.GESTURE_UP;
                break;
            case 4:
                _value = Aria.GESTURE_DOWN;
                break;
            case 5:
                _value = Aria.GESTURE_BACK;
                break;
        }
        Log.d(TAG, "onGestureChanged: " + value + " = " + _value);
        EventBus.getDefault().post(new GestureEvent(_value));
    }

    @Override
    public void onDataArrived(byte[] buf_str) {
        String str = new String(buf_str);
        Log.v(TAG, "RX: [" + str + "]");
        onCommandArrived(buf_str);
    }

    //---------- Write commands -----------------------------------------------------

    public void writeSingleCommand(char command, int value) {
        byte buf[] = new byte[4];
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
            return;
        }
    }

    public void onGestureStatusFeedback(int _value) {
        switch (_value) {
            case GESTURE_STATUS_NONE:
                Log.v(TAG, "GESTURE_STATUS_NONE");
                break;
            case GESTURE_STATUS_STARTED:
                Log.v(TAG, "GESTURE_STATUS_STARTED");
                break;
            case GESTURE_STATUS_RECORDING:
                Log.v(TAG, "GESTURE_STATUS_RECORDING");
                break;
            case GESTURE_STATUS_OK:
                Log.v(TAG, "GESTURE_STATUS_OK");
                break;
            case GESTURE_STATUS_ERROR1:
                Log.v(TAG, "GESTURE_STATUS_ERROR1");
                break;
            case GESTURE_STATUS_ERROR2:
                Log.v(TAG, "GESTURE_STATUS_ERROR2");
                break;
            case GESTURE_STATUS_ERROR3:
                Log.v(TAG, "GESTURE_STATUS_ERROR3");
                break;
            case GESTURE_STATUS_OKREPETITION:
                Log.v(TAG, "GESTURE_STATUS_OKREPETITION");
                break;
            case GESTURE_STATUS_OKGESTURE:
                Log.v(TAG, "GESTURE_STATUS_OKGESTURE");
                break;
            case GESTURE_STATUS_OKCALIBRATION:
                Log.v(TAG, "GESTURE_STATUS_OKCALIBRATION");
                break;
            case GESTURE_STATUS_OKCAMP:
                Log.v(TAG, "GESTURE_STATUS_OKCAMP");
                break;
            case GESTURE_STATUS_OKCAD:
                Log.v(TAG, "GESTURE_STATUS_OKCAD");
                break;
            case GESTURE_STATUS_OKCSIM:
                Log.v(TAG, "GESTURE_STATUS_OKCSIM");
                break;
        }
    }

    public void onCommandArrived(byte[] buf_str) {
        // Found single value command
        if (buf_str[0] == COMMAND_START && buf_str[3] == COMMAND_END) {
            int cmd = buf_str[1];
            int value = buf_str[2];

            // If it is a number we like it in digital form.
            if (value >= '0' && value <= '9') {
                value -= '0';
            }

            switch (cmd) {
                case COMMAND_GESTURE:
                    onGestureChanged(value);
                    return;
                case COMMAND_CAS_GESTURE_STATUS:
                case COMMAND_CAS_GESTURE_FEEDBACK:
                    onGestureStatusFeedback(value);
                    onGestureStatusWritten(value);
                    EventBus.getDefault().post(new GestureStatusEvent(value));
                    return;
                case COMMAND_OK:
                    if (value == 'K') {
                        Log.d(TAG, "OK FOUND!");
                        onDeviceRespondedToConnection();
                    }
                    break;
            }
            return;
        }

        // Value written correctly
        if (buf_str[0] == 'A' && buf_str[1] == 'C' && buf_str[2] == 'K') {
            int cmd = buf_str[4];
            int value = buf_str[5] - '0';

            switch (cmd) {
                case COMMAND_CAS_IS_CALIBRATED:
                    if (value == 0) {
                        Log.v(TAG, "Aria is not calibrated!");
                        EventBus.getDefault().post(new AriaNotCalibrated());
                    } else {
                        writeStatus_Exec();
                    }
                    break;
                case COMMAND_CAS_WRITE:
                    if (value == STATUS_CALIB) {
                        onCalibrationModeWritten(value);
                    }
                    return;
                case COMMAND_CAS_GESTURE_STATUS:
                    onGestureStatusFeedback(value);
                    onGestureStatusWritten(value);
                    return;
                default:
                    break;
            }

            return;
        }

        //EventBus.getDefault().post(new CharacterEvent(_value));
    }

    // ARIA MESSAGES
    public class AriaNotCalibrated {
    }
}
