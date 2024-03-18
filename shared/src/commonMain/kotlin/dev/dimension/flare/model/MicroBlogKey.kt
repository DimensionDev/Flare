package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class MicroBlogKey(
    val id: String,
    val host: String,
) {
    override fun hashCode(): Int {
        return this.id.hashCode() * 31 + host.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is MicroBlogKey) return false
        return other.id == id && other.host == host
    }

    override fun toString(): String {
        return if (host.isNotEmpty()) escapeText(id) + "@" + escapeText(host) else id
    }

    private fun escapeText(host: String): String {
        val sb = StringBuilder()
        var i = 0
        val j = host.length
        while (i < j) {
            val ch = host[i]
            if (isSpecialChar(ch)) {
                sb.append('\\')
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun isSpecialChar(ch: Char): Boolean {
        return ch == '\\' || ch == '@' || ch == ','
    }

    companion object {
        fun valueOf(str: String): MicroBlogKey {
            var escaping = false
            var idFinished = false
            val idBuilder = StringBuilder(str.length)
            val hostBuilder = StringBuilder(str.length)
            var i = 0
            val j = str.length
            while (i < j) {
                val ch = str[i]
                var append = false
                if (escaping) {
                    // accept all characters if is escaping
                    append = true
                    escaping = false
                } else if (ch == '\\') {
                    escaping = true
                } else if (ch == '@') {
                    idFinished = true
                } else if (ch == ',') {
                    // end of item, just jump out
                    break
                } else {
                    append = true
                }
                if (append) {
                    if (idFinished) {
                        hostBuilder.append(ch)
                    } else {
                        idBuilder.append(ch)
                    }
                }
                i++
            }
            return if (hostBuilder.isNotEmpty()) {
                MicroBlogKey(idBuilder.toString(), hostBuilder.toString())
            } else {
                MicroBlogKey(idBuilder.toString(), "")
            }
        }
    }
}
