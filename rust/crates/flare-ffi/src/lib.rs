//! Native library containing the `ViewModel` exports from `flare-core`.

// Keep the dependency linked even though BoltFFI calls its exports by symbol.
extern crate flare_core as _;

boltffi::scaffolding!();
