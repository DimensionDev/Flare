use crux_core::{
    App, Command,
    macros::effect,
    render::{RenderOperation, render},
};
use facet::Facet;
use serde::{Deserialize, Serialize};

use crate::domain::counter::{Counter, CounterOverflow};
use crate::ui::UiError;

#[derive(Clone, Copy, Debug, Facet, Serialize, Deserialize)]
#[repr(C)]
pub enum CounterEvent {
    Increment,
    Decrement,
    Reset,
}

#[effect(facet_typegen)]
#[derive(Debug)]
pub enum CounterEffect {
    Render(RenderOperation),
}

#[derive(Clone, Debug, PartialEq, Eq, Facet, Serialize)]
pub struct CounterState {
    pub value: i64,
    pub revision: u64,
    pub error: Option<UiError>,
}

pub struct Model {
    counter: Counter,
    revision: u64,
    error: Option<UiError>,
}

impl Model {
    #[must_use]
    pub const fn new(initial_value: i64) -> Self {
        Self {
            counter: Counter::new(initial_value),
            revision: 0,
            error: None,
        }
    }

    fn apply(&mut self, event: CounterEvent) -> Result<(), CounterOverflow> {
        let mut next = self.counter;
        match event {
            CounterEvent::Increment => next.increment()?,
            CounterEvent::Decrement => next.decrement()?,
            CounterEvent::Reset => next.reset(),
        }
        if next.value() != self.counter.value() {
            let revision = self.revision.checked_add(1).ok_or(CounterOverflow)?;
            self.counter = next;
            self.revision = revision;
        }
        Ok(())
    }
}

#[derive(Default)]
pub struct CounterApp;

impl App for CounterApp {
    type Event = CounterEvent;
    type Model = Model;
    type ViewModel = CounterState;
    type Effect = CounterEffect;

    fn update(
        &self,
        event: CounterEvent,
        model: &mut Model,
    ) -> Command<CounterEffect, CounterEvent> {
        let previous = self.view(model);
        model.error = model.apply(event).err().map(|_| UiError {
            code: "counter_overflow".into(),
            message: "counter value or state revision would overflow".into(),
        });
        if self.view(model) == previous {
            Command::done()
        } else {
            render()
        }
    }

    fn view(&self, model: &Model) -> CounterState {
        CounterState {
            value: model.counter.value(),
            revision: model.revision,
            error: model.error.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crux_core::Core;

    #[test]
    fn events_update_the_view_and_no_op_reset_does_not_render() {
        let core = Core::new_with(CounterApp, Model::new(7));
        assert_eq!(core.view().value, 7);
        assert_eq!(core.view().revision, 0);
        for (event, value, revision) in [
            (CounterEvent::Increment, 8, 1),
            (CounterEvent::Decrement, 7, 2),
            (CounterEvent::Reset, 0, 3),
        ] {
            assert!(matches!(
                core.process_event(event).as_slice(),
                [CounterEffect::Render(_)]
            ));
            assert_eq!(
                core.view(),
                CounterState {
                    value,
                    revision,
                    error: None
                }
            );
        }
        assert!(core.process_event(CounterEvent::Reset).is_empty());
        assert_eq!(core.view().revision, 3);
    }

    #[test]
    fn overflow_keeps_content_and_the_next_successful_event_clears_it() {
        for (initial, event) in [
            (i64::MAX, CounterEvent::Increment),
            (i64::MIN, CounterEvent::Decrement),
        ] {
            let core = Core::new_with(CounterApp, Model::new(initial));
            core.process_event(event);
            let failed = core.view();
            assert_eq!((failed.value, failed.revision), (initial, 0));
            assert_eq!(failed.error.unwrap().code, "counter_overflow");
            core.process_event(CounterEvent::Reset);
            assert_eq!(
                core.view(),
                CounterState {
                    value: 0,
                    revision: 1,
                    error: None
                }
            );
        }
    }

    #[test]
    fn revision_overflow_is_atomic_and_clearing_an_error_renders_even_on_a_no_op() {
        let core = Core::new_with(
            CounterApp,
            Model {
                revision: u64::MAX,
                ..Model::new(0)
            },
        );
        core.process_event(CounterEvent::Increment);
        assert_eq!((core.view().value, core.view().revision), (0, u64::MAX));
        assert!(core.view().error.is_some());
        assert!(matches!(
            core.process_event(CounterEvent::Reset).as_slice(),
            [CounterEffect::Render(_)]
        ));
        assert_eq!(
            core.view(),
            CounterState {
                value: 0,
                revision: u64::MAX,
                error: None
            }
        );
    }

    #[test]
    fn core_instances_and_owned_snapshots_are_independent() {
        let first = Core::new_with(CounterApp, Model::new(0));
        let second = Core::new_with(CounterApp, Model::new(9));
        let snapshot = first.view();
        first.process_event(CounterEvent::Increment);
        assert_eq!(snapshot.value, 0);
        assert_eq!(first.view().value, 1);
        assert_eq!(second.view().value, 9);
    }
}
