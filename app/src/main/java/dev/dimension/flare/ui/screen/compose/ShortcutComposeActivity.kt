package dev.dimension.flare.ui.screen.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import dev.dimension.flare.ui.FlareApp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

class ShortcutComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialText =
            when {
                intent?.action == Intent.ACTION_SEND -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                }
                else -> ""
            }

        val initialMedias =
            when {
                intent?.action == Intent.ACTION_SEND &&
                    intent.type?.startsWith("image/") == true -> {
                    listOfNotNull(
                        IntentCompat.getParcelableExtra(
                            intent,
                            Intent.EXTRA_STREAM,
                            Uri::class.java,
                        ),
                    ).toPersistentList()
                }
                intent?.action == Intent.ACTION_SEND_MULTIPLE &&
                    intent.type?.startsWith("image/") == true -> {
                    IntentCompat
                        .getParcelableArrayListExtra(
                            intent,
                            Intent.EXTRA_STREAM,
                            Uri::class.java,
                        ).orEmpty()
                        .toPersistentList()
                }
                else -> persistentListOf()
            }

        setContent {
            FlareApp {
                ShortcutComposeRoute(
                    onBack = { finish() },
                    initialText = initialText,
                    initialMedias = initialMedias,
                )
            }
        }
    }
}
