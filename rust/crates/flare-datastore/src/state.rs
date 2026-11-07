use std::{
    any::Any,
    collections::HashMap,
    sync::{Arc, Mutex, OnceLock, PoisonError},
};

use tokio::sync::watch;

use crate::{Error, StoreKey, WireMessage};

#[derive(Clone)]
pub(crate) struct Snapshot<T> {
    pub(crate) value: Result<Arc<T>, Arc<Error>>,
    bytes: Option<Arc<[u8]>>,
}

#[derive(Default)]
pub(crate) struct Records {
    values: Mutex<HashMap<&'static str, Arc<dyn StoredRecord>>>,
}

impl Records {
    pub(crate) fn get<T: WireMessage>(&self, key: &StoreKey<T>) -> Arc<Record<T>> {
        let mut values = self.values.lock().unwrap_or_else(PoisonError::into_inner);
        let value = values.entry(key.name).or_insert_with(|| {
            Arc::new(Record::<T> {
                #[cfg(target_arch = "wasm32")]
                key: key.clone(),
                value: OnceLock::new(),
            })
        });
        let erased = Arc::clone(value);
        let erased: Arc<dyn Any + Send + Sync> = erased;
        erased
            .downcast()
            .expect("a fixed StoreKey has one message type")
    }

    #[cfg(target_arch = "wasm32")]
    pub(crate) fn find(&self, name: &str) -> Option<(&'static str, Arc<dyn StoredRecord>)> {
        self.values
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .get_key_value(name)
            .map(|(&name, record)| (name, Arc::clone(record)))
    }

    #[cfg(target_arch = "wasm32")]
    pub(crate) fn all(&self) -> Vec<(&'static str, Arc<dyn StoredRecord>)> {
        self.values
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .iter()
            .map(|(&name, record)| (name, Arc::clone(record)))
            .collect()
    }
}

pub(crate) struct Record<T> {
    #[cfg(target_arch = "wasm32")]
    key: StoreKey<T>,
    value: OnceLock<watch::Sender<Snapshot<T>>>,
}

impl<T: WireMessage> Record<T> {
    pub(crate) fn current(&self) -> Option<Arc<T>> {
        self.value.get()?.borrow().value.as_ref().ok().cloned()
    }

    pub(crate) fn receiver(&self) -> watch::Receiver<Snapshot<T>> {
        self.value
            .get()
            .expect("successful reads initialize the record")
            .subscribe()
    }

    pub(crate) fn publish(&self, value: Arc<T>, bytes: Option<Arc<[u8]>>) {
        let snapshot = Snapshot {
            value: Ok(value),
            bytes,
        };
        let sender = self
            .value
            .get_or_init(|| watch::Sender::new(snapshot.clone()));
        sender.send_if_modified(|current| {
            if current.value.is_ok() && current.bytes == snapshot.bytes {
                return false;
            }
            *current = snapshot;
            true
        });
    }
}

pub(crate) trait StoredRecord: Any + Send + Sync {
    #[cfg(target_arch = "wasm32")]
    fn prepare(self: Arc<Self>, bytes: Option<Vec<u8>>) -> Result<Box<dyn FnOnce() + Send>, Error>;

    #[cfg(target_arch = "wasm32")]
    fn fail(&self, error: Error);
}

impl<T: WireMessage> StoredRecord for Record<T> {
    #[cfg(target_arch = "wasm32")]
    fn prepare(self: Arc<Self>, bytes: Option<Vec<u8>>) -> Result<Box<dyn FnOnce() + Send>, Error> {
        let value = Arc::new(self.key.decode(bytes.as_deref())?);
        let bytes = bytes.map(Arc::<[u8]>::from);
        Ok(Box::new(move || self.publish(value, bytes)))
    }

    #[cfg(target_arch = "wasm32")]
    fn fail(&self, error: Error) {
        if let Some(value) = self.value.get() {
            value.send_modify(|current| current.value = Err(Arc::new(error)));
        }
    }
}
