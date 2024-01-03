package com.github.shadowsocks.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.ContextWrapper;

@TargetApi(24)
public class DeviceContext extends ContextWrapper {
    public DeviceContext(Context context) {
        super(context.createDeviceProtectedStorageContext());
    }
    
    @Override
    public Context getApplicationContext() {
        return this;
    }
}