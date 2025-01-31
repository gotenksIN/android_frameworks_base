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

package com.android.keyguard;

// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
import android.content.res.Configuration;
// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
import android.telephony.TelephonyManager;

import com.android.systemui.util.ViewController;

// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
import java.util.Locale;

// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
import javax.inject.Inject;

/**
 * Controller for {@link CarrierText}.
 */
public class CarrierTextController extends ViewController<CarrierText> {
    private final CarrierTextManager mCarrierTextManager;
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
    private Locale mLocale;
// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
    private final CarrierTextManager.CarrierTextCallback mCarrierTextCallback =
            new CarrierTextManager.CarrierTextCallback() {
                @Override
                public void updateCarrierInfo(CarrierTextManager.CarrierTextCallbackInfo info) {
                    mView.setText(info.carrierText);
                }

                @Override
                public void startedGoingToSleep() {
                    mView.setSelected(false);
                }

                @Override
                public void finishedWakingUp() {
                    mView.setSelected(true);
// QTI_BEGIN: 2019-02-14: Android_UI: SystemUI: Refactor QTI features
                }
// QTI_END: 2019-02-14: Android_UI: SystemUI: Refactor QTI features
            };

    @Inject
    public CarrierTextController(CarrierText view,
            CarrierTextManager.Builder carrierTextManagerBuilder,
            KeyguardUpdateMonitor keyguardUpdateMonitor) {
        super(view);

        mCarrierTextManager = carrierTextManagerBuilder
                .setShowAirplaneMode(mView.getShowAirplaneMode())
                .setShowMissingSim(mView.getShowMissingSim())
                .setDebugLocationString(mView.getDebugLocation())
                .build();
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
        mView.setOnConfigurationChangedListener(this::refreshInfoIfNeeded);
        mLocale = mView.getResources().getConfiguration().locale;
// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
    }

    @Override
    protected void onInit() {
        super.onInit();
        mView.setSelected(mKeyguardUpdateMonitor.isDeviceInteractive());
    }

    @Override
    protected void onViewAttached() {
        mCarrierTextManager.setListening(mCarrierTextCallback);
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
    }

// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
    @Override
    protected void onViewDetached() {
        mCarrierTextManager.setListening(null);
// QTI_BEGIN: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
    }
// QTI_END: 2019-06-12: Android_UI: SystemUI: Don't display 5G in carrier name when data type is not LTE
// QTI_BEGIN: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language

    private void refreshInfoIfNeeded(Configuration newConfig) {
        if (mLocale != newConfig.locale) {
            mCarrierTextManager.loadCarrierMap();
            mCarrierTextManager.updateCarrierText();
            mLocale = newConfig.locale;
        }

    }
// QTI_END: 2023-07-13: Android_UI: SystemUI: Follow system settings to switch carrier name language
}
