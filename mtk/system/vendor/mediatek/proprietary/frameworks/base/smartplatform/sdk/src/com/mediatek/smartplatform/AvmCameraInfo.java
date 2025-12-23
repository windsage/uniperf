package com.mediatek.smartplatform;


/**
     * use for AvmCameraInfo params
     */
public class AvmCameraInfo {
	private int mWidth;
	private int mHeight;

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setWidth(int w) {
        mWidth = w;
    }

    public void setHeight(int h) {
        mHeight = h;
    }

    AvmCameraInfo() {
        mWidth = 1280;
        mHeight = 720;
    }
}


