package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

internal fun Status.toUi(accountKey: MicroBlogKey): UiStatus.VVO {
    val media =
        pics.orEmpty().mapNotNull {
            val url = it.large?.url ?: it.url
            if (url.isNullOrEmpty()) {
                null
            } else {
                UiMedia.Image(
                    url = url,
                    width = it.large?.geo?.widthValue ?: it.geo?.widthValue ?: 0f,
                    height = it.large?.geo?.heightValue ?: it.geo?.heightValue ?: 0f,
                    previewUrl = it.url ?: url,
                    description = null,
                    sensitive = false,
                )
            }
        } +
            listOfNotNull(
                pageInfo?.takeIf {
                    it.type == "video"
                }?.let {
                    val description = it.title ?: it.content2 ?: it.content1
                    val height = it.pagePic?.height?.toFloatOrNull() ?: 0f
                    val width = it.pagePic?.width?.toFloatOrNull() ?: 0f
                    val previewUrl = it.pagePic?.url
                    val videoUrl =
                        it.urls?.mp4720PMp4
                            ?: it.urls?.mp4HDMp4
                            ?: it.urls?.mp4LdMp4
                            ?: it.mediaInfo?.streamURLHD
                            ?: it.mediaInfo?.streamURL
                    if (videoUrl != null && previewUrl != null) {
                        UiMedia.Video(
                            url = videoUrl,
                            thumbnailUrl = previewUrl,
                            width = width,
                            height = height,
                            description = description,
                        )
                    } else {
                        null
                    }
                },
            )
    return UiStatus.VVO(
        statusKey =
            MicroBlogKey(
                id = id,
                host = vvoHost,
            ),
        accountKey = accountKey,
        content = text.orEmpty(),
        rawContent = rawText.orEmpty(),
        matrices =
            UiStatus.VVO.Matrices(
                commentCount = commentsCount ?: 0,
                repostCount = repostsCount ?: 0,
                likeCount = attitudesCount ?: 0,
            ),
        liked = favorited ?: false,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        rawUser = user?.toUi(accountKey),
        regionName = regionName,
        source = source,
        media = media.toImmutableList(),
        quote = retweetedStatus?.toUi(accountKey),
    )
}

internal fun User.toUi(accountKey: MicroBlogKey): UiUser.VVO {
    return UiUser.VVO(
        userKey =
            MicroBlogKey(
                id = id.toString(),
                host = vvoHost,
            ),
        avatarUrl = avatarHD.orEmpty(),
        bannerUrl = profileImageURL,
        rawHandle = screenName,
        rawDescription = description,
        verified = verified ?: false,
        verifiedReason = verifiedReason,
        accountKey = accountKey,
        matrices =
            UiUser.VVO.Matrices(
                followsCount = followCount ?: 0,
                fansCount = followersCountStr.orEmpty(),
                statusesCount = statusesCount ?: 0,
            ),
        relation =
            UiRelation.VVO(
                following = following ?: false,
                isFans = followMe ?: false,
            ),
    )
}