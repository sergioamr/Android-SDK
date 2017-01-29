package com.deus_tech.ariasdk.nusBleService;


public interface NusGattListener{
    void onNusNotifyEnabled();
    void onNusNotifyDisabled();
    void onDataArrived(byte[] str);
}
