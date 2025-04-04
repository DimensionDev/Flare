package dev.dimension.flare.data.network.nodeinfo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * NodeInfo schema version 2.0.
 */
@Serializable
internal data class Schema20(
    /**
     * Free form key value pairs for software specific values. Clients should not rely on any
     * specific key present.
     */
    val metadata: JsonObject? = null,
    /**
     * Whether this server allows open self-registration.
     */
    val openRegistrations: Boolean? = null,
    /**
     * The protocols supported on this server.
     */
    val protocols: List<Protocol>? = null,
    /**
     * The third party sites this server can connect to via their application API.
     */
    val services: Services? = null,
    /**
     * Metadata about server software in use.
     */
    val software: Software? = null,
    /**
     * Usage statistics for this server.
     */
    val usage: Usage? = null,
    /**
     * The schema version, must be 2.0.
     */
    val version: Version? = null,
) {
    @Serializable
    internal enum class Protocol(
        val value: String,
    ) {
        @SerialName("activitypub")
        Activitypub("activitypub"),

        @SerialName("buddycloud")
        Buddycloud("buddycloud"),

        @SerialName("dfrn")
        Dfrn("dfrn"),

        @SerialName("diaspora")
        Diaspora("diaspora"),

        @SerialName("libertree")
        Libertree("libertree"),

        @SerialName("ostatus")
        Ostatus("ostatus"),

        @SerialName("pumpio")
        Pumpio("pumpio"),

        @SerialName("tent")
        Tent("tent"),

        @SerialName("xmpp")
        XMPP("xmpp"),

        @SerialName("zot")
        Zot("zot"),
    }

    /**
     * The third party sites this server can connect to via their application API.
     */
    @Serializable
    internal data class Services(
        /**
         * The third party sites this server can retrieve messages from for combined display with
         * regular traffic.
         */
        val inbound: List<Inbound>? = null,
        /**
         * The third party sites this server can publish messages to on the behalf of a user.
         */
        val outbound: List<Outbound>? = null,
    )

    @Serializable
    internal enum class Inbound(
        val value: String,
    ) {
        @SerialName("atom1.0")
        Atom10("atom1.0"),

        @SerialName("gnusocial")
        Gnusocial("gnusocial"),

        @SerialName("imap")
        IMAP("imap"),

        @SerialName("pnut")
        Pnut("pnut"),

        @SerialName("pop3")
        Pop3("pop3"),

        @SerialName("pumpio")
        Pumpio("pumpio"),

        @SerialName("rss2.0")
        Rss20("rss2.0"),

        @SerialName("twitter")
        Twitter("twitter"),
    }

    @Serializable
    internal enum class Outbound(
        val value: String,
    ) {
        @SerialName("atom1.0")
        Atom10("atom1.0"),

        @SerialName("blogger")
        Blogger("blogger"),

        @SerialName("buddycloud")
        Buddycloud("buddycloud"),

        @SerialName("diaspora")
        Diaspora("diaspora"),

        @SerialName("dreamwidth")
        Dreamwidth("dreamwidth"),

        @SerialName("drupal")
        Drupal("drupal"),

        @SerialName("facebook")
        Facebook("facebook"),

        @SerialName("friendica")
        Friendica("friendica"),

        @SerialName("gnusocial")
        Gnusocial("gnusocial"),

        @SerialName("google")
        Google("google"),

        @SerialName("insanejournal")
        Insanejournal("insanejournal"),

        @SerialName("libertree")
        Libertree("libertree"),

        @SerialName("linkedin")
        Linkedin("linkedin"),

        @SerialName("livejournal")
        Livejournal("livejournal"),

        @SerialName("mediagoblin")
        Mediagoblin("mediagoblin"),

        @SerialName("myspace")
        Myspace("myspace"),

        @SerialName("pinterest")
        Pinterest("pinterest"),

        @SerialName("pnut")
        Pnut("pnut"),

        @SerialName("posterous")
        Posterous("posterous"),

        @SerialName("pumpio")
        Pumpio("pumpio"),

        @SerialName("redmatrix")
        Redmatrix("redmatrix"),

        @SerialName("rss2.0")
        Rss20("rss2.0"),

        @SerialName("smtp")
        SMTP("smtp"),

        @SerialName("tent")
        Tent("tent"),

        @SerialName("tumblr")
        Tumblr("tumblr"),

        @SerialName("twitter")
        Twitter("twitter"),

        @SerialName("wordpress")
        Wordpress("wordpress"),

        @SerialName("xmpp")
        XMPP("xmpp"),
    }

    /**
     * Metadata about server software in use.
     */
    @Serializable
    internal data class Software(
        /**
         * The canonical name of this server software.
         */
        val name: String? = null,
        /**
         * The version of this server software.
         */
        val version: String? = null,
    )

    /**
     * Usage statistics for this server.
     */
    @Serializable
    internal data class Usage(
        /**
         * The amount of comments that were made by users that are registered on this server.
         */
        val localComments: Long? = null,
        /**
         * The amount of posts that were made by users that are registered on this server.
         */
        val localPosts: Long? = null,
        /**
         * statistics about the users of this server.
         */
        val users: Users? = null,
    )

    /**
     * statistics about the users of this server.
     */
    @Serializable
    internal data class Users(
        /**
         * The amount of users that signed in at least once in the last 180 days.
         */
        val activeHalfyear: Long? = null,
        /**
         * The amount of users that signed in at least once in the last 30 days.
         */
        val activeMonth: Long? = null,
        /**
         * The total amount of on this server registered users.
         */
        val total: Long? = null,
    )

    /**
     * The schema version, must be 2.0.
     */
    @Serializable
    internal enum class Version(
        val value: String,
    ) {
        @SerialName("2.0")
        The20("2.0"),
    }
}
