//! Application startup shared by all UI hosts and Rust modules.

use std::{
    future::Future,
    sync::{Arc, Mutex, PoisonError},
};

use flare_datastore::DataStore;
use tokio::sync::watch;

static APPLICATION: Application = Application::new();

/// Platform information that Rust cannot discover itself.
#[cfg_attr(feature = "native-ffi", boltffi::data)]
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct InitConfig {
    /// Android's application `Context.getFilesDir()`. Leave absent elsewhere.
    pub android_files_dir: Option<String>,
}

impl InitConfig {
    fn validate(&self) -> Result<(), InitError> {
        #[cfg(target_os = "android")]
        if !self
            .android_files_dir
            .as_deref()
            .is_some_and(|path| std::path::Path::new(path).is_absolute())
        {
            return Err(InitError::InvalidConfiguration {
                message: "Android requires an absolute application filesDir".into(),
            });
        }
        #[cfg(not(target_os = "android"))]
        if self.android_files_dir.is_some() {
            return Err(InitError::InvalidConfiguration {
                message: "android_files_dir is only valid on Android".into(),
            });
        }
        Ok(())
    }
}

/// A startup failure that can be handled by the UI host.
#[cfg_attr(feature = "native-ffi", boltffi::error)]
#[derive(Clone, Debug, PartialEq, Eq, thiserror::Error)]
pub enum InitError {
    #[error("Invalid Rust initialization configuration: {message}")]
    InvalidConfiguration { message: String },
    #[error("Rust initialization has already accepted a different configuration")]
    ConfigurationConflict,
    #[error("Rust application services are not initialized")]
    NotInitialized,
    #[error("Rust DataStore initialization failed: {message}")]
    DataStore { message: String },
    #[error("Rust background executor could not start: {message}")]
    Executor { message: String },
    #[error("Rust initialization stopped before producing a result")]
    Interrupted,
}

impl From<flare_datastore::Error> for InitError {
    fn from(error: flare_datastore::Error) -> Self {
        use std::error::Error as _;

        let mut message = error.to_string();
        let mut source = error.source();
        while let Some(cause) = source {
            message.push_str(": ");
            message.push_str(&cause.to_string());
            source = cause.source();
        }
        Self::DataStore { message }
    }
}

/// Shared Rust dependencies. These are never exported to a UI host.
pub struct AppServices {
    pub datastore: DataStore,
}

impl AppServices {
    async fn build(config: InitConfig) -> Result<Arc<Self>, InitError> {
        #[cfg(target_os = "android")]
        DataStore::initialize_android(config.android_files_dir.as_deref().ok_or_else(|| {
            InitError::InvalidConfiguration {
                message: "Android application filesDir is missing".into(),
            }
        })?)?;
        #[cfg(not(target_os = "android"))]
        let _ = config;

        let datastore = DataStore::open().await?;
        Ok(Arc::new(Self { datastore }))
    }
}

/// Initialize the shared Rust application without blocking the UI thread.
///
/// Concurrent calls share one attempt. Success is reused for this process
/// (or WebAssembly instance). A failed attempt can be retried with the same
/// configuration. Once accepted, the configuration cannot be changed.
/// Dropping a polled initialization future only cancels that caller's wait.
///
/// Currently this opens `DataStore`; individual records are decoded on demand.
///
/// # Errors
///
/// Returns an error for invalid or conflicting configuration, unavailable
/// platform storage, or a background task that could not finish.
pub async fn initialize(config: InitConfig) -> Result<(), InitError> {
    config.validate()?;
    APPLICATION
        .initialize(config.clone(), AppServices::build(config))
        .await
        .map(|_| ())
}

/// Obtain the initialized dependencies for a Rust `ViewModel` or module.
///
/// # Errors
///
/// Returns `InitError::NotInitialized` until startup has succeeded.
pub fn services() -> Result<Arc<AppServices>, InitError> {
    APPLICATION.services()
}

/// The native host's completion callback, owned by Rust until the result arrives.
#[cfg(feature = "native-ffi")]
#[boltffi::export]
pub trait InitializationObserver: Send + Sync {
    fn on_initialized(&self);
    fn on_failed(&self, error: InitError);
}

/// Start native initialization and deliver its result through an owned callback.
/// Generated host helpers expose this as an ordinary async/suspend function.
///
/// # Errors
///
/// Returns an error for rejected configuration or unavailable task dispatch.
#[cfg(feature = "native-ffi")]
#[boltffi::export]
pub fn begin_initialize(
    config: InitConfig,
    observer: Arc<dyn InitializationObserver>,
) -> Result<(), InitError> {
    config.validate()?;
    let attempt = APPLICATION.start(config.clone(), AppServices::build(config))?;
    spawn(async move {
        match wait_for_attempt(attempt).await {
            Ok(_) => observer.on_initialized(),
            Err(error) => observer.on_failed(error),
        }
    })
}

type InitResult = Result<Arc<AppServices>, InitError>;
type Attempt = watch::Receiver<Option<InitResult>>;

struct Application {
    state: Mutex<State>,
}

struct State {
    config: Option<InitConfig>,
    attempt: Option<Attempt>,
}

impl Application {
    const fn new() -> Self {
        Self {
            state: Mutex::new(State {
                config: None,
                attempt: None,
            }),
        }
    }

    async fn initialize(
        &self,
        config: InitConfig,
        build: impl Future<Output = InitResult> + Send + 'static,
    ) -> InitResult {
        wait_for_attempt(self.start(config, build)?).await
    }

    fn start(
        &self,
        config: InitConfig,
        build: impl Future<Output = InitResult> + Send + 'static,
    ) -> Result<Attempt, InitError> {
        let (receiver, start) = {
            let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
            if state.config.as_ref().is_some_and(|value| value != &config) {
                return Err(InitError::ConfigurationConflict);
            }
            state.config = Some(config);
            let reusable = state.attempt.as_ref().filter(|attempt| {
                let result = attempt.borrow();
                matches!(&*result, Some(Ok(_)))
                    || (result.is_none() && attempt.has_changed().is_ok())
            });
            if let Some(attempt) = reusable {
                (attempt.clone(), None)
            } else {
                let (sender, receiver) = watch::channel(None);
                state.attempt = Some(receiver.clone());
                (receiver, Some(sender))
            }
        };

        if let Some(sender) = start {
            let failure = sender.clone();
            if let Err(error) = spawn(async move {
                sender.send_replace(Some(build.await));
            }) {
                failure.send_replace(Some(Err(error)));
            }
        }

        Ok(receiver)
    }

    fn services(&self) -> InitResult {
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        state
            .attempt
            .as_ref()
            .and_then(|attempt| {
                attempt
                    .borrow()
                    .as_ref()
                    .and_then(|value| value.as_ref().ok().cloned())
            })
            .ok_or(InitError::NotInitialized)
    }
}

async fn wait_for_attempt(mut receiver: Attempt) -> InitResult {
    loop {
        if let Some(result) = receiver.borrow_and_update().clone() {
            return result;
        }
        receiver
            .changed()
            .await
            .map_err(|_| InitError::Interrupted)?;
    }
}

#[cfg(not(target_arch = "wasm32"))]
fn spawn(future: impl Future<Output = ()> + Send + 'static) -> Result<(), InitError> {
    use tokio::runtime::{Builder, Runtime};

    static EXECUTOR: Mutex<Option<Runtime>> = Mutex::new(None);
    let handle = {
        let mut executor = EXECUTOR.lock().unwrap_or_else(PoisonError::into_inner);
        if executor.is_none() {
            *executor = Some(
                Builder::new_multi_thread()
                    .worker_threads(1)
                    .thread_name("flare-runtime")
                    .enable_all()
                    .build()
                    .map_err(|error| InitError::Executor {
                        message: error.to_string(),
                    })?,
            );
        }
        executor
            .as_ref()
            .expect("the executor was created")
            .handle()
            .clone()
    };
    handle.spawn(future);
    Ok(())
}

#[cfg(target_arch = "wasm32")]
#[expect(
    clippy::unnecessary_wraps,
    reason = "native executor creation can fail"
)]
fn spawn(future: impl Future<Output = ()> + Send + 'static) -> Result<(), InitError> {
    wasm_bindgen_futures::spawn_local(future);
    Ok(())
}

#[cfg(all(test, not(target_arch = "wasm32")))]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    use futures::{FutureExt as _, executor::block_on};
    use tokio::sync::oneshot;

    fn fixture() -> (tempfile::TempDir, Arc<AppServices>) {
        let directory = tempfile::tempdir().unwrap();
        let datastore = block_on(DataStore::open_in(directory.path())).unwrap();
        (directory, Arc::new(AppServices { datastore }))
    }

    #[test]
    fn cancelled_waiters_do_not_cancel_shared_startup() {
        let (_directory, expected) = fixture();
        let application = Application::new();
        let builds = Arc::new(AtomicUsize::new(0));
        block_on(async {
            assert!(matches!(
                application.services(),
                Err(InitError::NotInitialized)
            ));
            let (started, started_receiver) = oneshot::channel();
            let (release, released) = oneshot::channel();
            let count = Arc::clone(&builds);
            let value = Arc::clone(&expected);
            let mut first = Box::pin(application.initialize(InitConfig::default(), async move {
                count.fetch_add(1, Ordering::SeqCst);
                started.send(()).unwrap();
                released.await.unwrap();
                Ok(value)
            }));
            assert!(futures::poll!(first.as_mut()).is_pending());
            started_receiver.await.unwrap();
            drop(first);
            assert!(matches!(
                application.services(),
                Err(InitError::NotInitialized)
            ));

            let mut second = Box::pin(application.initialize(InitConfig::default(), async {
                panic!("a second caller must join the active attempt")
            }));
            assert!(futures::poll!(second.as_mut()).is_pending());
            release.send(()).unwrap();
            assert!(Arc::ptr_eq(&second.await.unwrap(), &expected));
            assert_eq!(builds.load(Ordering::SeqCst), 1);
            assert!(Arc::ptr_eq(&application.services().unwrap(), &expected));

            let repeated = application.initialize(InitConfig::default(), async {
                panic!("successful startup must not run again")
            });
            assert!(Arc::ptr_eq(
                &repeated.now_or_never().unwrap().unwrap(),
                &expected,
            ));
        });
    }

    #[test]
    fn failed_attempts_share_their_result_even_after_a_retry() {
        let (_directory, expected) = fixture();
        let application = Application::new();
        let failure = InitError::DataStore {
            message: "storage unavailable".into(),
        };
        block_on(async {
            let (release, released) = oneshot::channel();
            let error = failure.clone();
            let mut first = Box::pin(application.initialize(InitConfig::default(), async move {
                released.await.unwrap();
                Err(error)
            }));
            let mut second = Box::pin(application.initialize(InitConfig::default(), async {
                panic!("concurrent callers must receive the same attempt's result")
            }));
            assert!(futures::poll!(first.as_mut()).is_pending());
            assert!(futures::poll!(second.as_mut()).is_pending());
            release.send(()).unwrap();
            assert!(matches!(first.await, Err(error) if error == failure));
            assert!(matches!(
                application.services(),
                Err(InitError::NotInitialized)
            ));
            let value = Arc::clone(&expected);
            let retried = application
                .initialize(InitConfig::default(), async move { Ok(value) })
                .await
                .unwrap();
            assert!(Arc::ptr_eq(&retried, &expected));
            assert!(matches!(second.await, Err(error) if error == failure));
            assert!(Arc::ptr_eq(&application.services().unwrap(), &expected));
        });
    }

    #[test]
    fn accepted_configuration_cannot_change_during_or_after_startup() {
        let (_directory, expected) = fixture();
        let application = Application::new();
        // Exercise the coordinator independently of platform-specific validation.
        let different = InitConfig {
            android_files_dir: Some("/another-app".into()),
        };
        block_on(async {
            let (release, released) = oneshot::channel();
            let value = Arc::clone(&expected);
            let mut pending = Box::pin(application.initialize(InitConfig::default(), async move {
                released.await.unwrap();
                Ok(value)
            }));
            assert!(futures::poll!(pending.as_mut()).is_pending());
            let result = application
                .initialize(different.clone(), async {
                    panic!("conflicting configuration must not construct modules")
                })
                .await;
            assert!(matches!(result, Err(InitError::ConfigurationConflict)));
            release.send(()).unwrap();
            pending.await.unwrap();
            let result = application
                .initialize(different, async {
                    panic!("successful startup must keep its configuration")
                })
                .await;
            assert!(matches!(result, Err(InitError::ConfigurationConflict)));
        });
    }

    #[test]
    fn interrupted_attempts_can_retry() {
        let (_directory, expected) = fixture();
        let application = Application::new();
        block_on(async {
            assert!(matches!(
                application
                    .initialize(InitConfig::default(), async {
                        panic!("simulate an interrupted module constructor")
                    })
                    .await,
                Err(InitError::Interrupted)
            ));
            let value = Arc::clone(&expected);
            let result = application
                .initialize(InitConfig::default(), async move { Ok(value) })
                .await
                .unwrap();
            assert!(Arc::ptr_eq(&result, &expected));
        });
    }

    #[test]
    fn invalid_configuration_does_not_start_the_application() {
        fn require_send<T: Send>(_: T) {}
        require_send(initialize(InitConfig::default()));
        #[cfg(not(target_os = "android"))]
        let invalid = InitConfig {
            android_files_dir: Some("/android-only".into()),
        };
        #[cfg(target_os = "android")]
        let invalid = InitConfig {
            android_files_dir: Some("relative".into()),
        };
        assert!(matches!(
            block_on(initialize(invalid)),
            Err(InitError::InvalidConfiguration { .. })
        ));
        assert!(matches!(services(), Err(InitError::NotInitialized)));
    }
}
