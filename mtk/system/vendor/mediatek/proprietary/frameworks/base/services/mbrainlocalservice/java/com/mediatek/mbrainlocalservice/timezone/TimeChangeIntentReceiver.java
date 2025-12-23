package com.mediatek.mbrainlocalservice.timezone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Slog;

import com.mediatek.mbrainlocalservice.MBrainServiceWrapperAidl;
import com.mediatek.mbrainlocalservice.helper.NotifyMessageHelper;
import com.mediatek.mbrainlocalservice.helper.Utils;

import java.util.function.Supplier;

import java.util.TimeZone;


public class TimeChangeIntentReceiver extends BroadcastReceiver {
    private final String TAG = "TimeChangeIntentReceiver";

    private Supplier<Boolean> mMbrainWrapperChecker;
    private Supplier<MBrainServiceWrapperAidl> mMbrainWrapperGetter;
    private TimeChangeStatus mTimeChangeStatus = TimeChangeStatus.UNKNOWN;

    public TimeChangeIntentReceiver(Supplier<Boolean> mbrainWrapperChecker, Supplier<MBrainServiceWrapperAidl> mbrainWrapperGetter) {
        mMbrainWrapperChecker = mbrainWrapperChecker;
        mMbrainWrapperGetter = mbrainWrapperGetter;
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            mTimeChangeStatus = TimeChangeStatus.TIMEZONE;
        } else if (Intent.ACTION_TIME_CHANGED.equals(action)) {
            mTimeChangeStatus = TimeChangeStatus.TIME;
        }

        TimeChangeHelper.updateTimeZone();
        notifyChange();
    }

    private void notifyChange() {
        if (mMbrainWrapperChecker.get()) {
            try {
                String notifyMsg = NotifyMessageHelper.generateTimeChangeNotifyMsg(
                    mTimeChangeStatus.getValue(), TimeChangeHelper.getTimeZoneId(), TimeChangeHelper.getCurrentDateAndTime());
                if (Utils.isValidString(notifyMsg)) {
                    //Slog.d(TAG, "notifyChange: " + notifyMsg);
                    mMbrainWrapperGetter.get().notifyTimeChangeToService(notifyMsg);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "notifyChange RemoteException");
            }
        } else {
            Slog.e(TAG, "notifyChange failed");
        }
    }

    enum TimeChangeStatus {
        TIMEZONE(0),
        TIME(1),
        UNKNOWN(2);

        private int value;
        private TimeChangeStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}