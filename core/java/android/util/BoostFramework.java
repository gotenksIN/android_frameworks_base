// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
/*
 * Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.util;

// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
import android.content.Context;
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization
import android.database.ContentObserver;
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization
// QTI_BEGIN: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
import android.graphics.BLASTBufferQueue;
// QTI_END: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization
import android.net.Uri;
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
import android.os.SystemProperties;
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization
import android.provider.Settings;
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
import android.util.Log;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.

// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
import dalvik.system.PathClassLoader;

// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** @hide */
public class BoostFramework {

    private static final String TAG = "BoostFramework";
    private static final String PERFORMANCE_JAR = "/system/framework/QPerformance.jar";
    private static final String PERFORMANCE_CLASS = "com.qualcomm.qti.Performance";

// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    private static final String UXPERFORMANCE_JAR = "/system/framework/UxPerformance.jar";
    private static final String UXPERFORMANCE_CLASS = "com.qualcomm.qti.UxPerformance";
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2022-01-09: Core: Perf: Added support for app type in launch hint
    public  static final float PERF_HAL_V22 = 2.2f;
    public  static final float PERF_HAL_V23 = 2.3f;
// QTI_END: 2022-01-09: Core: Perf: Added support for app type in launch hint
// QTI_BEGIN: 2022-07-20: Core: PrefApps: Migrate to Perf Hal for PA feature
    public static final int VENDOR_T_API_LEVEL = 33;
// QTI_END: 2022-07-20: Core: PrefApps: Migrate to Perf Hal for PA feature
// QTI_BEGIN: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
    public static final int VENDOR_V_API_LEVEL = 202404;
// QTI_END: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
    public final int board_first_api_lvl = SystemProperties.getInt("ro.board.first_api_level", 0);
    public final int board_api_lvl = SystemProperties.getInt("ro.board.api_level", 0);
// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization
    //key in privider settings global
    public static final String KEY_LEGACY_UI_PERF_PKGS = "LEGACY_UI_PERF_PROCS";
    public static final String KEY_GPU_PREFER = "UI_PERF_GPU_PREFER";
    public static final String KEY_CPU_PREFER = "UI_PERF_CPU_PREFER";
    public static final String KEY_CPU_AGGRESSIVE = "UI_PERF_CPU_AGGRESSIVE";
    public static final String KEY_CPU_GPU = "UI_PERF_CPU_GPU";
    public static final String KEY_PKGS = "UI_PERF_PKGS";
    //ui perf mode controller in android space
    public static final String UI_PERF_PROP = "debug.ui.perfmode.enable";
    public static final String UI_PERF_PROC_PROP = "debug.ui.perfmode.process";

    private String[] mUIPerfProcs = null;
    private String[] mLegacyUIPerfProcs = null;
    private String[] mUIPerfGpuActivities = null;
    private String[] mUIPerfCpuActivities = null;
    private String[] mUIPerfCpuGpuActivities = null;
    private String[] mUIPerfCpuAggressive = null;
    private UiPerfProcsObserver mContentObserver = null;
    private boolean mUiPerfInited = false;
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization

// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
/** @hide */
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
    private static boolean sIsLoaded = false;
    private static Class<?> sPerfClass = null;
    private static Method sAcquireFunc = null;
    private static Method sPerfHintFunc = null;
    private static Method sReleaseFunc = null;
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
    private static Method sPerfHintRelFunc = null;
// QTI_END: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
    private static Method sReleaseHandlerFunc = null;
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
    private static Method sFeedbackFunc = null;
// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
    private static Method sFeedbackFuncExtn = null;
// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework
    private static Method sPerfGetPropFunc = null;
// QTI_END: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework
// QTI_BEGIN: 2020-08-26: Core: perf: Added new API support
    private static Method sAcqAndReleaseFunc = null;
// QTI_END: 2020-08-26: Core: perf: Added new API support
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
    private static Method sperfHintAcqRelFunc = null;
    private static Method sperfHintRenewFunc = null;
    private static Method sPerfEventFunc = null;
// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2022-01-09: Core: Perf: Added support for app type in launch hint
    private static Method sPerfGetPerfHalVerFunc = null;
// QTI_END: 2022-01-09: Core: Perf: Added support for app type in launch hint
// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
    private static Method sPerfSyncRequest = null;
// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.

// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    private static Method sIOPStart = null;
    private static Method sIOPStop  = null;
    private static Method sUXEngineEvents  = null;
    private static Method sUXEngineTrigger  = null;

    private static boolean sUxIsLoaded = false;
    private static Class<?> sUxPerfClass = null;
    private static Method sUxIOPStart = null;

// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
/** @hide */
    private Object mPerf = null;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    private Object mUxPerf = null;
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.

    //perf hints
    public static final int VENDOR_HINT_SCROLL_BOOST = 0x00001080;
    public static final int VENDOR_HINT_FIRST_LAUNCH_BOOST = 0x00001081;
    public static final int VENDOR_HINT_SUBSEQ_LAUNCH_BOOST = 0x00001082;
    public static final int VENDOR_HINT_ANIM_BOOST = 0x00001083;
    public static final int VENDOR_HINT_ACTIVITY_BOOST = 0x00001084;
    public static final int VENDOR_HINT_TOUCH_BOOST = 0x00001085;
    public static final int VENDOR_HINT_MTP_BOOST = 0x00001086;
    public static final int VENDOR_HINT_DRAG_BOOST = 0x00001087;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-27: Core: framework/base: add parallel verifyV1
    public static final int VENDOR_HINT_PACKAGE_INSTALL_BOOST = 0x00001088;
// QTI_END: 2018-03-27: Core: framework/base: add parallel verifyV1
// QTI_BEGIN: 2018-04-23: Core: Add perf hint for rotation latency
    public static final int VENDOR_HINT_ROTATION_LATENCY_BOOST = 0x00001089;
// QTI_END: 2018-04-23: Core: Add perf hint for rotation latency
// QTI_BEGIN: 2018-05-09: Core: Add perf hint for screen rotation animation
    public static final int VENDOR_HINT_ROTATION_ANIM_BOOST = 0x00001090;
// QTI_END: 2018-05-09: Core: Add perf hint for screen rotation animation
// QTI_BEGIN: 2019-05-06: Core: Add perf hint for performance mode
    public static final int VENDOR_HINT_PERFORMANCE_MODE = 0x00001091;
// QTI_END: 2019-05-06: Core: Add perf hint for performance mode
// QTI_BEGIN: 2019-08-16: Core: BoostFramework: Q Upgrade - Add Kill, Update Hints.
    public static final int VENDOR_HINT_APP_UPDATE = 0x00001092;
    public static final int VENDOR_HINT_KILL = 0x00001093;
// QTI_END: 2019-08-16: Core: BoostFramework: Q Upgrade - Add Kill, Update Hints.
// QTI_BEGIN: 2020-07-07: Core: Correct hint-id to resolve conflict
    public static final int VENDOR_HINT_BOOST_RENDERTHREAD = 0x00001096;
// QTI_END: 2020-07-07: Core: Correct hint-id to resolve conflict
// QTI_BEGIN: 2023-12-10: Core: Send top-app pid and renderthread tid to perf-hal
    public static final int VENDOR_HINT_PASS_PID = 0x0000109C;
// QTI_END: 2023-12-10: Core: Send top-app pid and renderthread tid to perf-hal
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization
    public static final int VENDOR_HINT_SCENARIO_GPU = 0x000010AA;
    public static final int VENDOR_HINT_SCENARIO_CPU = 0x000010AB;
    public static final int VENDOR_HINT_SCENARIO_CPU_GPU = 0x000010AC;
    public static final int VENDOR_HINT_SCENARIO_CPU_AGGRESSIVE = 0x000010AD;
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
    //perf events
    public static final int VENDOR_HINT_FIRST_DRAW = 0x00001042;
    public static final int VENDOR_HINT_TAP_EVENT = 0x00001043;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
    public static final int VENDOR_HINT_DRAG_START = 0x00001051;
    public static final int VENDOR_HINT_DRAG_END = 0x00001052;
// QTI_BEGIN: 2025-08-06: Core: base: pin/unpin files based on launch and exit for vendor pinner service
    public static final int VENDOR_HINT_PIN_FILE = 0x0000105E;
    public static final int VENDOR_HINT_UNPIN_FILE = 0x0000105F;
// QTI_END: 2025-08-06: Core: base: pin/unpin files based on launch and exit for vendor pinner service
// QTI_BEGIN: 2022-05-26: Core: perf: IME boost
    //Ime Launch Boost Hint
    public static final int VENDOR_HINT_IME_LAUNCH_EVENT = 0x0000109F;
// QTI_END: 2022-05-26: Core: perf: IME boost
// QTI_BEGIN: 2023-10-24: Core: perf: add exit app animation boost for apps exit.
    //App exit animation boost
    public static final int VENDOR_HINT_EXIT_ANIM_BOOST = 0x000010A9;
// QTI_END: 2023-10-24: Core: perf: add exit app animation boost for apps exit.

// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
    //feedback hints
    public static final int VENDOR_FEEDBACK_WORKLOAD_TYPE = 0x00001601;
    public static final int VENDOR_FEEDBACK_LAUNCH_END_POINT = 0x00001602;
// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
    public static final int VENDOR_FEEDBACK_PA_FW = 0x00001604;
// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.

// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    //UXE Events and Triggers
    public static final int UXE_TRIGGER = 1;
    public static final int UXE_EVENT_BINDAPP = 2;
    public static final int UXE_EVENT_DISPLAYED_ACT = 3;
    public static final int UXE_EVENT_KILL = 4;
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    public static final int UXE_EVENT_GAME  = 5;
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
    public static final int UXE_EVENT_SUB_LAUNCH = 6;
    public static final int UXE_EVENT_PKG_UNINSTALL = 7;
    public static final int UXE_EVENT_PKG_INSTALL = 8;

// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
    //New Hints while porting IOP to Perf Hal.
    public static final int VENDOR_HINT_BINDAPP = 0x000010A0;
    public static final int VENDOR_HINT_WARM_LAUNCH = 0x000010A1; //SUB_LAUNCH
    // 0x000010A2 is added in UXPerformance.java for SPEED Hints
    public static final int VENDOR_HINT_PKG_INSTALL = 0x000010A3;
    public static final int VENDOR_HINT_PKG_UNINSTALL = 0x000010A4;

// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
// QTI_BEGIN: 2020-07-09: Core: Hooks for background apps transition
    //perf opcodes
    public static final int MPCTLV3_GPU_IS_APP_FG = 0X42820000;
    public static final int MPCTLV3_GPU_IS_APP_BG = 0X42824000;

// QTI_END: 2020-07-09: Core: Hooks for background apps transition
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
    public class Scroll {
        public static final int VERTICAL = 1;
        public static final int HORIZONTAL = 2;
        public static final int PANEL_VIEW = 3;
        public static final int PREFILING = 4;
    };

    public class Launch {
        public static final int BOOST_V1 = 1;
        public static final int BOOST_V2 = 2;
        public static final int BOOST_V3 = 3;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        public static final int BOOST_GAME = 4;
// QTI_BEGIN: 2019-04-11: Core: Boostframework: Adding reserved hint types for launch boost
        public static final int RESERVED_1 = 5;
        public static final int RESERVED_2 = 6;
// QTI_END: 2019-04-11: Core: Boostframework: Adding reserved hint types for launch boost
// QTI_BEGIN: 2022-01-17: Core: Perf: Updated launch hint types
        public static final int RESERVED_3 = 7;
        public static final int RESERVED_4 = 8;
        public static final int RESERVED_5 = 9;
// QTI_END: 2022-01-17: Core: Perf: Updated launch hint types
// QTI_BEGIN: 2022-04-20: Core: perf: Fix for Activity launch hint when LAL is enabled.
        public static final int ACTIVITY_LAUNCH_BOOST = 10;
// QTI_END: 2022-04-20: Core: perf: Fix for Activity launch hint when LAL is enabled.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        public static final int TYPE_SERVICE_START = 100;
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2019-08-16: Core: BoostFramework: Q Upgrade - Add Kill, Update Hints.
        public static final int TYPE_START_PROC = 101;
// QTI_END: 2019-08-16: Core: BoostFramework: Q Upgrade - Add Kill, Update Hints.
// QTI_BEGIN: 2019-10-17: Core: BoostFramework: New hintType for App Starting from BG.
        public static final int TYPE_START_APP_FROM_BG = 102;
// QTI_END: 2019-10-17: Core: BoostFramework: New hintType for App Starting from BG.
// QTI_BEGIN: 2019-10-22: Core: Perf: Boost UI thread during app launching
        public static final int TYPE_ATTACH_APPLICATION = 103;
// QTI_END: 2019-10-22: Core: Perf: Boost UI thread during app launching
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
    };

// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2023-12-10: Core: Send top-app pid and renderthread tid to perf-hal
    public class PassPid {
        public static final int APP_PID = 4;
        public static final int RENDER_TID = 5;
    }

// QTI_END: 2023-12-10: Core: Send top-app pid and renderthread tid to perf-hal
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
    public class Draw {
        public static final int EVENT_TYPE_V1 = 1;
    };

// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-11-10: Core: Modify game detection logic
    public class WorkloadType {
        public static final int NOT_KNOWN = 0;
        public static final int APP = 1;
        public static final int GAME = 2;
        public static final int BROWSER = 3;
        public static final int PREPROAPP = 4;
    };

// QTI_END: 2018-11-10: Core: Modify game detection logic
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
/** @hide */
    public BoostFramework() {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
        initFunctions();

        try {
            if (sPerfClass != null) {
                mPerf = sPerfClass.newInstance();
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
            if (sUxPerfClass != null) {
                mUxPerf = sUxPerfClass.newInstance();
            }
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_2 = " + e);
        }
    }

/** @hide */
    public BoostFramework(Context context) {
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2020-04-03: Core: Add support for UxPerformance to receive context information.
        this(context, false);
    }

/** @hide */
    public BoostFramework(Context context, boolean isTrusted) {
// QTI_END: 2020-04-03: Core: Add support for UxPerformance to receive context information.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
        initFunctions();

        try {
            if (sPerfClass != null) {
                Constructor cons = sPerfClass.getConstructor(Context.class);
                if (cons != null)
                    mPerf = cons.newInstance(context);
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
            if (sUxPerfClass != null) {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2020-04-03: Core: Add support for UxPerformance to receive context information.
                if (isTrusted) {
                    Constructor cons = sUxPerfClass.getConstructor(Context.class);
                    if (cons != null)
                        mUxPerf = cons.newInstance(context);
                } else {
                    mUxPerf = sUxPerfClass.newInstance();
                }
// QTI_END: 2020-04-03: Core: Add support for UxPerformance to receive context information.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
            }
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_3 = " + e);
        }
    }

// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-10-31: Core: Support the perflock request from system/priv apps whose domain are untrusted
/** @hide */
    public BoostFramework(boolean isUntrustedDomain) {
        initFunctions();

        try {
            if (sPerfClass != null) {
                Constructor cons = sPerfClass.getConstructor(boolean.class);
                if (cons != null)
                    mPerf = cons.newInstance(isUntrustedDomain);
            }
            if (sUxPerfClass != null) {
                mUxPerf = sUxPerfClass.newInstance();
            }
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_5 = " + e);
        }
    }

// QTI_END: 2018-10-31: Core: Support the perflock request from system/priv apps whose domain are untrusted
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
    private void initFunctions () {
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        synchronized(BoostFramework.class) {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
            if (sIsLoaded == false) {
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
                try {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sPerfClass = Class.forName(PERFORMANCE_CLASS);
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.

                    Class[] argClasses = new Class[] {int.class, int[].class};
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sAcquireFunc = sPerfClass.getMethod("perfLockAcquire", argClasses);
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.

                    argClasses = new Class[] {int.class, String.class, int.class, int.class};
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sPerfHintFunc = sPerfClass.getMethod("perfHint", argClasses);
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.

                    argClasses = new Class[] {};
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sReleaseFunc = sPerfClass.getMethod("perfLockRelease", argClasses);
// QTI_END: 2018-03-21: Core: add perf_service into system_process.

// QTI_BEGIN: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
                    argClasses = new Class[] {};
                    sPerfHintRelFunc = sPerfClass.getMethod("perfHintRelease", argClasses);

// QTI_END: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
                    argClasses = new Class[] {int.class};
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sReleaseHandlerFunc = sPerfClass.getDeclaredMethod("perfLockReleaseHandler", argClasses);
// QTI_END: 2018-03-21: Core: add perf_service into system_process.

// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
                    argClasses = new Class[] {int.class, String.class};
                    sFeedbackFunc = sPerfClass.getMethod("perfGetFeedback", argClasses);

// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
                    argClasses = new Class[] {int.class, String.class, int.class, int[].class};
                    sFeedbackFuncExtn = sPerfClass.getMethod("perfGetFeedbackExtn", argClasses);

// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                    argClasses = new Class[] {int.class, String.class, String.class};
                    sIOPStart =   sPerfClass.getDeclaredMethod("perfIOPrefetchStart", argClasses);

                    argClasses = new Class[] {};
                    sIOPStop =  sPerfClass.getDeclaredMethod("perfIOPrefetchStop", argClasses);

// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework
                    argClasses = new Class[] {String.class, String.class};
                    sPerfGetPropFunc = sPerfClass.getMethod("perfGetProp", argClasses);

// QTI_END: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework
// QTI_BEGIN: 2020-08-26: Core: perf: Added new API support
                    argClasses = new Class[] {int.class, int.class, int.class, int.class, int[].class};
                    sAcqAndReleaseFunc = sPerfClass.getMethod("perfLockAcqAndRelease", argClasses);

// QTI_END: 2020-08-26: Core: perf: Added new API support
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
                    argClasses = new Class[] {int.class, String.class, int.class, int[].class};
                    sPerfEventFunc = sPerfClass.getMethod("perfEvent", argClasses);

// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
                    argClasses = new Class[] {int.class};
                    sPerfSyncRequest = sPerfClass.getMethod("perfSyncRequest", argClasses);

// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
                    argClasses = new Class[] {int.class, int.class, String.class, int.class,
                                              int.class, int.class, int[].class};
                    sperfHintAcqRelFunc = sPerfClass.getMethod("perfHintAcqRel", argClasses);

                    argClasses = new Class[] {int.class, int.class, String.class, int.class,
                                              int.class, int.class, int[].class};
                    sperfHintRenewFunc = sPerfClass.getMethod("perfHintRenew", argClasses);

// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2022-01-09: Core: Perf: Added support for app type in launch hint
                    try {
                        argClasses = new Class[] {};
                        sPerfGetPerfHalVerFunc = sPerfClass.getMethod("perfGetHalVer", argClasses);

                    } catch (Exception e) {
                        Log.i(TAG, "BoostFramework() : Exception_1 = perfGetHalVer not supported");
                        sPerfGetPerfHalVerFunc = null;
                    }

// QTI_END: 2022-01-09: Core: Perf: Added support for app type in launch hint
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                    try {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-05-30: Core: Perf: Change for AGPE
                        argClasses = new Class[] {int.class, int.class, String.class, int.class, String.class};
// QTI_END: 2019-05-30: Core: Perf: Change for AGPE
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                        sUXEngineEvents =  sPerfClass.getDeclaredMethod("perfUXEngine_events",
                                                                          argClasses);

                        argClasses = new Class[] {int.class};
                        sUXEngineTrigger =  sPerfClass.getDeclaredMethod("perfUXEngine_trigger",
                                                                           argClasses);
                    } catch (Exception e) {
                        Log.i(TAG, "BoostFramework() : Exception_4 = PreferredApps not supported");
                    }

// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
                    sIsLoaded = true;
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
                }
                catch(Exception e) {
                    Log.e(TAG,"BoostFramework() : Exception_1 = " + e);
                }
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                // Load UXE Class now Adding new try/catch block to avoid
                // any interference with Qperformance
                try {
                    sUxPerfClass = Class.forName(UXPERFORMANCE_CLASS);

                    Class[] argUxClasses = new Class[] {int.class, String.class, String.class};
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-05-30: Core: Perf: Change for AGPE
                    sUxIOPStart = sUxPerfClass.getDeclaredMethod("perfIOPrefetchStart", argUxClasses);
// QTI_END: 2019-05-30: Core: Perf: Change for AGPE
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.

                    sUxIsLoaded = true;
                }
                catch(Exception e) {
                    Log.e(TAG,"BoostFramework() Ux Perf: Exception = " + e);
                }
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
            }
        }
    }

/** @hide */
    public int perfLockAcquire(int duration, int... list) {
        int ret = -1;
        try {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
            if (sAcquireFunc != null) {
                Object retVal = sAcquireFunc.invoke(mPerf, duration, list);
                ret = (int)retVal;
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfLockRelease() {
        int ret = -1;
        try {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
            if (sReleaseFunc != null) {
                Object retVal = sReleaseFunc.invoke(mPerf);
                ret = (int)retVal;
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
/** @hide */
    public int perfHintRelease() {
        int ret = -1;
        try {
            if (sPerfHintRelFunc != null) {
                Object retVal = sPerfHintRelFunc.invoke(mPerf);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

// QTI_END: 2024-10-24: Performance: Added perf hint release support am: 9f4fce0d31
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
/** @hide */
    public int perfLockReleaseHandler(int handle) {
        int ret = -1;
        try {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
            if (sReleaseHandlerFunc != null) {
                Object retVal = sReleaseHandlerFunc.invoke(mPerf, handle);
                ret = (int)retVal;
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfHint(int hint, String userDataStr) {
        return perfHint(hint, userDataStr, -1, -1);
    }

/** @hide */
    public int perfHint(int hint, String userDataStr, int userData) {
        return perfHint(hint, userDataStr, userData, -1);
    }

/** @hide */
    public int perfHint(int hint, String userDataStr, int userData1, int userData2) {
        int ret = -1;
        try {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-03-21: Core: add perf_service into system_process.
            if (sPerfHintFunc != null) {
                Object retVal = sPerfHintFunc.invoke(mPerf, hint, userDataStr, userData1, userData2);
                ret = (int)retVal;
            }
// QTI_END: 2018-03-21: Core: add perf_service into system_process.
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
        } catch(Exception e) {
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2022-01-09: Core: Perf: Added support for app type in launch hint
/** @hide */
    public double getPerfHalVersion() {
        double retVal = PERF_HAL_V22;
        try {
            if (sPerfGetPerfHalVerFunc != null) {
                Object ret = sPerfGetPerfHalVerFunc.invoke(mPerf);
                retVal = (double)ret;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return retVal;
    }

// QTI_END: 2022-01-09: Core: Perf: Added support for app type in launch hint
// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
/** @hide */
// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
    public int perfGetFeedback(int req, String pkg_name) {
// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
        int ret = -1;
        try {
            if (sFeedbackFunc != null) {
// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
                Object retVal = sFeedbackFunc.invoke(mPerf, req, pkg_name);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfGetFeedbackExtn(int req, String pkg_name, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sFeedbackFuncExtn != null) {
                Object retVal = sFeedbackFuncExtn.invoke(mPerf, req, pkg_name, numArgs, list);
// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2018-11-10: Core: Add perfGetFeedback api support from framework
                ret = (int)retVal;
            }
        } catch(Exception e) {
// QTI_END: 2018-11-10: Core: Add perfGetFeedback api support from framework
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.

/** @hide */
    public int perfIOPrefetchStart(int pid, String pkgName, String codePath) {
        int ret = -1;
        try {
            Object retVal = sIOPStart.invoke(mPerf, pid, pkgName, codePath);
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        try {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-05-30: Core: Perf: Change for AGPE
             Object retVal = sUxIOPStart.invoke(mUxPerf, pid, pkgName, codePath);
             ret = (int) retVal;
         } catch (Exception e) {
             Log.e(TAG, "Ux Perf Exception " + e);
         }
// QTI_END: 2019-05-30: Core: Perf: Change for AGPE
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.

        return ret;
    }

/** @hide */
    public int perfIOPrefetchStop() {
        int ret = -1;
        try {
            Object retVal = sIOPStop.invoke(mPerf);
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfUXEngine_events(int opcode, int pid, String pkgName, int lat) {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-05-30: Core: Perf: Change for AGPE
        return perfUXEngine_events(opcode, pid, pkgName, lat, null);
     }

/** @hide */
    public int perfUXEngine_events(int opcode, int pid, String pkgName, int lat, String codePath) {
// QTI_END: 2019-05-30: Core: Perf: Change for AGPE
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
        int ret = -1;
        try {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-04-15: Core: Moving property to iop hal
            if (sUXEngineEvents == null) {
// QTI_END: 2019-04-15: Core: Moving property to iop hal
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                return ret;
            }
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-05-30: Core: Perf: Change for AGPE

            Object retVal = sUXEngineEvents.invoke(mPerf, opcode, pid, pkgName, lat,codePath);
// QTI_END: 2019-05-30: Core: Perf: Change for AGPE
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
            ret = (int) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }


/** @hide */
    public String perfUXEngine_trigger(int opcode) {
        String ret = null;
        try {
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
// QTI_BEGIN: 2019-04-15: Core: Moving property to iop hal
            if (sUXEngineTrigger == null) {
// QTI_END: 2019-04-15: Core: Moving property to iop hal
// QTI_BEGIN: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.
                return ret;
            }
            Object retVal = sUXEngineTrigger.invoke(mPerf, opcode);
            ret = (String) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }
// QTI_END: 2018-10-31: Core: IOP/UXE: This change is related to IOP and UXE Feature.

// QTI_BEGIN: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.
/** @hide */
    public String perfSyncRequest(int opcode) {
        String ret = null;
        try {
            if (sPerfSyncRequest == null) {
                return ret;
            }
            Object retVal = sPerfSyncRequest.invoke(mPerf, opcode);
            ret = (String) retVal;
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return ret;
    }
// QTI_END: 2022-06-06: Core: BoostFramework : Add new API and make sure IOP APIs are compatible for T.

// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
/** @hide */
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework
    public String perfGetProp(String prop_name, String def_val) {
        String ret = "";
        try {
            if (sPerfGetPropFunc != null) {
                Object retVal = sPerfGetPropFunc.invoke(mPerf, prop_name, def_val);
                ret = (String)retVal;
            }else {
                ret = def_val;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }
// QTI_END: 2019-01-29: Core: framework: Adding support for perf get prop in Boostframework

// QTI_BEGIN: 2020-08-26: Core: perf: Added new API support
/** @hide */
    public int perfLockAcqAndRelease(int handle, int duration, int numArgs,int reserveNumArgs, int... list) {
        int ret = -1;
        try {
            if (sAcqAndReleaseFunc != null) {
                Object retVal = sAcqAndReleaseFunc.invoke(mPerf, handle, duration, numArgs, reserveNumArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

// QTI_END: 2020-08-26: Core: perf: Added new API support
// QTI_BEGIN: 2021-07-12: Core: perf: Added support for new API.
/** @hide */
    public void perfEvent(int eventId, String pkg_name) {
        perfEvent(eventId, pkg_name, 0);
    }

/** @hide */
    public void perfEvent(int eventId, String pkg_name, int numArgs, int... list) {
        try {
            if (sPerfEventFunc != null) {
                sPerfEventFunc.invoke(mPerf, eventId, pkg_name, numArgs, list);
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name) {
        return perfHintAcqRel(handle, hint, pkg_name, -1, -1, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration) {
        return perfHintAcqRel(handle, hint, pkg_name, duration, -1, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration, int hintType) {
        return perfHintAcqRel(handle, hint, pkg_name, duration, hintType, 0);
    }

/** @hide */
    public int perfHintAcqRel(int handle, int hint, String pkg_name, int duration,
                              int hintType, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sperfHintAcqRelFunc != null) {
                Object retVal = sperfHintAcqRelFunc.invoke(mPerf,handle, hint, pkg_name,
                                                           duration, hintType, numArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name) {
        return perfHintRenew(handle, hint, pkg_name, -1, -1, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration) {
        return perfHintRenew(handle, hint, pkg_name, duration, -1, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration, int hintType) {
        return perfHintRenew(handle, hint, pkg_name, duration, hintType, 0);
    }

/** @hide */
    public int perfHintRenew(int handle, int hint, String pkg_name, int duration,
                             int hintType, int numArgs, int... list) {
        int ret = -1;
        try {
            if (sperfHintRenewFunc != null) {
                Object retVal = sperfHintRenewFunc.invoke(mPerf,handle, hint, pkg_name,
                                                          duration, hintType, numArgs, list);
                ret = (int)retVal;
            }
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

// QTI_END: 2021-07-12: Core: perf: Added support for new API.
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
    /** @hide */
    public static class ScrollOptimizer {
        /** @hide */
        public static final int FLING_START = 1;
        /** @hide */
        public static final int FLING_END = 0;
        private static final String SCROLL_OPT_PROP = "ro.vendor.perf.scroll_opt";
        private static final String QXPERFORMANCE_JAR =
                "/system/framework/QXPerformance.jar";
        private static final String SCROLL_OPT_CLASS =
                "com.qualcomm.qti.QXPerformance.ScrollOptimizer";
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
        private static boolean sScrollOptProp = false;
// QTI_END: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
        private static boolean sScrollOptEnable = false;
        private static boolean sQXIsLoaded = false;
        private static Class<?> sQXPerfClass = null;
        private static Method sSetFrameInterval = null;
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
        private static Method sDisableOptimizer = null;
// QTI_END: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
// QTI_BEGIN: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
        private static Method sSetBLASTBufferQueue = null;
// QTI_END: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
        private static Method sSetMotionType = null;
        private static Method sSetVsyncTime = null;
        private static Method sSetUITaskStatus = null;
        private static Method sSetFlingFlag = null;
        private static Method sShouldUseVsync = null;
        private static Method sGetFrameDelay = null;
        private static Method sGetAdjustedAnimationClock = null;

        private static void initQXPerfFuncs() {
            if (sQXIsLoaded) return;

            try {
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
                sScrollOptProp = SystemProperties.getBoolean(SCROLL_OPT_PROP, false);
                if (!sScrollOptProp) {
                    sScrollOptEnable = false;
// QTI_END: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
                    sQXIsLoaded = true;
                    return;
                }

                PathClassLoader qXPerfClassLoader = new PathClassLoader(
                        QXPERFORMANCE_JAR, ClassLoader.getSystemClassLoader());
                sQXPerfClass = qXPerfClassLoader.loadClass(SCROLL_OPT_CLASS);
                Class[] argClasses = new Class[]{long.class};
                sSetFrameInterval = sQXPerfClass.getMethod(
                        "setFrameInterval", argClasses);

// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
                argClasses = new Class[]{boolean.class};
                sDisableOptimizer = sQXPerfClass.getMethod("disableOptimizer", argClasses);

// QTI_END: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
// QTI_BEGIN: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
                argClasses = new Class[]{BLASTBufferQueue.class};
                sSetBLASTBufferQueue = sQXPerfClass.getMethod("setBLASTBufferQueue", argClasses);
// QTI_END: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part

                argClasses = new Class[]{int.class};
                sSetMotionType = sQXPerfClass.getMethod("setMotionType", argClasses);

                argClasses = new Class[]{long.class};
                sSetVsyncTime = sQXPerfClass.getMethod("setVsyncTime", argClasses);

                argClasses = new Class[]{boolean.class};
                sSetUITaskStatus = sQXPerfClass.getMethod("setUITaskStatus", argClasses);

                argClasses = new Class[]{int.class};
                sSetFlingFlag = sQXPerfClass.getMethod("setFlingFlag", argClasses);

                sShouldUseVsync = sQXPerfClass.getMethod("shouldUseVsync");

                argClasses = new Class[]{long.class};
                sGetFrameDelay = sQXPerfClass.getMethod("getFrameDelay", argClasses);

                argClasses = new Class[]{long.class};
                sGetAdjustedAnimationClock = sQXPerfClass.getMethod(
                        "getAdjustedAnimationClock", argClasses);
            } catch (Exception e) {
                Log.e(TAG, "initQXPerfFuncs failed");
                e.printStackTrace();
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
            } finally {
                // If frameworks and perf changes don't match(may not built together)
                // or other exception, need to set sQXIsLoaded as true to avoid retry.
                sQXIsLoaded = true;
// QTI_END: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
            }
        }

        /** @hide */
        public static void setFrameInterval(long frameIntervalNanos) {
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2022-07-25: Core: Reduce redundant calls of frame interval update
            if (sQXIsLoaded) {
                if (sScrollOptEnable && sSetFrameInterval != null) {
                    try {
                        sSetFrameInterval.invoke(null, frameIntervalNanos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return;
            }
// QTI_END: 2022-07-25: Core: Reduce redundant calls of frame interval update
// QTI_BEGIN: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
            Thread initThread = new Thread(new Runnable() {
                @Override
                public void run() {
// QTI_END: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
// QTI_BEGIN: 2022-11-22: Core: perf: Add lock protection for initialization in ScrollOptimizer
                    synchronized(ScrollOptimizer.class) {
                        try {
                            initQXPerfFuncs();
                            if (sScrollOptProp && sSetFrameInterval != null) {
                                sSetFrameInterval.invoke(null, frameIntervalNanos);
                                sScrollOptEnable = true;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to run initThread.");
                            e.printStackTrace();
// QTI_END: 2022-11-22: Core: perf: Add lock protection for initialization in ScrollOptimizer
// QTI_BEGIN: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
                        }
                    }
// QTI_END: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
                }
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
            });
            initThread.start();
// QTI_END: 2020-12-14: Core: Fix StrictMode violation and add filter for scroll mode
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
        }

// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
        /** @hide */
        public static void disableOptimizer(boolean disabled) {
            if (sScrollOptEnable && sDisableOptimizer != null) {
                try {
                    sDisableOptimizer.invoke(null, disabled);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

// QTI_END: 2022-11-22: Core: Filter multi-layer cases for pre-rendering
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
        /** @hide */
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
        public static void setBLASTBufferQueue(BLASTBufferQueue blastBufferQueue) {
            if (sScrollOptEnable && sSetBLASTBufferQueue != null) {
// QTI_END: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
                try {
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
                    sSetBLASTBufferQueue.invoke(null, blastBufferQueue);
// QTI_END: 2021-05-11: Core: refactor pre-rendering feature for BLASTBufferQueue
// QTI_BEGIN: 2020-06-15: Core: Pre-rendering AOSP part
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setMotionType(int eventType) {
            if (sScrollOptEnable && sSetMotionType != null) {
                try {
                    sSetMotionType.invoke(null, eventType);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setVsyncTime(long vsyncTimeNanos) {
            if (sScrollOptEnable && sSetVsyncTime != null) {
                try {
                    sSetVsyncTime.invoke(null, vsyncTimeNanos);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setUITaskStatus(boolean running) {
            if (sScrollOptEnable && sSetUITaskStatus != null) {
                try {
                    sSetUITaskStatus.invoke(null, running);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static void setFlingFlag(int flag) {
            if (sScrollOptEnable && sSetFlingFlag != null) {
                try {
                    sSetFlingFlag.invoke(null, flag);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /** @hide */
        public static boolean shouldUseVsync(boolean defaultVsyncFlag) {
            boolean useVsync = defaultVsyncFlag;
            if (sScrollOptEnable && sShouldUseVsync != null) {
                try {
                    Object retVal = sShouldUseVsync.invoke(null);
                    useVsync = (boolean)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return useVsync;
        }

        /** @hide */
        public static long getFrameDelay(long defaultDelay, long lastFrameTimeNanos) {
            long frameDelay = defaultDelay;
            if (sScrollOptEnable && sGetFrameDelay != null) {
                try {
                    Object retVal = sGetFrameDelay.invoke(null, lastFrameTimeNanos);
                    frameDelay = (long)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return frameDelay;
        }

        /** @hide */
        public static long getAdjustedAnimationClock(long frameTimeNanos) {
            long newFrameTimeNanos = frameTimeNanos;
            if (sScrollOptEnable && sGetAdjustedAnimationClock != null) {
                try {
                    Object retVal = sGetAdjustedAnimationClock.invoke(null,
                            frameTimeNanos);
                    newFrameTimeNanos = (long)retVal;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return newFrameTimeNanos;
        }
    }
// QTI_END: 2020-06-15: Core: Pre-rendering AOSP part
// QTI_BEGIN: 2025-03-24: Core: Perf: UI perf mode optimization

    public boolean isUiPerfEnabled(Context context, String pkgName) {
        if (pkgName == null || pkgName.isEmpty()) {
            return false;
        }
        uiPerfInit(context);
        if (SystemProperties.getBoolean(UI_PERF_PROP, false) && mUIPerfProcs != null) {
            for (int i = 0; i < mUIPerfProcs.length; i++) {
                if (pkgName.equals(mUIPerfProcs[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean isLegacyUiPerfEnabled(Context context, String pkgName) {
        if (pkgName == null || pkgName.isEmpty()) {
            return false;
        }
        uiPerfInit(context);
        legacyUIPerfPkgsObserver(context);
        if (SystemProperties.getBoolean(UI_PERF_PROP, false) && mLegacyUIPerfProcs != null) {
            for (int i = 0; i < mLegacyUIPerfProcs.length; i++) {
                if (pkgName.equals(mLegacyUIPerfProcs[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized int getLegacyUiPerfHint(Context context, String pkgName) {
        int hint = -1;
        if (pkgName == null || pkgName.isEmpty()) {
            return hint;
        }
        uiPerfInit(context);
        legacyUIPerfPkgsObserver(context);
        if (SystemProperties.getBoolean(UI_PERF_PROP, false)) {
            if (mLegacyUIPerfProcs != null) {
                for (int i = 0; i < mLegacyUIPerfProcs.length; i++) {
                    if (pkgName.equals(mLegacyUIPerfProcs[i])) {
                        hint = VENDOR_HINT_PERFORMANCE_MODE;
                        return hint;
                    }
                }
            }
        }
        return hint;
    }

    public int getUiPerfHint(Context context, String activityName) {
        int hint = -1;
        if (activityName == null || activityName.isEmpty()) {
            return hint;
        }
        uiPerfInit(context);
        if (SystemProperties.getBoolean(UI_PERF_PROP, false)) {
            if (mUIPerfGpuActivities != null) {
                for (int i = 0; i < mUIPerfGpuActivities.length; i++) {
                    if (activityName.equals(mUIPerfGpuActivities[i])) {
                        hint = VENDOR_HINT_SCENARIO_GPU;
                        return hint;
                    }
                }
            }
            if (mUIPerfCpuActivities != null) {
                for (int i = 0; i < mUIPerfCpuActivities.length; i++) {
                    if (activityName.equals(mUIPerfCpuActivities[i])) {
                        hint = VENDOR_HINT_SCENARIO_CPU;
                        return hint;
                    }
                }
            }
            if (mUIPerfCpuGpuActivities != null) {
                for (int i = 0; i < mUIPerfCpuGpuActivities.length; i++) {
                    if (activityName.equals(mUIPerfCpuGpuActivities[i])) {
                        hint = VENDOR_HINT_SCENARIO_CPU_GPU;
                        return hint;
                    }
                }
            }
            if (mUIPerfCpuAggressive != null) {
                for (int i = 0; i < mUIPerfCpuAggressive.length; i++) {
                    if (activityName.equals(mUIPerfCpuAggressive[i])) {
                        hint = VENDOR_HINT_SCENARIO_CPU_AGGRESSIVE;
                        return hint;
                    }
                }
            }
        }
        return hint;
    }

    private void uiPerfInit(Context context) {
        if (SystemProperties.getBoolean(UI_PERF_PROP, false) && context != null
                                                             && !mUiPerfInited) {
            String str = Settings.Global.getString(context.getContentResolver(),
                             KEY_PKGS);
            mUIPerfProcs = str.split(";");
            str = Settings.Global.getString(context.getContentResolver(),
                             KEY_GPU_PREFER);
            mUIPerfGpuActivities = str.split(";");
            str = Settings.Global.getString(context.getContentResolver(),
                             KEY_CPU_PREFER);
            mUIPerfCpuActivities = str.split(";");
            str = Settings.Global.getString(context.getContentResolver(),
                             KEY_CPU_GPU);
            mUIPerfCpuGpuActivities = str.split(";");
            str = Settings.Global.getString(context.getContentResolver(),
                             KEY_CPU_AGGRESSIVE);
            mUIPerfCpuAggressive = str.split(";");
            str = Settings.Global.getString(context.getContentResolver(),
                             KEY_LEGACY_UI_PERF_PKGS);
            mLegacyUIPerfProcs = str.split(";");
            mUiPerfInited = true;
        }
    }

    private void legacyUIPerfPkgsObserver(Context context) {
        if (context != null && mContentObserver == null && mUiPerfInited) {
            mContentObserver = new UiPerfProcsObserver(context);
            mContentObserver.register();
        }
    }

    private class UiPerfProcsObserver extends ContentObserver {
        private Context mContext = null;
        UiPerfProcsObserver(Context context) {
            super(null);
            mContext = context;
        }

        void register() {
            if (mContext != null) {
                mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                        KEY_LEGACY_UI_PERF_PKGS), false, this);
            }
        }

        public void onChange(boolean selfChange, Uri uri) {
            if (Settings.Global.getUriFor(KEY_LEGACY_UI_PERF_PKGS).equals(uri)) {
                synchronized (BoostFramework.this) {
                    String str = Settings.Global.getString(mContext.getContentResolver(),
                                            KEY_LEGACY_UI_PERF_PKGS);
                    mLegacyUIPerfProcs = str.split(";");
                }
            }
        }
    }
// QTI_END: 2025-03-24: Core: Perf: UI perf mode optimization
// QTI_BEGIN: 2018-02-20: Performance: BoostFramework: To Enhance performance.
};
// QTI_END: 2018-02-20: Performance: BoostFramework: To Enhance performance.
