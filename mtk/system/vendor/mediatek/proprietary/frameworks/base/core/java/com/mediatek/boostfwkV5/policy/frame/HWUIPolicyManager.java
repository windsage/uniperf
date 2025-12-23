package com.mediatek.boostfwkV5.policy.frame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.mediatek.boostfwk.scenario.animation.AnimScenario;
import com.mediatek.boostfwkV5.info.ScrollState;
import com.mediatek.boostfwkV5.policy.touch.TouchPolicy;
import com.mediatek.boostfwkV5.utils.LogUtil;

/**
 * Manager all HWUI scenario performance control.
 * @hide
 */
public class HWUIPolicyManager implements ScrollState.ScrollStateListener {
    private static final String TAG = "HWUIPolicyManager";
    private static final int DEFALUT_PRIORITY = 100;
    private static final Object LISTENER_LOCK = new Object();
    private static final Object STATUS_LOCK = new Object();
    private static final int SCROLL_KEY = ScrollState.class.hashCode();
    public static final int LONGPAGE_KEY = TouchPolicy.class.hashCode();
    private static volatile HWUIPolicyManager sInstance;

    private HashSet<Integer> mCriticalTask;
    private List<HWUIPolicyStateListener> mStateListeners = new ArrayList<>();
    private PriorityQueue<HWUIScenario> mQueue =
            new PriorityQueue<>((a, b) -> Integer.compare(a.priority, b.priority));
    private boolean mLastHWUIPolciyStatus = false;
    private int mLastHWUIPolciyPriority = DEFALUT_PRIORITY;
    private int mFrameAlgorithm = AnimScenario.ANIM_FRAME_ALGO_TYPE_1;
    private HashSet<Integer> mQ2QScenarioKey = new HashSet<>();

    private static final int PERF_HWUI_POLICY_LEVE_1_CMD = 65;
    private static final int PERF_HWUI_POLICY_LEVE_2_CMD = 66;
    private static final int PERF_HWUI_POLICY_LEVE_3_CMD = 67;

    private HashMap<Integer, Integer> POLICY_CODE_TO_CMD = new HashMap<>(){
        {
            put(AnimScenario.ANIM_POLICY_LEVE_1, PERF_HWUI_POLICY_LEVE_1_CMD);
            put(AnimScenario.ANIM_POLICY_LEVE_2, PERF_HWUI_POLICY_LEVE_2_CMD);
            put(AnimScenario.ANIM_POLICY_LEVE_3, PERF_HWUI_POLICY_LEVE_3_CMD);
        }
    };

    private static class AnimScenarioInternal extends AnimScenario {
        int count = 1;//count same animscenario hint status
        boolean enableQ2Q = false;
    }

    private static class HWUIScenario {
        // 0~100, high->low
        int priority;
        Map<Integer, AnimScenarioInternal> scenarioMap = new HashMap<>();
    }

    public interface HWUIPolicyStateListener {
        public void onHwuiPolicyStateChanged(boolean enable);
        default public void onHwuiPolicyParamChanged(int targetFps,
                int policy, int[] criticalTask){};
        default void onHwuiQ2Q(boolean enable){}
    }

    public static HWUIPolicyManager getInstance() {
        if (sInstance == null) {
            synchronized (HWUIPolicyManager.class) {
                if (sInstance == null) {
                    sInstance = new HWUIPolicyManager();
                }
            }
        }
        return sInstance;
    }

    private HWUIPolicyManager() {
        ScrollState.registerScrollStateListener(this);
    }

    @Override
    public void onScroll(boolean scrolling) {
        if (scrolling) {
            startHWUICtrl(SCROLL_KEY);
        } else {
            stopHWUICtrl(SCROLL_KEY);
        }
    }

    public void startHWUICtrl(int key) {
        startHWUICtrl(key, DEFALUT_PRIORITY);
    }

    public synchronized void startHWUICtrl(int key, int priority) {
        synchronized (STATUS_LOCK) {
            HWUIScenario hwui = null;
            for (HWUIScenario item : mQueue) {
                if (item.priority == priority) {
                    hwui = item;
                    break;
                }
            }
            int policy = AnimScenario.ANIM_POLICY_LEVE_1;
            if (key == SCROLL_KEY || key == LONGPAGE_KEY) {
                policy = -1;
            }
            // same priority HWUIScenario not found, create new one
            // if ask q2q for this key,
            // it will be true util call stop of this key.
            boolean q2q = mQ2QScenarioKey.contains(key);
            if (hwui == null) {
                AnimScenarioInternal scenario = new AnimScenarioInternal();
                scenario.setPolicy(policy)
                    .setTargetFps((int)ScrollState.getRefreshRate());
                hwui = new HWUIScenario();
                hwui.priority = priority;
                scenario.enableQ2Q = q2q;
                hwui.scenarioMap.put(key, scenario);
                mQueue.offer(hwui);
            } else if (!hwui.scenarioMap.containsKey(key)) {
                AnimScenarioInternal scenario = new AnimScenarioInternal();
                scenario.setPolicy(policy)
                    .setTargetFps((int)ScrollState.getRefreshRate());
                scenario.enableQ2Q = q2q;
                hwui.scenarioMap.put(key, scenario);
            } else {
                //this key hwui policy already start, update count!
                AnimScenarioInternal scenario = hwui.scenarioMap.get(key);
                if (scenario != null) {
                    scenario.count += 1;
                }
            }

            if (priority > mLastHWUIPolciyPriority) {
                mLastHWUIPolciyPriority = priority;
            }
            if (q2q) {
                notifyHWUIPolicyStateChanged(false);
                notifyHWUIQ2QStateChanged(true);
            } else {
                updateHWUICtrlStatus(true);
            }
        }
    }

    public void stopHWUICtrl(int key) {
        stopHWUICtrl(key, DEFALUT_PRIORITY);
    }

    public void stopHWUICtrl(int key, int priority) {
        synchronized (STATUS_LOCK) {
            boolean q2q = false;
            HWUIScenario opScenario = null;
            for (HWUIScenario hwui : mQueue) {
                if (hwui.priority == priority) {
                    AnimScenarioInternal scenario = hwui.scenarioMap.get(key);
                    if (scenario != null && --scenario.count == 0) {
                        hwui.scenarioMap.remove(key);
                        if (scenario.enableQ2Q) {
                            q2q = scenario.enableQ2Q;
                            mQ2QScenarioKey.remove(key);
                        }
                    }
                    opScenario = hwui;
                    break;
                }
            }
            if (opScenario != null
                    && opScenario.scenarioMap.isEmpty()) {
                mQueue.remove(opScenario);
            }

            int targetPriority = DEFALUT_PRIORITY;
            //find next high priority scenario
            for (HWUIScenario scenario : mQueue) {
                if (scenario.priority > targetPriority) {
                    targetPriority = scenario.priority;
                }
            }
            mLastHWUIPolciyPriority = targetPriority;
            if (q2q) {
                notifyHWUIQ2QStateChanged(false);
            } else {
                updateHWUICtrlStatus(true);
            }
        }
    }

    private void updateHWUICtrlStatus(boolean updatePolicy) {
        boolean enableHWUICtrl = false;
        HWUIScenario curScenario = null;
        for (HWUIScenario scenario : mQueue) {
            if (scenario.priority == mLastHWUIPolciyPriority) {
                curScenario = scenario;
                if (!scenario.scenarioMap.isEmpty()) {
                    enableHWUICtrl = true;
                }
                break;
            }
        }

        if (mLastHWUIPolciyStatus != enableHWUICtrl) {
            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "updateHWUICtrlStatus enableHWUICtrl="+enableHWUICtrl);
            }
            mLastHWUIPolciyStatus = enableHWUICtrl;
            notifyHWUIPolicyStateChanged(enableHWUICtrl);
        }

        if (updatePolicy) {
            int targetFpsMAX = -1;
            int policyMAX = -1;
            int[] criticalTask = null;

            if (mCriticalTask == null) {
                mCriticalTask = new HashSet<>();
            }

            if (curScenario != null) {
                mCriticalTask.clear();
                Collection<AnimScenarioInternal> values = curScenario.scenarioMap.values();

                for (AnimScenarioInternal scenario : values) {
                    if (scenario.getTargetFps() > targetFpsMAX) {
                        targetFpsMAX = scenario.getTargetFps();
                    }
                    if (scenario.getPolicy() > policyMAX) {
                        policyMAX = scenario.getPolicy();
                    }
                    int[] cirticalArray = scenario.getCriticalTask();
                    if (cirticalArray == null || cirticalArray.length == 0) {
                        continue;
                    }
                    for (int i = 0; i < cirticalArray.length; i++) {
                        if (cirticalArray[i] > 0) {
                            mCriticalTask.add(cirticalArray[i]);
                        }
                    }
                }
                if (targetFpsMAX > 0) {
                    targetFpsMAX = (int)ScrollState.adjClosestFPS(targetFpsMAX);
                }

                if (!mCriticalTask.isEmpty()) {
                    criticalTask = new int[mCriticalTask.size()];
                    int idx = 0;
                    for (Integer tid : mCriticalTask) {
                        criticalTask[idx] = tid.intValue();
                        idx++;
                    }
                }
            }

            int policyCMD = -1;
            if (policyMAX > 0) {
                policyCMD = POLICY_CODE_TO_CMD.get(policyMAX);
            }

            if (LogUtil.DEBUG) {
                LogUtil.traceAndMLogd(TAG, "updateHWUICtrlStatus targetFps=" + targetFpsMAX
                        + " policy=" + policyCMD + " critical="
                        + (criticalTask == null ? "0" : String.valueOf(criticalTask.length)));
            }
            //set Policy in framepolicy thread
            notifyHwuiPolicyParamChanged(targetFpsMAX, policyCMD, criticalTask);
        }
    }

    public void shutdownHWUIPolicy() {
        synchronized (STATUS_LOCK) {
            mQueue.clear();
            mLastHWUIPolciyPriority = DEFALUT_PRIORITY;
            updateHWUICtrlStatus(true);
        }
    }

    public void updateHWUIPolicy(int key, AnimScenario scenario) {
        if (scenario == null) {
            return;
        }
        synchronized (STATUS_LOCK) {
            boolean updatePolicy = true;
            AnimScenarioInternal policyScenario = null;
            for (HWUIScenario item : mQueue) {
                if (item.scenarioMap.containsKey(key)) {
                    policyScenario = item.scenarioMap.get(key);
                    break;
                }
            }

            if (scenario.getAlgorithm()
                    == AnimScenario.ANIM_FRAME_ALGO_TYPE_2){
                mQ2QScenarioKey.add(Integer.valueOf(key));
                updatePolicy= false;
            }

            //copy from scenario
            if (policyScenario != null) {
                policyScenario
                    .setTargetFps(scenario.getTargetFps())
                    .setCriticalTask(scenario.getCriticalTask())
                    .setPolicy(scenario.getPolicy());

                updateHWUICtrlStatus(updatePolicy);
            }
        }
    }

    public void registerHWUIPolicyStateListener(HWUIPolicyStateListener listener) {
        synchronized (LISTENER_LOCK) {
            if (listener != null) {
                mStateListeners.add(listener);
            }
        }
    }

    public void notifyHWUIQ2QStateChanged(boolean enable) {
        synchronized (LISTENER_LOCK) {
            for (HWUIPolicyStateListener listener : mStateListeners) {
                listener.onHwuiQ2Q(enable);
            }
        }
    }

    public void notifyHWUIPolicyStateChanged(boolean enable) {
        synchronized (LISTENER_LOCK) {
            for (HWUIPolicyStateListener listener : mStateListeners) {
                listener.onHwuiPolicyStateChanged(enable);
            }
        }
    }

    public void notifyHwuiPolicyParamChanged(int targetFps, int policy, int[] criticalTask) {
        synchronized (LISTENER_LOCK) {
            for (HWUIPolicyStateListener listener : mStateListeners) {
                listener.onHwuiPolicyParamChanged(targetFps, policy, criticalTask);
            }
        }
    }
}