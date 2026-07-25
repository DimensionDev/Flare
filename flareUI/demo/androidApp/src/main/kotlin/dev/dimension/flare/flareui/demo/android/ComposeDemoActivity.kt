package dev.dimension.flare.flareui.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.flareui.compose.FlareComposeContent
import dev.dimension.flare.flareui.demo.FlareUiDemo
import dev.dimension.flare.flareui.demo.shared.resources.DemoAndroidResources

public class ComposeDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        FlareComposeContent(
                            resources = DemoAndroidResources,
                        ) {
                            FlareUiDemo()
                        }
                    }
                }
            }
        }
    }
}
