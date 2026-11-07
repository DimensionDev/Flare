#![cfg(target_arch = "wasm32")]

use std::sync::Arc;

use flare_core::application::services;
use wasm_bindgen_test::{wasm_bindgen_test, wasm_bindgen_test_configure};

wasm_bindgen_test_configure!(run_in_browser);

#[wasm_bindgen_test]
async fn initializes_through_the_browser_export() {
    flare_ffi::initialize().await.unwrap();
    let first = services().unwrap();
    flare_ffi::initialize().await.unwrap();
    assert!(Arc::ptr_eq(&first, &services().unwrap()));
}
