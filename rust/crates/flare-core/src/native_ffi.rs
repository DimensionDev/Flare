use crux_core::{
    App, Core, EffectFFI,
    bridge::{BincodeFfiFormat, Bridge, BridgeError, EffectId, FfiFormat},
};
use serde::{Serialize, de::DeserializeOwned};

pub(crate) struct ViewModelBridge<A: App> {
    core: Bridge<A>,
    bridge_failed: fn(String) -> A::Event,
}

impl<A> ViewModelBridge<A>
where
    A: App + Default,
    A::Model: Default,
    A::Event: Serialize + DeserializeOwned,
    A::ViewModel: Serialize,
    A::Effect: EffectFFI,
{
    pub(crate) fn new(bridge_failed: fn(String) -> A::Event) -> Self {
        Self {
            core: Bridge::new(Core::new()),
            bridge_failed,
        }
    }

    fn render_bridge_error(&self, error: &BridgeError) -> Vec<u8> {
        let mut event = Vec::new();
        BincodeFfiFormat::serialize(&mut event, &(self.bridge_failed)(error.to_string()))
            .expect("a ViewModel must serialize its bridge error event");
        let mut requests = Vec::new();
        self.core
            .update(&event, &mut requests)
            .expect("the bridge can round-trip its own error event");
        requests
    }

    pub(crate) fn update(&self, data: &[u8]) -> Vec<u8> {
        let mut requests = Vec::new();
        match self.core.update(data, &mut requests) {
            Ok(()) => requests,
            Err(error) => self.render_bridge_error(&error),
        }
    }

    pub(crate) fn resolve(&self, id: u32, data: &[u8]) -> Vec<u8> {
        let mut requests = Vec::new();
        match self.core.resolve(EffectId(id), data, &mut requests) {
            Ok(()) => requests,
            Err(error) => self.render_bridge_error(&error),
        }
    }

    pub(crate) fn view(&self) -> Vec<u8> {
        let mut state = Vec::new();
        self.core
            .view(&mut state)
            .expect("a ViewModel must serialize its view state");
        state
    }
}

macro_rules! export_view_model {
    ($app:ty => $ffi:ident) => {
        #[boltffi::export]
        pub struct $ffi {
            bridge: $crate::native_ffi::ViewModelBridge<$app>,
        }

        #[boltffi::export]
        impl $ffi {
            #[must_use]
            pub fn new() -> Self {
                Self::default()
            }

            #[must_use]
            pub fn update(&self, data: &[u8]) -> Vec<u8> {
                self.bridge.update(data)
            }

            #[must_use]
            pub fn resolve(&self, id: u32, data: &[u8]) -> Vec<u8> {
                self.bridge.resolve(id, data)
            }

            /// # Panics
            /// If the Rust view state cannot be serialized.
            #[must_use]
            pub fn view(&self) -> Vec<u8> {
                self.bridge.view()
            }
        }

        impl Default for $ffi {
            fn default() -> Self {
                Self {
                    bridge: $crate::native_ffi::ViewModelBridge::new(
                        <$app as ::crux_core::App>::Event::BridgeFailed,
                    ),
                }
            }
        }
    };
}

pub(crate) use export_view_model;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::counter::{CounterEffectFfi, CounterEvent, CounterState, CounterViewModelFfi};
    use crate::ui::UiError;
    use crux_core::bridge::{Request, Requests};

    fn encoded(value: &impl Serialize) -> Vec<u8> {
        let mut bytes = Vec::new();
        BincodeFfiFormat::serialize(&mut bytes, value).unwrap();
        bytes
    }

    fn send(core: &CounterViewModelFfi, event: &CounterEvent) -> Vec<u8> {
        core.update(&encoded(event))
    }

    fn assert_render(bytes: &[u8]) {
        let Requests(requests) =
            BincodeFfiFormat::deserialize::<Requests<CounterEffectFfi>>(bytes).unwrap();
        assert!(matches!(
            requests.as_slice(),
            [Request {
                id: EffectId::NOTIFICATION,
                effect: CounterEffectFfi::Render(_),
            }]
        ));
    }

    #[test]
    fn messages_roundtrip_errors_recover_and_instances_stay_independent() {
        let first = CounterViewModelFfi::new();
        let second = CounterViewModelFfi::new();
        assert_render(&send(&first, &CounterEvent::Initialize(i64::MAX)));
        send(&second, &CounterEvent::Initialize(5));
        assert_render(&send(&first, &CounterEvent::Increment));
        assert_eq!(
            first.view(),
            encoded(&CounterState {
                value: i64::MAX,
                revision: 0,
                error: Some(UiError {
                    code: "counter_overflow".into(),
                    message: "counter value or state revision would overflow".into()
                }),
            })
        );
        send(&first, &CounterEvent::Reset);
        assert_eq!(
            first.view(),
            encoded(&CounterState {
                value: 0,
                revision: 1,
                error: None
            })
        );
        assert_eq!(
            second.view(),
            encoded(&CounterState {
                value: 5,
                revision: 0,
                error: None
            })
        );
    }

    #[test]
    fn malformed_messages_render_an_error_and_preserve_the_count() {
        let core = CounterViewModelFfi::new();
        send(&core, &CounterEvent::Initialize(7));
        for event in [Vec::new(), vec![255; 4]] {
            let error = BridgeError::<BincodeFfiFormat>::DeserializeEvent(
                BincodeFfiFormat::deserialize::<CounterEvent>(&event).unwrap_err(),
            );
            assert_render(&core.update(&event));
            assert_eq!(
                core.view(),
                encoded(&CounterState {
                    value: 7,
                    revision: 0,
                    error: Some(UiError {
                        code: "counter_bridge_error".into(),
                        message: error.to_string(),
                    }),
                })
            );
            send(&core, &CounterEvent::Initialize(7));
        }
        assert_render(&core.resolve(0, &[]));
        send(&core, &CounterEvent::Increment);
        assert_eq!(
            core.view(),
            encoded(&CounterState {
                value: 8,
                revision: 1,
                error: None
            })
        );
    }
}
