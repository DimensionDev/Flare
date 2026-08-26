package dev.dimension.flare.ui.resources.moko

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.compose.ui.res.painterResource
import com.google.android.material.imageview.ShapeableImageView
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.android.AbstractAndroidWidget
import dev.dimension.flare.ui.android.AndroidViewBackend
import dev.dimension.flare.ui.compose.AbstractAndroidComposeWidget
import dev.dimension.flare.ui.compose.AndroidComposeBackend

/** Installs [ResourceImage] for the Android View backend. */
public object AndroidViewMokoResourcesRendererPlugin : FlareRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(ResourceImageWidget::class) { backend ->
            AndroidViewResourceImageWidget(backend)
        }
    }
}

/** Installs [ResourceImage] for the Android Compose backend. */
public object AndroidComposeMokoResourcesRendererPlugin : FlareRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidComposeBackend>) {
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
    override fun setImage(value: FlareImage) {
        view.setImageResource(value.drawableResId)
    }

    override fun setContentDescription(value: String?) {
        view.contentDescription = value
    }
}

private class AndroidComposeResourceImageWidget :
    AbstractAndroidComposeWidget(),
    ResourceImageWidget {
    private var currentImage: FlareImage? by mutableStateOf(null)
    private var currentContentDescription: String? by mutableStateOf(null)

    override fun setImage(value: FlareImage) {
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
