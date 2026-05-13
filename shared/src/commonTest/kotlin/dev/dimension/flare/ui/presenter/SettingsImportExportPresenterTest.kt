package dev.dimension.flare.ui.presenter

import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.AppearanceSettings
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.LegacyAppearanceSettingsAndTabsExport
import dev.dimension.flare.data.model.LegacyAppearanceSettingsExport
import dev.dimension.flare.data.model.LegacySettingsExport
import dev.dimension.flare.data.model.Mastodon
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.toTimelineSlotOrNull
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.Path
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsImportExportPresenterTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: Path
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val pathProducer =
            object : PlatformPathProducer {
                override fun dataStoreFile(fileName: String): Path = root.resolve(fileName)

                override fun draftMediaFile(
                    groupId: String,
                    fileName: String,
                ): Path = root.resolve(groupId).resolve(fileName)
            }
        settingsRepository =
            SettingsRepository(
                pathProducer = pathProducer,
                appDataStore = AppDataStore(pathProducer),
                timelineResolver = TimelineResolver(),
            )
        startKoin {
            modules(
                module {
                    single { settingsRepository }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun exportUsesTabSettingsV2FieldOnly() =
        runTest {
            val slot = homeSlot()
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(slot))
            }

            val exported = ExportSettingsPresenter().export()
            val root = json.parseToJsonElement(exported).jsonObject

            assertTrue("tabSettingsV2" in root)
            assertFalse("tabSettings" in root)
            assertTrue("appearanceBag" in root)
            assertFalse("appearanceSettings" in root)
            assertEquals(
                listOf(slot.id),
                json
                    .decodeFromString<SettingsExport>(exported)
                    .tabSettingsV2.homeSlots
                    .map { it.id },
            )
        }

    @Test
    fun importV2SettingsReplacesHomeSlots() =
        runTest {
            val oldSlot = localSlot()
            val newSlot = homeSlot()
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(oldSlot))
            }
            val exported =
                json.encodeToString(
                    SettingsExport(
                        appearanceBag = AppearanceBag(),
                        appSettings = AppSettings(version = "v2"),
                        tabSettingsV2 = TabSettingsV2(homeSlots = listOf(newSlot)),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(listOf(newSlot.id), settings.homeSlots.map { it.id })
        }

    @Test
    fun importLegacyAppearanceSettingsConvertsToAppearanceBagInRepository() =
        runTest {
            val newSlot = homeSlot()
            val exported =
                json.encodeToString(
                    LegacyAppearanceSettingsExport(
                        appearanceSettings =
                            AppearanceSettings(
                                theme = Theme.DARK,
                                showNumbers = false,
                            ),
                        appSettings = AppSettings(version = "legacy-appearance"),
                        tabSettingsV2 = TabSettingsV2(homeSlots = listOf(newSlot)),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            assertEquals(Theme.DARK, settingsRepository.globalAppearance.first().theme)
            assertEquals(false, settingsRepository.timelineAppearance.first().showNumbers)
            assertTrue(
                settingsRepository.appearanceBag
                    .first()
                    .entries
                    .isNotEmpty(),
            )
        }

    @Test
    fun importLegacyV1SettingsMigratesTabsToV2WithMixedTimelineDisabled() =
        runTest {
            val accountKey = MicroBlogKey(id = "alice", host = "example.com")
            val exported =
                json.encodeToString(
                    LegacyAppearanceSettingsAndTabsExport(
                        appearanceSettings = AppearanceSettings(),
                        appSettings = AppSettings(version = "v1"),
                        tabSettings =
                            TabSettings(
                                enableMixedTimeline = false,
                                mainTabs =
                                    listOf(
                                        HomeTimelineTabItem(AccountType.Specific(accountKey)),
                                        Mastodon.LocalTimelineTabItem(
                                            account = AccountType.Specific(accountKey),
                                            metaData = localMetaData(),
                                        ),
                                    ),
                            ),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(
                listOf(
                    "common.home:$accountKey",
                    "mastodon.local:$accountKey",
                ),
                settings.homeSlots.map { it.id },
            )
        }

    @Test
    fun importLegacyV1SettingsMigratesTabsToV2WithMixedTimelineEnabled() =
        runTest {
            val accountKey = MicroBlogKey(id = "alice", host = "example.com")
            val exported =
                json.encodeToString(
                    LegacySettingsExport(
                        appearanceBag = AppearanceBag(),
                        appSettings = AppSettings(version = "v1"),
                        tabSettings =
                            TabSettings(
                                enableMixedTimeline = true,
                                mainTabs =
                                    listOf(
                                        HomeTimelineTabItem(AccountType.Specific(accountKey)),
                                        Mastodon.LocalTimelineTabItem(
                                            account = AccountType.Specific(accountKey),
                                            metaData = localMetaData(),
                                        ),
                                    ),
                            ),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(
                listOf(
                    SYSTEM_HOME_MIXED_TIMELINE_ID,
                    "common.home:$accountKey",
                    "mastodon.local:$accountKey",
                ),
                settings.homeSlots.map { it.id },
            )
        }

    private fun homeSlot() =
        HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = "home", host = "example.com")))
            .toTimelineSlotOrNull()
            ?: error("Home slot should be migratable")

    private fun localSlot() =
        Mastodon
            .LocalTimelineTabItem(
                account = AccountType.Specific(MicroBlogKey(id = "local", host = "example.com")),
                metaData = localMetaData(),
            ).toTimelineSlotOrNull()
            ?: error("Local slot should be migratable")

    private fun localMetaData() =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
            icon = IconType.Material(UiIcon.Local),
        )
}
