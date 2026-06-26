package dev.dimension.flare.di

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools
import kotlin.native.HiddenFromObjC

@PublishedApi
internal object KoinBridge : KoinComponent

@HiddenFromObjC
public inline fun <reified T : Any> koinInject(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
    noinline parameters: ParametersDefinition? = null,
): Lazy<T> =
    KoinBridge.inject(
        qualifier = qualifier,
        mode = mode,
        parameters = parameters,
    )

@HiddenFromObjC
public inline fun <reified T : Any> koinGet(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T =
    KoinBridge.get(
        qualifier = qualifier,
        parameters = parameters,
    )
