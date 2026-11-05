use super::{CatalogProfile, PresenterSession};
use std::{fmt, future::Future};

#[flare_presenter_macros::presenter]
mod contract {
    use super::{CatalogProfile, Future, PresenterSession, fmt};
    #[derive(Clone, Debug, PartialEq, Eq)]
    pub struct CatalogProbeState {
        pub profile: CatalogProfile,
    }

    #[derive(Debug)]
    pub enum CatalogProbeError {
        Closed,
    }

    impl fmt::Display for CatalogProbeError {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            f.write_str("catalog probe closed")
        }
    }

    impl std::error::Error for CatalogProbeError {}

    pub struct CatalogProbePresenter {
        session: PresenterSession<CatalogProbeState, String, CatalogProbeError>,
    }

    impl CatalogProbePresenter {
        pub fn new(profile: CatalogProfile) -> (Self, impl Future<Output = ()> + Send + 'static) {
            let mut state = CatalogProbeState { profile };
            let (session, task) = PresenterSession::new(state.clone(), move |name: String| {
                state.profile.name = name;
                Ok(Some(state.clone()))
            });
            (Self { session }, task)
        }

        /// # Errors
        /// Rejects actions after closure.
        pub async fn rename(&self, name: String) -> Result<(), CatalogProbeError> {
            self.session.dispatch(name).await
        }
    }
}
