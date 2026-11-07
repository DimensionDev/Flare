use std::{
    collections::HashMap,
    fs,
    io::{self, Write as _},
    path::{Path, PathBuf},
    sync::{Arc, LazyLock, Mutex, PoisonError, Weak},
};

use tokio::sync::{OnceCell, oneshot};

use crate::{Error, directory, state::Records};

static DEFAULT: OnceCell<Backend> = OnceCell::const_new();
static STORES: LazyLock<Mutex<HashMap<PathBuf, Weak<Inner>>>> = LazyLock::new(Mutex::default);

#[derive(Clone)]
pub(crate) struct Backend {
    inner: Arc<Inner>,
}

struct Inner {
    root: PathBuf,
    records: Records,
    update_lock: Mutex<()>,
}

impl Backend {
    pub(crate) async fn open() -> Result<Self, Error> {
        DEFAULT
            .get_or_try_init(|| async {
                let root = blocking::unblock(directory::resolve).await?;
                Self::open_in(root).await
            })
            .await
            .cloned()
    }

    pub(crate) async fn open_in(root: PathBuf) -> Result<Self, Error> {
        let root = blocking::unblock(move || {
            fs::create_dir_all(&root)?;
            fs::canonicalize(root)
        })
        .await?;
        let mut stores = STORES.lock().unwrap_or_else(PoisonError::into_inner);
        stores.retain(|_, state| state.strong_count() != 0);
        if let Some(inner) = stores.get(&root).and_then(Weak::upgrade) {
            return Ok(Self { inner });
        }
        let inner = Arc::new(Inner {
            root: root.clone(),
            records: Records::default(),
            update_lock: Mutex::new(()),
        });
        stores.insert(root, Arc::downgrade(&inner));
        Ok(Self { inner })
    }

    pub(crate) fn records(&self) -> &Records {
        &self.inner.records
    }

    pub(crate) async fn update<R: Send + 'static, C: FnOnce() + Send + 'static>(
        &self,
        name: &'static str,
        edit: impl FnOnce(Option<Vec<u8>>) -> Result<(R, Option<Vec<u8>>, C), Error> + Send + 'static,
    ) -> Result<R, Error> {
        let inner = Arc::clone(&self.inner);
        let (sender, receiver) = oneshot::channel();
        // Queued work must survive cancellation before a worker starts it.
        blocking::unblock(move || {
            let result = (|| {
                let _guard = inner
                    .update_lock
                    .lock()
                    .unwrap_or_else(PoisonError::into_inner);
                let root = &inner.root;
                let path = root.join(name);
                let (result, replacement, publish) = edit(read_file(&path)?)?;
                if let Some(bytes) = replacement {
                    fs::create_dir_all(root)?;
                    // Reclaim the single staging file if the previous process died before commit.
                    let staging_name = format!("{name}.rust.tmp");
                    match fs::remove_file(root.join(&staging_name)) {
                        Ok(()) => {}
                        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                        Err(error) => return Err(error.into()),
                    }
                    let mut temporary = tempfile::Builder::new()
                        .prefix(&staging_name)
                        .rand_bytes(0)
                        .tempfile_in(root)?;
                    temporary.write_all(&bytes)?;
                    temporary.as_file().sync_all()?;
                    temporary.persist(path).map_err(|err| err.error)?;
                }
                publish();
                Ok(result)
            })();
            let _ = sender.send(result);
        })
        .detach();
        receiver.await.unwrap_or(Err(Error::Aborted))
    }
}

fn read_file(path: &Path) -> io::Result<Option<Vec<u8>>> {
    match fs::read(path) {
        Ok(bytes) => Ok(Some(bytes)),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error),
    }
}
