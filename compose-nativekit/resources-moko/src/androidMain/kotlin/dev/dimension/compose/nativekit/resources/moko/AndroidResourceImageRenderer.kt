package dev.dimension.compose.nativekit.resources.moko

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.res.painterResource
import com.google.android.material.imageview.ShapeableImageView
import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.android.AbstractAndroidWidget
import dev.dimension.compose.nativekit.android.AndroidViewBackend
import dev.dimension.compose.nativekit.compose.AbstractAndroidComposeWidget
import dev.dimension.compose.nativekit.compose.AndroidComposeBackend

/** Installs [ResourceImage] for the Android View backend. */
public object AndroidViewMokoResourcesRendererPlugin : NativeKitRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(ResourceImageWidget::class) { backend ->
            AndroidViewResourceImageWidget(backend)
        }
    }
}

/** Installs [ResourceImage] for the Android Compose backend. */
public object AndroidComposeMokoResourcesRendererPlugin : NativeKitRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(ResourceImageWidget::class) { _ ->
            AndroidComposeResourceImageWidget()
        }
    }
}

private class AndroidViewResourceImageWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<ShapeableImageView>(
        ShapeableImageView(backend.context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        },
    ),
    ResourceImageWidget {
    override fun setImage(value: NativeKitImage) {
        view.setImageResource(value.drawableResId)
    }

    override fun setContentDescription(value: String?) {
        view.contentDescription = value
    }
}

private class AndroidComposeResourceImageWidget :
    AbstractAndroidComposeWidget(),
    ResourceImageWidget {
    private var currentImage: NativeKitImage? by mutableStateOf(null)
    private var currentContentDescription: String? by mutableStateOf(null)

    override fun setImage(value: NativeKitImage) {
        currentImage = value
    }

    override fun setContentDescription(value: String?) {
        currentContentDescription = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        currentImage?.let { image ->
            Image(
                painter = painterResource(image.drawableResId),
                contentDescription = currentContentDescription,
                modifier = composeModifier,
            )
        }
    }
}
