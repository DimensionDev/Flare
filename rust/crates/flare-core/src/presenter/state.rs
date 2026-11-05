use tokio::sync::{Mutex, watch};

/// Owns one presenter's observable state and close signal.
///
/// The presenter holds this handle; its task owns the separate publisher.
/// Dropping the store requests task shutdown even if subscriptions remain alive.
/// State values should be data snapshots without shared mutable fields.
pub struct StateStore<S> {
    states: watch::Receiver<S>,
    closing: watch::Sender<bool>,
}

impl<S> StateStore<S> {
    /// Creates an isolated state store and its single publishing handle.
    #[must_use]
    pub fn new(initial: S) -> (Self, StatePublisher<S>) {
        let (states, receiver) = watch::channel(initial);
        let (closing, closed) = watch::channel(false);
        (
            Self {
                states: receiver,
                closing,
            },
            StatePublisher { states, closed },
        )
    }

    /// Creates an independent observer with initial-value replay.
    #[must_use]
    pub fn subscribe(&self) -> StateSubscription<S> {
        let mut receiver = self.states.clone();
        receiver.mark_changed();
        let (stopped, _) = watch::channel(false);
        StateSubscription {
            states: Mutex::new(receiver),
            stopped,
            closing: self.closing.subscribe(),
        }
    }

    /// Whether closure was requested or the publishing task has gone away.
    #[must_use]
    pub fn is_closed(&self) -> bool {
        *self.closing.borrow() || self.states.is_closed()
    }

    /// Wakes observers and the publishing task without waiting for it to stop.
    /// An update already being processed by the task may still finish.
    pub fn request_close(&self) {
        self.closing.send_replace(true);
    }
}

impl<S: Clone> StateStore<S> {
    /// Reads an owned snapshot, including after the session has closed.
    #[must_use]
    pub fn snapshot(&self) -> S {
        self.states.borrow().clone()
    }
}

/// The task's publishing handle; it does not keep the owning store alive.
pub struct StatePublisher<S> {
    states: watch::Sender<S>,
    closed: watch::Receiver<bool>,
}

impl<S> StatePublisher<S> {
    /// Commits a snapshot. The business layer decides whether an update is needed.
    pub fn publish(&mut self, state: S) {
        self.states.send_replace(state);
    }

    /// Resolves when the owning store requests closure or is dropped.
    pub async fn closed(&mut self) {
        wait_for_close(&mut self.closed).await;
    }
}

/// A latest-value subscription, not a queue of every intermediate state.
/// Observing a session does not keep its store or publishing task alive.
pub struct StateSubscription<S> {
    states: Mutex<watch::Receiver<S>>,
    stopped: watch::Sender<bool>,
    closing: watch::Receiver<bool>,
}

impl<S: Clone> StateSubscription<S> {
    /// Returns the current value first, then newer values, or `None` once closed.
    ///
    /// Slow observers may skip updates. Concurrent reads on one subscription
    /// are serialized; separate observers should create separate subscriptions.
    /// Cancelling a pending read does not stop the subscription or lose a value.
    pub async fn next_state(&self) -> Option<S> {
        let mut stopped = self.stopped.subscribe();
        let mut closing = self.closing.clone();
        tokio::select! {
            biased;
            () = wait_for_close(&mut stopped) => None,
            () = wait_for_close(&mut closing) => None,
            state = async {
                let mut receiver = self.states.lock().await;
                receiver.changed().await.ok()?;
                let state = receiver.borrow_and_update().clone();
                Some(state)
            } => state,
        }
    }
}

impl<S> StateSubscription<S> {
    /// Stops this observer and wakes pending reads, leaving its session running.
    pub fn stop(&self) {
        self.stopped.send_replace(true);
    }
}

async fn wait_for_close(receiver: &mut watch::Receiver<bool>) {
    let _ = receiver.wait_for(|closed| *closed).await;
}
