#[derive(Clone, Copy)]
pub(crate) struct Counter {
    value: i64,
}

pub(crate) struct CounterOverflow;

impl Counter {
    pub(crate) const fn new(value: i64) -> Self {
        Self { value }
    }

    pub(crate) const fn value(self) -> i64 {
        self.value
    }

    pub(crate) fn increment(&mut self) -> Result<(), CounterOverflow> {
        self.value = self.value.checked_add(1).ok_or(CounterOverflow)?;
        Ok(())
    }

    pub(crate) fn decrement(&mut self) -> Result<(), CounterOverflow> {
        self.value = self.value.checked_sub(1).ok_or(CounterOverflow)?;
        Ok(())
    }

    pub(crate) fn reset(&mut self) {
        self.value = 0;
    }
}
