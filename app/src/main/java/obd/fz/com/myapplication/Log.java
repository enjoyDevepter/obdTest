package obd.fz.com.myapplication;

import android.os.Environment;

import timber.log.Timber;

/**
 * Created by guomin on 2018/4/23.
 */

public class Log {

    public final static String TAG = "OBD_CORE";

    static {
        Timber.plant(new Timber.DebugTree());
        Timber.plant(new FileLoggingTree(Environment.getExternalStorageDirectory().getPath() + "/obd_unbind"));
    }

    public static void d(String message) {
        Timber.tag(TAG);
        Timber.d(message + "\n");
    }
}

