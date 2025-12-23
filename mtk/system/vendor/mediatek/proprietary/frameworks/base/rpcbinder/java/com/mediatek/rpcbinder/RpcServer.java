
package com.mediatek.rpcbinder;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class RpcServer {
    private static final String TAG = "RpcServer";

    static {
        System.loadLibrary("rpcbinder_jni"); // Load the JNI library
        native_init();
    }

    private long mNativeContext; // Pointer to the native object

    private RpcServer() {
        native_setup();
    }

    public static RpcServer make() {
        return new RpcServer();
    }


    public native final void setMaxThreads(long threads);
    public native final int setupVsockServer(int cvd, int port);
    public native final int setupInetServer(String addr, int port);
    public native final void setRootObject(IBinder binder);
    public native final void join();
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
