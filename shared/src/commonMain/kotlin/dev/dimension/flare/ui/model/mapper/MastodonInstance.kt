package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

public fun InstanceData.render(): UiInstanceMetadata {
    val configuration =
        UiInstanceMetadata.Configuration(
            registration =
                UiInstanceMetadata.Configuration.Registration(
                    enabled = registrations?.enabled == true,
                ),
            statuses =
                UiInstanceMetadata.Configuration.Statuses(
                    maxCharacters = this.configuration?.statuses?.maxCharacters ?: 500,
                    maxMediaAttachments = this.configuration?.statuses?.maxMediaAttachments ?: 4,
                ),
            mediaAttachment =
                UiInstanceMetadata.Configuration.MediaAttachment(
                    imageSizeLimit = this.configuration?.mediaAttachments?.imageSizeLimit ?: -1,
                    descriptionLimit = this.configuration?.mediaAttachments?.descriptionLimit ?: 1500,
                    supportedMimeTypes =
                        this.configuration
                            ?.mediaAttachments
                            ?.supportedMIMETypes
                            .orEmpty()
                            .toImmutableList(),
                ),
            poll =
                UiInstanceMetadata.Configuration.Poll(
                    maxOptions = this.configuration?.polls?.maxOptions ?: 4,
                    maxCharactersPerOption = this.configuration?.polls?.maxCharactersPerOption ?: 50,
                    minExpiration = this.configuration?.polls?.minExpiration ?: 300,
                    maxExpiration = this.configuration?.polls?.maxExpiration ?: 2592000,
                ),
        )

    val rules =
        rules
            .orEmpty()
            .associate { rule ->
                (rule.text ?: "") to (rule.hint ?: "")
            }.toImmutableMap()

    return UiInstanceMetadata(
        instance =
            UiInstance(
                name = title ?: "Unknown",
                description = description,
                iconUrl = icon?.lastOrNull()?.src,
                domain = domain ?: "Unknown",
                type = PlatformType.Mastodon,
                bannerUrl = thumbnail?.url,
                usersCount = usage?.users?.activeMonth ?: 0,
            ),
        rules = rules,
        configuration = configuration,
    )
}
