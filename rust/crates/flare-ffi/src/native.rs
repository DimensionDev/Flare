use std::{
    future::Future,
    sync::{
        LazyLock,
        atomic::{AtomicU64, Ordering},
    },
};

use tokio::{runtime::Runtime, task::AbortHandle};

static RUNTIME: LazyLock<Result<Runtime, std::io::Error>> = LazyLock::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .thread_name("flare-rust")
        .build()
});

pub(crate) struct ResourceCounters {
    presenters: AtomicU64,
    subscriptions: AtomicU64,
    tasks: AtomicU64,
}

impl ResourceCounters {
    pub(crate) const fn new() -> Self {
        Self {
            presenters: AtomicU64::new(0),
            subscriptions: AtomicU64::new(0),
            tasks: AtomicU64::new(0),
        }
    }

    pub(crate) fn snapshot(&self) -> (u64, u64, u64) {
        (
            self.presenters.load(Ordering::SeqCst),
            self.subscriptions.load(Ordering::SeqCst),
            self.tasks.load(Ordering::SeqCst),
        )
    }

    pub(crate) fn presenter(&'static self) -> Resource {
        Resource::new(&self.presenters)
    }

    pub(crate) fn subscription(&'static self) -> Resource {
        Resource::new(&self.subscriptions)
    }
}

pub(crate) struct Resource(&'static AtomicU64);

impl Resource {
    fn new(counter: &'static AtomicU64) -> Self {
        counter.fetch_add(1, Ordering::SeqCst);
        Self(counter)
    }
}

impl Drop for Resource {
    fn drop(&mut self) {
        self.0.fetch_sub(1, Ordering::SeqCst);
    }
}

/// Owns task cancellation; the task's guard counts its actual lifetime.
pub(crate) struct NativeTask(AbortHandle);

impl NativeTask {
    pub(crate) fn spawn(
        task: impl Future<Output = ()> + Send + 'static,
        resources: &'static ResourceCounters,
    ) -> Result<Self, &'static std::io::Error> {
        let runtime = RUNTIME.as_ref()?;
        let resource = Resource::new(&resources.tasks);
        let task = runtime.spawn(async move {
            let _resource = resource;
            task.await;
        });
        Ok(Self(task.abort_handle()))
    }
}

impl Drop for NativeTask {
    fn drop(&mut self) {
        self.0.abort();
    }
}
