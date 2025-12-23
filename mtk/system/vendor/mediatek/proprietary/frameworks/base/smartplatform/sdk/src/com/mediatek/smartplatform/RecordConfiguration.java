package com.mediatek.smartplatform;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.annotation.IntDef;
import android.media.CamcorderProfile;


import com.mediatek.smartplatform.SpmCameraDevice.AudioCallback;
import com.mediatek.smartplatform.SpmCameraDevice.KeypointCallback;
import com.mediatek.smartplatform.SpmCameraDevice.RecordStatusCallback;
import com.mediatek.smartplatform.SpmCameraDevice.VideoCallback;
import com.mediatek.smartplatform.SpmCameraDevice.YUVCallback;
import com.mediatek.smartplatform.RecordSource;

public class RecordConfiguration {
    private final static String TAG = "RecordConfiguration";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        VideoFrameMode.VIDEO_FRAME_MODE_DISABLE,
        VideoFrameMode.VIDEO_FRAME_MODE_SOURCE,
        VideoFrameMode.VIDEO_FRAME_MODE_PACKET,
        VideoFrameMode.VIDEO_FRAME_MODE_DUAL_SOURCE,
        VideoFrameMode.VIDEO_FRAME_MODE_DUAL_PACKET
    })
    /**
     * @VideoFrameMode.DISABLE              just write to video file
     * @VideoFrameMode.SOURCE               callback to app use h264 format
     * @VideoFrameMode.PACKET                callback to app use ts format
     * @VideoFrameMode.DUAL                   write to video file and callback to app use ts format
     * @VideoFrameMode.DUAL_SOURCE      write to video file and callback H264 data to app
     * @VideoFrameMode.DUAL_PACKET       write to ts file and callback ts packet to app , only work for ts format
     */
    public @interface VideoFrameMode {
        int VIDEO_FRAME_MODE_DISABLE      =  0;
        int VIDEO_FRAME_MODE_SOURCE       =  1;
        int VIDEO_FRAME_MODE_PACKET       =  2;
        int VIDEO_FRAME_MODE_DUAL_SOURCE  =  3;
        int VIDEO_FRAME_MODE_DUAL_PACKET  =  4;
    }

    @IntDef(value = {
        ReduceRecordingFps.FULL,
        ReduceRecordingFps.HALF,
        ReduceRecordingFps.THIRTY_PERCENT,
        ReduceRecordingFps.QUARTER
    })
    public @interface ReduceRecordingFps {
        int FULL      =  1;
        int HALF       =  2;
        int THIRTY_PERCENT       =  3;
        int QUARTER         =  4;
    }


    /**
     *  definition of supported video file format
     */
    @IntDef(value = {
        OutputFormat.MPEG_4,
        OutputFormat.MPEG2TS,
    })
    public @interface OutputFormat {
        int MPEG_4        =  2;
        int MPEG2TS       =  8;
    }

    /**
     * Uses the settings from a CamcorderProfile object for recording.
     */
    public CamcorderProfile mCamcorderProfile;

    /**
     * Sets the path of the video output file to be produced. The
     * default path is "/sdcard/DCIM/Camera".
     */
    public String mOutPutFilePath;

    /**
     * Sets the path of the video output file name to be produced.
     */
    public String mOutPutFileName;

    /**
     * a callback to monitor if video is completed.
     */
    public VideoCallback mVideoCallback;

    /**
     *  a callback to monitor if audio data  is completed.
     */
    public AudioCallback mAudioCallback;


    /**
     * a callback to be notify when record status changed.
     */
    public RecordStatusCallback mRecordStatusCallback;

    /**
     * a callback to receive keypoint data.
     */
    public KeypointCallback mKeypointCallback;

    /*
     *   video data mode,it tell recordMgr how to handle video data
     */
    public @VideoFrameMode int mVideoFrameMode;

    /**
     * Sets the audio source to be used for recording.
     * @see android.media.MediaRecorder.AudioSource
     */
    public int mAudioSource;

    /*
     * keypoint span time limit, if time interval of two video file
     * greater than the seconds what user set,keypoint video will not
     * use the video file which record before
     *
     * @seconds the unit of time interval
     */
    public int mKeypointSpanLimit;

    /**
     * mute audio when recording
     */
    public boolean mRecordingMuteAudio;

    /**
     * Set video callbcak framerate
     */
    public int  mVideoCbFrameRate;

    /**
     * the path of the video lock file to be produced. The
     * default path is "/sdcard/DCIM/Camera/protect".
     */
    public String mLockFilePath;

    /**
     * set the prefix of locked file, no prefix by default
     */
    public String mLockFileNamePrefix;

    /**
     * Sets the maximum filesize (in bytes) of the recording video. When the
     * recording video reach the maximum size, the video will save in a new
     * file.
     */
    public int mVideoRotateSize;

    /**
     * Sets the maximum duration (in ms) of the recording session. When the
     * recording video reach the maximum duration, the video will save in a
     * new file.
     */
    public int mVideoRotateDuration ;

    /**
     * enable or disable the ring of starting record .
     */
    public boolean mEnableRecordStartRing;

    /**
     * recording video bitrate range
     *the minimum bitrate
     */
    public int mVideoBitRateMin;

    /**
     * recording video bitrate range
     * @param maxBitRate, the maximum bitrate
     */
    public int mVideoBitRateMax;

    /**
     * set the number of cycle to delete file.
     * when the free space of sdCard less than than limit,it will cycle to delete files to release space.
     */
    public int mVideoCycleDeleteFileNum;

    /**
      * Reduce fps only for recording
      * 1,2,3,4 ,means for 100%,50%,30%,25%
      */
    public @ReduceRecordingFps int mReduceRecordingFps;

    public boolean mEnableEncryption;
    public boolean mEnableNotifyEncryptType;

    /*
     * set the Limit speed of SDCard.
     * when the speed of SD Card less than the size that we set over 10 seconds.It means the speed of SDCard is too slow
     * and will callback a message to apk.
     * size (KB)
     */
    public int mSDCardSpeedLimit;
    /*
        * enable mmap type to write file, default use write.
        */
    public boolean mEnableMmapType;

    /*
      * whether to disable the circular delete function, default false.
      * only if you want to disable the circular delete(thread), set it to true.
      */
    public boolean mDisableCircularDelete;

    /*
    * whether the sub-stream share audio data, default false.
    * if set to true, only the first opened sub-stream will have audio callback,
    * and later opened a sub-stream doesn't have audio callback,
    * it can reduce cpu loading.
    */
    public boolean mEnableSubStreamShareAudio;

    private RecordConfiguration() {
    }

    public static RecordConfiguration get(@RecordSource.Format int recordSource) {

        RecordConfiguration config = new RecordConfiguration();

        //maybe default quality need get from property config or others config files.
        if(RecordSource.isAvmRecord(recordSource)) {
            try {
                config.mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                if (config.mCamcorderProfile == null) {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            config.mCamcorderProfile.videoFrameRate = 25;
            config.mCamcorderProfile.videoBitRate = 6* 1000 * 1000;

        } else if(RecordSource.isSubRecord(recordSource)) {
            try {
                config.mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            try {
                config.mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        config.mVideoRotateDuration = 60*1000; //1min

        return config;
    }

}

