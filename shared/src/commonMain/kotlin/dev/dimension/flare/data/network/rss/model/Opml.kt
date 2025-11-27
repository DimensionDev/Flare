package dev.dimension.flare.data.network.rss.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("opml")
public data class Opml(
    @XmlSerialName("version")
    @XmlElement(false)
    val version: String,
    @XmlSerialName("head")
    val head: OpmlHead? = null,
    @XmlSerialName("body")
    val body: OpmlBody,
)

@Serializable
@XmlSerialName("head")
public data class OpmlHead(
    @XmlSerialName("title")
    @XmlElement(true)
    val title: String? = null,
    @XmlSerialName("dateCreated")
    @XmlElement(true)
    val dateCreated: String? = null,
    @XmlSerialName("dateModified")
    @XmlElement(true)
    val dateModified: String? = null,
    @XmlSerialName("ownerName")
    @XmlElement(true)
    val ownerName: String? = null,
    @XmlSerialName("ownerEmail")
    @XmlElement(true)
    val ownerEmail: String? = null,
)

@Serializable
@XmlSerialName("body")
public data class OpmlBody(
    @XmlSerialName("outline")
    @XmlElement(true)
    val outlines: List<OpmlOutline> = emptyList(),
)

@Serializable
@XmlSerialName("outline")
public data class OpmlOutline(
    @XmlSerialName("text")
    @XmlElement(false)
    val text: String,
    @XmlSerialName("title")
    @XmlElement(false)
    val title: String? = null,
    @XmlSerialName("type")
    @XmlElement(false)
    val type: String? = null,
    @XmlSerialName("xmlUrl")
    @XmlElement(false)
    val xmlUrl: String? = null,
    @XmlSerialName("htmlUrl")
    @XmlElement(false)
    val htmlUrl: String? = null,
    @XmlSerialName("category")
    @XmlElement(false)
    val category: String? = null,
    @XmlSerialName("description")
    @XmlElement(false)
    val description: String? = null,
    @XmlSerialName("outline")
    @XmlElement(true)
    val outlines: List<OpmlOutline> = emptyList(),
)
