use std::{cell::Cell, future::Future, pin::Pin, rc::Rc, sync::Arc};

use idb::{Database, Event as _, Factory, ObjectStoreParams, Request as _, TransactionMode};
use js_sys::Uint8Array;
use tokio::sync::{OnceCell, mpsc, oneshot};
use wasm_bindgen::{JsCast as _, JsValue, closure::Closure};
use web_sys::{BroadcastChannel, Document, Event, MessageEvent, Window};

use crate::{
    Error,
    state::{Records, StoredRecord},
};

const DATABASE: &str = "flare-datastore";
const RECORDS: &str = "records";

type Request = Box<dyn FnOnce(Rc<Connection>) -> Pin<Box<dyn Future<Output = ()>>> + Send>;

thread_local! {
    static DEFAULT: Rc<OnceCell<Backend>> = Rc::new(OnceCell::const_new());
}

#[derive(Clone)]
pub(crate) struct Backend {
    inner: Arc<Inner>,
}

struct Inner {
    requests: mpsc::UnboundedSender<Request>,
    records: Records,
}

impl Backend {
    pub(crate) async fn open() -> Result<Self, Error> {
        let (sender, receiver) = oneshot::channel();
        DEFAULT.with(|default| {
            let default = Rc::clone(default);
            wasm_bindgen_futures::spawn_local(async move {
                let result = default.get_or_try_init(Self::connect).await.cloned();
                let _ = sender.send(result);
            });
        });
        receiver.await.unwrap_or(Err(Error::Aborted))
    }

    async fn connect() -> Result<Self, Error> {
        let (sender, mut receiver) = mpsc::unbounded_channel::<Request>();
        let inner = Arc::new(Inner {
            requests: sender,
            records: Records::default(),
        });
        let connection = Rc::new(Connection::open(&inner).await?);
        wasm_bindgen_futures::spawn_local(async move {
            while let Some(request) = receiver.recv().await {
                request(Rc::clone(&connection)).await;
            }
        });
        Ok(Self { inner })
    }

    pub(crate) fn records(&self) -> &Records {
        &self.inner.records
    }

    pub(crate) async fn update<R: Send + 'static, C: FnOnce() + Send + 'static>(
        &self,
        name: &'static str,
        edit: impl FnOnce(Option<Vec<u8>>) -> Result<(R, Option<Vec<u8>>, C), Error> + Send + 'static,
    ) -> Result<R, Error> {
        let (sender, receiver) = oneshot::channel();
        self.inner
            .requests
            .send(Box::new(move |connection| {
                Box::pin(async move {
                    let result = connection.update(name, edit).await;
                    let _ = sender.send(result);
                })
            }))
            .map_err(|_| Error::Aborted)?;
        receiver.await.unwrap_or(Err(Error::Aborted))
    }
}

struct Connection {
    database: Database,
    notifications: BroadcastChannel,
    on_message: Closure<dyn FnMut(MessageEvent)>,
    on_resume: Closure<dyn FnMut(Event)>,
    window: Option<Window>,
    document: Option<Document>,
}

impl Connection {
    async fn open(inner: &Arc<Inner>) -> Result<Self, Error> {
        let mut request = Factory::new()?.open(DATABASE, Some(1))?;
        request.on_upgrade_needed(|event| {
            if let Ok(request) = event.target()
                && request
                    .database()
                    .and_then(|db| db.create_object_store(RECORDS, ObjectStoreParams::new()))
                    .is_err()
                && let Some(transaction) = request.transaction()
            {
                let _ = transaction.abort();
            }
        });
        let database = request.await?;
        database.transaction(&[RECORDS], TransactionMode::ReadOnly)?;
        let notifications = BroadcastChannel::new(DATABASE)?;
        let weak = Arc::downgrade(inner);
        let on_message = Closure::new(move |event: MessageEvent| {
            if let Some(inner) = weak.upgrade()
                && let Some(name) = event.data().as_string()
                && let Some(record) = inner.records.find(&name)
            {
                refresh(&inner, [record]);
            }
        });
        let weak = Arc::downgrade(inner);
        let on_resume = Closure::new(move |_: Event| {
            if let Some(inner) = weak.upgrade() {
                refresh(&inner, inner.records.all());
            }
        });
        let window = web_sys::window();
        let document = window.as_ref().and_then(Window::document);
        let connection = Self {
            database,
            notifications,
            on_message,
            on_resume,
            window,
            document,
        };
        connection
            .notifications
            .set_onmessage(Some(connection.on_message.as_ref().unchecked_ref()));
        if let Some(window) = &connection.window {
            window.add_event_listener_with_callback(
                "pageshow",
                connection.on_resume.as_ref().unchecked_ref(),
            )?;
        }
        if let Some(document) = &connection.document {
            document.add_event_listener_with_callback(
                "visibilitychange",
                connection.on_resume.as_ref().unchecked_ref(),
            )?;
        }
        Ok(connection)
    }

    async fn update<R, C>(
        &self,
        name: &'static str,
        edit: impl FnOnce(Option<Vec<u8>>) -> Result<(R, Option<Vec<u8>>, C), Error> + 'static,
    ) -> Result<R, Error>
    where
        R: 'static,
        C: FnOnce() + 'static,
    {
        let transaction = self
            .database
            .transaction(&[RECORDS], TransactionMode::ReadWrite)?;
        let store = transaction.object_store(RECORDS)?;
        let key = JsValue::from_str(name);
        let mut request = store.get(key.clone())?;
        let outcome = Rc::new(Cell::new(None));
        let callback_outcome = Rc::clone(&outcome);
        let notifications = self.notifications.clone();

        // IndexedDB accepts the put while this request's event keeps the transaction active.
        request.on_success(move |event| {
            let result: Result<(R, C), Error> = (|| {
                let value = event.target()?.result()?;
                let current = record_bytes((!value.is_undefined()).then_some(value))?;
                let (result, replacement, publish) = edit(current)?;
                if let Some(bytes) = replacement {
                    let bytes = Uint8Array::from(bytes.as_slice());
                    store.put(bytes.as_ref(), Some(&key))?;
                    // Other tabs enqueue a fresh transaction behind this write, even if this tab closes.
                    let _ = notifications.post_message(&key);
                }
                Ok((result, publish))
            })();
            callback_outcome.set(Some(result));
        });

        let completion = transaction.await?;
        drop(request);
        if completion.is_aborted() {
            return Err(Error::Aborted);
        }
        let (result, publish) = outcome.take().ok_or(Error::Aborted)??;
        publish();
        Ok(result)
    }
}

impl Drop for Connection {
    fn drop(&mut self) {
        self.notifications.set_onmessage(None);
        self.notifications.close();
        if let Some(window) = &self.window {
            let _ = window.remove_event_listener_with_callback(
                "pageshow",
                self.on_resume.as_ref().unchecked_ref(),
            );
        }
        if let Some(document) = &self.document {
            let _ = document.remove_event_listener_with_callback(
                "visibilitychange",
                self.on_resume.as_ref().unchecked_ref(),
            );
        }
        self.database.close();
    }
}

fn refresh(
    inner: &Inner,
    records: impl IntoIterator<Item = (&'static str, Arc<dyn StoredRecord>)>,
) {
    for (name, record) in records {
        let _ = inner.requests.send(Box::new(move |connection| {
            Box::pin(async move {
                let committed = Arc::clone(&record);
                let result = connection
                    .update(name, move |bytes| {
                        let publish = committed.prepare(bytes)?;
                        Ok(((), None, publish))
                    })
                    .await;
                if let Err(error) = result {
                    record.fail(error);
                }
            })
        }));
    }
}

fn record_bytes(value: Option<JsValue>) -> Result<Option<Vec<u8>>, Error> {
    value
        .map(|value| {
            value
                .dyn_into::<Uint8Array>()
                .map(|bytes| bytes.to_vec())
                .map_err(|_| Error::InvalidRecord)
        })
        .transpose()
}
