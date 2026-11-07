use std::path::PathBuf;

use crate::Error;

#[cfg(target_os = "android")]
static ANDROID_FILES_DIR: std::sync::OnceLock<PathBuf> = std::sync::OnceLock::new();

#[cfg(target_os = "android")]
pub(crate) fn initialize_android(files_dir: &std::path::Path) -> Result<(), Error> {
    if !files_dir.is_absolute() {
        return Err(Error::Directory("Android filesDir must be absolute"));
    }
    if ANDROID_FILES_DIR.get_or_init(|| files_dir.to_path_buf()) != files_dir {
        return Err(Error::Directory(
            "Android filesDir has already been registered",
        ));
    }
    Ok(())
}

#[cfg(any(
    target_os = "android",
    target_os = "ios",
    target_os = "macos",
    target_os = "linux",
    windows
))]
pub(crate) fn resolve() -> Result<PathBuf, Error> {
    #[cfg(target_os = "android")]
    let root = ANDROID_FILES_DIR
        .get()
        .map(|files_dir| files_dir.join("datastore"))
        .ok_or(Error::Directory("Android filesDir has not been registered"))?;

    #[cfg(target_os = "ios")]
    let root = objc2::rc::autoreleasepool(|_| {
        use objc2_foundation::{NSFileManager, NSSearchPathDirectory, NSSearchPathDomainMask};

        let url = NSFileManager::defaultManager()
            .URLForDirectory_inDomain_appropriateForURL_create_error(
                NSSearchPathDirectory::DocumentDirectory,
                NSSearchPathDomainMask::UserDomainMask,
                None,
                false,
            )
            .map_err(|error| std::io::Error::other(error.to_string()))?;
        url.path()
            .map(|path| PathBuf::from(path.to_string()))
            .ok_or(Error::Directory("iOS Documents directory unavailable"))
    })?;

    #[cfg(target_os = "macos")]
    let root = objc2::rc::autoreleasepool(|_| {
        PathBuf::from(objc2_foundation::NSHomeDirectory().to_string()).join(".flare")
    });

    #[cfg(target_os = "linux")]
    let root = {
        use nix::unistd::{Uid, User};

        // The JVM looks up the account's home before falling back to HOME.
        User::from_uid(Uid::current())
            .map_err(std::io::Error::from)?
            .map(|user| user.dir)
            .filter(|home| home.as_os_str().len() > 1)
            .or_else(|| std::env::var_os("HOME").map(PathBuf::from))
            .map(|home| home.join(".flare"))
            .ok_or(Error::Directory("Linux user home directory unavailable"))?
    };

    // The JVM uses Windows' FOLDERID_Profile, which can differ from USERPROFILE.
    #[cfg(windows)]
    let root = dirs::home_dir()
        .map(|home| home.join(".flare"))
        .ok_or(Error::Directory(
            "Windows user profile directory unavailable",
        ))?;

    if !root.is_absolute() {
        return Err(Error::Directory("expected an absolute path"));
    }
    Ok(root)
}

#[cfg(not(any(
    target_os = "android",
    target_os = "ios",
    target_os = "macos",
    target_os = "linux",
    windows
)))]
pub(crate) fn resolve() -> Result<PathBuf, Error> {
    Err(Error::Directory(
        "default directory unsupported on this platform",
    ))
}
