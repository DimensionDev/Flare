package sh.christian.ozone

import kotlinx.serialization.json.Json
import sh.christian.ozone.api.runtime.buildXrpcJsonConfiguration
import sh.christian.ozone.api.xrpc.XrpcSerializersModule

public val BlueskyJson: Json = buildXrpcJsonConfiguration(XrpcSerializersModule)
