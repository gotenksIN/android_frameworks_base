/* Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.server.am;

import static android.os.Process.THREAD_PRIORITY_TOP_APP_BOOST;
import static com.android.server.cpu.CpuAvailabilityMonitoringConfig.CPUSET_ALL;
import static com.android.server.cpu.CpuAvailabilityMonitoringConfig.CPUSET_BACKGROUND;

import com.android.server.am.ProcessRecord;
import com.android.server.am.ProcessList;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.am.psc.Constants;
import com.android.server.ServiceThread;
import com.android.server.LocalServices;
import com.android.server.cpu.CpuMonitorInternal;
import com.android.server.cpu.CpuAvailabilityMonitoringConfig;
import com.android.server.cpu.CpuAvailabilityInfo;

import android.os.Trace;
import android.os.IBinder;
import android.os.Process;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.util.Slog;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.BoostFramework;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.provider.Settings;
import android.database.ContentObserver;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.PrintWriter;
import java.io.FileDescriptor;

public class QtiBackgroundManager {
    private static QtiBackgroundManager mInstance;
    private static String TAG = "QtiBackgroundManager";

    // Log prefixes for better categorization
    private static final String LOG_PREFIX_FREEZE = "[FREEZE]";
    private static final String LOG_PREFIX_UNFREEZE = "[UNFREEZE]";
    private static final String LOG_PREFIX_CPU = "[CPU]";
    private static final String LOG_PREFIX_POLICY = "[POLICY]";
    private static final String LOG_PREFIX_PKG = "[PACKAGE]";
    private static final String LOG_PREFIX_AUTOSTART = "[AUTOSTART]";
    private static final String LOG_PREFIX_KEEPALIVE = "[KEEPALIVE]";
    private static final String LOG_PREFIX_UI_AFFINITY = "[UI AFFINITY]";
    private static final String LOG_PREFIX_PKG_FREEZER = "[PKG FREEZER]";
    private static final String LOG_PREFIX_PROC_FREEZER = "[PROC FREEZER]";
    private static final String LOG_PREFIX_UI_RT = "[UI RT]";
    private static final String LOG_PREFIX_UI = "[UI]";
    private static final String LOG_PREFIX_CONFIG = "[CONFIG]";

    private static final long DEFAULT_LAUNCH_TIMEOUT = 2000;
    private static final long DEFAULT_DELAY_UNFREEZER_TIMEOUT = 1000;
    private static final int DEFAULT_CPU_USAGE_THRESHOLD = 60;
    private static final int DEFAULT_FREEZE_ADJ_THRESHOLD = Constants.FOREGROUND_APP_ADJ + 1;
    private static final int FREEZE_BINDER_TIMEOUT_MS = 10;

    private static final int UNFREEZE_PROCESS_SERVICE_LEVEL = 0;
    private static final int FREEZE_PROCESS_LEVEL = 1;
    private static final int UNFREEZE_PROCESS_LEVEL = 2;
    private static final int FREEZE_PACKAGE_LEVEL = 3;
    private static final int UNFREEZE_PACKAGE_LEVEL = 4;

    private static final int MSG_UI_AFFINITY_ENABLE = 1;
    private static final int MSG_UI_AFFINITY_DISABLE = 2;

    private static final int FREEZE_SUCCESS = 0;
    private static final int PID_NOT_FOUND = -1;
    private static final int BINDER_FREEZE_FAILED = -2;
    private static final int SKIP_FREEZE = -3;
    private static final int FOREGROUND_SERVICE_ACTIVE = -4;

    public static final int FIRST_LAUNCH_FREEZE = 0;
    public static final int WARM_LAUNCH_FREEZE = 1;
    public static final int COLD_LAUNCH_FREEZE = 2;

    public static final int COMPLETE_LAUNCH_UNFREEZE = 0;
    public static final int INTERRUPT_LAUNCH_UNFREEZE = 1;
    public static final int TIMEOUT_LAUNCH_UNFREEZE = 2;
    public static final int REMOVE_PROCESS_UNFREEZE = 3;
    public static final int CROSS_LAUNCH_UNFREEZE = 4;
    public static final int DEPEND_LAUNCH_UNFREEZE = 5;

    private int mFreezeAdjThreshold = DEFAULT_FREEZE_ADJ_THRESHOLD;
    private long mLaunchTimeout = DEFAULT_LAUNCH_TIMEOUT;
    private int mCpuUsageThreshold = DEFAULT_CPU_USAGE_THRESHOLD;
    private long mDelayUnfreezeTimeout = DEFAULT_DELAY_UNFREEZER_TIMEOUT;
    private boolean mCpuLoadMonitorBG = true;
    private boolean mUseDebug = false;
    private boolean mUseAppBgManager = false;
    private boolean mUseCpuLoadMonitor = false;
    private boolean mUseProcessLevelFreezer = false;
    private boolean mUseAggressivePolicy = false;
    private boolean mUsePackageLevelFreezer = false;
    private boolean mUseRestrictBgAutoStart = false;
    private boolean mUseUiFluencyMode = false;
    private boolean mUseAppKeepaliveManager = false;
    private boolean mUseUIRTSettings = false;
    private boolean mUseUIAffinitySettings = false;
    private boolean mUsePerfCoreAffinity = false;

    private Freezer mFreezer;
    private Handler mHandler;
    private CpuLoadMonitor mCpuLoadMonitor;
    private ContentResolver mContentResolver;
    private ProcessLevelFreezer mProcessLevelFreezer;
    private PackageLevelFreezer mPackageFreezerManager;
    private AutoStartManagement mAutoStartManager;
    private AppKeepaliveManagement mAppKeepaliveManager;
    private UiFluencyModeMonitor mUiFluencyModeMonitor;
    private ActivityManagerService mAm;

    private void syncAppFreezerStateWithUiFluencyMode() {
        final String targetState = mUseUiFluencyMode ? "disabled" : "enabled";
        final String currentState = Settings.Global.getString(
                mContentResolver, Settings.Global.CACHED_APPS_FREEZER_ENABLED);

        if (!targetState.equals(currentState)) {
            try {
                Settings.Global.putString(mContentResolver,
                        Settings.Global.CACHED_APPS_FREEZER_ENABLED, targetState);

                Slog.i(TAG, String.format("%s UI Fluency Mode %s - Default app freezer: %s -> %s",
                        LOG_PREFIX_CONFIG,
                        mUseUiFluencyMode ? "ENABLED" : "DISABLED",
                        currentState != null ? currentState : "null",
                        targetState));
            } catch (Exception e) {
                Slog.e(TAG, LOG_PREFIX_CONFIG + " Failed to update default app freezer setting", e);
            }
        }
    }

    public class UiFluencyModeMonitor {
        private static final String SETTINGS_UI_FLUENCY_MODE = "ui_fluency_mode_enabled";

        public UiFluencyModeMonitor() {
            if (mContentResolver == null) {
                return;
            }
            loadUiFluencyModeFlag();

            mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(SETTINGS_UI_FLUENCY_MODE),
                true,
                new UiFluencyModeObserver(mHandler));
        }

        private void loadUiFluencyModeFlag() {
            try {
                boolean oldUiFluencyMode = mUseUiFluencyMode;
                boolean newValue = Settings.Global.getInt(
                            mContentResolver,
                            SETTINGS_UI_FLUENCY_MODE,
                            0) == 1;
                Slog.d(TAG, String.format("%s Loaded UI Fluency Mode: %s",
                        LOG_PREFIX_CONFIG, newValue));
                mUseUiFluencyMode = newValue;

                // handle ui fluency mode disabled
                if (oldUiFluencyMode == true && newValue == false) {
                    handleUIFluencyModeDisabled();
                }
                mAppKeepaliveManager.updateLmkLazyKillFLag(newValue);
                syncAppFreezerStateWithUiFluencyMode();
            } catch (Exception e) {
                // If setting doesn't exist, assume it's disabled for ui fluency mode
                mUseUiFluencyMode = false;
                Slog.d(TAG, String.format("%s Setting not found, using default: %s",
                        LOG_PREFIX_CONFIG, mUseUiFluencyMode));
            }
        }

        private class UiFluencyModeObserver extends ContentObserver {
            public UiFluencyModeObserver(Handler handler) {
                super(handler);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                Slog.d(TAG, LOG_PREFIX_CONFIG + " UI fluency mode setting changed, reloading...");
                loadUiFluencyModeFlag();
            }
        }
    }

    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                    Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                final String packageName = intent.getData() != null ?
                        intent.getData().getSchemeSpecificPart() : null;
                if (packageName == null) {
                    Slog.w(TAG, LOG_PREFIX_PKG +
                            " Received package intent with null package name: " + action);
                    return;
                }

                final boolean isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (isReplacing) {
                    if (mUseDebug) {
                        String updateType = Intent.ACTION_PACKAGE_REMOVED.equals(action)
                                ? "uninstalled" : "installed";
                        Slog.d(TAG, String.format("%s Package %s is being updated, not %s",
                                LOG_PREFIX_PKG, packageName, updateType));
                    }
                    return;
                }

                final int userId =
                    intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);

                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Package uninstalled: %s (user=%d)",
                                LOG_PREFIX_PKG, packageName, userId));
                    }

                    handlePackageUninstalled(packageName, userId);
                } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Package installed: %s (user=%d)",
                                LOG_PREFIX_PKG, packageName, userId));
                    }

                    handlePackageInstalled(packageName, userId);
                }
            }
        }
    };

    private void handlePackageInstalled(String packageName, int userId) {
        if (mUseUiFluencyMode) {
            try {
                ApplicationInfo info = mAm.mContext.getPackageManager().getApplicationInfo(
                        packageName, 0);
                if (info != null && (info.isSystemApp() || info.isUpdatedSystemApp())) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Skip settings for system app: %s",
                                LOG_PREFIX_PKG, packageName));
                    }
                    return;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Slog.w(TAG, String.format("%s Package %s not found while checking if system app",
                        LOG_PREFIX_PKG, packageName));
                return;
            }

            mAutoStartManager.onPackageInstalled(packageName);
            mPackageFreezerManager.onPackageInstalled(packageName);
            mAppKeepaliveManager.onPackageInstalled(packageName);
            if (mUseDebug) {
                Slog.d(TAG, String.format("%s Updated policy settings for installed package: %s",
                        LOG_PREFIX_POLICY, packageName));
            }

            deactivateAppForBackground(packageName);
        }
    }

    private void handlePackageUninstalled(String packageName, int userId) {
        if (mUsePackageLevelFreezer) {
            mPackageFreezerManager.removeAppPids(packageName);
            mPackageFreezerManager.removePendingProcessesByPackage(packageName);
            mPackageFreezerManager.cleanupUninstalledAppResources(packageName);
        }

        if (mUseRestrictBgAutoStart) {
            mAutoStartManager.removePackageAutoStartState(packageName);
            mAutoStartManager.cleanupUninstalledAppResources(packageName);
        }

        if (mUseAppKeepaliveManager) {
            mAppKeepaliveManager.cleanupUninstalledAppResources(packageName);
        }
    }

    public void setAMS(ActivityManagerService am) {
        if (mAm == null) {
            mAm = am;
            mContentResolver = mAm.mContext.getContentResolver();
            mUiFluencyModeMonitor = new UiFluencyModeMonitor();

            IntentFilter packageFilter = new IntentFilter();
            packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            packageFilter.addDataScheme("package");
            mAm.mContext.registerReceiverAsUser(mPackageReceiver,
                    UserHandle.ALL, packageFilter, null, null);
        } else {
            Slog.e(TAG, LOG_PREFIX_CONFIG + " ActivityManagerService is already set");
        }
    }

    public boolean usePackageLevelFreezer() {
        return (mUseUiFluencyMode || mUseAppBgManager) && mUsePackageLevelFreezer;
    }

    public boolean useAppKeepaliveManager() {
        return (mUseUiFluencyMode || mUseAppBgManager) && mUseAppKeepaliveManager;
    }

    public boolean useUIRTSettings() {
        return (mUseUiFluencyMode || mUseAppBgManager) && mUseUIRTSettings;
    }

    public boolean useUIAffinitySettings() {
        return ((mUseUiFluencyMode || mUseAppBgManager) && mUseUIAffinitySettings)
                || mUsePerfCoreAffinity;
    }

    public boolean useProcessLevelFreezer() {
        return (mUseUiFluencyMode || mUseAppBgManager) && mUseProcessLevelFreezer;
    }

    public boolean useUIFluencyMode() {
        return mUseUiFluencyMode;
    }

    private boolean isThirdPartyAppMainProcess(ProcessRecordInternal state) {
        final ProcessRecord app = (ProcessRecord) state;

        if (!app.info.isSystemApp() && !app.info.isUpdatedSystemApp()
                && app.processName.equals(app.info.packageName)) {
            return true;
        }

        return false;
    }

    private boolean shouldApplyUIRTSettings(ProcessRecordInternal state) {
        if (state == null) {
            return false;
        }

        // Don't apply if already using FIFO scheduling
        if (state.useFifoUiScheduling()) {
            return false;
        }
        return useUIRTSettings() && isThirdPartyAppMainProcess(state);
    }

    private boolean shouldApplyUIAffinitySettings(ProcessRecordInternal state) {
        return useUIAffinitySettings() && isThirdPartyAppMainProcess(state);
    }

    private void applyUIAffinitySettings(ProcessRecordInternal app, boolean enable) {
        if (!shouldApplyUIAffinitySettings(app)) {
            return;
        }

        final int pid = app.getPid();
        final int renderTid = app.getRenderThreadTid();
        final String processName = app.processName;
        final String action = enable ? "Apply" : "Reset";

        try {
            // Set affinity for main process
            Process.setPerfCoreAffinity(pid, enable);

            // Set affinity for render thread if exists
            if (renderTid > 0) {
                Process.setPerfCoreAffinity(renderTid, enable);
            }

            if (mUseDebug) {
                Slog.d(TAG, String.format("%s %s UI affinity for %s (PID %d, RenderTID %d)",
                    LOG_PREFIX_UI_AFFINITY, action.toUpperCase(), processName, pid, renderTid));
            }
        } catch (Exception e) {
            Slog.e(TAG, String.format("%s Failed to %s UI affinity for %s (PID %d): %s",
                    LOG_PREFIX_UI_AFFINITY, action.toUpperCase(), processName, pid, e.getMessage()), e);
        }
    }

    private void applyUIAffinitySettingsAsync(ProcessRecordInternal app, boolean enable,
            Handler handler) {
        if (handler != null) {
            handler.sendMessage(handler.obtainMessage(
                    0 /*not used*/, enable ? MSG_UI_AFFINITY_ENABLE : MSG_UI_AFFINITY_DISABLE,
                    0 /*not used*/, app));
        }
    }

    public boolean handleProcessGroupMessage(Message msg) {
        // switch to top sched ground or non-top sched ground
        if (msg.arg1 == MSG_UI_AFFINITY_ENABLE || msg.arg1 == MSG_UI_AFFINITY_DISABLE) {
            final ProcessRecordInternal state = (ProcessRecordInternal) msg.obj;
            boolean enable = msg.arg1 == MSG_UI_AFFINITY_ENABLE;
            applyUIAffinitySettings(state, enable);
            return true;
        }

        return msg.arg1 > 0 || msg.arg2 > 0;
    }

    public void applyUIRtAndAffinitySettings(ProcessRecordInternal proc) {
        // Set CPU affinity for UI/Render threads
        applyUIAffinitySettings(proc, true);
        // Apply RT scheduling settings
        applyUIRTSettings(proc, true);
    }

    private void applyUIRTSettings(ProcessRecordInternal state, boolean apply) {
        if (!shouldApplyUIRTSettings(state)) {
            return;
        }

        if (state == null || mAm == null) {
            return;
        }

        try {
            if (apply) {
                // Apply RT scheduling
                state.setSavedPriority(Process.getThreadPriority(state.getPid()));
                mAm.setFifoPriority(state, true);

                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Applied RT scheduling: %s (pid=%d)",
                            LOG_PREFIX_UI_RT, state.processName, state.getPid()));
                }
            } else {
                // Reset RT scheduling
                mAm.setFifoPriority(state, false);
                Process.setThreadPriority(state.getPid(), state.getSavedPriority());

                if (state.getRenderThreadTid() != 0) {
                    Process.setThreadPriority(state.getRenderThreadTid(),
                        Process.THREAD_PRIORITY_DISPLAY);
                }

                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Reset RT scheduling: %s (pid=%d)",
                            LOG_PREFIX_UI_RT, state.processName, state.getPid()));
                }
            }
        } catch (Exception e) {
            String action = apply ? "apply" : "reset";
            Slog.e(TAG, String.format("%s Failed to %s RT scheduling for %s",
                    LOG_PREFIX_UI_RT, action, state.processName), e);
        }
    }

    private void updateProperties() {
        BoostFramework mPerf = new BoostFramework();

        mUseAppBgManager = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable", "false"));
        mUseRestrictBgAutoStart = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_restrict_auto_start", "true"));
        mUseProcessLevelFreezer = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_process_level_freezer", "false"));
        mUsePackageLevelFreezer = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_package_level_freezer", "true"));
        mUseAppKeepaliveManager = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_app_keepalive_manager", "true"));
        mUseUIRTSettings = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_ui_rt_settings", "true"));
        mUseUIAffinitySettings = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_ui_affinity_settings", "true"));
        mUseAggressivePolicy = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_aggressive_policy", "false"));
        mUseCpuLoadMonitor = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_cpu_load_monitor", "false"));
        mCpuUsageThreshold = Integer.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.cpu_load_monitor_usage_threshold",
                String.valueOf(DEFAULT_CPU_USAGE_THRESHOLD)));
        mCpuLoadMonitorBG = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.cpu_load_monitor_cpuset_bg", "true"));
        mFreezeAdjThreshold = Integer.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.freeze_adj_threshold",
                String.valueOf(DEFAULT_FREEZE_ADJ_THRESHOLD)));
        mLaunchTimeout = Integer.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.launch_timeout_threshold",
                String.valueOf(DEFAULT_LAUNCH_TIMEOUT)));
        mDelayUnfreezeTimeout = Integer.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.delay_unfreeze_threshold",
                String.valueOf(DEFAULT_DELAY_UNFREEZER_TIMEOUT)));
        mUsePerfCoreAffinity =
                Boolean.parseBoolean(mPerf.perfGetProp("ro.vendor.perf.affinity","false"));
        mUseDebug = Boolean.valueOf(mPerf.perfGetProp(
                "ro.vendor.perf.qti_bg_manager.enable_debug", "true"));
    }

    public class CpuLoadMonitor {
        private Object mLock = new Object();
        private boolean mCpuHighLoadFlag = false;
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
                    Slog.d(TAG, String.format("%s CPU usage: %d%% -> %s",
                            LOG_PREFIX_CPU,
                            100 - currentCpuAvalabilityPercent,
                            isHighLoad ? "HIGH LOAD" : "LOW LOAD"));
                }
                setCpuHighLoadFlagLocked(isHighLoad);
            }

            @Override
            public void onMonitoringIntervalChanged(long intervalMilliseconds){
                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Monitor interval changed: %dms",
                            LOG_PREFIX_CPU, intervalMilliseconds));
                }
            }
        }

        public void setCpuUsageThreshold(int cpuUsageThreshold) {
            int cpuAvalabilityPercentThreshold = 100 - cpuUsageThreshold;
            if (cpuAvalabilityPercentThreshold >= 0 && cpuAvalabilityPercentThreshold <= 100) {
                mCpuAvalabilityPercentThreshold = cpuAvalabilityPercentThreshold;
            } else {
                Slog.d(TAG, String.format("%s Invalid CPU usage threshold: %d, using default: %d",
                        LOG_PREFIX_CPU, cpuUsageThreshold, DEFAULT_CPU_USAGE_THRESHOLD));
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
                Slog.d(TAG, LOG_PREFIX_CPU + " Monitoring BACKGROUND CPU load");
            } else {
                mCpuSet = CPUSET_ALL;
                Slog.d(TAG, LOG_PREFIX_CPU + " Monitoring ALL CPU load");
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
                Slog.d(TAG, LOG_PREFIX_CPU + " CPU monitor service initialized and callback added");
            }
        }

        private void setCpuHighLoadFlagLocked(boolean isHighLoad) {
            synchronized (mLock) {
                mCpuHighLoadFlag = isHighLoad;
            }
        }

        private boolean getCpuHighLoadFlagLocked() {
            synchronized (mLock) {
                return mCpuHighLoadFlag;
            }
        }
    }

    public static QtiBackgroundManager getInstance() {
        if (mInstance == null) {
            synchronized (QtiBackgroundManager.class) {
                if (mInstance == null) {
                    mInstance = new QtiBackgroundManager();
                }
            }
        }
        return mInstance;
    }

    private static ServiceThread createAndStartFreezeThread() {
        final ServiceThread freezerManagerThread = new ServiceThread(
                "QtiFreezerThread", THREAD_PRIORITY_TOP_APP_BOOST, true /* allowIo */);
        freezerManagerThread.start();
        return freezerManagerThread;
    }

    private QtiBackgroundManager() {
        updateProperties();
        if (mUseCpuLoadMonitor) {
            mCpuLoadMonitor.setCpuUsageThreshold(mCpuUsageThreshold);
            mCpuLoadMonitor.setCpuSet(mCpuLoadMonitorBG);
        }

        mFreezer = new Freezer();
        mCpuLoadMonitor = new CpuLoadMonitor();
        mProcessLevelFreezer = new ProcessLevelFreezer();
        mPackageFreezerManager = new PackageLevelFreezer();
        mAutoStartManager = new AutoStartManagement();
        mAppKeepaliveManager = new AppKeepaliveManagement();

        mHandler = new Handler(createAndStartFreezeThread().getLooper(), msg -> {
            switch (msg.what) {
                case UNFREEZE_PROCESS_SERVICE_LEVEL: {
                    final int unfreezeReason = msg.arg1;
                    final ProcessRecord app = (ProcessRecord)msg.obj;
                    if (!mProcessLevelFreezer.isProcessFrozen(app)) {
                        Slog.d(TAG, String.format(
                                "%s Skip unfreeze service: %s (already removed)",
                                LOG_PREFIX_PROC_FREEZER, app.processName));
                        break;
                    }
                    if (mUseDebug) {
                        String unfreezeReasonStr =
                                mProcessLevelFreezer.getUnfreezeReason(unfreezeReason);
                        Slog.d(TAG, String.format("%s ═══ Start unfreeze service ═══\n" +
                                "    Process: %s\n" +
                                "    Reason: %s",
                                LOG_PREFIX_PROC_FREEZER, app.processName, unfreezeReasonStr));
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start unfreeze service: " + app.processName +
                                ", reason: " + unfreezeReasonStr);
                    }

                    unFreezeProcess(app);
                    mProcessLevelFreezer.removeProcessFromAllLists(app);

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case FREEZE_PROCESS_LEVEL: {
                    final int freezeReason = msg.arg1;
                    final String packageName = (String)msg.obj;
                    if (mUseDebug) {
                        String freezeReasonStr = mProcessLevelFreezer.getFreezeReason(freezeReason);
                        Slog.d(TAG, String.format(
                                "%s Start freeze processes \n" +
                                "    Package: %s\n" +
                                "    Adj threshold: >= %d\n" +
                                "    Reason: %s",
                                LOG_PREFIX_PROC_FREEZER, packageName,
                                mFreezeAdjThreshold, freezeReasonStr));
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start freeze processes which adj >= " + mFreezeAdjThreshold +
                                " for " + packageName + ", reason: " + freezeReasonStr);
                    }

                    final SparseArray<ProcessRecord> needFreezeProcesses =
                            mProcessLevelFreezer.getFrozenProcessesForPackage(packageName);
                    if (needFreezeProcesses != null) {
                        List<ProcessRecord> pidsToRemove = new ArrayList<>();
                        for (int i = 0; i < needFreezeProcesses.size(); i++) {
                            int pid = needFreezeProcesses.keyAt(i);
                            ProcessRecord app = needFreezeProcesses.valueAt(i);
                            if (freezeProcess(app) == FREEZE_SUCCESS) {
                                pidsToRemove.add(app);
                            }
                        }
                        mProcessLevelFreezer.removeProcessesFromList(packageName, pidsToRemove);
                        if (mUseDebug) {
                            Slog.d(TAG, String.format(
                                    "%s Frozen %d processes for %s",
                                    LOG_PREFIX_PROC_FREEZER,
                                    needFreezeProcesses.size(), packageName));
                        }
                    } else {
                        Slog.d(TAG, String.format("%s Freeze object is null for %s",
                                LOG_PREFIX_PROC_FREEZER, packageName));
                    }

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case UNFREEZE_PROCESS_LEVEL: {
                    final int unfreezeReason = msg.arg1;
                    final String packageName = (String)msg.obj;
                    if (!mProcessLevelFreezer.isFreezeActiveForPackage(packageName)) {
                        Slog.e(TAG, String.format("%s Already triggered unfreeze for %s",
                                LOG_PREFIX_PROC_FREEZER, packageName));
                        break;
                    }

                    if (mUseDebug) {
                        String unfreezeReasonStr =
                                mProcessLevelFreezer.getUnfreezeReason(unfreezeReason);
                        Slog.d(TAG, String.format("%s ═══ Start unfreeze processes ═══\n" +
                                "    Package: %s\n" +
                                "    Reason: %s",
                                LOG_PREFIX_PROC_FREEZER, packageName, unfreezeReasonStr));
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                                "start unfreeze processes for " + packageName +
                                ", reason: " + unfreezeReasonStr);
                    }

                    final SparseArray<ProcessRecord> needUnfreezeProcesses =
                            mProcessLevelFreezer.getFrozenProcessesForPackage(packageName);
                    if (needUnfreezeProcesses != null) {
                        for (int i = 0; i < needUnfreezeProcesses.size(); i++) {
                            int pid = needUnfreezeProcesses.keyAt(i);
                            ProcessRecord app = needUnfreezeProcesses.valueAt(i);
                            unFreezeProcess(app);
                        }
                        if (mUseDebug) {
                            Slog.d(TAG, String.format(
                                    "%s    ═ Unfrozen %d processes for %s",
                                    LOG_PREFIX_PROC_FREEZER,
                                    needUnfreezeProcesses.size(), packageName));
                        }
                        mProcessLevelFreezer.removeFrozenProcesses(packageName);
                        mProcessLevelFreezer.removeFreezeReasonForPackage(packageName);
                    } else {
                        Slog.d(TAG, String.format("%s Unfreeze object is null for %s",
                                LOG_PREFIX_PROC_FREEZER, packageName));
                    }

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                case FREEZE_PACKAGE_LEVEL: {
                    // freeze the processes in pending list firstly.
                    final List<ProcessRecord> pList = mPackageFreezerManager.getPendingList();
                    for (int i=0; i<pList.size(); i++) {
                        ProcessRecord pr = pList.get(i);
                        if (pr == null || pr.getPid() <= 0) {
                            mPackageFreezerManager.removePendingProcess(pr);
                            continue;
                        }

                        int rc = freezeProcess(pr);
                        switch (rc) {
                            case FREEZE_SUCCESS:
                                if (mUseDebug) {
                                    Slog.d(TAG, String.format(
                                            "%s Freeze succeeded: %s (from pending)",
                                            LOG_PREFIX_PKG_FREEZER, pr.processName));
                                }
                                mPackageFreezerManager.removePendingProcess(pr);
                                mPackageFreezerManager.appendAppPids(pr.info.packageName, pr);
                                break;
                            case BINDER_FREEZE_FAILED:
                            case FOREGROUND_SERVICE_ACTIVE:
                                if (mPackageFreezerManager.isFreezeRetryLimitReached(pr)) {
                                    Slog.w(TAG, String.format(
                                            "%s Give up freezing %s after %d retries",
                                            LOG_PREFIX_PKG_FREEZER, pr.processName,
                                            PackageLevelFreezer.MAX_FREEZE_RETRIES));
                                    mPackageFreezerManager.removePendingProcess(pr);
                                } else {
                                    mPackageFreezerManager.incrementFreezeAttempt(pr);
                                    if (mUseDebug) {
                                        Slog.d(TAG, String.format(
                                                "%s Binder freeze failed: %s, retry (%d)",
                                                LOG_PREFIX_PKG_FREEZER, pr.processName,
                                                mPackageFreezerManager.getFreezeRetryCount(pr)));
                                    }
                                }
                                break;
                            default:
                                if (mUseDebug) {
                                    Slog.d(TAG, String.format(
                                            "%s Freeze failed: %s (removing from pending)",
                                            LOG_PREFIX_PKG_FREEZER, pr.processName));
                                }
                                mPackageFreezerManager.removePendingProcess(pr);
                                break;
                        }
                    }

                    String packageName = (String)msg.obj;
                    final SparseArray<ProcessRecord> pids =
                            mPackageFreezerManager.findRelatedPids(packageName);

                    if (pids == null || pids.size() == 0) {
                        break;
                    }
                    if (mUseDebug) {
                        String trace = "Start freeze \"" + packageName + "\" application. ";
                        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, trace);
                        Slog.d(TAG, LOG_PREFIX_PKG_FREEZER + " " + trace);
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
                                break;
                            case BINDER_FREEZE_FAILED:
                                Slog.w(TAG, String.format(
                                        "%s Binder freeze failed, adding to pending: %s",
                                        LOG_PREFIX_PKG_FREEZER, pr.processName));
                                mPackageFreezerManager.appendPendingList(pr,
                                            "Binder Transaction Pending");
                                toRemove.add(pid);
                                break;
                            case FOREGROUND_SERVICE_ACTIVE:
                                Slog.w(TAG, String.format(
                                        "%s Foreground service active, adding to pending: %s",
                                        LOG_PREFIX_PKG_FREEZER, pr.processName));
                                mPackageFreezerManager.appendPendingList(pr,
                                            "Foreground Service Active");
                                toRemove.add(pid);
                                break;
                            default:
                                Slog.e(TAG, String.format("%s Freeze failed: %s",
                                        LOG_PREFIX_PKG_FREEZER, pr.processName));
                                toRemove.add(pid);
                                break;
                        }
                    }

                    for (int i = toRemove.size() - 1; i >= 0; i--) {
                        pids.remove(toRemove.get(i));
                    }

                    if (pids.size() > 0) {
                        mPackageFreezerManager.addAppPids(packageName, pids);
                    }

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
                        Slog.d(TAG, LOG_PREFIX_PKG_FREEZER + " " + trace);
                    }

                    for (int i = 0; i < pids.size(); i++) {
                        ProcessRecord pr = pids.valueAt(i);
                        unFreezeProcess(pr);
                    }

                    mPackageFreezerManager.removeAppPids(packageName);

                    if (mUseDebug) {
                        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    }
                } break;
                default:
                    return true;
            }
            return true;
        });
    }

    public class ProcessLevelFreezer {
        private final Object mLock = new Object();
        // key: application package name
        // value: list of processes to freeze
        private final ArrayMap<String, SparseArray<ProcessRecord>> mProcessesFrozenByPackage =
                new ArrayMap<>();
        private final SparseArray<Integer> mGlobalFrozenPids = new SparseArray<>();
        private final ArrayMap<String, Integer> mFreezeReasons = new ArrayMap<>();

        private final String[] mFreezeReasonStrings = {
            "First launch",
            "Warm launch",
            "Cold launch"
        };

        private final String[] mUnfreezeReasonStrings = {
            "Complete launch",
            "Interrupt launch",
            "Launch timeout",
            "Remove main process",
            "Cross launch process",
            "Dependent launch"
        };

        public String getFreezeReason(int freezeReason) {
            if (freezeReason >= 0 && freezeReason < mFreezeReasonStrings.length) {
                return mFreezeReasonStrings[freezeReason];
            }
            return "Unknown";
        }

        public String getUnfreezeReason(int unfreezeReason) {
            if (unfreezeReason >= 0 && unfreezeReason < mUnfreezeReasonStrings.length) {
                return mUnfreezeReasonStrings[unfreezeReason];
            }
            return "Unknown";
        }

        private SparseArray<ProcessRecord> findBackgroundProcessesToFreeze(
                    String initiatorPackage) {
            SparseArray<ProcessRecord> processesToFreeze = new SparseArray<>();
            synchronized (mAm.mPidsSelfLocked) {
                for (int i = 0; i < mAm.mPidsSelfLocked.size(); i++) {
                    final ProcessRecord app = mAm.mPidsSelfLocked.valueAt(i);
                    final ProcessRecordInternal state = app;
                    if (state.getCurAdj() >= Constants.FOREGROUND_APP_ADJ) {
                        String appPackageName = app.info.packageName;
                        if (app.info.isSystemApp() || initiatorPackage.equals(appPackageName)) {
                            continue;
                        }
                        processesToFreeze.put(app.getPid(), app);
                    }
                }
                return processesToFreeze;
            }
        }

        private boolean isProcessFrozen(ProcessRecord app) {
            int pid = app.getPid();
            synchronized (mLock) {
                return mGlobalFrozenPids.indexOfKey(pid) >= 0;
            }
        }

        private void removeProcessFromAllLists(ProcessRecord app) {
            int pid = app.getPid();
            synchronized (mLock) {
                boolean removed = false;
                for (int i = 0; i < mProcessesFrozenByPackage.size(); i++) {
                    SparseArray<ProcessRecord> freezeList = mProcessesFrozenByPackage.valueAt(i);
                    if (freezeList.get(pid) != null) {
                        freezeList.remove(pid);
                        removed = true;
                    }
                }
                if (removed) {
                    mGlobalFrozenPids.remove(pid);
                }
            }
        }

        private void removeProcessesFromList(String initiatorPackage,
                List<ProcessRecord> pidsToRemove) {
            synchronized (mLock) {
                SparseArray<ProcessRecord> freezeList =
                        mProcessesFrozenByPackage.get(initiatorPackage);
                if (freezeList != null) {
                    for (ProcessRecord process : pidsToRemove) {
                        int pid = process.getPid();
                        freezeList.remove(pid);
                        int count = mGlobalFrozenPids.get(pid, 0) - 1;
                        if (count <= 0) {
                            mGlobalFrozenPids.remove(pid);
                        } else {
                            mGlobalFrozenPids.put(pid, count);
                        }
                    }
                }
            }
        }

        private boolean isFreezeActiveForPackage(String initiatorPackage) {
            synchronized (mLock) {
                return mProcessesFrozenByPackage.containsKey(initiatorPackage);
            }
        }

        private SparseArray<ProcessRecord> getFrozenProcessesForPackage(String initiatorPackage) {
            synchronized (mLock) {
                return mProcessesFrozenByPackage.get(initiatorPackage);
            }
        }

        private void recordFrozenProcesses(String initiatorPackage,
                SparseArray<ProcessRecord> pidList) {
            synchronized (mLock) {
                mProcessesFrozenByPackage.put(initiatorPackage, pidList);
                for (int i = 0; i < pidList.size(); i++) {
                    int pid = pidList.keyAt(i);
                    mGlobalFrozenPids.put(pid, mGlobalFrozenPids.get(pid, 0) + 1);
                }
            }
        }

        private void removeFrozenProcesses(String initiatorPackage) {
            synchronized (mLock) {
                SparseArray<ProcessRecord> freezeList =
                        mProcessesFrozenByPackage.get(initiatorPackage);
                if (freezeList != null) {
                    for (int i = 0; i < freezeList.size(); i++) {
                        int pid = freezeList.keyAt(i);
                        int count = mGlobalFrozenPids.get(pid, 0) - 1;
                        if (count <= 0) {
                            mGlobalFrozenPids.remove(pid);
                        } else {
                            mGlobalFrozenPids.put(pid, count);
                        }
                    }
                    freezeList.clear();
                }
                mProcessesFrozenByPackage.remove(initiatorPackage);
            }
        }

        private ArrayList<String> getPackagesWithFrozenProcesses() {
            synchronized (mLock) {
                return new ArrayList<>(mProcessesFrozenByPackage.keySet());
            }
        }

        private int getFreezeReasonForPackage(String initiatorPackage) {
            synchronized (mLock) {
                return mFreezeReasons.getOrDefault(initiatorPackage, -1);
            }
        }

        private void setFreezeReasonForPackage(String initiatorPackage, int freezeReason) {
            synchronized (mLock) {
                mFreezeReasons.put(initiatorPackage, freezeReason);
            }
        }

        private void removeFreezeReasonForPackage(String initiatorPackage) {
            synchronized (mLock) {
                mFreezeReasons.remove(initiatorPackage);
            }
        }

        void freezeProcessLevelInternal(String packageName, int freezeReason) {
            if (!shouldFreezePackage(packageName)) {
                return;
            }

            if (isFreezeActiveForPackage(packageName)) {
                // make sure that already triggered freeze.
                Slog.d(TAG, String.format("%s Already triggered freeze for %s",
                        LOG_PREFIX_PROC_FREEZER, packageName));
                return;
            }

            if (mUseCpuLoadMonitor && !mCpuLoadMonitor.getCpuHighLoadFlagLocked()) {
                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Skip freeze: CPU load is low when launching %s",
                            LOG_PREFIX_PROC_FREEZER, packageName));
                }
                return;
            }
            // Avoid cross launch
            unfreezeProcessLevelAll();
            SparseArray<ProcessRecord> needFreezeProcesses =
                    findBackgroundProcessesToFreeze(packageName);
            if (needFreezeProcesses.size() == 0) {
                if (mUseDebug) {
                    Slog.d(TAG, String.format(
                            "%s Skip freeze: No proper processes to freeze for %s",
                            LOG_PREFIX_PROC_FREEZER, packageName));
                }
                return;
            }
            setFreezeReasonForPackage(packageName, freezeReason);
            recordFrozenProcesses(packageName, needFreezeProcesses);
            mHandler.sendMessage(mHandler.obtainMessage(
                    FREEZE_PROCESS_LEVEL, freezeReason, 0 /* unused */, packageName));
            startTimeoutUnfreeze(packageName);
        }

        private void startTimeoutUnfreeze(String packageName){
            // add a timeout unfreeze mechanism
            mHandler.sendMessageDelayed(mHandler.obtainMessage(
                    UNFREEZE_PROCESS_LEVEL, TIMEOUT_LAUNCH_UNFREEZE, 0 /* unused */, packageName),
                    mLaunchTimeout);
        }

        private void removeTimeoutUnfreeze(String packageName){
            // remove timeout unfreeze mechanism
            mHandler.removeMessages(UNFREEZE_PROCESS_LEVEL, packageName);
        }

        private void unfreezeProcessLevelAll() {
            ArrayList<String> packageNameList = getPackagesWithFrozenProcesses();
            for (String packageName : packageNameList) {
                unfreezeProcessLevelInternal(packageName, CROSS_LAUNCH_UNFREEZE);
            }
        }

        void unfreezeProcessLevelInternal(String packageName, int unfreezeReason) {
            if (!isFreezeActiveForPackage(packageName)) {
                return;
            }

            removeTimeoutUnfreeze(packageName);

            long delayMs = 0;
            if (unfreezeReason == COMPLETE_LAUNCH_UNFREEZE) {
                int freezeReason = getFreezeReasonForPackage(packageName);
                if (freezeReason != WARM_LAUNCH_FREEZE) {
                    delayMs = mDelayUnfreezeTimeout;
                }
            }

            if (delayMs > 0) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(
                        UNFREEZE_PROCESS_LEVEL, unfreezeReason, 0 /* unused */, packageName),
                        delayMs);
            } else {
                mHandler.sendMessage(mHandler.obtainMessage(
                        UNFREEZE_PROCESS_LEVEL, unfreezeReason, 0 /* unused */, packageName));
            }
        }

        private boolean shouldFreezePackage(String packageName) {
            if (packageName.contains(":")) {
                return false;
            }

            try {
                ApplicationInfo info = mAm.mContext.getPackageManager().getApplicationInfo(
                        packageName, 0);
                if (info != null && (info.isSystemApp() || info.isUpdatedSystemApp())) {
                    return false;
                }
            } catch (PackageManager.NameNotFoundException e) {
                if (mUseDebug) {
                    Slog.w(TAG, String.format(
                            "%s Package %s not found while checking if system app",
                            LOG_PREFIX_PROC_FREEZER, packageName));
                }
            }
            return true;
        }
    }

    private boolean isUsingForegroundService(ProcessRecord app) {
        final ProcessRecordInternal state = app;
        final int curSchedGroup = state.getCurrentSchedulingGroup();

        if (curSchedGroup == Constants.SCHED_GROUP_BACKGROUND) {
            if (mUseDebug) {
                Slog.d(TAG, String.format("%s No foreground service found for %s",
                        LOG_PREFIX_POLICY, app.processName));
            }
            return false;
        }

        return true;
    }

    private String getUnfreezeLogInfo(ProcessRecord app) {
        return String.format("Process: %s, UID: %d, PID: %d, Adj: %d, Frozen: %b",
                app.processName, app.uid, app.getPid(),
                ((ProcessRecordInternal)app).getCurAdj(), app.mOptRecord.isFrozen());
    }

    private String getFreezeLogInfo(ProcessRecord app) {
        return String.format(
                "Process: %s, UID: %d, PID: %d, Adj: %d, Frozen: %b, Services: %d",
                app.processName, app.uid, app.getPid(),
                ((ProcessRecordInternal)app).getCurAdj(), app.mOptRecord.isFrozen(),
                app.mServices.numberOfRunningServices());
    }

    private void unFreezeProcess(ProcessRecord app) {
        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        int pid = app.getPid();
        int uid = app.uid;
        String logInfo = getUnfreezeLogInfo(app);
        if (mUseDebug) {
            Slog.d(TAG, String.format("%s %s", LOG_PREFIX_UNFREEZE, logInfo));
        }
        // skip default frozen process and killed process (pid==0)
        if (opt.isFrozen() || pid == 0) {
            if (mUseDebug) {
                String reason = opt.isFrozen() ? "Default freezer active" : "Process died (PID=0)";
                Slog.d(TAG, String.format("  [SKIP] %s", reason));
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "SkipUnfreeze: " + reason);
            }
            return;
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "Unfreeze: " + logInfo);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "UnfreezingBinder: " + logInfo);
        }

        try {
            int rc = mFreezer.freezeBinder(pid, false, 2 /* timeout_ms */);
            if (rc != 0) {
                Slog.w(TAG, String.format("  [WARN] Binder unfreeze failed (rc=%d, PID %d)", rc, pid));
            } else if (mUseDebug) {
                Slog.d(TAG, String.format("  [OK] Binder unfrozen (PID %d)", pid));
            }
        } catch (RuntimeException e) {
            Slog.e(TAG, String.format("  [ERROR] Binder unfreeze exception (PID %d): %s",
                        pid, e.getMessage()));
            if (mUseDebug) {
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "BinderUnfreezeFailed: " + logInfo);
            }
        } finally {
            if (mUseDebug) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "UnfreezeProcess: " + logInfo);
        }

        try {
            mFreezer.setProcessFrozen(pid, uid, false);
            if (mUseDebug) {
                Slog.d(TAG, String.format("  [OK] Process unfrozen (PID %d)", pid));
            }
        } catch (Exception e) {
            Slog.e(TAG, String.format("  [ERROR] Process unfreeze failed (PID %d): %s",
                pid, e.getMessage()), e);
            if (mUseDebug) {
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ProcessUnfreezeFailed: " + logInfo);
            }
        } finally {
            if (mUseDebug) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of app info
        }
    }

    private int freezeProcess(ProcessRecord app) {
        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        final ProcessRecordInternal state = app;
        final int curAdj = state.getCurAdj();
        int pid = app.getPid();
        int uid = app.uid;
        String processName = app.processName;
        String logInfo = getFreezeLogInfo(app);

        if (mUseDebug) {
            Slog.d(TAG, String.format("%s %s", LOG_PREFIX_FREEZE, logInfo));
        }
        // skip freeze process that is frozen by system freezer
        if (opt.isFrozen() || pid == 0) {
            if (mUseDebug) {
                String reason = opt.isFrozen() ? "Default freezer active" : "Process died (PID=0)";
                Slog.d(TAG, String.format("  [SKIP] %s", reason));
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "SkipFreeze: " + reason);
            }
            return pid == 0 ? PID_NOT_FOUND : SKIP_FREEZE;
        }

        if (state.getCurAdj() < mFreezeAdjThreshold) {
            if (mUseDebug) {
                Slog.d(TAG, String.format("  [SKIP] Adj below threshold (%d < %d)",
                    curAdj, mFreezeAdjThreshold));
            }
            return SKIP_FREEZE;
        }

        final boolean isHighPriorityApp = (state.getCurAdj() >= Constants.FOREGROUND_APP_ADJ
                                   && state.getCurAdj() <= Constants.PERCEPTIBLE_APP_ADJ);

        if (isHighPriorityApp) {
            if (mUseAggressivePolicy) {
                if (mUseDebug) {
                    Slog.d(TAG, String.format(
                        "  [INFO] Skipping FG service check (aggressive mode, Adj %d)", curAdj));
                }
            } else {
                boolean isUsingFgService = isUsingForegroundService(app);
                if (isUsingFgService) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("  [SKIP] Foreground service active (Adj %d)", curAdj));
                        Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                            String.format("SkipFreeze|FGService:%s|Adj:%d", processName, curAdj));
                    }
                    return FOREGROUND_SERVICE_ACTIVE;
                }
            }
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, logInfo);
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "FreezingBinder: " + logInfo);
        }

        boolean freezeBinderSuccess = false;
        try {
            int rc = mFreezer.freezeBinder(pid, true, FREEZE_BINDER_TIMEOUT_MS);
            if (rc != 0){
                Slog.w(TAG,
                    String.format("  [WARN] Binder freeze failed (rc=%d, PID %d)", rc, pid));
            } else {
                freezeBinderSuccess = true;
                if (mUseDebug) {
                    Slog.d(TAG, String.format("  [OK] Binder frozen (PID %d)", pid));
                }
            }
        } catch (RuntimeException e) {
            Slog.e(TAG, String.format("  [ERROR] Binder freeze exception (PID %d): %s",
                    pid, e.getMessage()), e);
            if (mUseDebug) {
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "BinderFreezeFailed: " + logInfo);
            }
        } finally {
            if (mUseDebug) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }

        if (mUseDebug) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "FreezingProcess: " + logInfo);
        }

        try {
            if (freezeBinderSuccess) {
                mFreezer.setProcessFrozen(pid, uid, true);
                if (mUseDebug) {
                    Slog.d(TAG, String.format("  [OK] Process frozen (PID %d)", pid));
                }
            } else if (mUseDebug) {
                Slog.d(TAG, "  [SKIP] Process freeze skipped (binder freeze failed)");
            }
        } catch (RuntimeException e) {
            Slog.e(TAG, String.format("  [ERROR] Process freeze failed (PID %d): %s",
                    pid, e.getMessage()), e);
            if (mUseDebug) {
                Trace.instant(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ProcessFreezeFailed: " + logInfo);
            }
        } finally {
            if (mUseDebug) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }

        if (mUseDebug) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER); // end of app info
        }

        if (!freezeBinderSuccess) {
            return BINDER_FREEZE_FAILED;
        }
        return FREEZE_SUCCESS;
    }

    public void freezeProcessLevel(String packageName, int freezeReason) {
        if (!useProcessLevelFreezer()) {
            return;
        }
        if (mUseCpuLoadMonitor) {
            mCpuLoadMonitor.startCpuLoadMonitorOnce();
        }
        mProcessLevelFreezer.freezeProcessLevelInternal(packageName, freezeReason);
    }

    // unfreeze process that the application depends on when it launchs.
    public void startUnfreezeService(ProcessRecord app, int unfreezeReason) {
        if (!useProcessLevelFreezer()) {
            return;
        }
        mHandler.sendMessage(mHandler.obtainMessage(
                UNFREEZE_PROCESS_SERVICE_LEVEL, unfreezeReason, 0 /* unused */, app));
    }

    public void unfreezeProcessLevel(String packageName, int unfreezeReason) {
        if (!useProcessLevelFreezer()) {
            return;
        }
        mProcessLevelFreezer.unfreezeProcessLevelInternal(packageName, unfreezeReason);
    }

    public boolean useAppBgManager() {
        return mUseAppBgManager;
    }

    public class PackageLevelFreezer {
        private class PendingInfo {
            String mReason;
            int mRetryCount;

            PendingInfo(String reason) {
                mReason = reason;
                mRetryCount = 1;
            }
        }

        private static final String SETTINGS_FREEZE_PREFIX = "freeze_policy_";
        private final Map<String, SparseArray<ProcessRecord>> mAppPids = new HashMap<>();
        private final Map<ProcessRecord, PendingInfo> mPendingFreezeMap = new ArrayMap<>();
        private final Object mPendingFreezeLock = new Object();
        private final Object mAppPidsLock = new Object();
        public static final int MAX_FREEZE_RETRIES = 2;
        private static final int FREEZE_POLICY_ENABLED = 1;
        private static final int FREEZE_POLICY_DISABLED = 0;

        private boolean isPackageExempt(String packageName) {
            if (mContentResolver == null) {
                return true;
            }

            try {
                if (Settings.Global.getInt(mContentResolver,
                            SETTINGS_FREEZE_PREFIX + packageName) == FREEZE_POLICY_DISABLED) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Package %s is exempt from freeze (settings)",
                                LOG_PREFIX_PKG_FREEZER, packageName));
                    }
                    return true;
                } else {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format(
                                "%s Package %s is NOT exempt from freeze (settings)",
                                LOG_PREFIX_PKG_FREEZER, packageName));
                    }
                    return false;
                }
            } catch (Settings.SettingNotFoundException e) {
                // If setting doesn't exist, assume it's disabled for freeze
                return true;
            }
        }

        public void onPackageInstalled(String packageName) {
            try {
                Settings.Global.putInt(mContentResolver,
                        SETTINGS_FREEZE_PREFIX + packageName, FREEZE_POLICY_ENABLED);
            } catch (Exception e) {
                Slog.e(TAG, String.format(
                        "%s Failed to update freeze policy for installed package: %s",
                        LOG_PREFIX_PKG_FREEZER, packageName), e);
            }
        }

        public void cleanupUninstalledAppResources(String packageName) {
            try {
                mContentResolver.delete(
                    Settings.Global.CONTENT_URI,
                    Settings.NameValueTable.NAME + " = ?",
                    new String[]{SETTINGS_FREEZE_PREFIX + packageName}
                );
            } catch (Exception e) {
                Slog.e(TAG, String.format("%s Failed to clear freezer policy settings for %s",
                        LOG_PREFIX_PKG_FREEZER, packageName), e);
            }
        }

        public void appendPendingList(ProcessRecord app, String reason) {
            if (app == null) {
                return;
            }

            synchronized (mPendingFreezeLock) {
                PendingInfo info = mPendingFreezeMap.get(app);
                if (info == null) {
                    mPendingFreezeMap.put(app, new PendingInfo(reason));
                } else {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Process %s already pending\n" +
                                "    Old reason: %s\n    New reason: %s",
                                LOG_PREFIX_PKG_FREEZER, app.processName, info.mReason, reason));
                    }
                    info.mReason = reason;
                }
            }
        }

        public List<ProcessRecord> getPendingList() {
            synchronized (mPendingFreezeLock) {
                return new ArrayList<>(mPendingFreezeMap.keySet());
            }
        }

        public String getPendingFreezeReason(ProcessRecord app) {
            synchronized (mPendingFreezeLock) {
                PendingInfo info = mPendingFreezeMap.get(app);
                return (info != null) ? info.mReason : "unknown";
            }
        }

        public void incrementFreezeAttempt(ProcessRecord app) {
            synchronized (mPendingFreezeLock) {
                PendingInfo info = mPendingFreezeMap.get(app);
                if (info != null) {
                    info.mRetryCount++;
                }
            }
        }

        public int getFreezeRetryCount(ProcessRecord app) {
            synchronized (mPendingFreezeLock) {
                PendingInfo info = mPendingFreezeMap.get(app);
                return (info != null) ? info.mRetryCount : 0;
            }
        }

        public void removePendingProcessesByPackage(String packageName) {
            if (packageName == null) {
                return;
            }

            synchronized (mPendingFreezeLock) {
                Iterator<Map.Entry<ProcessRecord, PendingInfo>> iterator =
                        mPendingFreezeMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<ProcessRecord, PendingInfo> entry = iterator.next();
                    ProcessRecord app = entry.getKey();

                    if (app != null && app.info != null
                            && packageName.equals(app.info.packageName)) {
                        if (mUseDebug) {
                            PendingInfo info = entry.getValue();
                            Slog.d(TAG, String.format(
                                    "%s Removing process %s from pending\n" +
                                    "    Reason: %s, Retries: %d",
                                    LOG_PREFIX_PKG_FREEZER, app.processName,
                                    info.mReason, info.mRetryCount));
                        }
                        iterator.remove();
                    }
                }
            }
        }

        public boolean isFreezeRetryLimitReached(ProcessRecord app) {
            synchronized (mPendingFreezeLock) {
                PendingInfo info = mPendingFreezeMap.get(app);
                if (info == null) {
                    return false;
                }
                return info.mRetryCount > MAX_FREEZE_RETRIES;
            }
        }

        public boolean removePendingProcess(ProcessRecord app) {
            if (app == null) {
                return false;
            }

            synchronized (mPendingFreezeLock) {
                return mPendingFreezeMap.remove(app) != null;
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

        private SparseArray<ProcessRecord> findPidsByPackageName(String packageName) {
            SparseArray<ProcessRecord> pids = new SparseArray<>();
            synchronized (mAm.mPidsSelfLocked) {
                for (int i = 0; i < mAm.mPidsSelfLocked.size(); i++) {
                    final ProcessRecord app = mAm.mPidsSelfLocked.valueAt(i);
                    if (app.info.packageName.equals(packageName)) {
                        pids.put(app.getPid(), app);
                    }
                }
            }
            return pids;
        }

        public SparseArray<ProcessRecord> findRelatedPids(String packageName) {
            return findPidsByPackageName(packageName);
        }

        public void freezePackageLevel(String packageName) {
            if (containsApp(packageName)) {
                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Skip freeze request: %s (already frozen)",
                            LOG_PREFIX_PKG_FREEZER, packageName));
                }
                return;
            }

            mHandler.sendMessage(mHandler.obtainMessage(
                FREEZE_PACKAGE_LEVEL, 0, 0 /* unused */, packageName));
            if (mUseDebug) {
                Slog.i(TAG, String.format("%s Freeze request queued: %s",
                        LOG_PREFIX_PKG_FREEZER, packageName));
            }
        }

        public void unfreezePackageLevel(String packageName) {
            if (packageName == null) {
                return;
            }

            removePendingProcessesByPackage(packageName);
            if (!containsApp(packageName)) {
                if (mUseDebug) {
                    Slog.d(TAG, String.format("%s Skip unfreeze request: %s (not frozen)",
                            LOG_PREFIX_PKG_FREEZER, packageName));

                }
                return;
            }
            mHandler.sendMessage(mHandler.obtainMessage(
                UNFREEZE_PACKAGE_LEVEL, 0, 0 /* unused */, packageName));
            if (mUseDebug) {
                Slog.i(TAG, String.format("%s Unfreeze request queued: %s",
                        LOG_PREFIX_PKG_FREEZER, packageName));
            }
        }

        public void unfreezeAllFrozenPackages() {
            List<String> packagesToUnfreeze;

            synchronized (mAppPidsLock) {
                if (mAppPids.isEmpty()) {
                    return;
                }
                packagesToUnfreeze = new ArrayList<>(mAppPids.keySet());
            }

            for (String packageName : packagesToUnfreeze) {
                unfreezePackageLevel(packageName);
            }
        }

        public boolean isProcessFrozen(ProcessRecord app) {
            if (app == null || app.info == null || app.info.packageName == null) {
                return false;
            }

            String packageName = app.info.packageName;
            int pid = app.getPid();

            synchronized (mAppPidsLock) {
                SparseArray<ProcessRecord> frozenProcs = mAppPids.get(packageName);
                if (frozenProcs != null) {
                    // Check if this specific PID is in the frozen list for this package
                    return frozenProcs.indexOfKey(pid) >= 0;
                }
            }
            return false;
        }

        public void onProcessDied(ProcessRecord app, int pid) {
            removePendingProcess(app);

            if (app != null && app.info != null) {
                String packageName = app.info.packageName;
                synchronized (mAppPidsLock) {
                    SparseArray<ProcessRecord> pids = mAppPids.get(packageName);
                    if (pids != null) {
                        if (pids.get(pid) != null) {
                            pids.remove(pid);
                            if (mUseDebug) {
                                Slog.d(TAG, String.format(
                                        "%s Process died, removed from freezer: %s (pid=%d)",
                                        LOG_PREFIX_PKG_FREEZER, app.processName, pid));
                            }
                        }
                        if (pids.size() == 0) {
                            mAppPids.remove(packageName);
                        }
                    }
                }
            }
        }

        public void dump(PrintWriter pw, String prefix) {
            pw.print(prefix);
            pw.println("PACKAGE LEVEL FREEZER:");

            String innerPrefix = prefix + "  ";
            String doubleInnerPrefix = prefix + "    ";

            synchronized (mAppPidsLock) {
                if (mAppPids.isEmpty()) {
                    pw.print(innerPrefix);
                    pw.println("No packages currently frozen");
                } else {
                    pw.print(innerPrefix);
                    pw.println(mAppPids.size() + " package(s) frozen:");

                    for (Map.Entry<String, SparseArray<ProcessRecord>> entry: mAppPids.entrySet()){
                        String packageName = entry.getKey();
                        SparseArray<ProcessRecord> processes = entry.getValue();

                        pw.print(innerPrefix);
                        pw.println("Package: " + packageName
                                + " (" + processes.size() + " processes)");

                        for (int i = 0; i < processes.size(); i++) {
                            ProcessRecord app = processes.valueAt(i);
                            pw.print(doubleInnerPrefix);
                            pw.println("- PID: " + app.getPid() + ", Process: " + app.processName);
                        }
                    }
                }
            }

            synchronized (mPendingFreezeLock) {
                if (mPendingFreezeMap.isEmpty()) {
                    pw.print(innerPrefix);
                    pw.println("No pending processes");
                } else {
                    pw.print(innerPrefix);
                    pw.println("Pending processes (" + mPendingFreezeMap.size() + "):");

                    for (Map.Entry<ProcessRecord, PendingInfo> entry: mPendingFreezeMap.entrySet()){
                        ProcessRecord app = entry.getKey();
                        PendingInfo info = entry.getValue();

                        pw.print(doubleInnerPrefix);
                        pw.print("- PID: "); pw.print(app.getPid());
                        pw.print(", Process: "); pw.print(app.processName);
                        pw.print(", Pkg: "); pw.print(app.info.packageName);
                        pw.print(", Reason: "); pw.print(info.mReason);
                        pw.print(", Retries: "); pw.println(info.mRetryCount);
                    }
                }
            }
            pw.println();
        }
    }

    public class AppKeepaliveManagement {
        private static final String SETTINGS_KEEPALIVE_PREFIX = "keepalive_policy_";
        private static final int DEFAULT_PROC_WEIGHT = -1;
        private static final int LOW_PROC_WEIGHT = 0;

        public void onPackageInstalled(String packageName) {
            try {
                Settings.Global.putInt(mContentResolver,
                        SETTINGS_KEEPALIVE_PREFIX + packageName, LOW_PROC_WEIGHT);
            } catch (Exception e) {
                Slog.e(TAG, String.format(
                        "%s Failed to update keepalive policy for package: %s",
                        LOG_PREFIX_KEEPALIVE, packageName), e);
            }
        }

        public void updateLmkLazyKillFLag(boolean enabled) {
            if (mUseAppKeepaliveManager) {
                ProcessList.updateLmkLazyKillFLag(enabled);
            }
        }

        public ArrayList<Integer> getProcsKeepaliveWeight(ArrayList<ProcessRecordInternal> Procs) {
            ArrayList<Integer> weights = new ArrayList<>(Procs.size());
            for (int i = 0; i < Procs.size(); i++) {
                int weight = getProcKeepaliveWeight(Procs.get(i));
                weights.add(weight);
            }

            return weights;
        }

        public int getProcKeepaliveWeight(ProcessRecordInternal state) {
            final ProcessRecord app = (ProcessRecord) state;

            if (!isThirdPartyAppMainProcess(state)) {
                return DEFAULT_PROC_WEIGHT;
            }

            if (mContentResolver == null) {
                return LOW_PROC_WEIGHT;
            }

            try {
                return Settings.Global.getInt(mContentResolver,
                            SETTINGS_KEEPALIVE_PREFIX + app.processName);
            } catch (Settings.SettingNotFoundException e) {
                // If setting doesn't exist, assume it's low priority by default.
                return LOW_PROC_WEIGHT;
            }
        }

        public void cleanupUninstalledAppResources(String packageName) {
            try {
                mContentResolver.delete(
                    Settings.Global.CONTENT_URI,
                    Settings.NameValueTable.NAME + " = ?",
                    new String[]{SETTINGS_KEEPALIVE_PREFIX + packageName}
                );
            } catch (Exception e) {
                Slog.e(TAG, String.format("%s Failed to clear keepalive policy settings for %s",
                        LOG_PREFIX_KEEPALIVE, packageName), e);
            }
        }

        public void dump(PrintWriter pw, String prefix) {
            if (mUseAppKeepaliveManager) {
                pw.println("KEEPALIVE MANAGEMENT:");
                boolean hasPolicies = false;
                if (mAm != null && mContentResolver != null) {
                    try {
                        PackageManager pm = mAm.mContext.getPackageManager();
                        List<ApplicationInfo> apps =
                                pm.getInstalledApplications(PackageManager.GET_META_DATA);

                        if (apps != null && !apps.isEmpty()) {
                            pw.println("  Configured keepalive policies:");

                            for (ApplicationInfo app : apps) {
                                if ((app.flags & (ApplicationInfo.FLAG_SYSTEM)) != 0) {
                                    continue;
                                }

                                try {
                                    String key = SETTINGS_KEEPALIVE_PREFIX + app.packageName;
                                    int weight = Settings.Global.getInt(mContentResolver, key);

                                    String priorityDesc;
                                    switch (weight) {
                                        case 0:
                                            priorityDesc = "LOW";
                                            break;
                                        case 1:
                                            priorityDesc = "MEDIUM";
                                            break;
                                        case 2:
                                            priorityDesc = "HIGH";
                                            break;
                                        default:
                                            priorityDesc = "CUSTOM(" + weight + ")";
                                            break;
                                    }

                                    pw.println(String.format("    %-40s (priority: %s)",
                                        app.packageName, priorityDesc));
                                } catch (Settings.SettingNotFoundException e) {
                                    //
                                } catch (Exception e) {
                                    Slog.w(TAG, String.format(
                                            "%s Error checking keepalive policy for %s",
                                            LOG_PREFIX_KEEPALIVE, app.packageName), e);
                                }
                            }
                        } else {
                            pw.println("    No applications found to check");
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, LOG_PREFIX_KEEPALIVE +
                                " Failed to get installed applications", e);
                        pw.println("    Error retrieving application list: " +
                                e.getMessage());
                    }
                } else {
                    pw.println("    ActivityManager or ContentResolver not available");
                }

                pw.println("  Keepalive priorities can be configured via:");
                pw.println("    settings put global " + SETTINGS_KEEPALIVE_PREFIX
                            + "<package_name> <priority>");
                pw.println("  Where:");
                pw.println("    0 = LOW priority (default)");
                pw.println("    1 = MEDIUM priority");
                pw.println("    2 = HIGH priority");
                pw.println("  Example:");
                pw.println("    settings put global " + SETTINGS_KEEPALIVE_PREFIX
                            + "com.example.app 2");
                pw.println("  To list all configured keepalive policies:");
                pw.println("    settings list global | grep " + SETTINGS_KEEPALIVE_PREFIX);
                pw.println();
            }
        }
    }

    public class AutoStartManagement {
        private final String CUR_TAG = TAG;
        private static final String SETTINGS_AUTO_START_PREFIX = "auto_start_policy_";
        private static final int AUTO_START_POLICY_RESTRICTED = 0;
        private static final int AUTO_START_POLICY_ALLOWED = 1;
        private final Map<String, Boolean> mMainProcState = new HashMap<>();
        private Object mLock = new Object();

        public boolean isPackageExempt(String packageName) {
            if (mContentResolver == null) {
                return true;
            }

            try {
                if (Settings.Global.getInt(mContentResolver,
                        SETTINGS_AUTO_START_PREFIX + packageName) == AUTO_START_POLICY_ALLOWED) {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format("%s Package %s is exempt from auto-start (settings)",
                                LOG_PREFIX_AUTOSTART, packageName));
                    }
                    return true;
                } else {
                    if (mUseDebug) {
                        Slog.d(TAG, String.format(
                                "%s Package %s is exempt from auto-start (settings)",
                                LOG_PREFIX_AUTOSTART, packageName));
                    }
                    return false;
                }
            } catch (Settings.SettingNotFoundException e) {
                // If setting doesn't exist, assume it's allowed for auto start
                return true;
            }
        }

        public void onPackageInstalled(String packageName) {
            try {
                Settings.Global.putInt(mContentResolver,
                        SETTINGS_AUTO_START_PREFIX + packageName, AUTO_START_POLICY_RESTRICTED);
            } catch (Exception e) {
                Slog.e(TAG, String.format(
                        "%s Failed to update auto start policy for package: %s",
                        LOG_PREFIX_AUTOSTART, packageName), e);
            }
        }

        public void cleanupUninstalledAppResources(String packageName) {
            try {
                mContentResolver.delete(
                    Settings.Global.CONTENT_URI,
                    Settings.NameValueTable.NAME + " = ?",
                    new String[]{SETTINGS_AUTO_START_PREFIX + packageName,}
                );
            } catch (Exception e) {
                Slog.e(TAG, String.format("%s Failed to clear auto-start policy settings for %s",
                        LOG_PREFIX_AUTOSTART, packageName), e);
            }
        }

        public void clearAutoStartMap() {
            synchronized (mLock) {
                mMainProcState.clear();
            }
        }

        public void removePackageAutoStartState(String packageName) {
            synchronized (mLock) {
                mMainProcState.remove(packageName);
            }
        }

        public boolean shouldPreventStart(String packageName, String processName) {
            synchronized (mLock) {
                final Boolean isMainProcFg  = mMainProcState.get(packageName);
                if (isMainProcFg  != null && isMainProcFg ) {
                    if (mUseDebug) {
                        Slog.d(CUR_TAG, String.format("%s Allowed: %s (package: %s)",
                                LOG_PREFIX_AUTOSTART, processName, packageName));
                    }
                    return false;
                }
                if (mUseDebug) {
                    Slog.d(CUR_TAG, String.format("%s Blocked: %s (package: %s)",
                            LOG_PREFIX_AUTOSTART, processName, packageName));
                }
                return true;
            }
        }

        public void setPackageAutoStartAllowed(String packageName) {
            synchronized (mLock) {
                final Boolean previousState = mMainProcState.put(packageName, true);
                if (mUseDebug) {
                    Slog.d(CUR_TAG, String.format("%s Update: %s → ALLOW auto-start",
                            LOG_PREFIX_AUTOSTART, packageName));
                }
            }
        }

        public void setPackageAutoStartBlocked(String packageName) {
            synchronized (mLock) {
                final Boolean previousState = mMainProcState.put(packageName, false);
                if (mUseDebug) {
                    Slog.d(CUR_TAG, String.format("%s Update: %s → BLOCK auto-start",
                            LOG_PREFIX_AUTOSTART, packageName));
                }
            }
        }

        public void dump(PrintWriter pw, String prefix) {
            pw.print(prefix);
            pw.println("AUTO START MANAGEMENT:");

            String innerPrefix = prefix + "  ";
            String doubleInnerPrefix = prefix + "    ";

            synchronized (mLock) {
                if (mMainProcState.isEmpty()) {
                    pw.print(innerPrefix);
                    pw.println("No packages with auto-start restrictions");
                } else {
                    pw.print(innerPrefix);
                    pw.println(mMainProcState.size()
                            + " package(s) with auto-start restrictions:");

                    for (Map.Entry<String, Boolean> entry : mMainProcState.entrySet()) {
                        String packageName = entry.getKey();
                        boolean allowed = entry.getValue();

                        pw.print(doubleInnerPrefix);
                        pw.println("Package: " + packageName +
                                " (auto-start " + (allowed ? "allowed" : "blocked") + ")");
                    }
                }
            }
            pw.println();
        }
    }

    public boolean shouldSkipAnrForFrozenApp(ProcessRecord app) {
        if (!mUsePackageLevelFreezer || mPackageFreezerManager == null) {
            return false;
        }

        boolean isFrozen = mPackageFreezerManager.isProcessFrozen(app);
        if (isFrozen) {
            Slog.w(TAG, String.format("%s Skip ANR for frozen app: %s (pid=%d)",
                    LOG_PREFIX_POLICY, app.processName, app.getPid()));
        }

        return isFrozen;
    }

    public boolean shouldPreventProcessStart(String processName, ApplicationInfo info) {
        if (!mUseUiFluencyMode && !mUseAppBgManager) {
            return false;
        }

        if (!mUseRestrictBgAutoStart) {
            return false;
        }

        if (info == null || info.isSystemApp() || info.isUpdatedSystemApp()) {
            return false;
        }

        return mAutoStartManager.shouldPreventStart(info.packageName, processName);
    }

    private void handleUIFluencyModeDisabled() {
        // clear restrict auto-start flag
        // unfreeze the frozen processes
        Slog.e(TAG, LOG_PREFIX_CONFIG + " ═══ Handle UI Fluency Mode DISABLED ═══");
        mAutoStartManager.clearAutoStartMap();
        mPackageFreezerManager.unfreezeAllFrozenPackages();
    }

    public ArrayList<Integer> getProcsKeepaliveWeight(ArrayList<ProcessRecordInternal> Procs) {
        return mAppKeepaliveManager.getProcsKeepaliveWeight(Procs);
    }

    public int getProcKeepaliveWeight(ProcessRecordInternal state) {
        return mAppKeepaliveManager.getProcKeepaliveWeight(state);
    }

    public void handleProcessDied(ProcessRecord app, int pid) {
        if (!mUseUiFluencyMode && !mUseAppBgManager) {
            return;
        }

        if (mUsePackageLevelFreezer) {
            mPackageFreezerManager.onProcessDied(app, pid);
        }
    }

    public void handleActivityStart(ApplicationInfo info) {
        if (!mUseUiFluencyMode && !mUseAppBgManager) {
            return;
        }

        if (info == null || info.isSystemApp() || info.isUpdatedSystemApp()) {
            return;
        }

        if (mUseDebug) {
            Slog.d(TAG, String.format("%s === Activity Start Request Top app: %s ===",
                    LOG_PREFIX_UI, info.packageName));
        }

        activateAppForForeground(info.packageName);
    }

    public void handleSchedGroupTransition(ProcessRecordInternal state, int oldSchedGroup,
                int curSchedGroup, Handler handler) {
        if (!mUseUiFluencyMode && !mUseAppBgManager) {
            return;
        }

        if (!isThirdPartyAppMainProcess(state)) {
            return;
        }

        final String packageName = state.processName;

        if (curSchedGroup == Constants.SCHED_GROUP_TOP_APP &&
                oldSchedGroup != Constants.SCHED_GROUP_TOP_APP) {
            if (mUseDebug) {
                Slog.d(TAG, String.format("%s === App entered TOP_APP group: %s === ",
                        LOG_PREFIX_UI, packageName));
            }

            if (mUseUIAffinitySettings) {
                applyUIAffinitySettingsAsync(state, true, handler);
            }

            if (mUseUIRTSettings) {
                applyUIRTSettings(state, true);
            }

            activateAppForForeground(packageName);
        } else if (oldSchedGroup == Constants.SCHED_GROUP_TOP_APP &&
                curSchedGroup != Constants.SCHED_GROUP_TOP_APP) {
            if (mUseDebug) {
                Slog.d(TAG, String.format("%s === App entered NON TOP_APP group: %s ===",
                        LOG_PREFIX_UI, packageName));
            }

            if (mUseUIAffinitySettings) {
                applyUIAffinitySettingsAsync(state, false, handler);
            }

            if (mUseUIRTSettings) {
                applyUIRTSettings(state, false);
            }

            deactivateAppForBackground(packageName);
        }
    }

    private void activateAppForForeground(String packageName) {
        if (mUsePackageLevelFreezer) {
            mPackageFreezerManager.unfreezePackageLevel(packageName);
        }

        if (mUseRestrictBgAutoStart) {
            mAutoStartManager.setPackageAutoStartAllowed(packageName);
        }
    }

    private void deactivateAppForBackground(String packageName) {
        if (mUsePackageLevelFreezer) {
            if (mPackageFreezerManager.isPackageExempt(packageName)) {
            } else {
                mPackageFreezerManager.freezePackageLevel(packageName);
            }
        }

        if (mUseRestrictBgAutoStart) {
            if (mAutoStartManager.isPackageExempt(packageName)) {
            } else {
                mAutoStartManager.setPackageAutoStartBlocked(packageName);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        // Check permissions
        if (mAm != null && mAm.checkCallingPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump UI Fluency Info from pid=" +
                    android.os.Binder.getCallingPid() +
                    ", uid=" + android.os.Binder.getCallingUid());
            return;
        }
        pw.println("APP BACKGROUND MANAGER STATE (dumpsys activity ui-fluency):");
        pw.println();

        // Configuration dump
        pw.println("CONFIGURATION:");
        pw.println("  qti_bg_manager.enable: " + mUseAppBgManager);
        pw.println("  qti_bg_manager.enable_restrict_auto_start: " + mUseRestrictBgAutoStart);
        pw.println("  qti_bg_manager.enable_process_level_freezer: " + mUseProcessLevelFreezer);
        pw.println("  qti_bg_manager.enable_package_level_freezer: " + mUsePackageLevelFreezer);
        pw.println("  qti_bg_manager.enable_app_keepalive_manager: " + mUseAppKeepaliveManager);
        pw.println("  qti_bg_manager.enable_ui_rt_settings: " + mUseUIRTSettings);
        pw.println("  qti_bg_manager.enable_ui_affinity_settings: " + mUseUIAffinitySettings);
        pw.println("  qti_bg_manager.enable_aggressive_policy: " + mUseAggressivePolicy);
        pw.println("  qti_bg_manager.enable_cpu_load_monitor: " + mUseCpuLoadMonitor);
        pw.println("  qti_bg_manager.cpu_load_monitor_usage_threshold: "
                    + mCpuUsageThreshold + "%");
        pw.println("  qti_bg_manager.cpu_load_monitor_cpuset_bg: " + mCpuLoadMonitorBG);
        pw.println("  qti_bg_manager.freeze_adj_threshold: " + mFreezeAdjThreshold);
        pw.println("  qti_bg_manager.launch_timeout_threshold: " + mLaunchTimeout + "ms");
        pw.println("  qti_bg_manager.delay_unfreeze_threshold: " + mDelayUnfreezeTimeout + "ms");
        pw.println("  qti_bg_manager.enable_debug: " + mUseDebug);
        pw.println("  ro.vendor.perf.affinity: " + mUsePerfCoreAffinity);
        pw.println("  ui_fluency_mode_enabled: " + mUseUiFluencyMode);
        pw.println();

        // CPU Load Monitor status
        pw.println("CPU LOAD MONITOR:");
        pw.println("  current state: "
                    + (mCpuLoadMonitor.getCpuHighLoadFlagLocked() ? "HIGH LOAD" : "LOW LOAD"));
        pw.println("  usage threshold: " + mCpuUsageThreshold + "%");
        pw.println("  monitoring " + (mCpuLoadMonitorBG ? "background CPU set" : "all CPUs"));
        pw.println();

        // Package level freezer status
        if (mUsePackageLevelFreezer) {
            mPackageFreezerManager.dump(pw, "");
        }

        // Auto start management status
        if (mUseRestrictBgAutoStart) {
            mAutoStartManager.dump(pw, "");
        }

        // Keepalive management status
        if (mUseAppKeepaliveManager) {
            mAppKeepaliveManager.dump(pw, "");
        }
    }
}
