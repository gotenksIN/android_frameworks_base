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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.app.notificationManager
import android.content.applicationContext
import android.content.mockContentResolver
import androidx.compose.runtime.mutableStateListOf
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toFullRule
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.shared.notificationRulesLogBuffer

val Kosmos.realNotificationRulesRepository by
    Kosmos.Fixture {
        NotificationRulesRepositoryImpl(
            notificationManager,
            installedAppsRepository = fakeInstalledAppsRepository,
            contactsRepository = fakeContactsRepository,
            conversationPartnersRepository = fakeConversationPartnersRepository,
            contentResolver = mockContentResolver,
            freeformRuleRepository = realFreeformRuleRepository,
            applicationContext = applicationContext,
            applicationScope = applicationCoroutineScope,
            mainDispatcher = testDispatcher,
            backgroundDispatcher = testDispatcher,
            logBuffer = notificationRulesLogBuffer,
        )
    }

val Kosmos.fakeNotificationRulesRepository by Kosmos.Fixture { FakeNotificationRulesRepository() }

class FakeNotificationRulesRepository : NotificationRulesRepository {
    override var rules = mutableStateListOf<RuleModel>()

    var deleteRuleSuccessfully = true

    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel> {
        return ResponseModel.Error
    }

    override suspend fun saveRule(rule: DraftRuleModel): Boolean {
        return when (rule) {
            is DraftRuleModel.New -> {
                rules.add(0, rule.toFullRule(generateNewId()))
                true
            }
            is DraftRuleModel.PreExisting -> {
                val existingRuleIndex = rules.indexOfFirst { it.id == rule.id }
                rules[existingRuleIndex] = rule.toFullRule()
                true
            }
        }
    }

    override suspend fun deleteRule(ruleId: Int): Boolean {
        if (deleteRuleSuccessfully) {
            rules.removeIf { it.id == ruleId }
        }
        return deleteRuleSuccessfully
    }

    private fun generateNewId(): Int {
        val currentRuleIds = rules.map { it.id }
        return (100..200).first { newIdCandidate -> newIdCandidate !in currentRuleIds }
    }
}
