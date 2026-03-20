package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import dev.dimension.flare.ui.model.UiAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NostrServiceTest {
    @Test
    fun generateAccountCreatesMatchingPrivateAndPublicKeys() {
        val generated = NostrService.generateAccount(relayInput = "")

        assertMatchesKeyPair(generated)
        assertEquals(
            NostrService.defaultRelays.map { RelayUrlNormalizer.normalizeOrNull(it)!!.url },
            generated.relays,
        )
    }

    @Test
    fun exportAccountKeepsPrivateAndPublicKeysConsistent() {
        val generated = NostrService.generateAccount(relayInput = "wss://relay.damus.io, wss://nos.lol")
        val exported =
            NostrService.exportAccount(
                UiAccount.Nostr.Credential(
                    pubkey = generated.pubkeyHex,
                    nsec = generated.nsec,
                    relays = generated.relays,
                ),
            )

        assertMatchesKeyPair(exported)
        assertEquals(generated.pubkeyHex, exported.pubkeyHex)
        assertEquals(generated.npub, exported.npub)
        assertEquals(generated.relays, exported.relays)
    }

    @Test
    fun importAccountAcceptsSecretOnlyAndNormalizesRelays() {
        val imported =
            NostrService.importAccount(
                publicKeyInput = "",
                secretKeyInput = SECRET_KEY_HEX,
                relayInput = "wss://relay.damus.io/  wss://relay.damus.io  wss://nos.lol",
            )

        assertMatchesKeyPair(imported)
        assertEquals(
            listOf("wss://relay.damus.io/", "wss://nos.lol/"),
            imported.relays,
        )
    }

    private fun assertMatchesKeyPair(account: NostrService.ImportedAccount) {
        assertEquals(64, account.pubkeyHex.length)
        assertTrue(account.npub.startsWith("npub1"))
        val normalizedSecret = assertNotNull(account.nsec)
        assertTrue(normalizedSecret.isNotBlank())

        val reImported =
            NostrService.importAccount(
                publicKeyInput = "",
                secretKeyInput = normalizedSecret,
                relayInput = account.relays.joinToString(","),
            )
        assertEquals(account.pubkeyHex, reImported.pubkeyHex)
        assertEquals(account.npub, NPub.create(account.pubkeyHex))
    }

    private companion object {
        const val SECRET_KEY_HEX = "1111111111111111111111111111111111111111111111111111111111111111"
    }
}
