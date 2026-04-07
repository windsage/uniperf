package com.transsion.perfengine.demo;

import static com.transsion.usf.perfengine.TranPerfEventConstants.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.transsion.usf.perfengine.TranPerfEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import vendor.transsion.hardware.perfengine.IEventListener;

public class MainActivity extends Activity {
    private static final String TAG = "PerfEngineDemo";

    // ==================== Event definitions ====================
    // Aligned with event_definitions.xml (latest)
    private static final int[] EVENT_IDS = {
            EVENT_SYS_APP_LAUNCH_COLD, // 0x00002
            EVENT_SYS_APP_LAUNCH_WARM, // 0x00003
            EVENT_SYS_ACTIVITY_SWITCH, // 0x00006
            EVENT_SYS_TOUCH, // 0x00007
            EVENT_SYS_FLING, // 0x00009
            EVENT_SYS_SCROLL, // 0x0000A
            EVENT_SYS_SCREEN_ON, // 0x0000B
            EVENT_SYS_SCREEN_UNLOCK, // 0x0000E
            EVENT_SYS_AMIN_TRANSITION, // 0x00016
            EVENT_SYS_CAMERA_LAUNCH, // 0x00021
            EVENT_APP_LAUNCHER_SWIPE_UP, // 0x02001
            EVENT_APP_LAUNCHER_FOLDER_OPEN, // 0x02002
            EVENT_APP_WALLPAPER_CHANGE, // 0x03001
    };

    private static final String[] EVENT_NAMES = {
            "APP_LAUNCH_COLD (0x00002)",
            "APP_LAUNCH_WARM (0x00003)",
            "ACTIVITY_SWITCH (0x00006)",
            "TOUCH (0x00007)",
            "FLING (0x00009)",
            "SCROLL (0x0000A)",
            "SCREEN_ON (0x0000B)",
            "SCREEN_UNLOCK (0x0000E)",
            "AMIN_TRANSITION (0x00016)",
            "CAMERA_LAUNCH (0x00021)",
            "LAUNCHER_SWIPE_UP (0x02001)",
            "LAUNCHER_FOLDER_OPEN (0x02002)",
            "WALLPAPER_CHANGE (0x03001)",
    };

    private static String idToName(int id) {
        if (id == EVENT_SYS_APP_LAUNCH_COLD)
            return "APP_LAUNCH_COLD";
        if (id == EVENT_SYS_APP_LAUNCH_WARM)
            return "APP_LAUNCH_WARM";
        if (id == EVENT_SYS_ACTIVITY_SWITCH)
            return "ACTIVITY_SWITCH";
        if (id == EVENT_SYS_TOUCH)
            return "TOUCH";
        if (id == EVENT_SYS_FLING)
            return "FLING";
        if (id == EVENT_SYS_SCROLL)
            return "SCROLL";
        if (id == EVENT_SYS_SCREEN_ON)
            return "SCREEN_ON";
        if (id == EVENT_SYS_SCREEN_UNLOCK)
            return "SCREEN_UNLOCK";
        if (id == EVENT_SYS_AMIN_TRANSITION)
            return "AMIN_TRANSITION";
        if (id == EVENT_SYS_CAMERA_LAUNCH)
            return "CAMERA_LAUNCH";
        if (id == EVENT_APP_LAUNCHER_SWIPE_UP)
            return "LAUNCHER_SWIPE_UP";
        if (id == EVENT_APP_LAUNCHER_FOLDER_OPEN)
            return "LAUNCHER_FOLDER_OPEN";
        if (id == EVENT_APP_WALLPAPER_CHANGE)
            return "WALLPAPER_CHANGE";
        return "UNKNOWN(0x" + Integer.toHexString(id) + ")";
    }

    // ==================== Views ====================

    private Button mBtnTabSend, mBtnTabListen;
    private ScrollView mPanelSend;
    private LinearLayout mPanelListen;

    // Send panel
    private Spinner mSpinnerEvent;
    private EditText mEtPackage, mEtCount, mEtInterval;
    private Button mBtnSend, mBtnSendAll;

    // Listen panel
    private RadioGroup mRgListenerType;
    private EditText mEtFilter;
    private Button mBtnRegister, mBtnUnregister, mBtnClearLog;
    private TextView mTvListenStatus;
    private ListView mLvLog;
    private EventLogAdapter mLogAdapter;

    // ==================== State ====================

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // Only IBinder listener remains; PerfEventListener removed (no longer in TranPerfEvent API)
    private IEventListener.Stub mIBinderListener;

    // TrEventListener for in-process (system_server) monitoring
    private TranPerfEvent.TrEventListener mTrEventListener;

    private enum ListenerType { NONE, TR_EVENT, IBINDER }
    private ListenerType mRegisteredType = ListenerType.NONE;

    /**
     * Listener token to ignore in-flight callbacks after unregister.
     *
     * Vendor side broadcasts are oneway and may already be queued in Binder driver or worker
     * threads when unregister returns. We gate callbacks in demo UI to make behavior intuitive.
     */
    private final AtomicInteger mListenerSeq = new AtomicInteger(0);
    private volatile int mActiveListenerSeq = -1;

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View header = findViewById(R.id.layout_header);
        header.post(() -> {
            int statusBarHeight = 0;
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resId);
            }
            int basePadding = (int) (8 * getResources().getDisplayMetrics().density);
            header.setPadding(header.getPaddingLeft(), basePadding + statusBarHeight,
                    header.getPaddingRight(), header.getPaddingBottom());
        });
        bindViews();
        setupTabSwitcher();
        setupSendPanel();
        setupListenPanel();
        showTab(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnregister();
        mExecutor.shutdownNow();
    }

    // ==================== View binding ====================

    private void bindViews() {
        mBtnTabSend = findViewById(R.id.btn_tab_send);
        mBtnTabListen = findViewById(R.id.btn_tab_listen);
        mPanelSend = findViewById(R.id.panel_send);
        mPanelListen = findViewById(R.id.panel_listen);

        mSpinnerEvent = findViewById(R.id.spinner_event);
        mEtPackage = findViewById(R.id.et_package);
        mEtCount = findViewById(R.id.et_count);
        mEtInterval = findViewById(R.id.et_interval);
        mBtnSend = findViewById(R.id.btn_send);
        mBtnSendAll = findViewById(R.id.btn_send_all);

        mRgListenerType = findViewById(R.id.rg_listener_type);
        mEtFilter = findViewById(R.id.et_filter);
        mBtnRegister = findViewById(R.id.btn_register);
        mBtnUnregister = findViewById(R.id.btn_unregister);
        mTvListenStatus = findViewById(R.id.tv_listen_status);
        mBtnClearLog = findViewById(R.id.btn_clear_log);
        mLvLog = findViewById(R.id.lv_log);
    }

    // ==================== Tab ====================

    private void showTab(boolean showSend) {
        mPanelSend.setVisibility(showSend ? View.VISIBLE : View.GONE);
        mPanelListen.setVisibility(showSend ? View.GONE : View.VISIBLE);
        // Active tab: white bg + orange bottom indicator (tab_active_bg.xml)
        // Inactive tab: plain white bg + muted text color
        if (showSend) {
            mBtnTabSend.setBackgroundResource(R.drawable.tab_active_bg);
            mBtnTabSend.setTextColor(getColor(R.color.color_tab_active_text));
            mBtnTabListen.setBackgroundColor(getColor(R.color.color_tab_bg));
            mBtnTabListen.setTextColor(getColor(R.color.color_tab_inactive_text));
        } else {
            mBtnTabListen.setBackgroundResource(R.drawable.tab_active_bg);
            mBtnTabListen.setTextColor(getColor(R.color.color_tab_active_text));
            mBtnTabSend.setBackgroundColor(getColor(R.color.color_tab_bg));
            mBtnTabSend.setTextColor(getColor(R.color.color_tab_inactive_text));
        }
    }

    private void setupTabSwitcher() {
        mBtnTabSend.setOnClickListener(v -> showTab(true));
        mBtnTabListen.setOnClickListener(v -> showTab(false));
    }

    // ==================== Send Panel ====================

    private void setupSendPanel() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, EVENT_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerEvent.setAdapter(adapter);

        mBtnSend.setOnClickListener(v -> onSendClicked(false));
        mBtnSendAll.setOnClickListener(v -> onSendClicked(true));
    }

    private void onSendClicked(boolean sendAll) {
        String pkg = mEtPackage.getText().toString().trim();
        int count = parseIntOr(mEtCount.getText().toString().trim(), 1);
        int interval = parseIntOr(mEtInterval.getText().toString().trim(), 500);

        int[] events = sendAll ? EVENT_IDS
                               : new int[] {EVENT_IDS[mSpinnerEvent.getSelectedItemPosition()]};

        mBtnSend.setEnabled(false);
        mBtnSendAll.setEnabled(false);

        mExecutor.execute(() -> {
            try {
                for (int round = 0; round < count; round++) {
                    for (int eventId : events) {
                        sendEvent(eventId, pkg);
                        if (events.length > 1)
                            sleep(interval);
                    }
                    if (round < count - 1)
                        sleep(interval);
                }
            } finally {
                mUiHandler.post(() -> {
                    mBtnSend.setEnabled(true);
                    mBtnSendAll.setEnabled(true);
                });
            }
        });
    }

    private void sendEvent(int eventId, String pkg) {
        long ts = TranPerfEvent.now();
        if (!pkg.isEmpty()) {
            TranPerfEvent.notifyEventStart(eventId, ts, pkg);
        } else {
            TranPerfEvent.notifyEventStart(eventId, ts);
        }
        Log.d(TAG, "[SEND][START] eventId=0x" + Integer.toHexString(eventId) + " pkg=" + pkg);
        sleep(200);
        TranPerfEvent.notifyEventEnd(eventId, TranPerfEvent.now(), pkg.isEmpty() ? null : pkg);
        Log.d(TAG, "[SEND][ END ] eventId=0x" + Integer.toHexString(eventId));
    }

    // ==================== Listen Panel ====================

    private void setupListenPanel() {
        mLogAdapter = new EventLogAdapter(this);
        mLvLog.setAdapter(mLogAdapter);

        mBtnRegister.setOnClickListener(v -> onRegisterClicked());
        mBtnUnregister.setOnClickListener(v -> onUnregisterClicked());
        mBtnClearLog.setOnClickListener(v -> mLogAdapter.clear());

        mRgListenerType.setOnCheckedChangeListener((group, checkedId) -> {
            if (mRegisteredType != ListenerType.NONE) {
                toast("Please unregister first before switching listener type");
                return;
            }
            if (checkedId == R.id.rb_ibinder) {
                mEtFilter.setHint("Filter supported (IBinder mode)");
            } else {
                // rb_perf_event -> now maps to TrEventListener (in-process only)
                mEtFilter.setHint("No filter (TrEventListener: in-process only)");
            }
        });
    }

    private void onRegisterClicked() {
        if (mRegisteredType != ListenerType.NONE) {
            toast("Already registered, unregister first");
            return;
        }
        // rb_perf_event radio button now drives TrEventListener (in-process)
        // rb_ibinder radio button drives IEventListener.Stub (cross-process)
        boolean useIBinder = (mRgListenerType.getCheckedRadioButtonId() == R.id.rb_ibinder);
        if (useIBinder) {
            registerIBinderListener();
        } else {
            registerTrEventListener();
        }
    }

    // ---------- TrEventListener (in-process, system_server internal) ----------

    private void registerTrEventListener() {
        final int mySeq = mListenerSeq.incrementAndGet();
        mActiveListenerSeq = mySeq;
        mTrEventListener = new TranPerfEvent.TrEventListener() {
            @Override
            public void onEventStart(int eventId, long timestamp, int duration) {
                if (mActiveListenerSeq != mySeq || mRegisteredType != ListenerType.TR_EVENT) {
                    return;
                }
                String line = ts() + " [START] id=0x" + Integer.toHexString(eventId) + "("
                              + idToName(eventId) + ")"
                              + " duration=" + duration;
                appendLog(line);
            }

            @Override
            public void onEventEnd(int eventId, long timestamp) {
                if (mActiveListenerSeq != mySeq || mRegisteredType != ListenerType.TR_EVENT) {
                    return;
                }
                String line = ts() + " [ END ] id=0x" + Integer.toHexString(eventId) + "("
                              + idToName(eventId) + ")";
                appendLog(line);
            }
        };

        TranPerfEvent.registerListener(mTrEventListener);
        setStatus("TrEventListener registered (in-process only)");
        mRegisteredType = ListenerType.TR_EVENT;
        mBtnRegister.setEnabled(false);
        mBtnUnregister.setEnabled(true);
    }

    // ---------- IBinder Listener (cross-process, via vendor service) ----------

    private void registerIBinderListener() {
        final int mySeq = mListenerSeq.incrementAndGet();
        mActiveListenerSeq = mySeq;
        mIBinderListener = new IEventListener.Stub() {
            @Override
            public void onEventStart(int eventId, long timestamp, int numParams, int[] intParams,
                    String extraStrings) {
                if (mActiveListenerSeq != mySeq || mRegisteredType != ListenerType.IBINDER) {
                    return;
                }
                String line = ts() + " [START] id=0x" + Integer.toHexString(eventId) + "("
                              + idToName(eventId) + ")"
                              + " params=" + arrayToStr(intParams, numParams)
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
            }

            @Override
            public void onEventEnd(int eventId, long timestamp, String extraStrings) {
                if (mActiveListenerSeq != mySeq || mRegisteredType != ListenerType.IBINDER) {
                    return;
                }
                String line = ts() + " [ END ] id=0x" + Integer.toHexString(eventId) + "("
                              + idToName(eventId) + ")"
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
            }

            @Override
            public String getInterfaceHash() {
                return IEventListener.HASH;
            }

            @Override
            public int getInterfaceVersion() {
                return IEventListener.VERSION;
            }
        };

        int[] filter = parseFilter(mEtFilter.getText().toString().trim());
        if (filter.length > 0) {
            TranPerfEvent.registerEventListener(mIBinderListener.asBinder(), filter);
            setStatus("IBinder listener registered, filter=" + intArrayToStr(filter));
        } else {
            TranPerfEvent.registerEventListener(mIBinderListener.asBinder());
            setStatus("IBinder listener registered, all events");
        }
        mRegisteredType = ListenerType.IBINDER;
        mBtnRegister.setEnabled(false);
        mBtnUnregister.setEnabled(true);
    }

    // ---------- Unregister ----------

    private void onUnregisterClicked() {
        doUnregister();
        setStatus("Not registered");
        mBtnRegister.setEnabled(true);
        mBtnUnregister.setEnabled(false);
    }

    private void doUnregister() {
        // Stop consuming callbacks immediately (in-flight oneway callbacks may still arrive).
        mActiveListenerSeq = -1;

        if (mRegisteredType == ListenerType.TR_EVENT && mTrEventListener != null) {
            TranPerfEvent.unregisterListener(mTrEventListener);
            mTrEventListener = null;
        } else if (mRegisteredType == ListenerType.IBINDER && mIBinderListener != null) {
            TranPerfEvent.unregisterEventListener(mIBinderListener.asBinder());
            mIBinderListener = null;
        }
        mRegisteredType = ListenerType.NONE;
    }

    // ==================== Helpers ====================

    private void appendLog(String line) {
        mUiHandler.post(() -> {
            mLogAdapter.append(line);
            mLvLog.smoothScrollToPosition(mLogAdapter.getCount() - 1);
        });
    }

    private void setStatus(String msg) {
        mUiHandler.post(() -> mTvListenStatus.setText(msg));
    }

    private void toast(String msg) {
        mUiHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private static String ts() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int[] parseFilter(String s) {
        if (s.isEmpty())
            return new int[0];
        String[] parts = s.split(",");
        int[] result = new int[parts.length];
        int count = 0;
        for (String p : parts) {
            try {
                String trimmed = p.trim();
                result[count++] = trimmed.startsWith("0x") || trimmed.startsWith("0X")
                                          ? (int) Long.parseLong(trimmed.substring(2), 16)
                                          : Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
            }
        }
        int[] out = new int[count];
        System.arraycopy(result, 0, out, 0, count);
        return out;
    }

    private static String arrayToStr(int[] arr, int len) {
        if (arr == null || len == 0)
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len && i < arr.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static String intArrayToStr(int[] arr) {
        if (arr == null || arr.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append("0x").append(Integer.toHexString(arr[i]));
        }
        return sb.toString();
    }
}
