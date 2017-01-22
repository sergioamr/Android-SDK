package com.deus_tech.ariasdk.wearConnectivity;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.deus_tech.aria.SmartphoneEvents.DashboardEvent;
import com.deus_tech.aria.SmartphoneEvents.LearningEvent;
import com.deus_tech.aria.SmartphoneEvents.MusicEvent;
import com.deus_tech.aria.SmartphoneEvents.RouterEvent;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class SmartwatchManager implements DataApi.DataListener, MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<DataItemBuffer>{ //SensorEventListener
	private String TAG="SmartwatchManager";

	//paths
	public final static String ROUTER_PATH = "/router";
	public final static String ROUTER_VIEW = "/view";


	public final static int ROUTER_VIEW_INTRO = 1;
	public final static int ROUTER_VIEW_DASHBOARD = 2;
	public final static int ROUTER_VIEW_GOPRO = 3;
	public final static int ROUTER_VIEW_MUSIC = 4;
	public final static int ROUTER_VIEW_START_CALIBRATION = 5;
	public final static int ROUTER_VIEW_CALIBRATION = 6;
	public final static int ROUTER_VIEW_END_CALIBRATION = 7;
	public final static int ROUTER_VIEW_TEST = 8;
	public static final int ROUTER_VIEW_IFTTT = 9;
	public static final int ROUTER_VIEW_NOTIFICATION = 10;
	public final static int ROUTER_VIEW_DISCONNECT = 11;
	public final static int ROUTER_VIEW_FIRSTINTRO = 12;
	public final static int ROUTER_VIEW_PRE_CAL_MENU2 = 13;
	public final static int ROUTER_VIEW_PRE_CAL_AMP = 14;
	public final static int ROUTER_VIEW_PRE_CAL_CAD = 15;
	public final static int ROUTER_VIEW_PRE_CAL_SIM = 16;
	public final static int ROUTER_VIEW_PRE_CAL_MENU1 = 17;
	public final static int ROUTER_VIEW_STOPWATCH = 18;
	public final static int ROUTER_VIEW_CALIB_SIMPLE = 19;
	public final static int ROUTER_VIEW_PRE_CAL_DEB = 20;
	public final static int ROUTER_VIEW_PIANO = 21;
	public final static int ROUTER_VIEW_GAMES = 22;
	public final static int ROUTER_VIEW_ARIA_APPS = 23;
	public final static int ROUTER_VIEW_NON_ARIA_APPS =24;
	public final static int ROUTER_VIEW_PSETTINGS=25;
	public final static int ROUTER_VIEW_LEARNING=26;


	public final static String LEARNING_STATUS="learning_status";
	public final static String PHONE_MESSAGE_PATH = "/phone";
	public final static String PHONE_MESSAGE_PATH_NO_UI = "/phoneNo";
	public final static String PHONE_START_LEARNING = "PhoneStartLearning";
	public final static String PHONE_MESSAGE_MUSIC="/music";
	public final static String PHONE_PLAY="play";
	public final static String PHONE_NEXT="next";
	public final static String PHONE_PREV="prev";

	public final static String DASHBOARD_PATH = "/dashboard";
	public final static String DASHBOARD_MENU_ITEM = "menuItem";

	public final static String GOPRO_PATH = "/gopro";
	public final static String GOPRO_STATUS = "status";
	public final static int GOPRO_STATUS_SEARCHING = 1;
	public final static int GOPRO_STATUS_NOT_FOUND = 2;
	public final static int GOPRO_STATUS_CONNECTED = 3;
	public final static int GOPRO_STATUS_READY = 4;

	//path-aria messages
	public final static String ARIA_MESSAGE_PATH = "/aria";
	public final static String ARIA_MESSAGE_HOME = "home";
	public final static String ARIA_MESSAGE_ENTER = "enter";
	public final static String ARIA_MESSAGE_BACK = "back";
	public final static String ARIA_MESSAGE_UP = "up";
	public final static String ARIA_MESSAGE_DOWN = "down";

	public final static String ARIA_UI_PATH = "/ui";
	public final static String ARIA_UI_ACTIVE = "active";
	public final static String ARIA_UI_INACTIVE = "inactive";

	public final static String ARIA_NOTIFICATION_PATH_ADD = "/notification_add";
	public final static String ARIA_NOTIFICATION_PATH_REMOVE = "/notification_remove";
	public final static String ARIA_NOTIFICATION_PATH_FIRE = "/notification_fire";
	public final static String ARIA_NOTIFICATION_TITLE = "title";
	public final static String ARIA_NOTIFICATION_TEXT = "text";
	public final static String ARIA_NOTIFICATION_ID = "id";
	public final static String ARIA_NOTIFICATION_ICON = "icon";

	public final static String ARIA_IFTTT_PATH = "/ifttt_response";

	public static final String ARIA_CONNECTION_PATH = "/aria_conn_status";
	public static final String ARIA_CONNECTION_STATUS = "connection_status";
	public static final int ARIA_CONNECTION_STATUS_CONNECTED = 1;
	public static final int ARIA_CONNECTION_STATUS_DISCONNECTED = 0;

	public final static String ARIA_EXCEPTION_PATH = "/exception";
	public final static String ARIA_EXCEPTION_MESSAGE = "exception_message";

	public static final String PRECAL_PATH="/PreCalib";

	private Context context;
	private GoogleApiClient apiClient;
	private ArrayList<SmartwatchListener> listeners;
	private String pathToRead;
	private boolean isConnected;

	private int currentView=-1;

// Storage permission
	private static final int REQUEST_EXTERNAL_STORAGE = 1;
	private static String[] PERMISSIONS_STORAGE = {
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE
	};

    /*
    private SensorManager sensorManager;
    private Sensor accelerometer;

    int averageSize = 5;
    float[] xValues = new float[averageSize];
    float[] yValues = new float[averageSize];
    float[] zValues = new float[averageSize];
    int averageIndex = 0;
    float averageX = 0;
    float averageY = 0;
    float averageZ = 0;
    float threshold = 5;
    boolean isStable = true;
    */

	public SmartwatchManager(Context _context){
		Log.d(TAG, "SmartwatchManager: ");
		context = _context;

		GoogleApiClient.Builder builder = new GoogleApiClient.Builder(context);
		builder.addApi(Wearable.API);
		builder.addConnectionCallbacks(this);
		builder.addOnConnectionFailedListener(this);

		apiClient = builder.build();

		listeners = new ArrayList<>();

		//sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		//accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

	}//SmartphoneManager


	public void addListener(SmartwatchListener _listener){
		Log.d(TAG, "addListener: ");

		listeners.add(_listener);

	}//addListener


	public void removeListener(SmartwatchListener _listener){
		Log.d(TAG, "removeListener: ");

		listeners.remove(_listener);

	}//removeListener


	//actions

	public void connect(){
		Log.d(TAG, "connect: ");

		//sensorManager.registerListener(this, accelerometer , SensorManager.SENSOR_DELAY_NORMAL);

		apiClient.connect();

	}//connect


	public void disconnect(){
		Log.d(TAG, "disconnect: ");

		//sensorManager.unregisterListener(this);

		Wearable.DataApi.removeListener(apiClient, this);
		Wearable.MessageApi.removeListener(apiClient, this);
		apiClient.disconnect();
		apiClient.unregisterConnectionCallbacks(this);
		apiClient.unregisterConnectionFailedListener(this);

	}//disconnect

// TODO: get rid of it and put only MessageAPI
	public void writeSharedData(final String _path, final DataMap _map){
		Log.d(TAG, "writeSharedData: ");
		//delete the old one
		Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(_path).build();
		Wearable.DataApi.deleteDataItems(apiClient, uri).setResultCallback(new ResultCallback<DataApi.DeleteDataItemsResult>(){

			public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult){

				//create the new one
				PutDataMapRequest putRequest = PutDataMapRequest.create(_path);
				DataMap map = putRequest.getDataMap();
				map.putAll(_map);
				Wearable.DataApi.putDataItem(apiClient, putRequest.asPutDataRequest());

			}
		});

	}//writeSharedData


	public void readSharedData(String _path){
		Log.d(TAG, "readSharedData: " + _path);
		pathToRead = _path;

		PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(apiClient);
		results.setResultCallback(this);

	}//readSharedData


	//events

	public void onConnected(Bundle bundle){
		Log.d(TAG, "onConnected: ");
		Wearable.DataApi.addListener(apiClient, this);
		Wearable.MessageApi.addListener(apiClient, this);

		isConnected = true;

		for (int i=0; i<listeners.size(); i++){
			listeners.get(i).onApiConnected();
		}

	}//onConnected


	public void onConnectionSuspended(int _info) {
		Log.d(TAG, "onConnectionSuspended: ");

		isConnected = false;

		for (int i=0; i<listeners.size(); i++){
			listeners.get(i).onApiDisconnected();
		}
	}//onConnectionSuspended


	public void onConnectionFailed(ConnectionResult connectionResult) {
		Log.d(TAG, "onConnectionFailed: ");

		isConnected = false;

		for(int i=0 ; i<listeners.size() ; i++){
			listeners.get(i).onApiDisconnected();
		}

	}//onConnectionFailed

	public boolean isConnected(){
		Log.d(TAG, "isConnected: ");

		return isConnected;

	}//isConnected


	public void onDataChanged(DataEventBuffer _dataEvents){
		Log.d(TAG, "onDataChanged: ");
		for(int y=0 ; y<_dataEvents.getCount() ; y++){

			DataEvent dataEvent = _dataEvents.get(y);
			if(dataEvent.getType() == DataEvent.TYPE_DELETED) continue;

			DataItem data = dataEvent.getDataItem();

			String path = data.getUri().getPath();

			DataMapItem dataMapItem = DataMapItem.fromDataItem(data);
			DataMap dataMap = dataMapItem.getDataMap();
			Log.d(TAG, "onDataChanged: " + path);
//
//			for(int i=0 ; i<listeners.size() ; i++){
//				listeners.get(i).onSharedDataChanged(path, dataMap);
//			}

		}

	}//onDataChanged


	public void onResult(DataItemBuffer dataItems){
		Log.d(TAG, "onResult: ");

		boolean found = false;

		for(int y=0 ; y<dataItems.getCount() ; y++){

			DataItem data = dataItems.get(y);
			String path = data.getUri().getPath();

			if(path.equals(pathToRead)){

				DataMapItem dataMapItem = DataMapItem.fromDataItem(data);
				DataMap dataMap = dataMapItem.getDataMap();

				for(int i=0 ; i<listeners.size() ; i++){
					found = true;
					listeners.get(i).onSharedDataRead(pathToRead, dataMap);
				}

			}
			if(path.equals(SmartwatchManager.PHONE_MESSAGE_PATH)){

				DataMapItem dataMapItem = DataMapItem.fromDataItem(data);
				DataMap dataMap = dataMapItem.getDataMap();

				for(int i=0 ; i<listeners.size() ; i++){
					found = true;
					listeners.get(i).onSharedDataRead(SmartwatchManager.PHONE_MESSAGE_PATH, dataMap);
				}

			}

		}

		if(found == false){
			for(int i = 0; i < listeners.size(); i++){
				listeners.get(i).onSharedDataNotFound(pathToRead);
			}
		}

		dataItems.release();

	}//onResult - callback for Wearable.DataApi.getDataItems




	public void onMessageReceived(MessageEvent messageEvent){
		//if(isStable == false) return;
//		Log.d(TAG, "onMessageReceived: ");

		String path = messageEvent.getPath();
		String text = new String(messageEvent.getData());
		Log.d(TAG, "onMessageReceived path:" + path + "; message:" + text);


		if (text.contains("LOG")){
			Log.d(TAG, "onMessageReceived: " + path + " " + text);
			writeTextinFile(text);
		}

		if(path.equals(SmartwatchManager.ROUTER_PATH)){
			Log.d(TAG, "onMessageReceived: inside path");
			EventBus.getDefault().post(new RouterEvent(Integer.valueOf(text)));

		}else if(path.equals(SmartwatchManager.DASHBOARD_MENU_ITEM)){
			Log.d(TAG, "onMessageReceived: inside dashboard menu item");
			currentView=Integer.parseInt(text);
			EventBus.getDefault().post(new DashboardEvent(Integer.valueOf(text)));

		}else if(path.equals(SmartwatchManager.LEARNING_STATUS)){
			Log.d(TAG, "onMessageReceived: learning status");

			EventBus.getDefault().post(new LearningEvent(Integer.valueOf(text)));

		}else if(path.equals(SmartwatchManager.PHONE_MESSAGE_MUSIC)){
			EventBus.getDefault().post(new MusicEvent(text));
		}



		if(path==SmartwatchManager.PHONE_MESSAGE_PATH){
			switch(text){
				case SmartwatchManager.PHONE_START_LEARNING:

				break;


			}
		}

//
//		switch(path) {
//			case ARIA_MESSAGE_PATH:
//				Log.d(TAG, "onMessageReceived: ricevuto gesto padrone");
//				Log.d(TAG, "onMessageReceived: " + text);
//				for (int i=0; i<listeners.size(); i++) {
//					listeners.get(i).onGestureReceived(text);
//				}
//				break;
//			case ARIA_UI_PATH:
//				for (int i=0; i<listeners.size(); i++) {
//					listeners.get(i).onUiStateReceived(text);
//				}
//				break;
//			case ARIA_NOTIFICATION_PATH_REMOVE:
//				for (int i=0; i<listeners.size(); i++) {
//					listeners.get(i).onNotificationChangeReceived(text, path);
//				}
//				break;
//			case ARIA_NOTIFICATION_PATH_FIRE:
//				for (int i=0; i<listeners.size(); i++) {
//					listeners.get(i).onNotificationChangeReceived(text, path);
//				}
//				break;
//			default:
//				return;
//		}

	}//onMessageReceived


	public void setCurrentView(int i){
		Log.d(TAG, "setCurrentView: ");
		currentView=i;}

	public int getCurrentView(){
		Log.d(TAG, "getCurrentView: ");
		return currentView;}

	private void writeTextinFile(String text){
		Log.d(TAG, "writeTextinFile: ");
		String fullName = "AriaLog.txt";
		File root = Environment.getExternalStorageDirectory();
		File logFile = new File(root.getAbsolutePath(), fullName);
		if (!logFile.exists())
		{
			try
			{
				logFile.createNewFile();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try
		{
			//BufferedWriter for performance, true to set append to file flag
			BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
			buf.append(text);
			buf.newLine();
			buf.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}





	public void sendMessage(final String message, final String messagePath) {
		Log.d(TAG, "sendMessage: ");
		new Thread(new Runnable() {
			@Override
			public void run() {
				NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(apiClient).await();
				if(message != null && messagePath !=  null){
					for(Node node : nodes.getNodes()){
						Log.d(TAG, "run: stamandando " + messagePath + " // " + message );
						MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(apiClient, node.getId(), messagePath, message.getBytes()).await();
					}
				}
			}
		}).start();
	}


	//sensors

    /*
    public void onSensorChanged(SensorEvent _event){

        Sensor sensor = _event.sensor;

        if(sensor.getType() == Sensor.TYPE_GYROSCOPE){

            //1. calculate actual average
            for(int i=0 ; i<averageSize ; i++){

                averageX += xValues[i];
                averageY += xValues[i];
                averageZ += xValues[i];

            }

            averageX /= averageSize;
            averageY /= averageSize;
            averageZ /= averageSize;

            //2. take new values
            float x = _event.values[0];
            float y = _event.values[1];
            float z = _event.values[2];

            //3. check new values
            float deltaX = Math.abs(averageX-x);
            float deltaY = Math.abs(averageY-y);
            float deltaZ = Math.abs(averageZ-z);

            if(deltaX > threshold || deltaY > threshold || deltaZ > threshold){

                if(isStable == true){
                    isStable = false;
                    //Log.d("debug", "NO");
                }

            }else{

                if(isStable == false){
                    isStable = true;
                    //Log.d("debug", "YES");
                }

            }

            //4. add new values to the average
            xValues[averageIndex] = x;
            yValues[averageIndex] = y;
            zValues[averageIndex] = z;

            averageIndex++;
            if(averageIndex >= averageSize) averageIndex = 0;

        }

    }//onSensorChanged


    public void onAccuracyChanged(Sensor sensor, int accuracy){}//onAccuracyChanged
    */

	/**
	 * Checks if the app has permission to write to device storage
	 *
	 * If the app does not has permission then the user will be prompted to grant permissions
	 *
	 * @param activity
	 */
	public static void verifyStoragePermissions(Activity activity) {
		Log.d("SmartwatchManagerP", "verifyStoragePermissions: ");
		// Check if we have write permission
		int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

		if (permission != PackageManager.PERMISSION_GRANTED) {
			// We don't have permission so prompt the user
			ActivityCompat.requestPermissions(
					activity,
					PERMISSIONS_STORAGE,
					REQUEST_EXTERNAL_STORAGE
			);
		}
	}
}//SmartphoneManager
