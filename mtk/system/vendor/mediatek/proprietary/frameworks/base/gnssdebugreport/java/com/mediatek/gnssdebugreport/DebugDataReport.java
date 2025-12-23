package com.mediatek.gnssdebugreport;

import android.os.Parcel;
import android.os.Parcelable;

public class DebugDataReport {
    public class DebugData839 {
        public static final String KEY_CB = "cb";
        public static final String KEY_COMP_CB = "compCb";
        public static final String KEY_CLK_TEMP = "clkTemp";
        public static final String KEY_SAT = "saturation";
        public static final String KEY_PGA = "pga";
        public static final String KEY_TTFF = "ttff";
        public static final String KEY_SV_NUM = "svnum";
        public static final String KEY_TT4_SV = "tt4Sv";
        public static final String KEY_TOP4_CNR = "top4Cnr";
        public static final String KEY_INIT_L1H_LNG = "initLlhLng";
        public static final String KEY_INIT_L1H_LAT = "initLlhLat";
        public static final String KEY_INIT_L1H_HGT = "initLlhHgt";
        public static final String KEY_INIT_SRC = "initSrc";
        public static final String KEY_INIT_PACC = "initPacc";
        public static final String KEY_HAVE_EPO = "haveEpo";
        public static final String KEY_EPO_AGE = "epoAge";
        public static final String KEY_SENSOR_HACC = "sensorHacc";
        public static final String KEY_MPE_VALID = "mpeValid";
        public static final String KEY_LS_VALID = "lsvalid";
        public static final String KEY_NOISE_FLOOR = "noiseFloor";
        public static final String KEY_INIT_TIME_SRC = "initTimeSrc";
        public static final String KEY_INIT_GPS_SEC = "initGpsSec";
        public static final String KEY_CLK_TEMP_RATE = "clkTempRate";
        public static final String KEY_COMP_CD_RATE = "compCdRate";
        public static final String KEY_XTAL_JUMP_DETEC = "xtalJumpDetec";
        public static final String KEY_DIGITAL_I = "digitalI";
        public static final String KEY_DIGITAL_Q = "digitalQ";
        public static final String KEY_BLANKING = "blanking";
        public static final String KEY_L1_DUTY_CYCLE = "l1DutyCycle";
        public static final String KEY_L1_SV_NUM = "l1SvNum";
        public static final String KEY_L5_SV_NUM = "l5SvNum";
        public static final String KEY_L1_TOP4_CNR = "l1Top4Cnr";
        public static final String KEY_L5_TOP4_CNR = "l5Top4Cnr";
        public static final String KEY_NAVIC_SV_NUM = "navicSvNum";
        public static final String KEY_L1_USED_NUM = "l1UsedNum";
        public static final String KEY_L5_USED_NUM = "l5UsedNum";
        public static final String KEY_L1_EPH_NUM = "l1EphNum";
        public static final String KEY_L5_EPH_NUM = "l5EphNum";
        public static final String KEY_NO_FIX_TIME = "noFixTime";
        public static final String KEY_FG_EVEN_SAVE_TSX_TABLE = "fgEvenSaveTsxTable";
        public static final String KEY_FG_EVEN_CAL_TSX_FILE = "fgEvenCalTsxFile";
        public static final String KEY_GNSS_OPER_MODE = "gnssOperMode";
        public static final String KEY_L1_AIC = "l1Aic";
        public static final String KEY_L5_AIC = "l5Aic";
        public static final String KEY_RF_GAIN = "rfGain";
        public static final String KEY_GNSS_ABN = "gnssAbn";
        public static final String KEY_GPS_L1_PGA_GAIN = "gpsL1PgaGain";
        public static final String KEY_GLO_L1_PGA_GAIN = "gloL1PgaGain";
        public static final String KEY_BD_L1_PGA_GAIN = "bdL1PgaGain";
        public static final String KEY_GAL_L1_PGA_GAIN = "galL1PgaGain";
        public static final String KEY_NAV_L1_PGA_GAIN = "navL1PgaGain";
        public static final String KEY_L5_PGA_GAIN = "l5PgaGain";
        public static final String KEY_B2B_PGA_GAIN = "b2bPgaGain";
    }

    public static final String DATA_KEY = "DebugDataReport";
    public static final String DATA_KEY_TYPE1 = "data_type1";
    public static final String JSON_TYPE = "type";

    public static class DebugData840 {
        public static final String KEY_VER = "ver";
        public static final String KEY_SUPL_INJECT = "supl_inject";
        public static final String KEY_EPO = "epo";
        public static final String KEY_EPO_AGE = "epo_age";
        public static final String KEY_QEPO = "qepo";
        public static final String KEY_NLP = "nlp";
        public static final String KEY_AID_LAT = "aiding_lat";
        public static final String KEY_AID_LON = "aiding_lon";
        public static final String KEY_AID_HEIGHT = "aiding_height";
        public static final String KEY_NV = "nv";
        public static final String KEY_AID_SUMMARY = "aiding_summary";
    }

    public static class DebugData841 {
        public static final String KEY_VER = "ver";
        public static final String KEY_CLKD = "clk_d";
        public static final String KEY_XO_TEMPER = "xo_temper";
        public static final String KEY_PGA_GAIN = "pga_gain";
        public static final String KEY_NOISE_FLOOR = "noise_floor";
        public static final String KEY_DIGI_I = "digi_i";
        public static final String KEY_DIGI_Q = "digi_q";
        public static final String KEY_SENSOR = "sensor";
        public static final String KEY_CHIP_SUMMARY = "chip_summary";
        public static final String KEY_BLANKING = "blanking";
        public static final String KEY_CLKD_RATE = "clk_d_rate";
        public static final String KEY_XO_TEMPER_RATE = "xo_temper_rate";
        public static final String KEY_DCXO_TEMPER = "dcxo_temper";
        public static final String KEY_DCXO_TEMPER_RATE = "dcxo_temper_rate";
        public static final String KEY_C0 = "c0";
        public static final String KEY_C1 = "c1";
        public static final String KEY_GPS_FLAG = "gps_flag";
        public static final String KEY_L5_PGA_GAIN = "l5_pga_gain";
        public static final String KEY_L5_NOISE_FLOOR = "l5_noise_floor";
        public static final String KEY_L5_DIGI_I = "l5_digi_i";
        public static final String KEY_L5_DIGI_Q = "l5_digi_q";
        public static final String KEY_L1_AIC = "l1_aic";
        public static final String KEY_L5_AIC = "l5_aic";
        public static final String KEY_L1_GPS_PGA_GAIN = "l1_gps_pga_gain";
        public static final String KEY_L1_GLO_PGA_GAIN = "l1_glo_pga_gain";
        public static final String KEY_L1_BD_PGA_GAIN = "l1_bd_pga_gain";
        public static final String KEY_L1_GAL_PGA_GAIN = "l1_gal_pga_gain";
        public static final String KEY_L5_NAV_PGA_GAIN = "l5_nav_pga_gain";
        public static final String KEY_L5_PGA_GAIN_V2 = "l5_pga_gain_v2";
        public static final String KEY_B2B_PGA_GAIN = "b2b_pga_gain";
    }

    public static class DebugData842 {
        public static final String KEY_VER = "ver";//float
        public static final String KEY_TIME = "time";//uint64
        public static final String KEY_FAIL_PROCESS = "fail_process";//int
        public static final String KEY_FAIL_EVENT = "fail_event";//int
    }

    public static class DebugData843 {
        public static final String KEY_VER = "ver";//float
        public static final String KEY_TYPE = "type"; //EPO/QEPO type int
        public static final String KEY_DOWNLOAD_STATUS = "download_status";//int
        public static final String KEY_NETWORK_CONNECTION = "nw_conn";//bool
        public static final String KEY_NETWORK_CAPABILITY = "nw_capa";//bool
    }

    public static class DebugDataMPE1 {
        public static final String KEY_SYS_TIME = "sys_time";
        public static final String KEY_DELTA_TIME = "delta_time";
        public static final String KEY_LATITUDE = "latitude";
        public static final String KEY_LONGITUDE = "longitude";
        public static final String KEY_ALTITUDE = "altitude";
        public static final String KEY_NORTH_VELOCITY = "nolth_vel";
        public static final String KEY_EAST_VELOCITY = "east_vel";
        public static final String KEY_DOWN_VELOCITY = "down_vel";
        public static final String KEY_ROLL_ANGLE = "roll_angle";
        public static final String KEY_PICTH_ANGLE = "pitch_angle";
        public static final String KEY_HEADING_ANGLE = "heading_angle";
        public static final String KEY_STEP_SPEED = "step_speed";
        public static final String KEY_UDR_STATUS = "udr_status";
        public static final String KEY_PDR_STATUS = "pdr_status";
        public static final String KEY_KERNAL_FLAG = "kernal_flag";
        public static final String KEY_STATIC_FLAG = "static_flag";
    }

    public static class DebugDataAgpsSessionInfo { //DebugData9001
        //For DebugDataReport
        public static final int JSON_TYPE_ID = 9001; // for DebugDataReport.JSON_TYPE

        //Version of DebugDataAgpsSessionInfo
        public static final String KEY_VER = "ver";//int: Ver1(0), Ver2(1), ...

        //session
        public static final String KEY_SESSION_ID = "session_id";//int: a serial number
        public static final String KEY_PLANE = "plane";//String: "controlPlane", "userPlane"
        public static final String KEY_INITIATOR = "initiator";//String: "networkInitiated", "userEquipmentInitiated"
        public static final String KEY_PROTOCOL = "protocol";//String: "none", "rrlp", "rrc", "lpp", "tia801"
        public static final String KEY_RESULT = "result";//String: none, normal, rejectedbyConfig, rejectedbyUserPrivacy,
                                                         //     rejectedbyModem, rejectedbyNI, abortedByUser, abortedByNI
                                                         //     upPdnFailure, upDnsFailure, upTcpSetupFailure, upTlsSetupFailure
                                                         //     upDisconnectedByServer
        public static final String KEY_SESSION_PERIOD = "session_period";//double: in seconds  e.g., 3.476 means 3.476 seconds

        //assistance data provided by A-GPS / A-GNSS
        public static final String KEY_HAS_REF_TIME = "has_ref_time";//boolean: having reference time or not
        public static final String KEY_HAS_REF_LOCATION = "has_ref_location";//boolean: having reference location or not
        public static final String KEY_HAS_EPHEMERIS = "has_ephemeris";//boolean: having ephemeris or not
        public static final String KEY_HAS_ALMANAC = "has_almanac";//boolean: having almanac or not
        public static final String KEY_HAS_IONOSPHERE = "has_ionosphere";//boolean: having ionosphere or not
        public static final String KEY_HAS_UTC = "has_utc";//boolean: having UTC (Coordinated Universal Time) or not
        public static final String KEY_HAS_RTI = "has_rti";//boolean: having RTI (real time integrity) or not
        public static final String KEY_HAS_ACQUISION = "has_acquision";//boolean: having acquision or not
        public static final String KEY_HAS_DGPS = "has_dgps";//boolean: having DGPS (Differential GPS) or not
        public static final String KEY_HAS_TOW_ASSIST = "has_tow_assist";//boolean: having TOW (time of week) assistance or not
        public static final String KEY_HAS_TIME_MODEL = "has_time_model";//boolean: having time model or not
        public static final String KEY_HAS_DATA_BIT_ASSIST = "has_data_bit_assist";//boolean: having data bit assist or not
        public static final String KEY_HAS_EOP = "has_eop";//boolean: having EOP (Earth Orientation Parameters) or not
        public static final String KEY_HAS_AUX_INFO = "has_aux_info";//boolean: having auxiliary info or not

        //locationing or measurement
        public static final String KEY_HAS_LOC_EST = "has_loc_est";//boolean: having location estimate or not
        public static final String KEY_HAS_SAT_MEAS = "has_sat_meas";//boolean: having satellite measurement or not
    }

}
