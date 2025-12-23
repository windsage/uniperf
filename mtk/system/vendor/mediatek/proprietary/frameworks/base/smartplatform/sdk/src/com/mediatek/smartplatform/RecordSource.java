package com.mediatek.smartplatform;

import android.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class RecordSource {
    private static String TAG = "RecordSource";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        GENERAL_CAMERA,
        AVM_ALL,
        AVM_FRONT,
        AVM_LEFT,
        AVM_RIGHT,
        AVM_BACK,
        GENERAL_CAMERA_SUB,
        AVM_ALL_SUB,
        AVM_FRONT_SUB,
        AVM_LEFT_SUB,
        AVM_RIGHT_SUB,
        AVM_BACK_SUB,
        RECORD_0,
        RECORD_1,
        RECORD_2,
        RECORD_3,
        RECORD_4,
        RECORD_EX_0,
        RECORD_EX_1,
        RECORD_EX_2,
        RECORD_EX_3,
        RECORD_EX_4

    })
    public @interface Format {
    }

    public static final int GENERAL_CAMERA       = 0;

    public static final int AVM_ALL              = 1;
    public static final int AVM_FRONT            = 2;
    public static final int AVM_LEFT             = 3;
    public static final int AVM_RIGHT           = 4;
    public static final int AVM_BACK           = 5;

    public static final int GENERAL_CAMERA_SUB   = 6;

    public static final int AVM_ALL_SUB          = 7;
    public static final int AVM_FRONT_SUB       = 8;
    public static final int AVM_LEFT_SUB       = 9;
    public static final int AVM_RIGHT_SUB       = 10;
    public static final int AVM_BACK_SUB       = 11;


    public static final int RECORD_0       = 12;
    public static final int RECORD_1       = 13;
    public static final int RECORD_2       = 14;
    public static final int RECORD_3       = 15;
    public static final int RECORD_4       = 16;

    public static final int RECORD_EX_0   = 17;
    public static final int RECORD_EX_1   = 18;
    public static final int RECORD_EX_2   = 19;
    public static final int RECORD_EX_3   = 20;
    public static final int RECORD_EX_4   = 21;


    public static final int RecordSource_END     = 22;

    static int getSurfaceUsage(@Format int source) {
        switch(source) {
        case GENERAL_CAMERA :
        case AVM_ALL :
        case RECORD_0:
            return CameraSurfaceUsage.RECORD_0;
        case GENERAL_CAMERA_SUB:
        case AVM_FRONT:
        case RECORD_1:
            return CameraSurfaceUsage.RECORD_1;
        case AVM_LEFT:
        case RECORD_2:
            return CameraSurfaceUsage.RECORD_2;
        case AVM_RIGHT:
        case RECORD_3:
            return CameraSurfaceUsage.RECORD_3;
        case AVM_BACK:
        case RECORD_4:
            return CameraSurfaceUsage.RECORD_4;
        case AVM_ALL_SUB:
        case RECORD_EX_0:
            return CameraSurfaceUsage.EXTERNAL_0;
        case AVM_FRONT_SUB:
        case RECORD_EX_1:
            return CameraSurfaceUsage.EXTERNAL_1;
        case AVM_LEFT_SUB:
        case RECORD_EX_2:
            return CameraSurfaceUsage.EXTERNAL_2;
        case AVM_RIGHT_SUB:
        case RECORD_EX_3:
            return CameraSurfaceUsage.EXTERNAL_3;
        case AVM_BACK_SUB:
        case RECORD_EX_4:
            return CameraSurfaceUsage.EXTERNAL_4;
        default:
            Log.wtf(TAG, " Does not support this source : " + source);
            break;
        }
        return -1;
    }

    private static final int RECORDER_CAMERA_ID_COMMON = -1;   // 0 / 1
    private static final int RECORDER_CAMERA_ID_BACK = 0;   //main
    private static final int RECORDER_CAMERA_ID_FRONT = 1;  //sub,  interiro car
    private static final int RECORDER_CAMERA_ID_AVM_MAIN = 2; // AVM (all camera)
    private static final int RECORDER_CAMERA_ID_AVM_SUB = 3;  // AVM(single camera)
    private static final int RECORDER_CAMERA_ID_CVBS2_CAMERA = 4;
    private static final int RECORDER_CAMERA_ID_USB_CAMERA = 5;
    private static final int RECORDER_CAMERA_ID_FRONT_SUB = 6; // notify
    private static final int RECORDER_CAMERA_ID_MAX = 7;

    private static final int RECORDER_CAMERA_ID_AVM_CARDINALITY = 10;
    private static final int RECORDER_CAMERA_ID_SUB_CARDINALITY = 100;

    private static final int RECORDER_CAMERA_ID_AVM_FRONT    = 1;
    private static final int RECORDER_CAMERA_ID_AVM_LEFT     = 2;
    private static final int RECORDER_CAMERA_ID_AVM_RIGHT    = 3;
    private static final int RECORDER_CAMERA_ID_AVM_BACK     = 4;



    public static String getRecordId(@Format int source, String cameraId) {
        switch(source) {
        case GENERAL_CAMERA :
        case AVM_ALL:
        case RECORD_0:
            return "Cam_" + cameraId + "#Rec_0";
        case GENERAL_CAMERA_SUB:
        case AVM_FRONT :
        case RECORD_1:
            return "Cam_" + cameraId + "#Rec_1";
        case AVM_LEFT:
        case RECORD_2:
            return "Cam_" + cameraId + "#Rec_2";
        case AVM_RIGHT:
        case RECORD_3:
            return "Cam_" + cameraId + "#Rec_3";
        case AVM_BACK:
        case RECORD_4:
            return "Cam_" + cameraId + "#Rec_4";
        case AVM_ALL_SUB:
        case RECORD_EX_0:
            return "Cam_" + cameraId + "#Rec_Sub_0";
        case AVM_FRONT_SUB:
        case RECORD_EX_1:
            return "Cam_" + cameraId + "#Rec_Sub_1";
        case AVM_LEFT_SUB:
        case RECORD_EX_2:
            return "Cam_" + cameraId + "#Rec_Sub_2";
        case AVM_RIGHT_SUB:
        case RECORD_EX_3:
            return "Cam_" + cameraId + "#Rec_Sub_3";
        case AVM_BACK_SUB:
        case RECORD_EX_4:
            return "Cam_" + cameraId + "#Rec_Sub_4";
        default:
            Log.wtf(TAG, " Does not support this source : " + source);
            break;
        }
        return null;
    }

    public static boolean isSubRecord(@Format int source) {
        switch(source) {
        case GENERAL_CAMERA_SUB:
        case RECORD_EX_0:
        case RECORD_EX_1:
        case RECORD_EX_2:
        case RECORD_EX_3:
        case RECORD_EX_4:
        case AVM_ALL_SUB:
        case AVM_FRONT_SUB:
        case AVM_LEFT_SUB:
        case AVM_RIGHT_SUB:
        case AVM_BACK_SUB:

            return true;
        }
        return false;
    }
    public static boolean isAvmRecord(@Format int source) {
        switch(source) {
        case AVM_ALL :
        case AVM_FRONT:
        case AVM_LEFT:
        case AVM_RIGHT:
        case AVM_BACK:
            return true;
        }
        return false;
    }

}

