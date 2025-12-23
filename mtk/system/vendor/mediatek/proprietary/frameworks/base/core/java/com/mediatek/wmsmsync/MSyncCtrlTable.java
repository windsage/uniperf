/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.wmsmsync;

import android.util.Log;
import android.util.Slog;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Cache of MSyncCtrlTable list and provide the operation of the cache
 **/
public class MSyncCtrlTable {
    private static final String TAG = "MSyncCtrlTable";
    private static final String APP_LIST_PATH = "system/etc/msync_ctrl_table.xml";
    /**
     * format of app list XML file
     **/
    private static final String TAG_APP = "app";
    private static final String NODE_PACKAGE_NAME = "packagename";
    private static final String NODE_SLIDE_RESPONSE = "slideresponse";
    private static final String NODE_DEFAULT_FPS = "defaultfps";
    private static final String ARRAY_ACTIVITY = "activities";
    private static final String NODE_ACTIVITY = "activity";
    private static final String NODE_ACTIVITY_NAME = "name";
    private static final String NODE_ACTIVITY_FPS = "fps";
    private static final String NODE_IME_FPS = "imefps";
    private static final String NODE_VOICE_FPS = "voicefps";

    private static final String NODE_IME_GLOBAL_CONTROL = "enableimeglobalcontrol";
    private static final String NODE_IME_DEFAULT_FPS = "defaultimefps";

    private static final String NODE_VOICE_GLOBAL_CONTROL = "enablevoiceglobalcontrol";
    private static final String NODE_VOICE_DEFAULT_FPS = "defaultvoicefps";

    private static final String NODE_GLOBAL_FPS = "globalfps";


    private boolean mEnableImeGlobalFpsControl = false;
    private boolean mEnableVoiceGlobalFpsControl = false;
    private float mDefaultImeFps;
    private float mDefaultVoiceFps;
    private float mGlobalFPS;

    private volatile static MSyncCtrlTable sInstance;
    private ArrayList<MSyncCtrlBean> mMSyncAppCache;
    private boolean mIsRead = false;
    private static final Object LOCK = new Object();

    public ArrayList<MSyncCtrlBean> getMSyncAppCache() {
        return mMSyncAppCache;
    }

    public static MSyncCtrlTable getInstance() {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new MSyncCtrlTable();
                }
            }
        }
        return sInstance;
    }

    private MSyncCtrlTable() {
    }

    public boolean isRead() {
        return mIsRead;
    }

    public boolean isEnableImeGlobalFpsControl() {
        return mEnableImeGlobalFpsControl;
    }

    public boolean isEnableVoiceGlobalFpsControl() {
        return mEnableVoiceGlobalFpsControl;
    }

    public float getDefaultImeFps() {
        return mDefaultImeFps;
    }

    public float getDefaultVoiceFps() {
        return mDefaultVoiceFps;
    }

    public float getGlobalFPS() {
        return mGlobalFPS;
    }

    /**
     * load the xml in cache list
     */
    public void loadMSyncCtrlTable() {
        Slog.d(TAG, "loadMSyncCtrlTable + ");
        File target = null;
        InputStream inputStream = null;
        try {
            target = new File(APP_LIST_PATH);
            if (!target.exists()) {
                Slog.e(TAG, "Target file doesn't exist: " + APP_LIST_PATH);
                return;
            }
            inputStream = new FileInputStream(target);
            mMSyncAppCache = parseAppListFile(inputStream);
            mIsRead = true;
        } catch (IOException e) {
            Slog.w(TAG, "IOException", e);
        } finally {
            try {
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                Slog.w(TAG, "close failed..", e);
            }
        }
        Slog.d(TAG, "loadMSyncCtrlTable - ");
    }

    private ArrayList<MSyncCtrlBean> parseAppListFile(InputStream is) {
        ArrayList<MSyncCtrlBean> list = new ArrayList();
        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(is);
        } catch (ParserConfigurationException e) {
            Log.w(TAG, "dom2xml ParserConfigurationException", e);
            return list;
        } catch (SAXException e) {
            Log.w(TAG, "dom2xml SAXException", e);
            return list;
        } catch (IOException e) {
            Log.w(TAG, "IOException", e);
            return list;
        }
        //get IME Global Control & voice Global
        try {
            mEnableImeGlobalFpsControl = Boolean.parseBoolean(document
                    .getElementsByTagName(NODE_IME_GLOBAL_CONTROL).item(0).getTextContent());
            mEnableVoiceGlobalFpsControl = Boolean.parseBoolean(document
                    .getElementsByTagName(NODE_VOICE_GLOBAL_CONTROL).item(0).getTextContent());
            if (mEnableImeGlobalFpsControl) {
                mDefaultImeFps = Float.parseFloat(document
                        .getElementsByTagName(NODE_IME_DEFAULT_FPS).item(0).getTextContent());
            }
            if (mEnableVoiceGlobalFpsControl) {
                mDefaultVoiceFps =
                        Float.parseFloat(document.getElementsByTagName(NODE_VOICE_DEFAULT_FPS)
                                .item(0).getTextContent());
            }
            mGlobalFPS = Float.parseFloat(document
                    .getElementsByTagName(NODE_GLOBAL_FPS).item(0).getTextContent());
            Slog.d(TAG, "mEnableIMEGlobalFPSControl = " + mEnableImeGlobalFpsControl
                    + " | mDefaultIMEFPS = " + mDefaultImeFps + "\n" +
                    "mEnableVoiceGlobalFPSControl = " + mEnableVoiceGlobalFpsControl
                    + " | mDefaultVoiceFPS = " + mDefaultVoiceFps + "\n" +
                    "mGlobalFPS = " + mGlobalFPS);
            // get app list
            NodeList appList = document.getElementsByTagName(TAG_APP);
            //travesal app tag
            for (int i = 0; i < appList.getLength(); i++) {
                Node nodeApps = appList.item(i);
                NodeList childNodes = nodeApps.getChildNodes();
                MSyncCtrlBean MSyncCtrlBean = new MSyncCtrlBean();
                for (int j = 0; j < childNodes.getLength(); j++) {
                    Node childNode = childNodes.item(j);
                    switch (childNode.getNodeName()) {
                        case NODE_PACKAGE_NAME:
                            String packageName = childNode.getTextContent();
                            MSyncCtrlBean.setPackageName(packageName);
                            break;
                        case NODE_SLIDE_RESPONSE:
                            String slideResponse = childNode.getTextContent();
                            MSyncCtrlBean
                                    .setSlideResponse(Boolean.parseBoolean(slideResponse));
                            break;
                        case NODE_DEFAULT_FPS:
                            String defaultFps = childNode.getTextContent();
                            MSyncCtrlBean.setFps(Float.parseFloat(defaultFps));
                            break;
                        case NODE_IME_FPS:
                            String imeFps = childNode.getTextContent();
                            MSyncCtrlBean.setImeFps(Float.parseFloat(imeFps));
                            break;
                        case NODE_VOICE_FPS:
                            String voiceFps = childNode.getTextContent();
                            MSyncCtrlBean.setVoiceFps(Float.parseFloat(voiceFps));
                            break;
                        case ARRAY_ACTIVITY:
                            NodeList grandSunNodes = childNode.getChildNodes();
                            List<MSyncCtrlBean.ActivityBean> activityBeanList
                                    = new ArrayList<>();
                            for (int k = 0; k < grandSunNodes.getLength(); k++) {
                                Node grandSunNode = grandSunNodes.item(k);
                                if (NODE_ACTIVITY.equals(grandSunNode.getNodeName())) {
                                    NodeList grandGrandSunNodes = grandSunNode.getChildNodes();
                                    MSyncCtrlBean.ActivityBean activityBean
                                            = new MSyncCtrlBean.ActivityBean();
                                    for (int l = 0; l < grandGrandSunNodes.getLength(); l++) {
                                        Node grandGrandSunNode = grandGrandSunNodes.item(l);
                                        switch (grandGrandSunNode.getNodeName()) {
                                            case NODE_ACTIVITY_NAME:
                                                String activityName
                                                        = grandGrandSunNode.getTextContent();
                                                activityBean.setName(activityName);
                                                break;
                                            case NODE_ACTIVITY_FPS:
                                                String activityFps
                                                        = grandGrandSunNode.getTextContent();
                                                activityBean.setFps(Float.parseFloat(activityFps));
                                                break;
                                            case NODE_IME_FPS:
                                                String atyImeFps = grandGrandSunNode
                                                        .getTextContent();
                                                activityBean
                                                        .setImeFps(Float.parseFloat(atyImeFps));
                                                break;
                                            case NODE_VOICE_FPS:
                                                String atyVoiceFps
                                                        = grandGrandSunNode.getTextContent();
                                                activityBean.setVoiceFps(Float
                                                        .parseFloat(atyVoiceFps));
                                                break;
                                        }
                                    }
                                    activityBeanList.add(activityBean);
                                }
                            }
                            MSyncCtrlBean.setActivityBeans(activityBeanList);
                            break;
                        default:
                            break;
                    }
                }
                list.add(MSyncCtrlBean);
                Log.d(TAG, "MSyncCtrlTableBean dom2xml: " + MSyncCtrlBean);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList();
        }
        return list;
    }
}
