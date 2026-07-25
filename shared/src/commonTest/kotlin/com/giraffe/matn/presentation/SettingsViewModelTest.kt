package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository
import com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase
import com.giraffe.matn.domain.usecase.SetThemeModeUseCase
import com.giraffe.matn.presentation.settings.RemovalTarget
import com.giraffe.matn.presentation.settings.SettingsViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T068 — [SettingsViewModel] (storage-ui-contract.md §6): loading → populated; the zero state
 * driven by `onDemandUsedBytes`; ordering; starter row non-removable; removal updates the total
 * without a restart; "remove all" spares the starter; both removal outcomes reach `lastOutcome`.
 */
class SettingsViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private val starter = MatnStorageEntry(matnId = "starter-id", title = "الأجرومية", bytes = 300L, isStarter = true)
    private val big = MatnStorageEntry(matnId = "big-id", title = "متن كبير", bytes = 5_000L, isStarter = false)
    private val small = MatnStorageEntry(matnId = "small-id", title = "متن صغير", bytes = 1_000L, isStarter = false)

    private fun usageOf(entries: List<MatnStorageEntry>, freeBytes: Long = 999_000L): StorageUsage = StorageUsage(
        entries = entries.sortedByDescending { it.bytes },
        totalUsedBytes = entries.sumOf { it.bytes },
        onDemandUsedBytes = entries.filter { !it.isStarter }.sumOf { it.bytes },
        freeSpaceBytes = freeBytes,
    )

    private fun newViewModel(
        usageFlow: Flow<StorageUsage>,
        removeMatnContent: UseCase<String, RemovalOutcome> = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(0)) },
        removeAllContent: UseCase<Unit, List<RemovalOutcome>> = FakeRemoveAll { Resource.Success(emptyList()) },
        appearanceRepository: AppearancePreferencesRepository = FakeAppearancePreferencesRepository(),
    ): SettingsViewModel = SettingsViewModel(
        observeStorageUsage = object : FlowUseCase<Unit, StorageUsage> {
            override fun invoke(params: Unit): Flow<StorageUsage> = usageFlow
        },
        removeMatnContent = removeMatnContent,
        removeAllContent = removeAllContent,
        observeThemeMode = ObserveThemeModeUseCase(appearanceRepository),
        setThemeMode = SetThemeModeUseCase(appearanceRepository),
    )

    @Test
    fun `loading clears once the first emission arrives and populates the breakdown`() = runTest {
        val vm = newViewModel(usageFlow = MutableStateFlow(usageOf(listOf(starter, big, small))))
        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(big.matnId, small.matnId, starter.matnId), vm.state.value.entries.map { it.matnId })
    }

    @Test
    fun `zero state is driven by onDemandUsedBytes not the starter's presence`() = runTest {
        val vm = newViewModel(usageFlow = MutableStateFlow(usageOf(listOf(starter))))
        assertTrue(vm.state.value.isOnDemandEmpty)
        assertEquals(300L, vm.state.value.totalUsedBytes)

        val vmPopulated = newViewModel(usageFlow = MutableStateFlow(usageOf(listOf(starter, big))))
        assertFalse(vmPopulated.state.value.isOnDemandEmpty)
    }

    @Test
    fun `starter row non-removable is enforced by the use case even if targeted`() = runTest {
        // StorageUsageRow never wires a remove action for the starter (isStarter branch renders
        // "part of the app" with no button); this proves the ViewModel doesn't independently
        // second-guess that omission — it defers entirely to RemoveMatnContentUseCase's own
        // starter refusal (single source of truth, already covered by
        // RemoveMatnContentUseCaseTest's "removing the starter matn is refused").
        val vm = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter))),
            removeMatnContent = FakeRemove {
                Resource.Failure(com.giraffe.matn.domain.error.DeliveryError.StarterMatnNotRemovable)
            },
        )
        vm.onRemoveMatn(starter.matnId)
        assertEquals(RemovalTarget.SingleMatn(starter.matnId, starter.title, starter.bytes), vm.state.value.pendingRemoval)
        vm.onConfirmRemoval()
        assertNull(vm.state.value.pendingRemoval)
        assertNull(vm.state.value.lastOutcome, "a refused removal must never set a successful outcome")
    }

    @Test
    fun `removal never fires without confirmation`() = runTest {
        var removeInvoked = false
        val vm = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter, big))),
            removeMatnContent = FakeRemove { removeInvoked = true; Resource.Success(RemovalOutcome.Reclaimed(0)) },
        )
        vm.onConfirmRemoval()
        assertFalse(removeInvoked)
        assertNull(vm.state.value.pendingRemoval)
    }

    @Test
    fun `removing a matn updates the total without a restart`() = runTest {
        val usage = MutableStateFlow(usageOf(listOf(starter, big, small)))
        val vm = newViewModel(
            usageFlow = usage,
            removeMatnContent = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(5_000L)) },
        )
        vm.onRemoveMatn(big.matnId)
        assertEquals(RemovalTarget.SingleMatn(big.matnId, big.title, big.bytes), vm.state.value.pendingRemoval)

        vm.onConfirmRemoval()
        assertNull(vm.state.value.pendingRemoval)
        assertEquals(RemovalOutcome.Reclaimed(5_000L), vm.state.value.lastOutcome)

        // Simulate the repository's re-emission after the removal — no ViewModel restart needed.
        usage.value = usageOf(listOf(starter, small))
        assertEquals(listOf(small.matnId, starter.matnId), vm.state.value.entries.map { it.matnId })
    }

    @Test
    fun `remove all spares the starter`() = runTest {
        var removeAllInvoked = false
        val usage = MutableStateFlow(usageOf(listOf(starter, big, small)))
        val vm = newViewModel(
            usageFlow = usage,
            removeAllContent = FakeRemoveAll {
                removeAllInvoked = true
                Resource.Success(listOf(RemovalOutcome.Reclaimed(5_000L), RemovalOutcome.Reclaimed(1_000L)))
            },
        )
        vm.onRemoveAll()
        assertEquals(RemovalTarget.AllContent(6_000L), vm.state.value.pendingRemoval)
        vm.onConfirmRemoval()
        assertTrue(removeAllInvoked)
        assertNull(vm.state.value.pendingRemoval)

        usage.value = usageOf(listOf(starter))
        assertEquals(listOf(starter.matnId), vm.state.value.entries.map { it.matnId })
        assertTrue(vm.state.value.isOnDemandEmpty)
    }

    @Test
    fun `both removal outcomes reach lastOutcome unchanged`() = runTest {
        val vmReclaimed = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter, big))),
            removeMatnContent = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(5_000L)) },
        )
        vmReclaimed.onRemoveMatn(big.matnId)
        vmReclaimed.onConfirmRemoval()
        assertEquals(RemovalOutcome.Reclaimed(5_000L), vmReclaimed.state.value.lastOutcome)

        val vmPending = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter, big))),
            removeMatnContent = FakeRemove { Resource.Success(RemovalOutcome.ReleasedPendingSystemReclaim(5_000L)) },
        )
        vmPending.onRemoveMatn(big.matnId)
        vmPending.onConfirmRemoval()
        assertEquals(RemovalOutcome.ReleasedPendingSystemReclaim(5_000L), vmPending.state.value.lastOutcome)
    }

    private class FakeRemove(private val block: suspend (String) -> Resource<RemovalOutcome>) : UseCase<String, RemovalOutcome> {
        override suspend fun invoke(params: String): Resource<RemovalOutcome> = block(params)
    }

    private class FakeRemoveAll(private val block: suspend (Unit) -> Resource<List<RemovalOutcome>>) : UseCase<Unit, List<RemovalOutcome>> {
        override suspend fun invoke(params: Unit): Resource<List<RemovalOutcome>> = block(params)
    }

    /** T053 (US1): minimal in-memory fake — a single mutable flow, no persistence semantics needed. */
    private class FakeAppearancePreferencesRepository(
        initial: ThemeMode = ThemeMode.SYSTEM,
    ) : AppearancePreferencesRepository {
        private val mode = MutableStateFlow(initial)
        override fun observeThemeMode(): Flow<ThemeMode> = mode
        override fun themeModeNow(): ThemeMode = mode.value
        override suspend fun setThemeMode(mode: ThemeMode): Resource<Unit> {
            this.mode.value = mode
            return Resource.Success(Unit)
        }
    }

    @Test
    fun `selecting a theme mode updates state and persists through the use case`() = runTest {
        val repo = FakeAppearancePreferencesRepository(initial = ThemeMode.SYSTEM)
        val vm = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter))),
            appearanceRepository = repo,
        )
        assertEquals(ThemeMode.SYSTEM, vm.state.value.themeMode)

        vm.onThemeModeSelected(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        assertEquals(ThemeMode.DARK, repo.themeModeNow())
    }
}
