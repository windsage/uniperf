package com.mediatek.mbrainlocalservice.screen;

import android.content.Intent;
import android.util.Slog;

public class ScreenHelper {
    private static final String TAG = "ScreenHelper";

    private static ScreenStatus mScreen = ScreenStatus.UNKNOWN;

    public static boolean updateInfo(Intent intent) {
        if (intent == null) {
            return false;
        }
        //Slog.d(TAG, "mScreen: " + mScreen.getValue());
        ScreenStatus screen = ScreenStatus.UNKNOWN;
        switch(intent.getAction()) {
            case Intent.ACTION_SCREEN_ON:
                screen = ScreenStatus.ON;
                break;
            case Intent.ACTION_SCREEN_OFF:
                screen = ScreenStatus.OFF;
                break;
            case Intent.ACTION_USER_PRESENT:
                screen = ScreenStatus.USER_UNLOCK;
                break;
            default:
                screen = ScreenStatus.UNKNOWN;
                break;
        }

        boolean isChanged = (screen.getValue() != getScreen());
        //Slog.d(TAG, "screen: " + screen.getValue() + ", isChanged: " + isChanged);
        mScreen = screen;

        return isChanged;
    }

    public static int getScreen() {
        return mScreen.getValue();
    }

    public static boolean isScreenOff() {
        return mScreen == ScreenStatus.OFF;
    }

    enum ScreenStatus {
        OFF(0),
        ON(1),
        USER_UNLOCK(2),
        UNKNOWN(3);

        private int value;
        private ScreenStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
