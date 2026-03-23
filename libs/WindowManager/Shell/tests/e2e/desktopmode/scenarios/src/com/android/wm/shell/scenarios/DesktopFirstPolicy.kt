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

package com.android.wm.shell.scenarios

import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.platform.test.annotations.RequiresFlagsEnabled
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Tests the desktop-first policy for app launches, verifying that apps open in the correct
 * windowing mode (freeform or fullscreen) based on their launch type (New Launch, Freeform refocus,
 * Fullscreen refocus), regardless of the background state.
 */
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
@Ignore("Base Test Class")
abstract class DesktopFirstPolicy(
    private val backgroundState: BackgroundState,
    private val launchType: LaunchType,
) : TestScenarioBase() {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)

    private val targetApp = BrowserAppHelper(instrumentation)
    private val backgroundApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    /** Represents the different states of the device background before an app is launched. */
    enum class BackgroundState {
        /** The home activity is on top. */
        HOME_ON_TOP,
        /** The non-home fullscreen activity is on top. */
        FULLSCREEN_ON_TOP,
        /** The empty desk is on top. */
        DESK_ON_TOP,
    }

    /** Represents the different types of app launches being tested. */
    enum class LaunchType {
        /** A new app is launched. */
        NEW_LAUNCH,
        /** An existing freeform app is refocused. */
        FREEFORM_REFOCUS,
        /** An existing fullscreen app is refocused. */
        FULLSCREEN_REFOCUS,
    }

    @Before
    fun setup() {
        Assume.assumeTrue(Utils.isInDesktopFirstMode(wmHelper, DEFAULT_DISPLAY))
        setupLaunchTypePrerequisites()
        setupBackgroundState()
    }

    @After
    fun teardown() {
        targetApp.exit(wmHelper)
        backgroundApp.exit(wmHelper)
    }

    @Test
    fun testAppLaunchWindowingMode() {
        tapl.showTaskbarIfHidden()
        tapl.launchedAppState.taskbar.getAppIcon(targetApp.appName).launch(targetApp.packageName)

        val expectedWindowingMode =
            when (launchType) {
                LaunchType.NEW_LAUNCH -> WINDOWING_MODE_FREEFORM
                LaunchType.FREEFORM_REFOCUS -> WINDOWING_MODE_FREEFORM
                LaunchType.FULLSCREEN_REFOCUS -> WINDOWING_MODE_FULLSCREEN
            }

        Utils.waitForAndVerifyActivityState(wmHelper, targetApp, expectedWindowingMode)
    }

    private fun setupLaunchTypePrerequisites() {
        if (launchType == LaunchType.FREEFORM_REFOCUS) {
            targetApp.launchViaIntent(wmHelper, intent = targetApp.openAppIntent)
            Utils.waitForAndVerifyActivityState(wmHelper, targetApp, WINDOWING_MODE_FREEFORM)
            device.pressHome()
            wmHelper
                .StateSyncBuilder()
                .withHomeActivityVisible()
                .add("targetApp is not visible") { dump ->
                    !dump.wmState.isActivityVisible(targetApp)
                }
                .waitForAndVerify()
        } else if (launchType == LaunchType.FULLSCREEN_REFOCUS) {
            val options = ActivityOptions.makeBasic()
            options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN)
            targetApp.launchViaIntent(wmHelper, intent = targetApp.openAppIntent, options = options)
            Utils.waitForAndVerifyActivityState(wmHelper, targetApp, WINDOWING_MODE_FULLSCREEN)
            device.pressHome()
            wmHelper
                .StateSyncBuilder()
                .withHomeActivityVisible()
                .add("targetApp is not visible") { dump ->
                    !dump.wmState.isActivityVisible(targetApp)
                }
                .waitForAndVerify()
        }
    }

    private fun setupBackgroundState() {
        if (backgroundState == BackgroundState.FULLSCREEN_ON_TOP) {
            val options = ActivityOptions.makeBasic()
            options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN)
            backgroundApp.launchViaIntent(wmHelper = wmHelper, options = options)
            Utils.waitForAndVerifyActivityState(wmHelper, backgroundApp, WINDOWING_MODE_FULLSCREEN)
        } else if (backgroundState == BackgroundState.DESK_ON_TOP) {
            // To get desk on top: create another desk and activate it
            val overview =
                tapl.workspace
                    .openOverviewFromActionPlusTabKeyboardShortcut()
                    .createDeskViaClickAddDesktopButton()

            // Activate the newly created desk by flinging and opening it
            overview.flingBackward()
            var flingRetries = MAX_FLING_RETRIES
            while (!tapl.overview.currentTask.isDesktop && flingRetries > 0) {
                tapl.overview.flingBackward()
                flingRetries--
            }
            check(tapl.overview.currentTask.isDesktop) { "Failed to find Desktop task in Overview" }
            tapl.overview.currentTask.open()
            wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        }
    }

    companion object {
        private const val MAX_FLING_RETRIES = 10
    }
}
