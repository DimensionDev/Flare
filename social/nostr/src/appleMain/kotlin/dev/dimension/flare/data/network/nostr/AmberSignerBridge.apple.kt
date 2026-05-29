@file:Suppress("ktlint:standard:filename")

package dev.dimension.flare.data.network.nostr

import org.koin.core.annotation.Single

@Single(binds = [AmberSignerBridge::class])
internal class AppleAmberSignerBridge : AmberSignerBridge by UnsupportedAmberSignerBridge("Amber signer is only available on Android.")
