package com.mediatek.smartplatform;
public interface ICommand {

    public static final int CMD_COLLISION = 0;
    public static final int CMD_COLLISION_INIT_EVENTQUEUE = 1;
    public static final int CMD_COLLISION_REMOVE_EVENTQUEUE = 2;
    public static final int CMD_COLLISION_SET_NORMAL_STATUS = 3;
    public static final int CMD_COLLISION_SET_SUSPEND_STATUS = 4;
    public static final int CMD_COLLISION_SET_NORMAL_SENSITY = 5;
    public static final int CMD_COLLISION_SET_SUSPEND_SENSITY = 6;
    public static final int CMD_COLLISION_GET_NORMAL_STATUS = 7;
    public static final int CMD_COLLISION_GET_SUSPEND_STATUS = 8;
    public static final int CMD_COLLISION_GET_NORMAL_SENSITY = 9;
    public static final int CMD_COLLISION_GET_SUSPEND_SENSITY = 10;
    public static final int CMD_SET_BT_WAKEUP_SUPPORT         = 11;
    public static final int CMD_GET_BT_WAKEUP_SUPPORT         = 12;
    public static final int CMD_SET_WM_IMAGE_OPAQUE           = 13;
    public static final int CMD_GET_WM_IMAGE_OPAQUE           = 14;

    public static final int COLLISION_SET_CARCORDER_THRESHOLD = 0;
    public static final int COLLISION_GET_CARCORDER_THRESHOLD = 1;

    public static final int COLLISION_SET_GSENSOR_THRESHOLD = 2;
    public static final int COLLISION_GET_GSENSOR_THRESHOLD = 3;

    public static final int COLLISION_SET_GSENSOR_EVENTRATE   = 4;
    public static final int COLLISION_GET_GSENSOR_EVENTRATE   = 5;

    public static final int COMMAND_TYPE_INT   =  0;
    public static final int COMMAND_TYPE_STRING   =  1;


    // get or set
    /*
        public boolean mGetters = true;

        public int mCommandType;

        public int mCommandInt;
        public String mCommandString;
    */


}




