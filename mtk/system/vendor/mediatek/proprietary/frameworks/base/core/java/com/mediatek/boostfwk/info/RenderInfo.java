/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2021. All rights reserved.
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

package com.mediatek.boostfwk.info;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** @hide */
public class RenderInfo {
    int renderTid;
    int renderPid;
    String renderName;
    //sample:"renderA,renderB,..."
    List<String> knownDepNameList = new ArrayList<>();
    //sample:"renderATid,renderBTid,..."
    List<Integer> knownDepTidList = new ArrayList<>();

    public RenderInfo(String renderName, int renderPid) {
        this.renderName = renderName;
        this.renderPid = renderPid;
    }

    public RenderInfo() {}

    public String getRenderName() {
        return renderName;
    }

    public int getRenderTid() {
        return renderTid;
    }

    public int getRenderPid() {
        return renderPid;
    }

    public List<Integer> getKnownDepTidList() {
        return knownDepTidList;
    }

    public String getKnownDepNameStr() {
        String result = "";
        for (String name : knownDepNameList) {
            if (name != null && name != "") {
                result += (name + ",");
            }
        }
        return result;
    }

    public int getKnownDepSize() {
        return knownDepNameList.size();
    }

    @Override
    public String toString() {
        String tids = "";
        for (int tid : knownDepTidList) {
            tids += (tid + ",");
        }
        return "render[" + renderName + "," + renderTid + "," + renderPid + "]"
                + " knownDepName[" + getKnownDepNameStr() + "]"
                + " knownDepTid[" + tids + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        RenderInfo compare = (RenderInfo) obj;
        return compare.renderTid == this.renderTid && (this.renderName != null ?
                this.renderName.equals(compare.renderName) : compare.renderName == null);
    }

    @Override
    public int hashCode() {
        int result = renderName != null ? renderName.hashCode() : 0;
        result = 31 * result + renderTid;
        return result;
    }
}
