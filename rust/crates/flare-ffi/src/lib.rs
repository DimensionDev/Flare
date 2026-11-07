//! Application initialization and `ViewModel` exports for UI hosts.

// Keep the dependency linked even though BoltFFI calls its exports by symbol.
extern crate flare_core as _;

#[cfg(not(target_arch = "wasm32"))]
boltffi::scaffolding!();

/// Initialize this browser's Rust application after loading its Wasm module.
///
/// # Errors
///
/// Rejects the returned Promise if application initialization fails.
#[cfg(target_arch = "wasm32")]
#[wasm_bindgen::prelude::wasm_bindgen]
pub async fn initialize() -> Result<(), wasm_bindgen::JsValue> {
    flare_core::application::initialize(flare_core::application::InitConfig::default())
        .await
        .map_err(|error| js_sys::Error::new(&error.to_string()).into())
}
