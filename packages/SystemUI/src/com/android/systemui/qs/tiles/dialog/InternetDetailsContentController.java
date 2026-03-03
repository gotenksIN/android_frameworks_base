/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
/**
 * Changes from Qualcomm Innovation Center are provided under the following license:
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-04-23: Android_UI: Fix callback registering issue
 * Copyright (c) 2022, 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// QTI_END: 2024-04-23: Android_UI: Fix callback registering issue
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
package com.android.systemui.qs.tiles.dialog;

import static android.telephony.SubscriptionManager.PROFILE_CLASS_PROVISIONING;

import static com.android.settingslib.mobile.MobileMappings.getIconKey;
import static com.android.settingslib.mobile.MobileMappings.mapIconSets;
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
import static com.android.settingslib.mobile.MobileMappings.toIconKey;
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
import static com.android.settingslib.wifi.WifiUtils.getHotspotIconResource;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_CONNECTED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.AnyThread;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.content.res.Resources;
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
import android.database.ContentObserver;
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
import android.net.Uri;
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import android.os.RemoteException;
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.internal.logging.UiEventLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settingslib.SignalIcon;
import com.android.settingslib.Utils;
import com.android.settingslib.graph.SignalDrawable;
import com.android.settingslib.mobile.MobileMappings;
import com.android.settingslib.mobile.TelephonyIcons;
import com.android.settingslib.net.SignalStrengthUtil;
import com.android.settingslib.wifi.WifiUtils;
import com.android.settingslib.wifi.dpp.WifiDppIntentHelper;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.flags.QsWifiConfig;
import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeDisplayAware;
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.core.NewStatusBarIcons;
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
import com.android.systemui.statusbar.policy.FiveGServiceClient;
import com.android.systemui.statusbar.policy.FiveGServiceClient.FiveGServiceState;
import com.android.systemui.statusbar.policy.FiveGServiceClient.IFiveGStateListener;
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.toast.SystemUIToast;
import com.android.systemui.toast.ToastFactory;
import com.android.systemui.user.data.repository.UserRepository;
import com.android.systemui.util.CarrierConfigTracker;
import com.android.systemui.util.kotlin.JavaAdapterKt;
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
import com.android.systemui.util.CarrierNameCustomization;
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
import com.android.systemui.util.settings.GlobalSettings;
import com.android.wifitrackerlib.HotspotNetworkEntry;
import com.android.wifitrackerlib.MergedCarrierEntry;
import com.android.wifitrackerlib.WifiEntry;

import kotlinx.coroutines.CoroutineScope;
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import com.qti.extphone.ExtTelephonyManager;
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import com.qti.extphone.RadioIconType;
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import com.qti.extphone.ServiceCallback;

// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
import java.util.ArrayList;
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
import java.util.HashMap;
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * Controller for Internet Dialog.
 */
public class InternetDetailsContentController implements AccessPointController.AccessPointCallback {

    private static final String TAG = "InternetDetailsContentController";
    private static final String ACTION_WIFI_SCANNING_SETTINGS =
            "android.settings.WIFI_SCANNING_SETTINGS";
    /**
     * Fragment "key" argument passed thru {@link #SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS}
     */
    private static final String SETTINGS_EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    /**
     * When starting this activity, this extra can also be specified to supply a Bundle of arguments
     * to pass to that fragment when it is instantiated during the initial creation of the activity.
     */
    private static final String SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS =
            ":settings:show_fragment_args";
    private static final String AUTO_DATA_SWITCH_SETTING_R_ID = "auto_data_switch";
    public static final Drawable EMPTY_DRAWABLE = new ColorDrawable(Color.TRANSPARENT);
    public static final int NO_CELL_DATA_TYPE_ICON = 0;
    private static final int SUBTITLE_TEXT_WIFI_IS_OFF = R.string.wifi_is_off;
    private static final int SUBTITLE_TEXT_TAP_A_NETWORK_TO_CONNECT =
            R.string.tap_a_network_to_connect;
    private static final int SUBTITLE_TEXT_UNLOCK_TO_VIEW_NETWORKS =
            R.string.unlock_to_view_networks;
    private static final int SUBTITLE_TEXT_SEARCHING_FOR_NETWORKS =
            R.string.wifi_empty_list_wifi_on;
    private static final int SUBTITLE_TEXT_NON_CARRIER_NETWORK_UNAVAILABLE =
            R.string.non_carrier_network_unavailable;
    private static final int SUBTITLE_TEXT_ALL_CARRIER_NETWORK_UNAVAILABLE =
            R.string.all_network_unavailable;
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final TelephonyDisplayInfo DEFAULT_TELEPHONY_DISPLAY_INFO =
            new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false, false, false);

    static final int MAX_WIFI_ENTRY_COUNT = 3;

    static final int SATELLITE_CONNECTED = 2;
    static final int SATELLITE_STARTED = 1;
    static final int SATELLITE_NOT_STARTED = 0;

// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    private static final String DUAL_DATA_PREFERENCE = "dual_data_preference";
    private static final Uri DUAL_DATA_USER_PREFERENCE = Settings
            .Global.getUriFor(DUAL_DATA_PREFERENCE);
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    private final FeatureFlags mFeatureFlags;

    //Should be accessible only to the main thread.
    @VisibleForTesting
    final Map<Integer, TelephonyDisplayInfo>
            mSubIdTelephonyDisplayInfoMap = new HashMap<>();
    //Should be accessible only to the main thread.
    @VisibleForTesting
    final Map<Integer, TelephonyManager>
            mSubIdTelephonyManagerMap = new HashMap<>();
    // Should be accessible only to the main thread.
    @VisibleForTesting
    final Map<Integer, TelephonyCallback>
            mSubIdTelephonyCallbackMap = new HashMap<>();
    private final ShadeDialogContextInteractor mShadeDialogContextInteractor;

    private WifiManager mWifiManager;
    @Nullable
    private SatelliteManager mSatelliteManager;
    private Context mContext;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager mConnectivityManager;
    private CarrierConfigTracker mCarrierConfigTracker;
    private Handler mHandler;
    private Handler mWorkerHandler;
    private MobileMappings.Config mConfig = null;
    private Executor mExecutor;
    private AccessPointController mAccessPointController;
    private IntentFilter mConnectionStateFilter;
    @VisibleForTesting
    @Nullable
    InternetDialogCallback mCallback;
    private UiEventLogger mUiEventLogger;
    private BroadcastDispatcher mBroadcastDispatcher;
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private GlobalSettings mGlobalSettings;
    private int mDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private ConnectivityManager.NetworkCallback mConnectivityManagerNetworkCallback;
    private WindowManager mWindowManager;
    private ToastFactory mToastFactory;
    private SignalDrawable mSignalDrawable;
    private SignalDrawable mSecondarySignalDrawable; // For the secondary mobile data sub in DSDS
    private LocationController mLocationController;
    private DialogTransitionAnimator mDialogTransitionAnimator;
    private boolean mHasWifiEntries;
    private WifiStateWorker mWifiStateWorker;
    private boolean mHasActiveSubIdOnDds;
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
    private int mNonDdsCallState = TelephonyManager.CALL_STATE_IDLE;
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
    private int mActiveDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
    private boolean mIsMobileDataEnabled = false;
    private UserRepository mUserRepository;
    private boolean mHasMultipleFullUsers = false;

    @VisibleForTesting
    Map<Integer, ServiceState> mSubIdServiceState = new HashMap<>();
    @VisibleForTesting
    static final float TOAST_PARAMS_HORIZONTAL_WEIGHT = 1.0f;
    @VisibleForTesting
    static final float TOAST_PARAMS_VERTICAL_WEIGHT = 1.0f;
    @VisibleForTesting
    static final long SHORT_DURATION_TIMEOUT = 4000;
    @VisibleForTesting
    protected ActivityStarter mActivityStarter;
    @VisibleForTesting
    protected SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangedListener;
    protected WifiUtils.InternetIconInjector mWifiIconInjector;
    @VisibleForTesting
    protected boolean mCanConfigWifi;
    @VisibleForTesting
    protected KeyguardStateController mKeyguardStateController;
    @VisibleForTesting
    protected boolean mHasEthernet = false;
    @VisibleForTesting
    protected ConnectedWifiInternetMonitor mConnectedWifiInternetMonitor;
    @VisibleForTesting
    protected boolean mCarrierNetworkChangeMode;
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
    private CarrierNameCustomization mCarrierNameCustomization;
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names

// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
    private boolean mIsSmartDdsSwitchFeatureAvailable;
    private boolean mIsExtTelServiceConnected = false;
    private ExtTelephonyManager mExtTelephonyManager;
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    private boolean mHasDualDataCapability = false;
    private ContentObserver mDualDataContentObserver;
    private int mNddsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
    private boolean mIsNddsDataEnabled = false;
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
    private FiveGServiceClient mFiveGServiceClient;
    private final Map<Integer, FiveGStateMonitor> mSubIdFiveGStateMonitorMap = new HashMap<>();
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch

    private ServiceCallback mExtTelServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephony service connected");
            mIsExtTelServiceConnected = true;
            try {
                mIsSmartDdsSwitchFeatureAvailable =
                        mExtTelephonyManager.isSmartDdsSwitchFeatureAvailable();
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
                mHasDualDataCapability = mExtTelephonyManager.getDualDataCapability();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
                Log.d(TAG, "isSmartDdsSwitchFeatureAvailable: " +
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
                        mIsSmartDdsSwitchFeatureAvailable +
                        " mHasDualDataCapability: " + mHasDualDataCapability);
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
            } catch (RemoteException ex) {
                Log.e(TAG, "isSmartDdsSwitchFeatureAvailable exception " + ex);
            }
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            handleDualDataUserPerferenceListener();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephony service disconnected");
            mIsExtTelServiceConnected = false;
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            mHasDualDataCapability = false;
            handleDualDataUserPerferenceListener();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
        }
    };

// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
    int mCurrentSatelliteState = SATELLITE_NOT_STARTED;

    final SatelliteModemStateCallback mSatelliteModemStateCallback =
            new SatelliteModemStateCallback() {
                @Override
                public void onSatelliteModemStateChanged(int state) {
                    mCurrentSatelliteState = switch (state) {
                        case SatelliteManager.SATELLITE_MODEM_STATE_OFF,
                             SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE,
                             SatelliteManager.SATELLITE_MODEM_STATE_UNKNOWN ->
                                SATELLITE_NOT_STARTED;
                        case SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED ->
                                SATELLITE_CONNECTED;
                        default -> SATELLITE_STARTED;
                    };
                    if (mCallback != null) {
                        mCallback.onSatelliteModemStateChanged(state);
                    }
                }
            };

    private final KeyguardUpdateMonitorCallback mKeyguardUpdateCallback =
            new KeyguardUpdateMonitorCallback() {
                @Override
                public void onRefreshCarrierInfo() {
                    if (mCallback != null) {
                        mCallback.onRefreshCarrierInfo();
                    }
                }

                @Override
                public void onSimStateChanged(int subId, int slotId, int simState) {
                    if (mCallback != null) {
                        mCallback.onSimStateChanged();
                    }
                }
            };

    protected List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        return mSubscriptionManager.getActiveSubscriptionInfoList();
    }

    protected List<SubscriptionInfo> getFilteredSubscriptionInfo() {
        return mKeyguardUpdateMonitor.getFilteredSubscriptionInfo();
    }

    @Inject
    public InternetDetailsContentController(@ShadeDisplayAware Context context,
            UiEventLogger uiEventLogger, ActivityStarter starter,
            AccessPointController accessPointController, SubscriptionManager subscriptionManager,
            TelephonyManager telephonyManager, @Nullable WifiManager wifiManager,
            ConnectivityManager connectivityManager, Optional<SatelliteManager> optSatelliteManager,
            @Main Handler handler, @Main Executor mainExecutor,
            BroadcastDispatcher broadcastDispatcher, KeyguardUpdateMonitor keyguardUpdateMonitor,
            GlobalSettings globalSettings, KeyguardStateController keyguardStateController,
            @ShadeDisplayAware WindowManager windowManager, ToastFactory toastFactory,
            @Background Handler workerHandler, CarrierConfigTracker carrierConfigTracker,
            LocationController locationController,
            DialogTransitionAnimator dialogTransitionAnimator,
            WifiStateWorker wifiStateWorker,
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
            FeatureFlags featureFlags,
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
            ShadeDialogContextInteractor shadeDialogContextInteractor,
            UserRepository userRepository,
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
            CarrierNameCustomization carrierNameCustomization
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
    ) {
        if (DEBUG) {
            Log.d(TAG, "Init InternetDetailsContentController");
        }
        mHandler = handler;
        mWorkerHandler = workerHandler;
        mExecutor = mainExecutor;
        mContext = context;
        mGlobalSettings = globalSettings;
        mWifiManager = wifiManager;
        mTelephonyManager = telephonyManager;
        mSatelliteManager = optSatelliteManager.orElse(null);
        mConnectivityManager = connectivityManager;
        mSubscriptionManager = subscriptionManager;
        mCarrierConfigTracker = carrierConfigTracker;
        mBroadcastDispatcher = broadcastDispatcher;
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mKeyguardStateController = keyguardStateController;
        mConnectionStateFilter = new IntentFilter();
        mConnectionStateFilter.addAction(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mConnectionStateFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        mUiEventLogger = uiEventLogger;
        mActivityStarter = starter;
        mAccessPointController = accessPointController;
        mWifiIconInjector = new WifiUtils.InternetIconInjector(mContext);
        mConnectivityManagerNetworkCallback = new DataConnectivityListener();
        mWindowManager = windowManager;
        mToastFactory = toastFactory;
        mSignalDrawable = new SignalDrawable(mContext);
        mSecondarySignalDrawable = new SignalDrawable(mContext);
        mLocationController = locationController;
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mConnectedWifiInternetMonitor = new ConnectedWifiInternetMonitor();
        mWifiStateWorker = wifiStateWorker;
        mFeatureFlags = featureFlags;
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        mCarrierNameCustomization = carrierNameCustomization;
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        mShadeDialogContextInteractor = shadeDialogContextInteractor;
        mUserRepository = userRepository;
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
        mExtTelephonyManager = ExtTelephonyManager.getInstance(context);
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        mFiveGServiceClient = FiveGServiceClient.getInstance(context);
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
    }

    void onStart(@NonNull InternetDialogCallback callback,
            boolean canConfigWifi, @NonNull CoroutineScope coroutineScope) {
        if (DEBUG) {
            Log.d(TAG, "onStart");
        }
        mCallback = callback;
        mKeyguardUpdateMonitor.registerCallback(mKeyguardUpdateCallback);
        mAccessPointController.addAccessPointCallback(this);
        mBroadcastDispatcher.registerReceiver(mConnectionStateReceiver, mConnectionStateFilter,
                mExecutor);
        // Listen the subscription changes
        mOnSubscriptionsChangedListener = new InternetOnSubscriptionChangedListener();
        refreshHasActiveSubIdOnDds();
        mSubscriptionManager.addOnSubscriptionsChangedListener(mExecutor,
                mOnSubscriptionsChangedListener);
        mDefaultDataSubId = getDefaultDataSubscriptionId();
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
        mActiveDataSubId = mSubscriptionManager.getActiveDataSubscriptionId();
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
        if (DEBUG) {
            Log.d(TAG, "Init, SubId: " + mDefaultDataSubId);
        }
        mConfig = MobileMappings.Config.readConfig(mContext);
        mTelephonyManager = mTelephonyManager.createForSubscriptionId(mDefaultDataSubId);
        mSubIdTelephonyManagerMap.put(mDefaultDataSubId, mTelephonyManager);
        registerInternetTelephonyCallback(mTelephonyManager, mDefaultDataSubId);
        // Listen the connectivity changes
        mConnectivityManager.registerDefaultNetworkCallback(mConnectivityManagerNetworkCallback);
        mCanConfigWifi = canConfigWifi;
        scanWifiAccessPoints();
        if (mSatelliteManager != null) {
            try {
                mSatelliteManager.registerForModemStateChanged(mExecutor,
                        mSatelliteModemStateCallback);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Unable to register callback for modem state changes : " + e);
            }
        }

        JavaAdapterKt.collectFlow(
                coroutineScope,
                mUserRepository.getHasMultipleFullUsers(),
                hasMultipleFullUsers -> {
                    mHasMultipleFullUsers = hasMultipleFullUsers;
                    scanWifiAccessPoints();
                }
        );
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
        if (!mIsExtTelServiceConnected) {
            mExtTelephonyManager.connectService(mExtTelServiceCallback);
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        } else {
            notifyDualDataEnabledStateChanged();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
        }
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
        mIsNddsDataEnabled = mTelephonyManager.createForSubscriptionId(mNddsSubId).isDataEnabled();
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        registerFiveGStateMonitor();
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
    }

    void onStop() {
        if (DEBUG) {
            Log.d(TAG, "onStop");
        }
        mBroadcastDispatcher.unregisterReceiver(mConnectionStateReceiver);
        for (TelephonyManager tm : mSubIdTelephonyManagerMap.values()) {
            TelephonyCallback callback = mSubIdTelephonyCallbackMap.get(tm.getSubscriptionId());
            if (callback != null) {
                tm.unregisterTelephonyCallback(callback);
            } else if (DEBUG) {
                Log.e(TAG, "Unexpected null telephony call back for Sub " + tm.getSubscriptionId());
            }
        }
        mSubIdTelephonyManagerMap.clear();
        mSubIdTelephonyCallbackMap.clear();
        mSubIdTelephonyDisplayInfoMap.clear();
        mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
        mAccessPointController.removeAccessPointCallback(this);
        mKeyguardUpdateMonitor.removeCallback(mKeyguardUpdateCallback);
        mConnectivityManager.unregisterNetworkCallback(mConnectivityManagerNetworkCallback);
        mConnectedWifiInternetMonitor.unregisterCallback();
        mCallback = null;
        if (mSatelliteManager != null) {
            try {
                mSatelliteManager.unregisterForModemStateChanged(mSatelliteModemStateCallback);
            } catch (IllegalStateException e) {
                Log.w(TAG, "Unable to unregister callback for modem state changes : " + e);
            }
        }
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        unregisterFiveGStateMonitor();
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
    }

    /**
     * This is to generate and register the new callback to Telephony for uncached subscription id,
     * then cache it. Telephony also cached this callback into
     * {@link com.android.server.TelephonyRegistry}, so if subscription id and callback were cached
     * already, it shall do nothing to avoid registering redundant callback to Telephony.
     */
    private void registerInternetTelephonyCallback(TelephonyManager telephonyManager, int subId) {
        if (mSubIdTelephonyCallbackMap.containsKey(subId)) {
            // Avoid to generate and register unnecessary callback to Telephony.
            return;
        }
        InternetTelephonyCallback telephonyCallback = new InternetTelephonyCallback(subId);
        mSubIdTelephonyCallbackMap.put(subId, telephonyCallback);
        telephonyManager.registerTelephonyCallback(mExecutor, telephonyCallback);
    }

    boolean isAirplaneModeEnabled() {
        return mGlobalSettings.getInt(Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    void setAirplaneModeDisabled() {
        mConnectivityManager.setAirplaneMode(false);
    }

    protected int getDefaultDataSubscriptionId() {
        return mSubscriptionManager.getDefaultDataSubscriptionId();
    }

    @VisibleForTesting
    protected Intent getSettingsIntent() {
        return new Intent(Settings.ACTION_NETWORK_PROVIDER_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Nullable
    protected Intent getWifiDetailsSettingsIntent(String key) {
        if (TextUtils.isEmpty(key)) {
            if (DEBUG) {
                Log.d(TAG, "connected entry's key is empty");
            }
            return null;
        }
        return WifiUtils.getWifiDetailsSettingsIntent(key);
    }

    CharSequence getDialogTitleText() {
        if (isAirplaneModeEnabled()) {
            return mContext.getText(R.string.airplane_mode);
        }
        return mContext.getText(R.string.quick_settings_internet_label);
    }

    @Nullable
    CharSequence getSubtitleText(boolean isProgressBarVisible) {
        if (mCanConfigWifi && !isWifiEnabled()) {
            // When Wi-Fi is disabled.
            //   Sub-Title: Wi-Fi is off
            if (DEBUG) {
                Log.d(TAG, "Wi-Fi off.");
            }
            return mContext.getText(SUBTITLE_TEXT_WIFI_IS_OFF);
        }

        if (isDeviceLocked()) {
            // When the device is locked.
            //   Sub-Title: Unlock to view networks
            if (DEBUG) {
                Log.d(TAG, "The device is locked.");
            }
            return mContext.getText(SUBTITLE_TEXT_UNLOCK_TO_VIEW_NETWORKS);
        }

        if (mHasWifiEntries) {
            return mCanConfigWifi ? mContext.getText(SUBTITLE_TEXT_TAP_A_NETWORK_TO_CONNECT) : null;
        }

        if (mCanConfigWifi && isProgressBarVisible) {
            // When the Wi-Fi scan result callback is received
            //   Sub-Title: Searching for networks...
            return mContext.getText(SUBTITLE_TEXT_SEARCHING_FOR_NETWORKS);
        }

        if (isCarrierNetworkActive()) {
            return mContext.getText(SUBTITLE_TEXT_NON_CARRIER_NETWORK_UNAVAILABLE);
        }

        // Sub-Title:
        // show non_carrier_network_unavailable
        //   - while Wi-Fi on + no Wi-Fi item
        //   - while Wi-Fi on + no Wi-Fi item + mobile data off
        // show all_network_unavailable:
        //   - while Wi-Fi on + no Wi-Fi item + no carrier item
        //   - while Wi-Fi on + no Wi-Fi item + service is out of service
        //   - while Wi-Fi on + no Wi-Fi item + mobile data on + no carrier data.
        if (DEBUG) {
            Log.d(TAG, "No Wi-Fi item.");
        }
        boolean isActiveOnNonDds =
                getActiveAutoSwitchNonDdsSubId() != SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (!hasActiveSubIdOnDds() || (!isVoiceStateInService(mDefaultDataSubId)
                && !isDataStateInService(mDefaultDataSubId) && !isActiveOnNonDds)) {
            if (DEBUG) {
                Log.d(TAG, "No carrier or service is out of service.");
            }
            return mContext.getText(SUBTITLE_TEXT_ALL_CARRIER_NETWORK_UNAVAILABLE);
        }

// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        if (mCanConfigWifi && !isMobileDataEnabled(mDefaultDataSubId)) {
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            if (DEBUG) {
                Log.d(TAG, "Mobile data off");
            }
            return mContext.getText(SUBTITLE_TEXT_NON_CARRIER_NETWORK_UNAVAILABLE);
        }

        if (!activeNetworkIsCellular()) {
            if (DEBUG) {
                Log.d(TAG, "No carrier data.");
            }
            return mContext.getText(SUBTITLE_TEXT_ALL_CARRIER_NETWORK_UNAVAILABLE);
        }

        if (mCanConfigWifi) {
            return mContext.getText(SUBTITLE_TEXT_NON_CARRIER_NETWORK_UNAVAILABLE);
        }
        return null;
    }

    @Nullable
    Drawable getInternetWifiDrawable(@NonNull WifiEntry wifiEntry) {
        Drawable drawable = getWifiDrawable(wifiEntry);
        if (drawable == null) {
            return null;
        }
        drawable.setTint(mContext.getColor(R.color.connected_network_primary_color));
        return drawable;
    }

    /**
     * Returns a Wi-Fi icon {@link Drawable}.
     *
     * @param wifiEntry {@link WifiEntry}
     */
    @Nullable
    Drawable getWifiDrawable(@NonNull WifiEntry wifiEntry) {
        if (wifiEntry instanceof HotspotNetworkEntry) {
            int deviceType = ((HotspotNetworkEntry) wifiEntry).getDeviceType();
            return mContext.getDrawable(getHotspotIconResource(deviceType));
        }
        // If the Wi-Fi level is equal to WIFI_LEVEL_UNREACHABLE(-1), then a null drawable
        // will be returned.
        if (wifiEntry.getLevel() == WifiEntry.WIFI_LEVEL_UNREACHABLE) {
            return null;
        }
        return mWifiIconInjector.getIcon(wifiEntry.shouldShowXLevelIcon(), wifiEntry.getLevel());
    }

    Drawable getSignalStrengthDrawable(int subId) {
        Drawable drawable = mContext.getDrawable(
                R.drawable.ic_signal_strength_zero_bar_no_internet);
        try {
            if (mTelephonyManager == null) {
                if (DEBUG) {
                    Log.d(TAG, "TelephonyManager is null");
                }
                return drawable;
            }

            boolean isCarrierNetworkActive = isCarrierNetworkActive();
            if (isDataStateInService(subId) || isVoiceStateInService(subId)
                    || isCarrierNetworkActive) {
                AtomicReference<Drawable> shared = new AtomicReference<>();
                shared.set(getSignalStrengthDrawableWithLevel(isCarrierNetworkActive, subId));
                drawable = shared.get();
            }

            int tintColor = Utils.getColorAttrDefaultColor(mContext,
                    android.R.attr.textColorTertiary);
            if (activeNetworkIsCellular() || isCarrierNetworkActive) {
                tintColor = mContext.getColor(R.color.connected_network_primary_color);
            }
            drawable.setTint(tintColor);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return drawable;
    }

    /**
     * To get the signal bar icon with level.
     *
     * @return The Drawable which is a signal bar icon with level.
     */
    Drawable getSignalStrengthDrawableWithLevel(boolean isCarrierNetworkActive, int subId) {
        TelephonyManager tm = mSubIdTelephonyManagerMap.getOrDefault(subId, mTelephonyManager);
        final SignalStrength strength = tm.getSignalStrength();
        int level = (strength == null) ? 0 : strength.getLevel();
        int numLevels = SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
        boolean hideNoInternetState = mConfig.hideNoInternetState;
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
        if (isCarrierNetworkActive) {
            level = getCarrierNetworkLevel();
            numLevels = WifiEntry.WIFI_LEVEL_MAX + 1;
            if (mCarrierConfigTracker.getInflateSignalStrengthBool(subId)) {
                level += 1;
                numLevels += 1;
            }
        } else if (mSubscriptionManager != null && shouldInflateSignalStrength(subId)) {
            level += 1;
            numLevels += 1;
        }
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
        Log.i(TAG, "hideNoInternetState:" + hideNoInternetState);
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
        return getSignalStrengthIcon(subId, mContext, level, numLevels, NO_CELL_DATA_TYPE_ICON,
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
                !hideNoInternetState && !isMobileDataEnabledWithNddsOverrideConsidered(subId));
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
    }

    Drawable getSignalStrengthIcon(int subId, Context context, int level, int numLevels,
            int iconType, boolean cutOut) {
        boolean isForDds = subId == mDefaultDataSubId;
        int levelDrawable = mCarrierNetworkChangeMode ? SignalDrawable.getCarrierChangeState(
                numLevels) : SignalDrawable.getState(level, numLevels, cutOut);
        if (isForDds) {
            mSignalDrawable.setLevel(levelDrawable);
        } else {
            mSecondarySignalDrawable.setLevel(levelDrawable);
        }

        // Make the network type drawable
        final Drawable networkDrawable = iconType == NO_CELL_DATA_TYPE_ICON ? EMPTY_DRAWABLE
                : context.getResources().getDrawable(iconType, context.getTheme());

        // Overlay the two drawables
        final Drawable[] layers =
                {networkDrawable, isForDds ? mSignalDrawable : mSecondarySignalDrawable};
        final int iconSize = context.getResources().getDimensionPixelSize(
                R.dimen.signal_strength_icon_size);

        final LayerDrawable icons = new LayerDrawable(layers);
        // Set the network type icon at the top left
        icons.setLayerGravity(0 /* index of networkDrawable */, Gravity.TOP | Gravity.LEFT);
        // Set the signal strength icon at the bottom right
        icons.setLayerGravity(1 /* index of SignalDrawable */, Gravity.BOTTOM | Gravity.RIGHT);
        if (!NewStatusBarIcons.isEnabled()) {
            icons.setLayerSize(1 /* index of SignalDrawable */, iconSize, iconSize);
        }
        icons.setTintList(Utils.getColorAttr(context, android.R.attr.textColorTertiary));
        return icons;
    }

    private boolean shouldInflateSignalStrength(int subId) {
        return SignalStrengthUtil.shouldInflateSignalStrength(mContext, subId);
    }

    private CharSequence getUniqueSubscriptionDisplayName(int subscriptionId, Context context) {
        final Map<Integer, CharSequence> displayNames = getUniqueSubscriptionDisplayNames(context);
        CharSequence displayName =  displayNames.getOrDefault(subscriptionId, "");
        if (DEBUG) {
            Log.d(TAG, "getUniqueSubscriptionDisplayName(), subscriptionId: " + subscriptionId
                    + ", displayName:" + displayName);
        }
        return displayName;
    }

    private Map<Integer, CharSequence> getUniqueSubscriptionDisplayNames(Context context) {
        class DisplayInfo {
            DisplayInfo(SubscriptionInfo subscriptionInfo, CharSequence originalName) {
                this.subscriptionInfo = subscriptionInfo;
                this.originalName = originalName;
            }

            public SubscriptionInfo subscriptionInfo;
            public CharSequence originalName;
            public CharSequence uniqueName;
        }

        // Map of SubscriptionId to DisplayName
        final Supplier<Stream<DisplayInfo>> originalInfos =
                () -> getActiveSubscriptionInfoList().stream().filter(i -> {
                    // Filter out null values.
                    return (i != null && i.getDisplayName() != null);
                }).map(i -> new DisplayInfo(i, i.getDisplayName().toString().trim()));

        // A Unique set of display names
        Set<CharSequence> uniqueNames = new HashSet<>();
        // Return the set of duplicate names
        final Set<CharSequence> duplicateOriginalNames = originalInfos.get().filter(
                info -> !uniqueNames.add(info.originalName)).map(info -> info.originalName).collect(
                Collectors.toSet());

        // If a display name is duplicate, append the final 4 digits of the phone number.
        // Creates a mapping of Subscription id to original display name + phone number display name
        final Supplier<Stream<DisplayInfo>> uniqueInfos = () -> originalInfos.get().map(info -> {
            if (duplicateOriginalNames.contains(info.originalName)) {
                // This may return null, if the user cannot view the phone number itself.
                final String phoneNumber = DeviceInfoUtils.getBidiFormattedPhoneNumber(context,
                        info.subscriptionInfo);
                String lastFourDigits = "";
                if (phoneNumber != null) {
                    lastFourDigits = (phoneNumber.length() > 4) ? phoneNumber.substring(
                            phoneNumber.length() - 4) : phoneNumber;
                }

                if (TextUtils.isEmpty(lastFourDigits)) {
                    info.uniqueName = info.originalName;
                } else {
                    info.uniqueName = info.originalName + " " + lastFourDigits;
                }

            } else {
                info.uniqueName = info.originalName;
            }
            return info;
        });

        // Check uniqueness a second time.
        // We might not have had permission to view the phone numbers.
        // There might also be multiple phone numbers whose last 4 digits the same.
        uniqueNames.clear();
        final Set<CharSequence> duplicatePhoneNames = uniqueInfos.get().filter(
                info -> !uniqueNames.add(info.uniqueName)).map(info -> info.uniqueName).collect(
                Collectors.toSet());

        return uniqueInfos.get().map(info -> {
            if (duplicatePhoneNames.contains(info.uniqueName)) {
                info.uniqueName =
                        info.originalName + " " + info.subscriptionInfo.getSubscriptionId();
            }
            return info;
        }).collect(Collectors.toMap(info -> info.subscriptionInfo.getSubscriptionId(),
                info -> info.uniqueName));
    }

    /**
     * @return the subId of the visible non-DDS if it's actively being used for data, otherwise
     * return {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
     */
    int getActiveAutoSwitchNonDdsSubId() {
        if (!mFeatureFlags.isEnabled(Flags.QS_SECONDARY_DATA_SUB_INFO)) {
            // sets the non-DDS to be not found to hide its visual
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        int activeDataSubId = SubscriptionManager.getActiveDataSubscriptionId();
        if (activeDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                || mDefaultDataSubId == activeDataSubId) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(activeDataSubId);
        if (subInfo != null && subInfo.getSubscriptionId() != mDefaultDataSubId
                && !subInfo.isOpportunistic()) {
            int subId = subInfo.getSubscriptionId();
            if (mSubIdTelephonyManagerMap.get(subId) == null) {
                TelephonyManager secondaryTm = mTelephonyManager.createForSubscriptionId(subId);
                registerInternetTelephonyCallback(secondaryTm, subId);
                mSubIdTelephonyManagerMap.put(subId, secondaryTm);
            }
// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
            Log.d(TAG, "getActiveAutoSwitchNonDdsSubId: " + subId);
// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
            return subId;
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    /**
     * @return the subId of the visible ActiveDataSubId, otherwise
     * return {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
     */
    int getActiveDataSubId() {
        // TODO(b/440352380): refactor and reduce the times of API calls
        int activeDataSubId = SubscriptionManager.getActiveDataSubscriptionId();
        if (activeDataSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        SubscriptionInfo defaultDataSubInfo = mSubscriptionManager.getActiveSubscriptionInfo(
                mDefaultDataSubId);
        // get the visible Active SubscriptionInfo List from
        // KeyguardUpdateMonitor.getFilteredSubscriptionInfo() which is controlled by
        // CarrierConfigManager.KEY_ALWAYS_SHOW_PRIMARY_SIGNAL_BAR_IN_OPPORTUNISTIC_NETWORK_BOOLEAN.
        SubscriptionInfo subInfo = getFilteredSubscriptionInfo().stream()
                .filter(it -> it.getSubscriptionId() == activeDataSubId)
                .findFirst().orElse(defaultDataSubInfo);
        if (subInfo != null) {
            if (DEBUG) {
                Log.d(TAG, "getActiveDataSubId(), subInfo:" + subInfo);
            }
            // register the listen
            int subId = subInfo.getSubscriptionId();
            if (mSubIdTelephonyManagerMap.get(subId) == null) {
                TelephonyManager secondaryTm = mTelephonyManager.createForSubscriptionId(subId);
                registerInternetTelephonyCallback(secondaryTm, subId);
                mSubIdTelephonyManagerMap.put(subId, secondaryTm);
            }
            return subId;
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    CharSequence getMobileNetworkTitle(int subId) {
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        if (mCarrierNameCustomization.isRoamingCustomizationEnabled()
                && mCarrierNameCustomization.isRoaming(subId)) {
            return mCarrierNameCustomization.getRoamingCarrierName(subId);
        } else {
            return getUniqueSubscriptionDisplayName(subId, mContext);
        }
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
    }

    String getMobileNetworkSummary(int subId) {
        String networkTypeDescription = "";
        final FiveGServiceState fiveGState = getFiveGServiceState(subId);
        final int radioIconType = fiveGState.getRadioIconType();
        if ((mCarrierNameCustomization.show5GAIcon() && radioIconType == RadioIconType.TYPE_5G_UWB)
                || fiveGState.getRadioIconType() == RadioIconType.TYPE_LTE_NB_IOT) {
            networkTypeDescription = mCarrierNameCustomization.getNetworkTypeDescription(subId);
// QTI_BEGIN: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
        }
// QTI_END: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog

        if (TextUtils.isEmpty(networkTypeDescription)) {
            networkTypeDescription = getNetworkTypeDescription(mContext, mConfig, subId);
        }
        if (DEBUG) {
            Log.d(TAG,
                    "getMobileNetworkSummary(), NetworkTypeDescription:" + networkTypeDescription);
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        }
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
        return getMobileSummary(mContext, networkTypeDescription, subId);
    }

    /**
     * Get currently description of mobile network type.
     */
    private String getNetworkTypeDescription(Context context, MobileMappings.Config config,
            int subId) {
        TelephonyDisplayInfo telephonyDisplayInfo = mSubIdTelephonyDisplayInfoMap.getOrDefault(
                subId, DEFAULT_TELEPHONY_DISPLAY_INFO);
        if (DEBUG) {
            Log.d(TAG, "getNetworkTypeDescription(), subId:" + subId
                    + ",telephonyDisplayInfo:" + telephonyDisplayInfo);
        }
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        String iconKey = null;
        if (isNsa(telephonyDisplayInfo)) {
            final FiveGServiceState fiveGState = getFiveGServiceState(subId);
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
            // RadioIconType includes NR and NB-IoT, so NB-IoT needs to be filtered out.
            if (!fiveGState.isRadioIconTypeValid()
                    || fiveGState.getRadioIconType() == RadioIconType.TYPE_LTE_NB_IOT) {
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
                iconKey = toIconKey(telephonyDisplayInfo.getNetworkType());
            } else {
                iconKey = getIconKey(telephonyDisplayInfo);
            }
        } else {
            iconKey = getIconKey(telephonyDisplayInfo);
        }
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog

        if (mapIconSets(config) == null || mapIconSets(config).get(iconKey) == null) {
            if (DEBUG) {
                Log.d(TAG, "The description of network type is empty.");
            }
            return "";
        }

        int resId = Objects.requireNonNull(mapIconSets(config).get(iconKey)).dataContentDescription;
        SignalIcon.MobileIconGroup iconGroup;
        if (isCarrierNetworkActive()) {
            iconGroup = TelephonyIcons.CARRIER_MERGED_WIFI;
            resId = iconGroup.dataContentDescription;
        } else if (mCarrierNetworkChangeMode) {
            iconGroup = TelephonyIcons.CARRIER_NETWORK_CHANGE;
            resId = iconGroup.dataContentDescription;
        }

        return resId != 0 ? SubscriptionManager.getResourcesForSubId(context, subId).getString(
                resId) : "";
    }

// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
    private boolean isNsa(TelephonyDisplayInfo telephonyDisplayInfo) {
        if (telephonyDisplayInfo != null) {
            final int networkType = telephonyDisplayInfo.getOverrideNetworkType();
            return networkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                    || networkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA;
        }
        return false;
    }

// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
    private String getMobileSummary(Context context, String networkTypeDescription, int subId) {
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        if (!isMobileDataEnabled(subId)) {
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            return context.getString(R.string.mobile_data_off_summary);
        }
        String summary = networkTypeDescription;
        
// QTI_BEGIN: 2023-05-10: Telephony: Adjust string display for smart DDS switch
        boolean isSmartDdsEnabled = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.SMART_DDS_SWITCH, 0) == 1;
// QTI_END: 2023-05-10: Telephony: Adjust string display for smart DDS switch
        int activeDataSubId = getActiveDataSubId();
        boolean isForVisibleDds = subId == activeDataSubId;
        int activeAutoSwitchNonDdsSubId = getActiveAutoSwitchNonDdsSubId();
        boolean isOnNonDds =
                activeAutoSwitchNonDdsSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        // Set network description for the carrier network when connecting to the carrier network
        // under the airplane mode ON.
        if (activeNetworkIsCellular() || isCarrierNetworkActive()) {
            summary = context.getString(
                    com.android.settingslib.R.string.preference_summary_default_combination,
                    context.getString(
// QTI_BEGIN: 2023-05-10: Telephony: Adjust string display for smart DDS switch
                            // if nonDds is active, explains Dds status as poor connection
// QTI_END: 2023-05-10: Telephony: Adjust string display for smart DDS switch
                            isForVisibleDds || isSmartDdsEnabled
// QTI_BEGIN: 2023-05-10: Telephony: Adjust string display for smart DDS switch
                                    ? (isOnNonDds && !isSmartDdsEnabled
// QTI_END: 2023-05-10: Telephony: Adjust string display for smart DDS switch
                                    ? R.string.mobile_data_poor_connection
                                    : R.string.mobile_data_connection_active)
                                    : R.string.mobile_data_temp_connection_active),
                    networkTypeDescription);
        } else if (!isDataStateInService(subId)) {
            summary = context.getString(R.string.mobile_data_no_connection);
        }
        return summary;
    }

    void startActivity(Intent intent, View view) {
        ActivityTransitionAnimator.Controller controller =
                mDialogTransitionAnimator.createActivityTransitionController(view);

        if (controller == null && mCallback != null) {
            mCallback.dismissDialog();
        }

        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0, controller);
    }

    void startActivityForDialog(Intent intent) {
        mActivityStarter.startActivity(intent, false /* dismissShade */);
    }

    void launchNetworkSetting(View view) {
        startActivity(getSettingsIntent(), view);
    }

    void launchWifiDetailsSetting(String key, View view) {
        Intent intent = getWifiDetailsSettingsIntent(key);
        if (intent != null) {
            startActivity(intent, view);
        }
    }

    void launchMobileNetworkSettings(View view) {
        final int subId = getActiveAutoSwitchNonDdsSubId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "launchMobileNetworkSettings fail, invalid subId:" + subId);
            return;
        }
        startActivity(getSubSettingIntent(subId), view);
    }

    Intent getSubSettingIntent(int subId) {
        final Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
        final Bundle fragmentArgs = new Bundle();
        // Special contract for Settings to highlight permission row
        fragmentArgs.putString(SETTINGS_EXTRA_FRAGMENT_ARG_KEY, AUTO_DATA_SWITCH_SETTING_R_ID);
        intent.putExtra(Settings.EXTRA_SUB_ID, subId);
        intent.putExtra(SETTINGS_EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        return intent;
    }

    void launchWifiScanningSetting(View view) {
        final Intent intent = new Intent(ACTION_WIFI_SCANNING_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent, view);
    }

    /**
     * Enable or disable Wi-Fi.
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    @AnyThread
    public void setWifiEnabled(boolean enabled) {
        mWifiStateWorker.setWifiEnabled(enabled);
    }

    /**
     * Return whether Wi-Fi is enabled or disabled.
     *
     * @return {@code true} if Wi-Fi is enabled or enabling
     * @see WifiManager#getWifiState()
     */
    @AnyThread
    public boolean isWifiEnabled() {
        return mWifiStateWorker.isWifiEnabled();
    }

    void connectCarrierNetwork() {
        String errorLogPrefix = "Fail to connect carrier network : ";

// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        if (!isMobileDataEnabled(mDefaultDataSubId)) {
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            if (DEBUG) {
                Log.d(TAG, errorLogPrefix + "settings OFF");
            }
            return;
        }
        if (isDeviceLocked()) {
            if (DEBUG) {
                Log.d(TAG, errorLogPrefix + "device locked");
            }
            return;
        }
        if (activeNetworkIsCellular()) {
            Log.d(TAG, errorLogPrefix + "already active");
            return;
        }

        MergedCarrierEntry mergedCarrierEntry = mAccessPointController.getMergedCarrierEntry();
        if (mergedCarrierEntry == null) {
            Log.e(TAG, errorLogPrefix + "no merged entry");
            return;
        }

        if (!mergedCarrierEntry.canConnect()) {
            Log.w(TAG, errorLogPrefix + "merged entry connect state "
                    + mergedCarrierEntry.getConnectedState());
            return;
        }

        mergedCarrierEntry.connect(null /* ConnectCallback */, false);
        makeOverlayToast(R.string.wifi_wont_autoconnect_for_now);
    }

    boolean isCarrierNetworkActive() {
        final MergedCarrierEntry mergedCarrierEntry =
                mAccessPointController.getMergedCarrierEntry();
        return mergedCarrierEntry != null && mergedCarrierEntry.isDefaultNetwork();
    }

    int getCarrierNetworkLevel() {
        final MergedCarrierEntry mergedCarrierEntry =
                mAccessPointController.getMergedCarrierEntry();
        if (mergedCarrierEntry == null) return WifiEntry.WIFI_LEVEL_MIN;

        int level = mergedCarrierEntry.getLevel();
        // To avoid icons not found with WIFI_LEVEL_UNREACHABLE(-1), use WIFI_LEVEL_MIN(0) instead.
        if (level < WifiEntry.WIFI_LEVEL_MIN) level = WifiEntry.WIFI_LEVEL_MIN;
        return level;
    }

    @WorkerThread
    void setMergedCarrierWifiEnabledIfNeed(int subId, boolean enabled) {
        // If the Carrier Provisions Wi-Fi Merged Networks enabled, do not set the merged carrier
        // Wi-Fi state together.
        if (mCarrierConfigTracker.getCarrierProvisionsWifiMergedNetworksBool(subId)) {
            return;
        }

        final MergedCarrierEntry entry = mAccessPointController.getMergedCarrierEntry();
        if (entry == null) {
            if (DEBUG) {
                Log.d(TAG, "MergedCarrierEntry is null, can not set the status.");
            }
            return;
        }
        entry.setEnabled(enabled);
    }

    WifiManager getWifiManager() {
        return mWifiManager;
    }

    TelephonyManager getTelephonyManager() {
        return mTelephonyManager;
    }

    SubscriptionManager getSubscriptionManager() {
        return mSubscriptionManager;
    }

    /**
     * @return whether there is the carrier item in the slice.
     */
    boolean hasActiveSubIdOnDds() {
        if (isAirplaneModeEnabled() || mTelephonyManager == null) {
            return false;
        }

        return mHasActiveSubIdOnDds;
    }

    private static boolean isEmbeddedSubscriptionVisible(@NonNull SubscriptionInfo subInfo) {
        if (subInfo.isEmbedded() && subInfo.getProfileClass() == PROFILE_CLASS_PROVISIONING) {
            return false;
        }
        return true;
    }

    private void refreshHasActiveSubIdOnDds() {
        if (mSubscriptionManager == null) {
            mHasActiveSubIdOnDds = false;
            Log.e(TAG, "SubscriptionManager is null, set mHasActiveSubId = false");
            return;
        }
        int dds = getDefaultDataSubscriptionId();
        if (dds == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mHasActiveSubIdOnDds = false;
            Log.d(TAG, "DDS is INVALID_SUBSCRIPTION_ID");
            return;
        }
        SubscriptionInfo ddsSubInfo = mSubscriptionManager.getActiveSubscriptionInfo(dds);
        if (ddsSubInfo == null) {
            mHasActiveSubIdOnDds = false;
            Log.e(TAG, "Can't get DDS subscriptionInfo");
            return;
        } else if (ddsSubInfo.isOnlyNonTerrestrialNetwork()) {
            mHasActiveSubIdOnDds = false;
            Log.d(TAG, "This is NTN, so do not show mobile data");
            return;
        }

        mHasActiveSubIdOnDds = isEmbeddedSubscriptionVisible(ddsSubInfo);
        Log.i(TAG, "mHasActiveSubId:" + mHasActiveSubIdOnDds);
    }

// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
    /**
     * Return {@code true} if there is an ongoing call on the non-DDS
     */
    boolean isNonDdsCallStateIdle() {
        return mNonDdsCallState == TelephonyManager.CALL_STATE_IDLE;
    }

// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
    /**
     * Return {@code true} if temporary DDS switch happened
     */
    boolean isTempDdsHappened() {
        return mDefaultDataSubId != mActiveDataSubId;
    }

// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
    /**
     * Return {@code true} if mobile data is enabled
     */
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    boolean isMobileDataEnabled(int subId) {
        if (mTelephonyManager == null) {
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            return false;
        }
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        return mTelephonyManager.createForSubscriptionId(subId).isDataEnabled();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    }

        /**
     * Return {@code true} if mobile data is enabled
     */
    boolean isMobileDataEnabled() {
        return mIsMobileDataEnabled;
    }

// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
    boolean isMobileDataEnabledWithNddsOverrideConsidered(int subId) {
        if (subId != getDefaultDataSubscriptionId()) {
            Log.i(TAG, "isMobileDataEnabled：mIsNddsDataEnabled = " + mIsNddsDataEnabled);
            return mIsNddsDataEnabled;
        }
        return isMobileDataEnabled(subId);
    }

// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
    /**
     * Set whether to enable data for {@code subId}, also whether to disable data for other
     * subscription
     */
    void setMobileDataEnabled(Context context, int subId, boolean enabled,
            boolean disableOtherSubscriptions) {
        if (mTelephonyManager == null) {
            if (DEBUG) {
                Log.d(TAG, "TelephonyManager is null, can not set mobile data.");
            }
            return;
        }

        if (mSubscriptionManager == null) {
            if (DEBUG) {
                Log.d(TAG, "SubscriptionManager is null, can not set mobile data.");
            }
            return;
        }

// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        mTelephonyManager.createForSubscriptionId(subId).setDataEnabledForReason(
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
                TelephonyManager.DATA_ENABLED_REASON_USER, enabled);
        if (disableOtherSubscriptions) {
            final List<SubscriptionInfo> subInfoList = getActiveSubscriptionInfoList();
            if (subInfoList != null) {
                for (SubscriptionInfo subInfo : subInfoList) {
                    // We never disable mobile data for opportunistic subscriptions.
                    if (subInfo.getSubscriptionId() != subId && !subInfo.isOpportunistic()) {
                        context.getSystemService(TelephonyManager.class).createForSubscriptionId(
                                subInfo.getSubscriptionId()).setDataEnabled(false);
                    }
                }
            }
        }
        mWorkerHandler.post(() -> setMergedCarrierWifiEnabledIfNeed(subId, enabled));
    }

    void setAutoDataSwitchMobileDataPolicy(int subId, boolean enable) {
        TelephonyManager tm = mSubIdTelephonyManagerMap.getOrDefault(subId, mTelephonyManager);
        if (tm == null) {
            if (DEBUG) {
                Log.d(TAG, "TelephonyManager is null, can not set mobile data.");
            }
            return;
        }
        tm.setMobileDataPolicyEnabled(TelephonyManager.MOBILE_DATA_POLICY_AUTO_DATA_SWITCH, enable);
    }

    boolean isDataStateInService(int subId) {
        final ServiceState serviceState = mSubIdServiceState.getOrDefault(subId,
                new ServiceState());
        NetworkRegistrationInfo regInfo =
                (serviceState == null) ? null : serviceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        return (regInfo == null) ? false : regInfo.isRegistered();
    }

    boolean isVoiceStateInService(int subId) {
        if (mTelephonyManager == null) {
            if (DEBUG) {
                Log.d(TAG, "TelephonyManager is null, can not detect voice state.");
            }
            return false;
        }

        final ServiceState serviceState = mSubIdServiceState.getOrDefault(subId,
                new ServiceState());
        return serviceState != null && serviceState.getState() == serviceState.STATE_IN_SERVICE;
    }

// QTI_BEGIN: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
    /**
     * Return {@code true} if Smart DDS Switch feature is available
     */
    boolean isSmartDdsSwitchFeatureAvailable() {
        return mIsSmartDdsSwitchFeatureAvailable;
    }

// QTI_END: 2023-03-31: Telephony: Fix internet dialog behaviour during temp DDS switch
    public boolean isDeviceLocked() {
        return !mKeyguardStateController.isUnlocked();
    }

    int getCurrentSatelliteState() {
        return mCurrentSatelliteState;
    }

    boolean activeNetworkIsCellular() {
        if (mConnectivityManager == null) {
            if (DEBUG) {
                Log.d(TAG, "ConnectivityManager is null, can not check active network.");
            }
            return false;
        }

        final Network activeNetwork = mConnectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            Log.d(TAG, "getActiveNetwork is null.");
            return false;
        }
        final NetworkCapabilities networkCapabilities = mConnectivityManager.getNetworkCapabilities(
                activeNetwork);
        if (networkCapabilities == null) {
            return false;
        }
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    boolean connect(WifiEntry ap) {
        if (ap == null) {
            if (DEBUG) {
                Log.d(TAG, "No Wi-Fi ap to connect.");
            }
            return false;
        }

        if (ap.getWifiConfiguration() != null) {
            if (DEBUG) {
                Log.d(TAG, "connect networkId=" + ap.getWifiConfiguration().networkId);
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "connect to unsaved network " + ap.getTitle());
            }
        }
        ap.connect(new WifiEntryConnectCallback(mActivityStarter, ap, this));
        return false;
    }

    @WorkerThread
    boolean isWifiScanEnabled() {
        if (!mLocationController.isLocationEnabled()) {
            return false;
        }
        return mWifiManager != null && mWifiManager.isScanAlwaysAvailable();
    }

    static class WifiEntryConnectCallback implements WifiEntry.ConnectCallback {
        final ActivityStarter mActivityStarter;
        final WifiEntry mWifiEntry;
        final InternetDetailsContentController mInternetDetailsContentController;

        WifiEntryConnectCallback(ActivityStarter activityStarter, WifiEntry connectWifiEntry,
                InternetDetailsContentController internetDetailsContentController) {
            mActivityStarter = activityStarter;
            mWifiEntry = connectWifiEntry;
            mInternetDetailsContentController = internetDetailsContentController;
        }

        @Override
        public void onConnectResult(@ConnectStatus int status) {
            if (DEBUG) {
                Log.d(TAG, "onConnectResult " + status);
            }

            if (status == WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_NO_CONFIG) {
                final Intent intent = WifiUtils.getWifiDialogIntent(mWifiEntry.getKey(),
                        true /* connectForCaller */);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivityStarter.startActivity(intent, false /* dismissShade */);
            } else if (status == CONNECT_STATUS_FAILURE_UNKNOWN) {
                mInternetDetailsContentController.makeOverlayToast(
                        R.string.wifi_failed_connect_message);
            } else {
                if (DEBUG) {
                    Log.d(TAG, "connect failure reason=" + status);
                }
            }
        }
    }

    private void scanWifiAccessPoints() {
        if (mCanConfigWifi) {
            mAccessPointController.scanForAccessPoints();
        }
    }

    @Override
    @WorkerThread
    public void onAccessPointsChanged(List<WifiEntry> accessPoints) {
        if (!mCanConfigWifi) {
            return;
        }

        WifiEntry connectedEntry = null;
        List<WifiEntry> wifiEntries = null;
        final int accessPointsSize = (accessPoints == null) ? 0 : accessPoints.size();
        final boolean hasMoreWifiEntries = (accessPointsSize > MAX_WIFI_ENTRY_COUNT);
        if (accessPointsSize > 0) {
            wifiEntries = new ArrayList<>();
            final int count = QsWifiConfig.isEnabled() ? accessPointsSize
                    : hasMoreWifiEntries ? MAX_WIFI_ENTRY_COUNT : accessPointsSize;
            mConnectedWifiInternetMonitor.unregisterCallback();
            for (int i = 0; i < count; i++) {
                WifiEntry entry = accessPoints.get(i);
                mConnectedWifiInternetMonitor.registerCallbackIfNeed(entry);
                if (connectedEntry == null && entry.isDefaultNetwork()
                        && entry.hasInternetAccess()) {
                    connectedEntry = entry;
                } else {
                    wifiEntries.add(entry);
                }
            }
            mHasWifiEntries = true;
        } else {
            mHasWifiEntries = false;
        }

        if (mCallback != null) {
            mCallback.onAccessPointsChanged(wifiEntries, connectedEntry, hasMoreWifiEntries);
        }
    }

    @Override
    public void onSettingsActivityTriggered(Intent settingsIntent) {
    }

// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
    private void registerTelephonyCallbackOnNddsSub(int previousNddsSubId) {
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
        if (SubscriptionManager.isUsableSubscriptionId(mNddsSubId)) {
// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            boolean needUpdateCallback = false;
            if (previousNddsSubId != mNddsSubId) {
                if (mSubIdTelephonyCallbackMap.containsKey(previousNddsSubId)) {
                    unRegisterCallback(previousNddsSubId);
                    Log.d(TAG, "unregister for old  Ndds : " + previousNddsSubId);
                }
                needUpdateCallback = true;
            } else if (needChangeCallback()){
                needUpdateCallback = true;
            }

            if (needUpdateCallback) {
                if (mSubIdTelephonyCallbackMap.containsKey(mNddsSubId)) {
                    unRegisterCallback(mNddsSubId);
                    Log.d(TAG, "unregister old callback for new Ndds : " + mNddsSubId);
                }
                TelephonyCallback telephonyCallback = createNddsSubTelephonyCallback(mNddsSubId);
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
                TelephonyManager nDdsSubTm = mTelephonyManager.createForSubscriptionId(mNddsSubId);
                nDdsSubTm.registerTelephonyCallback(mExecutor, telephonyCallback);
                mSubIdTelephonyCallbackMap.put(mNddsSubId, telephonyCallback);
                mSubIdTelephonyManagerMap.put(mNddsSubId, nDdsSubTm);
// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
                Log.d(TAG, "register for nDDS: " + mNddsSubId);
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
            }
        } else {
            // Prune stale SUBs
            List<Integer> staleSubs = mSubIdTelephonyManagerMap.keySet()
                    .stream().filter(sub -> sub != mDefaultDataSubId).collect(Collectors.toList());
            for (Integer sub : staleSubs) {
                Log.d(TAG, "registerTelephonyCallOnNddsSub pruning on SUB: " + sub);
                TelephonyCallback oldTelephonyCallback = mSubIdTelephonyCallbackMap.get(sub);
                if (oldTelephonyCallback != null) {
                    mSubIdTelephonyManagerMap.get(sub)
                            .unregisterTelephonyCallback(oldTelephonyCallback);
                }
                mSubIdTelephonyManagerMap.remove(sub);
                mSubIdTelephonyCallbackMap.remove(sub);
                mSubIdTelephonyDisplayInfoMap.remove(sub);
            }
        }
    }

    private TelephonyCallback createNddsSubTelephonyCallback(int subId) {
        return isDualDataEnabled() ? new NonDdsInternetTelephonyCallback(subId)
                : new NonDdsCallStateCallback();
    }

// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
    private class NonDdsCallStateCallback extends TelephonyCallback implements
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
            TelephonyCallback.CallStateListener,
            TelephonyCallback.DataEnabledListener {
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed

        @Override
        public void onCallStateChanged(int callState) {
            Log.d(TAG, "onCallStateChanged: " + callState);
            mNonDdsCallState = callState;
// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
            if (mCallback != null) {
                mCallback.onNonDdsCallStateChanged(callState);
            }
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
        }
// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue

        @Override
        public void onDataEnabledChanged(boolean enabled,
                @TelephonyManager.DataEnabledChangedReason int reason) {
            mIsNddsDataEnabled = enabled;
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            if (mCallback != null) {
                mCallback.onDataEnabledChanged();
            }
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
            Log.d(TAG, "mIsNddsDataEnabled: " + mIsNddsDataEnabled);
       }
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
    }

    private class NonDdsInternetTelephonyCallback extends InternetTelephonyCallback
// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
            implements TelephonyCallback.CallStateListener,
            TelephonyCallback.DataEnabledListener {
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
        private NonDdsInternetTelephonyCallback(int subId) {
            super(subId);
        }

// QTI_END: 2023-07-11: Telephony: Fix summary of nDDS sub not got refreshed
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
        @Override
        public void onCallStateChanged(int callState) {
            Log.d(TAG, "onCallStateChanged: " + callState);
            mNonDdsCallState = callState;
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
            if (mCallback != null) {
                mCallback.onNonDdsCallStateChanged(callState);
            }
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
        }
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue

        @Override
        public void onDataEnabledChanged(boolean enabled,
                @TelephonyManager.DataEnabledChangedReason int reason) {
            mIsNddsDataEnabled = enabled;
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            if (mCallback != null) {
                mCallback.onDataEnabledChanged();
            }
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2024-03-11: Telephony: Fix exclamation show issue
            Log.d(TAG, "mIsNddsDataEnabled: " + mIsNddsDataEnabled);
       }
// QTI_END: 2024-03-11: Telephony: Fix exclamation show issue
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
    }

// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
    @Override
    public void onWifiScan(boolean isScan) {
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
        if (mCallback != null) {
            if (!isWifiEnabled() || isDeviceLocked()) {
                mCallback.onWifiScan(false);
                return;
            }
            mCallback.onWifiScan(isScan);
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
        }
    }

    private class InternetTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.DataEnabledListener, TelephonyCallback.DataConnectionStateListener,
            TelephonyCallback.DisplayInfoListener, TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener,
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
            TelephonyCallback.UserMobileDataStateListener,
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
            TelephonyCallback.CarrierNetworkListener,
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
            TelephonyCallback.ActiveDataSubscriptionIdListener {
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA

        private final int mSubId;

        private InternetTelephonyCallback(int subId) {
            mSubId = subId;
        }

        @Override
        public void onServiceStateChanged(@NonNull ServiceState serviceState) {
            if (mCallback != null) {
                mCallback.onServiceStateChanged(serviceState);
            }
            mSubIdServiceState.put(mSubId, serviceState);
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (mCallback != null) {
                mCallback.onDataConnectionStateChanged(state, networkType);
            }
        }

        @Override
        public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
            if (mCallback != null) {
                mCallback.onSignalStrengthsChanged(signalStrength);
            }
        }

        @Override
        public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
            mSubIdTelephonyDisplayInfoMap.put(mSubId, telephonyDisplayInfo);
            if (mCallback != null) {
                mCallback.onDisplayInfoChanged(telephonyDisplayInfo);
            }
        }

        @Override
        public void onUserMobileDataStateChanged(boolean enabled) {
            if (mCallback != null) {
                mCallback.onUserMobileDataStateChanged(enabled);
            }
        }

        @Override
        public void onCarrierNetworkChange(boolean active) {
            mCarrierNetworkChangeMode = active;
            if (mCallback != null) {
                mCallback.onCarrierNetworkChange(active);
            }
        }

// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            mActiveDataSubId = subId;
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
            if (mCallback != null) {
                mCallback.onTempDdsSwitchHappened();
            }
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
        }
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA

        @Override
        public void onDataEnabledChanged(boolean b, int i) {
            if (mSubId == mDefaultDataSubId) {
                mIsMobileDataEnabled = b;
            }
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            if (mCallback != null) {
                mCallback.onDataEnabledChanged();
            }
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
        }
    }

    private class InternetOnSubscriptionChangedListener extends
            SubscriptionManager.OnSubscriptionsChangedListener {
        InternetOnSubscriptionChangedListener() {
            super();
        }

        @Override
        public void onSubscriptionsChanged() {
            List<SubscriptionInfo> subInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
            int numberOfActiveSubscriptions = subInfos.size();
            /*
             * When there is only one subscription, there is no nDDS sub, so call state of nDDS is
             * idle by default. Ensure that call state of nDDS is correctly updated when number of
             * subscriptions change at runtime.
             */
            if (numberOfActiveSubscriptions == 1){
                Log.d(TAG, "Resetting call state of nDDS");
                mNonDdsCallState = TelephonyManager.CALL_STATE_IDLE;
            }

            refreshHasActiveSubIdOnDds();
            updateListener();
        }
    }

    private class DataConnectivityListener extends ConnectivityManager.NetworkCallback {
        @Override
        @WorkerThread
        public void onCapabilitiesChanged(@NonNull Network network,
                @NonNull NetworkCapabilities capabilities) {
            mHasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            if (mCanConfigWifi && (mHasEthernet || capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI))) {
                scanWifiAccessPoints();
            }
            // update UI
            if (mCallback != null) {
                mCallback.onCapabilitiesChanged(network, capabilities);
            }
        }

        @Override
        @WorkerThread
        public void onLost(@NonNull Network network) {
            mHasEthernet = false;
            if (mCallback != null) {
                mCallback.onLost(network);
            }
        }
    }

    /**
     * Helper class for monitoring the Internet access of the connected WifiEntry.
     */
    @VisibleForTesting
    protected class ConnectedWifiInternetMonitor implements WifiEntry.WifiEntryCallback {

        private WifiEntry mWifiEntry;

        public void registerCallbackIfNeed(WifiEntry entry) {
            if (entry == null || mWifiEntry != null) {
                return;
            }
            // If the Wi-Fi is not connected yet, or it's the connected Wi-Fi with Internet
            // access. Then we don't need to listen to the callback to update the Wi-Fi entries.
            if (entry.getConnectedState() != CONNECTED_STATE_CONNECTED || (entry.isDefaultNetwork()
                    && entry.hasInternetAccess())) {
                return;
            }
            mWifiEntry = entry;
            entry.setListener(this);
        }

        public void unregisterCallback() {
            if (mWifiEntry == null) {
                return;
            }
            mWifiEntry.setListener(null);
            mWifiEntry = null;
        }

        @MainThread
        @Override
        public void onUpdated() {
            if (mWifiEntry == null) {
                return;
            }
            WifiEntry entry = mWifiEntry;
            if (entry.getConnectedState() != CONNECTED_STATE_CONNECTED) {
                unregisterCallback();
                return;
            }
            if (entry.isDefaultNetwork() && entry.hasInternetAccess()) {
                unregisterCallback();
                // Trigger onAccessPointsChanged() to update the Wi-Fi entries.
                scanWifiAccessPoints();
            }
        }
    }

    /**
     * Return {@code true} If the Ethernet exists
     */
    @MainThread
    public boolean hasEthernet() {
        return mHasEthernet;
    }

    private final BroadcastReceiver mConnectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(action)) {
                if (DEBUG) {
                    Log.d(TAG, "ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
                }
                mConfig = MobileMappings.Config.readConfig(context);
                refreshHasActiveSubIdOnDds();
                updateListener();
            } else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)) {
                updateListener();
            }
        }
    };

    private void updateListener() {
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        updateFiveGStateMonitor();
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
        int defaultDataSubId = getDefaultDataSubscriptionId();
        if (mDefaultDataSubId == getDefaultDataSubscriptionId()) {
            if (DEBUG) {
                Log.d(TAG, "DDS: no change");
            }
// QTI_BEGIN: 2024-12-25: Android_UI: SystemUI: Fix nDDS layout display issue
            int lastNddsSubId = mNddsSubId;
// QTI_END: 2024-12-25: Android_UI: SystemUI: Fix nDDS layout display issue
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
            updateNddsSubId(defaultDataSubId);
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2024-12-25: Android_UI: SystemUI: Fix nDDS layout display issue
            if (lastNddsSubId != mNddsSubId && mCallback != null) {
                mCallback.onSubscriptionsChanged(defaultDataSubId);
            }
// QTI_END: 2024-12-25: Android_UI: SystemUI: Fix nDDS layout display issue
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "DDS: defaultDataSubId:" + defaultDataSubId);
        }

        if (SubscriptionManager.isUsableSubscriptionId(defaultDataSubId)) {
            // clean up old defaultDataSubId
            TelephonyCallback oldCallback = mSubIdTelephonyCallbackMap.get(mDefaultDataSubId);
            if (oldCallback != null) {
                mTelephonyManager.unregisterTelephonyCallback(oldCallback);
            } else if (DEBUG) {
                Log.e(TAG, "Unexpected null telephony call back for Sub " + mDefaultDataSubId);
            }
            mSubIdTelephonyCallbackMap.remove(mDefaultDataSubId);
            mSubIdTelephonyDisplayInfoMap.remove(mDefaultDataSubId);
            mSubIdTelephonyManagerMap.remove(mDefaultDataSubId);

// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            updateNddsSubId(defaultDataSubId);
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
            // create for new defaultDataSubId
            mTelephonyManager = mTelephonyManager.createForSubscriptionId(defaultDataSubId);
            mSubIdTelephonyManagerMap.put(defaultDataSubId, mTelephonyManager);
            registerInternetTelephonyCallback(mTelephonyManager, defaultDataSubId);
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
            if (mCallback != null) {
                mCallback.onSubscriptionsChanged(defaultDataSubId);
            }
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
        }
        mDefaultDataSubId = defaultDataSubId;
    }

// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    public int getNddsSubId() {
        return mNddsSubId;
    }

    private void handleDualDataUserPerferenceListener() {
        if (mHasDualDataCapability) {
            if (mDualDataContentObserver == null) {
                mDualDataContentObserver = new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        if (DUAL_DATA_USER_PREFERENCE.equals(uri)) {
                            notifyDualDataEnabledStateChanged();
                        }
                    }
                };
            }
            mContext.getContentResolver().registerContentObserver(DUAL_DATA_USER_PREFERENCE,
                    false, mDualDataContentObserver);
        } else {
            if (mDualDataContentObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mDualDataContentObserver);
                mDualDataContentObserver = null;
            }
        }
        notifyDualDataEnabledStateChanged();
    }

    private void notifyDualDataEnabledStateChanged() {
        updateNddsSubId(mDefaultDataSubId);
        final boolean isDualDataEnabled = isDualDataEnabled();
        Log.d(TAG, "Ndds sub ID: " + mNddsSubId + " isDualDataEnabled: " + isDualDataEnabled);
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2024-03-03: Android_UI: Prevent NPE from condition checking
        if (mCallback != null) {
            mCallback.onDualDataEnabledStateChanged();
        }
// QTI_END: 2024-03-03: Android_UI: Prevent NPE from condition checking
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    }

    private void updateNddsSubId(int defaultDataSubId) {
        // update mNddsSubId
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
        int previousNddsSubId = mNddsSubId;
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
        mNddsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        List<SubscriptionInfo> subInfos =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                if (subInfo.getSubscriptionId() != defaultDataSubId) {
                    mNddsSubId = subInfo.getSubscriptionId();
                }
            }
        }
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
        registerTelephonyCallbackOnNddsSub(previousNddsSubId);
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    }

    public boolean isDualDataEnabled() {
        return mHasDualDataCapability && Settings.Global.getInt(mContext.getContentResolver(),
                DUAL_DATA_PREFERENCE, 0) == 1;
    }

// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data
    boolean mayLaunchShareWifiSettings(WifiEntry wifiEntry, View view) {
        Intent intent = getConfiguratorQrCodeGeneratorIntentOrNull(wifiEntry);
        if (intent == null) {
            return false;
        }
        startActivity(intent, view);
        return true;
    }

// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
    private void registerFiveGStateMonitor() {
        List<SubscriptionInfo> subInfos =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                final FiveGStateMonitor monitor =
                        new FiveGStateMonitor(mFiveGServiceClient, subInfo);
                monitor.initiate();
                mSubIdFiveGStateMonitorMap.put(subInfo.getSubscriptionId(), monitor);
            }
        }
    }

    private void unregisterFiveGStateMonitor() {
        mSubIdFiveGStateMonitorMap.forEach((k, v) -> {
            v.deInitiate();
        });
        mSubIdFiveGStateMonitorMap.clear();
    }

    private void updateFiveGStateMonitor() {
        List<SubscriptionInfo> subInfos =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subInfos != null) {
            Map<Integer, FiveGStateMonitor> availableFiveGStateMonitors = new HashMap<>();
            for (SubscriptionInfo subInfo : subInfos) {
                if (mSubIdFiveGStateMonitorMap.containsKey(subInfo.getSubscriptionId())) {
                    availableFiveGStateMonitors.put(subInfo.getSubscriptionId(),
                            mSubIdFiveGStateMonitorMap.get(subInfo.getSubscriptionId()));
                    mSubIdFiveGStateMonitorMap.remove(subInfo.getSubscriptionId());
                } else {
                    final FiveGStateMonitor monitor =
                            new FiveGStateMonitor(mFiveGServiceClient, subInfo);
                    monitor.initiate();
                    availableFiveGStateMonitors.put(subInfo.getSubscriptionId(), monitor);
                }
            }
            unregisterFiveGStateMonitor(); // clear all invalid monitors
            mSubIdFiveGStateMonitorMap.putAll(availableFiveGStateMonitors);
            availableFiveGStateMonitors.clear();
        } else {
            unregisterFiveGStateMonitor();
        }
    }

    private FiveGServiceState getFiveGServiceState(int subId) {
        if (mSubIdFiveGStateMonitorMap.containsKey(subId)) {
            return mSubIdFiveGStateMonitorMap.get(subId).getFiveGServiceState();
        }
        return new FiveGServiceState();
    }

// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
    interface InternetDialogCallback {

        void onRefreshCarrierInfo();

        void onSimStateChanged();

        void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities);

        void onLost(@NonNull Network network);

        void onSubscriptionsChanged(int defaultDataSubId);

        void onServiceStateChanged(ServiceState serviceState);

        void onDataConnectionStateChanged(int state, int networkType);

        void onSignalStrengthsChanged(SignalStrength signalStrength);

        void onUserMobileDataStateChanged(boolean enabled);

        void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo);

        void onCarrierNetworkChange(boolean active);

        void dismissDialog();

        void onAccessPointsChanged(@Nullable List<WifiEntry> wifiEntries,
                @Nullable WifiEntry connectedEntry, boolean hasMoreWifiEntries);
// QTI_BEGIN: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data

        void onNonDdsCallStateChanged(int callState);
// QTI_END: 2022-03-27: Telephony: Add conditions to prevent disabling of mobile data
// QTI_BEGIN: 2022-11-22: Telephony: Add DDS cases's handling for DSDA

        void onTempDdsSwitchHappened();
// QTI_END: 2022-11-22: Telephony: Add DDS cases's handling for DSDA
// QTI_BEGIN: 2023-06-25: Telephony: Add an additional mobile data button support for dual data

        void onDualDataEnabledStateChanged();
// QTI_END: 2023-06-25: Telephony: Add an additional mobile data button support for dual data

        void onWifiScan(boolean isScan);

        void onSatelliteModemStateChanged(int state);

// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
        void onFiveGStateOverride();
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.

        default void onDataEnabledChanged() {}
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
    }

    private class FiveGStateMonitor implements IFiveGStateListener {

        private final FiveGServiceClient mClient;
        private final SubscriptionInfo mSubscriptionInfo;
        private FiveGServiceState mState;

        public FiveGStateMonitor(FiveGServiceClient client, SubscriptionInfo subscriptionInfo) {
            mClient = client;
            mSubscriptionInfo = subscriptionInfo;
        }

        public void initiate() {
            if (mClient != null) {
                mClient.registerListener(mSubscriptionInfo.getSimSlotIndex(), this);
            }
        }

        public void deInitiate() {
            if (mClient != null) {
                mClient.unregisterListener(mSubscriptionInfo.getSimSlotIndex(), this);
            }
        }

        public FiveGServiceState getFiveGServiceState() {
            if (mState != null) {
                return mState;
            }
            return new FiveGServiceState();
        }

        @Override
        public void onStateChanged(FiveGServiceState state) {
            mState = state;
            Log.d(TAG, "Five G state update on SUB " + mSubscriptionInfo.getSubscriptionId()
                    + " by " + mState);
            if (mCallback != null) {
                mCallback.onFiveGStateOverride();
            }
        }
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog


    }

    void makeOverlayToast(int stringId) {
        final Context displaySpecificContext = mShadeDialogContextInteractor.getContext();
        final Resources res = displaySpecificContext.getResources();

        final SystemUIToast systemUIToast = mToastFactory.createToast(mContext,
            displaySpecificContext,
            res.getString(stringId), mContext.getPackageName(), UserHandle.myUserId(),
            res.getConfiguration().orientation);
        if (systemUIToast == null) {
            return;
        }

        View toastView = systemUIToast.getView();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.format = PixelFormat.TRANSLUCENT;
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.y = systemUIToast.getYOffset();

        int absGravity = Gravity.getAbsoluteGravity(systemUIToast.getGravity(),
                res.getConfiguration().getLayoutDirection());
        params.gravity = absGravity;
        if ((absGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) {
            params.horizontalWeight = TOAST_PARAMS_HORIZONTAL_WEIGHT;
        }
        if ((absGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) {
            params.verticalWeight = TOAST_PARAMS_VERTICAL_WEIGHT;
        }
        WindowManager displayWm = displaySpecificContext.getSystemService(WindowManager.class);
        displayWm.addView(toastView, params);

        Animator inAnimator = systemUIToast.getInAnimation();
        if (inAnimator != null) {
            inAnimator.start();
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Animator outAnimator = systemUIToast.getOutAnimation();
                if (outAnimator != null) {
                    outAnimator.start();
                    outAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mWindowManager.removeViewImmediate(toastView);
                        }
                    });
                }
            }
        }, SHORT_DURATION_TIMEOUT);
    }

    /** @return  true if the current user is at the login screen. */
    @VisibleForTesting
    boolean isHeadlessSystemUser() {
        int userId = mUserRepository.getSelectedUserInfo().id;
        return UserManager.isHeadlessSystemUserMode()
                && userId == UserHandle.USER_SYSTEM;
    }

    /** @return  true if the current user is a guest. */
    @VisibleForTesting
    boolean isGuestUser() {
        UserInfo userInfo = mUserRepository.getSelectedUserInfo();
        return userInfo != null && userInfo.isGuest();
    }

    private boolean isNetworkOwner(@NonNull WifiConfiguration config) {
        int currentUserId = mUserRepository.getSelectedUserInfo().id;
        int creatorUserId = config.getCreatorUserId();

        // Network is "owned" if it's a single-user device OR if the creator
        // matches the current user
        return !mHasMultipleFullUsers || (currentUserId == creatorUserId);
    }

    Intent getConfiguratorQrCodeGeneratorIntentOrNull(WifiEntry wifiEntry) {
        if (!mFeatureFlags.isEnabled(Flags.SHARE_WIFI_QS_BUTTON) || wifiEntry == null
                || mWifiManager == null || !wifiEntry.canShare()) {
            return null;
        }
        var wifiConfiguration = wifiEntry.getWifiConfiguration();
        if (wifiConfiguration == null) {
            return null;
        }
        if (isGuestUser() || isHeadlessSystemUser()
                || !isNetworkOwner(wifiConfiguration)) {
            return null;
        }
        Intent intent = new Intent();
        intent.setAction(WifiDppIntentHelper.ACTION_CONFIGURATOR_AUTH_QR_CODE_GENERATOR);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        WifiDppIntentHelper.setConfiguratorIntentExtra(intent, mWifiManager, wifiConfiguration);
        return intent;
    }
// QTI_BEGIN: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.

    private void unRegisterCallback (int subId) {
        TelephonyCallback callback = mSubIdTelephonyCallbackMap.get(subId);
        TelephonyManager telephonyManager = mSubIdTelephonyManagerMap.get(subId);
        if (callback != null && telephonyManager != null) {
            telephonyManager.unregisterTelephonyCallback(callback);

        }
        mSubIdTelephonyCallbackMap.remove(subId);
    }

    private boolean needChangeCallback() {
        TelephonyCallback oldCallback = mSubIdTelephonyCallbackMap.get(mNddsSubId);
        if (oldCallback != null) {
            return oldCallback.getClass() != (isDualDataEnabled() ?
                    NonDdsInternetTelephonyCallback.class : NonDdsCallStateCallback.class);
        }
        return true;
    }
// QTI_END: 2025-02-27: Android_UI: SystemUI: Fixed Internet dialog UI issue.
}
