package com.mediatek.smartplatform;

import android.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;



public class PictureSequenceSource {
    private static String TAG = "PictureSequenceSource";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        GENERAL_CAMERA,
        AVM_ALL,
        AVM_FRONT,
        AVM_LEFT,
        AVM_RIGHT,
        AVM_BACK,
        GENERAL_CAMERA_EX,
        AVM_ALL_EX
    })
    public @interface Format {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        TAKE_PICTURE_SUCCESS,
        TAKE_PICTURE_FAIL
    })
    public @interface TakePictureResult {
    }

    public static final int GENERAL_CAMERA       = 0;

    public static final int AVM_ALL              = 1;
    public static final int AVM_FRONT            = 2;
    public static final int AVM_LEFT             = 3;
    public static final int AVM_RIGHT            = 4;
    public static final int AVM_BACK             = 5;

    public static final int GENERAL_CAMERA_EX    = 6;

    public static final int AVM_ALL_EX           = 7;


    public static final int PictureSource_END    = 8;

    public static final int TAKE_PICTURE_SUCCESS   = 0;
    public static final int TAKE_PICTURE_FAIL      = -1;


    static String getPictureSequenceId(@Format int source) {
        return Integer.toString(source);
    }

    static int getSurfaceUsage(@Format int source) {
        switch(source) {
        case GENERAL_CAMERA :
            return CameraSurfaceUsage.EXTERNAL_0;
        case GENERAL_CAMERA_EX:
        case AVM_FRONT:
            return CameraSurfaceUsage.EXTERNAL_1;
        case AVM_LEFT:
            return CameraSurfaceUsage.EXTERNAL_2;
        case AVM_RIGHT:
            return CameraSurfaceUsage.EXTERNAL_3;
        case AVM_BACK:
            return CameraSurfaceUsage.EXTERNAL_4;
        default:
            Log.wtf(TAG, " Does not support this source : " + source);
            break;
        }
        return -1;
    }


}

