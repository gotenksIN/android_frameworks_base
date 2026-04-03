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

package com.android.systemui.clock.domain.interactor

import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.activityStarter
import com.android.systemui.testKosmosNew
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ClockInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by Kosmos.Fixture { clockInteractor }

    @Test
    fun launchClockActivity_default() =
        kosmos.runTest {
            underTest.launchClockActivity()
            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat { intent: Intent? -> intent?.action == AlarmClock.ACTION_SHOW_ALARMS },
                    any<Int>(),
                )
        }

    @Test
    fun onTimeFormatChange_formattingIntents_emits() =
        kosmos.runTest {
            val formatChanges by collectValues(underTest.onTimeFormatChange)

            assertThat(formatChanges).hasSize(1)

            sendIntentActionBroadcast(Intent.ACTION_TIMEZONE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_LOCALE_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_CONFIGURATION_CHANGED)
            sendIntentActionBroadcast(Intent.ACTION_USER_SWITCHED)

            // 1 (initial) + 4 (broadcasts) = 5
            assertThat(formatChanges).hasSize(5)
        }

    @Test
    fun currentTime_initialTime() =
        kosmos.runTest {
            assertThat(underTest.currentTime.value)
                .isEqualTo(Date(fakeSystemClock.currentTimeMillis()))
        }

    @Test
    fun currentTime_timeChanged() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val earlierTime = checkNotNull(currentTime)

            fakeSystemClock.advanceTime(3.seconds.inWholeMilliseconds)

            sendIntentActionBroadcast(Intent.ACTION_TIME_CHANGED)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(3.seconds)
        }

    @Test
    fun currentTime_timeTicked() =
        kosmos.runTest {
            val currentTime by collectLastValue(underTest.currentTime)

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val earlierTime = checkNotNull(currentTime)

            fakeSystemClock.advanceTime(7.seconds.inWholeMilliseconds)

            sendIntentActionBroadcast(Intent.ACTION_TIME_TICK)
            val laterTime = checkNotNull(currentTime)

            assertThat(differenceBetween(laterTime, earlierTime)).isEqualTo(7.seconds)
        }

    private fun differenceBetween(date1: Date, date2: Date): Duration {
        return (date1.time - date2.time).milliseconds
    }

    private fun Kosmos.sendIntentActionBroadcast(intentAction: String) {
        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, Intent(intentAction))
    }
}
