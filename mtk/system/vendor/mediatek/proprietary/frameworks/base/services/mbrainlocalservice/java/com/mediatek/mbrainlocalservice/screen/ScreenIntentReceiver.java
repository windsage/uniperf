package com.mediatek.mbrainlocalservice.screen;

import android.os.Handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.MBrainServiceWrapperAidl;
import com.mediatek.mbrainlocalservice.helper.NotifyMessageHelper;
import com.mediatek.mbrainlocalservice.helper.MtkLoggerHelper;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.function.Supplier;


public class ScreenIntentReceiver extends BroadcastReceiver {
    private final String TAG = "ScreenIntentReceiver";

    private Supplier<Boolean> mMbrainWrapperChecker;
    private Supplier<MBrainServiceWrapperAidl> mMbrainWrapperGetter;

    public ScreenIntentReceiver(Supplier<Boolean> mbrainWrapperChecker,
        Supplier<MBrainServiceWrapperAidl> mbrainWrapperGetter) {
        mMbrainWrapperChecker = mbrainWrapperChecker;
        mMbrainWrapperGetter = mbrainWrapperGetter;
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        boolean isNeedToNotify = ScreenHelper.updateInfo(intent);
        if (isNeedToNotify) {
            notifyChange(ScreenHelper.getScreen());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MtkLoggerHelper.screenStatusChanged(context);
                }
            }).start();
        }
    }

    private void notifyChange(int screen) {
        if (mMbrainWrapperChecker.get()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateScreenChangeNotifyMsg(screen);
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyChange: " + notifyMsg);
                    mMbrainWrapperGetter.get().notifyScreenChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyChange RemoteException");
            }
        } else {
            Slog.e(TAG, "notifyChange failed");
        }
    }
}