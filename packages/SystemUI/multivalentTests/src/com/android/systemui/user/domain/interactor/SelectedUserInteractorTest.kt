package com.android.systemui.user.domain.interactor

import android.content.pm.UserInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SelectedUserInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(USER_INFOS)
    }

    @Test
    fun getSelectedUserIdReturnsId() =
        kosmos.runTest {
            fakeUserRepository.setSelectedUserInfo(USER_INFOS[0])

            val actualId = selectedUserInteractor.getSelectedUserId()

            assertThat(actualId).isEqualTo(USER_INFOS[0].id)
        }

    @Test
    fun isUserSwitching() =
        kosmos.runTest {
            fakeUserRepository.setSelectedUserInfo(USER_INFOS[0])
            assertThat(selectedUserInteractor.isUserSwitching.value).isFalse()

            fakeUserRepository.setMainUserIsUserSwitching()
            assertThat(selectedUserInteractor.isUserSwitching.value).isTrue()
        }

    companion object {
        private val USER_INFOS =
            listOf(UserInfo(100, "First user", 0), UserInfo(101, "Second user", 0))
    }
}
