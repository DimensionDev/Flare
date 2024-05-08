package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class MisskeyInstance(
    val date: String,
    val stats: MisskeyInstanceStats,
    val instancesInfos: List<InstancesInfo>,
)

@Serializable
internal data class InstancesInfo(
    val url: String,
//    val value: Double,
    val meta: Meta,
//    val nodeinfo: Nodeinfo,
    val stats: InstancesInfoStats,
//    val npd15: Double,
    val name: String,
    val description: String? = null,
//    val langs: List<String>,
//    val isAlive: Boolean,
//    val repo: String,
//    val icon: Boolean,
//    val banner: Boolean,
//    val background: Boolean,
)

@Serializable
internal data class Meta(
//    val maintainerName: String? = null,
//    val maintainerEmail: String? = null,
//    val version: String,
//    val name: String? = null,
//    val shortName: String? = null,
//    val uri: String,
//    val description: String? = null,
//    val langs: List<String>,
//    @SerialName("tosUrl")
//    val tosURL: String? = null,
//    @SerialName("repositoryUrl")
//    val repositoryURL: String? = null,
//    @SerialName("feedbackUrl")
//    val feedbackURL: String? = null,
//    @SerialName("impressumUrl")
//    val impressumURL: String? = null,
//    @SerialName("privacyPolicyUrl")
//    val privacyPolicyURL: String? = null,
//    val disableRegistration: Boolean,
//    val emailRequiredForSignup: Boolean? = null,
//    val enableHcaptcha: Boolean? = null,
//    val hcaptchaSiteKey: String? = null,
//    val enableRecaptcha: Boolean? = null,
//    val recaptchaSiteKey: String? = null,
//    val enableTurnstile: Boolean? = null,
//    val turnstileSiteKey: String? = null,
//    val swPublickey: String? = null,
//    val themeColor: String? = null,
//    @SerialName("mascotImageUrl")
//    val mascotImageURL: String? = null,
    @SerialName("bannerUrl")
    val bannerURL: String? = null,
//    @SerialName("infoImageUrl")
//    val infoImageURL: String? = null,
//    @SerialName("serverErrorImageUrl")
//    val serverErrorImageURL: String? = null,
//    @SerialName("notFoundImageUrl")
//    val notFoundImageURL: String? = null,
    @SerialName("iconUrl")
    val iconURL: String? = null,
//    @SerialName("backgroundImageUrl")
//    val backgroundImageURL: String? = null,
//    @SerialName("logoImageUrl")
//    val logoImageURL: String? = null,
//    val maxNoteTextLength: Long,
//    val defaultLightTheme: String? = null,
//    val defaultDarkTheme: String? = null,
//    val ads: List<Ad>? = null,
//    val notesPerOneAd: Long? = null,
//    val enableEmail: Boolean,
//    val enableServiceWorker: Boolean,
//    val translatorAvailable: Boolean? = null,
//    val serverRules: List<String>? = null,
//    val policies: Policies? = null,
//    val mediaProxy: String? = null,
//    val cacheRemoteFiles: Boolean? = null,
//    val cacheRemoteSensitiveFiles: Boolean? = null,
//    val requireSetup: Boolean? = null,
//    val proxyAccountName: String? = null,
//    val features: Map<String, Boolean>,
//    @SerialName("transactionsActNotationUrl")
//    val transactionsActNotationURL: String? = null,
//    val sellSubscription: Boolean? = null,
//    @SerialName("basicPlanRoleId")
//    val basicPlanRoleID: String? = null,
//    val basicPlanPrice: Long? = null,
//    val enableRegistrationLimit: Boolean? = null,
//    val registrationLimit: Long? = null,
//    val registrationLimitCooldown: Long? = null,
//    val enablePatreonIntegration: Boolean? = null,
//    val enableFanboxIntegration: Boolean? = null,
//    val enableSentryLogging: Boolean? = null,
//    @SerialName("sentryDsn")
//    val sentryDSN: JsonElement? = null,
//    val enableSupporterPage: Boolean? = null,
//    val disableExploreLocalUsers: Boolean? = null,
//    val disableEntranceFeatureTimeline: Boolean? = null,
//    val enableAgeRestriction: Boolean? = null,
//    val ageRestrictionThreshold: Long? = null,
//    val relationalDate: String? = null,
//    val mediaProxyKey: String? = null,
//    @SerialName("pinnedLtlChannelIds")
//    val pinnedLTLChannelIDS: List<String>? = null,
//    val disableInvitation: Boolean? = null,
//    val disableTrends: Boolean? = null,
//    @SerialName("errorImageUrl")
//    val errorImageURL: String? = null,
//    val pinnedPages: List<String>? = null,
//    @SerialName("pinnedClipId")
//    val pinnedClipID: JsonElement? = null,
//    val disableLocalTimeline: Boolean? = null,
//    val disableGlobalTimeline: Boolean? = null,
//    @SerialName("driveCapacityPerLocalUserMb")
//    val driveCapacityPerLocalUserMB: Float? = null,
//    @SerialName("driveCapacityPerRemoteUserMb")
//    val driveCapacityPerRemoteUserMB: Float? = null,
//    val enableTwitterIntegration: Boolean? = null,
//    val enableGithubIntegration: Boolean? = null,
//    val enableDiscordIntegration: Boolean? = null,
//    val secure: Boolean? = null,
//    val proxyRemoteFiles: Boolean? = null,
//    @SerialName("ToSUrl")
//    val toSURL: String? = null,
//    val machine: String? = null,
//    val os: String? = null,
//    val node: String? = null,
//    val psql: String? = null,
//    val redis: String? = null,
//    val cpu: CPU? = null,
//    val authorizedPublicTimeline: Boolean? = null,
//    val authorizedProfileDirectory: Boolean? = null,
//    val disableProfileDirectory: Boolean? = null,
//    val enableEmojiReaction: Boolean? = null,
//    val maintainer: Maintainer? = null,
//    val arch: String? = null,
//    val showReplayInPublicTimeline: Boolean? = null,
)

@Serializable
internal data class Ad(
    val id: String,
    val url: String,
    val place: String,
    val ratio: Long,
    @SerialName("imageUrl")
    val imageURL: String,
    val dayOfWeek: Long? = null,
    val forceShowAds: Boolean? = null,
)

@Serializable
internal data class CPU(
    val model: String,
    val cores: Long,
)

@Serializable
internal data class Maintainer(
    val email: String? = null,
    val name: String? = null,
)

@Serializable
internal data class Policies(
    val gtlAvailable: Boolean,
    val ltlAvailable: Boolean,
    val canPublicNote: Boolean,
    val canCreateContent: Boolean? = null,
    val canUpdateContent: Boolean? = null,
    val canDeleteContent: Boolean? = null,
    val canInvite: Boolean,
    val inviteLimit: Long? = null,
    val inviteLimitCycle: Long? = null,
    val inviteExpirationTime: Long? = null,
    val canManageCustomEmojis: Boolean,
    val canManageAvatarDecorations: Boolean? = null,
    val canSearchNotes: Boolean? = null,
    val canUseTranslator: Boolean? = null,
    val canHideAds: Boolean,
    @SerialName("driveCapacityMb")
    val driveCapacityMB: Float,
    val alwaysMarkNsfw: Boolean? = null,
//    val pinLimit: Long,
//    val antennaLimit: Long,
//    val wordMuteLimit: Double,
//    val webhookLimit: Long,
//    val clipLimit: Long,
//    val noteEachClipsLimit: Long,
//    val userListLimit: Long,
//    val userEachUserListsLimit: Double,
//    val rateLimitFactor: Double,
    val canEditNote: Boolean? = null,
    val canCreatePrivateChannel: Boolean? = null,
    val canAccountDelete: Boolean? = null,
    val canScheduleNote: Boolean? = null,
    val canRequestCustomEmojis: Boolean? = null,
    val canAddRoles: Boolean? = null,
    @SerialName("driveAdditionCapacityMb")
    val driveAdditionCapacityMB: Float? = null,
    val noteLengthLimit: Long? = null,
    @SerialName("additionalDriveCapacityMb")
    val additionalDriveCapacityMB: Float? = null,
    val canCreateVoiceChannel: Boolean? = null,
)

@Serializable
internal data class Nodeinfo(
    val version: String,
    val software: Software,
    val protocols: List<String>,
    val services: Services,
    val openRegistrations: Boolean,
    val usage: Usage,
    val metadata: Metadata,
    val mulukhiya: Mulukhiya? = null,
)

@Serializable
internal data class Metadata(
    val nodeName: String? = null,
    val nodeDescription: String? = null,
    val maintainer: Maintainer,
    val langs: List<String>,
    @SerialName("tosUrl")
    val tosURL: String? = null,
    @SerialName("privacyPolicyUrl")
    val privacyPolicyURL: String? = null,
    @SerialName("impressumUrl")
    val impressumURL: String? = null,
    @SerialName("repositoryUrl")
    val repositoryURL: String? = null,
    @SerialName("feedbackUrl")
    val feedbackURL: String? = null,
    val disableRegistration: Boolean,
    val disableLocalTimeline: Boolean? = null,
    val disableGlobalTimeline: Boolean,
    val emailRequiredForSignup: Boolean? = null,
    val enableHcaptcha: Boolean? = null,
    val enableRecaptcha: Boolean,
    val maxNoteTextLength: Long,
    val enableEmail: Boolean,
    val enableServiceWorker: Boolean,
    val proxyAccountName: String? = null,
    val themeColor: String? = null,
    val nodeAdmins: List<Maintainer>? = null,
    val disableInvitation: Boolean? = null,
    val enableTwitterIntegration: Boolean? = null,
    val enableGithubIntegration: Boolean? = null,
    val enableDiscordIntegration: Boolean? = null,
    @SerialName("ToSUrl")
    val toSURL: String? = null,
    val announcements: List<Announcement>? = null,
    val name: String? = null,
    val description: String? = null,
    val relayActor: JsonElement? = null,
    val relays: JsonArray? = null,
)

@Serializable
internal data class Announcement(
    val text: String,
    val image: JsonElement? = null,
    val title: String,
)

@Serializable
internal data class Mulukhiya(
    @SerialName("package")
    val mulukhiyaPackage: Package,
    val config: Config,
)

@Serializable
internal data class Config(
    val controller: String,
    val status: Status,
)

@Serializable
internal data class Status(
    val spoiler: Spoiler,
    @SerialName("default_hashtag")
    val defaultHashtag: String,
)

@Serializable
internal data class Spoiler(
    val text: JsonElement? = null,
    val emoji: String,
    val shortcode: String,
)

@Serializable
internal data class Package(
    val authors: List<String>,
    val description: String,
    val email: List<String>,
    val license: String,
    val url: String,
    val version: String,
)

@Serializable
internal data class Services(
    val inbound: JsonArray,
    val outbound: List<String>,
)

@Serializable
internal data class Software(
    val name: String,
    val version: String,
    val homepage: String? = null,
    val repository: String? = null,
)

@Serializable
internal data class Usage(
    val users: Users,
    val localPosts: Long? = null,
    val localComments: Long? = null,
)

@Serializable
internal data class Users(
    val total: Long? = null,
    val activeHalfyear: Long? = null,
    val activeMonth: Long? = null,
)

@Serializable
internal data class InstancesInfoStats(
//    val notesCount: Long,
//    val originalNotesCount: Long,
    val usersCount: Long,
//    val originalUsersCount: Long,
//    val reactionsCount: Long? = null,
//    val instances: Long,
//    val driveUsageLocal: Long,
//    val driveUsageRemote: Long,
)

@Serializable
internal data class MisskeyInstanceStats(
    val notesCount: Long,
    val usersCount: Long,
    val mau: Long,
    val instancesCount: Long,
)
