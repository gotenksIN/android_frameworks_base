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

import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/** atest SystemUITests:LockscreenNoteTakingAvailabilityTest */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LockscreenNoteTakingAvailabilityTest : SysuiTestCase() {

    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule = SetFlagsRule()

    @Mock private lateinit var userResolver: NoteTaskUserResolver

    private val kosmos = testKosmos()
    private lateinit var underTest: LockscreenNoteTakingAvailability

    @Before
    fun setUp() {
        underTest = LockscreenNoteTakingAvailability(mContext, kosmos.testDispatcher, userResolver)
    }

    private fun setLegacyUnconsentedLockscreenNoteTakingConfig(supported: Boolean) {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_supportLegacyUnconsentedLockScreenNoteTaking,
            supported,
        )
    }

    private suspend fun setConsent(
        accepted: Boolean,
        resolvedUser: UserHandle,
        noteTakingUser: UserHandle = resolvedUser,
    ) {
        whenever(userResolver.getUserForHandlingNoteTaking(any())).thenReturn(noteTakingUser)
        whenever(userResolver.resolveParentUserIfManaged(noteTakingUser)).thenReturn(resolvedUser)
        Settings.Secure.putIntForUser(
            mContext.contentResolver,
            Settings.Secure.LOCK_SCREEN_NOTE_TAKING_CONSENT,
            if (accepted) {
                LockscreenNoteTakingAvailability.CONSENT_GRANTED
            } else {
                LockscreenNoteTakingAvailability.CONSENT_NOT_GRANTED
            },
            resolvedUser.identifier,
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagDisabled_configTrue_returnsTrue() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(true)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagDisabled_configFalse_returnsFalse() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(false)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagEnabled_consentTrue_returnsTrue() =
        kosmos.runTest {
            setConsent(accepted = true, resolvedUser = TEST_USER)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagEnabled_consentFalse_returnsFalse() =
        kosmos.runTest {
            setConsent(accepted = false, resolvedUser = TEST_USER)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagEnabled_managedUser_consentTrueInParent_returnsTrue() =
        kosmos.runTest {
            val managedUser = UserHandle.of(10)
            val parentUser = UserHandle.of(0)
            setConsent(accepted = true, resolvedUser = parentUser, noteTakingUser = managedUser)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun isLockscreenNoteTakingEnabled_flagEnabled_managedUser_noParent_returnsFalse() =
        kosmos.runTest {
            val managedUser = UserHandle.of(10)
            whenever(userResolver.getUserForHandlingNoteTaking(any())).thenReturn(managedUser)
            whenever(userResolver.resolveParentUserIfManaged(managedUser)).thenReturn(null)

            assertThat(underTest.isLockscreenNoteTakingEnabled()).isFalse()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun shouldShowNotesInLockscreenShortcutPicker_flagDisabled_configTrue_returnsTrue() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(true)

            assertThat(underTest.shouldShowNotesInLockscreenShortcutPicker()).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun shouldShowNotesInLockscreenShortcutPicker_flagDisabled_configFalse_returnsFalse() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(false)

            assertThat(underTest.shouldShowNotesInLockscreenShortcutPicker()).isFalse()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLED_NOTES_LOCKSCREEN_CONSENT_FLOW)
    fun shouldShowNotesInLockscreenShortcutPicker_flagEnabled_configFalse_returnsTrue() =
        kosmos.runTest {
            setLegacyUnconsentedLockscreenNoteTakingConfig(false)

            assertThat(underTest.shouldShowNotesInLockscreenShortcutPicker()).isTrue()
        }

    private companion object {
        val TEST_USER = UserHandle.of(100)
    }
}
