use std::{future::Future, time::Duration};

use flare_core::presenter::StateStore;

async fn within<T>(future: impl Future<Output = T>) -> T {
    tokio::time::timeout(Duration::from_secs(5), future)
        .await
        .expect("state store operations must not hang")
}

#[tokio::test]
async fn owned_snapshots_and_subscriptions_are_isolated_between_stores() {
    within(async {
        let initial = vec![String::from("initial")];
        let (first, mut first_publisher) = StateStore::new(initial.clone());
        let (second, mut second_publisher) = StateStore::new(initial.clone());
        let first_observer = first.subscribe();
        let second_observer = second.subscribe();
        assert_eq!(first_observer.next_state().await, Some(initial.clone()));
        assert_eq!(second_observer.next_state().await, Some(initial.clone()));

        let old_snapshot = first.snapshot();
        let mut callers_copy = first.snapshot();
        callers_copy.push(String::from("caller-only"));
        assert_eq!(first.snapshot(), initial);

        first_publisher.publish(vec![String::from("intermediate")]);
        let latest = vec![String::from("latest")];
        first_publisher.publish(latest.clone());
        assert_eq!(first_observer.next_state().await, Some(latest.clone()));
        assert_eq!(first.subscribe().next_state().await, Some(latest));
        assert_eq!(old_snapshot, initial);
        assert_eq!(second.snapshot(), initial);

        first.request_close();
        assert!(first.is_closed());
        assert_eq!(first_observer.next_state().await, None);
        first_publisher.closed().await;
        assert!(!second.is_closed());
        let second_state = vec![String::from("second")];
        second_publisher.publish(second_state.clone());
        assert_eq!(second_observer.next_state().await, Some(second_state));
    })
    .await;
}

#[tokio::test]
async fn subscriptions_do_not_keep_the_store_alive_and_publisher_drop_closes_reads() {
    within(async {
        let (store, mut publisher) = StateStore::new(String::from("owned"));
        let observer = store.subscribe();
        assert_eq!(observer.next_state().await.as_deref(), Some("owned"));
        drop(store);
        publisher.closed().await;
        assert_eq!(observer.next_state().await, None);

        let (store, publisher) = StateStore::new(String::from("task"));
        let observer = store.subscribe();
        assert_eq!(observer.next_state().await.as_deref(), Some("task"));
        drop(publisher);
        assert!(store.is_closed());
        assert_eq!(observer.next_state().await, None);
        assert_eq!(store.snapshot(), "task");
    })
    .await;
}
