/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.clock.data.repository

import android.content.Intent
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.clock.ClockModernization
import com.android.systemui.demoModeController
import com.android.systemui.demomode.DemoMode
import com.android.systemui.demomode.DemoMode.ACTION_DEMO
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.fakeGlobalSettings
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import java.util.Date
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by Kosmos.Fixture { clockRepositorySwitcher }

    @Before
    fun setUp() {
        kosmos.fakeGlobalSettings.putInt(DemoModeController.DEMO_MODE_ALLOWED, 1)
    }

    @Test
    @EnableFlags(ClockModernization.FLAG_NAME)
    fun showSeconds_tunerKeyChanges_flowEmits() =
        kosmos.runTest {
            val showSeconds by collectLastValue(underTest.showSeconds)
            val secureSettings = kosmos.secureSettingsRepository
            assertThat(showSeconds).isFalse()

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 1)

            assertThat(showSeconds).isTrue()

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 0)

            assertThat(showSeconds).isFalse()
        }

    @Test
    fun currentTime_showSecondsFalse_notChangeEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val initialTime = currentTime!!

            assertThat(showSeconds).isFalse()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isEqualTo(initialTime)
        }

    @Test
    @EnableFlags(ClockModernization.FLAG_NAME)
    fun currentTime_showSecondsTrue_changesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val secureSettings = kosmos.secureSettingsRepository
            val initialTime = currentTime!!

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 1)

            assertThat(showSeconds).isTrue()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(initialTime)

            val timeAfterTick = currentTime!!
            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(timeAfterTick)
        }

    @Test
    @EnableFlags(ClockModernization.FLAG_NAME)
    fun currentTime_showSecondsTrueToFalse_notChangesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val secureSettings = kosmos.secureSettingsRepository
            val initialTime = currentTime!!

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 1)

            assertThat(showSeconds).isTrue()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isNotEqualTo(initialTime)

            val timeAfterTick = currentTime!!

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 0)

            assertThat(showSeconds).isFalse()

            advanceTimeBy(1000)
            fakeSystemClock.advanceTime(1000)

            assertThat(currentTime).isEqualTo(timeAfterTick)
        }

    @Test
    @EnableFlags(ClockModernization.FLAG_NAME)
    fun currentTime_showSecondsFalseToTrue_changesEverySecond() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)
            val showSeconds by collectLastValue(underTest.showSeconds)
            val secureSettings = kosmos.secureSettingsRepository
            val initialTime = currentTime!!

            assertThat(showSeconds).isFalse()

            fakeSystemClock.advanceTime(1000)
            advanceTimeBy(1000)

            assertThat(currentTime).isEqualTo(initialTime)

            val timeAfterTick = currentTime!!

            secureSettings.setInt(ClockRepositoryImpl.CLOCK_SECONDS_TUNER_KEY, 1)

            assertThat(showSeconds).isTrue()

            advanceTimeBy(1000)
            fakeSystemClock.advanceTime(1000)

            assertThat(currentTime).isNotEqualTo(timeAfterTick)
        }

    @Test
    fun currentTime_useDemoTimeHhmm() =
        kosmos.runTest {
            val calendar = Calendar.getInstance()
            val currentTime by collectLastValue(underTest.currentTime)
            startDemoMode()

            sendDemoCommand(args = mapOf("hhmm" to "1337"))

            calendar.time = checkNotNull(currentTime)
            assertThat(calendar.get(Calendar.HOUR)).isEqualTo(1)
            assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(37)

            // Trigger another update.
            sendDemoCommand(args = mapOf("hhmm" to "1903"))

            calendar.time = checkNotNull(currentTime)
            assertThat(calendar.get(Calendar.HOUR)).isEqualTo(7)
            assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(3)
        }

    @Test
    fun currentTime_useDemoTimeMillis() =
        kosmos.runTest {
            val calendar = Calendar.getInstance()
            val currentTime by collectLastValue(underTest.currentTime)
            startDemoMode()

            sendDemoCommand(args = mapOf("millis" to "13214343"))

            calendar.time = checkNotNull(currentTime)
            assertThat(calendar.timeInMillis).isEqualTo(13214343L)

            // Trigger another update.
            sendDemoCommand(args = mapOf("millis" to "3253462"))

            calendar.time = checkNotNull(currentTime)
            assertThat(calendar.timeInMillis).isEqualTo(3253462L)
        }

    @Test
    fun currentTime_switchBackToInitialWhenDemoModeFinishes() =
        kosmos.runTest {
            val calendar = Calendar.getInstance()
            val currentTime by collectLastValue(underTest.currentTime)
            val initialTime: Date = checkNotNull(currentTime)
            startDemoMode()

            sendDemoCommand(args = mapOf("millis" to "13214343"))

            calendar.time = checkNotNull(currentTime)
            assertThat(calendar.timeInMillis).isEqualTo(13214343L)
            assertThat(currentTime).isNotEqualTo(initialTime)

            finishDemoMode()

            assertThat(currentTime).isEqualTo(initialTime)
        }

    private fun Kosmos.sendDemoCommand(
        args: Map<String, String> = emptyMap(),
        command: String = DemoMode.COMMAND_CLOCK,
    ) {
        runOnMainThreadAndWaitForIdleSync {
            val intent = Intent(ACTION_DEMO)
            intent.putExtra("command", command)
            args.forEach { arg -> intent.putExtra(arg.key, arg.value) }
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
        }
    }

    private fun Kosmos.startDemoMode() {
        demoModeController.initialize()
        sendDemoCommand(emptyMap(), DemoMode.COMMAND_ENTER)
    }

    private fun Kosmos.finishDemoMode() {
        sendDemoCommand(emptyMap(), DemoMode.COMMAND_EXIT)
    }
}
