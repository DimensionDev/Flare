use flare_core::presenter::{PresenterError, PresenterSession, UiState};
use std::{fmt, future::Future};

mod shared {
    use flare_presenter_macros::presenter_dto;

    #[presenter_dto]
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixtureAvatar {
        pub url: String,
    }

    #[presenter_dto]
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixturePerson {
        pub name: String,
        pub avatar: Option<FixtureAvatar>,
    }
}

pub use shared::{FixtureAvatar, FixturePerson};

#[flare_presenter_macros::presenter]
mod rename_contract {
    use super::{FixtureAvatar, FixturePerson, Future, PresenterSession, UiState, fmt};
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixtureRenameState {
        pub name: String,
        pub history: Vec<String>,
        pub session: Option<String>,
        pub entries: Vec<Option<FixtureRenameEntry>>,
    }

    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixtureRenameEntry {
        pub author: FixturePerson,
        pub origin: FixtureRenameOrigin,
        pub related: Vec<FixtureRenameEntry>,
    }

    #[flare_presenter_macros::presenter_dto]
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub enum FixtureRenameOrigin {
        Manual,
        Imported { previous: FixturePerson },
        Legacy(String),
    }

    #[derive(Clone, Debug)]
    pub struct FixtureRenameRequest {
        pub value: String,
        pub uppercase: bool,
    }

    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixtureRenameFailure {
        pub value: String,
    }

    #[derive(Debug)]
    pub enum FixtureRenameError {
        Closed,
        InvalidName { detail: FixtureRenameFailure },
    }

    impl fmt::Display for FixtureRenameError {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            match self {
                Self::Closed => f.write_str("rename presenter closed"),
                Self::InvalidName { detail } => write!(f, "invalid name: {:?}", detail.value),
            }
        }
    }

    impl std::error::Error for FixtureRenameError {}

    pub struct FixtureRenamePresenter {
        session: PresenterSession<FixtureRenameState, String, FixtureRenameError>,
    }

    impl FixtureRenamePresenter {
        pub fn new(initial: String) -> (Self, impl Future<Output = ()> + Send + 'static) {
            let mut state = FixtureRenameState {
                name: initial,
                history: Vec::new(),
                session: Some("demo".into()),
                entries: Vec::new(),
            };
            let (session, task) = PresenterSession::new(state.clone(), move |name: String| {
                if name.is_empty() {
                    return Err(FixtureRenameError::InvalidName {
                        detail: FixtureRenameFailure { value: name },
                    });
                }
                if state.name == name {
                    return Ok(None);
                }
                state.history.push(state.name.clone());
                state.entries.push(Some(FixtureRenameEntry {
                    author: FixturePerson {
                        name: name.clone(),
                        avatar: Some(FixtureAvatar {
                            url: "avatar.png".into(),
                        }),
                    },
                    origin: FixtureRenameOrigin::Manual,
                    related: Vec::new(),
                }));
                state.name = name;
                Ok(Some(state.clone()))
            });
            (Self { session }, task)
        }

        /// # Errors
        /// Rejects empty names and actions after closure.
        pub async fn rename(
            &self,
            value: String,
            uppercase: bool,
        ) -> Result<FixtureRenameState, FixtureRenameError> {
            let value = if uppercase {
                value.to_uppercase()
            } else {
                value
            };
            self.session.dispatch(value).await?;
            Ok(self.snapshot())
        }

        /// # Errors
        /// Rejects empty names and actions after closure.
        pub async fn rename_request(
            &self,
            request: FixtureRenameRequest,
        ) -> Result<FixturePerson, FixtureRenameError> {
            let state = self.rename(request.value, request.uppercase).await?;
            Ok(state
                .entries
                .last()
                .unwrap()
                .as_ref()
                .unwrap()
                .author
                .clone())
        }

        /// # Errors
        /// This fixture returns its argument unchanged to exercise generic ABI values.
        pub async fn preview(
            &self,
            state: UiState<FixturePerson>,
        ) -> Result<UiState<FixturePerson>, FixtureRenameError> {
            self.session.dispatch(self.snapshot().name).await?;
            Ok(state)
        }
    }
}

#[flare_presenter_macros::presenter]
mod profile_contract {
    use super::{FixturePerson, Future, PresenterError, PresenterSession, UiState};
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct FixtureProfileState {
        pub owner: UiState<FixturePerson>,
        pub suggestions: UiState<Vec<FixturePerson>>,
    }

    pub struct FixtureURLPresenter {
        session: PresenterSession<FixtureProfileState, UiState<FixturePerson>>,
    }

    impl FixtureURLPresenter {
        pub fn new(owner: FixturePerson) -> (Self, impl Future<Output = ()> + Send + 'static) {
            let state = FixtureProfileState {
                owner: UiState::Success {
                    data: owner.clone(),
                },
                suggestions: UiState::Success { data: vec![owner] },
            };
            let mut current = state.clone();
            let (session, task) = PresenterSession::new(state, move |owner| {
                current.owner = owner;
                Ok(Some(current.clone()))
            });
            (Self { session }, task)
        }

        /// # Errors
        /// Rejects actions after closure.
        pub async fn select(&self, owner: FixturePerson) -> Result<FixturePerson, PresenterError> {
            self.session
                .dispatch(UiState::Success {
                    data: owner.clone(),
                })
                .await?;
            Ok(owner)
        }

        /// # Errors
        /// Rejects actions after closure.
        pub async fn set_owner(&self, state: UiState<FixturePerson>) -> Result<(), PresenterError> {
            self.session.dispatch(state).await
        }
    }
}
