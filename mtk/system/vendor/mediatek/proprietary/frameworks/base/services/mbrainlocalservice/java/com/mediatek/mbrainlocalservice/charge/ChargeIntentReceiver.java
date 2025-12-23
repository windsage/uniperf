package com.mediatek.mbrainlocalservice.charge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.MBrainServiceWrapperAidl;
import com.mediatek.mbrainlocalservice.helper.NotifyMessageHelper;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.function.Supplier;


public class ChargeIntentReceiver extends BroadcastReceiver {
    private final String TAG = "ChargeIntentReceiver";

    private Supplier<Boolean> mMbrainWrapperChecker;
    private Supplier<MBrainServiceWrapperAidl> mMbrainWrapperGetter;

    public ChargeIntentReceiver(Supplier<Boolean> mbrainWrapperChecker, Supplier<MBrainServiceWrapperAidl> mbrainWrapperGetter) {
        mMbrainWrapperChecker = mbrainWrapperChecker;
        mMbrainWrapperGetter = mbrainWrapperGetter;
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        boolean isNeedToNotify = ChargeHelper.updateInfo(intent);
        if (isNeedToNotify) {
            notifyChange(ChargeHelper.getStatus(), ChargeHelper.getApproach(), ChargeHelper.getLevel());
        }
    }

    private void notifyChange(int chargeStatus, int chargeApprach, int level) {
        if (mMbrainWrapperChecker.get()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateChargeChangeNotifyMsg(chargeStatus, chargeApprach, level);
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyChange: " + notifyMsg);
                    mMbrainWrapperGetter.get().notifyChargeChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyChange RemoteException");
            }
        } else {
            Slog.e(TAG, "notifyChange failed");
        }
    }    
}