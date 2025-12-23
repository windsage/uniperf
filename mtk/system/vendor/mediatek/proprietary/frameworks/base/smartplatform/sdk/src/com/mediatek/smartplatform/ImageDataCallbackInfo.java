package com.mediatek.smartplatform;

import java.nio.ByteBuffer;


public class ImageDataCallbackInfo {
    private final static String TAG = "ImageDataCallbackInfo";

    private String mCameraId;
    private int mImageFormat;
    private int mStatus;
    private byte[] mData;
    private ByteBuffer mBuffer;
    private Image mImage;
    private String mPath;
    private int mType;
    private int mWidth;
    private int mHeight;
    private long mTimestamp;


    ImageDataCallbackInfo() {
    }
    
    ImageDataCallbackInfo(String cameraId, int imageFormat, int status, int type) {
        mCameraId = cameraId;
        mImageFormat = imageFormat;
        mStatus = status;
        mType = type;
    }

    void setCameraId(String cameraId) {
        mCameraId = cameraId;
    }

    void setImageFormat (int imageFormat){
        mImageFormat = imageFormat;
    }

    void setStatus(int status) {
        mStatus = status;
    }

    void setData(byte[] data) {
        mData = data;
    }

    void setBuffer(ByteBuffer buffer) {
        mBuffer = buffer;
    }

    void setImage(Image image) {
        mImage = image;
    }

    void setPath(String path){
        mPath = path;
    }

    void setType(int type) {
        mType = type;
    }

    void setWidth(int width) {
        mWidth = width;
    }

    void setHeight(int height) {
        mHeight = height;
    }

    void setTimeStamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public String getCameraId(){
        return mCameraId;
    }

    public int getImageFormat(){
        return mImageFormat;
    }

    public int getStatus(){
        return mStatus;
    }

    public byte[] getData(){
        return mData;
    }

    public ByteBuffer getBuffer(){
        return mBuffer;
    }

    public Image getImage() {
        return mImage;
    }

    public String getPath(){
        return mPath;
    }

    public int getType(){
        return mType;
    }
    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public long getTimeStamp() {
        return mTimestamp;
    }

}

