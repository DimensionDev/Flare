// Dirty hack for material 3 bottom sheet navigation
// Google just doesn't want to make a Material 3 bottom sheet navigation,
// just like the Material 3 pull-to-refresh before. Only after I urged them
// at the offline Google I/O 2023 Shanghai event did they hastily release a
// version copied from Material 2 code. And even after more than a year,
// there's still no definition of pull-to-refresh in the Material You
// specification.

@file:SuppressLint("RestrictedApi")

package com.ramcosta.composedestinations.bottomsheet.spec

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.manualcomposablecalls.DestinationLambda
import com.ramcosta.composedestinations.manualcomposablecalls.ManualComposableCalls
import com.ramcosta.composedestinations.manualcomposablecalls.allDeepLinks
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.scope.BottomSheetDestinationScope
import com.ramcosta.composedestinations.scope.BottomSheetNavGraphBuilderDestinationScope
import com.ramcosta.composedestinations.scope.DestinationScopeImpl
import com.ramcosta.composedestinations.scope.NavGraphBuilderDestinationScopeImpl
import com.ramcosta.composedestinations.spec.DestinationStyle
import com.ramcosta.composedestinations.spec.TypedDestinationSpec
import com.stefanoq21.material3.navigation.bottomSheet

object DestinationStyleBottomSheet : DestinationStyle() {
    override fun <T> NavGraphBuilder.addComposable(
        destination: TypedDestinationSpec<T>,
        navController: NavHostController,
        dependenciesContainerBuilder: @Composable DependenciesContainerBuilder<*>.() -> Unit,
        manualComposableCalls: ManualComposableCalls,
    ) {
        @Suppress("UNCHECKED_CAST")
        val contentWrapper = manualComposableCalls[destination.route] as? DestinationLambda<T>?

        bottomSheet(
            route = destination.route,
            arguments = destination.arguments,
            deepLinks = destination.allDeepLinks(manualComposableCalls),
        ) { navBackStackEntry ->
            CallComposable(
                destination,
                navController,
                navBackStackEntry,
                dependenciesContainerBuilder,
                contentWrapper,
            )
        }
    }
}

@Composable
private fun <T> ColumnScope.CallComposable(
    destination: TypedDestinationSpec<T>,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry,
    dependenciesContainerBuilder: @Composable DependenciesContainerBuilder<*>.() -> Unit,
    contentWrapper: DestinationLambda<T>?,
) {
    val scope =
        remember(destination, navBackStackEntry, navController, this, dependenciesContainerBuilder) {
            BottomSheetDestinationScopeImpl(
                destination,
                navBackStackEntry,
                navController,
                this,
                dependenciesContainerBuilder,
            )
        }

    if (contentWrapper == null) {
        with(destination) { scope.Content() }
    } else {
        contentWrapper(scope)
    }
}

internal class BottomSheetDestinationScopeImpl<T>(
    override val destination: TypedDestinationSpec<T>,
    override val navBackStackEntry: NavBackStackEntry,
    override val navController: NavController,
    columnScope: ColumnScope,
    override val dependenciesContainerBuilder: @Composable DependenciesContainerBuilder<*>.() -> Unit,
) : DestinationScopeImpl<T>(),
    BottomSheetDestinationScope<T>,
    ColumnScope by columnScope

internal class BottomSheetNavGraphBuilderDestinationScopeImpl<T>(
    override val destination: TypedDestinationSpec<T>,
    override val navBackStackEntry: NavBackStackEntry,
    columnScope: ColumnScope,
) : NavGraphBuilderDestinationScopeImpl<T>(),
    BottomSheetNavGraphBuilderDestinationScope<T>,
    ColumnScope by columnScope
