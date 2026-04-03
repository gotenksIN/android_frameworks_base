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

package com.android.systemui.clock.data.repository

import android.app.PendingIntent
import android.os.Bundle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.demomode.DemoMode
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.util.time.DateFormatUtil
import com.android.systemui.util.time.SystemClock
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class DemoClockRepository
@Inject
constructor(
    @param:Background private val backgroundScope: CoroutineScope,
    private val systemClock: SystemClock,
    private val dateFormatUtil: DateFormatUtil,
    demoModeController: DemoModeController,
) : ClockRepository {
    private val calendar = Calendar.getInstance()

    override val currentTime: StateFlow<Date> =
        demoModeController
            .demoFlowForCommand(DemoMode.COMMAND_CLOCK)
            .filterNotNull()
            .map { event ->
                val dateFromCommand: Date =
                    processDemoCommand(event) ?: Date(systemClock.currentTimeMillis())
                dateFromCommand
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = Date(systemClock.currentTimeMillis()),
            )

    private fun processDemoCommand(args: Bundle): Date? {
        val millis = args.getString("millis")
        val hhmm = args.getString("hhmm")
        if (millis != null) {
            calendar.timeInMillis = millis.toLong()
        } else if (hhmm != null && hhmm.length == 4) {
            val hh = hhmm.substring(0, 2).toInt()
            val mm = hhmm.substring(2).toInt()
            if (dateFormatUtil.is24HourFormat) {
                calendar.set(Calendar.HOUR_OF_DAY, hh)
            } else {
                calendar.set(Calendar.HOUR, hh)
            }
            calendar.set(Calendar.MINUTE, mm)
        } else {
            return null
        }
        return calendar.time
    }

    override val showSeconds: StateFlow<Boolean> = MutableStateFlow(false)
    override var nextAlarmIntent: StateFlow<PendingIntent?> = MutableStateFlow(null)
    override val onTimeFormatChange: Flow<Unit> = emptyFlow()
}
