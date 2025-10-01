// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */
package com.android.systemui.util;

import android.content.Context;
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
import android.telephony.ServiceState;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
import com.android.systemui.dagger.SysUISingleton;
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
import com.android.systemui.res.R;
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
import com.android.systemui.statusbar.pipeline.mobile.data.model.MobileIconCustomizationMode;
import com.android.systemui.statusbar.policy.FiveGServiceClient;
import com.google.android.collect.Lists;
import com.qti.extphone.NrIconType;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement

// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
import java.util.ArrayList;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
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

// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private Context mContext;
    private String mSeparator;
    private FiveGServiceClient mFiveGServiceClient;
    private boolean mShowCustomizeName;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
    private boolean mShow5GAIcon;
// QTI_END: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
    private final ArrayList<KeyguardUpdateMonitorCallback>
            mCallbacks = Lists.newArrayList();

// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
    @Inject
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
    public CarrierNameCustomization(Context context, KeyguardUpdateMonitor keyguardUpdateMonitor,
                                    FiveGServiceClient fiveGServiceClient) {
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        mCarrierMap = new HashMap<String, String>();

// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mContext = context;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        mRoamingCustomizationCarrierNameEnabled = context.getResources().getBoolean(
                R.bool.config_show_roaming_customization_carrier_name);
        mConnector = context.getResources().getString(R.string.connector);
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        mSeparator = context.getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        mShowCustomizeName = context.getResources().getBoolean(
                com.android.settingslib.R.bool.config_show_customize_carrier_name);
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
        mShow5GAIcon = context.getResources().getBoolean(
                com.android.settingslib.R.bool.config_display_5g_a);
// QTI_END: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        mFiveGServiceClient = fiveGServiceClient;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names

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

// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
    public void loadCarrierMap(Context context) {
// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
// QTI_BEGIN: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
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
// QTI_END: 2022-12-13: Android_UI: SystemUI: Display combined carrier names
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement

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
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
        String networkClass = getNetworkTypeDescription(sub.getSubscriptionId());
        return getCustomizeCarrierNameInternal(originCarrierName, networkClass);
    }

    public String getNetworkTypeDescription(int subId) {
// QTI_END: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        int dataNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        int voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        boolean isInService = false;
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
        ServiceState ss = mKeyguardUpdateMonitor.getServiceState(subId);
// QTI_END: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
        if (ss != null) {
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-12-12: Android_UI: SystemUI: Fixed NPE in CarrierNameCustomization
            dataNetworkType = ss.getDataNetworkType();
            voiceNetworkType = ss.getVoiceNetworkType();
// QTI_END: 2024-12-12: Android_UI: SystemUI: Fixed NPE in CarrierNameCustomization
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
            isInService = (ss.getDataRegState() == ServiceState.STATE_IN_SERVICE
                    || ss.getVoiceRegState() == ServiceState.STATE_IN_SERVICE);
        }
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
        SubscriptionInfo sub = mKeyguardUpdateMonitor.getSubscriptionInfoForSubId(subId);
// QTI_END: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
// QTI_BEGIN: 2024-12-12: Android_UI: SystemUI: Fixed NPE in CarrierNameCustomization
        if (sub == null) {
            return getNetWorkName(dataNetworkType, voiceNetworkType, isInService,
                    NrIconType.INVALID);
        } else {
            FiveGServiceClient.FiveGServiceState fiveGServiceState =
                    mFiveGServiceClient.getCurrentServiceState(sub.getSimSlotIndex());
            return getNetWorkName(dataNetworkType, voiceNetworkType, isInService,
                    fiveGServiceState.getNrIconType());
        }
// QTI_END: 2024-12-12: Android_UI: SystemUI: Fixed NPE in CarrierNameCustomization
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
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
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
            if (nrIconType == NrIconType.TYPE_5G_UWB
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
// QTI_BEGIN: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
                    && mShow5GAIcon) {
// QTI_END: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
// QTI_BEGIN: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
                return mContext.getResources().getString(
                        com.android.settingslib.R.string.data_connection_5g_a);
            }
// QTI_END: 2024-05-22: Android_UI: SystemUI: Display 5GA icon for 3CC
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
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
// QTI_END: 2024-03-10: Android_UI: SystemUI: Carrier name customization enhancement
// QTI_BEGIN: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog

    public boolean showCustomizeName() {
        return mShowCustomizeName;
    }
// QTI_END: 2024-05-28: Android_UI: SystemUI: Customize network type in InternetDialog
// QTI_BEGIN: 2025-03-27: Android_UI: SystemUI: Enhance Network Description

    public boolean show5GAIcon() {
        return mShow5GAIcon;
    }
// QTI_END: 2025-03-27: Android_UI: SystemUI: Enhance Network Description
// QTI_BEGIN: 2024-06-06: Android_UI: Add 5G override for internet dialog
}
// QTI_END: 2024-06-06: Android_UI: Add 5G override for internet dialog
