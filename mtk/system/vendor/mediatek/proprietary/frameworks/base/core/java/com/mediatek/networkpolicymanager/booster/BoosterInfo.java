/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2023. All rights reserved.
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

package com.mediatek.networkpolicymanager.booster;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * Information about a Booster.
 *
 * Note: This feature has SDK for 3rd party customer.
 *
 *       So:
 *       You mustn't modify the current variables and their types.
 *       You can add new variable.
 *       The newly added variable must be at end of writeToParcel and readFromParcel.
 *       You can delete the existed variable, but you must keep the variable field for it
 *       in functions writeToParcel and readFromParcel.
 *
 *       For delete case, strongly suggest you update SDK and publish to customer in time
 *       because delete case is not backward compatibility.
 *
 *       Special note:
 *           please add mark to describe for your deleted variable, especially last one.
 *
 *       Example
 *           2023.01.01, version 1
 *           Variable: int a = 0;
 *           Variable: String b = 1; it is just the last variable
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString(b);
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               b = in.readString();
 *           }
 *
 *           2023.03.01, version 2
 *           Variable: int a = 0;
 *           (Variable: // String b = 1; it is just the last variable)
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString("");
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               in.readString();
 *           }
 *
 *           2023.04.01, version 3
 *           Variable: int a = 0;
 *           (Variable: // String b = 1; it is just the last variable)
 *           Variable: int c = 0;
 *           @Override
 *           public void writeToParcel(Parcel out, int flags) {
 *               out.writeInt(a);
 *               out.writeString("");
 *               out.writeInt(c);
 *           }
 *
 *           private void readFromParcel(Parcel in) {
 *               a = in.readInt();
 *               in.readString();
 *               c = in.readInt();
 *           }
 *
 *       So:
 *       You mustn't modify the current constant variables and their values.
 *       You must set new value for your newly added constant variable.
 *       The newly added constant variable value must be increasing.
 *       The newly added constant variable value mustn't duplicate the existing one.
 *       You can delete the existed constant variable, but you must keep the constant variable
 *       value for it.
 *       The newly added constant variable value mustn't duplicate the keeped constant variable
 *       value for deleted constant variable.
 *
 *       For delete case, strongly suggest you update SDK and publish to customer in time
 *       because delete case is not backward compatibility.
 *
 *       Special note:
 *           please add mark to describe for your deleted constant variable, especially last one.
 *
 *       Example
 *           2023.01.01, version 1
 *           public static final int BOOSTER_GROUP_A     = BOOSTER_GROUP_BASE + 1;
 *           // It is just the last constant variable
 *           public static final int BOOSTER_GROUP_B     = BOOSTER_GROUP_BASE + 2;
 *
 *           2023.03.01, version 2
 *           public static final int BOOSTER_GROUP_A     = BOOSTER_GROUP_BASE + 1;
 *           // keep the deleted constant variable here
 *           // It is just the last constant variable
 *           // public static final int BOOSTER_GROUP_B  = BOOSTER_GROUP_BASE + 2;
 *
 *           2023.04.01, version 3
 *           public static final int BOOSTER_GROUP_A     = BOOSTER_GROUP_BASE + 1;
 *           // keep the deleted constant variable here
 *           // It is just the last constant variable
 *           // public static final int BOOSTER_GROUP_B  = BOOSTER_GROUP_BASE + 2;
 *           public static final int BOOSTER_GROUP_C     = BOOSTER_GROUP_BASE + 3;
 *
 *@hide
 */
public class BoosterInfo implements Parcelable {

    /**
     * Booster base version, non function, that is to say it is unsupported.
     */
    public static final int BOOSTER_VERSION_BASE = 0;

    /**
     * Booster version constant, for underlying function upgrages.
     */
    public static final int BOOSTER_VERSION_1             = BOOSTER_VERSION_BASE + 1;

    /**
     * Booster base version, non function.
     */
    public static final int BOOSTER_GROUP_BASE = 0;

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

    /**
     * Booster action, used to debug or pre-research.
     * Note: put your command with parameters in a String[].
     * Format {cmd string, parameter string}. cmd string is necessary. User defines detail content.
     * sample: String[] command = {"priority_set_toup", "192.168.1.100 80 192.168.1.101 80 TCP"}.
     * sample: String[] command = {"add_reset_iptable", "iptables -A OUTPUT -j drop"}.
     */
    public static final int BOOSTER_ACTION_DBG_EXT                = BOOSTER_ACTION_BASE + 8;

    /**
     * Booster action, only used by platform to enable/disable (switch) or query Booster status.
     * Note: put your operation with parameters in a int[].
     * null for operation 0. Valid value of val[0] is [-1, 1].
     * Format {operationNumber}. -1 for disable, 1 for enable, 0 for query.
     * sample: int[] optr = {0}.
     */
    public static final int BOOSTER_ACTION_FEA_OPS                = BOOSTER_ACTION_BASE + 9;

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
