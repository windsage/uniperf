package com.mediatek.mbrainlocalservice.charge;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.SystemProperties;
import android.util.Slog;

public class ChargeHelper {
    private static ChargeStatus mChargeStatus = ChargeStatus.UNKNOWN;
    private static ChargeApporach mChargeApproach = ChargeApporach.UNKNOWN;
    private static int mBatteryLevel;
    private static final int DEFAULT_BATTERY_CHANGE_GAP =2;

    public static boolean updateInfo(Intent intent) {
        if (intent == null) {
            return false;
        }

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isStatusChanged = updateChargeStatus(status);

        int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isApproachChanged = updateChargeApproach(plugType);

        int batteryLevel = intent.getIntExtra("level", 0);
        boolean isBatterLevelChanged = updateBatteryLevel(batteryLevel);

        boolean isChanged = isStatusChanged || isApproachChanged || isBatterLevelChanged;
        return isChanged;
    }

    public static int getStatus() {
        return mChargeStatus.getValue();
    }

    public static int getApproach() {
        return mChargeApproach.getValue();
    }

    public static int getLevel() {
        return mBatteryLevel;
    }

    public static int convertChargeStatus(int status) {
        return getChargeStatus(status).getValue();
    }

    public static ChargeStatus getChargeStatus(int status) {
        ChargeStatus chargeStatus = ChargeStatus.UNKNOWN;
        switch(status) {
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                chargeStatus = ChargeStatus.NOT_CHARGE;
                break;
            case BatteryManager.BATTERY_STATUS_CHARGING:
                chargeStatus = ChargeStatus.CHARGE;
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                chargeStatus = ChargeStatus.DISCHARGE;
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                chargeStatus = ChargeStatus.FULL;
                break;
             case BatteryManager.BATTERY_STATUS_UNKNOWN:
                chargeStatus = ChargeStatus.UNKNOWN;
                break;
            default:
                chargeStatus = ChargeStatus.UNKNOWN;
                break;
        }
        return chargeStatus;
    }

    private static boolean updateChargeStatus(int status) {
        ChargeStatus currentChargeStatus = getChargeStatus(status);
        boolean isStatusChanged = (currentChargeStatus != mChargeStatus);
        mChargeStatus = currentChargeStatus;
        return isStatusChanged;
    }

    private static boolean updateChargeApproach(int plugType) {
        ChargeApporach currentChargeApproach = mChargeApproach;
        switch(plugType) {
            case BatteryManager.BATTERY_PLUGGED_USB:
                currentChargeApproach = ChargeApporach.USB;
                break;
            case BatteryManager.BATTERY_PLUGGED_AC:
                currentChargeApproach = ChargeApporach.AC;
                break;
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                currentChargeApproach = ChargeApporach.WIRELESS;
                break;
            case BatteryManager.BATTERY_PLUGGED_DOCK:
                currentChargeApproach = ChargeApporach.DOCKER;
                break;
            case 0:
                currentChargeApproach = ChargeApporach.BATTERY;
                break;
            default:
                currentChargeApproach = ChargeApporach.UNKNOWN;
                break;
        }
        boolean isApproachChanged = (currentChargeApproach != mChargeApproach);
        mChargeApproach = currentChargeApproach;
        return isApproachChanged;
    }

    private static boolean updateBatteryLevel(int batteryLevel) {
        boolean isBatteryLevelChanged = Math.abs(mBatteryLevel - batteryLevel) >= getBatteryLevelReportGapSetting();
        if (isBatteryLevelChanged) {
            mBatteryLevel = batteryLevel;
        }

        return isBatteryLevelChanged;
    }

    private static int getBatteryLevelReportGapSetting() {
        return SystemProperties.getInt("vendor.mbrain.battery" , DEFAULT_BATTERY_CHANGE_GAP);
    }


    enum ChargeStatus {
        NOT_CHARGE(0),
        CHARGE(1),
        DISCHARGE(2),
        FULL(3),
        UNKNOWN(4);
        
        private int value;
        private ChargeStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    enum ChargeApporach {
        USB(0),
        AC(1),
        WIRELESS(2),
        DOCKER(3),
        UNKNOWN(4),
        BATTERY(5);

        private int value;
        private ChargeApporach(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
