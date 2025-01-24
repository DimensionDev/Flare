package dev.dimension.flare.data.network.rss.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
internal sealed interface Feed {
    @Serializable
    @SerialName("feed")
    data class Atom(
        @XmlSerialName("id")
        @XmlElement(true)
        val id: String,
        @XmlSerialName("title")
        @XmlElement(true)
        val title: Text,
        @XmlSerialName("updated")
        @XmlElement(true)
        val updated: String?, // ISO-8601 datetime string
        @XmlSerialName("author")
        @XmlElement(true)
        val authors: List<Person> = emptyList(),
        @XmlSerialName("contributor")
        @XmlElement(true)
        val contributors: List<Person> = emptyList(),
        @XmlSerialName("category")
        @XmlElement(true)
        val categories: List<Category> = emptyList(),
        @XmlSerialName("generator")
        @XmlElement(true)
        val generator: Generator? = null,
        @XmlSerialName("icon")
        @XmlElement(true)
        val icon: String? = null, // URI
        @XmlSerialName("logo")
        @XmlElement(true)
        val logo: String? = null, // URI
        @XmlSerialName("rights")
        @XmlElement(true)
        val rights: Text? = null,
        @XmlSerialName("subtitle")
        @XmlElement(true)
        val subtitle: Text? = null,
        @XmlSerialName("link")
        @XmlElement(true)
        val links: List<Link> = emptyList(),
        @XmlSerialName("entry")
        @XmlElement(true)
        val entries: List<Entry> = emptyList(),
    ) : Feed {
        @Serializable
        data class Entry(
            @XmlSerialName("id")
            @XmlElement(true)
            val id: String,
            @XmlSerialName("title")
            @XmlElement(true)
            val title: Text,
            @XmlSerialName("updated")
            @XmlElement(true)
            val updated: String, // ISO-8601 datetime string
            @XmlSerialName("author")
            @XmlElement(true)
            val authors: List<Person> = emptyList(),
            @XmlSerialName("content")
            @XmlElement(true)
            val content: Content? = null,
            @XmlSerialName("contributor")
            @XmlElement(true)
            val contributors: List<Person> = emptyList(),
            @XmlSerialName("published")
            @XmlElement(true)
            val published: String? = null, // ISO-8601 datetime string
            @XmlSerialName("rights")
            @XmlElement(true)
            val rights: Text? = null,
            @XmlSerialName("source")
            @XmlElement(true)
            val source: Source? = null,
            @XmlSerialName("summary")
            @XmlElement(true)
            val summary: Text? = null,
            @XmlSerialName("link")
            @XmlElement(true)
            val links: List<Link> = emptyList(),
            @XmlSerialName("category")
            @XmlElement(true)
            val categories: List<Category> = emptyList(),
            @XmlSerialName("group", prefix = "media", namespace = "http://search.yahoo.com/mrss/")
            @XmlElement(true)
            val media: Media? = null,
        )

        @Serializable
        data class Media(
            @XmlSerialName("title", prefix = "media")
            @XmlElement(true)
            val title: Text? = null,
            @XmlSerialName("content", prefix = "media")
            @XmlElement(true)
            val content: Content? = null,
            @XmlSerialName("thumbnail", prefix = "media")
            @XmlElement(true)
            val thumbnail: Content? = null,
            @XmlSerialName("description", prefix = "media")
            @XmlElement(true)
            val description: Text? = null,
        ) {
            @Serializable
            data class Content(
                @XmlElement(false)
                val type: String? = null,
                @XmlElement(false)
                val url: String,
                @XmlElement(false)
                val width: Int? = null,
                @XmlElement(false)
                val height: Int? = null,
            )
        }

        @Serializable
        data class Person(
            @XmlSerialName("name")
            @XmlElement(true)
            val name: String,
            @XmlSerialName("uri")
            @XmlElement(true)
            val uri: String? = null,
            @XmlSerialName("email")
            @XmlElement(true)
            val email: String? = null,
        )

        @Serializable
        data class Text(
            @XmlValue
            val value: String,
            @XmlElement(false)
            val type: TextType = TextType.TEXT,
        )

        @Serializable
        enum class TextType {
            @SerialName("text")
            TEXT,

            @SerialName("html")
            HTML,

            @SerialName("xhtml")
            XHTML,
        }

        @Serializable
        data class Content(
            @XmlValue
            val value: String? = null,
            @XmlElement(false)
            val src: String? = null, // URI
            @XmlElement(false)
            val type: String? = null,
        )

        @Serializable
        data class Category(
            @XmlElement(false)
            val term: String,
            @XmlElement(false)
            val scheme: String? = null, // URI
            @XmlElement(false)
            val label: String? = null,
        )

        @Serializable
        data class Generator(
            @XmlValue
            val value: String,
            @XmlElement(false)
            val uri: String? = null,
            @XmlElement(false)
            val version: String? = null,
        )

        @Serializable
        data class Link(
            @XmlElement(false)
            val href: String, // URI
            @XmlElement(false)
            val rel: String? = null,
            @XmlElement(false)
            val type: String? = null,
            @XmlElement(false)
            val hreflang: String? = null,
            @XmlElement(false)
            val title: String? = null,
            @XmlElement(false)
            val length: Int? = null,
        )

        @Serializable
        data class Source(
            @XmlSerialName("id")
            @XmlElement(true)
            val id: String? = null,
            @XmlSerialName("title")
            @XmlElement(true)
            val title: Text? = null,
            @XmlSerialName("updated")
            @XmlElement(true)
            val updated: String? = null, // ISO-8601 datetime string
            @XmlSerialName("author")
            @XmlElement(true)
            val authors: List<Person> = emptyList(),
            @XmlSerialName("contributor")
            @XmlElement(true)
            val contributors: List<Person> = emptyList(),
            @XmlSerialName("category")
            @XmlElement(true)
            val categories: List<Category> = emptyList(),
            @XmlSerialName("generator")
            @XmlElement(true)
            val generator: Generator? = null,
            @XmlSerialName("icon")
            @XmlElement(true)
            val icon: String? = null, // URI
            @XmlSerialName("logo")
            @XmlElement(true)
            val logo: String? = null, // URI
            @XmlSerialName("rights")
            @XmlElement(true)
            val rights: Text? = null,
            @XmlSerialName("subtitle")
            @XmlElement(true)
            val subtitle: Text? = null,
            @XmlSerialName("link")
            @XmlElement(true)
            val links: List<Link> = emptyList(),
        )
    }

    @Serializable
    @SerialName("rss")
    data class Rss20(
        val version: String,
        @XmlElement(true)
        val channel: Channel,
    ) : Feed {
        @Serializable
        @SerialName("channel")
        data class Channel(
            @XmlElement(true)
            val title: String,
            @XmlElement(true)
            val link: String,
            @XmlElement(true)
            val description: String,
            @XmlElement(true)
            val language: String? = null,
            @XmlElement(true)
            val copyright: String? = null,
            @XmlElement(true)
            val managingEditor: String? = null,
            @XmlElement(true)
            val webMaster: String? = null,
            @XmlElement(true)
            val pubDate: String? = null,
            @XmlElement(true)
            val lastBuildDate: String? = null,
            @XmlElement(true)
            val category: String? = null,
            @XmlElement(true)
            val generator: String? = null,
            @XmlElement(true)
            val docs: String? = null,
            @XmlElement(true)
            val cloud: Cloud? = null,
            @XmlElement(true)
            val ttl: Int? = null,
            @XmlElement(true)
            val image: Image? = null,
            @XmlElement(true)
            val rating: String? = null,
            @XmlElement(true)
            val textInput: TextInput? = null,
            @XmlElement(true)
            val skipHours: List<Int> = emptyList(),
            @XmlElement(true)
            val skipDays: List<String> = emptyList(),
            @XmlSerialName(value = "item")
            val items: List<Item> = emptyList(),
        )

        @Serializable
        data class Cloud(
            @XmlElement(true)
            val domain: String,
            @XmlElement(true)
            val port: Int,
            @XmlElement(true)
            val path: String,
            @XmlElement(true)
            val registerProcedure: String,
            @XmlElement(true)
            val protocol: String,
        )

        @Serializable
        data class Image(
            @XmlElement(true)
            val url: String,
            @XmlElement(true)
            val title: String? = null,
            @XmlElement(true)
            val link: String? = null,
            @XmlElement(true)
            val width: Int? = null,
            @XmlElement(true)
            val height: Int? = null,
            @XmlElement(true)
            val description: String? = null,
        )

        @Serializable
        data class TextInput(
            @XmlElement(true)
            val title: String,
            @XmlElement(true)
            val description: String? = null,
            @XmlElement(true)
            val name: String,
            @XmlElement(true)
            val link: String? = null,
        )

        @Serializable
        data class Item(
            @XmlElement(true)
            val title: String,
            @XmlElement(true)
            val link: String,
            @XmlElement(true)
            val description: String? = null,
            @XmlElement(true)
            val author: String? = null,
            @XmlElement(true)
            val category: String? = null,
            @XmlElement(true)
            val comments: String? = null,
            @XmlElement(true)
            val enclosure: Enclosure? = null,
            @XmlElement(true)
            val guid: Guid? = null,
            @XmlElement(true)
            val pubDate: String? = null,
            @XmlElement(true)
            val source: Source? = null,
        )

        @Serializable
        data class Enclosure(
            @XmlElement(true)
            val url: String,
            @XmlElement(true)
            val length: Int,
            @XmlElement(true)
            val type: String,
        )

        @Serializable
        @SerialName("guid")
        data class Guid(
            @XmlElement(false)
            val isPermaLink: Boolean = false,
            @XmlValue
            val value: String,
        )

        @Serializable
        data class Source(
            @XmlElement(true)
            val url: String,
            @XmlElement(true)
            val title: String? = null,
        )
    }
}
