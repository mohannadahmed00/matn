package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository
import com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase
import com.giraffe.matn.domain.usecase.SetThemeModeUseCase
import com.giraffe.matn.permission.FakeNotificationPermission
import com.giraffe.matn.presentation.settings.RemovalTarget
import com.giraffe.matn.presentation.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    private val starter = MatnStorageEntry(matnId = "starter-id", title = "الأجرومية", bytes = 300L)
    private val big = MatnStorageEntry(matnId = "big-id", title = "متن كبير", bytes = 5_000L)
    private val small = MatnStorageEntry(matnId = "small-id", title = "متن صغير", bytes = 1_000L)

    private fun usageOf(entries: List<MatnStorageEntry>, freeBytes: Long = 999_000L): StorageUsage = StorageUsage(
        entries = entries.sortedByDescending { it.bytes },
        totalUsedBytes = entries.sumOf { it.bytes },
        freeSpaceBytes = freeBytes,
    )

    private fun newViewModel(
        usageFlow: Flow<StorageUsage>,
        removeMatnContent: UseCase<String, RemovalOutcome> = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(0)) },
        removeAllContent: UseCase<Unit, List<RemovalOutcome>> = FakeRemoveAll { Resource.Success(emptyList()) },
        appearanceRepository: AppearancePreferencesRepository = FakeAppearancePreferencesRepository(),
        readingPreferences: ReadingPreferencesRepository = FakeReadingPreferencesRepository(),
    ): SettingsViewModel = SettingsViewModel(
        observeStorageUsage = object : FlowUseCase<Unit, StorageUsage> {
            override fun invoke(params: Unit): Flow<StorageUsage> = usageFlow
        },
        removeMatnContent = removeMatnContent,
        removeAllContent = removeAllContent,
        observeThemeMode = ObserveThemeModeUseCase(appearanceRepository),
        setThemeMode = SetThemeModeUseCase(appearanceRepository),
        notificationPermission = FakeNotificationPermission(),
        getFontSize = com.giraffe.matn.domain.usecase.GetFontSizeUseCase(readingPreferences),
        setFontSize = com.giraffe.matn.domain.usecase.SetFontSizeUseCase(readingPreferences),
    )

    /** In-memory [ReadingPreferencesRepository] — the font-size preference Settings now surfaces. */
    private class FakeReadingPreferencesRepository(
        initial: ReadingFontSize = ReadingFontSize.DEFAULT,
    ) : ReadingPreferencesRepository {
        private val state = MutableStateFlow(initial)
        override fun observeFontSize(): Flow<ReadingFontSize> = state
        override suspend fun setFontSize(size: ReadingFontSize): Resource<Unit> {
            state.value = size
            return Resource.Success(Unit)
        }
    }

    @Test
    fun `font size is observed into state and persisted on select`() = runTest {
        val prefs = FakeReadingPreferencesRepository()
        val vm = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(big))),
            readingPreferences = prefs,
        )
        assertEquals(ReadingFontSize.MEDIUM, vm.state.value.fontSize)

        vm.onFontSizeSelected(ReadingFontSize.XLARGE)

        assertEquals(ReadingFontSize.XLARGE, vm.state.value.fontSize)
        assertEquals(ReadingFontSize.XLARGE, prefs.observeFontSize().first())
    }

    @Test
    fun `loading clears once the first emission arrives and populates the breakdown`() = runTest {
        val vm = newViewModel(usageFlow = MutableStateFlow(usageOf(listOf(starter, big, small))))
        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(big.matnId, small.matnId, starter.matnId), vm.state.value.entries.map { it.matnId })
    }

    @Test
    fun `zero state is driven by total used bytes`() = runTest {
        // Phase 13: the old split (`onDemandUsedBytes` vs `totalUsedBytes`) existed only to discount
        // the bundled starter, which never counted as something the student had downloaded. With no
        // matn exempt (FR-031) the two figures are the same, so the zero state keys off the total —
        // and a library with any downloaded matn in it is no longer "empty".
        val empty = newViewModel(usageFlow = MutableStateFlow(usageOf(emptyList())))
        assertTrue(empty.state.value.isOnDemandEmpty)
        assertEquals(0L, empty.state.value.totalUsedBytes)

        val populated = newViewModel(usageFlow = MutableStateFlow(usageOf(listOf(starter))))
        assertFalse(populated.state.value.isOnDemandEmpty)
        assertEquals(300L, populated.state.value.totalUsedBytes)
    }

    @Test
    fun `every row is removable`() = runTest {
        // Phase 13 replaces the old "starter row is non-removable" test. FR-031 forbids any item
        // being exempt, and SC-009 requires "remove all" to leave 0 bytes — which a non-removable
        // row would make impossible. Removal must now succeed for every entry, including the
        // smallest one that used to be the bundled starter.
        val vm = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter))),
            removeMatnContent = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(starter.bytes)) },
        )
        vm.onRemoveMatn(starter.matnId)
        assertEquals(
            RemovalTarget.SingleMatn(starter.matnId, starter.title, starter.bytes),
            vm.state.value.pendingRemoval,
        )
        vm.onConfirmRemoval()
        assertNull(vm.state.value.pendingRemoval)
        assertEquals(RemovalOutcome.Reclaimed(starter.bytes), vm.state.value.lastOutcome)
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
    fun `remove all spares nothing`() = runTest {
        // FR-031 / SC-009: the inverse of Phase 8's `remove all spares the starter`. Every entry is
        // a removal target, the confirmation totals ALL of them, and afterwards zero bytes remain.
        var removeAllInvoked = false
        val usage = MutableStateFlow(usageOf(listOf(starter, big, small)))
        val vm = newViewModel(
            usageFlow = usage,
            removeAllContent = FakeRemoveAll {
                removeAllInvoked = true
                Resource.Success(
                    listOf(
                        RemovalOutcome.Reclaimed(5_000L),
                        RemovalOutcome.Reclaimed(1_000L),
                        RemovalOutcome.Reclaimed(300L),
                    ),
                )
            },
        )
        vm.onRemoveAll()
        // 6_300, not 6_000 — the row that used to be exempt is now included in the total.
        assertEquals(RemovalTarget.AllContent(6_300L), vm.state.value.pendingRemoval)
        vm.onConfirmRemoval()
        assertTrue(removeAllInvoked)
        assertNull(vm.state.value.pendingRemoval)

        usage.value = usageOf(emptyList())
        assertTrue(vm.state.value.entries.isEmpty(), "remove all must spare nothing (FR-031)")
        assertEquals(0L, vm.state.value.totalUsedBytes)
        assertTrue(vm.state.value.isOnDemandEmpty)
    }

    @Test
    fun `the removal outcome reaches lastOutcome unchanged`() = runTest {
        // Phase 13 collapsed RemovalOutcome to one value: deleting app-private files reclaims
        // immediately on every platform, so the iOS "released, pending system reclaim" hedge is
        // gone and the figure shown is always the truth.
        val vmReclaimed = newViewModel(
            usageFlow = MutableStateFlow(usageOf(listOf(starter, big))),
            removeMatnContent = FakeRemove { Resource.Success(RemovalOutcome.Reclaimed(5_000L)) },
        )
        vmReclaimed.onRemoveMatn(big.matnId)
        vmReclaimed.onConfirmRemoval()
        assertEquals(RemovalOutcome.Reclaimed(5_000L), vmReclaimed.state.value.lastOutcome)
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
