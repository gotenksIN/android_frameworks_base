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

package com.android.systemui.notifications.intelligence.rules.domain.interactor

import android.app.NotificationRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.data.repository.fakeNotificationRulesRepository
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRulesInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.notificationRulesInteractor }

    @Test
    fun customRules_filtersOutSystemRules() =
        kosmos.runTest {
            val userRule =
                RuleModel(id = 100, action = ActionModel.Block, filter = null, isSystemRule = false)
            val systemRule =
                RuleModel(
                    id = NotificationRule.RESERVED_ID_PROMOTED,
                    action = ActionModel.Block,
                    filter = null,
                    isSystemRule = true,
                )
            kosmos.fakeNotificationRulesRepository.rules.addAll(listOf(userRule, systemRule))

            val result = underTest.customRules

            assertThat(result).containsExactly(userRule)
        }

    @Test
    fun customRules_emptyIfAllSystemRules() =
        kosmos.runTest {
            val systemRule1 =
                RuleModel(
                    id = NotificationRule.RESERVED_ID_PROMOTED,
                    action = ActionModel.Block,
                    filter = null,
                    isSystemRule = true,
                )
            val systemRule2 =
                RuleModel(
                    id = NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS,
                    action = ActionModel.Block,
                    filter = null,
                    isSystemRule = true,
                )
            kosmos.fakeNotificationRulesRepository.rules.addAll(listOf(systemRule1, systemRule2))

            val result = underTest.customRules

            assertThat(result).isEmpty()
        }
}
