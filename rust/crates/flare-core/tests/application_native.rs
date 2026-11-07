#![cfg(target_os = "macos")]

use std::{fs, process::Command, sync::Arc};

use flare_core::application::{InitConfig, InitError, initialize, services};
use flare_datastore::{DataStore, stores::APP_SETTINGS};
use futures::{StreamExt as _, executor::block_on, future::try_join_all};

const SETTINGS: &[u8] = include_bytes!("../../flare-datastore/tests/fixtures/app_settings.pb");

#[cfg(feature = "native-ffi")]
fn initialize_via_callback(config: InitConfig) -> Result<(), InitError> {
    use flare_core::application::{InitializationObserver, begin_initialize};
    use std::{sync::mpsc, time::Duration};

    struct Observer(mpsc::Sender<Result<(), InitError>>);
    impl InitializationObserver for Observer {
        fn on_initialized(&self) {
            let _ = self.0.send(Ok(()));
        }

        fn on_failed(&self, error: InitError) {
            let _ = self.0.send(Err(error));
        }
    }

    let (sender, receiver) = mpsc::channel();
    begin_initialize(config, Arc::new(Observer(sender)))?;
    receiver.recv_timeout(Duration::from_secs(5)).unwrap()
}

#[test]
fn initializes_the_existing_store_and_retries_storage_failures() {
    let Some(user_home) = std::env::var_os("FLARE_APPLICATION_TEST_HOME") else {
        let user_home = tempfile::tempdir().unwrap();
        let status = Command::new(std::env::current_exe().unwrap())
            .args([
                "--exact",
                "initializes_the_existing_store_and_retries_storage_failures",
                "--nocapture",
            ])
            .env("CFFIXED_USER_HOME", user_home.path())
            .env("FLARE_APPLICATION_TEST_HOME", user_home.path())
            .status()
            .unwrap();
        assert!(status.success());
        return;
    };
    let root = std::path::PathBuf::from(user_home).join(".flare");
    block_on(async {
        assert!(matches!(services(), Err(InitError::NotInitialized)));
        fs::write(&root, b"blocked directory").unwrap();
        assert!(matches!(
            initialize(InitConfig::default()).await,
            Err(InitError::DataStore { .. })
        ));
        assert_eq!(fs::read(&root).unwrap(), b"blocked directory");
        assert!(matches!(services(), Err(InitError::NotInitialized)));
        #[cfg(feature = "native-ffi")]
        assert!(matches!(
            initialize_via_callback(InitConfig::default()),
            Err(InitError::DataStore { .. })
        ));
        fs::remove_file(&root).unwrap();
        fs::create_dir(&root).unwrap();
        fs::write(root.join(APP_SETTINGS.name()), SETTINGS).unwrap();

        try_join_all((0..16).map(|_| initialize(InitConfig::default())))
            .await
            .unwrap();
        let first = services().unwrap();
        initialize(InitConfig::default()).await.unwrap();
        #[cfg(feature = "native-ffi")]
        initialize_via_callback(InitConfig::default()).unwrap();
        assert!(Arc::ptr_eq(&first, &services().unwrap()));
        let mut from_application = first.datastore.subscribe(APP_SETTINGS).await.unwrap();
        let store = DataStore::open().await.unwrap();
        let mut from_datastore = store.subscribe(APP_SETTINGS).await.unwrap();
        assert!(Arc::ptr_eq(
            &from_application.next().await.unwrap().unwrap(),
            &from_datastore.next().await.unwrap().unwrap(),
        ));
        assert_eq!(fs::read(root.join(APP_SETTINGS.name())).unwrap(), SETTINGS);
    });
}
