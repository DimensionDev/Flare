package dev.dimension.flare.common

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface CruxBridge : AutoCloseable {
    fun update(event: ByteArray): ByteArray

    fun view(): ByteArray
}

internal class CruxBinding<State : Any, Event : Any>(
    val createBridge: () -> CruxBridge,
    val encodeEvent: (Event) -> ByteArray,
    val decodeState: (ByteArray) -> State,
    val processEffects: (ByteArray, render: () -> Unit) -> Unit,
)

@MainThread
internal class CruxViewModel<State : Any, Event : Any>(
    private val binding: CruxBinding<State, Event>,
) : ViewModel(),
    AutoCloseable {
    private var bridge: CruxBridge? = null
    private val mutableState = MutableStateFlow<State?>(null)
    private val mutableError = MutableStateFlow<String?>(null)
    val state = mutableState.asStateFlow()
    val error = mutableError.asStateFlow()

    init {
        try {
            val native = binding.createBridge().also { bridge = it }
            mutableState.value = binding.decodeState(native.view())
        } catch (error: Exception) {
            close()
            mutableError.value = error.message ?: error.toString()
        }
    }

    fun send(event: Event) {
        val native = bridge ?: return
        try {
            mutableError.value = null
            binding.processEffects(native.update(binding.encodeEvent(event))) {
                if (bridge === native) {
                    try {
                        mutableState.value = binding.decodeState(native.view())
                    } catch (error: Exception) {
                        mutableError.value = error.message ?: error.toString()
                    }
                }
            }
        } catch (error: Exception) {
            mutableError.value = error.message ?: error.toString()
        }
    }

    override fun close() {
        val native = bridge ?: return
        bridge = null
        native.close()
    }

    override fun onCleared() {
        close()
    }
}

/** An explicit key keeps different features separate despite generic erasure. */
@Composable
internal fun <State : Any, Event : Any> cruxViewModel(
    key: String,
    binding: () -> CruxBinding<State, Event>,
): CruxViewModel<State, Event> = viewModel(key = key) { CruxViewModel(binding()) }
