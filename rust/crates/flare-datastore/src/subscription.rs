use std::{
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use futures_core::Stream;
use tokio::sync::watch;
use tokio_stream::wrappers::WatchStream;

use crate::{DataStore, Error, WireMessage, state::Snapshot};

/// A stream of immutable committed snapshots that keeps its store alive.
/// Browser refresh errors are shared with the stream's observers.
pub struct Subscription<T: WireMessage> {
    stream: WatchStream<Snapshot<T>>,
    _store: DataStore,
}

impl<T: WireMessage> Subscription<T> {
    pub(crate) fn new(receiver: watch::Receiver<Snapshot<T>>, store: DataStore) -> Self {
        Self {
            stream: WatchStream::new(receiver),
            _store: store,
        }
    }
}

impl<T: WireMessage> Stream for Subscription<T> {
    type Item = Result<Arc<T>, Arc<Error>>;

    fn poll_next(self: Pin<&mut Self>, context: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        Pin::new(&mut self.get_mut().stream)
            .poll_next(context)
            .map(|snapshot| snapshot.map(|snapshot| snapshot.value))
    }
}
