package dev.dimension.flare.ui.presenter

import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.model.SettingsExport
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.GroupTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePostKind
import dev.dimension.flare.data.model.tab.TimelinePresentation
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.datastore.SettingsRepository
import dev.dimension.flare.data.repository.homeTimelineTab
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
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
    private val timelineResolver = TimelineResolver(defaultSocialPlatformRegistry)
    private lateinit var root: Path
    private lateinit var appDataStore: AppDataStore
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
        appDataStore = AppDataStore(pathProducer)
        settingsRepository = SettingsRepository(appDataStore)
        startKoin {
            modules(
                module {
                    single { appDataStore }
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
                buildJsonObject {
                    put(
                        "appearanceSettings",
                        buildJsonObject {
                            put("theme", JsonPrimitive("DARK"))
                            put("showNumbers", JsonPrimitive(false))
                        },
                    )
                    put(
                        "appSettings",
                        json.encodeToJsonElement(AppSettings.serializer(), AppSettings(version = "legacy-appearance")),
                    )
                    put(
                        "tabSettingsV2",
                        json.encodeToJsonElement(TabSettingsV2.serializer(), TabSettingsV2(homeSlots = listOf(newSlot))),
                    )
                }.toString()

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
    fun homeTimelineTabResolvesNestedItemsByIdAndEmitsConfigUpdates() =
        runTest {
            val initialChild = homeSlot()
            val initialGroup =
                TimelineSlot(
                    id = "manual_group",
                    content =
                        TimelineSlotContent.Group(
                            children =
                                listOf(
                                    initialChild.copy(
                                        presentation =
                                            TimelinePresentation(
                                                filterConfig =
                                                    TimelineFilterConfig(
                                                        excludedKinds = listOf(TimelinePostKind.Reply),
                                                    ),
                                            ),
                                    ),
                                ),
                            source = GroupSource.Manual,
                            mergePolicy = TimelineMergePolicy.Staggered,
                        ),
                    presentation =
                        TimelinePresentation(
                            filterConfig =
                                TimelineFilterConfig(
                                    excludedKinds = listOf(TimelinePostKind.Quote),
                                ),
                        ),
                )
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(initialGroup))
            }

            val groupItem = settingsRepository.homeTimelineTab(initialGroup.id, timelineResolver).first() as? GroupTimelineTabItemV2
            val childItem = settingsRepository.homeTimelineTab(initialChild.id, timelineResolver).first()
            assertEquals(TimelineMergePolicy.Staggered, groupItem?.mergePolicy)
            assertEquals(listOf(TimelinePostKind.Quote), groupItem?.filterConfig?.excludedKinds)
            assertEquals(listOf(TimelinePostKind.Reply), childItem?.filterConfig?.excludedKinds)

            val updatedGroup =
                initialGroup.copy(
                    content =
                        TimelineSlotContent.Group(
                            children =
                                listOf(
                                    initialChild.copy(
                                        presentation =
                                            TimelinePresentation(
                                                filterConfig =
                                                    TimelineFilterConfig(
                                                        excludedKinds = listOf(TimelinePostKind.Repost),
                                                    ),
                                            ),
                                    ),
                                ),
                            source = GroupSource.Manual,
                            mergePolicy = TimelineMergePolicy.TimePerPage,
                        ),
                    presentation =
                        TimelinePresentation(
                            filterConfig =
                                TimelineFilterConfig(
                                    excludedKinds = listOf(TimelinePostKind.Original),
                                ),
                        ),
                )
            val nextGroup =
                async {
                    settingsRepository.homeTimelineTab(initialGroup.id, timelineResolver).drop(1).first() as? GroupTimelineTabItemV2
                }
            val nextChild = async { settingsRepository.homeTimelineTab(initialChild.id, timelineResolver).drop(1).first() }

            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(updatedGroup))
            }

            val updatedGroupItem = nextGroup.await()
            val updatedChildItem = nextChild.await()
            assertEquals(TimelineMergePolicy.TimePerPage, updatedGroupItem?.mergePolicy)
            assertEquals(listOf(TimelinePostKind.Original), updatedGroupItem?.filterConfig?.excludedKinds)
            assertEquals(listOf(TimelinePostKind.Repost), updatedChildItem?.filterConfig?.excludedKinds)
        }

    private fun homeSlot() =
        CommonTimelineSpecs
            .home
            .target(TimelineSpec.AccountBasedData(MicroBlogKey(id = "home", host = "example.com")))
            .toSlot()

    private fun localSlot() =
        MastodonPlatformSpec
            .localTimelineSpec
            .target(TimelineSpec.AccountBasedData(MicroBlogKey(id = "local", host = "example.com")))
            .toSlot()
}
