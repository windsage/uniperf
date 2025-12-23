package com.mediatek.mbrainlocalservice.usb;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.mediatek.mbrainlocalservice.helper.Utils;
import android.util.Slog;

public class UsbHelper {
    private static final String TAG = "UsbHelper";

    private static final String FUNCTION_NONE = "none";

    private static boolean mIsConnect;
    private static boolean mIsHost;
    private static String mFunction = FUNCTION_NONE;

    public static boolean updateInfo(Intent intent) {
        if (intent == null || intent.getExtras() == null) {
            return false;
        }
        //Slog.d(TAG, "mIsConnect: " + mIsConnect + ", mIsHost: " + mIsHost);
        boolean isConnect = intent.getExtras().getBoolean(UsbManager.USB_CONNECTED);
        boolean isHost = intent.getExtras().getBoolean(UsbManager.USB_HOST_CONNECTED);

        boolean isConnectChanged = (mIsConnect != isConnect);
        boolean isHostChanged = (mIsHost != isHost);
        boolean isFunctionChanged = updateFunction(intent.getExtras());

        mIsConnect = isConnect;
        mIsHost = isHost;
        //Slog.d(TAG, "mIsConnect: " + mIsConnect + ", mIsHost: " + mIsHost + ", isConnectChanged: " + isConnectChanged + ", isHostChanged: " + isHostChanged);

        boolean isChanged = isConnectChanged || isHostChanged || isFunctionChanged;
        return isChanged;
    }

    public static int getConnect() {
        return (mIsConnect || mIsHost) ? 1 : 0;
    }

    public static int getHost() {
        return (mIsHost) ? 1 : 0;
    }

    public static String getFunction() {
        return mFunction;
    }

    private static boolean updateFunction(Bundle extras) {
        if (extras == null) {
            return false;
        }

        long functionMask = UsbManager.FUNCTION_NONE;
        if(extras.getBoolean(UsbManager.USB_FUNCTION_ACCESSORY)) {
            functionMask |= UsbManager.FUNCTION_ACCESSORY;
        }
        if(extras.getBoolean(UsbManager.USB_FUNCTION_AUDIO_SOURCE)){
            functionMask |= UsbManager.FUNCTION_AUDIO_SOURCE;
        }
        if(extras.getBoolean(UsbManager.USB_FUNCTION_MIDI)){
            functionMask |= UsbManager.FUNCTION_MIDI;
        }

        String function = UsbManager.usbFunctionsToString(functionMask);
        if(!Utils.isValidString(function)) {
            function = FUNCTION_NONE;
        }

        boolean isChanged = !FUNCTION_NONE.equals(function);
        mFunction = function;
        return isChanged;
    }
}
