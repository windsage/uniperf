package com.transsion.perfengine.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TranPerfEvent;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vendor.transsion.hardware.perfengine.IEventListener;

public class MainActivity extends Activity {
    private static final String TAG = "PerfEngineDemo";

    // ==================== Event definitions ====================

    private static final int[] EVENT_IDS = {
            TranPerfEvent.EVENT_APP_LAUNCH,
            TranPerfEvent.EVENT_APP_SWITCH,
            TranPerfEvent.EVENT_SCROLL,
            TranPerfEvent.EVENT_CAMERA_OPEN,
            TranPerfEvent.EVENT_GAME_START,
            TranPerfEvent.EVENT_VIDEO_PLAY,
            TranPerfEvent.EVENT_ANIMATION,
    };

    private static final String[] EVENT_NAMES = {
            "APP_LAUNCH (1)",
            "APP_SWITCH (2)",
            "SCROLL (3)",
            "CAMERA_OPEN (4)",
            "GAME_START (5)",
            "VIDEO_PLAY (6)",
            "ANIMATION (7)",
    };

    private static String idToName(int id) {
        switch (id) {
            case TranPerfEvent.EVENT_APP_LAUNCH:
                return "APP_LAUNCH";
            case TranPerfEvent.EVENT_APP_SWITCH:
                return "APP_SWITCH";
            case TranPerfEvent.EVENT_SCROLL:
                return "SCROLL";
            case TranPerfEvent.EVENT_CAMERA_OPEN:
                return "CAMERA_OPEN";
            case TranPerfEvent.EVENT_GAME_START:
                return "GAME_START";
            case TranPerfEvent.EVENT_VIDEO_PLAY:
                return "VIDEO_PLAY";
            case TranPerfEvent.EVENT_ANIMATION:
                return "ANIMATION";
            default:
                return "UNKNOWN(" + id + ")";
        }
    }

    // ==================== Views ====================

    private Button mBtnTabSend, mBtnTabListen;
    private ScrollView mPanelSend;
    private LinearLayout mPanelListen;

    // Send panel
    private Spinner mSpinnerEvent;
    private EditText mEtPackage, mEtDuration, mEtCount, mEtInterval;
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

    // PerfEventListener instance (full)
    private TranPerfEvent.PerfEventListener mPerfEventListener;

    // IBinderEventListener instance (full)
    private IEventListener.Stub mIBinderListener;

    private enum ListenerType { NONE, PERF_EVENT, IBINDER }
    private ListenerType mRegisteredType = ListenerType.NONE;

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupTabSwitcher();
        setupSendPanel();
        setupListenPanel();
        // Show Send tab by default
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
        mEtDuration = findViewById(R.id.et_duration);
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
        mBtnTabSend.setAlpha(showSend ? 1.0f : 0.5f);
        mBtnTabListen.setAlpha(showSend ? 0.5f : 1.0f);
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
        String pkgStr = mEtPackage.getText().toString().trim();
        String durStr = mEtDuration.getText().toString().trim();
        String countStr = mEtCount.getText().toString().trim();
        String intrvStr = mEtInterval.getText().toString().trim();

        final int duration = durStr.isEmpty() ? 0 : parseInt(durStr, 0);
        final int count = Math.max(1, parseInt(countStr, 1));
        final int interval = Math.max(0, parseInt(intrvStr, 500));
        final String pkg = pkgStr;

        final int[] events;
        if (sendAll) {
            events = EVENT_IDS;
        } else {
            events = new int[] {EVENT_IDS[mSpinnerEvent.getSelectedItemPosition()]};
        }

        mBtnSend.setEnabled(false);
        mBtnSendAll.setEnabled(false);

        mExecutor.execute(() -> {
            try {
                for (int round = 0; round < count; round++) {
                    for (int eventId : events) {
                        sendEvent(eventId, pkg, duration);
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

    private void sendEvent(int eventId, String pkg, int duration) {
        long ts = TranPerfEvent.now();

        if (!pkg.isEmpty() && duration > 0) {
            TranPerfEvent.notifyEventStart(
                    eventId, ts, 1, new int[] {duration}, new String[] {pkg});
        } else if (!pkg.isEmpty()) {
            TranPerfEvent.notifyEventStart(eventId, ts, pkg);
        } else if (duration > 0) {
            TranPerfEvent.notifyEventStart(eventId, ts, 1, new int[] {duration});
        } else {
            TranPerfEvent.notifyEventStart(eventId, ts);
        }

        Log.d(TAG, "[SEND][START] eventId=" + eventId + " pkg=" + pkg + " dur=" + duration);

        sleep(duration > 0 ? Math.min(duration, 200) : 100);

        TranPerfEvent.notifyEventEnd(eventId, TranPerfEvent.now(), pkg.isEmpty() ? null : pkg);

        Log.d(TAG, "[SEND][ END ] eventId=" + eventId);
    }

    // ==================== Listen Panel ====================

    private void setupListenPanel() {
        mLogAdapter = new EventLogAdapter(this);
        mLvLog.setAdapter(mLogAdapter);

        mBtnRegister.setOnClickListener(v -> onRegisterClicked());
        mBtnUnregister.setOnClickListener(v -> onUnregisterClicked());
        mBtnClearLog.setOnClickListener(v -> mLogAdapter.clear());
    }

    private void onRegisterClicked() {
        if (mRegisteredType != ListenerType.NONE) {
            toast("Already registered, unregister first");
            return;
        }

        boolean usePerfEvent = (mRgListenerType.getCheckedRadioButtonId() == R.id.rb_perf_event);
        int[] filter = parseFilter(mEtFilter.getText().toString().trim());

        if (usePerfEvent) {
            registerPerfEventListener(filter);
        } else {
            registerIBinderListener();
        }
    }

    // ---------- PerfEventListener (full) ----------

    private void registerPerfEventListener(int[] filter) {
        mPerfEventListener = new TranPerfEvent.PerfEventListener() {
            @Override
            public void onEventStart(int eventId, long timestamp, int numParams, int[] intParams,
                    String extraStrings) {
                String line = ts() + " [START] id=" + eventId + "(" + idToName(eventId) + ")"
                              + " params=" + arrayToStr(intParams, numParams)
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
            }

            @Override
            public void onEventEnd(int eventId, long timestamp, String extraStrings) {
                String line = ts() + " [ END ] id=" + eventId + "(" + idToName(eventId) + ")"
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
            }
        };

        if (filter.length > 0) {
            TranPerfEvent.registerEventListener(mPerfEventListener, filter);
            setStatus("PerfEventListener registered, filter=" + intArrayToStr(filter));
        } else {
            TranPerfEvent.registerEventListener(mPerfEventListener);
            setStatus("PerfEventListener registered, all events");
        }

        mRegisteredType = ListenerType.PERF_EVENT;
        mBtnRegister.setEnabled(false);
        mBtnUnregister.setEnabled(true);
    }

    // ---------- IBinder Listener ----------

    private void registerIBinderListener() {
        mIBinderListener = new IEventListener.Stub() {
            @Override
            public void onEventStart(int eventId, long timestamp, int numParams, int[] intParams,
                    String extraStrings) {
                String line = ts() + " [START] id=" + eventId + "(" + idToName(eventId) + ")"
                              + " params=" + arrayToStr(intParams, numParams)
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
            }

            @Override
            public void onEventEnd(int eventId, long timestamp, String extraStrings) {
                String line = ts() + " [ END ] id=" + eventId + "(" + idToName(eventId) + ")"
                              + (extraStrings != null ? " extra=" + extraStrings : "");
                appendLog(line);
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
        if (mRegisteredType == ListenerType.PERF_EVENT && mPerfEventListener != null) {
            TranPerfEvent.unregisterEventListener(mPerfEventListener);
            mPerfEventListener = null;
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
            // Auto-scroll to bottom
            mLvLog.smoothScrollToPosition(mLogAdapter.getCount() - 1);
        });
    }

    private void setStatus(String msg) {
        mUiHandler.post(() -> mTvListenStatus.setText("Status: " + msg));
    }

    private void toast(String msg) {
        mUiHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int[] parseFilter(String s) {
        if (s.isEmpty())
            return new int[0];
        String[] parts = s.split(",");
        int[] result = new int[parts.length];
        int idx = 0;
        for (String p : parts) {
            try {
                result[idx++] = Integer.parseInt(p.trim());
            } catch (NumberFormatException e) {
            }
        }
        int[] trimmed = new int[idx];
        System.arraycopy(result, 0, trimmed, 0, idx);
        return trimmed;
    }

    private static String arrayToStr(int[] arr, int n) {
        if (arr == null || n == 0)
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(n, arr.length); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }

    private static String intArrayToStr(int[] arr) {
        if (arr == null || arr.length == 0)
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }

    private static String ts() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static void sleep(long ms) {
        if (ms <= 0)
            return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
