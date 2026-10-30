package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.manifest.PluginMethodTableV1
import dev.dimension.flare.feature.plugin.runtime.PluginHostCall
import dev.dimension.flare.feature.plugin.runtime.PluginJavaScriptEngine
import dev.dimension.flare.feature.plugin.runtime.createPluginJavaScriptEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString

internal class PluginRegistrationInspector(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val engineFactory: suspend (PluginHostCall) -> PluginJavaScriptEngine = { createPluginJavaScriptEngine(dispatcher, it) },
) {
    suspend fun inspect(source: String): PluginMethodTableV1 =
        withContext(dispatcher) {
            require(!containsDynamicImport(source)) { "Dynamic import is not supported" }
            val engine = engineFactory { _, _, _ -> error("Host API is unavailable during installation") }
            try {
                val result =
                    withTimeout(VALIDATION_TIMEOUT_MILLIS) {
                        engine.evaluate(registrationPrelude + DISABLE_DYNAMIC_CODE_PRELUDE + source + registrationResult, "plugin.js")
                    }
                PluginJsonV1.decodeFromString(result)
            } finally {
                engine.close()
            }
        }
}

private const val VALIDATION_TIMEOUT_MILLIS = 2_000L

private val registrationPrelude =
    """
    (() => {
      let registration;
      Object.defineProperty(globalThis, "defineFlareSocialPlugin", {
        configurable: false,
        writable: false,
        value(value) {
          if (registration !== undefined) throw new Error("defineFlareSocialPlugin may only be called once");
          if (value === null || typeof value !== "object") throw new Error("Invalid plugin registration");
          registration = value;
        },
      });
      Object.defineProperty(globalThis, "__flareReadRegistrationV1", {
        configurable: false,
        writable: false,
        value() { return registration; },
      });
    })();
    """.trimIndent() + "\n"

private val registrationResult =
    """

    ;(() => {
      const registration = globalThis.__flareReadRegistrationV1();
      if (registration === undefined) throw new Error("plugin.js did not call defineFlareSocialPlugin");
      const methods = [];
      const visit = (value, path) => {
        if (value === null || typeof value !== "object" || Array.isArray(value)) return;
        for (const key of Object.keys(value)) {
          const child = value[key];
          const childPath = path ? `${'$'}{path}.${'$'}{key}` : key;
          if (typeof child === "function") methods.push(childPath);
          else visit(child, childPath);
        }
      };
      visit(registration, "");
      methods.sort();
      return JSON.stringify({ apiVersion: 1, methods });
    })()
    """.trimIndent()
