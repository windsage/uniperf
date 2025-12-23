package com.mediatek.mbrainlocalservice.appstate;

import android.content.Intent;
import android.net.Uri;

public class AppStateHelper {
    private AppState mAppState;
    private int mUid;
    private String mPackageName;

    public boolean updateInfo(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            mAppState = AppState.REMOVED;
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            mAppState = AppState.ADDED;
        } else if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            mAppState = AppState.REPLACE;
        }
        mUid = getUid(intent);
        mPackageName = getPackageName(intent);

        return true;
    }

    private int getUid(Intent intent) {
        return intent.getIntExtra(Intent.EXTRA_UID, -1);
    }

    private String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        String name = uri != null ? uri.getSchemeSpecificPart() : null;
        return name;
    }

    public int getState() {
        return mAppState.getValue();
    }

    public int getUid() {
        return mUid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    enum AppState {
        REMOVED(0),
        ADDED(1),
        REPLACE(2);

        private int value;
        private AppState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }  
}
