/*
 * Copyright (C) 2022 The Android Open Source Project
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

// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
/*
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
package com.android.systemui.statusbar.pipeline.mobile.domain.interactor

import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.log.table.TableLogBuffer
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
import com.android.systemui.statusbar.pipeline.mobile.data.model.MobileIconCustomizationMode
// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.domain.model.NetworkTypeIconModel
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.shared.data.model.DataActivityModel
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMobileIconInteractor(
    override val tableLogBuffer: TableLogBuffer,
) : MobileIconInteractor {
    override val alwaysShowDataRatIcon = MutableStateFlow(false)

    override val activity =
        MutableStateFlow(
            DataActivityModel(
                hasActivityIn = false,
                hasActivityOut = false,
            )
        )

    override val carrierNetworkChangeActive = MutableStateFlow(false)

    override val mobileIsDefault = MutableStateFlow(true)

    override val isSingleCarrier = MutableStateFlow(true)

    override val networkTypeIconGroup =
        MutableStateFlow<NetworkTypeIconModel>(
            NetworkTypeIconModel.DefaultIcon(TelephonyIcons.THREE_G)
        )

    override val showSliceAttribution = MutableStateFlow(false)

    override val networkName = MutableStateFlow(NetworkNameModel.IntentDerived("demo mode"))

    override val carrierName = MutableStateFlow("demo mode")

    override val isRoaming = MutableStateFlow(false)

    override val isDataConnected = MutableStateFlow(true)

    override val isInService = MutableStateFlow(true)

    override val isEmergencyOnly = MutableStateFlow(true)

    override val isNonTerrestrial = MutableStateFlow(false)

    private val _isDataEnabled = MutableStateFlow(true)
    override val isDataEnabled = _isDataEnabled

    override val isForceHidden = MutableStateFlow(false)

// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
    private val _alwaysUseRsrpLevelForLte = MutableStateFlow(false)
    override val alwaysUseRsrpLevelForLte = _alwaysUseRsrpLevelForLte

    private val _hideNoInternetState = MutableStateFlow(false)
    override val hideNoInternetState = _hideNoInternetState

// QTI_END: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
    private val _networkTypeIconCustomization = MutableStateFlow(MobileIconCustomizationMode())
    override val networkTypeIconCustomization = _networkTypeIconCustomization

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt network type icon customization
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
    private val _showVolteIcon = MutableStateFlow(false)
    override val showVolteIcon = _showVolteIcon

    private val _imsInfo = MutableStateFlow(MobileIconCustomizationMode())
    override val imsInfo = _imsInfo

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt the Volte HD icon
// QTI_BEGIN: 2023-04-01: Android_UI: SystemUI: Readapt VoWifi icon
    private val _showVowifiIcon = MutableStateFlow(false)
    override val showVowifiIcon = _showVowifiIcon

    private val _voWifiAvailable = MutableStateFlow(false)
    override val voWifiAvailable = _voWifiAvailable

// QTI_END: 2023-04-01: Android_UI: SystemUI: Readapt VoWifi icon
// QTI_BEGIN: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
    private val _customizedIcon = MutableStateFlow(null)
    override val customizedIcon = _customizedIcon

// QTI_END: 2024-01-30: Android_UI: SystemUI: Implementation for MSIM C_IWLAN feature
// QTI_BEGIN: 2024-03-10: Android_UI: SystemUI: Readapt the ShadeCarrier SPN display customization
    override val customizedNetworkName = MutableStateFlow(NetworkNameModel.IntentDerived("demo mode"))

    override val customizedCarrierName = MutableStateFlow("demo mode")

// QTI_END: 2024-03-10: Android_UI: SystemUI: Readapt the ShadeCarrier SPN display customization
// QTI_BEGIN: 2023-06-26: Telephony: Separate exclamation mark display for mobile network
    private val _isConnectionFailed = MutableStateFlow(false)
    override val isConnectionFailed = _isConnectionFailed

// QTI_END: 2023-06-26: Telephony: Separate exclamation mark display for mobile network
    override val isAllowedDuringAirplaneMode = MutableStateFlow(false)

    override val signalLevelIcon: MutableStateFlow<SignalIconModel> =
        MutableStateFlow(
            SignalIconModel.Cellular(
                level = 0,
                numberOfLevels = 4,
                showExclamationMark = false,
                carrierNetworkChange = false,
            )
        )

    fun setIsDataEnabled(enabled: Boolean) {
        _isDataEnabled.value = enabled
    }

// QTI_BEGIN: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
    fun setAlwaysUseRsrpLevelForLte(alwaysUseRsrpLevelForLte: Boolean) {
        _alwaysUseRsrpLevelForLte.value = alwaysUseRsrpLevelForLte
    }

    fun setHideNoInternetState(hideNoInternetState: Boolean) {
        _hideNoInternetState.value = hideNoInternetState
    }
// QTI_END: 2023-03-02: Android_UI: SystemUI: Support customization signal strength icon
}
