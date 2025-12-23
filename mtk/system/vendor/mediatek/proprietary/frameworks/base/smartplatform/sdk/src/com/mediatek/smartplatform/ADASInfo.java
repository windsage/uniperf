package com.mediatek.smartplatform;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

public class ADASInfo implements Parcelable {

    public ADASInfo() {
        leftLanePos[0] = new Point(0, 0);
        leftLanePos[1] = new Point(0, 0);
        rightLanePos[0] = new Point(0, 0);
        rightLanePos[1] = new Point(0, 0);
    }

    public static final int SeverityNone            = 0;
    public static final int SeverityINFO            = 1;
    public static final int SeverityATTENTION       = 2;
    public static final int SeverityDanger          = 3;

    public static final int TypeNone                = 0;
    public static final int TypeSolidLineCrossing   = 1;
    public static final int TypeDashedLineCrossing  = 2;

    public static final int ObjectTypeNone          = 0;
    public static final int ObjectTypeVehicle       = 1;
    public static final int ObjectTypePedestrian    = 2;

    public static final int DRIVER_ATTENTION_LEFT = 0;
    public static final int DRIVER_ATTENTION_FORWARD = 1;
    public static final int DRIVER_ATTENTION_MIDDLE = 2;
    public static final int DRIVER_ATTENTION_RIGHT = 3;
    public static final int DRIVER_ATTENTION_DOWN = 4;
    public static final int DRIVER_ATTENTION_ABSENT =5;

    /**
     * Warning that the car is too close to left lane.
     */
    public int leftLaneWarning;
    /**
     * Warning that the car is too close to right lane.
     */
    public int rightLaneWarning;

    /**
       * The lane offset and its value is in the range of
       * <ul>
       * <li>{@link SeverityNone}
       * <li>{@link SeverityINFO}
       * <li>{@link SeverityATTENTION}
       * <li>{@link SeverityDanger}
       * </ul>
       */
    public int laneSeverity;

    /**
       * The line type and its value is in the range of
       * <ul>
       * <li>{@link TypeNone}
       * <li>{@link TypeSolidLineCrossing}
       * <li>{@link TypeDashedLineCrossing}
       * </ul>
       */
    public int laneType;

    /**
     * Position of the left lane. Point leftLanePos[0] and leftLanePos[1] can
     * draw a line to describe lane info.
     */
    public Point[] leftLanePos = new Point[2];

    /**
     * Position of the right lane. Point rightLanePos[0] and rightLanePos[1] can
     * draw a line to describe lane info.
     */
    public Point[] rightLanePos = new Point[2];

    public int lightValue;

    /**
     * Number of detected vehicles;
     */
    public int numOfVehicles;

    /**
       * The object type and its value is in the range of
       * <ul>
       * <li>{@link ObjectTypeNone}
       * <li>{@link ObjectTypeVehicle}
       * <li>{@link ObjectTypePedestrian}
       * </ul>
       */
    public int forwardObject;

    /**
       * The collision level and its value is in the range of
       * <ul>
       * <li>{@link SeverityNone}
       * <li>{@link SeverityINFO}
       * <li>{@link SeverityATTENTION}
       * <li>{@link SeverityDanger}
       * </ul>
       */
    public int forwardSeverity;

    /**
       *  calibration flag, 1 means ready,0 means not
       */
    public int calibrationReady;

    /**
       * The severity of driver and its value is in the range of
       * <ul>
       * <li>{@link SeverityNone}
       * <li>{@link SeverityINFO}
       * <li>{@link SeverityATTENTION}
       * <li>{@link SeverityDanger}
       * </ul>
       */
    public int driverSeverity;

    /**
       * The severity of passerby in rear view and its value is in the range of
       * <ul>
       * <li>{@link SeverityNone}
       * <li>{@link SeverityINFO}
       * <li>{@link SeverityATTENTION}
       * <li>{@link SeverityDanger}
       * </ul>
       */
    public int rearSeverity;

    /**
       * The state of driver and its value is in the range of
       * <ul>
       * <li>{@link DRIVER_ATTENTION_LEFT}
       * <li>{@link DRIVER_ATTENTION_FORWARD}
       * <li>{@link DRIVER_ATTENTION_MIDDLE}
       * <li>{@link DRIVER_ATTENTION_RIGHT}
       * <li>{@link DRIVER_ATTENTION_DOWN}
       * <li>{@link DRIVER_ATTENTION_ABSENT}
       * </ul>
       */
    public int driverState;

    /**
       * The object type and its value is in the range of
       * <ul>
       * <li>{@link ObjectTypeNone}
       * <li>{@link ObjectTypeVehicle}
       * <li>{@link ObjectTypePedestrian}
       * </ul>
       */
    public int rearObject;

    /**
       * unknown
       */
    public int fvdInfo;


    /**
     * Bounds of the detective vehicle. (-1000, -1000) represents the top-left
     * of the camera field of view, and (1000, 1000) represents the bottom-right
     * of the field of view. For example, suppose the size of the viewfinder UI
     * is 800x480. The rect passed from the driver is (-1000, -1000, 0, 0). The
     * corresponding viewfinder rect should be (0, 0, 400, 240). It is
     * guaranteed left < right and top < bottom. The coordinates can be smaller
     * than -1000 or bigger than 1000. But at least one vertex will be within
     * (-1000, -1000) and (1000, 1000).
     */
    public Rect vehiclesPos[];

    /**
     * Distances of the detective {@link #carsPos}
     */
    public int distances[];

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(leftLaneWarning);
        dest.writeInt(rightLaneWarning);
        dest.writeInt(laneSeverity);
        dest.writeInt(laneType);
        dest.writeInt(4);
        for (Point pos : leftLanePos) {
            pos.writeToParcel(dest, flags);
        }
        dest.writeInt(4);
        for (Point pos : rightLanePos) {
            pos.writeToParcel(dest, flags);
        }
        dest.writeInt(lightValue);
        dest.writeInt(numOfVehicles);
        dest.writeInt(forwardObject);
        dest.writeInt(forwardSeverity);
        dest.writeInt(calibrationReady);
        dest.writeInt(driverSeverity);
        dest.writeInt(rearSeverity);
        dest.writeInt(driverState);
        dest.writeInt(rearObject);
        dest.writeInt(fvdInfo);
        if (numOfVehicles > 0) {
            dest.writeInt(numOfVehicles*5);
            vehiclesPos = new Rect[numOfVehicles];
            for (int i = 0; i < numOfVehicles; i++) {
                vehiclesPos[i] = new Rect();
            }
            for (Rect rect : vehiclesPos) {
                rect.writeToParcel(dest, flags);
            }
            distances = new int[numOfVehicles];
            for (int distance : distances) {
                dest.writeInt(distance);
            }
        }

    }

    public void readFromParcel(Parcel in) {
        leftLaneWarning = in.readInt();
        rightLaneWarning = in.readInt();
        laneSeverity = in.readInt();
        laneType = in.readInt();
        in.readInt();
        leftLanePos[0].readFromParcel(in);
        leftLanePos[1].readFromParcel(in);
        in.readInt();
        rightLanePos[0].readFromParcel(in);
        rightLanePos[1].readFromParcel(in);
        lightValue = in.readInt();

        numOfVehicles = in.readInt();
        forwardObject = in.readInt();
        forwardSeverity = in.readInt();
        calibrationReady = in.readInt();
        driverSeverity = in.readInt();
        rearSeverity = in.readInt();
        driverState = in.readInt();
        rearObject = in.readInt();
        fvdInfo = in.readInt();
        if (numOfVehicles > 0) {
            in.readInt();
            vehiclesPos = new Rect[numOfVehicles];
            distances = new int[numOfVehicles];
            for (int i = 0; i < numOfVehicles; i++) {
                vehiclesPos[i] = new Rect();
                vehiclesPos[i].readFromParcel(in);
            }
            for (int i = 0; i < numOfVehicles; i++) {
                distances[i] = in.readInt();
            }
        }
    }

    public static final Parcelable.Creator<ADASInfo> CREATOR = new Parcelable.Creator<ADASInfo>() {
        @Override
        public ADASInfo createFromParcel(Parcel in) {
            ADASInfo info = new ADASInfo();
            info.readFromParcel(in);
            return info;
        }

        @Override
        public ADASInfo[] newArray(int size) {
            return new ADASInfo[size];
        }
    };

    @Override
    public String toString() {
        String str = super.toString() + " leftLaneWarning=" + leftLaneWarning + "; rightLaneWarning=" + rightLaneWarning
                     + "; laneSeverity=" + laneSeverity + "; laneType=" + laneType + "; leftLanePos=" + leftLanePos
                     + "; rightLanePos=" + rightLanePos + "; lightValue="+ lightValue + "; numOfVehicles=" + numOfVehicles
                     + "; forwardObject=" + forwardObject + "; forwardSeverity=" + forwardSeverity + "; calibrationReady=" + calibrationReady
                     + "; driverSeverity=" + driverSeverity + "; rearSeverity=" + rearSeverity + "; driverState=" + driverState
                     + "; rearObject=" + rearObject + "; fvdInfo=" + fvdInfo + "; vehiclesPos=" + vehiclesPos + "; distances=" + distances;
        return str;
    }
}
