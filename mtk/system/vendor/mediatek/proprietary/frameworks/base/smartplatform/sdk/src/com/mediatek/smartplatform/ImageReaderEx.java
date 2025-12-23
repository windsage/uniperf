package com.mediatek.smartplatform;

import android.graphics.Rect;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.graphics.PixelFormat;

//import android.media.Image;
//import android.media.Image.Plane;
//import android.media.ImageReader;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.SystemClock;


import android.util.Log;
import android.view.Surface;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Locale;

import com.mediatek.smartplatform.SpmCameraDevice.CamPictureCallback;
import com.mediatek.smartplatform.SpmCameraDevice.ImageDataCallback;
import com.mediatek.smartplatform.Image.Plane;

public class ImageReaderEx {
    private static String TAG = "ImageReaderEx";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    //adb shell setprop log.tag.ImageReaderEx DEBUG
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean DUMP = SystemProperties.getBoolean("persist.vendor.spm_ir", false);
    private static final boolean DUMP_JPEG = SystemProperties.getBoolean("persist.vendor.spm_ire", false);
    private static final boolean DUMP_BUFFER = SystemProperties.getBoolean("persist.vendor.spm_dbb", false);
    private static final boolean DUMP_IMAGE_SELF= SystemProperties.getBoolean("persist.vendor.spm_dis", false);
    private static final boolean USE_HW_JPG_ENC = SystemProperties.getBoolean("persist.vendor.spm_hwjpg", true);
    private static final boolean DEBUG_ONE = SystemProperties.getBoolean("persist.vendor.spm_ire_one", false);
    private static final int MAX_NUM_IMAGES = 5;
    private static final long WAIT_FOR_IMAGE_TIMEOUT_MS = 1000;
    private ImageReader mReader;
    private Surface mReaderSurface;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ImageListener mImageListener;
    private HandlerThread mLoopThread = null;
    private ProcessHandler mLoopHandler = null;
    private HandlerThread mDeleteThread = null;
    private DeleteHandler  mDeleteHandler = null;
    private String mCandidataDeleteDir = null;

    private File mMediaStorageDir = null;

    private int mImageNum = 0;
    private Object mImageNumLock = new Object();

    private int mOutImageFormat = 1; // ref  ImageCallback IMAGE_FORMAT
    private int mOutDataType = 2;   // ref  ImageCallback  IMAGE_DATA
    private int mCamImageFormat = ImageFormat.YUV_420_888;
    private String mOutImageDir = null;
    private File mOutImageDirF = null;
    private String mOutImageName = null;
    private ImageCallback mImageCallback = null;
    private ImageDataCallback  mImageDataCallback = null;
    private boolean mStopping = false;
    private String mCameraId;
    private int mMaxSaveImagNum = 100;
    private int mSaveImagNum =0;
    private boolean mJpegEncHw = true;
    private boolean mSequence = true;
    private boolean hasPicture = false;
    private CamPictureCallback mCamPictureCallback = null;
    private Handler mCameraDeviceHandler = null;

    private static final boolean mCallbackUseJpeg = SystemProperties.getBoolean("persist.vendor.spm_cb_jpeg", false);

    private static final int SUCCEEDED = 0;
    private static final int FAILED = -1;

    public ImageReaderEx() {

    }

    /**
     * Callback interface.
     *
     */
    public interface ImageCallback {
        public static final int IMAGE_FORMAT_YUV_420_888 = ImageFormat.YUV_420_888;
        public static final int IMAGE_FORMAT_JPEG = ImageFormat.JPEG;
        public static final int IMAGE_FORMAT_NV21 = ImageFormat.NV21;
        public static final int IMAGE_FORMAT_YV12 = ImageFormat.YV12;
        public static final int IMAGE_FORMAT_RGB_888 = PixelFormat.RGB_888;
        public static final int IMAGE_FORMAT_YUY2 = ImageFormat.YUY2;

        public static final int IMAGE_DATA_RAW    = 0;
        public static final int IMAGE_DATA_FILE   = 1;

        public static final int IMAGE_STATUS_SUCCEEDED = 0;
        public static final int IMAGE_STATUS_FAILED = -1;

        void onImageAvailable(String cameraId, int format, int status, byte[] data, String path);
    };

    private int getInitSurfaceFormat(int outImageFormat) {
        int format = ImageFormat.YUV_420_888;

        switch (outImageFormat) {
        case ImageDataCallback.IMAGE_FORMAT_YUV_420_888:
        case ImageDataCallback.IMAGE_FORMAT_NV21:
            format = ImageFormat.YUV_420_888;
            break;
        case ImageDataCallback.IMAGE_FORMAT_JPEG:
            //only support one jpeg in camera hal.
            if(canUseJpeg()) {
                format = ImageFormat.JPEG;
            } else {
                if(mSequence) {
                    format = ImageFormat.YUV_420_888;// camera hal nv21
                } else {
                    format = ImageFormat.YV12;//for Picture WaterMark,
                }
            }
            break;
        case ImageDataCallback.IMAGE_FORMAT_YV12:
            format = ImageFormat.YV12;
            break;
        case ImageDataCallback.IMAGE_FORMAT_RGB_888:
            format = PixelFormat.RGB_888;
            break;
        case ImageDataCallback.IMAGE_FORMAT_YUY2:
            format = ImageFormat.YUY2;
            break;
        default:
            Log.e(TAG,"error outImageFormat format");
        }
        return format;
    }

    public void init(int width, int height, int format, String name, String cameraId, boolean sequence) {

        mHandlerThread = new HandlerThread(TAG + name);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mImageListener = new ImageListener();
        mCameraId = cameraId;
        mSequence = sequence;

        // may be  use mCamImageFormat
        mCamImageFormat = getInitSurfaceFormat(format);

        Log.i(TAG,"createImageReader: width = " + width + ", height = " + height + ", format = " + mCamImageFormat);
        createImageReader(width, height, mCamImageFormat, MAX_NUM_IMAGES, mImageListener, mHandler);


        if (DUMP || DUMP_JPEG || DUMP_BUFFER) {
            mMediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DCIM), "imageReader");

            // Create the storage directory if it does not exist
            if (! mMediaStorageDir.exists()) {
                if (! mMediaStorageDir.mkdirs()) {
                    Log.e(TAG, "failed to create dump directory");
                }
            }
        }
    }

    private void close() {
        Log.i(TAG, "close...");
        mStopping = true;
        try {
            synchronized (mImageNumLock) {

                while (mImageNum != 0 && mLoopThread != null) {
                    if (DEBUG_ONE) break;
                    Log.i(TAG,"wait ...");
                    mImageNumLock.wait();
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG," wait InterruptedException ");
        }

        closeImageReader();

        if(mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandler = null;
            mHandlerThread = null;
        }

        if(mLoopThread !=null) {
            mLoopThread.quitSafely();
            mLoopThread = null;
            mLoopHandler = null;
        }

        if(mDeleteThread != null) {
            mDeleteThread.quitSafely();
            mDeleteThread = null;
        }
        mDeleteHandler = null;
        mCameraDeviceHandler = null;
    }

    int start() {
        if (mOutDataType == ImageDataCallback.IMAGE_DATA_FILE && mSequence) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            createDir(timeStamp);
        }

        if(mLoopThread == null) {
            mLoopThread = new HandlerThread(TAG + "_loop_" + mCameraId);
            mLoopThread.start();
            mLoopHandler= new ProcessHandler(mLoopThread.getLooper());
        }
        if(mSequence && mDeleteThread == null) {
            mDeleteThread = new HandlerThread(TAG + "_delete_" + mCameraId);
            mDeleteThread.start();
            mDeleteHandler = new DeleteHandler(mDeleteThread.getLooper());
        }
        return 0;
    }

    private static final int MESSAGE_DELETE_DIR = 1;
    private final class DeleteHandler extends Handler {
        public DeleteHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_DELETE_DIR:
                String dir = (String)msg.obj;
                Log.i(TAG, "delete dir: " + dir +
                      "; Candidata Delete Dir :" + mCandidataDeleteDir +
                      "; current save dir: " + mOutImageDirF.getAbsolutePath());
                deleteDir(new File(dir));
                break;
            default:
            }
        }
    }

    private boolean deleteDir(File dir) {
        if(dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i< children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    private final class ProcessHandler extends Handler {
        public ProcessHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DEBUG_ONE) {
                if (mSaveImagNum > 0) {
                    Log.d(TAG, "debug one, skip......");
                    return;
                }
            }
            process();
        }
    }

    int createDir(String time) {
        if(mOutImageDir != null) {
            mOutImageDirF = new File(mOutImageDir + File.separator + time);
        } else {
            mOutImageDirF = new File(Environment.getExternalStoragePublicDirectory(
                                         Environment.DIRECTORY_DCIM), File.separator + time);
        }
        // Create the storage directory if it does not exist
        if (! mOutImageDirF.exists()) {
            if (! mOutImageDirF.mkdirs()) {
                Log.e(TAG, "failed to create directory" + mOutImageDirF);
                return FAILED;
            }
        }
        return SUCCEEDED;
    }
    void process() {

        Image image = null;
        try {
            synchronized (mImageNumLock) {
                if(mImageNum < 1) {
                    Log.i(TAG, "no image in queue.");
                    return;
                }
            }
            image = mImageListener.getImage(WAIT_FOR_IMAGE_TIMEOUT_MS);

            if(image == null) {
                if (mLoopHandler != null) {
                    mLoopHandler.sendEmptyMessage(0);
                }
                return;
            }

            if(mSequence) {
                processImage(image);
            } else {
                processCamPicture(image);
            }
        } catch (InterruptedException e) {
            Log.e(TAG," getImage InterruptedException ");
        } finally {
            if (image != null) {
                image.close();
                synchronized (mImageNumLock) {
                    mImageNum --;
                    mImageNumLock.notifyAll();
                    if(DEBUG) Log.i(TAG, " mImageNum -- " + mImageNum);
                }
            }
        }


    }

    private int processCamPicture(Image image) {
        if(mCamPictureCallback == null) {
            Log.w(TAG, "no picturecallback, please set CamPictureCallback...");
        }

        byte[] jpegBuf;
        String fileExtension;

        if(mCameraDeviceHandler != null) {
            mCameraDeviceHandler.obtainMessage(SpmCameraDevice.EVENT_MSG_TAKE_PICTURE_OK, ImageReaderEx.this).sendToTarget();
        }

        if(mCamImageFormat != ImageFormat.JPEG) {
            jpegBuf = compressToJpeg(image, false);
        } else {
            Image.Plane plane0 = image.getPlanes()[0];
            final ByteBuffer buffer = plane0.getBuffer();

            if (buffer.hasArray()) {
                jpegBuf = buffer.array();
            } else {
                jpegBuf = new byte[buffer.capacity()];
                buffer.get(jpegBuf);
            }
        }

        String fileName;
        int status = SUCCEEDED;

        if(mOutImageName != null) {
            fileName = mOutImageName;
        } else {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            fileExtension = ".jpeg";
            status = createDir(timeStamp);
            fileName = mOutImageDirF.getPath() + File.separator +"IMG_"+ timeStamp + fileExtension;
        }
        if (mCamPictureCallback != null) {
            mCamPictureCallback.onPictureTaken(CamPictureCallback.PICTURE_TAKEN_SAVING, mCameraId, fileName);
        }

        Log.i(TAG, "save picture:" + fileName);

        if(status == SUCCEEDED && dumpFile(fileName, jpegBuf) == SUCCEEDED) {
            status = CamPictureCallback.PICTURE_TAKEN_SUCCESS;
        } else {
            status = CamPictureCallback.PICTURE_TAKEN_FAIL;
        }

        if(mCamPictureCallback != null) {
            mCamPictureCallback.onPictureTaken(status, mCameraId, fileName);
        }

        if(mCameraDeviceHandler != null) {
            mCameraDeviceHandler.obtainMessage(SpmCameraDevice.EVENT_MSG_RELEASE_PICTURETAKEN, ImageReaderEx.this).sendToTarget();
        }

        return 0;
    }

    private int processImage(Image image) {

        byte[] data = null;

        long dataTimeStamp = SystemClock.currentTimeMicro()/1000 - SystemClock.uptimeMillis() + image.getTimestamp()/1000000;

        switch (mOutDataType) {
        case ImageDataCallback.IMAGE_DATA_RAW:
            data = getData(image);
            imageRaw(data, image.getWidth(), image.getHeight(), dataTimeStamp);
            break;
        case ImageDataCallback.IMAGE_DATA_FILE:
            data = getData(image);
            imageFile(data, dataTimeStamp);
            break;
        case ImageDataCallback.IMAGE_DATA_BUFFER:
            imageBuffer(image, image.getWidth(), image.getHeight(), dataTimeStamp);
            break;
        case ImageDataCallback.IMAGE_DATA_IMAGE:
            imageSelf(image, image.getWidth(), image.getHeight(), dataTimeStamp);
            break;
        default:
            // can not reach here
            Log.e(TAG,"error DataType format");
        }

        return 0;
    }

    private byte[] getData(Image image){
        byte[] data = null;

        switch (mOutImageFormat) {
        case ImageDataCallback.IMAGE_FORMAT_YUV_420_888:
            //yuv_420_888, default nv21, trans to yuv420
            data = getDataFromImage(image);
            break;
        case ImageDataCallback.IMAGE_FORMAT_NV21:
        case ImageDataCallback.IMAGE_FORMAT_YV12:
        case ImageDataCallback.IMAGE_FORMAT_RGB_888:
        case ImageDataCallback.IMAGE_FORMAT_YUY2:
            data = getDataFromImageNoTrans(image);
            break;
        case ImageDataCallback.IMAGE_FORMAT_JPEG:
            //only support one jpeg in camera hal.
            if(canUseJpeg()) {
                data = getDataFromImageNoTrans(image);
            } else {
                data = compressToJpeg(image, mJpegEncHw);
            }
            break;
        default:
            // can not reach here
            Log.e(TAG,"error OutImageFormat format");
        }
        return data;
    }
    private int imageRaw(byte[] data, int width, int height, long dataTimeStamp){
        if(mImageCallback != null) {
            mImageCallback.onImageAvailable(this.mCameraId, mOutImageFormat, 0, data, null);
        } else if( mImageDataCallback != null) {
            ImageDataCallbackInfo info = new ImageDataCallbackInfo(this.mCameraId, this.mOutImageFormat, 0, this.mOutDataType);
            info.setData(data);
            info.setHeight(height);
            info.setWidth(width);
            info.setTimeStamp(dataTimeStamp);
            mImageDataCallback.onImageAvailable(info);
        }

        if (DUMP) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String fileExtension = getFileExtension(mOutImageFormat);
            String fileName = mMediaStorageDir.getPath() + File.separator +"IMG_"+ timeStamp + fileExtension;
            Log.d(TAG, "dump file: " + fileName);
            dumpFile(fileName, data);
        }

        return 0;
    }

    private String getFileExtension(int imageFormat) {
        String fileExtension = ".raw";
        switch (imageFormat) {
        case ImageDataCallback.IMAGE_FORMAT_YUV_420_888:
            fileExtension = ".yuv420";
            break;
        case ImageDataCallback.IMAGE_FORMAT_JPEG:
            fileExtension = ".jpeg";
            break;
        case ImageDataCallback.IMAGE_FORMAT_NV21:
            fileExtension = ".nv21";
            break;
        case ImageDataCallback.IMAGE_FORMAT_YV12:
            fileExtension = ".yv12";
            break;
        case ImageDataCallback.IMAGE_FORMAT_RGB_888:
            fileExtension = ".rgb888";
            break;
        case ImageDataCallback.IMAGE_FORMAT_YUY2:
            fileExtension = ".yuy2";
            break;
        default:
            Log.e(TAG,"error imageFormat format");
        }
        return fileExtension;
    }
    private int imageFile(byte[] data, long dataTimeStamp){

        int status = SUCCEEDED;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());

        String fileExtension = getFileExtension(mOutImageFormat);
        String fileName = mOutImageDirF.getPath() + File.separator +"IMG_"+ timeStamp + fileExtension;

        if(DUMP)
        {
            if (mSaveImagNum++ > mMaxSaveImagNum) {
                if (mCandidataDeleteDir != null && mDeleteHandler != null) {
                    mDeleteHandler.obtainMessage(MESSAGE_DELETE_DIR, mCandidataDeleteDir).sendToTarget();
                }
                mCandidataDeleteDir = mOutImageDirF.getAbsolutePath();
                status = createDir(timeStamp);
                mSaveImagNum = 0;
            }
            status = dumpFile(fileName, data);
        }

        if(status == SUCCEEDED) {
            status = ImageCallback.IMAGE_STATUS_SUCCEEDED;
        } else {
            status = ImageCallback.IMAGE_STATUS_FAILED;
        }

        if(mImageCallback != null) {
            mImageCallback.onImageAvailable(this.mCameraId, mOutImageFormat, status, null, fileName);
        } else if(mImageDataCallback != null) {
            ImageDataCallbackInfo info = new ImageDataCallbackInfo(this.mCameraId, this.mOutImageFormat, status, this.mOutDataType);
            info.setPath(fileName);
            info.setTimeStamp(dataTimeStamp);
            mImageDataCallback.onImageAvailable(info);
        }

        return 0;
    }

    private int imageBuffer(Image image, int width, int height, long dataTimeStamp){
        if(mImageDataCallback != null) {
            final ByteBuffer buffer = image.getByteBuffer();
            ImageDataCallbackInfo info = new ImageDataCallbackInfo(this.mCameraId, this.mOutImageFormat, 0, this.mOutDataType);
            info.setBuffer(buffer);
            info.setHeight(height);
            info.setWidth(width);
            info.setTimeStamp(dataTimeStamp);
            mImageDataCallback.onImageAvailable(info);
        }

        if(DUMP_BUFFER) {
            byte[] buf;
            ByteBuffer buffer = image.getByteBuffer();
            buf = new byte[buffer.remaining()];
            buffer.get(buf);
            buffer.rewind();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String fileExtension = getFileExtension(mOutImageFormat);
            String fileName = mMediaStorageDir.getPath() + File.separator +"IMG_"+ timeStamp + fileExtension;
            dumpFile(fileName, buf);
        }

        return 0;
    }

    private int imageSelf(Image image, int width, int height, long dataTimeStamp){
        if(mImageDataCallback != null) {
            ImageDataCallbackInfo info = new ImageDataCallbackInfo(this.mCameraId, this.mOutImageFormat, 0, this.mOutDataType);
            info.setImage(image);
            info.setHeight(height);
            info.setWidth(width);
            info.setTimeStamp(dataTimeStamp);
            mImageDataCallback.onImageAvailable(info);
        }

        if(DUMP_BUFFER) {
            byte[] buf;
            ByteBuffer buffer = image.getByteBuffer();
            buf = new byte[buffer.remaining()];
            buffer.get(buf);
            buffer.rewind();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String fileExtension = getFileExtension(mOutImageFormat);
            String fileName = mMediaStorageDir.getPath() + File.separator +"IMG_"+ timeStamp + fileExtension;
            dumpFile(fileName, buf);
        }

        return 0;
    }


    /**
     * Stops ADAS
     */
    int stop() {
        close();
        return 0;
    }

    void setImageCallback(ImageCallback callback) {
        mImageCallback = callback;
    }

    void setImageDataCallback(ImageDataCallback callback) {
        mImageDataCallback= callback;
    }

    void setCamPictureCallback(CamPictureCallback callback) {
        mCamPictureCallback = callback;
    }

    private boolean isSupportFormat(int format){
        switch (format) {
        case ImageDataCallback.IMAGE_FORMAT_YUV_420_888:
        case ImageDataCallback.IMAGE_FORMAT_NV21:
        case ImageDataCallback.IMAGE_FORMAT_JPEG:
        case ImageDataCallback.IMAGE_FORMAT_YV12:
        case ImageDataCallback.IMAGE_FORMAT_RGB_888:
        case ImageDataCallback.IMAGE_FORMAT_YUY2:
            return true;
        default:
            return false;
        }
    }
    void setOutImageFormat(int imageFormat, int dataType) {
        Log.i(TAG, "imageFormat = " + imageFormat + "; dataType = " + dataType);
        mOutImageFormat = imageFormat;
        if(!isSupportFormat(imageFormat)) {
            Log.e(TAG,"do not support format " + imageFormat + " , set default yuv_420_888 " + ImageDataCallback.IMAGE_FORMAT_YUV_420_888);
            mOutImageFormat = ImageDataCallback.IMAGE_FORMAT_YUV_420_888;
        }
        mOutDataType = dataType;
    }

    void setJpegHwEnc(boolean hw) {
        Log.i(TAG, "setJpegEnc hw= " + hw);
        mJpegEncHw = hw;
    }

    boolean getJpegHwEnc() {
        return mJpegEncHw;
    }

    void setOutImageDir(String dir) {
        Log.i(TAG,"OutImageDir = " + dir);
        mOutImageDir = dir;
    }

    void setOutImageName(String fileName) {
        Log.i(TAG,"mOutImageName = " + fileName);
        mOutImageName = fileName;
    }

    void setCameraDeviceHandler(Handler handler) {
        mCameraDeviceHandler = handler;

    }
    private class ImageListener implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Image> mQueue =
            new LinkedBlockingQueue<Image>();

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                if (mStopping == true) {
                    Log.i(TAG, "stopping , skip get image......");
                    return;
                }
                synchronized (mImageNumLock) {
                    if(DEBUG) Log.i(TAG,"onImageAvailable: mImageNum = " + mImageNum);

                    while/*if*/ (mImageNum >= MAX_NUM_IMAGES) {
                        Log.i(TAG," skip get Image, mImageNum = " + mImageNum + " ; max = " + MAX_NUM_IMAGES);
                        //return ;
                        mImageNumLock.wait();
                        break;
                    }

                    /*if(!mSequence && hasPicture) {
                        Log.i(TAG, "already has picture , skip get image......");
                        return;
                    }*/
                }

                Image image = reader.acquireNextImage();
                if (image == null) {
                    Log.e(TAG, "can not acquireNextImage");
                    return ;
                }

                mQueue.put(image);
                synchronized (mImageNumLock) {
                    mImageNum ++;
                    hasPicture = true;
                }
                if (mLoopHandler != null) {
                    mLoopHandler.sendEmptyMessage(0);
                }
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                    "Can't handle InterruptedException in onImageAvailable");
            }
        }

        /**
         * Get an image from the image reader.
         *
         * @param timeout Timeout value for the wait.
         * @return The image from the image reader.
         */
        public Image getImage(long timeout) throws InterruptedException {
            Image image = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (image == null)
                Log.e(TAG, "Wait for an image timed out in " + timeout);
            return image;
        }
    }





    public void createImageReader(
        int width, int height, int format, int maxNumImages,
        ImageReader.OnImageAvailableListener listener, Handler handler)  {
        closeImageReader();
        if (width < 1 || height < 1) {
            Log.e(TAG, "The image dimensions must be positive");
            return;
        }
        if (maxNumImages < 1) {
            Log.e(TAG, "Maximum outstanding image count must be at least 1");
            return;
        }
        if (format == ImageFormat.NV21) {
            Log.e(TAG, "NV21 format is not supported");
            return;
        }

        mReader = ImageReader.newInstance(width, height, format, maxNumImages);
        mReaderSurface = mReader.getSurface();
        mReader.setOnImageAvailableListener(listener, handler);
        if (VERBOSE) {
            Log.v(TAG, String.format("Created ImageReader size (%dx%d), format %d", width, height,
                                     format));
        }
    }

    public Surface getSurface() {
        return mReaderSurface;
    }

    /**
     * Close the pending images then close current active {@link ImageReader} object.
     */
    public void closeImageReader() {
        if (mReader != null) {
            try {
                // Close all possible pending images first.
                Image image = mReader.acquireLatestImage();
                if (image != null) {
                    image.close();
                }
            } catch (IllegalStateException e) {
                Log.i(TAG, "close image, IllegalStateException, has no pending image");
            } finally {
                mReader.close();
                mReader = null;
            }
        }
    }

    public FileDescriptor enableShareBuffer(boolean enableSharebuffer, boolean enableCallback) {
        if(mReader != null) {
            int yuvType = -1;

            switch (mCamImageFormat) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
                yuvType = 3;
                break;
            case ImageFormat.YV12:
                yuvType = 1;
                break;
            case ImageFormat.YUY2:
                yuvType = 7;
                break;
            default:
                Log.e(TAG,"enableShareBuffer: error mCamImageFormat format" + mCamImageFormat);
                return null;
            }

            int size = mReader.getWidth() * mReader.getHeight() * ImageFormat.getBitsPerPixel(mCamImageFormat) / 8;
            int numPlanes = ImageUtils.getNumPlanesForFormat(mCamImageFormat);

            if (enableSharebuffer) {
                mReader.makeCallbackAndSharebufferCoexist(enableCallback);
            }
            return mReader.enableShareBuffer(enableSharebuffer, size, yuvType, numPlanes);
        }

        return null;
    }


    /**
     * Get a byte array image data from an Image object.
     * <p>
     * Read data from all planes of an Image into a contiguous unpadded,
     * unpacked 1-D linear byte array, such that it can be write into disk, or
     * accessed by software conveniently. It supports YUV_420_888/NV21/YV12
     * input Image format.
     * </p>
     * <p>
     * For YUV_420_888/NV21/YV12/Y8/Y16, it returns a byte array that contains
     * the Y plane data first, followed by U(Cb), V(Cr) planes if there is any
     * (xstride = width, ystride = height for chroma and luma components).
     * </p>
     */
    private byte[] getDataFromImage(Image image) {
        if (image == null) {
            Log.e(TAG, "Invalid image:");
            return null;
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        int rowStride, pixelStride;
        byte[] data = null;

        // Read image data
        Plane[] planes = image.getPlanes();
        if (planes == null || planes.length <= 0) {
            Log.e(TAG, "Fail to get image planes");
            return null;
        }

        // Check image validity
        if (!checkAndroidImageFormat(image)) {
            return null;
        }


        ByteBuffer buffer = null;

        int offset = 0;
        data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if(VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        for (int i = 0; i < planes.length; i++) {
            int shift = (i == 0) ? 0 : 1;
            buffer = planes[i].getBuffer();
            if (buffer == null) {
                Log.e(TAG, "Fail to get bytebuffer from plane");
                return null;
            }
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (pixelStride <= 0) {
                Log.e(TAG, "pixel stride " + pixelStride + " is invalid");
                return null;
            }
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
            }
            // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
            int w = crop.width() >> shift;
            int h = crop.height() >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            if(rowStride < w) {
                Log.e(TAG, "rowStride " + rowStride + " should be >= width " + w);
                return null;
            }
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                int length;
                if (pixelStride == bytesPerPixel) {
                    // Special case: optimized read of the entire row
                    length = w * bytesPerPixel;
                    buffer.get(data, offset, length);
                    offset += length;
                } else {
                    // Generic case: should work for any pixelStride but slower.
                    // Use intermediate buffer to avoid read byte-by-byte from
                    // DirectByteBuffer, which is very bad for performance
                    length = (w - 1) * pixelStride + bytesPerPixel;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
                // Advance buffer the remainder of the row stride
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }


    /**
     * <p>Check android image format validity for an image, only support below formats:</p>
     *
     * <p>Valid formats are YUV_420_888/NV21/YV12 for video decoder</p>
     */
    private boolean checkAndroidImageFormat(Image image) {
        int format = image.getFormat();
        //Log.i(TAG,"Image Format: " + format);
        Plane[] planes = image.getPlanes();
        boolean ret = false;
        switch (format) {
        case ImageFormat.YUV_420_888:
        case ImageFormat.NV21:
        case ImageFormat.YV12:
            if(planes.length != 3) {
                Log.e(TAG,"YUV420 format Images should have 3 planes, but palnes = " + planes.length);
            } else {
                ret = true;
            }
            break;
        default:
            Log.e(TAG, "Unsupported Image Format: " + format);
        }
        return ret;
    }

    private static int dumpFile(String fileName, byte[] data) {
        if (fileName == null || data == null) {
            Log.e(TAG, "fileName and data must not be null");
            return FAILED;
        }

        if(DEBUG) Log.v(TAG, "output will be saved as " + fileName);

        try (FileOutputStream outStream = new FileOutputStream(fileName)) {
            outStream.write(data);
        } catch (IOException ioe) {
            //throw new RuntimeException("failed writing data to file " + fileName, ioe);
            Log.e(TAG, "failed writing data to file");
            return FAILED;
        }
        return SUCCEEDED;
    }

    private void compressToJpeg(String fileName, Image image, boolean useHw) {
        Log.i(TAG," compressToJpeg: " + fileName);
        if (fileName == null || image == null) {
            Log.e(TAG, "compressToJpeg, fileName and data must not be null");
            return;
        }
        FileOutputStream outStream;
        try {
            Log.v(TAG, "output will be saved as " + fileName);
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create debug output file " + fileName, ioe);
        }

        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image,COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        if (useHw) {
            //yuvImage.compressToJpegHw(rect, 100, outStream);
        } else {
            yuvImage.compressToJpeg(rect, 100, outStream);
        }
        try {
            Log.v(TAG, "close file " + fileName);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to close debug output file " + fileName, ioe);
        }
    }

    private byte[] compressToJpeg(Image image,boolean useHw) {
        if (image == null) {
            Log.e(TAG, "compressToJpeg, data must not be null");
            return null;
        }
        ByteArrayOutputStream outStream;
        try {
            outStream = new ByteArrayOutputStream();
        } catch (Exception ioe) {
            throw new RuntimeException("Unable to create ByteArrayOutputStream ", ioe);
        }

        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image,COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        if (useHw) {
            //yuvImage.compressToJpegHw(rect, 100, outStream);
        } else {
            yuvImage.compressToJpeg(rect, 100, outStream);
        }

        return outStream.toByteArray();

    }

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
        case ImageFormat.YUV_420_888:
        case ImageFormat.NV21:
        case ImageFormat.YV12:
            return true;
        }
        return false;
    }

    private byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
            case 0:
                channelOffset = 0;
                outputStride = 1;
                break;
            case 1:
                if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height;
                    outputStride = 1;
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1;
                    outputStride = 2;
                }
                break;
            case 2:
                if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (int) (width * height * 1.25);
                    outputStride = 1;
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height;
                    outputStride = 2;
                }
                break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    private byte[] getDataFromImageNoTrans(Image image) {

        final ByteBuffer buffer = image.getByteBuffer();
        //Log.i(TAG, " buffer size = " + buffer.remaining());
        buffer.rewind();
        byte[] buf = new byte[buffer.remaining()];
        //Log.i(TAG, " buf length = " + buf.length);
        buffer.get(buf);
        buffer.rewind();
        return buf;
    }

    private boolean canUseJpeg(){
        return (mCallbackUseJpeg && mSequence) || (!mCallbackUseJpeg && !mSequence);
    }

    @Override
    protected void finalize() throws Throwable {
        Log.i(TAG," finalize: " + this);
        super.finalize();

    }
    // other public interface


}
