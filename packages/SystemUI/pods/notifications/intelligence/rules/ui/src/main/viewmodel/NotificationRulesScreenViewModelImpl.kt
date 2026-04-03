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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.domain.interactor.ContactsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.NotificationRulesInteractor
import com.android.systemui.notifications.intelligence.rules.shared.NotificationRulesLog
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NotificationRulesScreenViewModelImpl
@AssistedInject
constructor(
    @Assisted override val backStack: List<RulesScreenViewState>,
    private val interactor: NotificationRulesInteractor,
    private val contactsInteractor: ContactsInteractor,
    @Application private val applicationScope: CoroutineScope,
    @NotificationRulesLog logBuffer: LogBuffer,
) : NotificationRulesScreenViewModel, HydratedActivatable() {
    private val logger = Logger(logBuffer, "ScreenViewModel")

    override val rules: List<RuleModel>
        get() = interactor.customRules

    override val currentScreen: RulesScreenViewState
        get() = backStack[backStack.size - 1]

    // Note: This only stores a single rule ID, so only one rule can show a deletion error at a time
    override var ruleWithDeletionError: Int? by mutableStateOf(null)
        private set

    override fun buildRuleText(rule: RuleModel, resources: Resources): RuleDisplayModel {
        return buildReadOnlyRuleText(rule, resources, logger)
    }

    override suspend fun loadContactBitmapFromUri(
        uri: Uri,
        userContext: Context,
        sizePx: Int,
    ): Bitmap? {
        return contactsInteractor.loadBitmapFromUri(uri, userContext, sizePx)
    }

    override fun deleteRule(ruleId: Int) {
        // Use application scope so it's never cancelled
        applicationScope.launch {
            val wasDeletedSuccessfully = interactor.deleteRule(ruleId)
            ruleWithDeletionError =
                if (wasDeletedSuccessfully) {
                    null
                } else {
                    ruleId
                }
        }
    }

    @AssistedFactory
    interface Factory : NotificationRulesScreenViewModel.Factory {
        override fun create(
            backStack: List<RulesScreenViewState>
        ): NotificationRulesScreenViewModelImpl
    }
}
