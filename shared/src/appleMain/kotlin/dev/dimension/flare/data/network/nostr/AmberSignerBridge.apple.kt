@file:Suppress("ktlint:standard:filename")

package dev.dimension.flare.data.network.nostr

internal class AppleAmberSignerBridge : AmberSignerBridge by UnsupportedAmberSignerBridge("Amber signer is only available on Android.")
