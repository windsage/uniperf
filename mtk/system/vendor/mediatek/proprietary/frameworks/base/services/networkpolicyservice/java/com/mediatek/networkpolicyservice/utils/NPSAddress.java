/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package com.mediatek.networkpolicyservice.utils;

import android.util.Log;

import com.mediatek.networkpolicymanager.NetworkPolicyManager;

import java.util.regex.Pattern;

/**
 * A collection of utilities relating to InetAddresses.
 *
 * @since 4.0
 */
public class NPSAddress {

    private static final String TAG = "NPSAddress";
    // The below spelling is simply to avoid coverity check.
    private static final String sIpv4AddrInvalid = "0.0.0.0";
    private static final String sIpv4AddrBroadAll = "255.255.255.255";

    private static final int IP_FORMAT_UNKONWN = 0;
    private static final int IP_FORMAT_IPV4    = 1;
    private static final int IP_FORMAT_IPV6    = 2;

    private static final Pattern IPV4_PATTERN = Pattern
        .compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\." +
        "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    private static final Pattern IPV6_STD_PATTERN = Pattern
        .compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");

    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern
        .compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::" +
        "((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    private static final Pattern IPV6_IPV4_COMPATIBLE_PATTERN = Pattern
        .compile("^::[fF]{4}:(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\." +
        "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");

    /**
     * Check whether IP is IPV4 address.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv4Address(final String input) {
        return IPV4_PATTERN.matcher(input).matches();
    }

    /**
     * Check whether IP is IPV4 source address.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv4SourceAddress(final String input) {
        return (input.equals("0.0.0.0/0") == true ||
            (isIPv4Address(input) && input.equals(sIpv4AddrInvalid) == false));
    }

    /**
     * Check whether IP is IPV4 multicast address.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv4MulticastAddress(final String input) {
        String[] items = input.split("\\.");

        if (items.length > 0) {
            int ipStartValue = Integer.parseInt(items[0]);

            /* 224.0.0.0 ~ 239.255.255.255 */
            if (ipStartValue >= 224 && ipStartValue <= 239) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether IP is valid.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv6StdAddress(final String input) {
        return IPV6_STD_PATTERN.matcher(input).matches();
    }

    /**
     * Check whether IP is valid.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv6HexCompressedAddress(final String input) {
        return IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches();
    }

    /**
     * Check whether IP is valid.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv6IPv4CompatibleAddress(final String input) {
        return IPV6_IPV4_COMPATIBLE_PATTERN.matcher(input).matches();
    }
    /**
     * Check whether IP is valid.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv6Address(final String input) {
        return (isIPv6StdAddress(input) || isIPv6HexCompressedAddress(input) ||
                isIPv6IPv4CompatibleAddress(input));
    }

    /**
     * Check whether IP is valid.
     *
     * @param input IP address to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIPv6SourceAddress(final String input) {
        return (input.equals("::/0") == true || isIPv6Address(input));
    }

    /**
     * Check whether IP and port are valid.
     *
     * @param srcIp source IP to check.
     * @param dstIp destination IP to check.
     * @param srcPort source port to check.
     * @param dstPort destination port to check.
     * @return true for valid, false for invalid.
     */
    public static boolean isIpPairValid(final String srcIp,
        final String dstIp, int srcPort, int dstPort) {
        int srcFormat = 0;
        int dstFormat = 0;

        /* port */
        /* srcPort -1 means not assigned */
        if (srcPort < -1 || srcPort > 65535 || dstPort < -1 || dstPort > 65535) {
            logd("invalid port, src:" + srcPort + ", dst:" + dstPort);
            return false; /* unknown format */
        }

        if (isIPv4SourceAddress(srcIp)) {
            srcFormat = IP_FORMAT_IPV4;
        } else if (isIPv6SourceAddress(srcIp)) {
            srcFormat = IP_FORMAT_IPV6;
        } else {
            logd("srcIp unknown:" + srcIp);
            return false; /* unknown format */
        }

        if (isIPv4Address(dstIp)) {
            dstFormat = IP_FORMAT_IPV4;
        } else if (isIPv6Address(dstIp)) {
            dstFormat = IP_FORMAT_IPV6;
        } else {
            logd("dstip unknown:" + dstIp);
            return false; /* unknown format */
        }

        /* source and destination are not matched */
        if (srcFormat != dstFormat) {
            logd("not match, srcIp:" + srcIp + ", dstIp:" + dstIp);
            return false;
        }

        /* destination check */
        if (dstFormat == IP_FORMAT_IPV4) {

            /* loopback address */
            if (dstIp.startsWith("127")) {
                logd("violate, loopback address, dstIp:" + dstIp);
                return false;
            }

            /* broadcast */
            if (dstIp.equals(sIpv4AddrBroadAll) == true) {
                logd("violate, broadcast, dstIp:" + dstIp);
                return false;
            }

            /* multicasting */
            if (isIPv4MulticastAddress(dstIp)) {
                logd("violate, multicasting, dstIp:" + dstIp);
                return false;
            }
        }
        return true;
    }

    /**
     * Get current function name, like C++ __FUNC__.
     * NOTE: StackTraceElement[0] is getStackTrace
     *       StackTraceElement[1] is getMethodName
     *       StackTraceElement[2] is logx
     *       StackTraceElement[3] is function name
     *       StackTraceElement[4] is function name with encapsulated layer
     * @return caller function name.
     */
    private static String getMethodName(boolean moreDepth) {
        int layer = 3;
        if (NetworkPolicyManager.NAME_LOG > 0) {
            if (moreDepth) {
                layer = 4;
            }
            return Thread.currentThread().getStackTrace()[layer].getMethodName();
        }
        return null;
    }

    /**
     * Encapsulated d levevl log function.
     */
    private static void logd(String message) {
        Log.d(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated i levevl log function.
     */
    private static void logi(String message) {
        Log.i(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     */
    private static void loge(String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(false) + ":" + message);
    }

    /**
     * Encapsulated e levevl log function.
     * NOTE: Only used in encapsulation check function
     */
    private static void loge(boolean moreDepth, String message) {
        Log.e(TAG, "JXNPS: " + getMethodName(moreDepth) + ":" + message);
    }
}

