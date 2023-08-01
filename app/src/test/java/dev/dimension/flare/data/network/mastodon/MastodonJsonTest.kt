package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MastodonJsonTest {
    @Test
    fun parseAccount() {
        val accountJson = this.javaClass.classLoader?.getResource("mastodon_account.json")?.readText()
        assertNotNull(accountJson)
        val account = accountJson?.decodeJson<Account>()
        assertNotNull(account)
        assertEquals("Tlaster", account?.acct)
    }

    @Test
    fun parseStatus() {
        val statusJson = this.javaClass.classLoader?.getResource("mastodon_status.json")?.readText()
        assertNotNull(statusJson)
        val status = statusJson?.decodeJson<List<Status>>()
        assertNotNull(status)
        assertEquals("105853517700650526", status?.get(0)?.id)
    }
}
