package dev.dimension.flare.ui.screen.rss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import dev.dimension.flare.ui.FlareApp

class AddRssSourceActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialText =
            when {
                intent?.action == Intent.ACTION_SEND -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                }

                else -> intent.dataString
            }

        setContent {
            FlareApp {
                ModalBottomSheet(
                    onDismissRequest = {
                        finish()
                    },
                ) {
                    RssSourceEditSheet(
                        onDismissRequest = {
                            finish()
                        },
                        id = null,
                        initialUrl = initialText,
                    )
                }
            }
        }
    }
}
