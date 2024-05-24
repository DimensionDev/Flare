package dev.dimension.flare.data.network.vvo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProfileData(
    val user: User? = null,
    val statuses: List<Status>? = null,
    val more: String? = null,
    val fans: String? = null,
    val follow: String? = null,
    val button: Button? = null,
)

@Serializable
internal data class Button(
    val type: String? = null,
    val name: String? = null,
    @SerialName("sub_type")
    val subType: Long? = null,
    val params: Params? = null,
)

@Serializable
internal data class Params(
    val uid: String? = null,
)

@Serializable
internal data class ContainerInfo(
    val isVideoCoverStyle: Long? = null,
    val isStarStyle: Long? = null,
    val userInfo: User? = null,
    @SerialName("fans_scheme")
    val fansScheme: String? = null,
    @SerialName("follow_scheme")
    val followScheme: String? = null,
    val tabsInfo: TabsInfo? = null,
    @SerialName("profile_ext")
    val profileEXT: String? = null,
    val scheme: String? = null,
    val showAppTips: Long? = null,
    val cardlistInfo: CardlistInfo? = null,
    val cards: List<Card>? = null,
)

@Serializable
internal data class CardlistInfo(
    val containerid: String? = null,
    @SerialName("v_p")
    val vP: Long? = null,
    @SerialName("show_style")
    val showStyle: Long? = null,
    val total: Long? = null,
    val autoLoadMoreIndex: Long? = null,
    @SerialName("since_id")
    val sinceID: Long? = null,
)

@Serializable
internal data class Card(
    @SerialName("card_type")
    val cardType: Long? = null,
    @SerialName("profile_type_id")
    val profileTypeID: String? = null,
    val itemid: String? = null,
    val scheme: String? = null,
    val mblog: Status? = null,
)

@Serializable
internal data class TabsInfo(
    val selectedTab: Long? = null,
    val tabs: List<Tab>? = null,
)

@Serializable
internal data class Tab(
    val id: Long? = null,
    val tabKey: String? = null,
    @SerialName("must_show")
    val mustShow: Long? = null,
    val hidden: Long? = null,
    val title: String? = null,
    @SerialName("tab_type")
    val tabType: String? = null,
    val containerid: String? = null,
    val apipath: String? = null,
    val headSubTitleText: String? = null,
    @SerialName("new_select_menu")
    val newSelectMenu: Long? = null,
    val gender: String? = null,
    val params: TabParams? = null,
    @SerialName("tab_icon")
    val tabIcon: String? = null,
    @SerialName("tab_icon_dark")
    val tabIconDark: String? = null,
    val url: String? = null,
    @SerialName("filter_group")
    val filterGroup: List<FilterGroup>? = null,
    @SerialName("filter_group_info")
    val filterGroupInfo: FilterGroupInfo? = null,
)

@Serializable
internal data class FilterGroup(
    val name: String? = null,
    val containerid: String? = null,
    val title: String? = null,
    val scheme: String? = null,
)

@Serializable
internal data class FilterGroupInfo(
    val title: String? = null,
    val icon: String? = null,
    @SerialName("icon_name")
    val iconName: String? = null,
    @SerialName("icon_scheme")
    val iconScheme: String? = null,
)

@Serializable
internal data class TabParams(
    @SerialName("new_select_menu")
    val newSelectMenu: Long? = null,
    val gender: String? = null,
)

@Serializable
internal data class RepostTimeline(
    val data: List<Status>? = null,
    @SerialName("total_number")
    val totalNumber: Long? = null,
    @SerialName("hot_total_number")
    val hotTotalNumber: Long? = null,
    val max: Long? = null,
)

@Serializable
internal data class HotflowData(
    val data: List<Comment>? = null,
    @SerialName("total_number")
    val totalNumber: Long? = null,
    @SerialName("max_id")
    val maxID: Long? = null,
    val max: Long? = null,
    @SerialName("max_id_type")
    val maxIDType: Long? = null,
)

@Serializable
internal data class HotflowChildData(
    val ok: Long? = null,
    val data: List<Comment>? = null,
    @SerialName("total_number")
    val totalNumber: Long? = null,
    @SerialName("max_id")
    val maxID: Long? = null,
    @SerialName("max_id_type")
    val maxIDType: Long? = null,
    val max: Long? = null,
    val rootComment: List<Comment>? = null,
)
