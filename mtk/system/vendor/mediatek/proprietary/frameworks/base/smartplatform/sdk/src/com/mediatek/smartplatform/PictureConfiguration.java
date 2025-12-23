package com.mediatek.smartplatform;

import android.graphics.ImageFormat;
import android.media.CamcorderProfile;

import com.mediatek.smartplatform.SpmCameraDevice.AudioCallback;
import com.mediatek.smartplatform.SpmCameraDevice.KeypointCallback;
import com.mediatek.smartplatform.SpmCameraDevice.RecordStatusCallback;
import com.mediatek.smartplatform.SpmCameraDevice.VideoCallback;
import com.mediatek.smartplatform.SpmCameraDevice.YUVCallback;




import com.mediatek.smartplatform.RecordSource;

public class PictureConfiguration {
    private final static String TAG = "PictureConfiguration";

    public String mPath;

    public ImageReaderEx.ImageCallback mImageCallback;
    public SpmCameraDevice.ImageDataCallback mImageDataCallback;

    /*
        * this value refers to the :
        * ImageReaderEx.ImageCallback Image_Format_xxx  (if you use ImageCallback)
        * or
        * SpmCameraDevice.ImageDataCallback Image_Format_xxx  (if you use ImageDataCallback)
        */
    public int mImageFormat;

    /*
        * this value refers to the :
        * ImageReaderEx.ImageCallback Image_Date_xxx
        * or
        * SpmCameraDevice.ImageDataCallback Image_Date_xxx
        */
    public int mDataType;

    public boolean mJpegHwEnc;
    public int mPicWidth;
    public int mPicHeight;
    //public int mCamImageFormat = ImageFormat.YUV_420_888; //discard, out of use


    private PictureConfiguration() {
    }

    public static PictureConfiguration get(@PictureSequenceSource.Format int PictureSource) {
        PictureConfiguration config = new PictureConfiguration();
        return config;
    }

}

