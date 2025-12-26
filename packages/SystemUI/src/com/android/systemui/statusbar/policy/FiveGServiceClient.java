// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
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

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
/*
  Changes from Qualcomm Innovation Center are provided under the following license:

// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
  Copyright (c) 2023-2024 Qualcomm Innovation Center, Inc. All rights reserved.
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
  SPDX-License-Identifier: BSD-3-Clause-Clear
*/

// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
package com.android.systemui.statusbar.policy;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
import android.net.Uri;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-03-24: Android_UI: SystemUI: Fix 5G icon not restore after phone is killed
import android.os.DeadObjectException;
// QTI_END: 2019-03-24: Android_UI: SystemUI: Fix 5G icon not restore after phone is killed
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
import com.google.android.collect.Lists;
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
import com.android.internal.annotations.VisibleForTesting;

// QTI_END: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
import java.lang.Exception;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
import java.util.ArrayList;
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
import java.util.concurrent.CopyOnWriteArrayList;
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
import java.lang.ref.WeakReference;
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
import javax.inject.Inject;
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon

// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
import com.android.keyguard.KeyguardUpdateMonitorCallback;
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
import com.android.settingslib.mobile.TelephonyIcons;
import com.android.settingslib.SignalIcon.MobileIconGroup;
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
import com.android.settingslib.R;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
import com.android.systemui.dagger.SysUISingleton;
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon

// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
import com.qti.extphone.Client;
import com.qti.extphone.ExtTelephonyManager;
import com.qti.extphone.IExtPhoneCallback;
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
import com.qti.extphone.ExtPhoneCallbackListener;
// QTI_END: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
import com.qti.extphone.NrIcon;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
import com.qti.extphone.NrIconType;
import com.qti.extphone.Status;
import com.qti.extphone.ServiceCallback;
import com.qti.extphone.Token;

// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
@SysUISingleton
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
public class FiveGServiceClient {
    private static final String TAG = "FiveGServiceClient";
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG)||true;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    private static final int MESSAGE_REBIND = 1024;
    private static final int MESSAGE_REINIT = MESSAGE_REBIND+1;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
    private static final int MESSAGE_NOTIFIY_MONITOR_CALLBACK = MESSAGE_REBIND+2;
// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    private static final int MAX_RETRY = 4;
    private static final int DELAY_MILLISECOND = 3000;
    private static final int DELAY_INCREMENT = 2000;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information

// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
    private static FiveGServiceClient sInstance;
    private final ArrayList<WeakReference<KeyguardUpdateMonitorCallback>>
            mKeyguardUpdateMonitorCallbacks = Lists.newArrayList();
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
    @VisibleForTesting
// QTI_END: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
    final SparseArray<CopyOnWriteArrayList<IFiveGStateListener>> mStatesListeners =
            new SparseArray<>();
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    private final SparseArray<FiveGServiceState> mCurrentServiceStates = new SparseArray<>();
    private final SparseArray<FiveGServiceState> mLastServiceStates = new SparseArray<>();

    private Context mContext;
    private boolean mServiceConnected;
    private String mPackageName;
    private Client mClient;
    private int mInitRetryTimes = 0;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
    private ExtTelephonyManager mExtTelephonyManager;
    private boolean mIsConnectInProgress = false;
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information

    public static class FiveGServiceState{
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
        private static final String COL_NR_ICON_TYPE = "NrIconType";
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
        private int mNrIconType;
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        private boolean mIs6Rx;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
        private MobileIconGroup mIconGroup;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information

        public FiveGServiceState(){
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
            mNrIconType = NrIconType.INVALID;
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
            mIs6Rx = false;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            mIconGroup = TelephonyIcons.UNKNOWN;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
        @VisibleForTesting
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        public FiveGServiceState(int nrIconType, boolean is6Rx, Context context) {
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
            mNrIconType = nrIconType;
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
            mIs6Rx = is6Rx;
            mIconGroup = getNrIconGroup(nrIconType, is6Rx, context);
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
        }

// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
        public boolean isNrIconTypeValid() {
            return mNrIconType != NrIconType.INVALID && mNrIconType != NrIconType.TYPE_NONE;
// QTI_END: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
        @VisibleForTesting
// QTI_END: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
        public MobileIconGroup getIconGroup() {
            return mIconGroup;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
        @VisibleForTesting
// QTI_END: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
        public int getNrIconType() {
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
            return mNrIconType;
        }

// QTI_END: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        @VisibleForTesting
        public boolean getIs6Rx() {
            return mIs6Rx;
        }

// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        public void copyFrom(FiveGServiceState state) {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
            this.mIconGroup = state.mIconGroup;
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
            this.mNrIconType = state.mNrIconType;
// QTI_END: 2019-03-11: Android_UI: SystemUI: Upgrade the logic of 5G NSA icons
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
            this.mIs6Rx = state.mIs6Rx;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
        public boolean equals(FiveGServiceState state) {
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2020-07-09: Android_UI: SystemUI: Remove deprecated code
            return this.mIconGroup == state.mIconGroup
// QTI_END: 2020-07-09: Android_UI: SystemUI: Remove deprecated code
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
                    && this.mNrIconType == state.mNrIconType
                    && this.mIs6Rx == state.mIs6Rx;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
        }
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state

// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2020-07-09: Android_UI: SystemUI: Remove deprecated code
            builder.append("mNrIconType=").append(mNrIconType).append(", ").
// QTI_END: 2020-07-09: Android_UI: SystemUI: Remove deprecated code
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
                    append("is6Rx=").append(mIs6Rx).append(", ").
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
                    append("mIconGroup=").append(mIconGroup);
// QTI_END: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information

            return builder.toString();
        }
    }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
    @Inject
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support side car 5G icon
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    public FiveGServiceClient(Context context) {
        mContext = context;
        mPackageName = mContext.getPackageName();
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
        if (mExtTelephonyManager == null) {
            mExtTelephonyManager = ExtTelephonyManager.getInstance(mContext);
        }
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
    public static FiveGServiceClient getInstance(Context context) {
        if ( sInstance == null ) {
            sInstance = new FiveGServiceClient(context);
        }

        return sInstance;
    }

    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        for (int i = 0; i < mKeyguardUpdateMonitorCallbacks.size(); i++) {
            if (mKeyguardUpdateMonitorCallbacks.get(i).get() == callback) {
                return;
            }
        }
        mKeyguardUpdateMonitorCallbacks.add(new WeakReference<>(callback));
    }

    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        mKeyguardUpdateMonitorCallbacks.removeIf(el -> el.get() == callback);
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
    }

// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    public void registerListener(int phoneId, IFiveGStateListener listener) {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
        Log.d(TAG, "registerListener phoneId=" + phoneId + "  listener: " + listener);
// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
        resetState(phoneId);
// QTI_END: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
        CopyOnWriteArrayList<IFiveGStateListener> statesListenersForPhone =
                mStatesListeners.get(phoneId);
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
        if (statesListenersForPhone == null) {
// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
            statesListenersForPhone = new CopyOnWriteArrayList<>();
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
            mStatesListeners.put(phoneId, statesListenersForPhone);
        }
        statesListenersForPhone.add(listener);

// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        if ( !isServiceConnected() ) {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
            connectService();
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }else{
            initFiveGServiceState(phoneId);
        }
    }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
    private void resetState(int phoneId) {
        Log.d(TAG, "resetState phoneId=" + phoneId);
        FiveGServiceState currentState = getCurrentServiceState(phoneId);
        currentState.mNrIconType = NrIconType.INVALID;
// QTI_END: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        currentState.mIs6Rx = false;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
        currentState.mIconGroup = TelephonyIcons.UNKNOWN;

        FiveGServiceState lastState = getLastServiceState(phoneId);
        lastState.mNrIconType = NrIconType.INVALID;
// QTI_END: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        lastState.mIs6Rx = false;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
        lastState.mIconGroup = TelephonyIcons.UNKNOWN;
    }

// QTI_END: 2021-05-19: Android_UI: SystemUI: Reset the cache state when registering the listener
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
    public void unregisterListener(int phoneId, IFiveGStateListener fiveGStateListener) {
        Log.d(TAG, "unregisterListener phoneId=" + phoneId + " listener: " + fiveGStateListener);
// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
        CopyOnWriteArrayList<IFiveGStateListener> statesListenersForPhone =
                mStatesListeners.get(phoneId);
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
        if (statesListenersForPhone != null) {
            statesListenersForPhone.remove(fiveGStateListener);
            if (statesListenersForPhone.size() == 0) {
                mStatesListeners.remove(phoneId);
                mCurrentServiceStates.remove(phoneId);
                mLastServiceStates.remove(phoneId);
            }
        }
// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    }

    public boolean isServiceConnected() {
        return mServiceConnected;
    }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
    private void connectService() {
        if (!isServiceConnected() && !mIsConnectInProgress) {
            mIsConnectInProgress = true;
            Log.d(TAG, "Connect to ExtTelephony bound service...");
            mExtTelephonyManager.connectService(mServiceCallback);
        }
    }

    private ServiceCallback mServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephony Service connected");
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
            int[] events = new int[] {
// QTI_END: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
                    ExtPhoneCallbackListener.EVENT_ON_NR_ICON_TYPE,
// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
                    ExtPhoneCallbackListener.EVENT_QUERY_NR_ICON_RESPONSE,
                    ExtPhoneCallbackListener.EVENT_ON_NR_ICON_CHANGE,
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
                    ExtPhoneCallbackListener.EVENT_ON_CIWLAN_AVAILABLE};
// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
            mServiceConnected = true;
            mIsConnectInProgress = false;
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
            mClient = mExtTelephonyManager.registerCallbackWithEvents(
                    mPackageName, mExtPhoneCallbackListener, events);
// QTI_END: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
            initFiveGServiceState();
            Log.d(TAG, "Client = " + mClient);
        }
        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephony Service disconnected...");
            if (mServiceConnected) {
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
                mExtTelephonyManager.unregisterCallback(mExtPhoneCallbackListener);
// QTI_END: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
            }
            mServiceConnected = false;
            mClient = null;
            mIsConnectInProgress = false;
            mHandler.sendEmptyMessageDelayed(MESSAGE_REBIND,
                    DELAY_MILLISECOND + DELAY_INCREMENT);
        }
    };

// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
    @VisibleForTesting
// QTI_END: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
// QTI_BEGIN: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
    public FiveGServiceState getCurrentServiceState(int phoneId) {
// QTI_END: 2019-04-29: Android_UI: SystemUI: Display 5G in carrier name for 5G NSA
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        return getServiceState(phoneId, mCurrentServiceStates);
    }

    private FiveGServiceState getLastServiceState(int phoneId) {
        return getServiceState(phoneId, mLastServiceStates);
    }

    private static FiveGServiceState getServiceState(int key,
                                                     SparseArray<FiveGServiceState> array) {
        FiveGServiceState state = array.get(key);
        if ( state == null ) {
            state = new FiveGServiceState();
            array.put(key, state);
        }
        return state;
    }

    private void notifyListenersIfNecessary(int phoneId) {
        FiveGServiceState currentState = getCurrentServiceState(phoneId);
        FiveGServiceState lastState = getLastServiceState(phoneId);
        if ( !currentState.equals(lastState) ) {

            if ( DEBUG ) {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
                Log.d(TAG, "phoneId(" + phoneId + ") Change in state from " + lastState + " \n"+
// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
                        "\tto " + currentState);

            }

            lastState.copyFrom(currentState);
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
            CopyOnWriteArrayList<IFiveGStateListener> statesListenersForPhone =
                    mStatesListeners.get(phoneId);
// QTI_END: 2023-06-05: Android_UI: SystemUI: Fix ConcurrentModificationException
// QTI_BEGIN: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
            if (statesListenersForPhone != null) {
                for (IFiveGStateListener listener: statesListenersForPhone) {
                    if (listener != null) {
                        listener.onStateChanged(currentState);
                    }
                }
// QTI_END: 2023-04-27: Android_UI: SystemUI: Fix Qs tile network type not correct
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
            }
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
            mHandler.sendEmptyMessage(MESSAGE_NOTIFIY_MONITOR_CALLBACK);
// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }
    }

    private void initFiveGServiceState() {
        Log.d(TAG, "initFiveGServiceState size=" + mStatesListeners.size());
        for( int i=0; i < mStatesListeners.size(); ++i ) {
            int phoneId = mStatesListeners.keyAt(i);
            initFiveGServiceState(phoneId);
        }
    }

    private void initFiveGServiceState(int phoneId) {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
        Log.d(TAG, "mServiceConnected=" + mServiceConnected + " mClient=" + mClient);
        if ( mServiceConnected && mClient != null) {
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
            Log.d(TAG, "query 5G service state for phoneId " + phoneId);
            try {
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
                Token token = mExtTelephonyManager.queryNrIcon(phoneId, mClient);
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
                Log.d(TAG, "queryNrIconType result:" + token);
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
            } catch (Exception e) {
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
                Log.d(TAG, "initFiveGServiceState: Exception = " + e);
                if ( mInitRetryTimes < MAX_RETRY && !mHandler.hasMessages(MESSAGE_REINIT) ) {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REINIT,
                            DELAY_MILLISECOND + mInitRetryTimes*DELAY_INCREMENT);
                    mInitRetryTimes +=1;
                }
            }
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue

            boolean ciWlanAvailable = mExtTelephonyManager.isCiwlanAvailable(phoneId);
            try {
                mExtPhoneCallbackListener.onCiwlanAvailable(phoneId, ciWlanAvailable);
            } catch (RemoteException e) {
                Log.d(TAG, "onCiwlanAvailable: Exception = " + e);
            }
// QTI_END: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
        }
    }

// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
    @VisibleForTesting
// QTI_END: 2019-07-16: Android_UI: SystemUI: Algin with Android SA solution
// QTI_BEGIN: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue
    void update5GIcon(FiveGServiceState state) {
// QTI_END: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        state.mIconGroup = getNrIconGroup(state.mNrIconType, state.mIs6Rx, mContext);
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
    }

// QTI_END: 2018-12-18: Android_UI: SystemUI: Display 5G Basic or 5G UWB icon per 5G service state
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
    private static MobileIconGroup getNrIconGroup(int nrIconType , boolean is6Rx, Context context) {
        boolean show6RxConfig = context.getResources().getBoolean(R.bool.config_display_6Rx);
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
        boolean show5Ga = context.getResources().getBoolean(R.bool.config_display_5g_a);
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        Log.d(TAG, "getNrIconGroup nrIconType:" + nrIconType +
            "; is6Rx:" + is6Rx + "; show6RxConfig:" + show6RxConfig);
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
        MobileIconGroup iconGroup = TelephonyIcons.UNKNOWN;
        switch (nrIconType){
            case NrIconType.TYPE_5G_BASIC:
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
                iconGroup = (show6RxConfig && is6Rx) ?
                        TelephonyIcons.FIVE_G_BASIC_6RX : TelephonyIcons.FIVE_G_BASIC;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
                break;
            case NrIconType.TYPE_5G_UWB:
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
                if (show5Ga) {
                    iconGroup = TelephonyIcons.FIVE_G_A;
                } else {
                    iconGroup = (show6RxConfig && is6Rx) ?
                            TelephonyIcons.FIVE_G_UWB_6RX : TelephonyIcons.FIVE_G_UWB;
                }
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
                break;
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
            case NrIconType.TYPE_5G_PLUS_PLUS:
                iconGroup = (show6RxConfig && is6Rx) ?
                        TelephonyIcons.FIVE_G_PLUS_PLUS_6RX : TelephonyIcons.FIVE_G_PLUS_PLUS;
// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2023-12-17: Data: SystemUI: Enhanced 5g icon
                break;
// QTI_END: 2023-12-17: Data: SystemUI: Enhanced 5g icon
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
        }
        return iconGroup;
    }

// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
    private void notifyMonitorCallback() {
        for (int i = 0; i < mKeyguardUpdateMonitorCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback cb = mKeyguardUpdateMonitorCallbacks.get(i).get();
            if (cb != null) {
                cb.onRefreshCarrierInfo();
            }
        }
    }

// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            int what = msg.what;
            switch ( msg.what ) {
                case MESSAGE_REBIND:
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
                    connectService();
// QTI_END: 2021-02-09: Telephony: Change to move IExtTelephony to IExtPhone
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
                    break;

                case MESSAGE_REINIT:
                    initFiveGServiceState();
                    break;
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE

                case MESSAGE_NOTIFIY_MONITOR_CALLBACK:
                    notifyMonitorCallback();
                    break;
// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
            }

        }
    };


// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
    @VisibleForTesting
// QTI_END: 2019-01-24: Android_UI: SystemUI: Add unit test for 5G
// QTI_BEGIN: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface
    protected ExtPhoneCallbackListener mExtPhoneCallbackListener = new ExtPhoneCallbackListener() {
// QTI_END: 2023-01-09: Telephony: FR84002: Re-design ExtTelephonyManager interface

// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
        @Override
        public void onNrIconType(int slotId, Token token, Status status, NrIconType
                nrIconType) throws RemoteException {
            Log.d(TAG,
                    "onNrIconType: slotId = " + slotId + " token = " + token + " " + "status"
                            + status + " NrIconType = " + nrIconType);
            if (status.get() == Status.SUCCESS) {
                FiveGServiceState state = getCurrentServiceState(slotId);
                state.mNrIconType = nrIconType.get();
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
// QTI_BEGIN: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue
                update5GIcon(state);
// QTI_END: 2024-04-19: Android_UI: SystemUI: Fix FiveGStateListener registration failure issue
// QTI_BEGIN: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType
                notifyListenersIfNecessary(slotId);
            }
        }
// QTI_END: 2019-03-11: Android_UI: SystemUI: Change 5G icons by NrIconType

// QTI_BEGIN: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
        @Override
        public void onNrIconResponse(int slotId, Token token, Status status, NrIcon
                icon) throws RemoteException {
            Log.d(TAG,
                    "onNrIconResponse: slotId = " + slotId + " token = " + token + " " + "status"
                            + status + " NrIcon = " + icon);
            if (status.get() == Status.SUCCESS) {
                FiveGServiceState state = getCurrentServiceState(slotId);
                state.mNrIconType = icon.getType();
                state.mIs6Rx = icon.getRxCount() > 0;
                update5GIcon(state);
                notifyListenersIfNecessary(slotId);
            }
        }

        @Override
        public void onNrIconChange(int slotId, NrIcon icon) throws RemoteException {
            Log.d(TAG,
                    "onNrIconChange: slotId = " + slotId + " icon = " + icon);
            FiveGServiceState state = getCurrentServiceState(slotId);
            state.mNrIconType = icon.getType();
            state.mIs6Rx = icon.getRxCount() > 0;
            update5GIcon(state);
            notifyListenersIfNecessary(slotId);
        }

// QTI_END: 2024-05-21: Android_UI: SystemUI: Add 6Rx icons support for NrIcons
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
        @Override
        public void onCiwlanAvailable(int slotId, boolean available)  throws RemoteException {
            Log.d(TAG,
                    "onCiwlanAvailable: slotId = " + slotId + " available = " + available);
            CopyOnWriteArrayList<IFiveGStateListener> statesListenersForPhone =
                    mStatesListeners.get(slotId);
            if (statesListenersForPhone != null) {
                for (IFiveGStateListener listener: statesListenersForPhone) {
                    if (listener != null) {
                        listener.onCiwlanAvailableChanged(available);
                    }
                }
            }
        }
// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    };

    public interface IFiveGStateListener {
        public void onStateChanged(FiveGServiceState state);
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
        public default void onCiwlanAvailableChanged(boolean available) {}
// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
// QTI_BEGIN: 2018-07-10: Android_UI: SystemUI: Display 5G information
    }
}
// QTI_END: 2018-07-10: Android_UI: SystemUI: Display 5G information
