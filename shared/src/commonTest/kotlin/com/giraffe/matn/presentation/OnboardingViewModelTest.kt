package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.domain.repository.OnboardingRepository
import com.giraffe.matn.domain.usecase.CompleteOnboardingUseCase
import com.giraffe.matn.domain.usecase.GetOnboardingStatusNowUseCase
import com.giraffe.matn.presentation.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T079 (US3, onboarding-permissions-contract.md §7) — panel advance; skip from every panel
 * completes; completing twice is idempotent; the interrupted path (FR-025) restarts rather than
 * strands the student.
 */
class OnboardingViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    /** In-memory fake — mirrors [FakeAppearancePreferencesRepository] in SettingsViewModelTest. */
    private class FakeOnboardingRepository(initial: OnboardingStatus = OnboardingStatus.NOT_COMPLETED) : OnboardingRepository {
        private val status = MutableStateFlow(initial)
        override fun observeStatus(): Flow<OnboardingStatus> = status
        override fun statusNow(): OnboardingStatus = status.value
        override suspend fun markCompleted(): Resource<Unit> {
            status.value = OnboardingStatus.COMPLETED
            return Resource.Success(Unit)
        }
    }

    private fun newViewModel(repo: OnboardingRepository) = OnboardingViewModel(CompleteOnboardingUseCase(repo))

    @Test
    fun `onNext advances the panel index until the last panel`() = runTest {
        val vm = newViewModel(FakeOnboardingRepository())
        assertEquals(0, vm.state.value.panelIndex)
        vm.onNext()
        assertEquals(1, vm.state.value.panelIndex)
        vm.onNext()
        assertEquals(2, vm.state.value.panelIndex)
        assertTrue(vm.state.value.isLastPanel)
    }

    @Test
    fun `onNext from the last panel completes instead of advancing further`() = runTest {
        val repo = FakeOnboardingRepository()
        val vm = newViewModel(repo)
        vm.onNext()
        vm.onNext()
        vm.onNext()
        assertEquals(2, vm.state.value.panelIndex)
        assertTrue(vm.state.value.isCompleted)
        assertEquals(OnboardingStatus.COMPLETED, repo.statusNow())
    }

    @Test
    fun `skip from every panel completes with identical resulting state`() = runTest {
        for (startPanel in 0..2) {
            val repo = FakeOnboardingRepository()
            val vm = newViewModel(repo)
            repeat(startPanel) { vm.onNext() }
            assertEquals(startPanel, vm.state.value.panelIndex)

            vm.onSkip()

            assertTrue(vm.state.value.isCompleted)
            assertEquals(OnboardingStatus.COMPLETED, repo.statusNow())
        }
    }

    @Test
    fun `completing twice is idempotent`() = runTest {
        val repo = FakeOnboardingRepository()
        val vm = newViewModel(repo)
        vm.onFinish()
        vm.onFinish()
        assertTrue(vm.state.value.isCompleted)
        assertEquals(OnboardingStatus.COMPLETED, repo.statusNow())
    }

    @Test
    fun `interrupted onboarding restarts rather than resuming a stuck panel`() = runTest {
        // FR-025: advance partway, but never complete — onboarding_completed stays absent.
        val repo = FakeOnboardingRepository()
        val abandoned = newViewModel(repo)
        abandoned.onNext()
        assertEquals(1, abandoned.state.value.panelIndex)
        assertFalse(abandoned.state.value.isCompleted)

        // A freshly constructed ViewModel (as a real re-launch would create) starts at panel 0,
        // and the synchronous status read still reports NOT_COMPLETED.
        val relaunched = newViewModel(repo)
        assertEquals(0, relaunched.state.value.panelIndex)
        assertEquals(OnboardingStatus.NOT_COMPLETED, GetOnboardingStatusNowUseCase(repo)())
    }
}
