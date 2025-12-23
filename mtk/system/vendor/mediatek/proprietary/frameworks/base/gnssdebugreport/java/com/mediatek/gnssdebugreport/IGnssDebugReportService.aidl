package com.mediatek.gnssdebugreport;

import com.mediatek.gnssdebugreport.IDebugReportCallback;

interface IGnssDebugReportService {
    boolean startDebug(IDebugReportCallback callback);
    boolean stopDebug(IDebugReportCallback callback);
    oneway void startRecReport(IDebugReportCallback callback);
    oneway void stopRecReport(IDebugReportCallback callback);
    oneway void addListener(IDebugReportCallback callback);
    oneway void removeListener(IDebugReportCallback callback);
}
