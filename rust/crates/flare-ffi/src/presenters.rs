/// Generates concrete `UniFFI` objects and the platform adapter registry together.
/// State types are shared with the core via `UniFFI` remote records.
macro_rules! export_presenter {
    (
        declarations { $($declaration:item)* }
        $presenter:ident {
            core: $core:ty,
            state: $state:ty,
            subscription: $subscription:ident,
            error: $error:ident,
            resources: $resources:ident,
            resource_type: $resource_type:ident,
            resource_fn: $resource_fn:ident,
            binding: $binding:literal,
            native_presenter: $native_presenter:literal,
            native_subscription: $native_subscription:literal,
            constructor($($arg:ident: $arg_type:ty),* $(,)?) => $constructor:expr;
            $($(#[$action_doc:meta])* fn $action:ident($($param:ident: $param_type:ty),* $(,)?) -> $result:ty;)*
        }
    ) => {
            $($declaration)*
            static $resources: $crate::native::ResourceCounters =
                $crate::native::ResourceCounters::new();

            #[derive(Debug, PartialEq, Eq, uniffi::Record)]
            pub struct $resource_type {
                pub presenters: u64,
                pub subscriptions: u64,
                pub tasks: u64,
            }

            #[uniffi::export]
            #[must_use]
            pub fn $resource_fn() -> $resource_type {
                let (presenters, subscriptions, tasks) = $resources.snapshot();
                $resource_type { presenters, subscriptions, tasks }
            }

            #[derive(uniffi::Object)]
            pub struct $presenter {
                core: $core,
                _task: $crate::native::NativeTask,
                _resource: $crate::native::Resource,
            }

            #[uniffi::export]
            impl $presenter {
                /// # Errors
                /// Returns `RuntimeUnavailable` if the native runtime cannot start.
                #[uniffi::constructor]
                pub fn new($($arg: $arg_type),*) -> Result<std::sync::Arc<Self>, $error> {
                    let (core, task) = $constructor;
                    let task = $crate::native::NativeTask::spawn(task, &$resources)
                        .map_err(|_| $error::RuntimeUnavailable)?;
                    Ok(std::sync::Arc::new(Self {
                        core,
                        _task: task,
                        _resource: $resources.presenter(),
                    }))
                }

                #[must_use]
                pub fn snapshot(&self) -> $state {
                    self.core.snapshot()
                }

                #[must_use]
                pub fn subscribe(&self) -> std::sync::Arc<$subscription> {
                    std::sync::Arc::new($subscription {
                        core: self.core.subscribe(),
                        _resource: $resources.subscription(),
                    })
                }

                $(
                    $(#[$action_doc])*
                    pub async fn $action(&self, $($param: $param_type),*) -> Result<$result, $error> {
                        self.core.$action($($param),*).await.map_err(Into::into)
                    }
                )*

                /// Requests closure without blocking the UI thread.
                pub fn shutdown(&self) {
                    self.core.request_shutdown();
                }
            }

            impl Drop for $presenter {
                fn drop(&mut self) {
                    self.core.request_shutdown();
                }
            }

            #[derive(uniffi::Object)]
            pub struct $subscription {
                core: flare_core::presenter::StateSubscription<$state>,
                _resource: $crate::native::Resource,
            }

            #[uniffi::export]
            impl $subscription {
                pub async fn next_state(&self) -> Option<$state> {
                    self.core.next_state().await
                }

                pub fn stop(&self) {
                    self.core.stop();
                }
            }

            impl Drop for $subscription {
                fn drop(&mut self) {
                    self.core.stop();
                }
            }
            #[cfg(feature = "bindgen")]
            impl $presenter {
                const BINDING: $crate::presenters::PresenterBinding = $crate::presenters::PresenterBinding {
                    presenter: $native_presenter,
                    subscription: $native_subscription,
                    kotlin_binding: $binding,
                };
            }
    };
}

pub(crate) use export_presenter;

#[cfg(feature = "bindgen")]
#[derive(Clone, Copy)]
pub struct PresenterBinding {
    pub presenter: &'static str,
    pub subscription: &'static str,
    pub kotlin_binding: &'static str,
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    include!(concat!(env!("OUT_DIR"), "/test-exports.rs"));

    #[tokio::test]
    async fn generated_contract_forwards_records_parameters_errors_and_releases_resources() {
        #[cfg(feature = "bindgen")]
        {
            assert_eq!(
                FixtureRenamePresenter::BINDING.presenter,
                "FixtureRenamePresenter"
            );
            assert_eq!(
                FixtureRenamePresenter::BINDING.kotlin_binding,
                "fixtureRenamePresenterBinding"
            );
            assert_eq!(
                FixtureURLPresenter::BINDING.presenter,
                "FixtureUrlPresenter"
            );
            assert_eq!(
                FixtureURLPresenter::BINDING.subscription,
                "FixtureUrlSubscription"
            );
            assert_eq!(PRESENTER_BINDINGS.len(), 2);
        }
        tokio::time::timeout(Duration::from_secs(5), async {
            let presenter = FixtureRenamePresenter::new("initial".into()).unwrap();
            let subscription = presenter.subscribe();
            let owner = presenter
                .rename_request(FixtureRenameRequest {
                    value: "updated".into(),
                    uppercase: true,
                })
                .await
                .unwrap();
            let state = presenter.snapshot();
            assert_eq!(state.name, "UPDATED");
            assert_eq!(state.history, ["initial"]);
            assert_eq!(state.session.as_deref(), Some("demo"));
            assert_eq!(state.entries[0].as_ref().unwrap().author, owner);
            let profile = FixtureURLPresenter::new(owner.clone()).unwrap();
            assert_eq!(profile.snapshot().owner, UiStateOfFixturePerson::Success { data: owner.clone() });
            assert_eq!(profile.select(owner.clone()).await.unwrap(), owner);
            assert_eq!(subscription.next_state().await, Some(state.clone()));
            assert_roundtrip(&state);
            let mut nested = state.clone();
            let entry = nested.entries[0].as_mut().unwrap();
            entry.related.push(entry.clone());
            entry.origin = FixtureRenameOrigin::Imported { previous: owner };
            entry.related[0].origin = FixtureRenameOrigin::Legacy("before".into());
            entry.related[0].author.avatar = None;
            nested.entries.push(None);
            assert_roundtrip(&nested);
            assert_roundtrip(&profile.snapshot());
            for next in [
                UiStateOfFixturePerson::Loading,
                UiStateOfFixturePerson::Error { error: UiError::new("profile_unavailable", "Profile unavailable") },
                UiStateOfFixturePerson::Success { data: nested.entries[0].as_ref().unwrap().author.clone() },
            ] {
                let next = presenter.preview(next).await.unwrap();
                profile.set_owner(next.clone()).await.unwrap();
                assert_eq!(profile.snapshot().owner, next);
                assert_roundtrip(&profile.snapshot());
            }
            let error = presenter.rename(String::new(), false).await.unwrap_err();
            assert!(
                matches!(&error, FixtureRenameError::InvalidName { detail } if detail.value.is_empty())
            );
            assert_eq!(error.to_string(), "invalid name: \"\"");
            assert_eq!(presenter.snapshot(), state);
            assert_eq!(
                fixture_rename_resources(),
                FixtureRenameResources {
                    presenters: 1,
                    subscriptions: 1,
                    tasks: 1
                }
            );
            subscription.stop();
            assert_eq!(subscription.next_state().await, None);
            drop(subscription);
            let subscription = presenter.subscribe();
            presenter.shutdown();
            assert!(matches!(
                presenter.rename("closed".into(), false).await,
                Err(FixtureRenameError::Closed)
            ));
            drop(presenter);
            assert_eq!(subscription.next_state().await, None);
            drop(subscription);
            profile.shutdown();
            drop(profile);
            while FIXTURE_RENAME_RESOURCES.snapshot() != (0, 0, 0) || FIXTURE_URL_RESOURCES.snapshot() != (0, 0, 0)
            {
                tokio::task::yield_now().await;
            }
        })
        .await
        .expect("generated native presenters must release their resources");
    }

    fn assert_roundtrip<T>(value: &T)
    where
        T: uniffi::FfiConverter<crate::UniFfiTag> + Clone + PartialEq + std::fmt::Debug,
    {
        let mut buffer = Vec::new();
        T::write(value.clone(), &mut buffer);
        let mut bytes = buffer.as_slice();
        assert_eq!(&T::try_read(&mut bytes).unwrap(), value);
        assert!(bytes.is_empty());
    }
}
