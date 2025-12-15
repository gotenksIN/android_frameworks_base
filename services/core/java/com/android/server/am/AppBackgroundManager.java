
/* Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.server.am;

import static android.os.Process.THREAD_PRIORITY_TOP_APP_BOOST;
import static com.android.server.cpu.CpuAvailabilityMonitoringConfig.CPUSET_ALL;
import static com.android.server.cpu.CpuAvailabilityMonitoringConfig.CPUSET_BACKGROUND;

import com.android.server.am.ProcessRecord;
import com.android.server.am.ProcessList;
import com.android.server.ServiceThread;
import com.android.server.LocalServices;
import com.android.server.cpu.CpuMonitorInternal;
import com.android.server.cpu.CpuAvailabilityMonitoringConfig;
import com.android.server.cpu.CpuAvailabilityInfo;

import android.os.Trace;
import android.os.IBinder;
import android.os.Process;
import android.os.Handler;
import android.util.Slog;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.BoostFramework;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.ContentResolver;
import android.provider.Settings;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

import java.util.HashMap;
import java.util.Map;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
import java.util.Set;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
import java.util.List;
import java.util.ArrayList;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
import java.util.Iterator;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
public class AppBackgroundManager {
    private static AppBackgroundManager mInstance;
    private static String TAG = "AppBackgroundManager";
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static BoostFramework mPerf = new BoostFramework();
    private static boolean ALREADY_READ_PROPERTIES = false;
    private static final long DEFAULT_LAUNCH_TIMEOUT = 2000;
    private static final long DEFAULT_DELAY_UNFREEZER_TIMEOUT = 1000;
    private static final int DEFAULT_CPU_USAGE_THRESHOLD = 60;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static final int DEFAULT_FREEZE_ADJ_THRESHOLD = ProcessList.FOREGROUND_APP_ADJ + 1;
    private static final int FREEZE_BINDER_TIMEOUT_MS = 10;

// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static final int REPORT_UNFREEZE_SERVICE_MSG = 0;
    private static final int FROZEN_AND_UPDATE_PROCESS_MSG = 1;
    private static final int REPORT_UNFREEZE_PROCESS_MSG = 2;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static final int FREEZE_PACKAGE_LEVEL = 3;
    private static final int UNFREEZE_PACKAGE_LEVEL = 4;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

    public static final int FIRST_LAUNCH_FREEZE = 0;
    public static final int WARM_LAUNCH_FREEZE = 1;
    public static final int COLD_LAUNCH_FREEZE = 2;

    public static final int COMPLETE_LAUNCH_UNFREEZE = 0;
    public static final int INTERRUPT_LAUNCH_UNFREEZE = 1;
    public static final int TIMEOUT_LAUNCH_UNFREEZE = 2;
    public static final int REMOVE_PROCESS_UNFREEZE = 3;
    public static final int CROSS_LAUNCH_UNFREEZE = 4;
    public static final int DEPEND_LAUNCH_UNFREEZE = 5;

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static final int FREEZE_SUCCESS = 0;
    private static final int PID_NOT_FOUND = -1;
    private static final int BINDER_FREEZE_FAILED = -2;
    private static final int SKIP_FREEZE = -3;

// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private Object mPhenotypeFlagLock = new Object();
    private Object mFreezeFlagLock = new Object();
    private Object mCpuHighLoadLock = new Object();
    private final CpuLoadMonitor mCpuLoadMonitor = new CpuLoadMonitor();
    private final Handler mFreezerManagerHandler;
    private volatile boolean mCpuHighLoadFlag = false;
    private static volatile int mFreezeAdjThreshold = DEFAULT_FREEZE_ADJ_THRESHOLD;
    private static volatile long mLaunchTimeout = DEFAULT_LAUNCH_TIMEOUT;
    private static volatile int mCpuUsageThreshold = DEFAULT_CPU_USAGE_THRESHOLD;
    private static volatile boolean mCpuLoadMonitorBG = true;
    private static volatile long mDelayUnfreezeTimeout = DEFAULT_DELAY_UNFREEZER_TIMEOUT;
    private static volatile boolean mUseDebug = false;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static volatile boolean mUseAppBgManager = false;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static volatile boolean mUseCpuLoadMonitor = false;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static volatile boolean mUseProcessLevelFreezer = false;
    private static volatile boolean mUseAggressivePolicy = false;
    private static volatile boolean mUsePackageLevelFreezer = false;
    private static volatile boolean mUseRestrictBgAutoStart = false;

    private static final String SETTINGS_AUTO_START_PREFIX = "auto_start_policy_";
    private static final String SETTINGS_FREEZE_PREFIX = "freeze_policy_";
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

    private final Freezer mFreezer;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private static Context mContext;
    private static ContentResolver mContentResolver;
    private PackageLevelFreezer mPackageFreezerManager;
    private AutoStartManagement mAutoStartManagement;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

    public class CpuLoadMonitor {
        private CpuMonitorInternal mCpuMonitorService = null;
        private int mCpuAvalabilityPercentThreshold = 100 - DEFAULT_CPU_USAGE_THRESHOLD;
        private int mCpuSet = CPUSET_BACKGROUND;
        public class CpuAvailabilityCallback implements CpuMonitorInternal.CpuAvailabilityCallback {
            @Override
            public void onAvailabilityChanged(CpuAvailabilityInfo info) {
                int currentCpuAvalabilityPercent = info.latestAvgAvailabilityPercent;
                boolean isHighLoad =
                    currentCpuAvalabilityPercent < mCpuAvalabilityPercentThreshold ? true : false;
                if (mUseDebug) {
                    if (isHighLoad) {
                        Slog.d(TAG,
                                "Current CPU usage is " + (100 - currentCpuAvalabilityPercent) +
                                " % and convert to high load");
                    } else {
                        Slog.d(TAG,
                                "Current CPU usage is " + (100 - currentCpuAvalabilityPercent) +
                                " % and convert to low load");
                    }
                }
                setCpuHighLoadFlagLocked(isHighLoad);
            }

            @Override
            public void onMonitoringIntervalChanged(long intervalMilliseconds){
                if (mUseDebug) {
                    Slog.d(TAG, "CPU load monitor interval convert to "+ intervalMilliseconds);
                }
            }
        }

        public void setCpuUsageThreshold(int cpuUsageThreshold) {
            int cpuAvalabilityPercentThreshold = 100 - cpuUsageThreshold;
            if (cpuAvalabilityPercentThreshold >= 0 && cpuAvalabilityPercentThreshold <= 100) {
                mCpuAvalabilityPercentThreshold = cpuAvalabilityPercentThreshold;
            } else {
                Slog.d(TAG,
                        cpuUsageThreshold + " is an invalid CPU usage threshold. The default " +
                        DEFAULT_CPU_USAGE_THRESHOLD + " will be used");
            }
        }

        /**
         * Set monitor which CPU load group
         * @param useBgCPU CPU load group
         * false: use CPUSET_ALL
         * true : use CPUSET_BACKGROUND
        */
        public void setCpuSet(boolean useBgCPU) {
            if (useBgCPU) {
                mCpuSet = CPUSET_BACKGROUND;
                Slog.d(TAG, "Monitor the BG CPU load");
            } else {
                mCpuSet = CPUSET_ALL;
                Slog.d(TAG, "Monitor the all CPU load");
            }
        }

        public void startCpuLoadMonitorOnce() {
            if (mCpuMonitorService != null) {
                return;
            }
            CpuAvailabilityCallback callback = new CpuAvailabilityCallback();
            CpuAvailabilityMonitoringConfig config =
                new CpuAvailabilityMonitoringConfig.Builder(mCpuSet).addThreshold(
                        mCpuAvalabilityPercentThreshold).build();
            mCpuMonitorService = LocalServices.getService(CpuMonitorInternal.class);
            if (mCpuMonitorService != null) {
                mCpuMonitorService.addCpuAvailabilityCallback(
                            /* executor= */ null, config, callback);
                Slog.d(TAG, "Already get CPU monitor service and add callback");
            }
        }
    }

    private void setCpuHighLoadFlagLocked(boolean isHighLoad) {
        synchronized (mCpuHighLoadLock) {
            mCpuHighLoadFlag = isHighLoad;
        }
    }

    private boolean getCpuHighLoadFlagLocked() {
        synchronized (mCpuHighLoadLock) {
            return mCpuHighLoadFlag;
        }
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    public static AppBackgroundManager getInstance() {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!ALREADY_READ_PROPERTIES) {
            ALREADY_READ_PROPERTIES = true;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            mUseAppBgManager = Boolean.valueOf(mPerf.perfGetProp(
                    "ro.vendor.perf.app_bg_manager.enable", "false"));
            if (!mUseAppBgManager) {
                return null;
            }
            mUseRestrictBgAutoStart = Boolean.valueOf(mPerf.perfGetProp(
                    "ro.vendor.perf.app_bg_manager.enable_restrict_auto_start", "false"));
            mUseProcessLevelFreezer = Boolean.valueOf(mPerf.perfGetProp(
                    "ro.vendor.perf.app_bg_manager.enable_process_level_freezer", "false"));
            mUsePackageLevelFreezer = Boolean.valueOf(mPerf.perfGetProp(
                    "ro.vendor.perf.app_bg_manager.enable_package_level_freezer", "false"));
            mUseAggressivePolicy = Boolean.valueOf(mPerf.perfGetProp(
                    "ro.vendor.perf.app_bg_manager.enable_aggressive_policy", "false"));
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            mUseCpuLoadMonitor = Boolean.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.enable_cpu_load_monitor", "false"));
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            mCpuUsageThreshold = Integer.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.cpu_load_monitor_usage_threshold",
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    String.valueOf(DEFAULT_CPU_USAGE_THRESHOLD)));
            mCpuLoadMonitorBG = Boolean.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.cpu_load_monitor_cpuset_bg", "true"));
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            mFreezeAdjThreshold = Integer.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.freeze_adj_threshold",
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    String.valueOf(DEFAULT_FREEZE_ADJ_THRESHOLD)));
            mLaunchTimeout = Integer.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.launch_timeout_threshold",
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    String.valueOf(DEFAULT_LAUNCH_TIMEOUT)));
            mDelayUnfreezeTimeout = Integer.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.delay_unfreeze_threshold",
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    String.valueOf(DEFAULT_DELAY_UNFREEZER_TIMEOUT)));
            mUseDebug = Boolean.valueOf(mPerf.perfGetProp(
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    "ro.vendor.perf.app_bg_manager.enable_debug", "false"));

            Slog.d(TAG, "---- app background manager settings ----");
            Slog.d(TAG, "use_app_bg_manager=" + mUseAppBgManager);
            Slog.d(TAG, "enable_restrict_auto_start=" + mUseRestrictBgAutoStart);
            Slog.d(TAG, "enable_process_level_freezer=" + mUseProcessLevelFreezer);
            Slog.d(TAG, "enable_package_level_freezer=" + mUsePackageLevelFreezer);
            Slog.d(TAG, "enable_aggressive_policy=" + mUseAggressivePolicy);
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            Slog.d(TAG, "enable_cpu_load_monitor=" + mUseCpuLoadMonitor);
            Slog.d(TAG, "cpu_load_monitor_usage_threshold=" + mCpuUsageThreshold);
            Slog.d(TAG, "cpu_load_monitor_cpuset_bg=" + mCpuLoadMonitorBG);
            Slog.d(TAG, "freeze_adj_threshold=" + mFreezeAdjThreshold);
            Slog.d(TAG, "launch_timeout_threshold=" + mLaunchTimeout);
            Slog.d(TAG, "delay_unfreeze_threshold=" + mDelayUnfreezeTimeout);
            Slog.d(TAG, "enable_debug=" + mUseDebug);
        }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!mUseAppBgManager) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            return null;
        }
        if (mInstance == null) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            synchronized (AppBackgroundManager.class) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                if (mInstance == null) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    mInstance = new AppBackgroundManager();
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                }
            }
        }
        return mInstance;
    }

    private static final String getFreezeReason(int freezeReason) {
        switch (freezeReason) {
            case FIRST_LAUNCH_FREEZE:
                return "First launch";
            case WARM_LAUNCH_FREEZE:
                return "Warm launch";
            case COLD_LAUNCH_FREEZE:
                return "Cold launch";
            default:
                return "Unknown";
        }
    }

    private static final String getUnfreezeReason(int unfreezeReason) {
        switch (unfreezeReason) {
            case COMPLETE_LAUNCH_UNFREEZE:
                return "Complete launch";
            case INTERRUPT_LAUNCH_UNFREEZE:
                return "Interrupt launch";
            case TIMEOUT_LAUNCH_UNFREEZE:
                return "Launch timeout";
            case REMOVE_PROCESS_UNFREEZE:
                return "Remove main process";
            case CROSS_LAUNCH_UNFREEZE:
                return "Cross launch process";
            case DEPEND_LAUNCH_UNFREEZE:
                return "Dependent launch";
            default:
                return "Unknown";
        }
    }

    private static ServiceThread createAndStartFreezeThread() {
        final ServiceThread freezerManagerThread = new ServiceThread(
                "FreezerManagerThread", THREAD_PRIORITY_TOP_APP_BOOST, true /* allowIo */);
        freezerManagerThread.start();
        return freezerManagerThread;
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private AppBackgroundManager() {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (mUseCpuLoadMonitor) {
            mCpuLoadMonitor.setCpuUsageThreshold(mCpuUsageThreshold);
            mCpuLoadMonitor.setCpuSet(mCpuLoadMonitorBG);
        }

        mFreezer = new Freezer();
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        mPackageFreezerManager = new PackageLevelFreezer();
        mAutoStartManagement = new AutoStartManagement();
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

        mFreezerManagerHandler = new Handler(createAndStartFreezeThread().getLooper(), msg -> {
            switch (msg.what) {
                case REPORT_UNFREEZE_SERVICE_MSG: {
                    final int unfreezeReason = msg.arg1;
                    final ProcessRecord app = (ProcessRecord)msg.obj;
                    if (!checkInFreezeProcessLocked(app)) {
                        Slog.d(TAG, "skip unfreeze service: skip reason: " + app.processName +
                                " has been removed from freeze list");
                        break;
                    }
                    if (mUseDebug) {
                        String unfreezeReasonStr = getUnfreezeReason(unfreezeReason);
                        Slog.d(TAG, "= start unfreeze service: " + app.processName +
                                ", reason: " + unfreezeReasonStr);
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start unfreeze service: " + app.processName +
                                ", reason: " + unfreezeReasonStr);
                    }

                    unFreezeProcess(app);
                    removeProcessFromListLocked(app);

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case FROZEN_AND_UPDATE_PROCESS_MSG: {
                    final int freezeReason = msg.arg1;
                    final String packageName = (String)msg.obj;
                    if (mUseDebug) {
                        String freezeReasonStr = getFreezeReason(freezeReason);
                        Slog.d(TAG,
                                "# start freeze processes which adj >= " + mFreezeAdjThreshold +
                                " for " + packageName + ", reason: " + freezeReasonStr);
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start freeze processes which adj >= " + mFreezeAdjThreshold +
                                " for " + packageName + ", reason: " + freezeReasonStr);
                    }

                    synchronized (mFreezeFlagLock) {
                        final SparseArray<ProcessRecord> needFreezeProcesses =
                                getFreezeProcessesLocked(packageName);
                        if (needFreezeProcesses != null) {
                            List<ProcessRecord> pidsToRemove = new ArrayList<>();
                            for (int i = 0; i < needFreezeProcesses.size(); i++) {
                                int pid = needFreezeProcesses.keyAt(i);
                                ProcessRecord app = needFreezeProcesses.valueAt(i);
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                                if (freezeProcess(app) == FREEZE_SUCCESS) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                                    pidsToRemove.add(app);
                                }
                            }
                            removeProcessFromListLocked(packageName, pidsToRemove);
                            if (mUseDebug) {
                                Slog.d(TAG, "# number of processes to freeze is " +
                                        needFreezeProcesses.size() + " for " + packageName);
                            }
                        } else {
                            Slog.d(TAG, "freeze object is null for " + packageName);
                        }
                    } // end of synchronized (mFreezeFlagLock)

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case REPORT_UNFREEZE_PROCESS_MSG: {
                    final int unfreezeReason = msg.arg1;
                    final String packageName = (String)msg.obj;
                    if (!packageContainKey(packageName)) {
                        Slog.e(TAG, "Alread triggered unfreeze for " + packageName);
                        break;
                    }

                    if (mUseDebug) {
                        String unfreezeReasonStr = getUnfreezeReason(unfreezeReason);
                        Slog.d(TAG, "= start unfreeze processes for " + packageName +
                                ", reason: " + unfreezeReasonStr);
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start unfreeze processes for " + packageName +
                                ", reason: " + unfreezeReasonStr);
                    }

                    synchronized (mFreezeFlagLock) {
                        final SparseArray<ProcessRecord> needUnfreezeProcesses =
                                getUnfreezeProcessesLocked(packageName);
                        if (needUnfreezeProcesses != null) {
                            for (int i = 0; i < needUnfreezeProcesses.size(); i++) {
                                int pid = needUnfreezeProcesses.keyAt(i);
                                ProcessRecord app = needUnfreezeProcesses.valueAt(i);
                                unFreezeProcess(app);
                            }
                            if (mUseDebug) {
                                Slog.d(TAG, "= number of processes to unfreeze is " +
                                        needUnfreezeProcesses.size() + " for " + packageName);
                            }
                            removePackageLocked(packageName);
                            removeFreezeRecordLocked(packageName);
                        } else {
                            Slog.d(TAG, "unfreeze object is null for " + packageName);
                        }
                    } // end of synchronized (mFreezeFlagLock)

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                case FREEZE_PACKAGE_LEVEL: {
                    // freeze the processes in pending list firstly.
                    final List<ProcessRecord> pList = mPackageFreezerManager.getPendingList();
                    for (int i=0; i<pList.size(); i++) {
                        ProcessRecord pr = pList.get(i);
                        if (pr == null || pr.getPid() <= 0) {
                            mPackageFreezerManager.removeProcessFromPendingList(pr);
                            continue;
                        }

                        int rc = freezeProcess(pr);
                        switch (rc) {
                            case FREEZE_SUCCESS:
                                if (mUseDebug) {
                                    Slog.d(TAG, "Freeze succeeded for process: "
                                            + pr.processName + " from pending list.");
                                }
                                pr.setDebugging(true);
                                mPackageFreezerManager.removeProcessFromPendingList(pr);
                                mPackageFreezerManager.appendAppPids(pr.info.packageName, pr);
                                break;
                            case BINDER_FREEZE_FAILED:
                                if (mUseDebug) {
                                    Slog.d(TAG, "Binder freeze failed for process: "
                                            + pr.processName + ", will retry later");
                                }
                                break;
                            default:
                                if (mUseDebug) {
                                    Slog.d(TAG, "Freeze failed for process: "
                                            + pr.processName + ", removing from pending list");
                                }
                                mPackageFreezerManager.removeProcessFromPendingList(pr);
                                break;
                        }
                    }

                    String packageName = (String)msg.obj;
                    final SparseArray<ProcessRecord> pids =
                            mPackageFreezerManager.findRelatedPids(packageName);

                    if (mUseDebug) {
                        String trace = "Start freeze \"" + packageName + "\" application. ";
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, trace);
                        Slog.d(TAG, trace);
                    }

                    List<Integer> toRemove = new ArrayList<>();

                    for (int i = 0; i < pids.size(); i++) {
                        int pid = pids.keyAt(i);
                        ProcessRecord pr = pids.valueAt(i);
                        if (pr == null || pr.getPid() <= 0) {
                            toRemove.add(pid);
                            continue;
                        }
                        int rc = freezeProcess(pr);
                        switch (rc) {
                            case FREEZE_SUCCESS:
                                // Avoid be killed due to bg ANR
                                pr.setDebugging(true);
                                break;
                            case BINDER_FREEZE_FAILED:
                                Slog.w(TAG, "Add to pending list for freezing again later: "
                                        + pr.processName);
                                mPackageFreezerManager.appendPendingList(pr);
                                toRemove.add(pid);
                                break;
                            default:
                                Slog.e(TAG, "Freeze failed for process: " + pr.processName);
                                break;
                        }
                    }

                    for (int i = toRemove.size() - 1; i >= 0; i--) {
                        pids.remove(toRemove.get(i));
                    }

                    mPackageFreezerManager.addAppPids(packageName, pids);

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case UNFREEZE_PACKAGE_LEVEL: {
                    String packageName = (String)msg.obj;
                    final SparseArray<ProcessRecord> pids =
                            mPackageFreezerManager.getAppPids(packageName);

                    if (pids == null) {
                        mPackageFreezerManager.removeAppPids(packageName);
                        return true;
                    }

                    if (mUseDebug) {
                        String trace = "Start unfreeze \"" + packageName + "\" application. ";
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, trace);
                        Slog.d(TAG, trace);
                    }

                    for (int i = 0; i < pids.size(); i++) {
                        ProcessRecord pr = pids.valueAt(i);
                        unFreezeProcess(pr);
                        // Can be killed due to bg ANR
                        pr.setDebugging(false);
                    }

                    mPackageFreezerManager.removeAppPids(packageName);

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                default:
                    return true;
            }
            return true;
        });
    }

    final PidMap mPidsSelfLocked = new PidMap();
    static final class PidMap {
        private final SparseArray<ProcessRecord> mPidMap = new SparseArray<>();

        ProcessRecord get(int pid) {
            return mPidMap.get(pid);
        }

        int size() {
            return mPidMap.size();
        }

        ProcessRecord valueAt(int index) {
            return mPidMap.valueAt(index);
        }

        int keyAt(int index) {
            return mPidMap.keyAt(index);
        }

        int indexOfKey(int key) {
            return mPidMap.indexOfKey(key);
        }

        void doAddInternal(int pid, ProcessRecord app) {
            mPidMap.put(pid, app);
        }

        boolean doRemoveInternal(int pid, ProcessRecord app) {
            final ProcessRecord existingApp = mPidMap.get(pid);
            if (existingApp != null && existingApp.getStartSeq() == app.getStartSeq()) {
                mPidMap.remove(pid);
                return true;
            }
            return false;
        }
    }

    void addPidLocked(ProcessRecord app) {
        synchronized (mPidsSelfLocked) {
            mPidsSelfLocked.doAddInternal(app.getPid(), app);
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

            if (mContext == null) {
                mContext = app.mService.mContext;
                mContentResolver = mContext.getContentResolver();
            }
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        }
    }

    boolean removePidLocked(int pid, ProcessRecord app) {
        synchronized (mPidsSelfLocked) {
            return mPidsSelfLocked.doRemoveInternal(pid, app);
        }
    }

    ProcessRecord findProcessByNameLocked(String processName) {
        synchronized (mPidsSelfLocked) {
            for ( int i = 0; i < mPidsSelfLocked.size(); i++) {
                ProcessRecord foundProcess = mPidsSelfLocked.valueAt(i);
                if (foundProcess.processName.equals(processName)) {
                    return foundProcess;
                }
            }
        }
        return null;
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private SparseArray<ProcessRecord> findPidsByPackageName(String packageName) {
        SparseArray<ProcessRecord> pids = new SparseArray<>();
        synchronized (mPidsSelfLocked) {
            for (int i = 0; i < mPidsSelfLocked.size(); i++) {
                final ProcessRecord app = mPidsSelfLocked.valueAt(i);
                if (app.info.packageName.equals(packageName)) {
                    pids.put(app.getPid(), app);
                }
            }
        }
        return pids;
    }

// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private SparseArray<ProcessRecord> findNeedFreezeProcessesLocked(String processName) {
        SparseArray<ProcessRecord> needFreezeProcesses = new SparseArray<>();
        synchronized (mPidsSelfLocked) {
            for (int i = 0; i < mPidsSelfLocked.size(); i++) {
                final ProcessRecord app = mPidsSelfLocked.valueAt(i);
                final ProcessStateRecord state = app.mState;
                if (state.getCurAdj() >= ProcessList.FOREGROUND_APP_ADJ) {
                    String appPackageName = app.info.packageName;
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    if (processName.equals(appPackageName) || app.info.isSystemApp()) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                        continue;
                    }
                    needFreezeProcesses.put(app.getPid(), app);
                }
            }
            return needFreezeProcesses;
        }
    }

    final PackageMap mPackagesSelfLocked = new PackageMap();
    static final class PackageMap {
        // key  : application package name
        // value: list of processes to freeze
        private final Map<String, SparseArray<ProcessRecord>>  mPackageMap = new HashMap<>();

        SparseArray<ProcessRecord> get(String processName) {
            return mPackageMap.get(processName);
        }

        boolean contains(String processName) {
            return mPackageMap.containsKey(processName);
        }

        int size() {
            return mPackageMap.size();
        }

        ArrayList<String> getAllKeys() {
            ArrayList<String> packageNameList = new ArrayList<String>();
            for (String packageName : mPackageMap.keySet()) {
                packageNameList.add(packageName);
            }
            return packageNameList;
        }

        void put(String processName, SparseArray<ProcessRecord> pidList) {
            mPackageMap.put(processName, pidList);
        }

        boolean remove(String processName) {
            if (mPackageMap.containsKey(processName)) {
                mPackageMap.remove(processName);
                return true;
            }
            return false;
        }

        void clear() {
            mPackageMap.clear();
        }
    }

    private boolean checkInFreezeProcessLocked(ProcessRecord app) {
        int pid = app.getPid();
        synchronized (mPackagesSelfLocked) {
            for (String packageName : mPackagesSelfLocked.mPackageMap.keySet()) {
                SparseArray<ProcessRecord> freezeList = mPackagesSelfLocked.get(packageName);
                if (freezeList.get(pid) != null) {
                    return true;
                }
            }
            return false;
        }
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private boolean isUsingForegroundService(ProcessRecord app) {
        final int curSchedGroup = app.mState.getCurrentSchedulingGroup();

        if (curSchedGroup == ProcessList.SCHED_GROUP_BACKGROUND) {
            if (mUseDebug) {
                Slog.d(TAG, "isUsingForegroundService for " + app.processName
                            + ": No foreground service found.");
            }
            return false;
        }

        return true;
    }

// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private boolean isBoundClient(ProcessRecord app, String processName, boolean equal) {
        final ProcessServiceRecord psr = app.mServices;
        int sevicesNum = psr.numberOfRunningServices();
        boolean isBound = false;
        for (int i = sevicesNum - 1; i >= 0; i--) {
            final ServiceRecord sr = psr.getRunningServiceAt(i);
            if (sr == null) {
                continue;
            }

            ArrayMap<IBinder, ArrayList<ConnectionRecord>> conns = sr.getConnections();
            for (int conni = conns.size() - 1; conni >= 0; conni--) {
                ArrayList<ConnectionRecord> c = conns.valueAt(conni);
                for (int con = 0; con < c.size(); con++) {
                    ConnectionRecord cr = c.get(con);
                    if (equal) {
                        if (cr.clientPackageName.equals(processName)) {
                            isBound = true;
                            if (mUseDebug) {
                                Slog.d(TAG,
                                        "Immediately unfreeze service " + app.processName +
                                        ". Reason: depend on service(" + sr.processName +
                                        ") when launch " + processName);
                            }
                            return isBound;
                        }
                    } else {
                        isBound = true;
                        if (mUseDebug) {
                            Slog.d(TAG,
                                    "  " + app.processName + " has been bound client (" +
                                    cr.clientPackageName + ").");
                            continue;
                        }
                        return isBound;
                    }
                }
            } // end of for (int conni = conns.size() - 1; ...
        } // end of for (int i = sevicesNum - 1; ...
        return isBound;
    }

    public boolean checkNeedFreezeProcessLocked(ProcessRecord app) {
        int pid = app.getPid();
        boolean isInList = false;
        synchronized (mPackagesSelfLocked) {
            for (String packageName : mPackagesSelfLocked.mPackageMap.keySet()) {
                SparseArray<ProcessRecord> freezeList = mPackagesSelfLocked.get(packageName);
                if (freezeList.get(pid) == null) {
                    continue;
                }
                if (isBoundClient(app, packageName, true)) {
                    isInList = true;
                }
            }
            return isInList;
        }
    }

    private void removeProcessFromListLocked(ProcessRecord app) {
        int pid = app.getPid();
        synchronized (mPackagesSelfLocked) {
            for (String packageName : mPackagesSelfLocked.mPackageMap.keySet()) {
                SparseArray<ProcessRecord> freezeList = mPackagesSelfLocked.get(packageName);
                if (freezeList.get(pid) != null) {
                    freezeList.remove(pid);
                }
            }
        }
    }

    private void removeProcessFromListLocked(String processName, List<ProcessRecord> pidsToRemove) {
        synchronized (mPackagesSelfLocked) {
            SparseArray<ProcessRecord> freezeList = mPackagesSelfLocked.get(processName);
            for (ProcessRecord process : pidsToRemove) {
                freezeList.remove(process.getPid());
            }
        }
    }

    private boolean packageContainKey(String processName) {
        synchronized (mPackagesSelfLocked) {
            return mPackagesSelfLocked.contains(processName);
        }
    }

    private SparseArray<ProcessRecord> getFreezeProcessesLocked(String processName) {
        synchronized (mPackagesSelfLocked) {
            if (mPackagesSelfLocked.contains(processName)) {
                return mPackagesSelfLocked.get(processName);
            }
            return null;
        }
    }

    private SparseArray<ProcessRecord> getUnfreezeProcessesLocked(String processName) {
        synchronized (mPackagesSelfLocked) {
            if (mPackagesSelfLocked.contains(processName)) {
                return mPackagesSelfLocked.get(processName);
            }
            return null;
        }
    }

    private int getPackageSizeLocked() {
        synchronized (mPackagesSelfLocked) {
            return mPackagesSelfLocked.size();
        }
    }

    private void addPackageLocked(String processName, SparseArray<ProcessRecord> pidList) {
        synchronized (mPackagesSelfLocked) {
            mPackagesSelfLocked.put(processName, pidList);
        }
    }

    private boolean removePackageLocked(String processName) {
        synchronized (mPackagesSelfLocked) {
            SparseArray<ProcessRecord> freezeList = mPackagesSelfLocked.get(processName);
            freezeList.clear();
            return mPackagesSelfLocked.remove(processName);
        }
    }

    private ArrayList<String> getPackageNameListLocked() {
        synchronized (mPackagesSelfLocked) {
            return mPackagesSelfLocked.getAllKeys();
        }
    }

    private void clearPackageLocked() {
        synchronized (mPackagesSelfLocked) {
            mPackagesSelfLocked.clear();
        }
    }

    private final Map<String, Integer>  mProcessFreezeRecordLocked = new HashMap<>();
    private int getFreezeRecordLocked(String processName) {
        synchronized (mProcessFreezeRecordLocked) {
            if (mProcessFreezeRecordLocked.containsKey(processName)){
                return mProcessFreezeRecordLocked.get(processName);
            }
            return -1;
        }
    }

    private void addFreezeRecordLocked(String processName, int freezeReason) {
        synchronized (mProcessFreezeRecordLocked) {
            mProcessFreezeRecordLocked.put(processName, freezeReason);
        }
    }

    private void removeFreezeRecordLocked(String processName) {
        synchronized (mProcessFreezeRecordLocked) {
            if (mProcessFreezeRecordLocked.containsKey(processName)){
                mProcessFreezeRecordLocked.remove(processName);
            }
        }
    }

    private void unFreezeProcess(ProcessRecord app) {
        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        final ProcessStateRecord state = app.mState;
        int pid = app.getPid();
        int uid = app.uid;
        String processName = app.processName;
        String logInfo = String.format("app info: uid=%d, pid=%d, adj=%d, frozen=%b, proc name=%s",
                uid, pid, state.getCurAdj(), opt.isFrozen(), processName);
        // skip default frozen process and killed process (pid==0)
        if (opt.isFrozen() || pid == 0){
            if (mUseDebug) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "skip unfreeze: " + logInfo);
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                if (opt.isFrozen()) {
                    Slog.d(TAG,
                            " *skip unfreeze: skip reason: process is frozen by default freezer. "
                            + logInfo);
                }
                if (pid == 0) {
                    Slog.d(TAG," *skip unfreeze: skip reason: process is dead. " + logInfo);
                }
            }
            return;
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, logInfo);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "unfreeze binder: " + logInfo);
        }

        try {
            int rc = mFreezer.freezeBinder(pid, false, 2 /* timeout_ms */);
            if (rc != 0) {
                Slog.w(TAG, " *unable to unfreeze binder: " +  logInfo + " " + rc );
            } else {
                if (mUseDebug) {
                    Slog.d(TAG,"  unfreeze binder:  " + logInfo);
                }
            }
        } catch (RuntimeException e) {
            Slog.w(TAG, " *unable to unfreeze binder for " + pid + ": " + e);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "unable to unfreeze binder: " + logInfo);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of unfreeze binder
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "unfreeze process: " + logInfo);
        }

        try{
            mFreezer.setProcessFrozen(pid, uid, false);
            if (mUseDebug) {
                Slog.d(TAG, "  unfreeze process: " +  logInfo);
            }
        } catch (Exception e) {
            Slog.w(TAG, " *unable to unfreeze process: " + logInfo + " " + e);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "unable to unfreeze process: " + logInfo);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of unfreeze process
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of app info
        }
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private int freezeProcess(ProcessRecord app) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        final ProcessStateRecord state = app.mState;
        final ProcessServiceRecord psr = app.mServices;
        int pid = app.getPid();
        int uid = app.uid;
        int sevicesNum = psr.numberOfRunningServices();
        String processName = app.processName;
        String logInfo = String.format(
                "app info: uid=%d, pid=%d, adj=%d, frozen=%b, services=%d, proc name=%s",
                uid, pid, state.getCurAdj(), opt.isFrozen(), sevicesNum, processName);
        boolean freezeBinderSuccess = false;
        boolean freezeProcessSuccess = false;
        // skip freeze process that is frozen by system freezer
        if (opt.isFrozen() || pid == 0) {
            if (mUseDebug) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "skip frozen process: "+ logInfo);
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                if (opt.isFrozen()) {
                    Slog.d(TAG,
                            " *skip freeze: skip reason: process is frozen by default freezer. " +
                            logInfo);
                }
                if (pid == 0) {
                    Slog.d(TAG," *skip freeze: skip reason: process is dead. " + logInfo);
                }
            }
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            return pid == 0 ? PID_NOT_FOUND : SKIP_FREEZE;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        }

        if (state.getCurAdj() < mFreezeAdjThreshold) {
            if (mUseDebug) {
                Slog.d(TAG," *skip freeze: skip reason: process's adj < " +
                        mFreezeAdjThreshold + ". " + logInfo);
            }
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            return SKIP_FREEZE;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        final boolean isHighPriorityApp = (state.getCurAdj() >= ProcessList.FOREGROUND_APP_ADJ
                                   && state.getCurAdj() <= ProcessList.PERCEPTIBLE_APP_ADJ);

        if (isHighPriorityApp) {
            if (mUseAggressivePolicy) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                if (mUseDebug) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                    Slog.d(TAG, String.format(
                        "Skipping fg service check for %s (Adj: %d) due to aggressive policy.",
                        app != null ? app.processName : "unknown", state.getCurAdj()));
                }
            } else {
                boolean isUsingFgService = isUsingForegroundService(app);
                if (isUsingFgService) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format(
                            "Skipping freeze for %s (Adj: %d): Foreground service is running. %s",
                            processName, state.getCurAdj(), logInfo));
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                        String.format("SkippingFreeze|FGService:%s|Adj:%d",
                                        processName, state.getCurAdj()));
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                    return SKIP_FREEZE;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
                }
            }
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, logInfo);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "freeze binder: " + logInfo);
        }

        try {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            int rc = mFreezer.freezeBinder(pid, true, FREEZE_BINDER_TIMEOUT_MS);
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            if (rc != 0){
                Slog.w(TAG, " *unable to freeze binder for " + pid + ": " + rc);
            } else {
                freezeBinderSuccess = true;
                if (mUseDebug) {
                    Slog.d(TAG,"  freeze binder : " + logInfo);
                }
            }
        } catch (RuntimeException e) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "unable to freeze binder: " + logInfo);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            Slog.w(TAG, "  unbale to freeze binder: " + logInfo);
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of freeze binder
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "freeze process: " + logInfo);
        }

        try {
            if (freezeBinderSuccess) {
                mFreezer.setProcessFrozen(pid, uid, true);
                if (mUseDebug) {
                    Slog.d(TAG, "  freeze process: " + logInfo);
                }
                freezeProcessSuccess = true;
            } else {
                Slog.d(TAG,
                        " *skip freeze process: skip reason: unable to freeze process's binder. " +
                        logInfo);
            }
        } catch (RuntimeException e) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "unable to freeze process: " + logInfo);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            Slog.w(TAG, "  unbale to freeze process: " + logInfo);
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of freeze process
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of app info
        }
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer

        if (!freezeBinderSuccess) {
            return BINDER_FREEZE_FAILED;
        }
        return FREEZE_SUCCESS;
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    }

    public boolean isMainProcess(String packageName) {
        return !packageName.contains(":");
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    private boolean isSystemApp(String processName) {
        ProcessRecord pr = findProcessByNameLocked(processName);
        if (pr == null) {
            return false;
        }

        if (pr.info.isSystemApp()) {
            return true;
        }
        return false;
    }

// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    public void startFreeze(String packageName, int freezeReason) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!mUseProcessLevelFreezer) {
            return;
        }
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (mUseCpuLoadMonitor) {
            mCpuLoadMonitor.startCpuLoadMonitorOnce();
        }
        startFreezeInternal(packageName, freezeReason);
    }

    private void startFreezeInternal(String packageName, int freezeReason) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!isMainProcess(packageName) || isSystemApp(packageName)) {
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
            return;
        }

        if (packageContainKey(packageName)) {
            // make sure that already triggered freeze.
            Slog.d(TAG, "Already triggered freeze for " + packageName);
            return;
        }

        if (mUseCpuLoadMonitor && !getCpuHighLoadFlagLocked()) {
            if (mUseDebug) {
                Slog.d(TAG, "Skip freeze: skip reason: CPU load is low when launching " +
                        packageName);
            }
            return;
        }
        // Avoid cross launch
        startUnfreezeAll();
        SparseArray<ProcessRecord> needFreezeProcesses = findNeedFreezeProcessesLocked(packageName);
        if (needFreezeProcesses.size() == 0) {
            if (mUseDebug) {
                Slog.d(TAG,
                        "skip freeze: skip reason: No proper processes to freeze for " +
                        packageName);
            }
            return;
        }
        addFreezeRecordLocked(packageName, freezeReason);
        addPackageLocked(packageName, needFreezeProcesses);
        mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                FROZEN_AND_UPDATE_PROCESS_MSG, freezeReason, 0 /* unused */, packageName));
        startTimeoutUnfreeze(packageName);
    }

    private void startTimeoutUnfreeze(String packageName){
        // add a timeout unfreeze mechanism
        mFreezerManagerHandler.sendMessageDelayed(mFreezerManagerHandler.obtainMessage(
                REPORT_UNFREEZE_PROCESS_MSG, TIMEOUT_LAUNCH_UNFREEZE, 0 /* unused */, packageName),
                mLaunchTimeout);
    }

    private void removeTimeoutUnfreeze(String packageName){
        // remove timeout unfreeze mechanism
        mFreezerManagerHandler.removeMessages(REPORT_UNFREEZE_PROCESS_MSG, packageName);
    }

    private void startUnfreezeAll() {
        ArrayList<String> packageNameList = getPackageNameListLocked();
        for (String packageName : packageNameList) {
            startUnfreezeInternal(packageName, CROSS_LAUNCH_UNFREEZE);
        }
    }

    // unfreeze process that the application depends on when it launchs.
    public void startUnfreezeService(ProcessRecord app, int unfreezeReason) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!mUseProcessLevelFreezer) {
            return;
        }
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                REPORT_UNFREEZE_SERVICE_MSG, unfreezeReason, 0 /* unused */, app));
    }

    public void startUnfreeze(String packageName, int unfreezeReason) {
// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        if (!mUseProcessLevelFreezer) {
            return;
        }
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        startUnfreezeInternal(packageName, unfreezeReason);
    }

    private void startUnfreezeInternal(String packageName, int unfreezeReason) {
        if (!packageContainKey(packageName)) {
            return;
        }

        removeTimeoutUnfreeze(packageName);
        if (unfreezeReason == COMPLETE_LAUNCH_UNFREEZE) {
            int freezeReason = getFreezeRecordLocked(packageName);
            if (freezeReason == WARM_LAUNCH_FREEZE) {
                mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                        REPORT_UNFREEZE_PROCESS_MSG, unfreezeReason, 0 /* unused */, packageName));
            } else {
                mFreezerManagerHandler.sendMessageDelayed(mFreezerManagerHandler.obtainMessage(
                        REPORT_UNFREEZE_PROCESS_MSG, unfreezeReason, 0 /* unused */, packageName),
                        mDelayUnfreezeTimeout);
            }
        } else {
            mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                    REPORT_UNFREEZE_PROCESS_MSG, unfreezeReason, 0 /* unused */, packageName));
        }
    }

// QTI_BEGIN: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
    public boolean useAppBgManager() {
        return mUseAppBgManager;
    }

    public boolean isPackageExemptFromAutoStart(String packageName) {
        if (mContentResolver == null) {
            return true;
        }

        try {
            if (Settings.Global.getInt(mContentResolver,
                        SETTINGS_AUTO_START_PREFIX + packageName) == 1) {
                if (mUseDebug) {
                    Slog.d(TAG,
                        "Package " + packageName + " is exempt from auto-start by settings");
                }
                return true;
            } else {
                if (mUseDebug) {
                    Slog.d(TAG,
                        "Package " + packageName + " is NOT exempt from auto-start by settings");
                }
                return false;
            }
        } catch (Settings.SettingNotFoundException e) {
            // If setting doesn't exist, assume it's allowed for auto start
            return true;
        }
    }

    public boolean isPackageExemptFromFreeze(String packageName) {
        if (mContentResolver == null) {
            return true;
        }

        try {
            if (Settings.Global.getInt(mContentResolver,
                        SETTINGS_FREEZE_PREFIX + packageName) == 0) {
                if (mUseDebug) {
                    Slog.d(TAG,
                        "Package " + packageName + " is exempt from freeze by settings");
                }
                return true;
            } else {
                if (mUseDebug) {
                    Slog.d(TAG,
                        "Package " + packageName + " is NOT exempt from freeze by settings");
                }
                return false;
            }
        } catch (Settings.SettingNotFoundException e) {
            // If setting doesn't exist, assume it's disabled for freeze
            return true;
        }
    }

    public class PackageLevelFreezer {
        private final Map<String, SparseArray<ProcessRecord>> mAppPids = new HashMap<>();
        public List<ProcessRecord> mPendingFreezeList = new ArrayList<>();
        public final Object mPendingFreezeListLock = new Object();
        private final Object mAppPidsLock = new Object();

        public void appendPendingList(ProcessRecord app) {
            if (app == null) {
                return;
            }
            synchronized (mPendingFreezeListLock) {
                mPendingFreezeList.add(app);
            }
        }

        public List<ProcessRecord> getPendingList() {
            synchronized (mPendingFreezeListLock) {
                return new ArrayList<>(mPendingFreezeList);
            }
        }

        public void removePendingList(String packageName) {
            if (packageName == null) {
                return;
            }
            synchronized (mPendingFreezeListLock) {
                Iterator<ProcessRecord> iterator = mPendingFreezeList.iterator();
                while (iterator.hasNext()) {
                    ProcessRecord app = iterator.next();
                    if (app != null && packageName.equals(app.info.packageName)) {
                        if (mUseDebug) {
                            Slog.d(TAG, "Removing process from pending list: " + app.processName);
                        }
                        iterator.remove();
                    }
                }
            }
        }

        public boolean removeProcessFromPendingList(ProcessRecord app) {
            if (app == null) {
                return false;
            }
            synchronized (mPendingFreezeListLock) {
                return mPendingFreezeList.remove(app);
            }
        }

        public SparseArray<ProcessRecord> getAppPids(String packageName) {
            if (packageName == null) {
                return null;
            }
            synchronized (mAppPidsLock) {
                return mAppPids.get(packageName);
            }
        }

        public void appendAppPids(String packageName, ProcessRecord app) {
            if (packageName == null || app == null) {
                return;
            }
            synchronized (mAppPidsLock) {
                SparseArray<ProcessRecord> pids = mAppPids.get(packageName);
                if (pids == null) {
                    pids = new SparseArray<>();
                    mAppPids.put(packageName, pids);
                }
                pids.put(app.getPid(), app);
            }
        }

        public void addAppPids(String packageName, SparseArray<ProcessRecord> pids) {
            if (packageName == null || pids == null) {
                return;
            }
            synchronized (mAppPidsLock) {
                mAppPids.put(packageName, pids);
            }
        }

        public void removeAppPids(String packageName) {
            if (packageName == null) {
                return;
            }
            synchronized (mAppPidsLock) {
                mAppPids.remove(packageName);
            }
        }

        public boolean containsApp(String packageName) {
            if (packageName == null) {
                return false;
            }
            synchronized (mAppPidsLock) {
                return mAppPids.containsKey(packageName);
            }
        }

        public SparseArray<ProcessRecord> findRelatedPids(String packageName) {
            return findPidsByPackageName(packageName);
        }

        public void freezePackageLevel(String packageName) {
            if (containsApp(packageName)) {
                if (mUseDebug) {
                    Slog.d(TAG, "Skipping freeze request for " + packageName
                            + ": Already marked as frozen.");
                }
                return;
            }

            mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                FREEZE_PACKAGE_LEVEL, 0, 0 /* unused */, packageName));
            if (mUseDebug) {
                Slog.i(TAG, "Freeze request queued for package: " + packageName);
            }
        }

        public void unfreezePackageLevel(String packageName) {
            if (packageName == null) {
                return;
            }

            removePendingList(packageName);
            if (!containsApp(packageName)) {
                if (mUseDebug) {
                    Slog.d(TAG, "Skipping unfreeze request for " + packageName
                            + ": Not currently marked as frozen.");

                }
                return;
            }
            mFreezerManagerHandler.sendMessage(mFreezerManagerHandler.obtainMessage(
                UNFREEZE_PACKAGE_LEVEL, 0, 0 /* unused */, packageName));
            if (mUseDebug) {
                Slog.i(TAG, "Unfreeze request queued for package: " + packageName);
            }
        }
    }

    private void freezePackageLevel(String packageName) {
        mPackageFreezerManager.freezePackageLevel(packageName);
    }

    private void unfreezePackageLevel(String packageName) {
        mPackageFreezerManager.unfreezePackageLevel(packageName);
    }

    public class AutoStartManagement {
        private final String CUR_TAG = TAG;
        private final Map<String, Boolean> mMainProcState = new HashMap<>();
        private Object mLock = new Object();

        public boolean shouldPreventStart(String packageName, String processName) {
            synchronized (mLock) {
                final Boolean isMainProcFg  = mMainProcState.get(packageName);
                if (isMainProcFg  != null && isMainProcFg ) {
                    if (mUseDebug) {
                        Slog.d(CUR_TAG,
                            "Allowed: " + processName + " for package " + packageName);
                    }
                    return false;
                }
                if (mUseDebug) {
                    Slog.d(CUR_TAG, "Blocked: " + processName + " for package " + packageName);
                }
                return true;
            }
        }

        public void setPackageAutoStartAllowed(String packageName) {
            synchronized (mLock) {
                final Boolean previousState = mMainProcState.put(packageName, true);
                if (mUseDebug) {
                    Slog.d(CUR_TAG, "Update | package: " + packageName
                                + " | Set to ALLOW auto-start");
                }
            }
        }

        public void setPackageAutoStartBlocked(String packageName) {
            synchronized (mLock) {
                final Boolean previousState = mMainProcState.put(packageName, false);
                if (mUseDebug) {
                    Slog.d(CUR_TAG, "Update | package: " + packageName
                                + " | Set to BLOCK auto-start");
                }
            }
        }
    }

    public void setPackageAutoStartAllowed(String packageName) {
        mAutoStartManagement.setPackageAutoStartAllowed(packageName);
    }

    public void setPackageAutoStartBlocked(String packageName) {
        mAutoStartManagement.setPackageAutoStartBlocked(packageName);
    }

    public boolean shouldPreventProcessStart(String processName, ApplicationInfo info) {
        if (!mUseRestrictBgAutoStart) {
            return false;
        }

        if (info == null || info.isSystemApp() || info.isUpdatedSystemApp()) {
            return false;
        }

        return mAutoStartManagement.shouldPreventStart(info.packageName, processName);
    }

    public void handleActivityStart(ApplicationInfo info) {
        if (info == null || info.isSystemApp() || info.isUpdatedSystemApp()) {
            return;
        }

        if (mUseDebug) {
            Slog.d(TAG, "--- Handle activity start request ---");
            Slog.d(TAG, "handleActivityStart: Updated current top app to: " + info.packageName);
        }

        activateAppForForeground(info.packageName);
    }

    public void handleSchedGroupTransition(ProcessRecord app) {
        if (app.info.isSystemApp() || app.info.isUpdatedSystemApp()
                || !app.processName.equals(app.info.packageName)) {
            return;
        }

        final int curSchedGroup = app.mState.getCurrentSchedulingGroup();
        final String packageName = app.info.packageName;

        switch (curSchedGroup) {
            case ProcessList.SCHED_GROUP_TOP_APP:
            case ProcessList.SCHED_GROUP_TOP_APP_BOUND:
                if (mUseDebug) {
                    Slog.d(TAG, "--- Handle sched group transition ---");
                    Slog.d(TAG, "App " + packageName + " entered TOP_APP group");
                }
                activateAppForForeground(packageName);
                break;
            default:
                if (mUseDebug) {
                    Slog.d(TAG, "--- Handle sched group transition ---");
                    Slog.d(TAG, "App " + packageName + " entered NON TOP_APP group");
                }
                deactivateAppForBackground(packageName);
                break;
        }
    }

    private void activateAppForForeground(String packageName) {
        if (mUseDebug) {
            Slog.d(TAG, "(activating) app for foreground use: " + packageName);
        }
        if (mUsePackageLevelFreezer) {
            unfreezePackageLevel(packageName);
        }

        if (mUseRestrictBgAutoStart) {
            setPackageAutoStartAllowed(packageName);
        }
    }

    private void deactivateAppForBackground (String packageName) {
        if (mUseDebug) {
            Slog.d(TAG, "(deactivating) app for background use: " + packageName);
        }

        if (mUsePackageLevelFreezer) {
            if (isPackageExemptFromFreeze(packageName)) {
                Slog.d(TAG, "(Disallow freeze) exempt package: " + packageName);
            } else {
                freezePackageLevel(packageName);
            }
        }

        if (mUseRestrictBgAutoStart) {
            if (isPackageExemptFromAutoStart(packageName)) {
                Slog.d(TAG, "(Allow auto-start) exempt package: " + packageName);
            } else {
                setPackageAutoStartBlocked(packageName);
            }
// QTI_END: 2025-12-09: Performance: Introduce restrictions on BG process restart and package-level freezer
        }
    }
}
