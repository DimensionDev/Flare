package dev.dimension.flare.feature.plugin.abi

/** Removes JavaScript string-compilation entry points before plugin code is evaluated. */
internal val DISABLE_DYNAMIC_CODE_PRELUDE: String =
    """
    ;(() => {
      const constructors = [
        globalThis.Function,
        Object.getPrototypeOf(async function() {}).constructor,
        Object.getPrototypeOf(function* () {}).constructor,
        Object.getPrototypeOf(async function* () {}).constructor,
      ];
      for (const constructor of constructors) {
        if (typeof constructor === "function" && constructor.prototype) {
          Object.defineProperty(constructor.prototype, "constructor", {
            configurable: false,
            writable: false,
            value: undefined,
          });
        }
      }
      Object.defineProperty(globalThis, "eval", {
        configurable: false,
        writable: false,
        value: undefined,
      });
      Object.defineProperty(globalThis, "Function", {
        configurable: false,
        writable: false,
        value: undefined,
      });
    })();
    """.trimIndent() + "\n"
