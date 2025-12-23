package com.mediatek.mbrainlocalservice.appstate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.MBrainServiceWrapperAidl;
import com.mediatek.mbrainlocalservice.helper.NotifyMessageHelper;
import com.mediatek.mbrainlocalservice.helper.ApplicationHelper;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.function.Supplier;


public class AppStateIntentReceiver extends BroadcastReceiver {
    private final String TAG = "AppStateIntentReceiver";

    private Supplier<Boolean> mMbrainWrapperChecker;
    private Supplier<MBrainServiceWrapperAidl> mMbrainWrapperGetter;

    public AppStateIntentReceiver(Supplier<Boolean> mbrainWrapperChecker, Supplier<MBrainServiceWrapperAidl> mbrainWrapperGetter) {
        mMbrainWrapperChecker = mbrainWrapperChecker;
        mMbrainWrapperGetter = mbrainWrapperGetter;
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        AppStateHelper appStateHelper = new AppStateHelper();
        appStateHelper.updateInfo(intent);
        ApplicationHelper.updateInstalledApplication();
        notifyChange(appStateHelper.getState(), appStateHelper.getUid(), appStateHelper.getPackageName());
    }

    private void notifyChange(int state, int uid, String packageName) {
        if (mMbrainWrapperChecker.get() && uid != -1 && Utils.isValidString(packageName)) {
            try {
                String notifyMsg = NotifyMessageHelper.generateAppStateChangeNotifyMsg(state, uid,
                                    packageName, ApplicationHelper.getVersionNameByPackageName(packageName));
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyChange: " + notifyMsg);
                    mMbrainWrapperGetter.get().notifyAppStateChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyChange RemoteException");
            }
        } else {
            Slog.e(TAG, "notifyChange failed");
        }
    }
}