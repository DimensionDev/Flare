package dev.dimension.flare.ui.presenter.home

import kotlin.test.Test
import kotlin.test.assertEquals

class FavIconPresenterTest {
    @Test
    fun hostIsConvertedToHttpsUrl() {
        assertEquals("https://fanbox.cc", "fanbox.cc".toFavIconFetchUrl())
    }

    @Test
    fun httpsUrlIsKeptAsIs() {
        assertEquals("https://www.fanbox.cc/", "https://www.fanbox.cc/".toFavIconFetchUrl())
    }

    @Test
    fun httpUrlIsKeptAsIs() {
        assertEquals("http://example.com", "http://example.com".toFavIconFetchUrl())
    }
}
