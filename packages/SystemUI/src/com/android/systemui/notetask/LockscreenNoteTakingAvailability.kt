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

package com.android.systemui.notetask

import android.content.Context
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notetask.NoteTaskEntryPoint.QUICK_AFFORDANCE
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Encapsulates the logic to determine if note taking on the lock screen is available. */
@SysUISingleton
class LockscreenNoteTakingAvailability
@Inject
constructor(
    private val context: Context,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val userResolver: NoteTaskUserResolver,
) {

    /** Whether note taking on the lock screen is supported without user consent flow. */
    private val isLegacyUnconsentedLockScreenNoteTakingSupported: Boolean by lazy {
        context.resources.getBoolean(R.bool.config_supportLegacyUnconsentedLockScreenNoteTaking)
    }

    /** Returns true if note taking on lock screen is enabled. */
    suspend fun isLockscreenNoteTakingEnabled(): Boolean =
        withContext(bgDispatcher) {
            when {
                Flags.enabledNotesLockscreenConsentFlow() -> isConsentAccepted()
                isLegacyUnconsentedLockScreenNoteTakingSupported -> true
                else -> false
            }
        }

    /** Returns true if notes should show in lockscreen shortcut picker. */
    suspend fun shouldShowNotesInLockscreenShortcutPicker(): Boolean =
        withContext(bgDispatcher) {
            when {
                Flags.enabledNotesLockscreenConsentFlow() -> true
                isLegacyUnconsentedLockScreenNoteTakingSupported -> true
                else -> false
            }
        }

    private suspend fun isConsentAccepted(): Boolean {
        val rawUser = userResolver.getUserForHandlingNoteTaking(QUICK_AFFORDANCE)
        val user = userResolver.resolveParentUserIfManaged(rawUser) ?: return false
        return Settings.Secure.getIntForUser(
            context.contentResolver,
            Settings.Secure.LOCK_SCREEN_NOTE_TAKING_CONSENT,
            CONSENT_NOT_GRANTED,
            user.identifier,
        ) == CONSENT_GRANTED
    }

    companion object {
        @VisibleForTesting const val CONSENT_NOT_GRANTED = 0
        @VisibleForTesting const val CONSENT_GRANTED = 1
    }
}
