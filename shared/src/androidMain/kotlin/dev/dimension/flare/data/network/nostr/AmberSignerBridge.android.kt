package dev.dimension.flare.data.network.nostr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.dimension.flare.ui.model.NostrSignerCredential
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AmberIntentResult(
    val resultCode: Int,
    val data: Intent?,
)

internal class AmberIntentLauncherRegistry {
    private val mutex = Mutex()
    private var launcher: ((Intent, (AmberIntentResult) -> Unit) -> Unit)? = null

    fun attach(launcher: (Intent, (AmberIntentResult) -> Unit) -> Unit): AutoCloseable {
        this.launcher = launcher
        return AutoCloseable {
            if (this.launcher === launcher) {
                this.launcher = null
            }
        }
    }

    suspend fun launch(intent: Intent): AmberIntentResult =
        mutex.withLock {
            suspendCancellableCoroutine { continuation ->
                val currentLauncher = launcher
                if (currentLauncher == null) {
                    continuation.resumeWithException(
                        IllegalStateException("Amber signer launcher is not attached."),
                    )
                    return@suspendCancellableCoroutine
                }
                currentLauncher.invoke(intent) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
}

internal class AndroidAmberSignerBridge(
    private val context: Context,
    private val launcherRegistry: AmberIntentLauncherRegistry,
) : AmberSignerBridge {
    override fun isAvailable(): Boolean {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
    }

    override suspend fun connect(): AmberConnection {
        ensureAvailable()
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                putExtra("type", "get_public_key")
            }
        val result = launcherRegistry.launch(intent)
        val data = requireApprovedResult(result)
        val pubkeyHex =
            data
                .getStringExtra("result")
                ?.let(::parsePublicKeyHex)
                .orEmpty()
        val packageName = data.getStringExtra("package")?.trim().orEmpty()
        require(pubkeyHex.isNotEmpty()) {
            "Amber did not return a public key."
        }
        require(packageName.isNotEmpty()) {
            "Amber did not return a signer package name."
        }
        return AmberConnection(
            credential =
                NostrSignerCredential.Amber(
                    userPubkeyHex = pubkeyHex,
                    packageName = packageName,
                    approvedSignerPubkey = pubkeyHex,
                ),
            pubkeyHex = pubkeyHex,
        )
    }

    override suspend fun getPublicKey(credential: NostrSignerCredential.Amber): String = credential.userPubkeyHex

    override suspend fun signEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String =
        querySignedEvent(
            credential = credential,
            unsignedEventJson = unsignedEventJson,
        ) ?: launchSignEvent(
            credential = credential,
            unsignedEventJson = unsignedEventJson,
        )

    private suspend fun launchSignEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String {
        ensureAvailable()
        val currentUser = credential.userPubkeyHex
        val intentId = unsignedEventJson.hashCode().toString()
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("nostrsigner:${Uri.encode(unsignedEventJson)}"),
            ).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                `package` = credential.packageName
                putExtra("type", "sign_event")
                putExtra("id", intentId)
                putExtra("current_user", currentUser)
            }
        val result = launcherRegistry.launch(intent)
        val data = requireApprovedResult(result)
        return data
            .getStringExtra("event")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Amber did not return a signed event.")
    }

    private fun querySignedEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String? {
        val packageName = credential.packageName ?: return null
        val uri = Uri.parse("content://$packageName.SIGN_EVENT")
        val cursor =
            context.contentResolver.query(
                uri,
                arrayOf(unsignedEventJson, "", credential.userPubkeyHex),
                null,
                null,
                null,
            ) ?: return null
        cursor.use {
            if (it.getColumnIndex("rejected") >= 0) {
                throw IllegalStateException("Amber rejected the signing request.")
            }
            if (!it.moveToFirst()) {
                return null
            }
            val eventIndex = it.getColumnIndex("event")
            if (eventIndex < 0) {
                return null
            }
            return it.getString(eventIndex)?.takeIf(String::isNotBlank)
        }
    }

    private fun requireApprovedResult(result: AmberIntentResult): Intent {
        if (result.resultCode != Activity.RESULT_OK) {
            throw IllegalStateException("Amber rejected the request.")
        }
        return result.data ?: throw IllegalStateException("Amber returned no result data.")
    }

    private fun ensureAvailable() {
        check(isAvailable()) {
            "Amber signer is not installed."
        }
    }
}
