package com.deus_tech.ariasdk.wearConnectivity;

import com.google.android.gms.wearable.DataMap;

public interface SmartwatchListener {

	void onApiConnected();

	void onApiDisconnected();

	void onSharedDataRead(String _path, DataMap _dataMap);

	void onSharedDataNotFound(String _path);

	void onSharedDataChanged(String _path, DataMap _dataMap);

	void onGestureReceived(String _gesture);

	void onUiStateReceived(String state);

	void onNotificationChangeReceived(String id, String path);

	void onSmartwatchMessage(String text, String path);
}//SmartwatchListener
