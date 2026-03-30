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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.demomode.domain.interactor.DemoModeInteractor
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class ClockRepositorySwitcher
@Inject
constructor(
    @param:Background private val scope: CoroutineScope,
    private val clockRepositoryImpl: ClockRepositoryImpl,
    private val demoClockRepository: DemoClockRepository,
    demoModeInteractor: DemoModeInteractor,
) : ClockRepository {

    val activeRepo: StateFlow<ClockRepository> =
        demoModeInteractor.isInDemoMode
            .mapLatest { demoMode -> if (demoMode) demoClockRepository else clockRepositoryImpl }
            .stateIn(scope, SharingStarted.WhileSubscribed(), clockRepositoryImpl)

    override val currentTime: StateFlow<Date> =
        activeRepo
            .flatMapLatest { it.currentTime }
            .stateIn(scope, SharingStarted.WhileSubscribed(), clockRepositoryImpl.currentTime.value)

    override val showSeconds: StateFlow<Boolean> =
        activeRepo
            .flatMapLatest { it.showSeconds }
            .stateIn(scope, SharingStarted.WhileSubscribed(), clockRepositoryImpl.showSeconds.value)

    override val onTimeFormatChange: Flow<Unit> = activeRepo.flatMapLatest { it.onTimeFormatChange }
    override val nextAlarmIntent: StateFlow<PendingIntent?> =
        activeRepo
            .flatMapLatest { it.nextAlarmIntent }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                clockRepositoryImpl.nextAlarmIntent.value,
            )
}
