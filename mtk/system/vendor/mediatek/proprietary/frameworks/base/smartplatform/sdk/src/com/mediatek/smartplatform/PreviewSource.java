package com.mediatek.smartplatform;

import android.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class PreviewSource {
    private static String TAG = "PreviewSource";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        GENERAL_CAMERA,
        AVM_ALL,
        AVM_ONE,
        AVM_FRONT,
        AVM_LEFT,
        AVM_RIGHT,
        AVM_BACK,
        GENERAL_CAMERA_EX,
        AVM_ALL_ALGO,
        PREVIEW_0,
        PREVIEW_1,
        PREVIEW_2,
        PREVIEW_3,
        PREVIEW_4

    })
    public @interface Format {
    }


    public static final int GENERAL_CAMERA        = 0;

    public static final int AVM_ALL               = 1;
    public static final int AVM_ONE               = 2;
    public static final int AVM_FRONT             = 3;
    public static final int AVM_LEFT              = 4;
    public static final int AVM_RIGHT             = 5;
    public static final int AVM_BACK              = 6;
    public static final int GENERAL_CAMERA_EX     = 7;
    public static final int AVM_ALL_ALGO          = 8;

    public static final int PREVIEW_0             = 9;
    public static final int PREVIEW_1             = 10;
    public static final int PREVIEW_2             = 11;
    public static final int PREVIEW_3             = 12;
    public static final int PREVIEW_4             = 13;


    public static final int PreviewSource_END     = 14;

    static int getSurfaceUsage(@Format int source) {
        switch(source) {
        case GENERAL_CAMERA :
        case AVM_ALL :
        case AVM_ALL_ALGO:
        case PREVIEW_0:
            return CameraSurfaceUsage.DISPLAY_0;
        case AVM_ONE:
        case AVM_FRONT:
        case GENERAL_CAMERA_EX:
        case PREVIEW_1:
            return CameraSurfaceUsage.DISPLAY_1;
        case AVM_LEFT:
        case PREVIEW_2:
            return CameraSurfaceUsage.DISPLAY_2;
        case AVM_RIGHT:
        case PREVIEW_3:
            return CameraSurfaceUsage.DISPLAY_3;
        case AVM_BACK:
        case PREVIEW_4:
            return CameraSurfaceUsage.DISPLAY_4;

        default:
            Log.wtf(TAG, " Does not support this source : " + source);
            break;
        }
        return -2;
    }

}

