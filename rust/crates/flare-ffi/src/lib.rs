//! Crux message bridge used by the native platform shells.

// BoltFFI's generated exports use underscore-prefixed ABI helpers.
#![allow(clippy::used_underscore_items)]

use crux_core::{Core, bridge::Bridge};
use flare_core::counter::{CounterApp, Model};

pub struct CounterCore {
    core: Bridge<CounterApp>,
}

// shortcut: BoltFFI 0.29.3's i64 constructor collides with Kotlin's handle constructor; use a free factory until fixed upstream.
#[boltffi::export]
#[must_use]
pub fn create_counter_core(initial_value: i64) -> CounterCore {
    CounterCore {
        core: Bridge::new(Core::new_with(CounterApp, Model::new(initial_value))),
    }
}

#[boltffi::export]
impl CounterCore {
    /// # Errors
    /// Rejects malformed events without changing the counter.
    pub fn update(&self, event: &[u8]) -> Result<Vec<u8>, String> {
        let mut requests = Vec::new();
        self.core
            .update(event, &mut requests)
            .map_err(|error| error.to_string())?;
        Ok(requests)
    }

    /// # Errors
    /// Reports serialization failures to the platform shell.
    pub fn view(&self) -> Result<Vec<u8>, String> {
        let mut state = Vec::new();
        self.core
            .view(&mut state)
            .map_err(|error| error.to_string())?;
        Ok(state)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crux_core::bridge::{BincodeFfiFormat, FfiFormat};
    use flare_core::counter::{CounterEvent, CounterState};
    use flare_core::ui::UiError;
    use serde::Serialize;

    fn encoded(value: &impl Serialize) -> Vec<u8> {
        let mut bytes = Vec::new();
        BincodeFfiFormat::serialize(&mut bytes, value).unwrap();
        bytes
    }

    fn send(core: &CounterCore, event: CounterEvent) -> Vec<u8> {
        core.update(&encoded(&event)).unwrap()
    }

    #[test]
    fn messages_roundtrip_errors_recover_and_instances_stay_independent() {
        let first = create_counter_core(i64::MAX);
        let second = create_counter_core(5);
        assert!(!send(&first, CounterEvent::Increment).is_empty());
        assert_eq!(
            first.view().unwrap(),
            encoded(&CounterState {
                value: i64::MAX,
                revision: 0,
                error: Some(UiError {
                    code: "counter_overflow".into(),
                    message: "counter value or state revision would overflow".into()
                }),
            })
        );
        send(&first, CounterEvent::Reset);
        assert_eq!(
            first.view().unwrap(),
            encoded(&CounterState {
                value: 0,
                revision: 1,
                error: None
            })
        );
        assert_eq!(
            second.view().unwrap(),
            encoded(&CounterState {
                value: 5,
                revision: 0,
                error: None
            })
        );
    }

    #[test]
    fn malformed_messages_preserve_state_and_do_not_poison_the_core() {
        let core = create_counter_core(7);
        let initial = core.view().unwrap();
        for event in [vec![], vec![255; 4]] {
            assert!(core.update(&event).is_err());
            assert_eq!(core.view().unwrap(), initial);
        }
        send(&core, CounterEvent::Increment);
        assert_eq!(
            core.view().unwrap(),
            encoded(&CounterState {
                value: 8,
                revision: 1,
                error: None
            })
        );
    }
}
