package com.mediatek.mbrainlocalservice.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.MBrainServiceWrapperAidl;
import com.mediatek.mbrainlocalservice.helper.NotifyMessageHelper;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.function.Supplier;


public class UsbIntentReceiver extends BroadcastReceiver {
    private final String TAG = "UsbIntentReceiver";

    private Supplier<Boolean> mMbrainWrapperChecker;
    private Supplier<MBrainServiceWrapperAidl> mMbrainWrapperGetter;

    public UsbIntentReceiver(Supplier<Boolean> mbrainWrapperChecker, Supplier<MBrainServiceWrapperAidl> mbrainWrapperGetter) {
        mMbrainWrapperChecker = mbrainWrapperChecker;
        mMbrainWrapperGetter = mbrainWrapperGetter;
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        boolean isNeedToNotify = UsbHelper.updateInfo(intent);
        if (isNeedToNotify) {
            notifyChange(UsbHelper.getConnect(), UsbHelper.getHost(), UsbHelper.getFunction());
        }
    }

    private void notifyChange(int isConnect, int isHost, String function) { 
        if (mMbrainWrapperChecker.get()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateUsbChangeNotifyMsg(isConnect, isHost, function);
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyChange: " + notifyMsg);
                    mMbrainWrapperGetter.get().notifyUsbChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyChange RemoteException");
            }
        } else {
            Slog.d(TAG, "notifyChange failed");
        }
    }
}