use std::time::Duration;

use flare_core::presenter::{PresenterClosed, PresenterSession};

#[derive(Debug, PartialEq, Eq)]
enum RenameError {
    Closed,
    EmptyName,
}

impl From<PresenterClosed> for RenameError {
    fn from(_: PresenterClosed) -> Self {
        Self::Closed
    }
}

#[tokio::test]
async fn sessions_support_owned_actions_snapshots_and_business_errors() {
    tokio::time::timeout(Duration::from_secs(5), async {
        let (session, task) = PresenterSession::new(String::from("initial"), |name: String| {
            if name.is_empty() {
                Err(RenameError::EmptyName)
            } else {
                Ok(Some(name))
            }
        });
        let task = tokio::spawn(task);
        let states = session.subscribe();
        assert_eq!(states.next_state().await.as_deref(), Some("initial"));
        session.dispatch(String::from("updated")).await.unwrap();
        assert_eq!(session.snapshot(), "updated");
        assert_eq!(states.next_state().await.as_deref(), Some("updated"));
        assert_eq!(
            session.dispatch(String::new()).await,
            Err(RenameError::EmptyName)
        );
        assert_eq!(session.snapshot(), "updated");
        session
            .dispatch(String::from("still running"))
            .await
            .unwrap();
        session.shutdown().await;
        assert_eq!(states.next_state().await, None);
        assert_eq!(
            session.dispatch(String::from("closed")).await,
            Err(RenameError::Closed)
        );
        task.await.unwrap();
    })
    .await
    .expect("generic presenter sessions must shut down");
}
