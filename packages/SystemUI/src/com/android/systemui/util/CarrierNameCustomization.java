/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
package com.android.systemui.util;

import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.pipeline.mobile.data.model.MobileIconCustomizationMode;
import com.android.systemui.statusbar.policy.FiveGServiceClient;
import com.google.android.collect.Lists;
import com.qti.extphone.NrIconType;

import java.util.ArrayList;
import java.util.HashMap;
import javax.inject.Inject;

@SysUISingleton
public class CarrierNameCustomization {
    private final String TAG = "CarrierNameCustomization";
    private final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * The map for carriers:
     * The key is MCCMNC.
     * The value of the key is unique carrier name.
     * Carrier can have several MCCMNC, but it only has one unique carrier name.
     */
    private HashMap<String, String> mCarrierMap;
    private boolean mRoamingCustomizationCarrierNameEnabled;
    private String mConnector;
    private TelephonyManager mTelephonyManager;

    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private Context mContext;
    private String mSeparator;
    private FiveGServiceClient mFiveGServiceClient;
    private boolean mShowCustomizeName;
    private final ArrayList<KeyguardUpdateMonitorCallback>
            mCallbacks = Lists.newArrayList();

    @Inject
    public CarrierNameCustomization(Context context, KeyguardUpdateMonitor keyguardUpdateMonitor,
                                    FiveGServiceClient fiveGServiceClient) {
        mCarrierMap = new HashMap<String, String>();

        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mContext = context;
        mRoamingCustomizationCarrierNameEnabled = context.getResources().getBoolean(
                R.bool.config_show_roaming_customization_carrier_name);
        mConnector = context.getResources().getString(R.string.connector);
        mSeparator = context.getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        mShowCustomizeName = context.getResources().getBoolean(
                com.android.settingslib.R.bool.config_show_customize_carrier_name);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mFiveGServiceClient = fiveGServiceClient;

        if (mRoamingCustomizationCarrierNameEnabled) {
            loadCarrierMap(context);
        }
    }

    /**
     * Returns true if the roaming customization is enabled
     * @return
     */
    public boolean isRoamingCustomizationEnabled() {
        return mRoamingCustomizationCarrierNameEnabled;
    }

    /**
     * Returns true if the current network for the subscription is considered roaming.
     * It is considered roaming if the carrier of the sim card and network are not the same.
     * @param subId the subscription ID.
     */
    public boolean isRoaming(int subId) {
        String simOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getSimOperator(subId), "");
        String networkOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getNetworkOperator(subId), "");
        if (DEBUG) {
            Log.d(TAG, "isRoaming subId=" + subId
                    + " simOperator=" + mTelephonyManager.getSimOperator(subId)
                    + " networkOperator=" + mTelephonyManager.getNetworkOperator(subId));
        }
        boolean roaming = false;
        if (!TextUtils.isEmpty(simOperatorName) && !TextUtils.isEmpty(networkOperatorName)
                && !simOperatorName.equals(networkOperatorName)) {
            roaming = true;
        }

        return roaming;
    }

    /**
     * Returns the roaming customization carrier name.
     * @param subId the subscription ID.
     */
    public String getRoamingCarrierName(int subId) {
        String simOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getSimOperator(subId), "");
        String networkOperatorName =
                mCarrierMap.getOrDefault(mTelephonyManager.getNetworkOperator(subId), "");
        StringBuilder combinedCarrierName = new StringBuilder();
        combinedCarrierName.append(simOperatorName)
                .append(mConnector)
                .append(networkOperatorName);
        return combinedCarrierName.toString();
    }

    public void loadCarrierMap(Context context) {
        String customizationConfigs[] =
                context.getResources().getStringArray(R.array.customization_carrier_name_list);
        for(String config : customizationConfigs ) {
            String[] kv = config.trim().split(":");
            if (kv.length != 2) {
                Log.e(TAG, "invalid key value config " + config);
                continue;
            }
            mCarrierMap.put(kv[0], kv[1]);
        }
    }

    public void registerCallback(KeyguardUpdateMonitorCallback callback) {
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
            mFiveGServiceClient.registerCallback(callback);
        }
    }

    public void removeCallback(KeyguardUpdateMonitorCallback callback) {
        mCallbacks.remove(callback);
        mFiveGServiceClient.removeCallback(callback);
    }

    public String getCustomizeCarrierNameOld(CharSequence originCarrierName, SubscriptionInfo sub) {
        String networkClass = getNetworkTypeDescription(sub.getSubscriptionId());
        return getCustomizeCarrierNameInternal(originCarrierName, networkClass);
    }

    public String getNetworkTypeDescription(int subId) {
        int dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        int voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        boolean isInService = false;
        ServiceState ss = mKeyguardUpdateMonitor.getServiceState(subId);
        if (ss != null) {
            dataNetworkType = ss.getVoiceNetworkType();
            voiceNetworkType = ss.getDataNetworkType();
            isInService = (ss.getDataRegState() == ServiceState.STATE_IN_SERVICE
                    || ss.getVoiceRegState() == ServiceState.STATE_IN_SERVICE);
        }
        SubscriptionInfo sub = mKeyguardUpdateMonitor.getSubscriptionInfoForSubId(subId);
        FiveGServiceClient.FiveGServiceState fiveGServiceState =
                mFiveGServiceClient.getCurrentServiceState(sub.getSimSlotIndex());
        return getNetWorkName(dataNetworkType, voiceNetworkType, isInService,
                fiveGServiceState.getNrIconType());
    }

    public String getCustomizeCarrierNameModern(int subId, String originCarrierName,
                                                boolean showNetworkType,
                                                int nrIconType,
                                                int dataNetworkType,
                                                int voiceNetworkType,
                                                boolean isInService) {
        if (mShowCustomizeName) {
            if (isRoamingCustomizationEnabled() && isRoaming(subId)) {
                originCarrierName = getRoamingCarrierName(subId);
            } else if (showNetworkType) {
                String networkClass = getNetWorkName(dataNetworkType, voiceNetworkType, isInService,
                        nrIconType);
                originCarrierName = getCustomizeCarrierNameInternal(originCarrierName,
                        networkClass);
            } else {
                originCarrierName = getCustomizeCarrierNameInternal(originCarrierName, null);
            }
        }
        return originCarrierName;
    }

    private String getCustomizeCarrierNameInternal(CharSequence originCarrierName,
                                                   String networkType) {
        StringBuilder newCarrierName = new StringBuilder();
        if (!TextUtils.isEmpty(originCarrierName)) {
            String[] names = originCarrierName.toString().split(mSeparator.toString(), 2);
            for (int j = 0; j < names.length; j++) {
                names[j] = getLocalString(
                        names[j], com.android.settingslib.R.array.origin_carrier_names,
                        com.android.settingslib.R.array.locale_carrier_names);
                if (!TextUtils.isEmpty(names[j])) {
                    if (!TextUtils.isEmpty(networkType)) {
                        names[j] = new StringBuilder().append(names[j]).append(" ")
                                .append(networkType).toString();
                    }
                    if (j > 0 && names[j].equals(names[j - 1])) {
                        continue;
                    }
                    if (j > 0) {
                        newCarrierName.append(mSeparator);
                    }
                    newCarrierName.append(names[j]);
                }
            }
        }
        return newCarrierName.toString();
    }

    private String getNetWorkName(int dataNetworkType,
                                  int voiceNetworkType,
                                  boolean isInService, int nrIconType) {
        int networkType = getNetworkType(dataNetworkType, voiceNetworkType, isInService);
        String fiveGNetworkClass = get5GNetworkClass(dataNetworkType, networkType, nrIconType);
        return (fiveGNetworkClass != null) ? fiveGNetworkClass : networkTypeToString(networkType);
    }

    private int getNetworkType(int dataNetworkType,
                               int voiceNetworkType,
                               boolean isInService) {
        int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        if (isInService) {
            networkType = dataNetworkType;
            if (networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                networkType = voiceNetworkType;
            }
        }
        return networkType;
    }

    private String networkTypeToString(int networkType) {
        int classId = com.android.settingslib.R.string.config_rat_unknown;
        long mask = TelephonyManager.getBitMaskForNetworkType(networkType);
        if ((mask & TelephonyManager.NETWORK_CLASS_BITMASK_2G) != 0) {
            classId = com.android.settingslib.R.string.config_rat_2g;
        } else if ((mask & TelephonyManager.NETWORK_CLASS_BITMASK_3G) != 0) {
            classId = com.android.settingslib.R.string.config_rat_3g;
        } else if ((mask & TelephonyManager.NETWORK_CLASS_BITMASK_4G) != 0) {
            classId = com.android.settingslib.R.string.config_rat_4g;
        }
        return mContext.getResources().getString(classId);
    }

    private String get5GNetworkClass(int dataType, int networkType, int nrIconType) {
        if ((networkType == TelephonyManager.NETWORK_TYPE_NR)
                || (nrIconType != NrIconType.INVALID
                && nrIconType != NrIconType.TYPE_NONE
                && isDataRegisteredOnLte(dataType))) {
            if (nrIconType == NrIconType.TYPE_5G_UWB
                    && mContext.getResources().getBoolean(
                    com.android.settingslib.R.bool.config_display_5g_a)) {
                return mContext.getResources().getString(
                        com.android.settingslib.R.string.data_connection_5g_a);
            }
            return mContext.getResources().getString(
                    com.android.settingslib.R.string.data_connection_5g);
        }
        return null;
    }

    private boolean isDataRegisteredOnLte(int dataType) {
        return (dataType == TelephonyManager.NETWORK_TYPE_LTE
                || dataType == TelephonyManager.NETWORK_TYPE_LTE_CA);
    }

    private String getLocalString(String originalString,
                                  int originNamesId, int localNamesId) {
        String[] origNames = mContext.getResources().getStringArray(originNamesId);
        String[] localNames = mContext.getResources().getStringArray(localNamesId);
        for (int i = 0; i < origNames.length; i++) {
            if (origNames[i].equalsIgnoreCase(originalString)) {
                return localNames[i];
            }
        }
        return originalString;
    }

    public boolean showCustomizeName() {
        return mShowCustomizeName;
    }
}