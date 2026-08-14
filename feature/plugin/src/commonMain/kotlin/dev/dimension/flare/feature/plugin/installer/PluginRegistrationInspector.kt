package dev.dimension.flare.feature.plugin.installer

import com.dokar.quickjs.QuickJs
import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.manifest.PluginMethodTableV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString

internal class PluginRegistrationInspector(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun inspect(source: String): PluginMethodTableV1 {
        require(!DYNAMIC_IMPORT.containsMatchIn(source)) { "Dynamic import is not supported" }
        val quickJs = QuickJs.create(dispatcher)
        try {
            quickJs.memoryLimit = VALIDATION_MEMORY_BYTES
            quickJs.maxStackSize = VALIDATION_STACK_BYTES
            quickJs.evaluationTimeoutMillis = VALIDATION_TIMEOUT_MILLIS
            val result =
                withTimeout(VALIDATION_TIMEOUT_MILLIS) {
                    quickJs.evaluate<String>(registrationPrelude + DISABLE_DYNAMIC_CODE_PRELUDE + source + registrationResult)
                }
            return PluginJsonV1.decodeFromString(result)
        } finally {
            quickJs.close()
        }
    }
}

private const val VALIDATION_MEMORY_BYTES = 16L * 1024 * 1024
private const val VALIDATION_STACK_BYTES = 256L * 1024
private const val VALIDATION_TIMEOUT_MILLIS = 2_000L
private val DYNAMIC_IMPORT = Regex("\\bimport\\s*\\(")

private val registrationPrelude =
    """
    (() => {
      let registration;
      Object.defineProperty(globalThis, "definePlugin", {
        configurable: false,
        writable: false,
        value(value) {
          if (registration !== undefined) throw new Error("definePlugin may only be called once");
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
      if (registration === undefined) throw new Error("plugin.js did not call definePlugin");
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
