package com.mediatek.smartplatform;


import java.lang.RuntimeException;
import java.lang.IllegalStateException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Arrays;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.SystemProperties;

import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Pair;

public class SpmCamParameters {
    private String TAG = "SpmCameraDevice";
    private static final boolean DEBUG = Build.IS_ENG || SystemProperties.getBoolean("persist.vendor.log.spmsdk", false);

    CameraCharacteristics mCameraCharacteristics = null;
    CameraMetadataNative mResultMetadata = null;
    CameraMetadataNative mRequestMetadata = null;
    Range<Integer> mPreviwFpsRange = null;
    // first:streamPolicy, second:usage
    ArrayList<Pair<Integer, Integer>> mDropStreamFrameInfo = null;

    public class Size {
        /**
         * Sets the dimensions for pictures.
         *
         * @param w
         *            the photo width (pixels)
         * @param h
         *            the photo height (pixels)
         */
        public Size(int w, int h) {
            width = w;
            height = h;
        }

        public Size(android.util.Size size) {
            width = size.getWidth();
            height = size.getHeight();
        }

        /**
         * Compares {@code obj} to this size.
         *
         * @param obj
         *            the object to compare this size with.
         * @return {@code true} if the width and height of {@code obj} is the
         *         same as those of this size. {@code false} otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Size)) {
                return false;
            }

            Size s = (Size) obj;
            return width == s.width && height == s.height;
        }

        @Override
        public int hashCode() {
            return width * 32713 + height;
        }

        /** width of the picture */
        public int width;
        /** height of the picture */
        public int height;
    };

    public enum AVMCameraDisplay {
        FRONT   (0),
        LEFT    (1),
        RIGHT   (2),
        BACK    (3),
        NONE    (-1);

        AVMCameraDisplay(int position) {
            this.position=position;
        }
        final int position;

        public int getPosition() {
            return position;
        }
    }

    public enum VehicleGearStatus {
        GEAR_STATUS_P(0),
        GEAR_STATUS_N(1),
        GEAR_STATUS_R(2),
        GEAR_STATUS_D(3),
        GEAR_STATUS_M(4),
        GEAR_STATUS_I(5);

        VehicleGearStatus(int gearStatus) {
            this.gearStatus = gearStatus;
        }

        final int gearStatus;

        public int getGearStatus() {
            return gearStatus;
        }
    }
    public class WatermarkCarInfo
    {
        public int rec;         //recording
        public int vcStatus;    //voice 0 or 1
        public int gearStatus;  //gear 0:N, 1:D, 2:R, 3:P, 4:S
        public int speed;       //speed
        public int ccSpeed;     //Cruise Control speed
        public int accStatus;   //ACC
        public int nnpStatus;   //NNP
        public int acceStatus;  //Accelerator
        public int bpStatus;    //brake padal
        public int absStatus;   //abs
        public int lsStatus;    //left signal
        public int rsStatus;    //right signal
        public int hblStatus;   //high beam lamp
        public int dblStatus;   //dipped beam lamp
        public int fflStatus;   //front frog lamp
        public int msbStatus;   //main safty beltt
        public int csbStatus;   //copilot safty beltt
        public int temp;        //temperature
        public int tempDec;     //temperature decimal
        public String vin;      //vin code
    }

    private LinkedHashMap<String, String> mMap;

    private Object mMapLock = new Object();
    private static final String SUPPORTED_VALUES_SUFFIX = "-values";

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    // Parameter keys to communicate with the camera framework
    private static final String KEY_PREVIEW_SIZE = "preview-size";
    private static final String KEY_VIDEO_SIZE = "video-size";
    private static final String KEY_VIDEO_ROTATE_SIZE = "max-filesize";
    private static final String KEY_VIDEO_ROTATE_DURATION = "max-duration";
    private static final String KEY_VIDEO_MUTE_AIUDO = "mute-recording_audio";
    private static final String KEY_MOTION_DETECT_MODE = "motion_detect_mode";
    private static final String KEY_VIDEO_OUTPUT_FORMAT = "video-output-format";
    private static final String KEY_VIDEO_FRAME_RATE = "video-frame-rate";
    private static final String KEY_VIDEO_PARAM_ENCODING_BITRATE = "video-param-encoding-bitrate";
    private static final String KEY_VIDEO_ENCODER = "video-encoder";
    private static final String KEY_VIDEO_PARAM_CAMERA_ID = "video-param-camera-id";
    private static final String KEY_AUDIO_PARAM_ENCODING_BITRATE = "audio-param-encoding-bitrate";
    private static final String KEY_AUDIO_PARAM_NUMBER_OF_CHANNELS = "audio-param-number-of-channels";
    private static final String KEY_AUDIO_PARAM_SAMPLING_RATE = "audio-param-sampling-rate";
    private static final String KEY_AUDIO_ENCODER = "audio-encoder";
    private static final String KEY_VIDEO_OUTPUT_FILE = "video-output-file";
    private static final String KEY_VIDEO_OUTPUT_FILE_NAME = "video-output-file-name";
    private static final String KEY_VIDEO_LOCK_FILE = "video-lock-file";
    private static final String KEY_LOCK_FILE_NAME_PREFIX = "lock-file-name-prefix";
    private static final String KEY_WATERMARK_OFFSET = "watermark-offset";
    private static final String KEY_WATERMARK_TIMER_OFFSET = "watermark-timer-offset";
    private static final String KEY_WATERMARK_ADDRESS = "watermark-address";
    private static final String KEY_WATERMARK_SIZE = "watermark-size";
    private static final String KEY_WATERMARK_DATA_PATH = "watermark-data-path";
    private static final String KEY_WATERMARK_OWNER_NAME = "watermark-owner-name";
    private static final String KEY_WHITE_BALANCE = "whitebalance";
    private static final String KEY_AUTO_WHITEBALANCE_LOCK = "auto-whitebalance-lock";
    private static final String KEY_AUTO_WHITEBALANCE_LOCK_SUPPORTED = "auto-whitebalance-lock-supported";
    private static final String KEY_MAX_EXPOSURE_COMPENSATION = "max-exposure-compensation";
    private static final String KEY_MIN_EXPOSURE_COMPENSATION = "min-exposure-compensation";
    private static final String KEY_EXPOSURE_COMPENSATION = "exposure-compensation";
    private static final String KEY_EXPOSURE_COMPENSATION_STEP = "exposure-compensation-step";
    private static final String KEY_FREE_SIZE_LIMIT= "free-size-limit";
    private static final String KEY_ADAS_SIZE = "adas-size";
    private static final String KEY_ADAS_END_POINT = "adas-end-point";
    private static final String KEY_ADAS_SCALE_FACTOR = "adas-scale-factor";
    private static final String KEY_ADAS_FOCAL_LENGTH = "adas-focal-length";
    static final String KEY_MAIN_VIDEO_FRAME_ENABLED= "main-video-frame-enabled";
    private static final String KEY_USER_SET_VIDEO_ENCODEBITRATE= "user_set_video_encod_bitrate";
    private static final String KEY_VIDEO_PARAM_ENCODING_MINBITRATE = "video-param-encoding-minbitrate";
    private static final String KEY_VIDEO_PARAM_ENCODING_MAXBITRATE = "video-param-encoding-maxbitrate";
    private static final String KEY_KEYPOINT_SPAN_LIMIT = "keypoint-span-limit";
    private static final String KEY_IMEI_NUMBER = "imei-number";
    private static final String KEY_JPEG_IMAGE_DESC = "jpeg-image-desc";
    private static final String KEY_REC_MUTE_OGG = "rec-mute-ogg";
    private static final String KEY_ENABLE_FILE_TIME = "enable-file-time";
    private static final String KEY_SDCARD_SPEED_LIMIT = "sdcard-speed-limit";
    private static final String KEY_ENABLE_ENCRYPT = "enable-encrypt-flag";
    private static final String KEY_NOTIFY_ENCRYPT_TYPE = "notify-encrypt-type";
    private static final String KEY_ENABLE_MMAP_TYEPE = "enable_mmap_type";
    private static final String KEY_DISABLE_CIRCULAR_DELETE = "disable-circular-delete";
    //Reduce  fps only for Recording
    private static final String KEY_REDUCE_REC_FPS = "reduce-recorde-fps";
    //set the number of cycle to delete file
    private static final String KEY_DELETE_FILE_NUM = "delete-file-num";

    //some setting for sub video data
    private static final String KEY_SUB_VIDEO_SIZE = "videocb-size";
    private static final String KEY_SUB_VIDEO_FRAME_RATE = "sub-video-frame-rate";
    private static final String KEY_SUB_VIDEO_FRAME_ENABLED= "sub-video-frame-enabled";
    private static final String KEY_SUB_VIDEO_PARAM_ENCODING_BITRATE = "sub-video-param-encoding-bitrate";
    private static final String KEY_SUB_VIDEO_OUTPUT_FILE = "sub-video-output-file";
    private static final String KEY_SUB_VIDEO_OUTPUT_FILE_NAME = "sub-video-output-file-name";

    private static final String KEY_SUB_STREAM_SHARE_AUDIO = "sub_stream_share_audio";

    //some setting for AVM(single camera) video data
    private static final String KEY_AVM_VIDEO_SIZE = "avm-video-size";
    private static final String KEY_AVM_VIDEO_FRAME_RATE = "avm-video-frame-rate";
    private static final String KEY_AVM_VIDEO_FRAME_ENABLED= "avm-video-frame-enabled";
    private static final String KEY_AVM_VIDEO_PARAM_ENCODING_BITRATE = "avm-video-param-encoding-bitrate";
    private static final String KEY_AVM_VIDEO_OUTPUT_FILE = "avm-video-output-file";
    private static final String KEY_AVM_VIDEO_OUTPUT_FILE_NAME = "avm-video-output-file-name";
    private static final String KEY_AVM_DISPLAY = "avm-display";

    //AVM calibration
    private static final String KEY_AVM_CALIBRATION_MODE = "avm-calibration-mode";
    private static final String KEY_AVM_CALIBRATION = "avm-calibration";


    //for new watermark function settings
    private static final String KEY_PREVIEW_WATERMARK_TEXT_MODE="preview-watermark-text-mode";
    private static final String KEY_RECORD_WATERMARK_TEXT_MODE="record-watermark-text-mode";
    private static final String KEY_PICTURE_WATERMARK_TEXT_MODE = "picture-watermark-text-mode";
    private static final String KEY_VIDEOCB_WATERMARK_TEXT_MODE = "videocb-watermark-text-mode";
    private static final String KEY_WATERMARK_AREA="watermark-area";
    private static final String KEY_WATERMARK_TEXT="watermark-text";
    private static final String KEY_WATERMARK_AREA_EX1 = "watermark-area-ex1";
    private static final String KEY_WATERMARK_TEXT_EX1 = "watermark-text-ex1";
    private static final String KEY_WATERMARK_AREA_EX2 = "watermark-area-ex2";
    private static final String KEY_WATERMARK_TEXT_EX2 = "watermark-text-ex2";
    private static final String KEY_WATERMARK_AREA_EX3 = "watermark-area-ex3";
    private static final String KEY_WATERMARK_TEXT_EX3 = "watermark-text-ex3";
    private static final String KEY_WATERMARK_TEXT_SIZE="watermark-text-size";
    private static final String KEY_WATERMARK_TEXT_X="watermark-text-x";
    private static final String KEY_WATERMARK_TEXT_Y="watermark-text-y";
    private static final String KEY_WATERMARK_TEXT_COLOR="watermark-text-color";
    private static final String KEY_WATERMARK_FONT_FILE = "watermark-font-file";
    private static final String KEY_WATERMARK_TIMESTAMP_FORMAT="watermark-timestamp-format";
    private static final String KEY_WATERMARK_TIME_MS="watermark-timestamp-ms";
    private static final String KEY_PREVIEW_WATERMARK_IMG_MODE = "preview-watermark-img-mode";
    private static final String KEY_RECORD_WATERMARK_IMG_MODE = "record-watermark-img-mode";
    private static final String KEY_PICTURE_WATERMARK_IMG_MODE = "picture-watermark-img-mode";
    private static final String KEY_VIDEOCB_WATERMARK_IMG_MODE = "videocb-watermark-img-mode";
    private static final String KEY_WATERMARK_IMG_PATH = "watermark-img-path";
    private static final String KEY_WATERMARK_IMG_AREA = "watermark-img-area";
    private static final String KEY_WATERMARK_TEXT_MODE = "watermark-text-mode";
    private static final String KEY_WATERMARK_IMG_MODE = "watermark-img-mode";
    //text watermark shadow
    private static final String KEY_WATERMARK_TEXT_SHADOW_RADIUS="watermark-text-shadow-radius";
    private static final String KEY_WATERMARK_TEXT_SHADOW_XOFFSET="watermark-text-shadow-xoffset";
    private static final String KEY_WATERMARK_TEXT_SHADOW_YOFFSET="watermark-text-shadow-yoffset";
    private static final String KEY_WATERMARK_TEXT_SHADOW_COLOR="watermark-text-shadow-color";
    //add_watermark_preview_enable
    private static final String KEY_WATERMARK_PREVIEW_EN="watermark-preview-en";

    private static final String KEY_WATERMARK_CAR_MODE = "watermark-car-mode";
    private static final String KEY_WATERMARK_CAR_INFO = "watermark-car-info";
    private static final String KEY_WATERMARK_CAR_VIN  = "watermark-car-vin";

    // Values for white balance settings
    public static final String WHITE_BALANCE_AUTO = "auto";
    public static final String WHITE_BALANCE_INCANDECSENT = "incandescent";
    public static final String WHITE_BALANCE_FLUORESCENT = "fluorescent";
    public static final String WHITE_BALANCE_WARM_FLUORESCENT = "warm-fluorescent";
    public static final String WHITE_BALANCE_DAYLIGHT = "daylight";
    public static final String WHITE_BALANCE_CLOUDY_DAYLIGHT = "cloudy-daylight";
    public static final String WHITE_BALANCE_TWILIGHT = "twilight";
    public static final String WHITE_BALANCE_SHADE = "shade";
    public static final String WHITE_BALANCE_TUNGSTEN = "tungsten";

    // some apis of Parameters  from Camera.java
    private static final String KEY_PREVIEW_FRAME_RATE = "preview-frame-rate";
    private static final String KEY_PREVIEW_FPS_RANGE = "preview-fps-range";
    private static final String KEY_SCENE_MODE = "scene-mode";
    private static final String KEY_EIS_MODE = "eis-mode";
    private static final String KEY_VIDEO_STABILIZATION = "video-stabilization";
    private static final String KEY_VIDEO_STABILIZATION_SUPPORTED = "video-stabilization-supported";
    private static final String KEY_EFFECT = "effect";
    private static final String KEY_VIDCB_FRAMERATE = "vidcb-frame-rate"; //set video callback framerate
    private static final String KEY_DROP_CAMERA_FRAME = "drop-camera-frame"; //drop camera frame
    private static final String KEY_FRAME_TIMESTAMP_FOR_PRV = "prv-timestamp";//timestamp with preview yuv frame
    private static final String KEY_FRAME_TIMESTAMP_FOR_REC = "rec-timestamp";//timestamp with rec frame in ts file
    private static final String KEY_YUV_CALLBACK    = "yuv-callback-type";
    //
    private static final String KEY_PICTURE_SIZE = "picture-size";
    private static final String KEY_JPEG_THUMBNAIL_SIZE = "jpeg-thumbnail-size";
    private static final String KEY_JPEG_THUMBNAIL_WIDTH = "jpeg-thumbnail-width";
    private static final String KEY_JPEG_THUMBNAIL_HEIGHT = "jpeg-thumbnail-height";
    private static final String KEY_JPEG_THUMBNAIL_QUALITY = "jpeg-thumbnail-quality";
    private static final String KEY_JPEG_QUALITY = "jpeg-quality";
    private static final String KEY_RECORDING_HINT = "recording-hint";
    private static final String KEY_ROTATION = "rotation";
    private static final String KEY_VIDEO_ROTATION = "video-rotation";
    private static final String KEY_PREVIEW_MIRROR = "preview-mirror";
    //
    private static final String KEY_GPS_LATITUDE = "gps-latitude";
    private static final String KEY_GPS_LONGITUDE = "gps-longitude";
    private static final String KEY_GPS_ALTITUDE = "gps-altitude";
    private static final String KEY_GPS_TIMESTAMP = "gps-timestamp";
    private static final String KEY_GPS_PROCESSING_METHOD = "gps-processing-method";

    // HDR
    public static final String KEY_VIDEO_HDR = "video-hdr";
    public static final String KEY_VIDEO_HDR_MODE = "video-hdr-mode";
    public static final String VIDEO_HDR_MODE_IVHDR = "video-hdr-mode-ivhdr";
    public static final String VIDEO_HDR_MODE_MVHDR = "video-hdr-mode-mvhdr";

    //warp
    public static final String KEY_WARP_MODE = "warp-enable";

    public static final String KEY_CAM_MODE = "mtk-cam-mode";

    public static final String KEY_AUDIO_SOURCE = "audio-source";

    //sub recorder or ADAS2
    public static final String KEY_EXT1_SIZE = "ext1-size";
    public static final String KEY_EXT1_FMT = "ext1-format";

    //ADAS1
    public static final String KEY_EXT0_SIZE = "ext0-size";
    public static final String KEY_EXT0_FMT = "ext0-format";

    //EXTERNAL2  avm callback
    public static final String KEY_EXT2_SIZE = "ext2-size";
    public static final String KEY_EXT2_FMT = "ext2-format";

    //EXTERNAL3   avm back callback
    public static final String KEY_EXT3_SIZE = "ext3-size";
    public static final String KEY_EXT3_FMT = "ext3-format";

    // KEY_EXT_SIZE an KEY_EXT_FMT

    // CALIB PARA
    public static final String KEY_AVM_CALIB_INTREAR = "avm_calib_intrear";
    public static final String KEY_AVM_CALIB_POSREAR = "avm_calib_posrear";
    public static final String KEY_AVM_CALIB_DISTCOEFFREAR = "avm_calib_distcoeffrear";

    public static final String KEY_RECORD_ID = "record-id";

    // vehicle info
    public static final String KEY_VEHICLE_LONGITUDE = "vehicle-longitude";
    public static final String KEY_VEHICLE_LATITUDE = "vehicle-latitude";
    public static final String KEY_VEHICLE_SPEED = "vehicle-speed";
    public static final String KEY_VEHICLE_GEAR_STATUS = "vehicle-gear-status";
    public static final String KEY_VEHICLE_ACCELERATOR = "vehicle-accelerator";
    public static final String KEY_VEHICLE_BRAKE = "vehicle-brake";
    public static final String KEY_VEHICLE_LEFT_SIGNAL = "vehicle-left-signal";
    public static final String KEY_VEHICLE_RIGHT_SIGNAL = "vehicle-right-signal";
    public static final String KEY_VEHICLE_SAFETYBELT = "vehicle-safetybelt";
    public static final String KEY_VEHICLE_ADAS_STATUS = "vehicle-adas-status";
    public static final String KEY_VEHICLE_LANUAGE = "vehicle-lanuage";

    public  static final String[] KEY_EXT_SURFACE_SIZE = {
        "ext0-size",
        "ext1-size",
        "ext2-size",
        "ext3-size",
        "ext4-size",
    };

    public  static final String[] KEY_EXT_SURFACE_FORMAT = {
        "ext0-format",
        "ext1-format",
        "ext2-format",
        "ext3-format",
        "ext4-format",
    };

    public  static final String[] KEY_EXT_SIZE = {
        "ext0-size",
        "ext1-size",
        "ext2-size",
        "ext3-size",
    };

    public  static final String[] KEY_EXT_FORMAT = {
        "ext0-format",
        "ext1-format",
        "ext2-format",
        "ext3-format",
    };


    private static final CameraMetadataNative.Key<int[]> CAMERA_FRAME_DROP_POLICY =
            new CameraMetadataNative.Key<int[]>("com.mediatek.streamingfeature.frameDropPolicy", int[].class);

    private static final String KEY_AE_BLOCK_Y_MODE = "ae-blocky-mode"; //drop camera frame
    private static final CameraMetadataNative.Key<Integer> AE_BLOCK_Y_MODE =
            new CameraMetadataNative.Key<Integer>("com.mediatek.3afeature.aeBlockYvaluesGetMode", int.class);

    //define in VendorTagTable.h
    private static final CameraMetadataNative.Key<int[]> AE_BLOCK_Y_VALUES =
            new CameraMetadataNative.Key<int[]>("com.mediatek.3afeature.aeBlockYvalues", int[].class);
    static final int  AeBlockValueSize = 25;


    private static final CameraMetadataNative.Key<Integer> WATERMARK_TEXT_ENABLE =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkTextEnable", int.class);

    private static final CameraMetadataNative.Key<Float> WATERMARK_TEXT_SIZE =
            new CameraMetadataNative.Key<Float>("com.mediatek.watermark.watermarkTextSize", float.class);

    private static final CameraMetadataNative.Key<Integer> WATERMARK_TEXT_COLOR =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkTextColor", int.class);

    private static final CameraMetadataNative.Key<String> WATERMARK_TEXT_FONTFILE =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkTextFont", String.class);

    private static final CameraMetadataNative.Key<Integer> WATERMARK_TEXT_TIME_AREA =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkTimeArea", int.class);

    private static final CameraMetadataNative.Key<String> WATERMARK_TEXT=
               new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkText", String.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_TEXT_AREA =
               new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkTextArea", int[].class);

    private static final CameraMetadataNative.Key<String> WATERMARK_TEXT_EX1 =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkTextEx1", String.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_TEXT_AREA_EX1 =
            new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkTextAreaEx1", int[].class);

    private static final CameraMetadataNative.Key<String> WATERMARK_TEXT_EX2 =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkTextEx2", String.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_TEXT_AREA_EX2 =
            new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkTextAreaEx2", int[].class);

    private static final CameraMetadataNative.Key<String> WATERMARK_TEXT_EX3 =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkTextEx3", String.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_TEXT_AREA_EX3 =
            new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkTextAreaEx3", int[].class);

    private static final CameraMetadataNative.Key<float[]> WATERMARK_TEXT_POSITION =
            new CameraMetadataNative.Key<float[]>("com.mediatek.watermark.watermarkTextPosition", float[].class);

    private static final CameraMetadataNative.Key<Integer> WATERMARK_IMAGE_ENABLE =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkImageEnable", int.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_IMAGE_AREA =
            new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkImageArea", int[].class);

    private static final CameraMetadataNative.Key<String> WATERMARK_IMAGE_PATH =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkImagePath", String.class);


    private static final CameraMetadataNative.Key<Integer> WATERMARK_TIME_MS =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkTimeMs", int.class);

    private static final CameraMetadataNative.Key<String> WATERMARK_FORMAT =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkFormat", String.class);//add_watermark_Format_interface

    private static final CameraMetadataNative.Key<Integer> WATERMARK_PREVIEW_EN =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.previewEn", int.class); //add_watermark_preview_enable

    private static final CameraMetadataNative.Key<Integer> WATERMARK_CAR_ENABLE =
            new CameraMetadataNative.Key<Integer>("com.mediatek.watermark.watermarkCarEnable", int.class);

    private static final CameraMetadataNative.Key<int[]> WATERMARK_CAR_INFO =
            new CameraMetadataNative.Key<int[]>("com.mediatek.watermark.watermarkCarInfo", int[].class);

    private static final CameraMetadataNative.Key<String> WATERMARK_CAR_VIN =
            new CameraMetadataNative.Key<String>("com.mediatek.watermark.watermarkCarVin", String.class);

    public SpmCamParameters() {
        mMap = new LinkedHashMap<String, String>(128);
    }

    /**
     * Overwrite existing parameters with a copy of the ones from {@code other}.
     * @hide
     */
    public void copyFrom(SpmCamParameters other) {
        if (other == null) {
            throw new NullPointerException("other must not be null");
        }
        synchronized (mMapLock) {
            mMap.putAll(other.mMap);
        }
    }


    /**
     * Takes a flattened string of parameters and adds each one to this
     * Parameters object.
     * <p>
     * The {@link #flatten()} method does the reverse.
     * </p>
     *
     * @param flattened
     *            a String of parameters (key-value paired) that are
     *            semi-colon delimited
     */
    public void unflatten(String flattened) {
        synchronized (mMapLock) {
            mMap.clear();

            TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(';');
            splitter.setString(flattened);

            for (String kv : splitter) {
                int pos = kv.indexOf('=');

                if (pos == -1) {
                    continue;
                }

                String k = kv.substring(0, pos);
                String v = kv.substring(pos + 1);
                mMap.put(k, v);
            }
        }
    }

    /**
     * Creates a single string with all the parameters set in this
     * Parameters object.
     * <p>
     * The {@link #unflatten(String)} method does the reverse.
     * </p>
     *
     * @return a String with all values from this Parameters object, in
     *         semi-colon delimited key-value pairs
     */
    public String flatten() {
        StringBuilder flattened = new StringBuilder(128);

        synchronized (mMapLock) {
            for (String k : mMap.keySet()) {
                flattened.append(k);
                flattened.append("=");
                flattened.append(mMap.get(k));
                flattened.append(";");
            }
        }
        // chop off the extra semicolon at the end
        flattened.deleteCharAt(flattened.length() - 1);
        return flattened.toString();
    }

    public void remove(String key) {
        synchronized (mMapLock) {
            mMap.remove(key);
        }
    }

    // Splits a comma delimited string to an ArrayList of String.
    // Return null if the passing string is null or the size is 0.
    private ArrayList<String> split(String str) {
        if (str == null) return null;

        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(str);
        ArrayList<String> substrings = new ArrayList<String>();

        for (String s : splitter) {
            substrings.add(s);
        }

        return substrings;
    }

    public String get(String key) {
        synchronized (mMapLock) {
            return mMap.get(key);
        }
    }

    public String get(String key, String defaultValue) {
        String value;
        synchronized (mMapLock) {
            value = mMap.get(key);
        }
        if(value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
         * Returns the value of an integer parameter.
         *
         * @param key the key name for the parameter
         * @return the int value of the parameter
         */
    public int getInt(String key) {
        synchronized (mMapLock) {
            return Integer.parseInt(mMap.get(key));
        }
    }

    /**
     * Returns the value of a float parameter
     */
    private float getFloat(String key, float defaultValue) {
        try {
            synchronized (mMapLock) {
                return Float.parseFloat(mMap.get(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    /**
     * Returns the value of an integer parameters.
     */
    int getInt(String key, int defaultValue) {
        try {
            synchronized (mMapLock) {
                return Integer.parseInt(mMap.get(key));
            }
        } catch (NumberFormatException e) {
            //e.printStackTrace();
            Log.w(TAG, "getInt NumberFormatException, use defaultValue :" + defaultValue);
            return defaultValue;
        }
    }

    private void put(String key, String value) {
        /*
         * Remove the key if it already exists.
         *
         * This way setting a new value for an already existing key will
         * always move that key to be ordered the latest in the map.
         */
        synchronized (mMapLock) {
            mMap.remove(key);
            mMap.put(key, value);
        }
    }

    public void set(String key, String value) {
        if(key == null || value == null) {
            Log.e(TAG,"key or value is null, key:" + key + ", value:" + value);
            return;
        }

		if (key.indexOf('=') != -1 || key.indexOf(';') != -1
                || key.indexOf(0) != -1) {
            Log.e(TAG, "Key \"" + key
                  + "\" contains invalid character (= or ; or \\0)");
            return;
        }

        if (value.indexOf('=') != -1 || value.indexOf(';') != -1
                || value.indexOf(0) != -1) {
            Log.e(TAG, "Value \"" + value
                  + "\" contains invalid character (= or ; or \\0)");
            return;
        }

        put(key, value);
    }

    /**
     * Sets an integer parameter.
     *
     * @param key
     *            the key name for the parameter
     * @param value
     *            the int value of the parameter
     */
    public void set(String key, int value) {
        put(key, Integer.toString(value));
    }


    /**
     * Sets an integer parameter.
     *
     * @param key
     *            the key name for the parameter
     * @param value
     *            the float value of the parameter
     */
    public void set(String key, float value) {
        put(key, Float.toString(value));
    }

    // Splits a comma delimited string to an ArrayList of Size.
    // Return null if the passing string is null or the size is 0.
    private ArrayList<Size> splitSize(String str) {
        if (str == null)
            return null;

        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(
            ',');
        splitter.setString(str);
        ArrayList<Size> sizeList = new ArrayList<Size>();

        for (String s : splitter) {
            Size size = strToSize(s);

            if (size != null)
                sizeList.add(size);
        }

        if (sizeList.size() == 0)
            return null;

        return sizeList;
    }

    // Parses a string (ex: "480x320") to Size object.
    // Return null if the passing string is null.
    private Size strToSize(String str) {
        if (str == null)
            return null;

        int pos = str.indexOf('x');

        if (pos != -1) {
            String width = str.substring(0, pos);
            String height = str.substring(pos + 1);
            return new Size(Integer.parseInt(width),
                            Integer.parseInt(height));
        }

        return null;
    }

    private ArrayList<int[]> splitRange(String str) {
        if (str == null || str.charAt(0) != '('
                || str.charAt(str.length() - 1) != ')') {
            Log.e(TAG, "Invalid range list string=" + str);
            return null;
        }

        ArrayList<int[]> rangeList = new ArrayList<int[]>();
        int endIndex, fromIndex = 1;

        do {
            int[] range = new int[2];
            endIndex = str.indexOf("),(", fromIndex);

            if (endIndex == -1) endIndex = str.length() - 1;

            splitInt(str.substring(fromIndex, endIndex), range);
            rangeList.add(range);
            fromIndex = endIndex + 3;
        } while (endIndex != str.length() - 1);

        if (rangeList.size() == 0) return null;

        return rangeList;
    }

    // Splits a comma delimited string to an ArrayList of Integer.
    // Return null if the passing string is null or the size is 0.
    private ArrayList<Integer> splitInt(String str) {
        if (str == null) return null;

        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(str);
        ArrayList<Integer> substrings = new ArrayList<Integer>();

        for (String s : splitter) {
            substrings.add(Integer.parseInt(s));
        }

        if (substrings.size() == 0) return null;

        return substrings;
    }

    private void splitInt(String str, int[] output) {
        if (str == null) return;

        TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(str);
        int index = 0;

        for (String s : splitter) {
            output[index++] = Integer.parseInt(s);
        }
    }

    /**
     * Gets the supported preview sizes.
     *
     * @return a list of Size object. This method will always return a list
     *         with at least one element.
     */
    public List<Size> getSupportedPreviewSizes() {
        StreamConfigurationMap configMap;
        try {
            configMap = mCameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to obtain preview sizes.", ex);
            return new ArrayList<>(0);
        }
        ArrayList<Size> supportedPictureSizes = new ArrayList<>();
        for (android.util.Size androidSize : configMap.getOutputSizes(SurfaceTexture.class)) {
            supportedPictureSizes.add(new Size(androidSize));
        }
        return supportedPictureSizes;
    }

    /**
     * Sets the dimensions for preview pictures. If the preview has already
     * started, applications should stop the preview first before changing
     * preview size.
     *
     *
     * @param width
     *            the width of the pictures, in pixels
     * @param height
     *            the height of the pictures, in pixels
     */
    public void setPreviewSize(int width, int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_PREVIEW_SIZE, v);
    }

    /**
     * Returns the dimensions setting for preview pictures.
     *
     * @return a Size object with the width and height setting for the
     *         preview picture
     */
    public Size getPreviewSize() {
        String pair = get(KEY_PREVIEW_SIZE);
        return strToSize(pair);
    }


    /**
         * <p>Sets the dimensions for pictures.</p>
         *
         * <p>Applications need to consider the display orientation. See {@link
         * #setPreviewSize(int,int)} for reference.</p>
         *
         * @param width  the width for pictures, in pixels
         * @param height the height for pictures, in pixels
         * @see #setPreviewSize(int,int)
         *
         */
    public void setPictureSize(int width, int height) {
        String str = Integer.toString(width) + "x"
                     + Integer.toString(height);
        set(KEY_PICTURE_SIZE, str);
    }


    /**
         * Returns the dimension setting for pictures.
         *
         * @return a Size object with the height and width setting
         *          for pictures
         */
    public Size getPictureSize() {
        String str = get(KEY_PICTURE_SIZE);
        return strToSize(str);
    }

    /**
         * Gets the supported picture sizes.
         *
         * @return a list of supported picture sizes. This method will always
         *         return a list with at least one element.
         */
    public List<Size> getSupportedPictureSizes(int imageFormat) {
        StreamConfigurationMap configMap;
        try {
            configMap = mCameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to obtain picture sizes.", ex);
            return new ArrayList<>(0);
        }

        ArrayList<Size> supportedPictureSizes = new ArrayList<>();
        for (android.util.Size androidSize : configMap.getOutputSizes(imageFormat)) {
            supportedPictureSizes.add(new Size(androidSize));
        }
        return supportedPictureSizes;
    }

    /**
         * <p>Sets the dimensions for EXIF thumbnail in Jpeg picture. If
         * applications set both width and height to 0, EXIF will not contain
         * thumbnail.</p>
         *
         * <p>Applications need to consider the display orientation. See {@link
         * #setPreviewSize(int,int)} for reference.</p>
         *
         * @param width  the width of the thumbnail, in pixels
         * @param height the height of the thumbnail, in pixels
         * @see #setPreviewSize(int,int)
         */
    public void setJpegThumbnailSize(int width, int height) {
        set(KEY_JPEG_THUMBNAIL_WIDTH, width);
        set(KEY_JPEG_THUMBNAIL_HEIGHT, height);
    }

    /**
         * Returns the dimensions for EXIF thumbnail in Jpeg picture.
         *
         * @return a Size object with the height and width setting for the EXIF
         *         thumbnails
         */
    public Size getJpegThumbnailSize() {
        return new Size(getInt(KEY_JPEG_THUMBNAIL_WIDTH),
                        getInt(KEY_JPEG_THUMBNAIL_HEIGHT));
    }

    /**
         * Gets the supported jpeg thumbnail sizes.
         *
         * @return a list of Size object. This method will always return a list
         *         with at least two elements. Size 0,0 (no thumbnail) is always
         *         supported.
         */
    public List<Size> getSupportedJpegThumbnailSizes() {
        String str = get(KEY_JPEG_THUMBNAIL_SIZE + SUPPORTED_VALUES_SUFFIX);
        return splitSize(str);
    }

    /**
         * Sets the quality of the EXIF thumbnail in Jpeg picture.
         *
         * @param quality the JPEG quality of the EXIF thumbnail. The range is 1
         *                to 100, with 100 being the best.
         */
    public void setJpegThumbnailQuality(int quality) {
        set(KEY_JPEG_THUMBNAIL_QUALITY, quality);
    }

    /**
         * Returns the quality setting for the EXIF thumbnail in Jpeg picture.
         *
         * @return the JPEG quality setting of the EXIF thumbnail.
         */
    public int getJpegThumbnailQuality() {
        return getInt(KEY_JPEG_THUMBNAIL_QUALITY);
    }

    /**
         * Sets Jpeg quality of captured picture.
         *
         * @param quality the JPEG quality of captured picture. The range is 1
         *                to 100, with 100 being the best.
         */
    public void setJpegQuality(int quality) {
        set(KEY_JPEG_QUALITY, quality);
    }

    /**
         * Returns the quality setting for the JPEG picture.
         *
         * @return the JPEG picture quality setting.
         */
    public int getJpegQuality() {
        return getInt(KEY_JPEG_QUALITY);
    }


    /**
      * Sets image description of captured picture,This will be stored in JPEG EXIF header.
      *
      * @desc image description.
      */
    public void setImageDesc(String desc) {
        set(KEY_JPEG_IMAGE_DESC, desc);
    }

    /**
         * Returns the image description of captured picture.
         *
         * @return the image description.
         */
    public String getImageDesc() {
        return get(KEY_JPEG_IMAGE_DESC);
    }


    /**
      * Sets the rate at which preview frames are received. This is the
      * target frame rate. The actual frame rate depends on the driver.
      *
      * @param fps the frame rate (frames per second)
      * @deprecated replaced by {@link #setPreviewFpsRange(int,int)}
     */
    @Deprecated
    public void setPreviewFrameRate(int fps) {
        set(KEY_PREVIEW_FRAME_RATE, fps);
    }

    /**
      * Returns the setting for the rate at which preview frames are
      * received. This is the target frame rate. The actual frame rate
      * depends on the driver.
      *
      * @return the frame rate setting (frames per second)
      * @deprecated replaced by {@link #getPreviewFpsRange(int[])}
      */
    @Deprecated
    public int getPreviewFrameRate() {
        return getInt(KEY_PREVIEW_FRAME_RATE);
    }

    /**
      * Gets the supported preview frame rates.
      *
      * @return a list of supported preview frame rates. null if preview
      *		 frame rate setting is not supported.
      * @deprecated replaced by {@link #getSupportedPreviewFpsRange()}
    */
    @Deprecated
    public List<Integer> getSupportedPreviewFrameRates() {
        String str = get(KEY_PREVIEW_FRAME_RATE + SUPPORTED_VALUES_SUFFIX);
        return splitInt(str);
    }

    /**
        * Sets the minimum and maximum preview fps. The minimum and
        * maximum preview fps must be one of the elements from {@link
        * #getSupportedPreviewFpsRange}.
        *
        * @param min the minimum preview fps (scaled by 1000).
        * @param max the maximum preview fps (scaled by 1000).
       */
    public void setPreviewFpsRange(int min, int max) {
        set(KEY_PREVIEW_FPS_RANGE, "" + min + "," + max);
        mPreviwFpsRange =  new Range<>(min, max);
    }


    /**
    * Returns the current minimum and maximum preview fps. The values are
    * one of the elements returned by {@link #getSupportedPreviewFpsRange}.
    *
    * @return range the minimum and maximum preview fps (scaled by 1000).
    */
    public void getPreviewFpsRange(int[] range) {
        if (range == null || range.length != 2) {
            throw new IllegalArgumentException(
                "range must be an array with two elements.");
        }

        splitInt(get(KEY_PREVIEW_FPS_RANGE), range);
    }

    /**
         * Gets the supported preview fps (frame-per-second) ranges. Each range
         * contains a minimum fps and maximum fps. If minimum fps equals to
         * maximum fps, the camera outputs frames in fixed frame rate. If not,
         * the camera outputs frames in auto frame rate.
         *
         * @return a list of supported preview fps ranges. This method returns a
         *         list with at least one element. Every element is an int array
         *         of two values - minimum fps and maximum fps. The list is
         *         sorted from small to large (first by maximum fps and then
         *         minimum fps).
         */
    public List<int[]> getSupportedPreviewFpsRange() {
        //String str = get(KEY_PREVIEW_FPS_RANGE + SUPPORTED_VALUES_SUFFIX);
        //return splitRange(str);
        ArrayList<int[]> rangeList = new ArrayList<int[]>();
        Range<Integer>[] fpsRange = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        for(Range<Integer> it : fpsRange) {
            //rangeList.add(MetadataUtil.convertAeFpsRangeToLegacy(it));
            int[] legacyFps = new int[2];
            legacyFps[Camera.Parameters.PREVIEW_FPS_MIN_INDEX] = it.getLower();
            legacyFps[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] = it.getUpper();
			rangeList.add(legacyFps);
        }
        return rangeList;
    }

    /**
     * Set the video size.
     */
    public void setVideoSize(int width, int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_VIDEO_SIZE, v);
    }

    /**
     * Returns the dimensions setting for video pictures.
     *
     * @return a Size object with the width and height setting for the video
     *         picture
     */
    public Size getVideoSize() {
        String pair = get(KEY_VIDEO_SIZE);
        return strToSize(pair);
    }

    /**
      * Set the sub video size.
      */
    public void setSubVideoSize(int width, int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_SUB_VIDEO_SIZE, v);
    }

    /**
     * Returns the dimensions setting for sub video pictures.
     *
     * @return a Size object with the width and height setting for the video
     *         picture
     */
    public Size getSubVideoSize() {
        String pair = get(KEY_SUB_VIDEO_SIZE);
        return strToSize(pair);
    }

    /**
      * Set the AVM(single camera) video size.
      */
    public void setAVMVideoSize(int width, int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_AVM_VIDEO_SIZE, v);
    }

    /**
     * Returns the dimensions setting for AVM(single camera) video pictures.
     *
     * @return a Size object with the width and height setting for the video
     *         picture
     */
    public Size getAVMVideoSize() {
        String pair = get(KEY_AVM_VIDEO_SIZE);
        return strToSize(pair);
    }

    /**
     * Gets the supported preview sizes.
     *
     * @return a list of Size object.
     */
    public List<Size> getSupportedVideoSizes() {
        String values = get(KEY_VIDEO_SIZE + SUPPORTED_VALUES_SUFFIX);
        return splitSize(values);
    }

    /*
    * <P>used to set black-and-white images.</p>
    * @see SpmCamParameters#setSceneMode
    */
    public static final int MTK_CONTROL_SCENE_MODE_DUMMY_NIGHT = 129;

    /**
         * Gets the current scene mode setting.
         *
         * @return one of SCENE_MODE_XXX string constant. null if scene mode
         *         setting is not supported.
         */
    public String getSceneMode() {
        return get(KEY_SCENE_MODE);
    }

    /**
         * Sets the scene mode. Changing scene mode may override other
         * parameters (such as flash mode, focus mode, white balance). For
         * example, suppose originally flash mode is on and supported flash
         * modes are on/off. In night scene mode, both flash mode and supported
         * flash mode may be changed to off. After setting scene mode,
         * applications should call getParameters to know if some parameters are
         * changed.
         *
         * @param value scene mode.
         * @see #getSceneMode()
         */
    public void setSceneMode(int value) {
        set(KEY_SCENE_MODE, value);
    }

    /**
         * Gets the supported scene modes.
         *
         * @return a list of supported scene modes. null if scene mode setting
         *         is not supported.
         * @see #getSceneMode()
         */
    public List<String> getSupportedSceneModes() {
        String str = get(KEY_SCENE_MODE + SUPPORTED_VALUES_SUFFIX);
        return split(str);
    }


    /**
     * @hide
     * Gets the current Eis mode setting (on/off)
     * @ return one of EIS_MODE_xxx string constant.
     */
    public String getEisMode() {
        return get(KEY_EIS_MODE);
    }
    /**
     * @hide
     */
    public void setEisMode(String eis) {
        set(KEY_EIS_MODE, eis);
    }
    /**
     * @hide
     */
    public List<String> getSupportedEisMode() {
        String str = get(KEY_EIS_MODE + SUPPORTED_VALUES_SUFFIX);
        return split(str);
    }
    /**
         * <p>Enables and disables video stabilization. Use
         * {@link #isVideoStabilizationSupported} to determine if calling this
         * method is valid.</p>
         *
         * <p>Video stabilization reduces the shaking due to the motion of the
         * camera in both the preview stream and in recorded videos, including
         * data received from the preview callback. It does not reduce motion
         * blur in images captured with
         * {@link CameraDevice#takePicture takePicture}.</p>
         *
         * <p>Video stabilization can be enabled and disabled while preview or
         * recording is active, but toggling it may cause a jump in the video
         * stream that may be undesirable in a recorded video.</p>
         *
         * @param toggle Set to true to enable video stabilization, and false to
         * disable video stabilization.
         * @see #isVideoStabilizationSupported()
         * @see #getVideoStabilization()
         */
    public void setVideoStabilization(boolean toggle) {
        set(KEY_VIDEO_STABILIZATION, toggle ? TRUE : FALSE);
    }

    /**
         * Get the current state of video stabilization. See
         * {@link #setVideoStabilization} for details of video stabilization.
         *
         * @return true if video stabilization is enabled
         * @see #isVideoStabilizationSupported()
         * @see #setVideoStabilization(boolean)
         */
    public boolean getVideoStabilization() {
        String str = get(KEY_VIDEO_STABILIZATION);
        return TRUE.equals(str);
    }

    /**
         * Returns true if video stabilization is supported. See
         * {@link #setVideoStabilization} for details of video stabilization.
         *
         * @return true if video stabilization is supported
         * @see #setVideoStabilization(boolean)
         * @see #getVideoStabilization()
         */
    public boolean isVideoStabilizationSupported() {
        String str = get(KEY_VIDEO_STABILIZATION_SUPPORTED);
        return TRUE.equals(str);
    }


    public void setProtectRecordingMode(boolean isMotionMode) {
        if (isMotionMode) {
            set(KEY_MOTION_DETECT_MODE, Integer.toString(1));
        } else {
            set(KEY_MOTION_DETECT_MODE, Integer.toString(0));
        }
    }
    public void setVideoEncodingBitRate(int mEncodingBitRate) {
        set(KEY_VIDEO_PARAM_ENCODING_BITRATE,Integer.toString(mEncodingBitRate));
        set(KEY_USER_SET_VIDEO_ENCODEBITRATE,Integer.toString(1));
    }





    /**
     * Sets the offset of water mark
     *
     * @param offsetX
     *            The offset of X-axis
     * @param offsetY
     *            The offset of Y-axis
     */
    public void setWaterMarkOffset(int offsetX, int offsetY) {
        String str = Integer.toString(offsetX) + ","
                     + Integer.toString(offsetY);
        set(KEY_WATERMARK_OFFSET, str);
    }

    /**
     * Sets the offset of water mark timer characters
     *
     * @param offsetX
     *            The offset of X-axis
     * @param offsetY
     *            The offset of Y-axis
     */
    public void setWaterMarkTimerOffset(int offsetX, int offsetY) {
        String str = Integer.toString(offsetX) + ","
                     + Integer.toString(offsetY);
        set(KEY_WATERMARK_TIMER_OFFSET, str);
    }

    /**
     * Sets the address of water mark data
     *
     * @param address
     *            The address of water mark data in the buffer
     */
    public void setWaterMarkDataAddress(int address) {
        set(KEY_WATERMARK_ADDRESS, Integer.toString(address));
    }

    /**
     * Sets the size of water mark bitmap
     *
     * @param width
     *            The width of water mark bitmap
     * @param height
     *            The height of water mark bitmap
     */
    public void setWaterMarkSize(int width, int height) {
        String str = Integer.toString(width) + "x"
                     + Integer.toString(height);
        set(KEY_WATERMARK_SIZE, str);
    }

    /**
     * Sets the file path to storing the water mark data
     *
     * @param path
     *            The file path of storing the water mark data
     */
    public void setWaterMarkDataPath(String path) {
        set(KEY_WATERMARK_DATA_PATH, path);
    }

    /**
     * Sets the owner who using water mark APP should set the ownerName to
     * carcorderservice when APP opens the water mark AAnd should set the
     * ownerName to null when APP closes the water mark
     *
     * @param ownerName
     *            owner name should be carcorderservice when opening water
     *            mark owner name should be null when closing water mark
     */
    public void setWaterMarkOwner(String ownerName) {
        set(KEY_WATERMARK_OWNER_NAME, ownerName);
    }

    /**
     * Sets the white balance. Changing the setting will release the
     * auto-white balance lock. It is recommended not to change white
     * balance and AWB lock at the same time.
     *
     * @param value
     *            new white balance.
     * @see #getWhiteBalance()
     * @see #setAutoWhiteBalanceLock(boolean)
     */
    public void setWhiteBalance(String value) {
        String oldValue = get(KEY_WHITE_BALANCE);
        if (same(value, oldValue)) return;
        set(KEY_WHITE_BALANCE, value);
        set(KEY_AUTO_WHITEBALANCE_LOCK, FALSE);
    }

    private boolean same(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 != null && s1.equals(s2)) return true;
        return false;
    }

    /**
     * Gets the current white balance setting.
     *
     * @return current white balance. null if white balance setting is not
     *         supported.
     * @see #WHITE_BALANCE_AUTO
     * @see #WHITE_BALANCE_INCANDESCENT
     * @see #WHITE_BALANCE_FLUORESCENT
     * @see #WHITE_BALANCE_WARM_FLUORESCENT
     * @see #WHITE_BALANCE_DAYLIGHT
     * @see #WHITE_BALANCE_CLOUDY_DAYLIGHT
     * @see #WHITE_BALANCE_TWILIGHT
     * @see #WHITE_BALANCE_SHADE
     */
    public String getWhiteBalance() {
        return get(KEY_WHITE_BALANCE);
    }

    /**
          * Gets the supported white balance.
          *
          * @return a list of supported white balance. null if white balance
          *         setting is not supported.
          * @see #getWhiteBalance()
          */
    public List<String> getSupportedWhiteBalance() {
        int[] modeEnums = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        List<String> modeNames = convertEnumToString(modeEnums);

        return modeNames;
    }

    /**
     * Scene mode enum value.
     */
    enum ModeEnum {
        OFF(0),
        AUTO(1),
        INCANDESCENT(2),
        FLUORESCENT(3),
        WARM_FLUORESCENT(4),
        DAYLIGHT(5),
        CLOUDY_DAYLIGHT(6),
        TWILIGHT(7),
        SHADE(8);

        private int mValue = 0;
        ModeEnum(int value) {
            this.mValue = value;
        }

        /**
         * Get enum value which is in integer.
         *
         * @return The enum value.
         */
        public int getValue() {
            return this.mValue;
        }

        /**
         * Get enum name which is in string.
         *
         * @return The enum name.
         */
        public String getName() {
            return this.toString();
        }
    }
    private List<String> convertEnumToString(int[] enumIndexs) {
        ModeEnum[] modes = ModeEnum.values();
        List<String> names = new ArrayList<>(enumIndexs.length);
        for (int i = 0; i < enumIndexs.length; i++) {
            int enumIndex = enumIndexs[i];
            if (modes == null) {
                return null;
            }
            for (ModeEnum mode : modes) {
                if (mode.getValue() == enumIndex) {
                    String name = mode.getName().replace('_', '-').toLowerCase(Locale.ENGLISH);
                    names.add(name);
                    break;
                }
            }
        }
        return names;
    }

    private int convertStringToEnum(String value) {
        int enumIndex = 0;
        ModeEnum[] modes = ModeEnum.values();
        if (modes == null) {
            return enumIndex;
        }
        for (ModeEnum mode : modes) {
            String modeName = mode.getName().replace('_', '-').toLowerCase(Locale.ENGLISH);
            if (modeName.equalsIgnoreCase(value)) {
                enumIndex = mode.getValue();
            }
        }
        return enumIndex;
    }


    /**
     * Gets the current exposure compensation index
     *
     * @return current exposure compensation index, The range is
     *         {@link #getMinExposureCompensation} to
     *         {@link #getMaxExposureCompensation}. 0 means exposure is not
     *         adjusted.
     */
    public int getExposureCompensation() {
        return getInt(KEY_EXPOSURE_COMPENSATION, 0);
    }

    /**
     * Sets the auto-white balance lock state.
     */
    public void setAutoWhiteBalanceLock(boolean toggle) {
        set(KEY_AUTO_WHITEBALANCE_LOCK, toggle ? TRUE : FALSE);
    }

    /**
     * Sets the exposure compensation index.
     *
     * @param value
     *            exposure compensation index. The valid value range is from
     *            {@link #getMinExposureCompensation} (inclusive) to
     *            {@link #getMaxExposureCompensation} (inclusive). 0 means
     *            exposure is not adjusted. Application should call
     *            getMinExposureCompensation and getMaxExposureCompensation
     *            to know if exposure compensation is supported.
     */
    public void setExposureCompensation(int value) {
        set(KEY_EXPOSURE_COMPENSATION, value);
    }

    /**
     * Get the maximum exposure compensation index.
     *
     * @return maximum exposure compensation index (>= 0). If both this
     *         method and {@link #getMinExposureCompensation} return 0,
     *         exposure compensation is not supported.
     */
    public int getMaxExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        Range<Integer> compensationRange =
            mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getUpper();
    }

    /**
     * Get the minimum exposure compensation index.
     *
     * @return minimum exposure compensation index (>= 0). If both this
     *         method and {@link #getMaxExposureCompensation} return 0,
     *         exposure compensation is not supported.
     */
    public int getMinExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        Range<Integer> compensationRange =
            mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getLower();
    }

    /**
     * Get the exposure compensation step.
     *
     * @return exposure compensation step.
     */
    public float getExposureCompensationStep() {
        if (!isExposureCompensationSupported()) {
            return -1.0f;
        }
        Rational compensationStep = mCameraCharacteristics.get(
                                        CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        return (float) compensationStep.getNumerator() / compensationStep.getDenominator();
    }

    /**
     * Sets the opened camera id.
     *
     * @param value
     *            the opened camera id
     */
    public void setCameraId(int value) {
        set(KEY_VIDEO_PARAM_CAMERA_ID, value);
    }

    public void setCameraId(String value) {
        set(KEY_VIDEO_PARAM_CAMERA_ID, value);
    }

    /**
     * Set Sdcard or TF card  free size limit
     *
     * @param limitSize   The unit is mega byte
     */
    public void setFreeSizeLimit(int limitSize) {

        set(KEY_FREE_SIZE_LIMIT, limitSize);
    }

    /**
     * Sets the dimensions for adas video frame. If the adas has already
     * started, applications should stop the adas first before changing
     * video frame size.
     *
     * @param width
     *            the width of video frame for adas, in pixels
     * @param height
     *            the height of video frame for adas, in pixels
     */
    public void setAdasSize(int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_ADAS_SIZE, v);
    }

    /**
          * Sets the adas-end-point
          *
          * @param value
          *
          */
    public void setAdasEndPoint(int value) {
        set(KEY_ADAS_END_POINT, value);
    }

    /**
          * Sets the adas-scale-factor
          *
          * @param value
          *
          */
    public void setAdasScaleFactor(float value) {
        set(KEY_ADAS_SCALE_FACTOR, value);
    }

    /**
          * Sets the adas-focal-length
          *
          * @param value
          *
          */
    public void setAdasFocalLength(float value) {
        set(KEY_ADAS_FOCAL_LENGTH, value);
    }

    /**
          * Sets the imei number  for adas
          *
          * @param imei
          *
          */
    public void setImeiNumber(String imei) {
        set(KEY_IMEI_NUMBER, imei);
    }

    /**
      * whether show text watermark for preview, record vieo, picture(normal capture and vss) and video data callback or not
          * The below APIs {@link #setWatermarkArea}, {@link #setWatermarkTextSize}, {@link #setWatermarkTextSize}
          * {@link #setWatermarkTextPosition} should be invoked before
          * And either the {@link #setWatermarkTimestampFormat} or the {@link #setWatermarkText} should be invoked before
          * The {@link #setWatermarkTextColor} is optional
          *
      * @param enable if true show watermark on preview pictures
      */
    public void enableWatermarkText(boolean enable) {
        if(enable) {
            set(KEY_WATERMARK_TEXT_MODE,"on");
        } else {
            set(KEY_WATERMARK_TEXT_MODE,"off");
        }
    }
    /**
       * enable car watermark, it is different normal watermark;
       * car watermark include car info
    */
    public void enableWateramrkCar(boolean enable) {
        if(enable) {
            set(KEY_WATERMARK_CAR_MODE,"on");
        } else {
            set(KEY_WATERMARK_CAR_MODE,"off");
        }
    }
    /**
       * set car watermark info, like speed, acc status...
    */
    public void setWatermarkCarInfo(WatermarkCarInfo wmInfo) {
        String carInfo="("+wmInfo.rec       + "," + wmInfo.vcStatus   + "," + wmInfo.gearStatus + ","
                          +wmInfo.speed     + "," + wmInfo.ccSpeed    + "," + wmInfo.accStatus  + ","
                          +wmInfo.nnpStatus + "," + wmInfo.acceStatus + "," + wmInfo.bpStatus   + ","
                          +wmInfo.absStatus + "," + wmInfo.lsStatus   + "," + wmInfo.rsStatus   + ","
                          +wmInfo.hblStatus + "," + wmInfo.dblStatus  + "," + wmInfo.fflStatus  + ","
                          +wmInfo.msbStatus + "," + wmInfo.csbStatus  + "," + wmInfo.temp       + ","
                          +wmInfo.tempDec + ")";
        set(KEY_WATERMARK_CAR_INFO, carInfo);
        set(KEY_WATERMARK_CAR_VIN,  wmInfo.vin);
    }


    /**
      * set watermart showing area
      * @param left
      * @param top
      * @param right
      * @param bottom
      */
    public void setWatermarkTextArea(int left,int top,int right,int bottom) {
        String area="("+left+","+top+","+right+","+bottom+",1)";
        set(KEY_WATERMARK_AREA,area);
    }

    /**
      * set watermark
      * The time string in the format to be set by {@link #setWatermarkTimestampFormat} is shown
      * when {@link #setWatermarkText} is NOT invoked
      *
      * @param text watermark content
      */
    public void setWatermarkText(String text) {
        set(KEY_WATERMARK_TEXT,text);
    }


    /**
      * set watermark external Text
      * example : gps watermak
      *
      * @param text watermark content
    */
    public void setWatermarkTextEx1(String text){
        set(KEY_WATERMARK_TEXT_EX1,text);
    }

    /**
      * set watermart external text showing area
      * @param left
      * @param top
      * @param right
      * @param bottom
    */
    public void setWatermarkTextAreaEx1(int left,int top,int right,int bottom){
        String area="("+left+","+top+","+right+","+bottom+",1)";
        set(KEY_WATERMARK_AREA_EX1,area);
    }

    /**
      * set watermark external Text
      * example : gps watermakr
      *
      * @param text watermark content
    */
    public void setWatermarkTextEx2(String text){
        set(KEY_WATERMARK_TEXT_EX2,text);
    }

    /**
      * set watermart external text showing area
      * @param left
      * @param top
      * @param right
      * @param bottom
    */
    public void setWatermarkTextAreaEx2(int left,int top,int right,int bottom){
        String area="("+left+","+top+","+right+","+bottom+",1)";
        set(KEY_WATERMARK_AREA_EX2,area);
    }

    /**
      * set watermark external Text 3
      * example : gps watermakr
      *
      * @param text watermark content
    */
    public void setWatermarkTextEx3(String text){
        set(KEY_WATERMARK_TEXT_EX3,text);
    }

    /**
      * set watermart external text showing area 3
      * @param left
      * @param top
      * @param right
      * @param bottom
    */
    public void setWatermarkTextAreaEx3(int left,int top,int right,int bottom){
        String area="("+left+","+top+","+right+","+bottom+",1)";
        set(KEY_WATERMARK_AREA_EX3,area);
    }

    /**
      * set watermark text size
      * @param size
      */
    public void setWatermarkTextSize(float size) {
        set(KEY_WATERMARK_TEXT_SIZE,size);
    }

    /**
      * set watermark text color
      * The default text color is RED
      *
      * @param color
      */
    public void setWatermarkTextColor(int color) {
        set(KEY_WATERMARK_TEXT_COLOR,color);
    }

    /**
      * set watermark text color
      * The default text color is RED
      * @param a
      * @param r
      * @param g
      * @param b
      */
    public void setWatermarkTextColor(int a, int r, int g, int b) {
        int color = ((a << 24) & 0xff000000) | ((r << 16) & 0xff0000) | ((g << 8) & 0xff00) | (b & 0xff);
        set(KEY_WATERMARK_TEXT_COLOR,color);
    };

    /**
      * set watermark position offset on watermark area
      *
      * @param x  x offset on watermark area
      * @param y  y offset on watermark area
      */
    public void setWatermarkTextPosition(float x,float y) {
        set(KEY_WATERMARK_TEXT_X,x);
        set(KEY_WATERMARK_TEXT_Y,y);
    }

    /**
          * set font file to draw watermark text
          * The chinese characters are included in /system/fonts/NotoSansCJK-Regular.ttc
          */
    public void setWatermarkFontFile(String file) {
        set(KEY_WATERMARK_FONT_FILE, file);
    }

    /**
      * set watermark time format
      * The default time format is "%Y%m%d-%H:%M:%S"
      *
      * @param format
      */
    public void setWatermarkTimestampFormat(String format) {
        set(KEY_WATERMARK_TIMESTAMP_FORMAT,format);
    }


    //add_watermark_preview_enable
    /**
      * set watermark preview enable
      * The default value is true
      *
      * @param format
      */
    public void setWatermarkPreviewEn(boolean enable) {
        if(enable) {
            set(KEY_WATERMARK_PREVIEW_EN, 1);
        } else {
            set(KEY_WATERMARK_PREVIEW_EN, 0);
        }
    }

    /**
      * set watermark time ms
      *
      * @param enable
      */
    public void setWatermarkTimeMs(boolean enable){
        if(enable) {
            set(KEY_WATERMARK_TIME_MS, 1);
        } else {
            set(KEY_WATERMARK_TIME_MS, 0);
        }
    }


    /**
      * set watermark Text shadow radius
      *
      * @param shadowRadius
      */
    public void setWatermarkTextShadowRadius(float shadowRadius){
        set(KEY_WATERMARK_TEXT_SHADOW_RADIUS,shadowRadius);
    }

    /**
      * set watermark Text shadow Offset
      *
      * @param xOffset  yOffset
      */
    public void setWatermarkTextShadowOffset(float xOffset, float yOffset){
        set(KEY_WATERMARK_TEXT_SHADOW_XOFFSET,xOffset);
        set(KEY_WATERMARK_TEXT_SHADOW_YOFFSET,yOffset);
    }

    /**
      * set watermark Text shadow color
      *
      * @param shadowColor
      */
    public void setWatermarkTextShadowColor(int shadowColor){
        set(KEY_WATERMARK_TEXT_SHADOW_COLOR,shadowColor);
    }


    /**
      * whether show image watermark on preview, recording video, picture(normal capture and vss) and video data callback  or not
           * And both of the {@link #setWatermarkImgPath} and {@link #setWatermarkImgArea} should be invoked before
           *
      * @param enable if true show watermark on preview pictures
      */
    public void enableWatermarkImage(boolean enable) {
        if(enable) {
            set(KEY_WATERMARK_IMG_MODE,"on");
        } else {
            set(KEY_WATERMARK_IMG_MODE,"off");
        }
    }

    /**
          * Set the file path of watermark image
          * It's effective only when {@link #enablePictureWatermarkImage} is invoked with true
          *
          * @param path
          *                   The watermark image's file path
          */
    public void setWatermarkImgPath(String path) {
        set(KEY_WATERMARK_IMG_PATH, path);
    }


    /**
          * Set the area of screen in which the watermark image is composed
          * It's effective only when {@link #enablePictureWatermarkImage} is invoked with true
          *
          * @param left The position of the left edge of this area
          * @param top The position of the top edge of this area
          * @param right The position of the right edge of this area
          * @param bottom The position of the bottom edge of this area
          */
    public void setWatermarkImgArea(int left,int top,int right,int bottom) {
        String area="("+left+","+top+","+right+","+bottom+",1)";
        set(KEY_WATERMARK_IMG_AREA, area);
    }


    /**
         * Sets recording mode hint. This tells the camera that the intent of
         * the application is to record videos {@link
         * android.media.MediaRecorder#start()}, not to take still pictures
         * {@link #takePicture(CameraDevice.ShutterCallback, CameraDevice.PictureCallback,
         * CameraDevice.PictureCallback, CameraDevice.PictureCallback)}. Using this hint can
         * allow MediaRecorder.start() to start faster or with fewer glitches on
         * output. This should be called before starting preview for the best
         * result, but can be changed while the preview is active. The default
         * value is false.
         *
         * The app can still call takePicture() when the hint is true or call
         * MediaRecorder.start() when the hint is false. But the performance may
         * be worse.
         *
         * @param hint true if the apps intend to record videos using
         *             {@link android.media.MediaRecorder}.
         */
    public void setRecordingHint(boolean hint) {
        set(KEY_RECORDING_HINT, hint ? TRUE : FALSE);
    }

    /**
          * To enable or disable HDR
          *
          * @param enabled
          *                        true, means the HDR is to be enabed,
          *                        and hdr mode{@link #setHdrMode} should be set {@link #VIDEO_HDR_MODE_MVHDR}
          *
          *                        false, mean HDR is disabled
          */
    public void setHdr(boolean enabled) {
        set(KEY_VIDEO_HDR, enabled? "on" : "off");
    }

    /**
          * Get the setting of HDR
          *
          * @return
          *            if returned string is on,  means HDR is enabled
          *            if returned string is off means HDR is disalbed
          *            if returned string is empty, means HDR isn't supported
          */
    public String getHdr() {
        String value = get(KEY_VIDEO_HDR);
        return value;
    }

    /**
          * Get the supported setting of HDR
          *
          * @return
          *            if the  setting list includes "on", then the vhdr is supported
          *            if the setting list only has "off", then the vhdr isn't supported
          *            if null is returned, then the vhdr isn't supported
          */
    public List<String> getSuportedHdr() {
        String str = get(KEY_VIDEO_HDR + SUPPORTED_VALUES_SUFFIX);
        return split(str);
    }

    /**
         * Gets the current color effect setting.
         *
         * @return current color effect. null if color effect
         *         setting is not supported.
         */
    public String getColorEffect() {
        return get(KEY_EFFECT);
    }

    /**
         * Sets the current color effect setting.
         *
         * @param value new color effect.
         * @see #getColorEffect()
         */
    public void setColorEffect(String value) {
        set(KEY_EFFECT, value);
    }

    /**
         * Gets the supported color effects.
         *
         * @return a list of supported color effects. null if color effect
         *         setting is not supported.
         * @see #getColorEffect()
         */
    public List<String> getSupportedColorEffects() {
        String str = get(KEY_EFFECT + SUPPORTED_VALUES_SUFFIX);
        return split(str);
    }

    /**
          * Set the HDR mode
          *
          * @param mode
          *                    VIDEO_HDR_MODE_IVHDR: should never be set
          *                    VIDEO_HDR_MODE_MVHDR: is set when HDR is enabled
          */
    public void setHdrMode(String mode) {
        if (!mode.isEmpty() && (mode.equals(VIDEO_HDR_MODE_IVHDR) || mode.equals(VIDEO_HDR_MODE_MVHDR))) {
            set(KEY_VIDEO_HDR_MODE, mode);
        }
    }

    /**
     * Sets the clockwise rotation angle in degrees relative to the
     * orientation of the camera. This affects the pictures returned from
     * JPEG {@link PictureCallback}. The camera driver may set orientation
     * in the EXIF header without rotating the picture. Or the driver may
     * rotate the picture and the EXIF thumbnail. If the Jpeg picture is
     * rotated, the orientation in the EXIF header will be missing or 1 (row
     * #0 is top and column #0 is left side).
     *
     * <p>
     * If applications want to rotate the picture to match the orientation
     * of what users see, apps should use
     * {@link android.view.OrientationEventListener} and
     * {@link android.hardware.Camera.CameraInfo}. The value from
     * OrientationEventListener is relative to the natural orientation of
     * the device. CameraInfo.orientation is the angle between camera
     * orientation and natural device orientation. The sum of the two is the
     * rotation angle for back-facing camera. The difference of the two is
     * the rotation angle for front-facing camera. Note that the JPEG
     * pictures of front-facing cameras are not mirrored as in preview
     * display.
     *
     * <p>
     * For example, suppose the natural orientation of the device is
     * portrait. The device is rotated 270 degrees clockwise, so the device
     * orientation is 270. Suppose a back-facing camera sensor is mounted in
     * landscape and the top side of the camera sensor is aligned with the
     * right edge of the display in natural orientation. So the camera
     * orientation is 90. The rotation should be set to 0 (270 + 90).
     *
     * <p>The reference code is as follows.
     *
     * <pre>
     * public void onOrientationChanged(int orientation) {
     *     if (orientation == ORIENTATION_UNKNOWN) return;
     *     android.hardware.Camera.CameraInfo info =
     *            new android.hardware.Camera.CameraInfo();
     *     android.hardware.Camera.getCameraInfo(cameraId, info);
     *     orientation = (orientation + 45) / 90 * 90;
     *     int rotation = 0;
     *     if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
     *         rotation = (info.orientation - orientation + 360) % 360;
     *     } else {  // back-facing camera
     *         rotation = (info.orientation + orientation) % 360;
     *     }
     *     mParameters.setRotation(rotation);
     * }
     * </pre>
     *
     * @param rotation The rotation angle in degrees relative to the
     *                 orientation of the camera. Rotation can only be 0,
     *                 90, 180 or 270.
     * @throws IllegalArgumentException if rotation value is invalid.
     * @see android.view.OrientationEventListener
     * @see #getCameraInfo(int, CameraInfo)
     */
    public void setRotation(int rotation) {
        if (rotation == 0 || rotation == 90 || rotation == 180
                || rotation == 270) {
            set(KEY_ROTATION, Integer.toString(rotation));
        } else {
            throw new IllegalArgumentException(
                "Invalid rotation=" + rotation);
        }
    }


    /**
         * Sets the clockwise rotation angle in degrees relative to the orientation of the camera.
         * This affects the recording video and video callback data and preview callback data.
         * The camera hal rotates the recording video and video callback data and
         * preview callback data according to this parameter.
         * Now the rotation angle only has two values: 0 and 180, other values are considered as 0
         */
    public void setVideoRotation(int rotation) {
        if (DEBUG) Log.i(TAG, "setVideoBufRotation: "  + rotation);
        set(KEY_VIDEO_ROTATION, rotation);
    }

    public void setPreviewMirror(boolean mirror) {
        if (DEBUG) Log.i(TAG, "setPreviewMirror: "  + mirror);
        set(KEY_PREVIEW_MIRROR, mirror ? 1 : 0);
    }

    /**
      * Sets GPS latitude coordinate. This will be stored in JPEG EXIF header.
      *
      * @param latitude GPS latitude coordinate.
     */
    public void setGpsLatitude(double latitude) {
        set(KEY_GPS_LATITUDE, Double.toString(latitude));
    }

    /**
      * Sets GPS longitude coordinate. This will be stored in JPEG EXIF header.
      *
      * @param longitude GPS longitude coordinate.
     */
    public void setGpsLongitude(double longitude) {
        set(KEY_GPS_LONGITUDE, Double.toString(longitude));
    }

    /**
      * Sets GPS altitude. This will be stored in JPEG EXIF header.
      *
      * @param altitude GPS altitude in meters.
      */
    public void setGpsAltitude(double altitude) {
        set(KEY_GPS_ALTITUDE, Double.toString(altitude));
    }

    /**
      * Sets GPS timestamp. This will be stored in JPEG EXIF header.
      *
      * @param timestamp GPS timestamp (UTC in seconds since January 1, 1970).
      *
      */
    public void setGpsTimestamp(long timestamp) {
        set(KEY_GPS_TIMESTAMP, Long.toString(timestamp));
    }

    /**
      * Sets GPS processing method. It will store up to 32 characters
      * in JPEG EXIF header.
      *
      * @param processing_method The processing method to get this location.
      */
    public void setGpsProcessingMethod(String processing_method) {
        set(KEY_GPS_PROCESSING_METHOD, processing_method);
    }

    /**
      * Removes GPS latitude, longitude, altitude, and timestamp from the parameters.
      */
    public void removeGpsData() {
        remove(KEY_GPS_LATITUDE);
        remove(KEY_GPS_LONGITUDE);
        remove(KEY_GPS_ALTITUDE);
        remove(KEY_GPS_TIMESTAMP);
        remove(KEY_GPS_PROCESSING_METHOD);
    }


    /**
       * Set drop camera frame . It will  affect all client data.
       *
       * @param the interval is that you want to skip  frame  .
       * if param == 0, not drop frame;
          if param > 0,  drop one frame each param;
          if param < 0,  drop param frames each one frame;
       */
    public void setDropCamFrame(int interval) {
        set(KEY_DROP_CAMERA_FRAME,interval);
    }

    public void setDropStreamFrame(int streamPolicy, int usage) {
        if (mDropStreamFrameInfo == null) {
            mDropStreamFrameInfo = new ArrayList<>(5);
        }
        Pair<Integer, Integer> oldVal = null;
        for (Pair<Integer, Integer> pair: mDropStreamFrameInfo) {
            if (usage == pair.second) {
                oldVal = pair;
                break;
            }
        }
        if (oldVal != null) {
            mDropStreamFrameInfo.remove(oldVal);
        }
        Pair<Integer, Integer> pair = new Pair<>(streamPolicy, usage);
        mDropStreamFrameInfo.add(pair);
    }

    /**
       * enable timestamp with frame .
       * yuv frame callback with absolute timestamp and record ts file with absolute timestamp;
       * @param:1, enable, 0 disable .
       *
       * Note:it should be called before startYuvVideoFrame;
       */
    public void enableTimestampWithFrame(int isEnable) {
        set(KEY_FRAME_TIMESTAMP_FOR_PRV, isEnable);
        set(KEY_FRAME_TIMESTAMP_FOR_REC, isEnable);
    }

    /**
       * enable timestamp with PreviewClient YUV frame .
       * yuv frame callback with absolute timestamp
       * @param:1, enable, 0 disable .
       *
       * Note:it should be called before startYuvVideoFrame;
       */
    public void enableTimestampForPrv(int isEnable) {
        set(KEY_FRAME_TIMESTAMP_FOR_PRV, isEnable);
    }

    /**
     * enable timestamp with Record frame in TS stream .
     * yuv frame callback with absolute timestamp
     * @param:1, enable, 0 disable .
     *
     * Note:it only support TS file, not support MP4 file;
     */
    public void enableTimestampForRec(int isEnable) {
        set(KEY_FRAME_TIMESTAMP_FOR_REC, isEnable);
    }

    public void enableVideoWithTimeCallback(boolean isEnable) {
        set(KEY_ENABLE_FILE_TIME, isEnable?1:0);
    }


    /**
          * Enable the warp .
          * @param enable
          *             true means enable the image watermark, otherwise disable it
          */
    public void enableWarp(boolean enable) {
        set(KEY_WARP_MODE, enable ? "on" : "off");
    }

    /**
          * Returns true if Warp is on.
          *
          */
    public boolean isWarpEnable() {
        String str = get(KEY_WARP_MODE);
        return "on".equals(str);
    }


    /**@hide*/
    public void setCamMode(int mode) {
        set(KEY_CAM_MODE, mode);
    }

    public void setSubFrameSize (int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_EXT1_SIZE, v);
    }

    void setExt0Size(int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_EXT0_SIZE, v);
    }

    void setExt0Format(String v) {
        set(KEY_EXT0_FMT, v);
    }

    void setExt1Size(int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        set(KEY_EXT1_SIZE, v);
    }

    void setExt1Format(String v) {
        set(KEY_EXT1_FMT, v);
    }

    public void setAVMDisplay(AVMCameraDisplay display) {
        set(KEY_AVM_DISPLAY, display.position);
    }

    public void setAvmCalibrationMode(int mode ) {
        set(KEY_AVM_CALIBRATION_MODE, mode);
    }

    public void setAvmCalibration(boolean enable) {
        set(KEY_AVM_CALIBRATION, enable? 1:2);
    }

    void setExtSize(int id, int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        if(id < 0 || id >= KEY_EXT_SIZE.length) {
            Log.e(TAG, "setExtSize error : id=" + id + ",bug length is " +  KEY_EXT_SIZE.length );
        }
        set(KEY_EXT_SIZE[id], v);
    }

    void setExtFormat(int id, String v) {
        if(id < 0 || id >= KEY_EXT_SIZE.length) {
            Log.e(TAG, "setExtFormat error : id=" + id + ",bug length is " +  KEY_EXT_SIZE.length );
        }
        set(KEY_EXT_FORMAT[id], v);
    }

    public String getAvmCalibIntRearStr() {
        String value = get(KEY_AVM_CALIB_INTREAR);
        return value;
    }

    public String getAvmCalibPosRearStr() {
        String value = get(KEY_AVM_CALIB_POSREAR);
        return value;
    }

    public String getAvmCalibDistCoeffRearStr() {
        String value = get(KEY_AVM_CALIB_DISTCOEFFREAR);
        return value;
    }

    public float[] getAvmCalibIntRear() {
        String value = get(KEY_AVM_CALIB_INTREAR);
        if(value == null) {
            Log.e(TAG,"the value of KEY_AVM_CALIB_INTREAR is null, try again");
            return null;
        }
        float[] result = new float[9];
        String2FloatArray(value, result, 9, ',');
        return result;
    }

    public float[] getAvmCalibPosRear() {
        String value = get(KEY_AVM_CALIB_POSREAR);
        if(value == null) {
            Log.e(TAG,"the value of KEY_AVM_CALIB_POSREAR is null, try again");
            return null;
        }
        float[] result = new float[3];
        String2FloatArray(value, result, 3, ',');
        return result;
    }

    public float[] getAvmCalibDistCoeffRear() {
        String value = get(KEY_AVM_CALIB_DISTCOEFFREAR);
        if(value == null) {
            Log.e(TAG,"the value of KEY_AVM_CALIB_DISTCOEFFREAR is null, try again");
            return null;
        }
        float[] result = new float[4];
        String2FloatArray(value, result, 4, ',');
        return result;
    }

    boolean String2FloatArray(String value, float result[], int num, char split) {
        if (!"".equals(value)) {
            TextUtils.StringSplitter splitter = new TextUtils.SimpleStringSplitter(split);
            splitter.setString(value);

            int i = 0;
            for (String kv : splitter) {
                if(i > num) {
                    Log.e(TAG,"size > " + num);
                    return false;
                }
                result[i] = Float.parseFloat(kv);
                i++;
            }
            return true;
        }
        return false;
    }

    void setExtSurfaceSize(int id, int width,int height) {
        String v = Integer.toString(width) + "x" + Integer.toString(height);
        if(id < 0 || id >= KEY_EXT_SURFACE_SIZE.length) {
            Log.wtf(TAG, "setExtSurfaceSize error : id=" + id + ",bug length is " +  KEY_EXT_SIZE.length );
        }
        set(KEY_EXT_SURFACE_SIZE[id], v);
    }

    void setExtSurfaceFormat(int id, String v) {
        if(id < 0 || id >= KEY_EXT_SURFACE_SIZE.length) {
            Log.e(TAG, "setExtSurfaceFormat error : id=" + id + ",bug length is " +  KEY_EXT_SIZE.length );
        }
        set(KEY_EXT_SURFACE_FORMAT[id], v);
    }

    private static final String KEY_AVM_ALGO = "avm-algo";

    public void setAvmAlgo(int algo) {
        set(KEY_AVM_ALGO, algo);
    }

    void setFromRecordConfig(RecordConfiguration config) {


        String path = config.mOutPutFilePath;
        String fileName = config.mOutPutFileName;

        set(KEY_VIDEO_OUTPUT_FILE, path);
        set(KEY_VIDEO_OUTPUT_FILE_NAME, fileName);


        if(config.mLockFilePath != null) {
            set(KEY_VIDEO_LOCK_FILE, config.mLockFilePath);
        }

        if(config.mLockFileNamePrefix != null) {
            set(KEY_LOCK_FILE_NAME_PREFIX, config.mLockFileNamePrefix);
        }

        set(KEY_VIDEO_OUTPUT_FORMAT,
            Integer.toString(config.mCamcorderProfile.fileFormat));
        set(KEY_VIDEO_ENCODER,
            Integer.toString(config.mCamcorderProfile.videoCodec));

        setVideoSize(config.mCamcorderProfile.videoFrameWidth,
                     config.mCamcorderProfile.videoFrameHeight);

        set(KEY_VIDEO_FRAME_RATE,
            Integer.toString(config.mCamcorderProfile.videoFrameRate));

        set(KEY_VIDEO_PARAM_ENCODING_BITRATE,
            Integer.toString(config.mCamcorderProfile.videoBitRate));
        //set(KEY_USER_SET_VIDEO_ENCODEBITRATE,Integer.toString(1));


        set(KEY_AUDIO_SOURCE, config.mAudioSource);

        set(KEY_AUDIO_ENCODER, Integer.toString(config.mCamcorderProfile.audioCodec));
        set(KEY_AUDIO_PARAM_ENCODING_BITRATE,
            Integer.toString(config.mCamcorderProfile.audioBitRate));
        set(KEY_AUDIO_PARAM_NUMBER_OF_CHANNELS,
            Integer.toString(config.mCamcorderProfile.audioChannels));
        set(KEY_AUDIO_PARAM_SAMPLING_RATE,
            Integer.toString(config.mCamcorderProfile.audioSampleRate));


        int videoFrameMode = config.mVideoFrameMode;
        set(KEY_MAIN_VIDEO_FRAME_ENABLED, videoFrameMode);

        if (config.mRecordingMuteAudio) {
            set(KEY_VIDEO_MUTE_AIUDO, Integer.toString(1));
        } else {
            set(KEY_VIDEO_MUTE_AIUDO, Integer.toString(0));
        }


        set(KEY_KEYPOINT_SPAN_LIMIT, config.mKeypointSpanLimit);

        set(KEY_VIDCB_FRAMERATE, config.mVideoCbFrameRate);

        set(KEY_VIDEO_ROTATE_SIZE, Integer.toString(config.mVideoRotateSize));
        set(KEY_VIDEO_ROTATE_DURATION, Integer.toString(config.mVideoRotateDuration));

        set(KEY_REC_MUTE_OGG, config.mEnableRecordStartRing ? 0 : 1);

        set(KEY_VIDEO_PARAM_ENCODING_MINBITRATE, config.mVideoBitRateMin);
        set(KEY_VIDEO_PARAM_ENCODING_MAXBITRATE, config.mVideoBitRateMax);
        set(KEY_DELETE_FILE_NUM, config.mVideoCycleDeleteFileNum);

        set(KEY_REDUCE_REC_FPS, config.mReduceRecordingFps);

        set(KEY_ENABLE_ENCRYPT, config.mEnableEncryption ? 1 : 0);
        set(KEY_NOTIFY_ENCRYPT_TYPE, config.mEnableNotifyEncryptType ? 1 : 0);

        set(KEY_SDCARD_SPEED_LIMIT, config.mSDCardSpeedLimit);
        set(KEY_ENABLE_MMAP_TYEPE, config.mEnableMmapType?1:0);

        set(KEY_DISABLE_CIRCULAR_DELETE, config.mDisableCircularDelete ? 1 : 0);
        set(KEY_SUB_STREAM_SHARE_AUDIO, config.mEnableSubStreamShareAudio ? 1 : 0);

    }

    void setCameraCharacteristics(CameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    void setRequestMetadata(CameraMetadataNative metadata) {
        mRequestMetadata = metadata;
    }

    CameraMetadataNative getRequestMetadata() {
       return mRequestMetadata;
    }

    void updateMetadata(CameraMetadataNative metadata, int usage) {
        if (metadata == null) {
            Log.e(TAG, " updateMetadata is null");
            return;
        }

        if(CameraSurfaceUsage.isPicture(usage)){
            resetFrameDrop(metadata);
        } else {
            mapFrameDrop(metadata);
        }
        //mapAeBlockMode(metadata); //this not common
        // TODO: watermark
        // mapWatermark(metadata, usage);
        mapFpsRange(metadata, usage);
        mapSceneMode(metadata, usage);
        if(DEBUG) {
           Log.i(TAG, " updateMetadata usage: " + usage);
           metadata.dumpToLog();
        }
    }

    private void mapFrameDrop(CameraMetadataNative metadata ) {
        // sensor: 0x01, stream: 0x15
        int sensorMode = 0x01;
        int streamMode = 0x15;
        int[] interval = null;
        if (mDropStreamFrameInfo != null && mDropStreamFrameInfo.size() != 0) {
            int size = mDropStreamFrameInfo.size() * 2 + 2;
            interval = new int[size];
            interval[0] = streamMode;
            interval[1] = mDropStreamFrameInfo.size();
            int index = 2;
            for (Pair<Integer, Integer> pair: mDropStreamFrameInfo) {
                if (index < size) {
                    interval[index++] = pair.first;
                    interval[index++] = pair.second;
                } else {
                    Log.e(TAG, "Error: size " + size + "index " + index);
                }
            }
        } else {
            int dropFrame = getInt(KEY_DROP_CAMERA_FRAME, 0);
            String strDropFrame = get(KEY_DROP_CAMERA_FRAME);
            interval = new int[]{0, 0};
            if(strDropFrame != null) {
                interval[0] = sensorMode;
            }
            interval[1] = dropFrame;
        }
        Log.e(TAG, "mapFrameDrop " + Arrays.toString(interval));
        try {
            metadata.set(CAMERA_FRAME_DROP_POLICY, interval);
        } catch(IllegalArgumentException e) {
            Log.e(TAG,"set fail:" + CAMERA_FRAME_DROP_POLICY.getName());
        }
    }

    private void resetFrameDrop(CameraMetadataNative metadata ) {
        int[] interval = {1, 0}; // (0,0) ng, maybe camerahal need change policy
        try {
            metadata.set(CAMERA_FRAME_DROP_POLICY, interval);
        } catch(IllegalArgumentException e) {
            Log.e(TAG,"resetFrameDrop fail:" + CAMERA_FRAME_DROP_POLICY.getName());
        }
    }

    void mapAeBlockMode(CameraMetadataNative metadata ) {
        int aeBlockyMode = getInt(KEY_AE_BLOCK_Y_MODE, 0);
        try {
            metadata.set(AE_BLOCK_Y_MODE, aeBlockyMode);
        } catch(IllegalArgumentException e) {
            Log.e(TAG,"set fail:" + AE_BLOCK_Y_MODE.getName());
        }
    }

    private int[] getWatermarkArea(String area) {
        int[] ret = new int[4];
        if(area == null) {
            area = "(0,200,300,240)";
        }
        String str = area.replaceAll("\\(|\\)", "");
        String[] digstr = str.split(",");
        for(int i= 0; i<4; i++){
            try {
                ret[i] = Integer.parseInt(digstr[i]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "number format error: " + area);
                ret[i] = 0;
            }
        }
        return ret;
    }
    //watermark car info include 19 param, like car speed ...
    private int[] getWatermarkCarInfo(String area) {
        int[] ret = new int[19];
        if(area == null) {
            area = "(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)";
        }
        String str = area.replaceAll("\\(|\\)", "");
        String[] digstr = str.split(",");
        for(int i= 0; i<19; i++){
            try {
                ret[i] = Integer.parseInt(digstr[i]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "number format error: " + area);
                ret[i] = 0;
            }
        }
        return ret;
    }

    boolean isWatermarkEnable(int usage) {
        String watermarkTextMode = get(KEY_WATERMARK_TEXT_MODE);
        String watermarkImageMode = get(KEY_WATERMARK_IMG_MODE);


        boolean textEnable = "on".equals(watermarkTextMode);
        boolean imageEnable = "on".equals(watermarkImageMode);

        return textEnable || imageEnable;
        //return true;
    }
    private void mapWatermark(CameraMetadataNative metadata , int usage) {

        String watermarkTextMode = get(KEY_WATERMARK_TEXT_MODE);
        String watermarkImageMode = get(KEY_WATERMARK_IMG_MODE);
        String watermarkCarMode = get(KEY_WATERMARK_CAR_MODE);

        if("on".equals(watermarkCarMode)) {
            String watermarkCarInfo = get(KEY_WATERMARK_CAR_INFO);
            metadata.set(WATERMARK_CAR_ENABLE, 1);
            metadata.set(WATERMARK_CAR_INFO, getWatermarkCarInfo(watermarkCarInfo));
            metadata.set(WATERMARK_CAR_VIN,  get(KEY_WATERMARK_CAR_VIN));
        }else {
            metadata.set(WATERMARK_CAR_ENABLE, 0);
        }

        if("on".equals(watermarkTextMode)) {
            String watermarkText            = get(KEY_WATERMARK_TEXT);
            String watermarkArea            = get(KEY_WATERMARK_AREA);
            String watermarkTextEx1         = get(KEY_WATERMARK_TEXT_EX1);
            String watermarkAreaEx1         = get(KEY_WATERMARK_AREA_EX1);
            String watermarkTextEx2         = get(KEY_WATERMARK_TEXT_EX2);
            String watermarkAreaEx2         = get(KEY_WATERMARK_AREA_EX2);
            String watermarkTextEx3         = get(KEY_WATERMARK_TEXT_EX3);
            String watermarkAreaEx3         = get(KEY_WATERMARK_AREA_EX3);

            String watermarkSize       = get(KEY_WATERMARK_SIZE);
            float  watermarkTextSize   = getFloat(KEY_WATERMARK_TEXT_SIZE, 1);
            int watermarkTextColor  = getInt(KEY_WATERMARK_TEXT_COLOR, 0xffff0000);
            String watermarkTextFont   = get(KEY_WATERMARK_FONT_FILE);

            float[] watermarkTextPosition = new float[2];
            watermarkTextPosition[0] = getFloat(KEY_WATERMARK_TEXT_X, 1.0f);
            watermarkTextPosition[1] = getFloat(KEY_WATERMARK_TEXT_Y, 1.0f);

            String watermarkOffset = get(KEY_WATERMARK_OFFSET);

            int watermarkTimeMs = getInt(KEY_WATERMARK_TIME_MS, 0);

            String watermarkTimerOffset = get(KEY_WATERMARK_TIMER_OFFSET);

            String watermarkFormat = get(KEY_WATERMARK_TIMESTAMP_FORMAT); //add_watermark_Format_interface //mtk70199_0111
            int watermarkPreviewEn = getInt(KEY_WATERMARK_PREVIEW_EN, 1);//add_watermark_preview_enable

            try {
                metadata.set(WATERMARK_TEXT_ENABLE, 1);
                metadata.set(WATERMARK_TEXT_SIZE, watermarkTextSize);
                metadata.set(WATERMARK_TEXT_COLOR,watermarkTextColor);
                metadata.set(WATERMARK_TEXT_FONTFILE, watermarkTextFont);
                metadata.set(WATERMARK_TEXT_POSITION, watermarkTextPosition);
                metadata.set(WATERMARK_TIME_MS, watermarkTimeMs);

                metadata.set(WATERMARK_TEXT, watermarkText );
                metadata.set(WATERMARK_TEXT_AREA, getWatermarkArea(watermarkArea));
                metadata.set(WATERMARK_TEXT_EX1, watermarkTextEx1 );
                metadata.set(WATERMARK_TEXT_AREA_EX1, getWatermarkArea(watermarkAreaEx1));
                metadata.set(WATERMARK_TEXT_EX2, watermarkTextEx2 );
                metadata.set(WATERMARK_TEXT_AREA_EX2, getWatermarkArea(watermarkAreaEx2));
                metadata.set(WATERMARK_TEXT_EX3, watermarkTextEx3 );
                metadata.set(WATERMARK_TEXT_AREA_EX3, getWatermarkArea(watermarkAreaEx3));

				//add_watermark_Format_interface
				metadata.set(WATERMARK_FORMAT, watermarkFormat);

				//add_watermark_preview_enable
				metadata.set(WATERMARK_PREVIEW_EN, watermarkPreviewEn);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set fail:" + e.getMessage());
            }

        }else {
            try {
                metadata.set(WATERMARK_TEXT_ENABLE, 0);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set fail:" + WATERMARK_TEXT_ENABLE.getName());
            }

        }

        if("on".equals(watermarkImageMode)){
            String watermarkImageArea = get(KEY_WATERMARK_IMG_AREA);
            String watermarkImagePath = get(KEY_WATERMARK_IMG_PATH);
            try {
                metadata.set(WATERMARK_IMAGE_ENABLE, 1);
                metadata.set(WATERMARK_IMAGE_AREA, getWatermarkArea(watermarkImageArea));
                metadata.set(WATERMARK_IMAGE_PATH, watermarkImagePath);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set watermark image fail");
            }
        } else {
            try {
                metadata.set(WATERMARK_IMAGE_ENABLE, 0);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set fail:" + WATERMARK_IMAGE_ENABLE.getName());
            }
        }

    }

    private void mapFpsRange(CameraMetadataNative metadata , int usage) {
        Range<Integer> fpsRange = null;

        fpsRange = mPreviwFpsRange;

        if(fpsRange != null) {
            try {
                metadata.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set fail:" + CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE.getName());
            }
        }
    }

    private void mapSceneMode(CameraMetadataNative metadata , int usage) {
        String sceneMode = getSceneMode();
        if (sceneMode != null) {
            try {
                metadata.set(CaptureRequest.CONTROL_SCENE_MODE, Integer.parseInt(sceneMode));
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"set fail:" + CaptureRequest.CONTROL_SCENE_MODE.getName());
            }
        }
    }

    void setResultMetadata(CameraMetadataNative resultMetadata) {
        mResultMetadata = resultMetadata;
    }

    public int getSensorOrientation() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    public Rect getSensorInfoActiveArraySize() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }


    public float getAvailableMaxDigitalZoom() {
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }


    public boolean isFlashSupported() {
        return mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    }

    private static final int CONTROL_SCENE_MODE_HDR = 0x12;
    public boolean isHdrSceneSupported() {
        int[] availableSceneModes = mCameraCharacteristics.get(
                                        CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        for (int availableSceneMode : availableSceneModes) {
            if (availableSceneMode == CONTROL_SCENE_MODE_HDR) {
                return true;
            }
        }
        return false;
    }


    public Integer getSupportedHardwareLevel() {
        Integer supportedHardwareLevel = mCameraCharacteristics
                                         .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        return supportedHardwareLevel;
    }


    public int[] getSupportedFaceDetectModes() {
        int[] modes = mCameraCharacteristics.get(
                          CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        return modes;
    }

    public boolean isExposureCompensationSupported() {
        Range<Integer> compensationRange =
            mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getLower() != 0 || compensationRange.getUpper() != 0;
    }

    public boolean isAutoFocusSupported() {
        Integer maxAfRegions = mCameraCharacteristics.get(
                                   CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        // Auto-Focus is supported if the device supports one or more AF regions
        return maxAfRegions != null && maxAfRegions > 0;
    }

    public boolean isAutoExposureSupported() {
        Integer maxAeRegions = mCameraCharacteristics.get(
                                   CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        // Auto-Exposure is supported if the device supports one or more AE regions
        return maxAeRegions != null && maxAeRegions > 0;
    }

    /**
          * Sets the ae blocky values mode number  for adas
          *
          * @param mode:  0 ->disable, 1 -> enable
          *
          */
    public void setAeBlockMode(int mode) {
        set(KEY_AE_BLOCK_Y_MODE, mode);
    }

    int[] getResultAeBlockValue() {
        int[] value = new int[AeBlockValueSize];
        if(mResultMetadata != null) {
            try {
                value = mResultMetadata.get(AE_BLOCK_Y_VALUES);
            } catch(IllegalArgumentException e) {
                Log.e(TAG,"get fail:" + AE_BLOCK_Y_VALUES.getName());
            }
        }
        return value;
    }

    public <T> void set(CaptureRequest.Key<T> key, T value) {
        mRequestMetadata.set(key, value);
    }

    public <T> void set(CaptureResult.Key<T> key, T value) {
        mRequestMetadata.set(key, value);
    }

    public <T> void set(CameraCharacteristics.Key<T> key, T value) {
        mRequestMetadata.set(key, value);
    }

    public <T> T get(CameraCharacteristics.Key<T> key) {
        return mRequestMetadata.get(key.getNativeKey());
    }

    public <T> T get(CaptureResult.Key<T> key) {
        return mRequestMetadata.get(key.getNativeKey());
    }

    public <T> T get(CaptureRequest.Key<T> key) {
        return mRequestMetadata.get(key.getNativeKey());
    }

    /**
      * set vehicle longitude
      *
      * @param longitude.
     */
    public void setVehicleLongitude(String longitude) {
        set(KEY_VEHICLE_LONGITUDE, longitude);
    }

    /**
      * set vehicle latitude
      *
      * @param latitude.
     */
    public void setVehicleLatitude(String latitude) {
        set(KEY_VEHICLE_LATITUDE, latitude);
    }

    /**
      * set vehicle speed
      *
      * @param speed.
     */
    public void setVehicleSpeed(int speed) {
        set(KEY_VEHICLE_SPEED, speed);
    }

    /**
      * set vehicle gear status
      *
      * @param gearStatus.
     */
    public void setVehicleGearStatus(VehicleGearStatus gearStatus) {
        set(KEY_VEHICLE_GEAR_STATUS, gearStatus.gearStatus);
    }

    /**
      * set vehicle accelerator
      *
      * @param accelerator.
     */
    public void setVehicleAccelerator(int accelerator) {
        set(KEY_VEHICLE_ACCELERATOR, accelerator);
    }

    /**
      * set vehicle brake
      *
      * @param brake.
     */
    public void setVehicleBrake(boolean brake) {
        set(KEY_VEHICLE_BRAKE, brake ? 1 : 0);
    }

    /**
      * set vehicle leftSignal
      *
      * @param leftSignal.
     */
    public void setVehicleLeftSignal(boolean leftSignal) {
        set(KEY_VEHICLE_LEFT_SIGNAL, leftSignal ? 1 : 0);
    }

    /**
      * set vehicle rightSignal
      *
      * @param rightSignal.
     */
    public void setVehicleRightSignal(boolean rightSignal) {
        set(KEY_VEHICLE_RIGHT_SIGNAL, rightSignal ? 1 : 0);
    }

    /**
      * set vehicle safetybelt
      *
      * @param safetybelt.
     */
    public void setVehicleSafetybelt(boolean safetybelt) {
        set(KEY_VEHICLE_SAFETYBELT, safetybelt ? 1 : 0);
    }

    /**
      * set vehicle adasStatus
      *
      * @param adasStatus.
     */
    public void setVehicleAdasStatus(int adasStatus) {
        set(KEY_VEHICLE_ADAS_STATUS, adasStatus);
    }

    /**
      * set vehicle accelerator
      *
      * @param lanuage. 0: Eng 1: Chi
     */
    public void setVehicleLanuage(int lanuage) {
        set(KEY_VEHICLE_LANUAGE, lanuage);
    }

}

