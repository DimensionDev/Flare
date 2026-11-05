use std::future::Future;

use crate::domain::counter::Counter;
use crate::presenter::{PresenterError, PresenterSession, UiError, UiState};

#[flare_presenter_macros::presenter]
mod contract {
    use super::{Counter, Future, Model, Operation, PresenterError, PresenterSession, UiState};
    /// An owned snapshot. Changing a caller's copy never changes the presenter.
    #[derive(Clone, Copy, Debug, PartialEq, Eq)]
    pub struct CounterState {
        pub value: i64,
        /// Increases on each value change; no-op actions leave it unchanged.
        pub revision: u64,
    }

    /// A stable action handle; its task is the sole owner of mutable business state.
    ///
    /// Dropping the last owner closes the command channel and stops the task.
    /// Keeping snapshots or subscriptions alive does not keep this presenter alive.
    pub struct CounterPresenter {
        session: PresenterSession<UiState<CounterState>, Operation>,
    }

    impl CounterPresenter {
        /// Creates a presenter and its task without choosing or starting an executor.
        ///
        /// The caller must run the returned future concurrently with actions and
        /// shutdown, e.g. using `tokio::spawn` on native targets. Dropping the task
        /// closes pending actions and subscriptions. No runtime is created here.
        #[must_use = "the returned task must be scheduled for presenter actions to complete"]
        pub fn new(initial_value: i64) -> (Self, impl Future<Output = ()> + Send + 'static) {
            let mut model = Model {
                counter: Counter::new(initial_value),
                revision: 0,
            };
            let (session, task) = PresenterSession::new(
                UiState::Success {
                    data: model.snapshot(),
                },
                move |operation| {
                    model
                        .update(operation)
                        .map(|next| next.map(|data| UiState::Success { data }))
                },
            );
            (Self { session }, task)
        }

        /// Adds one and returns after the new state has been committed.
        ///
        /// Cancelling this future after enqueueing does not undo the action.
        ///
        /// # Errors
        /// Returns `Closed` if unavailable, or `counter_overflow` without changing state.
        pub async fn increment(&self) -> Result<(), PresenterError> {
            self.session.dispatch(Operation::Increment).await
        }

        /// Subtracts one and returns after the new state has been committed.
        ///
        /// Cancelling this future after enqueueing does not undo the action.
        ///
        /// # Errors
        /// Returns `Closed` if unavailable, or `counter_overflow` without changing state.
        pub async fn decrement(&self) -> Result<(), PresenterError> {
            self.session.dispatch(Operation::Decrement).await
        }

        /// Sets the value to zero; resetting zero does not emit another state.
        ///
        /// Cancelling this future after enqueueing does not undo the action.
        ///
        /// # Errors
        /// Returns `Closed` if unavailable, or `counter_overflow` if the revision is exhausted.
        pub async fn reset(&self) -> Result<(), PresenterError> {
            self.session.dispatch(Operation::Reset).await
        }
    }
}

#[derive(Clone, Copy)]
enum Operation {
    Increment,
    Decrement,
    Reset,
}

struct Model {
    counter: Counter,
    revision: u64,
}

impl Model {
    fn snapshot(&self) -> CounterState {
        CounterState {
            value: self.counter.value(),
            revision: self.revision,
        }
    }

    fn update(&mut self, operation: Operation) -> Result<Option<CounterState>, PresenterError> {
        let mut next = self.counter;
        match operation {
            Operation::Increment => next.increment().map_err(|_| overflow())?,
            Operation::Decrement => next.decrement().map_err(|_| overflow())?,
            Operation::Reset => next.reset(),
        }
        if next.value() == self.counter.value() {
            return Ok(None);
        }
        let revision = self.revision.checked_add(1).ok_or_else(overflow)?;
        self.counter = next;
        self.revision = revision;
        Ok(Some(self.snapshot()))
    }
}

fn overflow() -> PresenterError {
    UiError::new(
        "counter_overflow",
        "counter value or state revision would overflow",
    )
    .into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exhausted_revision_rejects_changes_atomically() {
        let mut model = Model {
            counter: Counter::new(7),
            revision: u64::MAX,
        };
        let before = model.snapshot();
        assert_eq!(model.update(Operation::Increment), Err(overflow()));
        assert_eq!(model.update(Operation::Reset), Err(overflow()));
        assert_eq!(model.snapshot(), before);
    }
}
