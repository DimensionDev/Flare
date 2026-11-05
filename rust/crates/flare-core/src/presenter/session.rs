use std::{error::Error, fmt, future::Future};

use tokio::sync::{mpsc, oneshot};

use super::{PresenterError, StateStore, StateSubscription};

const COMMAND_CAPACITY: usize = 32;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PresenterClosed;

impl fmt::Display for PresenterClosed {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("presenter is closed")
    }
}

impl Error for PresenterClosed {}

/// Serializes actions and acknowledges them only after committing their state.
/// The returned task is scheduled by the caller; the core never owns a runtime.
pub struct PresenterSession<S, A, E = PresenterError> {
    commands: mpsc::Sender<Command<A, E>>,
    state: StateStore<S>,
}

struct Command<A, E> {
    action: A,
    reply: oneshot::Sender<Result<(), E>>,
}

impl<S: Send + Sync + 'static, A: Send + 'static, E: Send + 'static> PresenterSession<S, A, E> {
    /// The update function owns the business model and must not block on I/O.
    /// Return `None` for an action that does not change observable state.
    #[must_use = "the returned task must be scheduled for presenter actions to complete"]
    pub fn new(
        initial: S,
        mut update: impl FnMut(A) -> Result<Option<S>, E> + Send + 'static,
    ) -> (Self, impl Future<Output = ()> + Send + 'static) {
        let (state, mut publisher) = StateStore::new(initial);
        let (commands, mut inbox) = mpsc::channel::<Command<A, E>>(COMMAND_CAPACITY);
        let task = async move {
            loop {
                let command = tokio::select! {
                    biased;
                    () = publisher.closed() => break,
                    command = inbox.recv() => {
                        let Some(command) = command else { break };
                        command
                    }
                };
                let result = update(command.action).map(|next| {
                    if let Some(next) = next {
                        publisher.publish(next);
                    }
                });
                let _ = command.reply.send(result);
            }
        };
        (Self { commands, state }, task)
    }
}

impl<S, A, E> PresenterSession<S, A, E> {
    #[must_use]
    pub fn subscribe(&self) -> StateSubscription<S> {
        self.state.subscribe()
    }

    /// Requests closure without blocking the caller or waiting for queue space.
    pub fn request_shutdown(&self) {
        self.state.request_close();
    }

    /// Waits until the task can no longer change state; idempotent.
    pub async fn shutdown(&self) {
        self.request_shutdown();
        self.commands.closed().await;
    }
}

impl<S: Clone, A, E> PresenterSession<S, A, E> {
    #[must_use]
    pub fn snapshot(&self) -> S {
        self.state.snapshot()
    }
}

impl<S, A, E: From<PresenterClosed>> PresenterSession<S, A, E> {
    /// Cancelling the wait after enqueueing does not undo the action.
    ///
    /// # Errors
    /// Returns the business error, or `PresenterClosed` converted to `E`.
    pub async fn dispatch(&self, action: A) -> Result<(), E> {
        if self.state.is_closed() {
            return Err(PresenterClosed.into());
        }
        let (reply, result) = oneshot::channel();
        self.commands
            .send(Command { action, reply })
            .await
            .map_err(|_| E::from(PresenterClosed))?;
        result.await.unwrap_or_else(|_| Err(PresenterClosed.into()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{future::poll_fn, task::Poll, time::Duration};

    #[tokio::test]
    async fn shutdown_wakes_queued_and_backpressured_actions() {
        let (session, task) = PresenterSession::new(0, |value| Ok(Some(value)));
        let mut requests: Vec<_> = (0..=COMMAND_CAPACITY)
            .map(|_| Box::pin(session.dispatch(1)))
            .collect();
        poll_fn(|context| {
            for request in &mut requests {
                assert!(request.as_mut().poll(context).is_pending());
            }
            Poll::Ready(())
        })
        .await;
        assert_eq!(session.commands.capacity(), 0);
        let mut closing = Box::pin(session.shutdown());
        poll_fn(|context| {
            assert!(closing.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;

        let task = tokio::spawn(task);
        tokio::time::timeout(Duration::from_secs(5), async {
            closing.await;
            for request in requests {
                assert_eq!(request.await, Err(PresenterClosed));
            }
            task.await.unwrap();
        })
        .await
        .expect("shutdown must bypass the full queue");
        assert_eq!(session.snapshot(), 0);
    }
}
