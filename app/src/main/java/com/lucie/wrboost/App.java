package com.lucie.wrboost;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        new Thread(() -> CrashLog.scanExits(this)).start();
    }
}
