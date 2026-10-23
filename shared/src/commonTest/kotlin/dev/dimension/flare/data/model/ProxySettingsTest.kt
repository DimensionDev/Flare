package dev.dimension.flare.data.model

import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.withProxy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ProxySettingsTest {
    private val alice = ProxyRef.Account(MicroBlogKey("alice", "example.social"))
    private val bob = ProxyRef.Account(MicroBlogKey("bob", "example.social"))
    private val profile = ProxyProfile("one", "Work", ProxyProtocol.SOCKS5, "proxy.example", 1080)

    @Test
    fun policiesDistinguishInheritanceSystemAndDirect() {
        val settings =
            ProxySettings(
                profiles = listOf(profile),
                global = ProxyPolicy(ProxyMode.PROXY, profile.id),
                accounts = mapOf(bob.accountKey.toString() to ProxyPolicy(ProxyMode.DIRECT)),
            )
        assertEquals(profile.connection(), settings.resolve(alice))
        assertEquals(ProxyMode.DIRECT, settings.resolve(bob).mode)
        assertEquals(ProxyMode.SYSTEM, settings.resolve(ProxyRef.System).mode)
        assertEquals(ProxyMode.SYSTEM, ProxySettings().resolve(ProxyRef.Global).mode)
        assertFailsWith<IllegalStateException> { settings.resolve(ProxyRef.Unbound) }
    }

    @Test
    fun invalidOrDeletedProxyNeverFallsBackToDirect() {
        val settings = ProxySettings(global = ProxyPolicy(ProxyMode.PROXY, "missing"))
        assertFailsWith<IllegalArgumentException> { settings.resolve(alice) }
        assertFailsWith<IllegalArgumentException> { settings.validatePolicy(ProxyPolicy(), account = false) }
        assertFailsWith<IllegalArgumentException> { profile.copy(host = "https://proxy.example").validate() }
        assertFailsWith<IllegalArgumentException> { profile.copy(host = "name@proxy.example").validate() }
        assertFailsWith<IllegalArgumentException> { profile.copy(port = 65536).validate() }
        val removed = ProxySettings(removedAccounts = setOf(alice.accountKey.toString()))
        val restored = removed.encodeProtobuf<ProxySettings>().decodeProtobuf<ProxySettings>()
        assertFailsWith<IllegalStateException> { restored.resolve(alice) }
    }

    @Test
    fun profileInUseMustBeReassignedAndRenameKeepsConnection() {
        val settings = ProxySettings(listOf(profile), ProxyPolicy(ProxyMode.PROXY, profile.id))
        assertFailsWith<IllegalArgumentException> { settings.remove(profile.id) }
        assertEquals(settings.resolve(alice), settings.save(profile.copy(name = "Renamed")).resolve(alice))
        assertNotEquals(settings.resolve(alice), settings.save(profile.copy(port = 1081)).resolve(alice))
    }

    @Test
    fun mediaBindingDoesNotChangePersistedBytes() {
        val media: UiMedia = UiMedia.Image("https://example.social/avatar.jpg")
        val encoded = media.encodeProtobuf<UiMedia>()
        assertContentEquals(encoded, media.withProxy(alice).encodeProtobuf<UiMedia>())
        assertContentEquals(encoded, media.withProxy(bob).encodeProtobuf<UiMedia>())
        val restored = encoded.decodeProtobuf<UiMedia>()
        assertEquals(ProxyRef.Unbound, restored.proxy)
        assertEquals(alice, restored.withProxy(alice).preview?.proxy)
        assertEquals(bob, restored.withProxy(bob).preview?.proxy)
    }
}
