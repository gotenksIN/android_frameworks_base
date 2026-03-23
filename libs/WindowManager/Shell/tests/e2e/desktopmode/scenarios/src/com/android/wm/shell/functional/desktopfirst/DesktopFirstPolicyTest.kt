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

package com.android.wm.shell.functional.desktopfirst

import android.platform.test.annotations.Postsubmit
import android.platform.test.rule.ScreenRecordRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.scenarios.DesktopFirstPolicy
import com.android.wm.shell.shared.desktopmode.DesktopState
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/* Functional test for [DesktopFirstPolicyTest]. */
@RunWith(Parameterized::class)
@Postsubmit
@ScreenRecordRule.ScreenRecord
class DesktopFirstPolicyTest(
    private val launchSource: LaunchSource,
    private val backgroundState: BackgroundState,
    private val launchType: LaunchType,
) : DesktopFirstPolicy(launchSource, backgroundState, launchType) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "LaunchSource={0}, Background={1}, LaunchType={2}")
        fun data(): Iterable<Array<Any>> {
            val parameters = mutableListOf<Array<Any>>()
            for (source in DesktopFirstPolicy.LaunchSource.values()) {
                for (bg in DesktopFirstPolicy.BackgroundState.values()) {
                    for (type in DesktopFirstPolicy.LaunchType.values()) {
                        if (shouldSkip(source, bg)) {
                            continue
                        }
                        parameters.add(arrayOf(source, bg, type))
                    }
                }
            }
            return parameters
        }

        private fun shouldSkip(source: LaunchSource, bg: BackgroundState): Boolean {
            val context = InstrumentationRegistry.getInstrumentation().context
            val showHomeBehindDesktop =
                DesktopState.fromContext(context).shouldShowHomeBehindDesktop
            if (source == LaunchSource.HOME_SHORTCUT && bg == BackgroundState.FULLSCREEN_ON_TOP) {
                // Cannot launch app from home shortcut when fullscreen app is on top.
                return true
            }
            if (
                !showHomeBehindDesktop &&
                    source == LaunchSource.HOME_SHORTCUT &&
                    bg == BackgroundState.DESK_ON_TOP
            ) {
                // Cannot launch app from home shortcut when home is not shown behind
                // desktop.
                return true
            }
            return false
        }
    }
}
