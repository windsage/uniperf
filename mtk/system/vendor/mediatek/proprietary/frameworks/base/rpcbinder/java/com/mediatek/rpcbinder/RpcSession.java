
package com.mediatek.rpcbinder;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class RpcSession {
    private static final String TAG = "RpcSession";

    static {
        System.loadLibrary("rpcbinder_jni"); // Load the JNI library
        native_init();
    }

    private long mNativeContext; // Pointer to the native object

    private RpcSession() {
        native_setup();
    }

    public static RpcSession make() {
        return new RpcSession();
    }

    public native final void setMaxIncomingThreads(long threads);
    public native final int setupVsockClient(int cvd, int port);
    public native final int setupInetClient(String addr, int port);
    public native final IBinder getRootObject();
    public native final void release();


    @Override
    protected void finalize() {
        Log.i(TAG, "finalize called");
        native_finalize();
    }

    private static native final void native_init();
    private native final void native_setup();
    private native final void native_finalize();



}
