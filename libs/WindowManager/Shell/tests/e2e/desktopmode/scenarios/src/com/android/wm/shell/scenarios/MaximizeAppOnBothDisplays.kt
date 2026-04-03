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

import android.app.Instrumentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowInsets
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper.MaximizeDesktopAppTrigger
import com.android.server.wm.flicker.helpers.SimpleAppHelper
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import platform.test.desktop.SimulatedConnectedDisplayTestRule

/**
 * Base scenario test for maximizing an app on both displays in extended mode.
 *
 * To verify apps can be made full screen on both displays by using the drag areas, app menu or
 * keyboard shortcuts in extended mode.
 */
@Ignore("Test Base Class")
abstract class MaximizeAppOnBothDisplays(
    private val trigger: MaximizeDesktopAppTrigger = MaximizeDesktopAppTrigger.LAYOUT_MENU
) : TestScenarioBase() {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val displayManager =
        instrumentation.targetContext.getSystemService(DisplayManager::class.java)
    private val testApp = DesktopModeAppHelper(SimpleAppHelper(instrumentation))

    @get:Rule(order = 0) val connectedDisplayRule = SimulatedConnectedDisplayTestRule()

    @Before
    fun setup() {
        val externalDisplayId = connectedDisplayRule.setupTestDisplay()
        wmHelper.StateSyncBuilder().withDesktopModeOnDisplay(externalDisplayId).waitForAndVerify()

        testApp.enterDesktopMode(wmHelper, device)
    }

    @Test
    open fun maximizeAppOnBothDisplays() {
        val externalDisplayId = connectedDisplayRule.addedDisplays.first()

        // 1. Move to external display and maximize
        testApp.moveToNextDisplayViaKeyboard(wmHelper, externalDisplayId)
        val initialBoundsExt = wmHelper.getWindowRegion(testApp).bounds
        testApp.maximiseDesktopApp(wmHelper, device, trigger)
        val maximizedBoundsExt = wmHelper.getWindowRegion(testApp).bounds
        assertMaximized(externalDisplayId, maximizedBoundsExt)

        // 2. Restore to the original size before moving back
        testApp.maximiseDesktopApp(wmHelper, device, trigger)
        val restoredBoundsExt = wmHelper.getWindowRegion(testApp).bounds
        assertEquals(initialBoundsExt, restoredBoundsExt)

        // 3. Move back to default display and maximize
        testApp.moveToNextDisplayViaKeyboard(wmHelper, DEFAULT_DISPLAY)
        testApp.maximiseDesktopApp(wmHelper, device, trigger)
        val maximizedBoundsDef = wmHelper.getWindowRegion(testApp).bounds
        assertMaximized(DEFAULT_DISPLAY, maximizedBoundsDef)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        connectedDisplayRule.cleanupTestDisplays()
    }

    private fun assertMaximized(displayId: Int, bounds: Rect) {
        val displayContext =
            instrumentation.targetContext.createDisplayContext(displayManager.getDisplay(displayId))
        val windowContext =
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION, null)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        val windowMetrics = wm.currentWindowMetrics
        val insets =
            windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        val expectedWidth = windowMetrics.bounds.width() - insets.left - insets.right
        val expectedHeight = windowMetrics.bounds.height() - insets.top - insets.bottom

        assertWithMessage("Window should have been maximized")
            .that(bounds.width())
            .isEqualTo(expectedWidth)
        assertWithMessage("Window should have been maximized")
            .that(bounds.height())
            .isEqualTo(expectedHeight)
    }
}
