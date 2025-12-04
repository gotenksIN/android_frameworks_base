/*
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
 *
 * Not a contribution.
*/

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wm;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
// QTI_BEGIN: 2020-09-25: Android_UI: Fix the deadlock when device bootup
import android.os.Looper;
// QTI_END: 2020-09-25: Android_UI: Fix the deadlock when device bootup
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;

// QTI_BEGIN: 2020-09-25: Android_UI: Fix the deadlock when device bootup
import com.android.server.UiThread;

// QTI_END: 2020-09-25: Android_UI: Fix the deadlock when device bootup
/**
 * A list for packages that should force the display out of high refresh rate.
 */
class ForceRefreshRatePackageList {

    private static final String TAG = ForceRefreshRatePackageList.class.getSimpleName();
    private static final String KEY_FORCE_REFRESH_RATE_LIST = "ext_force_refresh_rate_list";
    private static final float REFRESH_RATE_EPSILON  = 0.01f;

    private final ArrayMap<String, Float> mForcedPackageList = new ArrayMap<>();
    private final Object mLock = new Object();
// QTI_BEGIN: 2020-09-25: Android_UI: Fix the deadlock when device bootup
    private final Handler mHandler;
// QTI_END: 2020-09-25: Android_UI: Fix the deadlock when device bootup
    private DisplayInfo mDisplayInfo;
    private SettingsObserver mSettingsObserver;

// QTI_BEGIN: 2020-09-25: Android_UI: Fix the deadlock when device bootup
    ForceRefreshRatePackageList(WindowManagerService wmService, DisplayInfo displayInfo) {
        mDisplayInfo = displayInfo;
        final Looper looper = UiThread.getHandler().getLooper();
        mHandler = new Handler(looper);
// QTI_END: 2020-09-25: Android_UI: Fix the deadlock when device bootup
        mSettingsObserver = new SettingsObserver(wmService.mContext);
// QTI_BEGIN: 2020-09-25: Android_UI: Fix the deadlock when device bootup
        mHandler.post(mSettingsObserver::observe);
// QTI_END: 2020-09-25: Android_UI: Fix the deadlock when device bootup
    }

    private void updateForcedPackagelist(String forcePackagesStr) {
        synchronized (mLock) {
            mForcedPackageList.clear();
            if (!TextUtils.isEmpty(forcePackagesStr)) {
                String[] pairs = forcePackagesStr.split(";");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(",");
                    if (keyValue != null && keyValue.length == 2) {
                        if (!TextUtils.isEmpty(keyValue[0].trim())
                                && !TextUtils.isEmpty(keyValue[1].trim())) {
                            try {
                                String packageName = keyValue[0].trim();
                                Float refreshRate = new Float(keyValue[1].trim());
                                mForcedPackageList.put(packageName, refreshRate);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Invalid refresh rate input! input: " + keyValue);
                            }
                        }
                    }
                }
            }
        }
    }

    int getForceRefreshRateId(String packageName) {
        synchronized (mLock) {
            if(mForcedPackageList.containsKey(packageName)) {
// QTI_BEGIN: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
                float refreshRate = mForcedPackageList.get(packageName).floatValue();
                Display.Mode mode = findModeByRefreshRate(refreshRate);
                return mode != null ? mode.getModeId() : 0;
// QTI_END: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
            }else {
                return 0;
            }
        }
    }

// QTI_BEGIN: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
    float getForceRefreshRate(String packageName) {
        synchronized (mLock) {
            if(mForcedPackageList.containsKey(packageName)) {
                float refreshRate = mForcedPackageList.get(packageName).floatValue();
                Display.Mode mode = findModeByRefreshRate(refreshRate);
                return mode != null ? mode.getRefreshRate() : 0;
            }else {
                return 0;
            }
        }
    }

    private Display.Mode findModeByRefreshRate(float refreshRate) {
// QTI_END: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
        Display.Mode[] modes = mDisplayInfo.supportedModes;
        for (int i = 0; i < modes.length; i++) {
            if (Math.abs(modes[i].getRefreshRate() - refreshRate) < REFRESH_RATE_EPSILON) {
// QTI_BEGIN: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
                return modes[i];
// QTI_END: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
            }
        }
// QTI_BEGIN: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
        return null;
// QTI_END: 2022-03-21: Android_UI: Enable force app refresh rate for frame rate override
    }

    private class SettingsObserver extends ContentObserver {
        private final Uri mForceRefreshRateListSetting =
                Settings.System.getUriFor(KEY_FORCE_REFRESH_RATE_LIST);
        private Context mContext;

        SettingsObserver(@NonNull Context context) {
            super(mHandler);
            mContext = context;
        }

        public void observe() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(mForceRefreshRateListSetting, false, this,
                    UserHandle.USER_SYSTEM);
            updateForcedPackagelist(getForcePackages());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int userId) {
            if (mForceRefreshRateListSetting.equals(uri)) {
                updateForcedPackagelist(getForcePackages());
            }
        }

        private String getForcePackages() {
            ContentResolver cr = mContext.getContentResolver();
            return Settings.System.getString(cr, KEY_FORCE_REFRESH_RATE_LIST);
        }
    }
}

