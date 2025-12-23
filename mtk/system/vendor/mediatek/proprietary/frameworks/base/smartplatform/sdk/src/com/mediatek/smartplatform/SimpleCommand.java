package com.mediatek.smartplatform;

import com.mediatek.smartplatform.ICommand;

import android.os.Parcel;
import android.os.Parcelable;

public class SimpleCommand implements Parcelable,ICommand {


    // get or set
    private boolean mGetters = false;

    // int: 0, String: 1
    private int mCommandType = -1;
    // we do not use Template,  because we haven't found how to make  Java and C++ unified.
    private int mCommandInt = -1;
    private int mParaInt = -1;
    private String mCommandString = "";
    private String mParaString = "";

    // int: 0: String 1
    private int mResultType = -1;
    private int mResultInt =-1;
    private String mResultString = "";

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(mGetters);
        out.writeInt(mCommandType);
        out.writeInt(mCommandInt);
        out.writeInt(mParaInt);
        out.writeString(mCommandString);
        out.writeString(mParaString);
        out.writeInt(mResultType);
        out.writeInt(mResultInt);
        out.writeString(mResultString);

    }

    public void readFromParcel(Parcel in) {
        mGetters = in.readBoolean();
        mCommandType = in.readInt();
        mCommandInt = in.readInt();
        mParaInt = in.readInt();
        mCommandString = in.readString();
        mParaString = in.readString();
        mResultType = in.readInt();
        mResultInt = in.readInt();
        mResultString = in.readString();


    }

    public static final Parcelable.Creator<SimpleCommand> CREATOR =
    new Parcelable.Creator<SimpleCommand>() {
        @Override
        public SimpleCommand createFromParcel(Parcel in) {
            SimpleCommand info = new SimpleCommand();
            info.readFromParcel(in);

            return info;
        }

        @Override
        public SimpleCommand[] newArray(int size) {
            return new SimpleCommand[size];
        }
    };

    public SimpleCommand() {

    }
    public SimpleCommand(boolean getter, int cmd, int arg, int resultType) {
        mGetters = getter;
        mCommandType = 0;
        mCommandInt = cmd;
        mParaInt = arg;
        mResultType = resultType;
    }


    public SimpleCommand(boolean getter, int cmd, String arg, int resultType) {
        mGetters = getter;
        mCommandType = 0;
        mCommandInt = cmd;
        mParaString= arg;
        mResultType = resultType;
    }


    public SimpleCommand(boolean getter, String cmd, String arg, int resultType) {
        mGetters = getter;
        mCommandType = 1;
        mCommandString= cmd;
        mParaString = arg;
        mResultType = resultType;
    }

    public SimpleCommand(boolean getter, String cmd, int arg, int resultType) {
        mGetters = getter;
        mCommandType = 1;
        mCommandString= cmd;
        mParaInt= arg;
        mResultType = resultType;
    }

    public String getResultString() {
        if (mResultType == 1) {
            return mResultString;
        }

        return null;
    }

    public int getResultInt() {
        if (mResultType == 0) {
            return mResultInt;
        }

        return -1;
    }

    public String toString() {
        String returnValue = "[Getters:" + mGetters + " ,CommandType:" + mCommandType
                             + " ,CommandInt:" + mCommandInt
                             + " ,ParaInt:" + mParaInt
                             + " ,CommandString:" + mCommandString
                             + " ,ParaString;" + mParaString
                             + " ,ResultType:" + mResultType
                             + " ,ResultInt:" + mResultInt
                             + " ,ResultString:" + mResultString
                             + "]";
        return returnValue;
    }


}

