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

import android.graphics.drawable.AnimatedVectorDrawable
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.shared.NotificationSummarizationAllowAnimation
import com.android.systemui.statusbar.notification.data.repository.AnimationState
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.statusbar.notification.data.repository.summarizationAnimationRepository
import com.android.systemui.statusbar.notification.domain.interactor.summarizationInteractor
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SmallTest
@EnableSceneContainer
@RunWith(AndroidJUnit4::class)
class SummarizationInteractorTest : SysuiTestCase() {

    private var kosmos = Kosmos()
    private val repository = kosmos.summarizationAnimationRepository
    private val shadeInteractor = mock<ShadeInteractor>()

    private val Kosmos.underTest by Kosmos.Fixture { summarizationInteractor }

    @Mock private lateinit var entry: NotificationEntry
    @Mock private lateinit var drawable: AnimatedVectorDrawable

    @Before
    fun setup() {
        kosmos.shadeInteractor = shadeInteractor
        MockitoAnnotations.openMocks(this)
        whenever(entry.key).thenReturn("0")
    }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun animationReadyEntryKeys_whenShadeOpenAndNeedsAnimation_containsEntry() =
        kosmos.runTest {
            whenever(shadeInteractor.isShadeFullyExpanded).thenReturn(flowOf(true))

            val readyKeys by collectLastValue(underTest.animationReadyEntryKeys)
            val shadeAllowsAnimation by collectLastValue(underTest.shadeStateAllowsAnimation)
            val isShadeFullyExpanded by
                collectLastValue(kosmos.shadeInteractor.isShadeFullyExpanded)

            activeNotificationListRepository.setActiveNotifs(2) // with keys "0", "1"
            repository.animationHistory["0"] = AnimationState.NEEDS_ANIMATION
            repository.animationHistory["1"] = AnimationState.NEEDS_ANIMATION

            yield()

            assertNotNull(readyKeys)
            assertTrue(shadeAllowsAnimation == true, "Shade should allow animation")
            assertTrue(isShadeFullyExpanded == true, "Shade should be fully expanded")

            assertThat(readyKeys).containsExactly("0", "1")
        }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun animationReadyEntryKeys_whenShadeClosedAndNeedsAnimation_empty() =
        kosmos.runTest {
            whenever(shadeInteractor.isShadeFullyExpanded).thenReturn(flowOf(false))

            val readyKeys by collectLastValue(underTest.animationReadyEntryKeys)
            val shadeAllowsAnimation by collectLastValue(underTest.shadeStateAllowsAnimation)
            val isShadeFullyExpanded by
                collectLastValue(kosmos.shadeInteractor.isShadeFullyExpanded)

            activeNotificationListRepository.setActiveNotifs(2) // with keys "0", "1"
            repository.animationHistory["0"] = AnimationState.NEEDS_ANIMATION
            repository.animationHistory["1"] = AnimationState.NEEDS_ANIMATION

            yield()

            // Keys aren't queued in the ready flow until shade is open.
            assertThat(readyKeys).isEmpty()
        }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun trackDecoratedEntry_addsToRepository() =
        kosmos.runTest {
            underTest.trackDecoratedEntry(entry, drawable)

            assertThat(repository.drawables["0"]).isEqualTo(drawable)
            assertThat(repository.animationHistory["0"]).isEqualTo(AnimationState.NEEDS_ANIMATION)
        }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun onTriggered_updatesHistory() =
        kosmos.runTest {
            repository.animationHistory["0"] = AnimationState.NEEDS_ANIMATION
            underTest.onTriggered("0")
            assertThat(repository.animationHistory["0"]).isEqualTo(AnimationState.HAS_ANIMATED)
        }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun cleanupEntry_removesEverything() =
        kosmos.runTest {
            repository.drawables["0"] = drawable
            repository.animationHistory["0"] = AnimationState.HAS_ANIMATED

            underTest.cleanupEntry(entry)

            assertThat(repository.drawables).isEmpty()
            assertThat(repository.animationHistory).isEmpty()
        }
}
