/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.provider.AlarmClock
import com.android.systemui.clock.data.repository.ClockRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest

@SysUISingleton
class ClockInteractor
@Inject
constructor(
    @param:ShadeDisplayAware private val context: Context,
    private val clockRepository: ClockRepository,
    private val activityStarter: ActivityStarter,
) {
    val onTimeFormatChange: Flow<Unit> = clockRepository.onTimeFormatChange
    private val longerPattern = context.getString(R.string.abbrev_wday_month_day_no_year_alarm)
    private val shorterPattern = context.getString(R.string.abbrev_month_day_no_year)
    val currentTime: StateFlow<Date> = clockRepository.currentTime
    val showSeconds: StateFlow<Boolean> = clockRepository.showSeconds

    @OptIn(ExperimentalCoroutinesApi::class)
    val longerDateFormat: Flow<DateFormat> =
        onTimeFormatChange.mapLatest { getFormatFromPattern(longerPattern) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val shorterDateFormat: Flow<DateFormat> =
        onTimeFormatChange.mapLatest { getFormatFromPattern(shorterPattern) }

    fun launchClockActivity() {
        val nextAlarmIntentValue = clockRepository.nextAlarmIntent.value
        if (nextAlarmIntentValue != null) {
            activityStarter.postStartActivityDismissingKeyguard(nextAlarmIntentValue)
        } else {
            activityStarter.postStartActivityDismissingKeyguard(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                0,
            )
        }
    }

    private fun getFormatFromPattern(pattern: String?): DateFormat {
        return DateFormat.getInstanceForSkeleton(pattern, Locale.getDefault()).apply {
            setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        }
    }
}
