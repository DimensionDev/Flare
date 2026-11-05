//! Native ABI and runtime ownership. Business state remains in `flare-core`.

mod native;
mod presenters;
#[cfg(feature = "bindgen")]
pub use presenters::PresenterBinding;

uniffi::setup_scaffolding!();

include!(concat!(env!("OUT_DIR"), "/exports.rs"));

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[tokio::test]
    async fn closing_or_dropping_native_owners_releases_handles_and_pending_tasks() {
        tokio::time::timeout(Duration::from_secs(5), async {
            for iteration in 0..20 {
                let initial = if iteration == 0 { i64::MAX } else { 0 };
                let presenter = CounterPresenter::new(initial).unwrap();
                let subscription = presenter.subscribe();
                if iteration == 0 {
                    let error = presenter.increment().await.unwrap_err();
                    assert!(matches!(&error, PresenterError::Failed { error } if error.code == "counter_overflow"));
                    assert_eq!(
                        error.to_string(),
                        "counter value or state revision would overflow"
                    );
                    assert!(matches!(subscription.next_state().await, Some(UiStateOfCounterState::Success { data }) if data.value == i64::MAX));
                } else {
                    presenter.increment().await.unwrap();
                    assert!(matches!(subscription.next_state().await, Some(UiStateOfCounterState::Success { data }) if data.value == 1));
                }
                assert_eq!(
                    counter_resources(),
                    CounterResources {
                        presenters: 1,
                        subscriptions: 1,
                        tasks: 1
                    }
                );
                let read = tokio::spawn(async move { subscription.next_state().await });
                if iteration % 2 == 0 {
                    presenter.shutdown();
                }
                drop(presenter);
                assert!(read.await.unwrap().is_none());
                while counter_resources()
                    != (CounterResources {
                        presenters: 0,
                        subscriptions: 0,
                        tasks: 0,
                    })
                {
                    tokio::task::yield_now().await;
                }
            }
        })
        .await
        .expect("native objects and actor tasks must not leak");
    }
}
