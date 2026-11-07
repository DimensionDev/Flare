"""Give BoltFFI 0.32.1's initialization callback table immutable, stable storage."""

from pathlib import Path
import sys

binding = Path(sys.argv[1])
source = binding.read_text()
replacements = [
    (
        "public protocol InitializationObserver: AnyObject {",
        "public protocol InitializationObserver: AnyObject, Sendable {",
    ),
    (
        "private var boltffiInitializationObserverVtable: ___InitializationObserverVTable = {",
        "private let boltffiInitializationObserverVtable: RustInitializationVtableStorage = {",
    ),
    (
        "    ___InitializationObserverVTable(",
        "    RustInitializationVtableStorage(___InitializationObserverVTable(",
    ),
    (
        "\n    )\n}()\n\nprivate enum InitializationObserverBridge",
        "\n    ))\n}()\n\nprivate enum InitializationObserverBridge",
    ),
    ("&boltffiInitializationObserverVtable", "boltffiInitializationObserverVtable.pointer"),
]
for old, new in replacements:
    if source.count(old) != 1:
        raise SystemExit("BoltFFI's initialization callback changed; review its Swift storage")
    source = source.replace(old, new)
binding.write_text(source)
