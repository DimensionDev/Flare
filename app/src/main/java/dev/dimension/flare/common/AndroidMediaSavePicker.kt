package dev.dimension.flare.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.annotation.Single
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal data class AndroidCreateDocumentRequest(
    val fileName: String,
    val mimeType: String,
)

private class AndroidCreateDocumentContract : ActivityResultContract<AndroidCreateDocumentRequest, Uri?>() {
    override fun createIntent(
        context: Context,
        input: AndroidCreateDocumentRequest,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.fileName)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = intent?.data
}

@Stable
@Single
internal class AndroidMediaSavePicker {
    private val mutex = Mutex()

    private var createDocumentLauncher: ActivityResultLauncher<AndroidCreateDocumentRequest>? = null
    private var openDirectoryLauncher: ActivityResultLauncher<Uri?>? = null
    private var createDocumentContinuation: Continuation<Uri?>? = null
    private var openDirectoryContinuation: Continuation<Uri?>? = null

    fun attach(
        createDocumentLauncher: ActivityResultLauncher<AndroidCreateDocumentRequest>,
        openDirectoryLauncher: ActivityResultLauncher<Uri?>,
    ) {
        this.createDocumentLauncher = createDocumentLauncher
        this.openDirectoryLauncher = openDirectoryLauncher
    }

    fun detach() {
        createDocumentContinuation?.resume(null)
        openDirectoryContinuation?.resume(null)
        createDocumentContinuation = null
        openDirectoryContinuation = null
        createDocumentLauncher = null
        openDirectoryLauncher = null
    }

    suspend fun createDocument(
        fileName: String,
        mimeType: String,
    ): Uri? =
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val launcher = createDocumentLauncher ?: return@withContext null
                suspendCancellableCoroutine { continuation ->
                    createDocumentContinuation = continuation
                    continuation.invokeOnCancellation {
                        if (createDocumentContinuation === continuation) {
                            createDocumentContinuation = null
                        }
                    }
                    launcher.launch(AndroidCreateDocumentRequest(fileName = fileName, mimeType = mimeType))
                }
            }
        }

    suspend fun openDirectory(): Uri? =
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val launcher = openDirectoryLauncher ?: return@withContext null
                suspendCancellableCoroutine { continuation ->
                    openDirectoryContinuation = continuation
                    continuation.invokeOnCancellation {
                        if (openDirectoryContinuation === continuation) {
                            openDirectoryContinuation = null
                        }
                    }
                    launcher.launch(null)
                }
            }
        }

    fun onCreateDocumentResult(uri: Uri?) {
        createDocumentContinuation?.resume(uri)
        createDocumentContinuation = null
    }

    fun onOpenDirectoryResult(uri: Uri?) {
        openDirectoryContinuation?.resume(uri)
        openDirectoryContinuation = null
    }
}

@Composable
internal fun ProvideAndroidMediaSavePicker(content: @Composable () -> Unit) {
    val picker = koinInject<AndroidMediaSavePicker>()
    val createDocumentLauncher =
        rememberLauncherForActivityResult(AndroidCreateDocumentContract()) { uri ->
            picker.onCreateDocumentResult(uri)
        }
    val openDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            picker.onOpenDirectoryResult(uri)
        }
    DisposableEffect(picker, createDocumentLauncher, openDirectoryLauncher) {
        picker.attach(createDocumentLauncher, openDirectoryLauncher)
        onDispose {
            picker.detach()
        }
    }
    content()
}
