package dev.dimension.flare.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CruxViewModelTest {
    @Test
    fun clearingOneEntryClosesOnlyItsBridge() {
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        var firstCloses = 0
        var secondCloses = 0
        val first = owner(firstStore) { firstCloses++ }
        val second = owner(secondStore) { secondCloses++ }

        assertNotSame(first.viewModel, second.viewModel)
        firstStore.clear()
        assertEquals(1, firstCloses)
        assertEquals(0, secondCloses)
        secondStore.clear()
        assertEquals(1, firstCloses)
        assertEquals(1, secondCloses)
    }

    @Test
    fun initializationFailureClosesTheBridge() {
        var closes = 0
        assertThrows(IllegalStateException::class.java) {
            CruxViewModel<Any>(AutoCloseable { closes++ }) { error("init failed") }
        }
        assertEquals(1, closes)
    }

    private fun owner(
        store: ViewModelStore,
        bridge: AutoCloseable,
    ): CruxViewModel<*> {
        val factory = viewModelFactory { initializer { CruxViewModel(bridge) { Any() } } }
        val provider = ViewModelProvider.create(store, factory)
        return provider["counter", CruxViewModel::class]
    }
}
