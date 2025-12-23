package com.mediatek.smartplatform;

import java.io.FileDescriptor;
import java.io.IOException;

import android.util.Log;
import android.graphics.Bitmap;

/**
 * A class for jni
 */
public final class SmartPlatformJni {

    private static final String TAG = "SmartPlatformJni";

    static {
        try {
            System.loadLibrary("smartplatformjni");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static native FileDescriptor nativeAshmemOpen(String name, int size) throws IOException;
    public static native FileDescriptor nativeAshmemOpen(FileDescriptor fd, int size) throws IOException;
    public static native long nativeAshmemMmap(FileDescriptor fd, int size, int prot) throws IOException;
    public static native void nativeAshmemMunmap(FileDescriptor fd, long address, int size) throws IOException;
    public static native int nativeAshmemWriteBitmap(FileDescriptor fd, long address, Bitmap bitmap);
    public static native int nativeAshmemWriteBytes(FileDescriptor fd, long address, byte[] bytes, int srcOffset, int destOffset, int count, boolean unpined);
    public static native int nativeAshmemReadBytes(FileDescriptor fd, long address, byte[] bytes, int srcOffset, int destOffset, int count) throws IOException;
    public static native int nativeAshmemClose(FileDescriptor fd);
    public static native boolean nativeIsAvmCamera(int cameraId);
}
