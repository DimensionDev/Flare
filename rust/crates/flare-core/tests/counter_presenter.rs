use std::{
    future::{Future, poll_fn},
    pin::pin,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    task::{Context, Poll, Wake, Waker},
    time::Duration,
};

use flare_core::presenter::{CounterPresenter, CounterState, PresenterError, UiState};

fn state(value: i64, revision: u64) -> UiState<CounterState> {
    UiState::Success {
        data: CounterState { value, revision },
    }
}

async fn within<T>(future: impl Future<Output = T>) -> T {
    tokio::time::timeout(Duration::from_secs(5), future)
        .await
        .expect("presenter operation must not hang")
}

#[tokio::test]
async fn actions_commit_before_returning_and_old_snapshots_stay_unchanged() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let states = presenter.subscribe();
        let initial = states.next_state().await.unwrap();
        assert_eq!(initial, state(0, 0));

        presenter.increment().await.unwrap();
        assert_eq!(presenter.snapshot(), state(1, 1));
        assert_eq!(states.next_state().await, Some(presenter.snapshot()));
        presenter.decrement().await.unwrap();
        presenter.decrement().await.unwrap();
        assert_eq!(states.next_state().await, Some(state(-1, 3)));
        presenter.reset().await.unwrap();
        assert_eq!(states.next_state().await, Some(state(0, 4)));
        assert_eq!(initial, state(0, 0));

        presenter.reset().await.unwrap();
        let mut next = pin!(states.next_state());
        poll_fn(|context| {
            assert!(next.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;
        states.stop();
        assert_eq!(next.await, None);
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn new_and_slow_subscribers_receive_the_latest_state() {
    within(async {
        let (presenter, task) = CounterPresenter::new(5);
        let task = tokio::spawn(task);
        presenter.increment().await.unwrap();
        let first = presenter.subscribe();
        let second = presenter.subscribe();
        assert_eq!(first.next_state().await, Some(state(6, 1)));
        for _ in 0..10 {
            presenter.increment().await.unwrap();
        }
        let latest = state(16, 11);
        assert_eq!(first.next_state().await, Some(latest.clone()));
        assert_eq!(second.next_state().await, Some(latest.clone()));
        assert_eq!(
            presenter.subscribe().next_state().await,
            Some(latest.clone())
        );
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn overflow_leaves_state_unchanged_and_does_not_close_the_presenter() {
    within(async {
        for initial in [i64::MIN, i64::MAX] {
            let (presenter, task) = CounterPresenter::new(initial);
            let task = tokio::spawn(task);
            let before = presenter.snapshot();
            let result = if initial == i64::MAX {
                presenter.increment().await
            } else {
                presenter.decrement().await
            };
            assert!(matches!(result, Err(PresenterError::Failed { error }) if error.code == "counter_overflow"));
            assert_eq!(presenter.snapshot(), before);
            presenter.reset().await.unwrap();
            assert_eq!(
                presenter.snapshot(),
                state(0, 1
                )
            );
            presenter.shutdown().await;
            task.await.unwrap();
        }
    })
    .await;
}

#[tokio::test]
async fn stopping_one_subscription_wakes_all_its_reads_and_keeps_others_alive() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let first = presenter.subscribe();
        let second = presenter.subscribe();
        first.next_state().await.unwrap();
        let mut reads: Vec<_> = (0..3).map(|_| Box::pin(first.next_state())).collect();
        poll_fn(|context| {
            for read in &mut reads {
                assert!(read.as_mut().poll(context).is_pending());
            }
            Poll::Ready(())
        })
        .await;
        first.stop();
        first.stop();
        for read in reads {
            assert_eq!(read.await, None);
        }
        presenter.increment().await.unwrap();
        assert_eq!(second.next_state().await, Some(state(1, 1)));
        assert_eq!(first.next_state().await, None);
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn shutdown_ends_pending_reads_and_rejects_new_actions_and_subscriptions() {
    within(async {
        let (presenter, task) = CounterPresenter::new(9);
        let task = tokio::spawn(task);
        let states = presenter.subscribe();
        states.next_state().await.unwrap();
        let mut pending = pin!(states.next_state());
        poll_fn(|context| {
            assert!(pending.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;
        presenter.shutdown().await;
        presenter.shutdown().await;
        assert_eq!(pending.await, None);
        assert_eq!(presenter.subscribe().next_state().await, None);
        assert_eq!(presenter.increment().await, Err(PresenterError::Closed));
        assert_eq!(presenter.decrement().await, Err(PresenterError::Closed));
        assert_eq!(presenter.reset().await, Err(PresenterError::Closed));
        assert_eq!(presenter.snapshot(), state(9, 0));
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn subscriptions_do_not_keep_a_dropped_presenter_alive() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let states = presenter.subscribe();
        states.next_state().await.unwrap();
        drop(presenter);
        task.await.unwrap();
        assert_eq!(states.next_state().await, None);
    })
    .await;
}

#[tokio::test]
async fn dropping_the_task_closes_accepted_actions_and_subscriptions() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let states = presenter.subscribe();
        states.next_state().await.unwrap();
        let mut action = pin!(presenter.increment());
        poll_fn(|context| {
            assert!(action.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;
        drop(task);
        assert_eq!(action.await, Err(PresenterError::Closed));
        assert_eq!(states.next_state().await, None);
        assert_eq!(presenter.increment().await, Err(PresenterError::Closed));
        presenter.shutdown().await;
    })
    .await;
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn concurrent_actions_do_not_lose_updates() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let presenter = Arc::new(presenter);
        let states = presenter.subscribe();
        let mut clients = Vec::new();
        for _ in 0..8 {
            let presenter = Arc::clone(&presenter);
            clients.push(tokio::spawn(async move {
                for _ in 0..128 {
                    presenter.increment().await.unwrap();
                }
            }));
        }
        for client in clients {
            client.await.unwrap();
        }
        let expected = state(1024, 1024);
        assert_eq!(presenter.snapshot(), expected.clone());
        assert_eq!(states.next_state().await, Some(expected));
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn cancelling_a_read_keeps_the_subscription_usable() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let states = presenter.subscribe();
        states.next_state().await.unwrap();
        let mut pending = Box::pin(states.next_state());
        poll_fn(|context| {
            assert!(pending.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;
        drop(pending);
        presenter.increment().await.unwrap();
        assert_eq!(states.next_state().await, Some(state(1, 1)));
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

#[tokio::test]
async fn cancelling_an_accepted_action_does_not_undo_it() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let mut action = Box::pin(presenter.increment());
        poll_fn(|context| {
            assert!(action.as_mut().poll(context).is_pending());
            Poll::Ready(())
        })
        .await;
        drop(action);
        let task = tokio::spawn(task);
        presenter.increment().await.unwrap();
        assert_eq!(presenter.snapshot(), state(2, 2));
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}

struct ReadSnapshotOnWake {
    presenter: Arc<CounterPresenter>,
    observed: AtomicBool,
}

impl Wake for ReadSnapshotOnWake {
    fn wake(self: Arc<Self>) {
        self.wake_by_ref();
    }

    fn wake_by_ref(self: &Arc<Self>) {
        assert_eq!(self.presenter.snapshot(), state(1, 1));
        self.observed.store(true, Ordering::Relaxed);
    }
}

#[tokio::test]
async fn notification_allows_synchronous_snapshot_readback() {
    within(async {
        let (presenter, task) = CounterPresenter::new(0);
        let task = tokio::spawn(task);
        let presenter = Arc::new(presenter);
        let states = presenter.subscribe();
        states.next_state().await.unwrap();
        let observer = Arc::new(ReadSnapshotOnWake {
            presenter: Arc::clone(&presenter),
            observed: AtomicBool::new(false),
        });
        let waker = Waker::from(Arc::clone(&observer));
        let mut next = pin!(states.next_state());
        assert!(
            next.as_mut()
                .poll(&mut Context::from_waker(&waker))
                .is_pending()
        );

        presenter.increment().await.unwrap();
        assert!(observer.observed.load(Ordering::Relaxed));
        assert_eq!(next.await, Some(state(1, 1)));
        presenter.shutdown().await;
        task.await.unwrap();
    })
    .await;
}
