package io.github.fangtianq.callsms;

import android.app.Application;
import android.content.Context;

import io.github.fangtianq.callsms.core.Logs;

public class App extends Application {

    private static App instance;

    public static Context ctx() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Logs.i("app start v1.0.0");
    }
}
