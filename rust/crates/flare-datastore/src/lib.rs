//! Typed persistence for Flare's current `DataStore` records.
//!
//! [`DataStore::open`] uses the existing app data directory on native platforms
//! and the current origin's `IndexedDB` database on Web.

#[cfg(not(target_arch = "wasm32"))]
#[path = "native.rs"]
mod backend;
#[cfg(target_arch = "wasm32")]
#[path = "web.rs"]
mod backend;
mod codec;
#[cfg(not(target_arch = "wasm32"))]
mod directory;
pub mod model;
mod state;
pub mod stores;
mod subscription;

use backend::Backend;
pub use codec::{ProtoError, WireMessage};
use std::sync::Arc;
pub use subscription::Subscription;

/// A record's fixed name, Protobuf type and value when it has never been saved.
#[derive(Clone)]
pub struct StoreKey<T> {
    name: &'static str,
    default_value: fn() -> T,
}

impl<T: WireMessage> StoreKey<T> {
    const fn new(name: &'static str) -> Self {
        Self::with_default(name, T::default)
    }

    const fn with_default(name: &'static str, default_value: fn() -> T) -> Self {
        Self {
            name,
            default_value,
        }
    }

    /// The native file name and the Web record key.
    #[must_use]
    pub const fn name(&self) -> &'static str {
        self.name
    }

    fn decode(&self, bytes: Option<&[u8]>) -> Result<T, Error> {
        match bytes {
            Some(bytes) => Ok(T::parse_from_bytes(bytes)?),
            None => Ok((self.default_value)()),
        }
    }
}

/// Failure to access, decode or commit a record.
#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("DataStore Protobuf error")]
    Protobuf(#[from] ProtoError),
    #[cfg(not(target_arch = "wasm32"))]
    #[error("DataStore file I/O failed")]
    Io(#[from] std::io::Error),
    #[cfg(not(target_arch = "wasm32"))]
    #[error("DataStore directory error ({0})")]
    Directory(&'static str),
    #[cfg(target_arch = "wasm32")]
    #[error("DataStore IndexedDB operation failed ({0})")]
    IndexedDb(String),
    #[cfg(target_arch = "wasm32")]
    #[error("DataStore browser operation failed ({0})")]
    Browser(String),
    #[cfg(target_arch = "wasm32")]
    #[error("DataStore IndexedDB record is not a byte array")]
    InvalidRecord,
    #[error("DataStore update was aborted")]
    Aborted,
}

/// The Rust-only entry point for all Flare stores.
#[derive(Clone)]
pub struct DataStore {
    backend: Backend,
}

impl DataStore {
    /// Open the shared default Flare store for this platform.
    ///
    /// On Android, the runtime must register `Context.getFilesDir()` once
    /// using `Self::initialize_android` before opening a store.
    ///
    /// # Errors
    ///
    /// Returns an error if the native directory cannot be resolved or the
    /// browser cannot open or initialize `IndexedDB`.
    pub async fn open() -> Result<Self, Error> {
        Ok(Self {
            backend: Backend::open().await?,
        })
    }

    /// Open a shared store in an explicit native directory.
    /// Handles for the same canonical directory share committed state.
    ///
    /// # Errors
    ///
    /// Returns an error if the directory cannot be created or resolved.
    #[cfg(not(target_arch = "wasm32"))]
    pub async fn open_in(root: impl Into<std::path::PathBuf>) -> Result<Self, Error> {
        Ok(Self {
            backend: Backend::open_in(root.into()).await?,
        })
    }

    /// Register Android's application files directory at runtime startup.
    /// The store uses the `datastore` child of this directory.
    ///
    /// # Errors
    ///
    /// Returns an error for a relative path or an attempt to change the
    /// directory after it has been registered.
    #[cfg(target_os = "android")]
    pub fn initialize_android(files_dir: impl AsRef<std::path::Path>) -> Result<(), Error> {
        directory::initialize_android(files_dir.as_ref())
    }

    /// Read the cached committed snapshot, loading the record on first use.
    /// Only a missing record uses the store's default.
    ///
    /// # Errors
    ///
    /// Returns an error if the record is unreadable or invalid.
    pub async fn read<T: WireMessage>(&self, key: StoreKey<T>) -> Result<T, Error> {
        Ok((*self.snapshot(key).await?).clone())
    }

    /// Observe the current committed snapshot and subsequent changes.
    /// Slow consumers receive the latest state. Dropping the stream releases
    /// the subscription. A browser refresh error is emitted as a stream item,
    /// and a later successful refresh or update can recover the stream.
    ///
    /// # Errors
    ///
    /// Returns an error if the initial record cannot be read or decoded.
    pub async fn subscribe<T: WireMessage>(
        &self,
        key: StoreKey<T>,
    ) -> Result<Subscription<T>, Error> {
        let record = self.backend.records().get(&key);
        self.snapshot(key).await?;
        Ok(Subscription::new(record.receiver(), self.clone()))
    }

    /// Edit the latest record in a single transaction and return the edit's
    /// result after commit and publication. The edit runs once and must not
    /// reenter `DataStore`.
    ///
    /// A queued update completes its commit and publication even if the
    /// caller stops waiting for it.
    ///
    /// # Errors
    ///
    /// An edit error aborts the write. Access, decoding and commit errors are
    /// returned without replacing the previous record.
    pub async fn update<T, R>(
        &self,
        key: StoreKey<T>,
        edit: impl FnOnce(&mut T) -> Result<R, Error> + Send + 'static,
    ) -> Result<R, Error>
    where
        T: WireMessage,
        R: Send + 'static,
    {
        let state = self.backend.records().get(&key);
        self.backend
            .update(key.name, move |bytes| {
                let mut record = key.decode(bytes.as_deref())?;
                let before = record.write_to_bytes()?;
                let result = edit(&mut record)?;
                let after = record.write_to_bytes()?;
                let replacement = (before != after).then_some(after);
                let committed = replacement
                    .as_deref()
                    .or(bytes.as_deref())
                    .map(Arc::<[u8]>::from);
                let snapshot = Arc::new(record);
                Ok((result, replacement, move || {
                    state.publish(snapshot, committed);
                }))
            })
            .await
    }

    async fn snapshot<T: WireMessage>(&self, key: StoreKey<T>) -> Result<Arc<T>, Error> {
        let state = self.backend.records().get(&key);
        if let Some(value) = state.current() {
            return Ok(value);
        }
        self.backend
            .update(key.name, move |bytes| {
                let snapshot = Arc::new(key.decode(bytes.as_deref())?);
                let result = Arc::clone(&snapshot);
                let committed = bytes.map(Arc::<[u8]>::from);
                Ok((result, None, move || state.publish(snapshot, committed)))
            })
            .await
    }
}

#[cfg(target_arch = "wasm32")]
impl From<idb::Error> for Error {
    fn from(error: idb::Error) -> Self {
        // Keep JS objects in the browser task so store futures remain Send.
        Self::IndexedDb(error.to_string())
    }
}

#[cfg(target_arch = "wasm32")]
impl From<wasm_bindgen::JsValue> for Error {
    fn from(error: wasm_bindgen::JsValue) -> Self {
        Self::Browser(format!("{error:?}"))
    }
}
