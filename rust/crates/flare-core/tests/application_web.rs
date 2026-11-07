#![cfg(target_arch = "wasm32")]

use std::sync::Arc;

use flare_core::application::{InitConfig, InitError, initialize, services};
use flare_datastore::stores::APP_SETTINGS;
use futures::future::try_join_all;
use wasm_bindgen_test::{wasm_bindgen_test, wasm_bindgen_test_configure};

wasm_bindgen_test_configure!(run_in_browser);

#[wasm_bindgen_test]
async fn browser_initialization_is_shared_and_survives_cancellation() {
    assert!(matches!(services(), Err(InitError::NotInitialized)));
    assert!(matches!(
        initialize(InitConfig {
            android_files_dir: Some("/android-only".into())
        })
        .await,
        Err(InitError::InvalidConfiguration { .. })
    ));
    let mut cancelled = Box::pin(initialize(InitConfig::default()));
    assert!(futures::poll!(cancelled.as_mut()).is_pending());
    drop(cancelled);
    try_join_all((0..16).map(|_| initialize(InitConfig::default())))
        .await
        .unwrap();
    let first = services().unwrap();
    assert_eq!(
        first
            .datastore
            .read(APP_SETTINGS)
            .await
            .unwrap()
            .version
            .as_deref(),
        Some("")
    );
    initialize(InitConfig::default()).await.unwrap();
    assert!(Arc::ptr_eq(&first, &services().unwrap()));
}
