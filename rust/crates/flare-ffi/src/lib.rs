//! Crux message bridge used by the native platform shells.

// BoltFFI's generated exports use underscore-prefixed ABI helpers.
#![allow(clippy::used_underscore_items)]

use crux_core::{
    Core,
    bridge::{BincodeFfiFormat, Bridge, BridgeError, EffectId, FfiFormat},
};
use flare_core::counter::{CounterEvent, CounterViewModel, Model};

pub struct CounterViewModelFfi {
    core: Bridge<CounterViewModel>,
}

impl Default for CounterViewModelFfi {
    fn default() -> Self {
        Self {
            core: Bridge::new(Core::new_with(CounterViewModel, Model::new(0))),
        }
    }
}

impl CounterViewModelFfi {
    fn render_bridge_error(&self, error: &BridgeError) -> Vec<u8> {
        let mut event = Vec::new();
        BincodeFfiFormat::serialize(&mut event, &CounterEvent::BridgeFailed(error.to_string()))
            .expect("a counter error uses only bincode primitives");
        let mut requests = Vec::new();
        self.core
            .update(&event, &mut requests)
            .expect("the bridge can round-trip its own error event");
        requests
    }
}

#[boltffi::export]
impl CounterViewModelFfi {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    #[must_use]
    pub fn update(&self, data: &[u8]) -> Vec<u8> {
        let mut requests = Vec::new();
        match self.core.update(data, &mut requests) {
            Ok(()) => requests,
            Err(error) => self.render_bridge_error(&error),
        }
    }

    #[must_use]
    pub fn resolve(&self, id: u32, data: &[u8]) -> Vec<u8> {
        let mut requests = Vec::new();
        match self.core.resolve(EffectId(id), data, &mut requests) {
            Ok(()) => requests,
            Err(error) => self.render_bridge_error(&error),
        }
    }

    /// # Panics
    /// If the counter view cannot be serialized.
    #[must_use]
    pub fn view(&self) -> Vec<u8> {
        let mut state = Vec::new();
        self.core
            .view(&mut state)
            .expect("the counter view uses only bincode primitives");
        state
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crux_core::bridge::{Request, Requests};
    use flare_core::counter::{CounterEffectFfi, CounterState};
    use flare_core::ui::UiError;
    use serde::Serialize;

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
            let mut expected = Vec::new();
            let error = core.core.update(&event, &mut expected).unwrap_err();
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
