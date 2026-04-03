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

package com.android.systemui.clock.data.repository

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.systemui.Flags
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.statusbar.policy.NextAlarmController
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.time.SystemClock
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

interface ClockRepository {
    val currentTime: StateFlow<Date>
    val showSeconds: StateFlow<Boolean>
    val nextAlarmIntent: StateFlow<PendingIntent?>
    val onTimeFormatChange: Flow<Unit>
}

@SysUISingleton
class ClockRepositoryImpl
@Inject
constructor(
    @param:Application private val applicationScope: CoroutineScope,
    @param:Background private val backgroundScope: CoroutineScope,
    private val systemClock: SystemClock,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val nextAlarmController: NextAlarmController,
    secureSettingsRepository: SecureSettingsRepository,
) : ClockRepository {

    override val showSeconds: StateFlow<Boolean> =
        if (!Flags.clockModernization()) {
            MutableStateFlow(false)
        } else {
            secureSettingsRepository
                .boolSetting(CLOCK_SECONDS_TUNER_KEY, false)
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.WhileSubscribed(),
                    initialValue = false,
                )
        }

    /**
     * [StateFlow] that emits the current `Date`.
     *
     * This flow is designed to be efficient; it ticks once per second only if seconds are being
     * displayed, else it ticks once per minute. It will also emit a new value whenever the time is
     * changed by the system.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentTime: StateFlow<Date> =
        showSeconds
            .flatMapLatest { show ->
                val ticker =
                    if (show) {
                        flow {
                            val startTime = systemClock.currentTimeMillis()
                            while (true) {
                                emit(Unit)
                                val delaySkewMillis =
                                    (systemClock.currentTimeMillis() - startTime) % 1000L
                                delay(1000L - delaySkewMillis)
                            }
                        }
                    } else {
                        broadcastFlowForActions(Intent.ACTION_TIME_TICK)
                    }
                val manualOrTimezoneChanges = broadcastFlowForActions(Intent.ACTION_TIME_CHANGED)
                merge(ticker, manualOrTimezoneChanges).emitOnStart()
            }
            .map { Date(systemClock.currentTimeMillis()) }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = Date(systemClock.currentTimeMillis()),
            )

    /**
     * Returns a `Flow` that, when collected, emits `Unit` whenever a broadcast matching one of the
     * given [actionsToFilter] is received.
     */
    private fun broadcastFlowForActions(
        vararg actionsToFilter: String,
        user: UserHandle? = null,
    ): Flow<Unit> {
        return broadcastDispatcher.broadcastFlow(
            filter = IntentFilter().apply { actionsToFilter.forEach(::addAction) },
            user = user,
        )
    }

    /** [Flow] that emits `Unit` whenever the time settings have changed. */
    override val onTimeFormatChange: Flow<Unit> =
        broadcastFlowForActions(
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_CONFIGURATION_CHANGED,
                Intent.ACTION_USER_SWITCHED,
            )
            .emitOnStart()

    override val nextAlarmIntent: StateFlow<PendingIntent?> =
        callbackFlow {
                val callback =
                    NextAlarmController.NextAlarmChangeCallback { nextAlarm ->
                        trySend(nextAlarm?.showIntent)
                    }
                nextAlarmController.addCallback(callback)
                awaitClose { nextAlarmController.removeCallback(callback) }
            }
            .stateIn(backgroundScope, SharingStarted.Eagerly, null)

    companion object {
        @VisibleForTesting const val CLOCK_SECONDS_TUNER_KEY = "clock_seconds"
    }
}
