/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.powerhalmgr;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Information about a Booster.
 *
 * @hide
 */
public class BoosterInfo implements Parcelable {

    /**
     * Booster base version, non function.
     */
    public static int BOOSTER_GROUP_BASE = 0;

    /**
     * Booster version constant.
     */
    public static final int BOOSTER_GROUP_A                     = BOOSTER_GROUP_BASE + 1;
    public static final int BOOSTER_GROUP_B                     = BOOSTER_GROUP_BASE + 2;
    public static final int BOOSTER_GROUP_C                     = BOOSTER_GROUP_BASE + 3;
    public static final int BOOSTER_GROUP_D                     = BOOSTER_GROUP_BASE + 4;

    /**
     * Booster Max version. Why only 4 verion? Because MARK bit limitation.
     */
    public static final int BOOSTER_GROUP_MAX                     = BOOSTER_GROUP_D;

    /**
     * Booster base action, non action.
     */
    public static final int BOOSTER_ACTION_BASE = 0;

    /**
     * Booster action, set accelerator by UID.
     */
    public static final int BOOSTER_ACTION_ADD_BY_UID             = BOOSTER_ACTION_BASE + 1;

    /**
     * Booster action, del accelerator by UID.
     */
    public static final int BOOSTER_ACTION_DEL_BY_UID             = BOOSTER_ACTION_BASE + 2;

    /**
     * Booster action, del all accelerators based on UID.
     */
    public static final int BOOSTER_ACTION_DEL_BY_UID_ALL         = BOOSTER_ACTION_BASE + 3;

    /**
     * Booster action, set accelerator by LINKINFO.
     */
    public static final int BOOSTER_ACTION_ADD_BY_LINKINFO        = BOOSTER_ACTION_BASE + 4;

    /**
     * Booster action, del accelerator by LINKINFO.
     */
    public static final int BOOSTER_ACTION_DEL_BY_LINKINFO        = BOOSTER_ACTION_BASE + 5;

    /**
     * Booster action, del all accelerators based on LINKINFO.
     */
    public static final int BOOSTER_ACTION_DEL_BY_LINKINFO_ALL    = BOOSTER_ACTION_BASE + 6;

    /**
     * Booster action, del all accelerators.
     * Note: only support del all accelerators within a group.
     *       cannot support del all accelerators in all group at once.
     *       please del all accelerators one by one group if you want.
     */
    public static final int BOOSTER_ACTION_DEL_ALL                = BOOSTER_ACTION_BASE + 7;

    private int mGroup = -1;
    private int mAction = -1;
    private int mUid = -1;
    /**
     * mSrcIp and mDstIp should be the same IP version.
     * 0.0.0.0/0 (ipv4) and ::/0 (ipv6) mean to match all IP address.
     */
    private String mSrcIp = null;
    private String mDstIp = null;
    /**
     * Only be valid for tcp and udp of mProto, -1 for any port.
     */
    private int mSrcPort = -1;
    private int mDstPort = -1;
    /**
     * 1: tcp, 2: udp, -1: any.
     */
    private int mProto = -1;
    private String[] mMoreInfo = null;
    private int[] mMoreValue = null;
    /**
     * Booster copy constructor.
     * @param info to be config.
     */
    public BoosterInfo(BoosterInfo info) {
        this(info.mGroup, info.mAction, info.mUid, info.mSrcIp, info.mDstIp, info.mSrcPort,
                info.mDstPort, info.mProto, info.mMoreInfo, info.mMoreValue);
    }

    /**
     * Booster constructor, backward compatible with v1.0 (setPriorityByUid).
     * @param group to be config.
     * @param action what to do.
     * @param uid application uid.
     */
    public BoosterInfo(int group, int action, int uid) {
        this(group, action, uid, null, null, -1, -1, -1, null, null);
    }

    /**
     * Booster constructor, backward compatible with v1.0 (setPriorityByLinkinfo).
     * @param group to be config.
     * @param action what to do.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     */
    public BoosterInfo(int group, int action, String srcIp, String dstIp, int srcPort, int dstPort,
        int proto) {
        this(group, action, -1, srcIp, dstIp, srcPort, dstPort, proto, null, null);
    }

    /**
     * Booster constructor, backward compatible with v1.0 (flushPriorityRules).
     * @param group to be config.
     * @param action what to do.
     */
    public BoosterInfo(int group, int action) {
        this(group, action, -1, null, null, -1, -1, -1, null, null);
    }

    /**
     * Booster constructor, uid and five-tuple.
     * @param group to be config.
     * @param action what to do.
     * @param uid application uid.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     */
    public BoosterInfo(int group, int action, int uid, String srcIp, String dstIp, int srcPort,
        int dstPort, int proto) {
        this(group, action, uid, srcIp, dstIp, srcPort, dstPort, proto, null, null);
    }

    /**
     * Booster constructor, more info only.
     * @param group to be config.
     * @param action what to do.
     * @param sa string array for more information.
     * @param ia int array for more information.
     */
    public BoosterInfo(int group, int action, String[] sa, int[] ia) {
        this(group, action, -1, null, null, -1, -1, -1, sa, ia);
    }

    /**
     * Booster constructor, all parameters.
     * @param group to be config.
     * @param action what to do.
     * @param uid application uid.
     * @param srcIp source ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param dstIp destination ip, 0.0.0.0/0 and ::/0 to bypass it.
     * @param srcPort source port, -1 for any and only valid for protocl with 1 or 2.
     * @param dstPort destination port, -1 for any and only valid for protocl with 1 or 2.
     * @param proto protocol, 1 for tcp, 2 for udp and -1 for any.
     * @param sa string array for more information.
     * @param ia int array for more information.
     */
    public BoosterInfo(int group, int action, int uid, String srcIp, String dstIp, int srcPort,
        int dstPort, int proto, String[] sa, int[] ia) {
        this.mGroup = group;
        this.mAction = action;
        this.mUid = uid;
        this.mSrcIp = srcIp;
        this.mDstIp = dstIp;
        this.mSrcPort = srcPort;
        this.mDstPort = dstPort;
        this.mProto = proto;
        this.mMoreInfo = sa;
        this.mMoreValue = ia;
    }

    private BoosterInfo(Parcel in) {
        this.readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getGroup() {
        return mGroup;
    }

    public int getAction() {
        return mAction;
    }

    public int getUid() {
        return mUid;
    }

    public String getSrcIp() {
        return mSrcIp;
    }

    public String getDstIp() {
        return mDstIp;
    }

    public int getSrcPort() {
        return mSrcPort;
    }

    public int getDstPort() {
        return mDstPort;
    }

    public int getProto() {
        return mProto;
    }

    public String[] getMoreInfo() {
        return mMoreInfo;
    }

    public int[] getMoreValue() {
        return mMoreValue;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mGroup);
        out.writeInt(mAction);
        out.writeInt(mUid);
        out.writeString(mSrcIp);
        out.writeString(mDstIp);
        out.writeInt(mSrcPort);
        out.writeInt(mDstPort);
        out.writeInt(mProto);
        out.writeStringArray(mMoreInfo);
        out.writeIntArray(mMoreValue);
    }

    private void readFromParcel(Parcel in) {
        mGroup = in.readInt();
        mAction = in.readInt();
        mUid = in.readInt();
        mSrcIp = in.readString();
        mDstIp = in.readString();
        mSrcPort = in.readInt();
        mDstPort = in.readInt();
        mProto = in.readInt();
        mMoreInfo = in.createStringArray();
        mMoreValue = in.createIntArray();
    }

    public static final Parcelable.Creator<BoosterInfo> CREATOR =
            new Parcelable.Creator<BoosterInfo>() {
        @Override
        public BoosterInfo createFromParcel(Parcel in) {
            return new BoosterInfo(in);
        }

        @Override
        public BoosterInfo[] newArray(int size) {
            return new BoosterInfo[size];
        }
    };

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BoosterInfo(");
        sb.append(mGroup);
        sb.append(",");
        sb.append(mAction);
        sb.append(",");
        sb.append(mUid);
        sb.append(",");
        sb.append(mSrcIp);
        sb.append(",");
        sb.append(mDstIp);
        sb.append(",");
        sb.append(mSrcPort);
        sb.append(",");
        sb.append(mDstPort);
        sb.append(",");
        sb.append(mProto);
        sb.append(",");
        sb.append(Arrays.toString(mMoreInfo));
        sb.append(",");
        sb.append(Arrays.toString(mMoreValue));
        sb.append(")");
        return sb.toString();
    }
}
