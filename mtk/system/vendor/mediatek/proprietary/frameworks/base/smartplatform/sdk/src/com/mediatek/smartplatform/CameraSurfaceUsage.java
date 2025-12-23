package com.mediatek.smartplatform;

import android.annotation.IntDef;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.hardware.camera2.CameraDevice;


public class CameraSurfaceUsage {
    private static String TAG = "CameraSurfaceUsage";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        DISPLAY_0,
        DISPLAY_1,
        DISPLAY_2,
        DISPLAY_3,
        DISPLAY_4,
        RECORD_0,
        RECORD_1,
        RECORD_2,
        RECORD_3,
        RECORD_4,
        EXTERNAL_0,
        EXTERNAL_1,
        EXTERNAL_2,
        EXTERNAL_3,
        EXTERNAL_4,
        PICTURE_0,
        ADAS_0

    })
    public @interface Format {
    }

    public static final int BEGIIN        = 0;

    public static final int DISPLAY_0     = 0;

    public static final int DISPLAY_1     = 1;
    public static final int DISPLAY_2     = 2;
    public static final int DISPLAY_3     = 3;
    public static final int DISPLAY_4     = 4;

    public static final int RECORD_0      = 5;
    public static final int RECORD_1      = 6;
    public static final int RECORD_2      = 7;
    public static final int RECORD_3      = 8;
    public static final int RECORD_4      = 9;


    public static final int EXTERNAL_0    = 10;
    public static final int EXTERNAL_1    = 11;
    public static final int EXTERNAL_2    = 12;
    public static final int EXTERNAL_3    = 13;
    public static final int EXTERNAL_4    = 14;

    public static final int PICTURE_0     = 15;

    public static final int ADAS_0        = 16;

    public static final int END           = 17;



    //should update SpmCameraDevice STATE_XXX
    private static final int DISPLAY_0_STATE      = 0x1;

    private static final int DISPLAY_1_STATE      = 0x2;
    private static final int DISPLAY_2_STATE      = 0x4;
    private static final int DISPLAY_3_STATE      = 0x8;
    private static final int DISPLAY_4_STATE      = 0x10;

    private static final int RECORD_0_STATE       = 0x20;
    private static final int RECORD_1_STATE       = 0x40;
    private static final int RECORD_2_STATE       = 0x80;
    private static final int RECORD_3_STATE       = 0x100;
    private static final int RECORD_4_STATE       = 0x200;


    private static final int EXTERNAL_0_STATE     = 0x400;
    private static final int EXTERNAL_1_STATE     = 0x800;
    private static final int EXTERNAL_2_STATE     = 0x1000;
    private static final int EXTERNAL_3_STATE     = 0x2000;
    private static final int EXTERNAL_4_STATE     = 0x4000;

    private static final int PICTURE_0_STATE      = 0x8000;

    private static final int ADAS_0_STATE         = 0x10000;

    static boolean isValid(@Format int surface) {
        switch(surface) {
        case DISPLAY_0 :
        case DISPLAY_1 :
        case DISPLAY_2 :
        case DISPLAY_3 :
        case DISPLAY_4 :
        case RECORD_0 :
        case RECORD_1 :
        case RECORD_2 :
        case RECORD_3 :
        case RECORD_4 :
        case EXTERNAL_0 :
        case EXTERNAL_1 :
        case EXTERNAL_2 :
        case EXTERNAL_3 :
        case EXTERNAL_4 :
        case PICTURE_0:
        case ADAS_0:
            return true;
        }
        return false;
    }

    static int getSurfaceState(@Format int surface) {
        switch(surface) {
        case DISPLAY_0 :
            return DISPLAY_0_STATE;
        case DISPLAY_1 :
            return DISPLAY_1_STATE;
        case DISPLAY_2 :
            return DISPLAY_2_STATE;
        case DISPLAY_3 :
            return DISPLAY_3_STATE;
        case DISPLAY_4 :
            return DISPLAY_4_STATE;
        case RECORD_0 :
            return RECORD_0_STATE;
        case RECORD_1 :
            return RECORD_1_STATE;
        case RECORD_2 :
            return RECORD_2_STATE;
        case RECORD_3 :
            return RECORD_3_STATE;
        case RECORD_4 :
            return RECORD_4_STATE;
        case EXTERNAL_0 :
            return EXTERNAL_0_STATE;
        case EXTERNAL_1 :
            return EXTERNAL_1_STATE;
        case EXTERNAL_2 :
            return EXTERNAL_2_STATE;
        case EXTERNAL_3 :
            return EXTERNAL_3_STATE;
        case EXTERNAL_4 :
            return EXTERNAL_4_STATE;
        case PICTURE_0:
            return PICTURE_0_STATE;
        case ADAS_0:
            return ADAS_0_STATE;
        }
        return 0;

    }

    static int getSurfaceRequestType(@Format int surface) {
        switch(surface) {
        case DISPLAY_0 :
        case DISPLAY_1 :
        case DISPLAY_2 :
        case DISPLAY_3 :
        case DISPLAY_4 :
            return CameraDevice.TEMPLATE_PREVIEW;
        case RECORD_0 :
        case RECORD_1 :
        case RECORD_2 :
        case RECORD_3 :
        case RECORD_4 :
            return CameraDevice.TEMPLATE_RECORD;
        case EXTERNAL_0 :
        case EXTERNAL_1 :
        case EXTERNAL_2 :
        case EXTERNAL_3 :
        case EXTERNAL_4 :
            return CameraDevice.TEMPLATE_PREVIEW;
        case PICTURE_0:
            return CameraDevice.TEMPLATE_PREVIEW;
        case ADAS_0:
            return CameraDevice.TEMPLATE_PREVIEW;
        }
        return 0;

    }


    static int getRecordState() {
        int recordState = RECORD_0_STATE |
                          RECORD_1_STATE |
                          RECORD_2_STATE |
                          RECORD_3_STATE |
                          RECORD_4_STATE;
        return recordState;

    }
    static int getSurfaceExId(@Format int surface) {
        switch(surface) {
        case EXTERNAL_0 :
            return 0;
        case EXTERNAL_1 :
            return 1;
        case EXTERNAL_2 :
            return 2;
        case EXTERNAL_3 :
            return 3;
        case EXTERNAL_4 :
            return 4;
        }
        return -1;

    }

    static boolean IsExSurface(@Format int surface) {
        switch(surface) {
        case EXTERNAL_0 :
        case EXTERNAL_1 :
        case EXTERNAL_2 :
        case EXTERNAL_3 :
        case EXTERNAL_4 :
            return true;
        }
        return false;

    }

    static boolean isPreview(@Format int surface) {
        switch(surface) {
        case DISPLAY_0 :
        case DISPLAY_1 :
        case DISPLAY_2 :
        case DISPLAY_3 :
        case DISPLAY_4 :
            return true;
        }
        return false;

    }

    static boolean isRecord(@Format int surface) {
        switch(surface) {
        case RECORD_0 :
        case RECORD_1 :
        case RECORD_2 :
        case RECORD_3 :
        case RECORD_4 :
            return true;
        }
        return false;

    }

    static boolean isPicture(@Format int surface) {
        switch(surface) {
        case PICTURE_0:
            return true;
        }
        return false;
    }


}

