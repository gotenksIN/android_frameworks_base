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

/* Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2022-2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
package com.android.systemui.qs.tiles.dialog;

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM;

import static com.android.settingslib.satellite.SatelliteDialogUtils.TYPE_IS_WIFI;
import static com.android.systemui.Prefs.Key.QS_HAS_TURNED_OFF_MOBILE_DATA;
import static com.android.systemui.qs.tiles.dialog.InternetDetailsContentController.MAX_WIFI_ENTRY_COUNT;

import static com.qti.extphone.ExtPhoneCallbackListener.EVENT_ON_CIWLAN_CONFIG_CHANGE;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.telephony.flags.Flags;
import com.android.settingslib.satellite.SatelliteDialogUtils;
import com.android.settingslib.wifi.WifiEnterpriseRestrictionUtils;
import com.android.systemui.Prefs;
import com.android.systemui.accessibility.floatingmenu.AnnotationLinkSpan;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.qs.flags.QsDetailedView;
import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeDisplayAware;
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor;
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.wifitrackerlib.WifiEntry;

import com.qti.extphone.CiwlanConfig;
import com.qti.extphone.Client;
import com.qti.extphone.ExtPhoneCallbackListener;
import com.qti.extphone.ExtTelephonyManager;
import com.qti.extphone.ServiceCallback;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Dialog for showing mobile network, connected Wi-Fi network and Wi-Fi networks.
 */
public class InternetDialogDelegateLegacy implements
        SystemUIDialog.Delegate,
        InternetDetailsContentController.InternetDialogCallback {
    private static final String TAG = "InternetDialog";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String ABOVE_STATUS_BAR = "above_status_bar";
    private static final String CAN_CONFIG_MOBILE_DATA = "can_config_mobile_data";
    private static final String CAN_CONFIG_WIFI = "can_config_wifi";

    static final int MAX_NETWORK_COUNT = 4;

    private final Handler mHandler;
    private final Executor mBackgroundExecutor;
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final boolean mAboveStatusBar;
    private final SystemUIDialog.Factory mSystemUIDialogFactory;
    private final ShadeDialogContextInteractor mShadeDialogContextInteractor;

    @VisibleForTesting
    protected InternetAdapter mAdapter;
    @VisibleForTesting
    protected View mDialogView;
    @VisibleForTesting
    protected boolean mCanConfigWifi;

    private final InternetDialogManager mInternetDialogManager;
    private ImsManager mImsManager;
    private TelephonyManager mTelephonyManager;
    @Nullable
    private AlertDialog mAlertDialog;
    private final UiEventLogger mUiEventLogger;
    private final InternetDetailsContentController mInternetDetailsContentController;
    private TextView mInternetDialogTitle;
    private TextView mInternetDialogSubTitle;
    private View mDivider;
    private ProgressBar mProgressBar;
    private LinearLayout mConnectedWifListLayout;
    private LinearLayout mMobileNetworkLayout;
    private LinearLayout mSecondaryMobileNetworkLayout;
    private LinearLayout mTurnWifiOnLayout;
    private LinearLayout mEthernetLayout;
    private TextView mWifiToggleTitleText;
    private LinearLayout mWifiScanNotifyLayout;
    private TextView mWifiScanNotifyText;
    private LinearLayout mSeeAllLayout;
    private RecyclerView mWifiRecyclerView;
    private ImageView mConnectedWifiIcon;
    private ImageView mWifiSettingsIcon;
    private TextView mConnectedWifiTitleText;
    private TextView mConnectedWifiSummaryText;
    private ImageView mSignalIcon;
    private TextView mMobileTitleText;
    private TextView mMobileSummaryText;
    private TextView mAirplaneModeSummaryText;
    private Switch mMobileDataToggle;
    private Switch mSecondaryMobileDataToggle;
    private View mMobileToggleDivider;
    private Switch mWiFiToggle;
    private Button mDoneButton;

    @VisibleForTesting
    protected Button mShareWifiButton;
    private Button mAirplaneModeButton;
    private Drawable mBackgroundOn;
    private Drawable mSecondaryBackgroundOn;
    private final KeyguardStateController mKeyguard;
    @Nullable
    private Drawable mBackgroundOff = null;
    private int mDefaultDataSubId;
    private int mNddsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private boolean mCanConfigMobileData;
    private boolean mCanChangeWifiState;
    // Wi-Fi entries
    private int mWifiNetworkHeight;
    @Nullable
    @VisibleForTesting
    protected WifiEntry mConnectedWifiEntry;
    @VisibleForTesting
    protected int mWifiEntriesCount;
    @VisibleForTesting
    protected boolean mHasMoreWifiEntries;

    // Wi-Fi scanning progress bar
    protected boolean mIsProgressBarVisible;
    private SystemUIDialog mDialog;
    private final CoroutineScope mCoroutineScope;
    @Nullable
    private Job mClickJob;

    private static String mPackageName;
    private ExtTelephonyManager mExtTelephonyManager;
    private boolean mExtTelServiceConnected = false;
    private Client mClient;
    private CiwlanConfig mCiwlanConfig = null;
    private CiwlanConfig mNddsCiwlanConfig = null;
    private SparseBooleanArray mIsSubInCall;
    private SparseBooleanArray mIsCiwlanModeSupported;
    private SparseBooleanArray mIsCiwlanEnabled;
    private SparseBooleanArray mIsInCiwlanOnlyMode;
    private SparseBooleanArray mIsImsRegisteredOnCiwlan;
    private ServiceCallback mExtTelServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephony service connected");
            mExtTelServiceConnected = true;
            int[] events = new int[] {EVENT_ON_CIWLAN_CONFIG_CHANGE};
            mClient = mExtTelephonyManager.registerCallbackWithEvents(mPackageName,
                    mExtPhoneCallbackListener, events);
            Log.d(TAG, "Client = " + mClient);
            // Query the C_IWLAN config
            try {
                mCiwlanConfig = mExtTelephonyManager.getCiwlanConfig(
                        SubscriptionManager.getSlotIndex(mDefaultDataSubId));
                mNddsCiwlanConfig = mExtTelephonyManager.getCiwlanConfig(
                        SubscriptionManager.getSlotIndex(mNddsSubId));
            } catch (RemoteException ex) {
                Log.e(TAG, "getCiwlanConfig exception", ex);
            }
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephony service disconnected");
            mExtTelServiceConnected = false;
            mClient = null;
        }
    };

    private ExtPhoneCallbackListener mExtPhoneCallbackListener = new ExtPhoneCallbackListener() {
        @Override
        public void onCiwlanConfigChange(int slotId, CiwlanConfig ciwlanConfig) {
            Log.d(TAG, "onCiwlanConfigChange: slotId = " + slotId + ", config = " + ciwlanConfig);
            if (SubscriptionManager.getSubscriptionId(slotId) == mDefaultDataSubId) {
                mCiwlanConfig = ciwlanConfig;
            } else {
                mNddsCiwlanConfig = ciwlanConfig;
            }
        }
    };

    // These are to reduce the UI janky frame duration. b/323286540
    private LifecycleRegistry mLifecycleRegistry;
    @VisibleForTesting
    LifecycleOwner mLifecycleOwner;
    @VisibleForTesting
    MutableLiveData<InternetContent> mDataInternetContent = new MutableLiveData<>();

    @AssistedFactory
    public interface Factory {
        InternetDialogDelegateLegacy create(
                @Assisted(ABOVE_STATUS_BAR) boolean aboveStatusBar,
                @Assisted(CAN_CONFIG_MOBILE_DATA) boolean canConfigMobileData,
                @Assisted(CAN_CONFIG_WIFI) boolean canConfigWifi,
                @Assisted CoroutineScope coroutineScope);
    }

    @AssistedInject
    public InternetDialogDelegateLegacy(
            @ShadeDisplayAware Context context,
            InternetDialogManager internetDialogManager,
            InternetDetailsContentController internetDetailsContentController,
            @Assisted(CAN_CONFIG_MOBILE_DATA) boolean canConfigMobileData,
            @Assisted(CAN_CONFIG_WIFI) boolean canConfigWifi,
            @Assisted(ABOVE_STATUS_BAR) boolean aboveStatusBar,
            @Assisted CoroutineScope coroutineScope,
            UiEventLogger uiEventLogger,
            DialogTransitionAnimator dialogTransitionAnimator,
            @Main Handler handler,
            @Background Executor executor,
            KeyguardStateController keyguardStateController,
            SystemUIDialog.Factory systemUIDialogFactory,
            ShadeDialogContextInteractor shadeDialogContextInteractor,
            ShadeModeInteractor shadeModeInteractor) {
        // TODO (b/393628355): remove this after the details view is supported for single shade.
        if (shadeModeInteractor.isDualShade()){
            // If `QsDetailedView` is enabled, it should show the details view.
            QsDetailedView.assertInLegacyMode();
        }

        mAboveStatusBar = aboveStatusBar;
        mSystemUIDialogFactory = systemUIDialogFactory;
        mShadeDialogContextInteractor = shadeDialogContextInteractor;
        if (DEBUG) {
            Log.d(TAG, "Init InternetDialog");
        }

        // Save the context that is wrapped with our theme.
        mHandler = handler;
        mBackgroundExecutor = executor;
        mInternetDialogManager = internetDialogManager;
        mInternetDetailsContentController = internetDetailsContentController;
        mDefaultDataSubId = mInternetDetailsContentController.getDefaultDataSubscriptionId();
        mNddsSubId = getNddsSubId();
        mCanConfigMobileData = canConfigMobileData;
        mCanConfigWifi = canConfigWifi;
        mCanChangeWifiState = WifiEnterpriseRestrictionUtils.isChangeWifiStateAllowed(context);
        mKeyguard = keyguardStateController;
        mImsManager = context.getSystemService(ImsManager.class);
        mTelephonyManager = mInternetDetailsContentController.getTelephonyManager();
        mCoroutineScope = coroutineScope;
        mUiEventLogger = uiEventLogger;
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mAdapter = new InternetAdapter(mInternetDetailsContentController, coroutineScope);
        mPackageName = this.getClass().getPackage().toString();
        mExtTelephonyManager = ExtTelephonyManager.getInstance(context);
    }

    @Override
    public SystemUIDialog createDialog() {
        SystemUIDialog dialog = mSystemUIDialogFactory.create(this,
                mShadeDialogContextInteractor.getContext());
        if (!mAboveStatusBar) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }

        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = dialog;
        mLifecycleOwner = new LifecycleOwner() {
            @NonNull
            @Override
            public Lifecycle getLifecycle() {
                return mLifecycleRegistry;
            }
        };
        mLifecycleRegistry = new LifecycleRegistry(mLifecycleOwner);

        return dialog;
    }

    @Override
    public void onCreate(SystemUIDialog dialog, Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }
        Context context = dialog.getContext();
        mUiEventLogger.log(InternetDialogEvent.INTERNET_DIALOG_SHOW);
        mDialogView = LayoutInflater.from(context).inflate(
                R.layout.internet_connectivity_dialog, null);
        mDialogView.setAccessibilityPaneTitle(
                context.getText(R.string.accessibility_desc_quick_settings));
        final Window window = dialog.getWindow();
        window.setContentView(mDialogView);

        window.setWindowAnimations(R.style.Animation_InternetDialog);

        mWifiNetworkHeight = context.getResources()
                .getDimensionPixelSize(R.dimen.internet_dialog_wifi_network_height);
        mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        mDataInternetContent.observe(
                mLifecycleOwner, (internetContent) -> updateDialogUI(internetContent));
        mInternetDialogTitle = mDialogView.requireViewById(R.id.internet_dialog_title);
        mInternetDialogSubTitle = mDialogView.requireViewById(R.id.internet_dialog_subtitle);
        mDivider = mDialogView.requireViewById(R.id.divider);
        mProgressBar = mDialogView.requireViewById(R.id.wifi_searching_progress);
        mEthernetLayout = mDialogView.requireViewById(R.id.ethernet_layout);
        mMobileNetworkLayout = mDialogView.requireViewById(R.id.mobile_network_layout);
        mTurnWifiOnLayout = mDialogView.requireViewById(R.id.turn_on_wifi_layout);
        mWifiToggleTitleText = mDialogView.requireViewById(R.id.wifi_toggle_title);
        mWifiScanNotifyLayout = mDialogView.requireViewById(R.id.wifi_scan_notify_layout);
        mWifiScanNotifyText = mDialogView.requireViewById(R.id.wifi_scan_notify_text);
        mConnectedWifListLayout = mDialogView.requireViewById(R.id.wifi_connected_layout);
        mConnectedWifiIcon = mDialogView.requireViewById(R.id.wifi_connected_icon);
        mConnectedWifiTitleText = mDialogView.requireViewById(R.id.wifi_connected_title);
        mConnectedWifiSummaryText = mDialogView.requireViewById(R.id.wifi_connected_summary);
        mWifiSettingsIcon = mDialogView.requireViewById(R.id.wifi_settings_icon);
        mWifiRecyclerView = mDialogView.requireViewById(R.id.wifi_list_layout);
        mSeeAllLayout = mDialogView.requireViewById(R.id.see_all_layout);
        mDoneButton = mDialogView.requireViewById(R.id.done_button);
        mShareWifiButton = mDialogView.requireViewById(R.id.share_wifi_button);
        mAirplaneModeButton = mDialogView.requireViewById(R.id.apm_button);
        mSignalIcon = mDialogView.requireViewById(R.id.signal_icon);
        mMobileTitleText = mDialogView.requireViewById(R.id.mobile_title);
        mMobileSummaryText = mDialogView.requireViewById(R.id.mobile_summary);
        mAirplaneModeSummaryText = mDialogView.requireViewById(R.id.airplane_mode_summary);
        mMobileToggleDivider = mDialogView.requireViewById(R.id.mobile_toggle_divider);
        mMobileDataToggle = mDialogView.requireViewById(R.id.mobile_toggle);
        mWiFiToggle = mDialogView.requireViewById(R.id.wifi_toggle);
        mBackgroundOn = context.getDrawable(R.drawable.settingslib_switch_bar_bg_on);
        mInternetDialogTitle.setText(getDialogTitleText());
        mInternetDialogTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mBackgroundOff = context.getDrawable(R.drawable.internet_dialog_selected_effect);
        mSecondaryBackgroundOn = mBackgroundOn.getConstantState().newDrawable().mutate();
        setOnClickListener(dialog);
        mTurnWifiOnLayout.setBackground(null);
        mAirplaneModeButton.setVisibility(
                mInternetDetailsContentController.isAirplaneModeEnabled() ? View.VISIBLE
                        : View.GONE);
        mWifiRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mWifiRecyclerView.setAdapter(mAdapter);

        updateDialogUI(getWifiNetworkContent());
    }

    @Override
    public void onStart(SystemUIDialog dialog) {
        if (DEBUG) {
            Log.d(TAG, "onStart");
        }
        if (!mExtTelServiceConnected) {
            mExtTelephonyManager.connectService(mExtTelServiceCallback);
        }

        mLifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);

        mInternetDetailsContentController.onStart(this, mCanConfigWifi);
        if (!mCanConfigWifi) {
            hideWifiViews();
        }
    }

    @VisibleForTesting
    void hideWifiViews() {
        setProgressBarVisible(false);
        mTurnWifiOnLayout.setVisibility(View.GONE);
        mConnectedWifListLayout.setVisibility(View.GONE);
        mWifiRecyclerView.setVisibility(View.GONE);
        mSeeAllLayout.setVisibility(View.GONE);
        mShareWifiButton.setVisibility(View.GONE);
    }

    @Override
    public void onStop(SystemUIDialog dialog) {
        if (DEBUG) {
            Log.d(TAG, "onStop");
        }
        if (mExtTelServiceConnected) {
            mExtTelephonyManager.disconnectService(mExtTelServiceCallback);
        }
        mLifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        mMobileNetworkLayout.setOnClickListener(null);
        mConnectedWifListLayout.setOnClickListener(null);
        if (mSecondaryMobileNetworkLayout != null) {
            mSecondaryMobileNetworkLayout.setOnClickListener(null);
        }
        mSeeAllLayout.setOnClickListener(null);
        mWiFiToggle.setOnCheckedChangeListener(null);
        mDoneButton.setOnClickListener(null);
        mShareWifiButton.setOnClickListener(null);
        mAirplaneModeButton.setOnClickListener(null);
        mInternetDetailsContentController.onStop();
        mInternetDialogManager.destroyDialog();
        if (mSecondaryMobileDataToggle != null) {
            mSecondaryMobileDataToggle.setOnCheckedChangeListener(null);
        }
    }

    @Override
    public void dismissDialog() {
        if (DEBUG) {
            Log.d(TAG, "dismissDialog");
        }
        mInternetDialogManager.destroyDialog();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    /**
     * Update the internet dialog when receiving the callback.
     *
     * @param shouldUpdateMobileNetwork {@code true} for update the mobile network layout,
     *                                  otherwise {@code false}.
     */
    void updateDialog(boolean shouldUpdateMobileNetwork) {
        mBackgroundExecutor.execute(() -> {
            mDataInternetContent.postValue(getInternetContent(shouldUpdateMobileNetwork));
        });
    }

    private void updateDialogUI(InternetContent internetContent) {
        if (DEBUG) {
            Log.d(TAG, "updateDialog ");
        }

        mInternetDialogTitle.setText(internetContent.mInternetDialogTitleString);
        mInternetDialogSubTitle.setText(internetContent.mInternetDialogSubTitle);
        mAirplaneModeButton.setVisibility(
                internetContent.mIsAirplaneModeEnabled ? View.VISIBLE : View.GONE);

        updateEthernet(internetContent);
        setMobileDataLayout(internetContent);

        if (!mCanConfigWifi) {
            return;
        }
        updateWifiToggle(internetContent);
        updateConnectedWifi(internetContent);
        updateWifiListAndSeeAll(internetContent);
        updateWifiScanNotify(internetContent);
    }

    private InternetContent getInternetContent(boolean shouldUpdateMobileNetwork) {
        InternetContent internetContent = new InternetContent();
        internetContent.mShouldUpdateMobileNetwork = shouldUpdateMobileNetwork;
        internetContent.mInternetDialogTitleString = getDialogTitleText();
        internetContent.mInternetDialogSubTitle = getSubtitleText();
        if (shouldUpdateMobileNetwork) {
            internetContent.mActiveNetworkIsCellular =
                    mInternetDetailsContentController.activeNetworkIsCellular();
            internetContent.mIsCarrierNetworkActive =
                    mInternetDetailsContentController.isCarrierNetworkActive();
        }
        internetContent.mIsAirplaneModeEnabled =
                mInternetDetailsContentController.isAirplaneModeEnabled();
        internetContent.mHasEthernet = mInternetDetailsContentController.hasEthernet();
        internetContent.mIsWifiEnabled = mInternetDetailsContentController.isWifiEnabled();
        internetContent.mHasActiveSubIdOnDds =
                mInternetDetailsContentController.hasActiveSubIdOnDds();
        internetContent.mIsDeviceLocked = mInternetDetailsContentController.isDeviceLocked();
        internetContent.mIsWifiScanEnabled = mInternetDetailsContentController.isWifiScanEnabled();
        internetContent.mActiveAutoSwitchNonDdsSubId =
                mInternetDetailsContentController.getActiveAutoSwitchNonDdsSubId();
        return internetContent;
    }

    private InternetContent getWifiNetworkContent() {
        InternetContent internetContent = new InternetContent();
        internetContent.mInternetDialogTitleString = getDialogTitleText();
        internetContent.mInternetDialogSubTitle = getSubtitleText();
        internetContent.mIsWifiEnabled = mInternetDetailsContentController.isWifiEnabled();
        internetContent.mIsDeviceLocked = mInternetDetailsContentController.isDeviceLocked();
        return internetContent;
    }

    private void setOnClickListener(SystemUIDialog dialog) {
        mMobileNetworkLayout.setOnClickListener(v -> {
            // Do not show auto data switch dialog if Smart DDS Switch feature is available
            if (!mInternetDetailsContentController.isSmartDdsSwitchFeatureAvailable()) {
                int autoSwitchNonDdsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                if (mDataInternetContent.getValue() != null) {
                    autoSwitchNonDdsSubId =
                            mDataInternetContent.getValue().mActiveAutoSwitchNonDdsSubId;
                }
                if (autoSwitchNonDdsSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    showTurnOffAutoDataSwitchDialog(dialog, autoSwitchNonDdsSubId);
                }
            }
            mInternetDetailsContentController.connectCarrierNetwork();
        });
        mMobileDataToggle.setOnClickListener(v -> {
            boolean isChecked = mMobileDataToggle.isChecked();
            if (!isChecked && shouldShowMobileDialog(mDefaultDataSubId)) {
                mMobileDataToggle.setChecked(true);
                showTurnOffMobileDialog(mDefaultDataSubId);
            } else if (mInternetDetailsContentController.isMobileDataEnabled(mDefaultDataSubId) != isChecked) {
                mInternetDetailsContentController.setMobileDataEnabled(
                        dialog.getContext(), mDefaultDataSubId, isChecked, false);
            }
        });
        mConnectedWifListLayout.setOnClickListener(this::onClickConnectedWifi);
        mSeeAllLayout.setOnClickListener(this::onClickSeeMoreButton);
        mWiFiToggle.setOnClickListener(v -> {
            handleWifiToggleClicked(mWiFiToggle.isChecked());
        });
        mDoneButton.setOnClickListener(v -> dialog.dismiss());
        mShareWifiButton.setOnClickListener(v -> {
            if (mInternetDetailsContentController.mayLaunchShareWifiSettings(mConnectedWifiEntry,
                    v)) {
                mUiEventLogger.log(InternetDialogEvent.SHARE_WIFI_QS_BUTTON_CLICKED);
            }
        });
        mAirplaneModeButton.setOnClickListener(v -> {
            mInternetDetailsContentController.setAirplaneModeDisabled();
        });
    }

    private void handleWifiToggleClicked(boolean isChecked) {
        if (Flags.oemEnabledSatelliteFlag()) {
            if (mClickJob != null && !mClickJob.isCompleted()) {
                return;
            }
            mClickJob = SatelliteDialogUtils.mayStartSatelliteWarningDialog(
                    mDialog.getContext(), mCoroutineScope, TYPE_IS_WIFI, isAllowClick -> {
                        if (isAllowClick) {
                            setWifiEnable(isChecked);
                        } else {
                            mWiFiToggle.setChecked(!isChecked);
                        }
                        return null;
                    });
            return;
        }
        setWifiEnable(isChecked);
    }

    private void setWifiEnable(boolean isChecked) {
        if (mInternetDetailsContentController.isWifiEnabled() == isChecked) {
            return;
        }
        mInternetDetailsContentController.setWifiEnabled(isChecked);
    }

    @MainThread
    private void updateEthernet(InternetContent internetContent) {
        mEthernetLayout.setVisibility(
                internetContent.mHasEthernet ? View.VISIBLE : View.GONE);
    }

    /**
     * Do not allow the user to disable mobile data of DDS while there is an active
     * call on the nDDS.
     * Whether device works under DSDA or DSDS mode, if temp DDS switch has happened,
     * disabling mobile data won't be allowed.
     */
    private boolean shouldDisallowUserToDisableDdsMobileData() {
        return mInternetDetailsContentController.isMobileDataEnabled(mDefaultDataSubId)
                && !mInternetDetailsContentController.isNonDdsCallStateIdle()
                && mInternetDetailsContentController.isTempDdsHappened();
    }

    private void setMobileDataLayout(InternetContent internetContent) {
        if (!internetContent.mShouldUpdateMobileNetwork || mDialog == null) {
            return;
      }
        setMobileDataLayout(mDialog, internetContent);
    }

    private void setMobileDataLayout(SystemUIDialog dialog, InternetContent internetContent) {
        boolean isNetworkConnected =
                internetContent.mActiveNetworkIsCellular
                        || internetContent.mIsCarrierNetworkActive;
        // 1. Mobile network should be gone if airplane mode ON or the list of active
        //    subscriptionId is null.
        // 2. Carrier network should be gone if airplane mode ON and Wi-Fi is OFF.
        if (DEBUG) {
            Log.d(TAG, "setMobileDataLayout, isCarrierNetworkActive = "
                    + internetContent.mIsCarrierNetworkActive);
        }

        if (!internetContent.mHasActiveSubIdOnDds && (!internetContent.mIsWifiEnabled
                || !internetContent.mIsCarrierNetworkActive)) {
            mMobileNetworkLayout.setVisibility(View.GONE);
            if (mSecondaryMobileNetworkLayout != null) {
                mSecondaryMobileNetworkLayout.setVisibility(View.GONE);
            }
        } else {
            if (shouldDisallowUserToDisableDdsMobileData()) {
                Log.d(TAG, "Do not allow mobile data switch to be turned off");
                mMobileDataToggle.setEnabled(false);
            } else {
                mMobileDataToggle.setEnabled(true);
            }
            mMobileNetworkLayout.setVisibility(View.VISIBLE);
            mMobileDataToggle.setChecked(mInternetDetailsContentController.isMobileDataEnabled());
            mMobileTitleText.setText(getMobileNetworkTitle(mDefaultDataSubId));
            String summary = getMobileNetworkSummary(mDefaultDataSubId);
            if (!TextUtils.isEmpty(summary)) {
                mMobileSummaryText.setText(
                        Html.fromHtml(summary, Html.FROM_HTML_MODE_LEGACY));
                mMobileSummaryText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                mMobileSummaryText.setVisibility(View.VISIBLE);
            } else {
                mMobileSummaryText.setVisibility(View.GONE);
            }
            mBackgroundExecutor.execute(() -> {
                Drawable drawable = getSignalStrengthDrawable(mDefaultDataSubId);
                mHandler.post(() -> {
                    mSignalIcon.setImageDrawable(drawable);
                });
            });

            mMobileDataToggle.setVisibility(mCanConfigMobileData ? View.VISIBLE : View.INVISIBLE);
            mMobileToggleDivider.setVisibility(
                    mCanConfigMobileData ? View.VISIBLE : View.INVISIBLE);
            mNddsSubId = getNddsSubId();
            boolean nonDdsVisibleForDualData = SubscriptionManager
                    .isUsableSubscriptionId(mNddsSubId) && isDualDataEnabled();
            int primaryColor = isNetworkConnected
                    ? R.color.connected_network_primary_color
                    : R.color.disconnected_network_primary_color;
            mMobileToggleDivider.setBackgroundColor(dialog.getContext().getColor(primaryColor));
            // Display the info for the non-DDS if it's actively being used
            int autoSwitchNonDdsSubId = internetContent.mActiveAutoSwitchNonDdsSubId;
            int nonDdsVisibility = (autoSwitchNonDdsSubId
                    != SubscriptionManager.INVALID_SUBSCRIPTION_ID || nonDdsVisibleForDualData)
                    ? View.VISIBLE : View.GONE;
            Log.d(TAG, "mNddsSubId: " + mNddsSubId
                    + " isDualDataEnabled: " + isDualDataEnabled()
                    + " nonDdsVisibleForDualData: " + nonDdsVisibleForDualData
                    + " nonDdsVisibility: " + nonDdsVisibility);
            int secondaryRes = isNetworkConnected
                    ? R.style.TextAppearance_InternetDialog_Secondary_Active
                    : R.style.TextAppearance_InternetDialog_Secondary;
            if (nonDdsVisibleForDualData) {
                ViewStub stub = mDialogView.findViewById(R.id.secondary_mobile_network_stub);
                if (stub != null) {
                    stub.setLayoutResource(R.layout.qs_diaglog_secondary_generic_mobile_network);
                    stub.inflate();
                }
                mMobileNetworkLayout.setBackground(mBackgroundOn);
                mSecondaryMobileNetworkLayout = mDialogView.findViewById(
                        R.id.secondary_mobile_network_layout);
                mSecondaryMobileNetworkLayout.setBackground(mSecondaryBackgroundOn);
                mSecondaryMobileDataToggle =
                        mDialogView.requireViewById(R.id.secondary_generic_mobile_toggle);
                mSecondaryMobileDataToggle.setChecked(
                        mInternetDetailsContentController.isMobileDataEnabled(mNddsSubId));
                TextView mobileTitleText =
                        mDialogView.requireViewById(R.id.secondary_generic_mobile_title);
                mobileTitleText.setText(getMobileNetworkTitle(mNddsSubId));

                TextView summaryText =
                        mDialogView.requireViewById(R.id.secondary_generic_mobile_summary);
                String secondarySummary = getMobileNetworkSummary(mNddsSubId);
                if (!TextUtils.isEmpty(secondarySummary)) {
                    summaryText.setText(
                            Html.fromHtml(secondarySummary, Html.FROM_HTML_MODE_LEGACY));
                    summaryText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                    summaryText.setVisibility(View.VISIBLE);
                } else {
                    summaryText.setVisibility(View.GONE);
                }

                final ImageView signalIcon =
                        mDialogView.requireViewById(R.id.secondary_generic_signal_icon);
                mBackgroundExecutor.execute(() -> {
                    Drawable drawable = getSignalStrengthDrawable(mNddsSubId);
                    mHandler.post(() -> {
                        signalIcon.setImageDrawable(drawable);
                    });
                });

                View divider = mDialogView.requireViewById(
                        R.id.secondary_generic_mobile_toggle_divider);

                mSecondaryMobileDataToggle.setVisibility(
                        mCanConfigMobileData ? View.VISIBLE : View.INVISIBLE);
                divider.setVisibility(
                        mCanConfigMobileData ? View.VISIBLE : View.INVISIBLE);
                mSecondaryMobileDataToggle.setOnClickListener(
                    (v) -> {
                        boolean isChecked = mSecondaryMobileDataToggle.isChecked();
                        if (!isChecked && shouldShowMobileDialog(mNddsSubId)) {
                            mSecondaryMobileDataToggle.setChecked(true);
                            showTurnOffMobileDialog(mNddsSubId);
                        } else if (!shouldShowMobileDialog(mNddsSubId)) {
                            if (mInternetDetailsContentController.isMobileDataEnabled(
                                    mNddsSubId) == isChecked) {
                                return;
                            }
                            mInternetDetailsContentController.setMobileDataEnabled(
                                    dialog.getContext(), mNddsSubId, isChecked, false);
                        }
                });
                nonDdsVisibility = View.VISIBLE;
            } else if (nonDdsVisibility == View.VISIBLE) {
                // non DDS is the currently active sub, set primary visual for it
                ViewStub stub = mDialogView.findViewById(R.id.secondary_mobile_network_stub);
                if (stub != null) {
                    stub.inflate();
                }
                mSecondaryMobileNetworkLayout = mDialogView.findViewById(
                        R.id.secondary_mobile_network_layout);
                if (mCanConfigMobileData) {
                    mSecondaryMobileNetworkLayout.setOnClickListener(
                            this::onClickConnectedSecondarySub);
                }
                mSecondaryMobileNetworkLayout.setBackground(mSecondaryBackgroundOn);

                TextView mSecondaryMobileTitleText = mDialogView.requireViewById(
                        R.id.secondary_mobile_title);
                mSecondaryMobileTitleText.setText(getMobileNetworkTitle(autoSwitchNonDdsSubId));
                mSecondaryMobileTitleText.setTextAppearance(
                        R.style.TextAppearance_InternetDialog_Active);

                TextView mSecondaryMobileSummaryText =
                        mDialogView.requireViewById(R.id.secondary_mobile_summary);
                summary = getMobileNetworkSummary(autoSwitchNonDdsSubId);
                if (!TextUtils.isEmpty(summary)) {
                    mSecondaryMobileSummaryText.setText(
                            Html.fromHtml(summary, Html.FROM_HTML_MODE_LEGACY));
                    mSecondaryMobileSummaryText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                    mSecondaryMobileSummaryText.setTextAppearance(
                            R.style.TextAppearance_InternetDialog_Active);
                }

                ImageView mSecondarySignalIcon =
                        mDialogView.requireViewById(R.id.secondary_signal_icon);
                mBackgroundExecutor.execute(() -> {
                    Drawable drawable = getSignalStrengthDrawable(autoSwitchNonDdsSubId);
                    mHandler.post(() -> {
                        mSecondarySignalIcon.setImageDrawable(drawable);
                    });
                });

                ImageView mSecondaryMobileSettingsIcon =
                        mDialogView.requireViewById(R.id.secondary_settings_icon);
                mSecondaryMobileSettingsIcon.setColorFilter(
                        dialog.getContext().getColor(R.color.connected_network_primary_color));
                mSecondaryMobileSettingsIcon.setVisibility(mCanConfigMobileData ?
                        View.VISIBLE : View.INVISIBLE);

                // set secondary visual for default data sub
                mMobileNetworkLayout.setBackground(mBackgroundOff);
                mMobileTitleText.setTextAppearance(R.style.TextAppearance_InternetDialog);
                mMobileSummaryText.setTextAppearance(
                        R.style.TextAppearance_InternetDialog_Secondary);
                mSignalIcon.setColorFilter(
                        dialog.getContext().getColor(R.color.connected_network_secondary_color));
            } else {
                mMobileNetworkLayout.setBackground(
                        isNetworkConnected ? mBackgroundOn : mBackgroundOff);
                mMobileTitleText.setTextAppearance(isNetworkConnected
                        ?
                        R.style.TextAppearance_InternetDialog_Active
                        : R.style.TextAppearance_InternetDialog);
                mMobileSummaryText.setTextAppearance(secondaryRes);
            }

            if (mSecondaryMobileNetworkLayout != null) {
                mSecondaryMobileNetworkLayout.setVisibility(nonDdsVisibility);
            }

            // Set airplane mode to the summary for carrier network
            if (internetContent.mIsAirplaneModeEnabled) {
                mAirplaneModeSummaryText.setVisibility(View.VISIBLE);
                mAirplaneModeSummaryText.setText(
                        dialog.getContext().getText(R.string.airplane_mode));
                mAirplaneModeSummaryText.setTextAppearance(secondaryRes);
            } else {
                mAirplaneModeSummaryText.setVisibility(View.GONE);
            }
        }
    }

    @MainThread
    private void updateWifiToggle(InternetContent internetContent) {
        if (mWiFiToggle.isChecked() != internetContent.mIsWifiEnabled) {
            mWiFiToggle.setChecked(internetContent.mIsWifiEnabled);
        }
        if (internetContent.mIsDeviceLocked) {
            mWifiToggleTitleText.setTextAppearance((mConnectedWifiEntry != null)
                    ? R.style.TextAppearance_InternetDialog_Active
                    : R.style.TextAppearance_InternetDialog);
        }
        mTurnWifiOnLayout.setBackground(
                (internetContent.mIsDeviceLocked && mConnectedWifiEntry != null) ? mBackgroundOn
                        : null);

        if (!mCanChangeWifiState && mWiFiToggle.isEnabled()) {
            mWiFiToggle.setEnabled(false);
            mWifiToggleTitleText.setEnabled(false);
            final TextView summaryText = mDialogView.requireViewById(R.id.wifi_toggle_summary);
            summaryText.setEnabled(false);
            summaryText.setVisibility(View.VISIBLE);
        }
    }

    @MainThread
    private void updateConnectedWifi(InternetContent internetContent) {
        if (mDialog == null || !internetContent.mIsWifiEnabled || mConnectedWifiEntry == null
                || internetContent.mIsDeviceLocked) {
            mConnectedWifListLayout.setVisibility(View.GONE);
            mShareWifiButton.setVisibility(View.GONE);
            return;
        }
        mConnectedWifListLayout.setVisibility(View.VISIBLE);
        mConnectedWifiTitleText.setText(mConnectedWifiEntry.getTitle());
        mConnectedWifiSummaryText.setText(mConnectedWifiEntry.getSummary(false));
        mConnectedWifiIcon.setImageDrawable(
                mInternetDetailsContentController.getInternetWifiDrawable(mConnectedWifiEntry));
        mWifiSettingsIcon.setColorFilter(
                mDialog.getContext().getColor(R.color.connected_network_primary_color));
        if (mInternetDetailsContentController.getConfiguratorQrCodeGeneratorIntentOrNull(
                mConnectedWifiEntry) != null) {
            mShareWifiButton.setVisibility(View.VISIBLE);
        } else {
            mShareWifiButton.setVisibility(View.GONE);
        }

        if (mSecondaryMobileNetworkLayout != null) {
            mSecondaryMobileNetworkLayout.setVisibility(View.GONE);
        }
    }

    @MainThread
    private void updateWifiListAndSeeAll(InternetContent internetContent) {
        if (!internetContent.mIsWifiEnabled || internetContent.mIsDeviceLocked) {
            mWifiRecyclerView.setVisibility(View.GONE);
            mSeeAllLayout.setVisibility(View.GONE);
            return;
        }
        final int wifiListMaxCount = getWifiListMaxCount();
        if (mAdapter.getItemCount() > wifiListMaxCount) {
            mHasMoreWifiEntries = true;
        }
        mAdapter.setMaxEntriesCount(wifiListMaxCount);
        final int wifiListMinHeight = mWifiNetworkHeight * wifiListMaxCount;
        if (mWifiRecyclerView.getMinimumHeight() != wifiListMinHeight) {
            mWifiRecyclerView.setMinimumHeight(wifiListMinHeight);
        }
        mWifiRecyclerView.setVisibility(View.VISIBLE);
        mSeeAllLayout.setVisibility(mHasMoreWifiEntries ? View.VISIBLE : View.INVISIBLE);
    }

    @VisibleForTesting
    @MainThread
    int getWifiListMaxCount() {
        // Use the maximum count of networks to calculate the remaining count for Wi-Fi networks.
        int count = MAX_NETWORK_COUNT;
        if (mEthernetLayout.getVisibility() == View.VISIBLE) {
            count -= 1;
        }
        if (mMobileNetworkLayout.getVisibility() == View.VISIBLE) {
            count -= 1;
        }

        // If the remaining count is greater than the maximum count of the Wi-Fi network, the
        // maximum count of the Wi-Fi network is used.
        if (count > MAX_WIFI_ENTRY_COUNT) {
            count = MAX_WIFI_ENTRY_COUNT;
        }
        if (mConnectedWifListLayout.getVisibility() == View.VISIBLE) {
            count -= 1;
        }
        return count;
    }

    @MainThread
    private void updateWifiScanNotify(InternetContent internetContent) {
        if (mDialog == null || internetContent.mIsWifiEnabled
                || !internetContent.mIsWifiScanEnabled
                || internetContent.mIsDeviceLocked) {
            mWifiScanNotifyLayout.setVisibility(View.GONE);
            return;
        }
        if (TextUtils.isEmpty(mWifiScanNotifyText.getText())) {
            final AnnotationLinkSpan.LinkInfo linkInfo = new AnnotationLinkSpan.LinkInfo(
                    AnnotationLinkSpan.LinkInfo.DEFAULT_ANNOTATION,
                    mInternetDetailsContentController::launchWifiScanningSetting);
            mWifiScanNotifyText.setText(AnnotationLinkSpan.linkify(
                    mDialog.getContext().getText(R.string.wifi_scan_notify_message), linkInfo));
            mWifiScanNotifyText.setMovementMethod(LinkMovementMethod.getInstance());
        }
        mWifiScanNotifyLayout.setVisibility(View.VISIBLE);
    }

    void onClickConnectedWifi(View view) {
        if (mConnectedWifiEntry == null) {
            return;
        }
        mInternetDetailsContentController.launchWifiDetailsSetting(mConnectedWifiEntry.getKey(),
                view);
    }

    /** For DSDS auto data switch **/
    void onClickConnectedSecondarySub(View view) {
        mInternetDetailsContentController.launchMobileNetworkSettings(view);
    }

    void onClickSeeMoreButton(View view) {
        mInternetDetailsContentController.launchNetworkSetting(view);
    }

    CharSequence getDialogTitleText() {
        return mInternetDetailsContentController.getDialogTitleText();
    }

    @Nullable
    CharSequence getSubtitleText() {
        return mInternetDetailsContentController.getSubtitleText(mIsProgressBarVisible);
    }

    private Drawable getSignalStrengthDrawable(int subId) {
        return mInternetDetailsContentController.getSignalStrengthDrawable(subId);
    }

    CharSequence getMobileNetworkTitle(int subId) {
        return mInternetDetailsContentController.getMobileNetworkTitle(subId);
    }

    String getMobileNetworkSummary(int subId) {
        if (subId == mDefaultDataSubId && shouldDisallowUserToDisableDdsMobileData()) {
            return mDialog.getContext().getString(R.string.mobile_data_summary_not_allowed_to_disable_data);
        }
        return mInternetDetailsContentController.getMobileNetworkSummary(subId);
    }

    private void setProgressBarVisible(boolean visible) {
        if (mIsProgressBarVisible == visible) {
            return;
        }
        mIsProgressBarVisible = visible;
        mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        mProgressBar.setIndeterminate(visible);
        mDivider.setVisibility(visible ? View.GONE : View.VISIBLE);
        mInternetDialogSubTitle.setText(getSubtitleText());
    }

    private boolean shouldShowMobileDialog(int subId) {
        if (mDialog == null) {
            return false;
        }
        if (mInternetDetailsContentController.isMobileDataEnabled(subId)) {
            if (isCiwlanWarningConditionSatisfied(subId)) {
                return true;
            }
            boolean flag = Prefs.getBoolean(mDialog.getContext(), QS_HAS_TURNED_OFF_MOBILE_DATA, false);
            if (!flag) {
                return true;
            }
        }
        return false;
    }

    private boolean isCiwlanWarningConditionSatisfied(int subId) {
        // For targets that support MSIM C_IWLAN, the warning is to be shown only for the DDS when
        // either sub is in a call. For other targets, it will be shown only when there is a call on
        // the DDS.
        if (subId != mDefaultDataSubId) {
            return false;
        }
        int[] activeSubIdList = SubscriptionManager.from(
                mDialog.getContext()).getActiveSubscriptionIdList();
        mIsSubInCall = new SparseBooleanArray(activeSubIdList.length);
        mIsCiwlanModeSupported = new SparseBooleanArray(activeSubIdList.length);
        mIsCiwlanEnabled = new SparseBooleanArray(activeSubIdList.length);
        mIsInCiwlanOnlyMode = new SparseBooleanArray(activeSubIdList.length);
        mIsImsRegisteredOnCiwlan = new SparseBooleanArray(activeSubIdList.length);
        for (int i = 0; i < activeSubIdList.length; i++) {
            int subscriptionId = activeSubIdList[i];
            TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subscriptionId);
            mIsSubInCall.put(subscriptionId, tm.getCallStateForSubscription() !=
                    TelephonyManager.CALL_STATE_IDLE);
            mIsCiwlanModeSupported.put(subscriptionId, isCiwlanModeSupported(subscriptionId));
            mIsCiwlanEnabled.put(subscriptionId, isCiwlanEnabled(subscriptionId));
            mIsInCiwlanOnlyMode.put(subscriptionId, isInCiwlanOnlyMode(tm, subscriptionId));
            mIsImsRegisteredOnCiwlan.put(subscriptionId, isImsRegisteredOnCiwlan(subscriptionId));
        }
        boolean isMsimCiwlanSupported = mExtTelephonyManager.isFeatureSupported(
                ExtTelephonyManager.FEATURE_CIWLAN_MODE_PREFERENCE);
        int subToCheck = mDefaultDataSubId;
        if (isMsimCiwlanSupported) {
            // The user is trying to toggle the mobile data of the DDS. In this case, we need to
            // check if the nDDS is in a C_IWLAN call. If it is, we will check the C_IWLAN related
            // settings of the nDDS. Otherwise, we will check those of the DDS.
            subToCheck = subToCheckForCiwlanWarningDialog();
            Log.d(TAG, "isCiwlanWarningConditionSatisfied DDS = " + mDefaultDataSubId +
                    ", subToCheck = " + subToCheck);
        }
        if (mIsSubInCall.get(subToCheck)) {
            boolean isCiwlanModeSupported = mIsCiwlanModeSupported.get(subToCheck);
            boolean isCiwlanEnabled = mIsCiwlanEnabled.get(subToCheck);
            boolean isInCiwlanOnlyMode = mIsInCiwlanOnlyMode.get(subToCheck);
            boolean isImsRegisteredOnCiwlan = mIsImsRegisteredOnCiwlan.get(subToCheck);
            if (isCiwlanEnabled && (isInCiwlanOnlyMode || !isCiwlanModeSupported)) {
                Log.d(TAG, "isInCall = true, isCiwlanEnabled = true" +
                        ", isInCiwlanOnlyMode = " + isInCiwlanOnlyMode +
                        ", isCiwlanModeSupported = " + isCiwlanModeSupported +
                        ", isImsRegisteredOnCiwlan = " + isImsRegisteredOnCiwlan);
                // If IMS is registered over C_IWLAN-only mode, the device is in a call, and
                // user is trying to disable mobile data, display a warning dialog that
                // disabling mobile data will cause a call drop.
                return isImsRegisteredOnCiwlan;
            } else {
                Log.d(TAG, "C_IWLAN not enabled or not in C_IWLAN-only mode");
            }
        } else {
            Log.d(TAG, "Not in a call");
        }
        return false;
    }

    private boolean isImsRegisteredOnCiwlan(int subId) {
        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
        IImsRegistration imsRegistrationImpl = tm.getImsRegistration(
                SubscriptionManager.from(mDialog.getContext()).getSlotIndex(subId), FEATURE_MMTEL);
        if (imsRegistrationImpl != null) {
            try {
                return imsRegistrationImpl.getRegistrationTechnology() ==
                        REGISTRATION_TECH_CROSS_SIM;
            } catch (RemoteException ex) {
                Log.e(TAG, "getRegistrationTechnology failed", ex);
            }
        }
        return false;
    }

    private int subToCheckForCiwlanWarningDialog() {
        int subToCheck = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (mIsSubInCall.get(mNddsSubId) && mIsCiwlanEnabled.get(mNddsSubId) &&
                (mIsInCiwlanOnlyMode.get(mNddsSubId) || !mIsCiwlanModeSupported.get(mNddsSubId)) &&
                mIsImsRegisteredOnCiwlan.get(mNddsSubId)) {
            subToCheck = mNddsSubId;
        } else {
            subToCheck = mDefaultDataSubId;
        }
        return subToCheck;
    }

    private void showTurnOffMobileDialog(int subId) {
        Context context = mDialog.getContext();
        CharSequence carrierName = getMobileNetworkTitle(subId);
        boolean isInService = mInternetDetailsContentController.isVoiceStateInService(subId);
        if (TextUtils.isEmpty(carrierName) || !isInService) {
            carrierName = context.getString(R.string.mobile_data_disable_message_default_carrier);
        }
        String mobileDataDisableDialogMessage = isDualDataEnabled() ?
                context.getString(R.string.mobile_data_disable_message_on_dual_data, carrierName) :
                context.getString(R.string.mobile_data_disable_message, carrierName);

        // Adjust the dialog message for CIWLAN
        if (isCiwlanWarningConditionSatisfied(subId)) {
            mobileDataDisableDialogMessage = isCiwlanModeSupported(subId) ?
                    context.getString(R.string.data_disable_ciwlan_call_will_drop_message) :
                    context.getString(R.string.data_disable_ciwlan_call_might_drop_message);
        }

        final Switch mobileDataToggle = (subId == mDefaultDataSubId)
                ? mMobileDataToggle : mSecondaryMobileDataToggle;
        mAlertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_data_disable_title)
                .setMessage(mobileDataDisableDialogMessage)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    // toggle has already been set to off before dialog is shown,
                    // it shall be set back to true if negative button is selected
                    mobileDataToggle.setChecked(true);
                })
                .setPositiveButton(
                        com.android.internal.R.string.alert_windows_notification_turn_off_action,
                        (d, w) -> {
                            mInternetDetailsContentController.setMobileDataEnabled(context,
                                    subId, false, false);
                            mobileDataToggle.setChecked(false);
                            Prefs.putBoolean(context, QS_HAS_TURNED_OFF_MOBILE_DATA, true);
                        })
                .create();
        mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        SystemUIDialog.setShowForAllUsers(mAlertDialog, true);
        SystemUIDialog.registerDismissListener(mAlertDialog);
        SystemUIDialog.setWindowOnTop(mAlertDialog, mKeyguard.isShowing());
        mDialogTransitionAnimator.showFromDialog(mAlertDialog, mDialog, null, false);
    }

    private void showTurnOffAutoDataSwitchDialog(SystemUIDialog dialog, int subId) {
        Context context = dialog.getContext();
        CharSequence carrierName = getMobileNetworkTitle(mDefaultDataSubId);
        if (TextUtils.isEmpty(carrierName)) {
            carrierName = context.getString(R.string.mobile_data_disable_message_default_carrier);
        }
        mAlertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.auto_data_switch_disable_title, carrierName))
                .setMessage(R.string.auto_data_switch_disable_message)
                .setNegativeButton(R.string.auto_data_switch_dialog_negative_button,
                        (d, w) -> {
                        })
                .setPositiveButton(R.string.auto_data_switch_dialog_positive_button,
                        (d, w) -> {
                            mInternetDetailsContentController
                                    .setAutoDataSwitchMobileDataPolicy(subId, false);
                            if (mSecondaryMobileNetworkLayout != null) {
                                mSecondaryMobileNetworkLayout.setVisibility(View.GONE);
                            }
                        })
                .create();
        mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        SystemUIDialog.setShowForAllUsers(mAlertDialog, true);
        SystemUIDialog.registerDismissListener(mAlertDialog);
        SystemUIDialog.setWindowOnTop(mAlertDialog, mKeyguard.isShowing());
        mDialogTransitionAnimator.showFromDialog(mAlertDialog, dialog, null, false);
    }

    private boolean isCiwlanEnabled(int subId) {
        ImsMmTelManager imsMmTelMgr = getImsMmTelManager(subId);
        if (imsMmTelMgr == null) {
            return false;
        }
        try {
            return imsMmTelMgr.isCrossSimCallingEnabled();
        } catch (ImsException exception) {
            Log.e(TAG, "Failed to get C_IWLAN toggle status", exception);
        }
        return false;
    }

    private ImsMmTelManager getImsMmTelManager(int subId) {
        if (!SubscriptionManager.isUsableSubscriptionId(subId)) {
            Log.d(TAG, "getImsMmTelManager: subId unusable");
            return null;
        }
        if (mImsManager == null) {
            Log.d(TAG, "getImsMmTelManager: ImsManager null");
            return null;
        }
        return mImsManager.getImsMmTelManager(subId);
    }

    private boolean isInCiwlanOnlyMode(TelephonyManager tm, int subId) {
        CiwlanConfig ciwlanConfig =
                (subId == mDefaultDataSubId) ? mCiwlanConfig : mNddsCiwlanConfig;
        if (ciwlanConfig == null) {
            Log.d(TAG, "isInCiwlanOnlyMode: C_IWLAN config null on SUB " + subId);
            return false;
        }
        if (isRoaming(tm)) {
            return ciwlanConfig.isCiwlanOnlyInRoam();
        }
        return ciwlanConfig.isCiwlanOnlyInHome();
    }

    private boolean isCiwlanModeSupported(int subId) {
        CiwlanConfig ciwlanConfig =
                (subId == mDefaultDataSubId) ? mCiwlanConfig : mNddsCiwlanConfig;
        if (ciwlanConfig == null) {
            Log.d(TAG, "isCiwlanModeSupported: C_IWLAN config null on SUB " + subId);
            return false;
        }
        return ciwlanConfig.isCiwlanModeSupported();
    }

    private boolean isRoaming(TelephonyManager tm) {
        if (tm == null) {
            Log.d(TAG, "isRoaming: TelephonyManager null");
            return false;
        }
        boolean nriRoaming = false;
        ServiceState serviceState = tm.getServiceState();
        if (serviceState != null) {
            NetworkRegistrationInfo nri =
                    serviceState.getNetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN);
            if (nri != null) {
                nriRoaming = nri.isNetworkRoaming();
            } else {
                Log.d(TAG, "isRoaming: network registration info null");
            }
        } else {
            Log.d(TAG, "isRoaming: service state null");
        }
        return nriRoaming;
    }

    @Override
    public void onRefreshCarrierInfo() {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onSimStateChanged() {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    @WorkerThread
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    @WorkerThread
    public void onLost(Network network) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onSubscriptionsChanged(int defaultDataSubId) {
        mDefaultDataSubId = defaultDataSubId;
        mNddsSubId = getNddsSubId();
        updateCiwlanConfigs();
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onUserMobileDataStateChanged(boolean enabled) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    @WorkerThread
    public void onDataConnectionStateChanged(int state, int networkType) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onDisplayInfoChanged(TelephonyDisplayInfo telephonyDisplayInfo) {
        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onCarrierNetworkChange(boolean active) {

        updateDialog(true /* shouldUpdateMobileNetwork */);
    }

    @Override
    public void onNonDdsCallStateChanged(int callState) {
        mHandler.post(() -> updateDialog(true /* shouldUpdateMobileNetwork */));
    }

    @Override
    public void onTempDdsSwitchHappened() {
        mHandler.post(() -> updateDialog(true /* shouldUpdateMobileNetwork */));
    }

    @Override
    @WorkerThread
    public void onAccessPointsChanged(@Nullable List<WifiEntry> wifiEntries,
            @Nullable WifiEntry connectedEntry, boolean hasMoreWifiEntries) {
        // Should update the carrier network layout when it is connected under airplane mode ON.
        boolean shouldUpdateCarrierNetwork = mMobileNetworkLayout.getVisibility() == View.VISIBLE
                && mInternetDetailsContentController.isAirplaneModeEnabled();
        mHandler.post(() -> {
            mConnectedWifiEntry = connectedEntry;
            mWifiEntriesCount = wifiEntries == null ? 0 : wifiEntries.size();
            mHasMoreWifiEntries = hasMoreWifiEntries;
            updateDialog(shouldUpdateCarrierNetwork /* shouldUpdateMobileNetwork */);
            mAdapter.setWifiEntries(wifiEntries, mWifiEntriesCount);
            mAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onWifiScan(boolean isScan) {
        setProgressBarVisible(isScan);
    }

    @Override
    public void onWindowFocusChanged(SystemUIDialog dialog, boolean hasFocus) {
        if (mAlertDialog != null && !mAlertDialog.isShowing()) {
            if (!hasFocus && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    private boolean isDualDataEnabled() {
        return mInternetDetailsContentController.isDualDataEnabled();
    }

    @Override
    public void onDualDataEnabledStateChanged() {
        mNddsSubId = getNddsSubId();
        updateCiwlanConfigs();
        mHandler.post(() -> updateDialog(true /* shouldUpdateMobileNetwork */));
    }

    @Override
    public void onFiveGStateOverride() {
        mHandler.post(() -> updateDialog(true /* shouldUpdateMobileNetwork */));
    }

    private void updateCiwlanConfigs() {
        if (mExtTelephonyManager != null) {
            try {
                if (SubscriptionManager.isUsableSubscriptionId(mDefaultDataSubId)) {
                    mCiwlanConfig = mExtTelephonyManager.getCiwlanConfig(
                            SubscriptionManager.getSlotIndex(mDefaultDataSubId));
                }
                if (SubscriptionManager.isUsableSubscriptionId(mNddsSubId)) {
                    mNddsCiwlanConfig = mExtTelephonyManager.getCiwlanConfig(
                            SubscriptionManager.getSlotIndex(mNddsSubId));
                }
            } catch (RemoteException ex) {
                Log.e(TAG, "getCiwlanConfig exception", ex);
            }
        }
    }

    private int getNddsSubId() {
        return mInternetDetailsContentController.getNddsSubId();
    }

    public enum InternetDialogEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "The Internet dialog became visible on the screen.")
        INTERNET_DIALOG_SHOW(843),

        @UiEvent(doc = "The share wifi button is clicked.")
        SHARE_WIFI_QS_BUTTON_CLICKED(1462);

        private final int mId;

        InternetDialogEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    @VisibleForTesting
    static class InternetContent {
        CharSequence mInternetDialogTitleString = "";
        CharSequence mInternetDialogSubTitle = "";
        boolean mIsAirplaneModeEnabled = false;
        boolean mHasEthernet = false;
        boolean mShouldUpdateMobileNetwork = false;
        boolean mActiveNetworkIsCellular = false;
        boolean mIsCarrierNetworkActive = false;
        boolean mIsWifiEnabled = false;
        boolean mHasActiveSubIdOnDds = false;
        boolean mIsDeviceLocked = false;
        boolean mIsWifiScanEnabled = false;
        int mActiveAutoSwitchNonDdsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
}
