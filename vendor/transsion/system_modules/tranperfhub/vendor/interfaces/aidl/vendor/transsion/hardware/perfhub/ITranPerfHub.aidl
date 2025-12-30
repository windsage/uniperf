package vendor.transsion.hardware.perfhub;

/**
 * TranPerfHub AIDL Interface
 * 
 * 统一接口，供以下调用方使用:
 *  1. System JNI → Vendor Adapter
 *  2. Vendor Native Client → Vendor Adapter
 */
interface ITranPerfHub {
    /**
     * 通知性能事件开始
     * 
     * @param eventId 事件ID/场景ID
     * @param timestamp 事件时间戳 (纳秒)
     * @param numParams intParams 数组的有效长度
     * @param intParams 整型参数数组
     * @param extraStrings 字符串参数 (通常是 packageName)
     */
    oneway void notifyEventStart(
        int eventId,
        long timestamp,
        int numParams,
        in int[] intParams,
        @nullable @utf8InCpp String extraStrings
    );
    
    /**
     * 通知性能事件结束
     * 
     * @param eventId 事件ID/场景ID
     * @param timestamp 事件时间戳 (纳秒)
     * @param extraStrings 字符串参数 (通常是 packageName)
     */
    oneway void notifyEventEnd(
        int eventId,
        long timestamp,
        @nullable @utf8InCpp String extraStrings
    );
}
