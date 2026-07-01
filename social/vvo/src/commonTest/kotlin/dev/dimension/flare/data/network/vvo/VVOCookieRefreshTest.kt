package dev.dimension.flare.data.network.vvo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VVOCookieRefreshTest {
    @Test
    fun mergeVvoCookieHeaderKeepsExistingCookiesMissingFromSetCookie() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=new-sub; XSRF-TOKEN=old-xsrf", merged)
    }

    @Test
    fun mergeVvoCookieHeaderAddsNewCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=new-sub", merged)
    }

    @Test
    fun mergeVvoCookieHeaderSkipsMaxAgeExpiredCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=; Max-Age=0; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertNull(merged)
    }

    @Test
    fun mergeVvoCookieHeaderSkipsExpiresExpiredCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertNull(merged)
    }

    @Test
    fun mergeVvoCookieHeaderKeepsExistingCookiesWhenExpiredCookieIsMixedWithFreshCookie() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/; Domain=.weibo.cn",
                        "MLOGIN=1; Expires=Fri, 31 Dec 9999 23:59:59 GMT; Path=/; Domain=.weibo.cn",
                        "NEW_COOKIE=new-value; Expires=Fri, 31 Dec 9999 23:59:59 GMT; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf; NEW_COOKIE=new-value", merged)
    }

    @Test
    fun mergeVvoCookieHeaderReturnsNullWhenNothingChanges() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub",
                setCookieHeaders =
                    listOf(
                        "SUB=old-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertNull(merged)
    }
}
