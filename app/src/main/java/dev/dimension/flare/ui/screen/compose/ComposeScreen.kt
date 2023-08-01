package dev.dimension.flare.ui.screen.compose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.theme.FlareTheme

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun ComposeScreenPreview() {
    ComposeScreen(onBack = {})
}

@Destination
@Composable
fun ComposeRoute(
    navigator: DestinationsNavigator
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        }
    )
}

object ComposeTransitions : DestinationStyle.Animated {
    override fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition? {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Up
        ) + fadeIn()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition? {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Up
        ) + fadeOut()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition(): EnterTransition? {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Down
        ) + fadeIn()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition(): ExitTransition? {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Down
        ) + fadeOut()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by producePresenter {
        composePresenter()
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    FlareTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.compose_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /*TODO*/ }) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        IconButton(onClick = { /*TODO*/ }) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null)
                        }
                        IconButton(onClick = { /*TODO*/ }) {
                            Icon(imageVector = Icons.Default.Poll, contentDescription = null)
                        }
                        IconButton(onClick = { /*TODO*/ }) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(it)
                    .fillMaxSize()
            ) {
                TextField(
                    value = state.text,
                    onValueChange = state::onTextChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(
                            focusRequester = focusRequester
                        ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(text = stringResource(id = R.string.compose_hint))
                    }
                )
            }
        }
    }
}

@Composable
private fun composePresenter() = run {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    object {
        val text = text
        fun onTextChange(value: TextFieldValue) {
            text = value
        }
    }
}
