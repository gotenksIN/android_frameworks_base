/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.os.Build.HW_TIMEOUT_MULTIPLIER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.android.server.wm.utils.CommonUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Presubmit
public class WindowLayoutObserverTests {
    private static final int WAIT_TIMEOUT_MS = 5000 * HW_TIMEOUT_MULTIPLIER;

    private TestActivity mActivity;

    @Before
    public void setUp() {
        CommonUtils.dismissKeyguard();
    }

    @After
    public void tearDown() {
        if (mActivity != null) {
            CommonUtils.waitUntilActivityRemoved(mActivity);
        }
    }

    @Test
    public void testRegularCallbackOnLaunch() {
        mActivity = TestActivity.launch();

        assertThat(mActivity.mCountOnApplyWindowInsets).isAtMost(1);
        assertThat(mActivity.mCountOnGlobalLayout).isAtMost(1);
    }

    public static class TestActivity extends Activity {

        static TestActivity launch() {
            final Instrumentation instrumentation = getInstrumentation();
            final Context context = instrumentation.getContext();
            final Intent intent = new Intent(context, TestActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
            final TestActivity activity = (TestActivity) instrumentation.startActivitySync(
                    intent, options.toBundle());
            try {
                if (!activity.mLaunchLatch.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    throw new AssertionError("Failed to launch " + intent);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return activity;
        }

        final CountDownLatch mLaunchLatch = new CountDownLatch(1);
        int mCountOnApplyWindowInsets;
        int mCountOnGlobalLayout;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            final View rootView = getWindow().getDecorView();
            rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                mCountOnApplyWindowInsets++;
                return view.onApplyWindowInsets(windowInsets);
            });
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                    () -> mCountOnGlobalLayout++);
            rootView.post(mLaunchLatch::countDown);
        }
    }
}
