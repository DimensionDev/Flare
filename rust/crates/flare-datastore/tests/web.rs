#![cfg(target_arch = "wasm32")]

use flare_datastore::{
    DataStore, Error, WireMessage,
    stores::{APP_SETTINGS, PROXY_SETTINGS},
};
use futures::{FutureExt as _, StreamExt as _};
use idb::{Database, Factory, TransactionMode};
use js_sys::Uint8Array;
use std::sync::Arc;
use wasm_bindgen::JsValue;
use wasm_bindgen_test::{wasm_bindgen_test, wasm_bindgen_test_configure};
use web_sys::{BroadcastChannel, Event};

wasm_bindgen_test_configure!(run_in_browser);

#[wasm_bindgen_test]
async fn commits_updates_across_connections_and_reopens() {
    fn require_send<T: Send>(_: T) {}
    fn require_send_sync<T: Send + Sync>() {}

    require_send_sync::<DataStore>();
    require_send(DataStore::open());
    let first = DataStore::open().await.unwrap();
    let second = DataStore::open().await.unwrap();
    require_send(first.read(APP_SETTINGS));
    require_send(first.subscribe(APP_SETTINGS));
    require_send(first.update(APP_SETTINGS, |_| Ok(())));

    first
        .update(PROXY_SETTINGS, |record| {
            record.removed_accounts.clear();
            Ok(())
        })
        .await
        .unwrap();
    let mut changes = first.subscribe(PROXY_SETTINGS).await.unwrap();
    let mut other_changes = second.subscribe(PROXY_SETTINGS).await.unwrap();
    require_send(changes.next());
    let initial = changes.next().await.unwrap().unwrap();
    let other_initial = other_changes.next().await.unwrap().unwrap();
    assert!(Arc::ptr_eq(&initial, &other_initial));
    first.update(PROXY_SETTINGS, |_| Ok(())).await.unwrap();
    assert!(changes.next().now_or_never().is_none());

    let (one, two) = futures::join!(
        first.update(PROXY_SETTINGS, |record| {
            record.removed_accounts.push("first".into());
            Ok(())
        }),
        second.update(PROXY_SETTINGS, |record| {
            record.removed_accounts.push("second".into());
            Ok(())
        }),
    );
    one.unwrap();
    two.unwrap();
    let latest = changes.next().await.unwrap().unwrap();
    assert_eq!(latest.removed_accounts.len(), 2);
    assert!(Arc::ptr_eq(
        &latest,
        &other_changes.next().await.unwrap().unwrap()
    ));

    let aborted = second
        .update(PROXY_SETTINGS, |record| {
            record.removed_accounts.clear();
            Err::<(), _>(Error::Aborted)
        })
        .await;
    assert!(matches!(aborted, Err(Error::Aborted)));
    assert!(changes.next().now_or_never().is_none());

    let mut cancelled = Box::pin(first.update(PROXY_SETTINGS, |record| {
        record.removed_accounts.push("after-cancellation".into());
        Ok(())
    }));
    assert!(futures::poll!(&mut cancelled).is_pending());
    drop(cancelled);
    let published = changes.next().await.unwrap().unwrap();
    assert_eq!(
        published.removed_accounts.last().map(String::as_str),
        Some("after-cancellation")
    );
    drop(first);
    drop(second);

    let reopened = DataStore::open().await.unwrap();
    let mut accounts = reopened
        .read(PROXY_SETTINGS)
        .await
        .unwrap()
        .removed_accounts;
    accounts.sort();
    assert_eq!(accounts, ["after-cancellation", "first", "second"]);
    assert!(
        reopened
            .read(APP_SETTINGS)
            .await
            .unwrap()
            .refresh_home_timeline_on_launch()
    );
    external_changes_and_refresh_errors(&reopened).await;
}

async fn write_external(database: &Database, bytes: &[u8]) {
    let transaction = database
        .transaction(&["records"], TransactionMode::ReadWrite)
        .unwrap();
    transaction
        .object_store("records")
        .unwrap()
        .put(
            Uint8Array::from(bytes).as_ref(),
            Some(&JsValue::from_str(APP_SETTINGS.name())),
        )
        .unwrap()
        .await
        .unwrap();
    assert!(!transaction.await.unwrap().is_aborted());
}

async fn external_changes_and_refresh_errors(store: &DataStore) {
    let database = Factory::new()
        .unwrap()
        .open("flare-datastore", None)
        .unwrap()
        .await
        .unwrap();
    let notifications = BroadcastChannel::new("flare-datastore").unwrap();
    let mut changes = store.subscribe(APP_SETTINGS).await.unwrap();
    changes.next().await.unwrap().unwrap();
    let mut settings = store.read(APP_SETTINGS).await.unwrap();
    settings.language = Some("external".into());
    write_external(&database, &settings.write_to_bytes().unwrap()).await;
    notifications
        .post_message(&JsValue::from_str(APP_SETTINGS.name()))
        .unwrap();
    assert_eq!(
        changes.next().await.unwrap().unwrap().language.as_deref(),
        Some("external")
    );
    assert!(changes.next().now_or_never().is_none());

    write_external(&database, &[0x0a, 0xff]).await;
    notifications
        .post_message(&JsValue::from_str(APP_SETTINGS.name()))
        .unwrap();
    let error = changes.next().await.unwrap().unwrap_err();
    assert!(matches!(&*error, Error::Protobuf(_)));
    assert!(matches!(
        store.read(APP_SETTINGS).await,
        Err(Error::Protobuf(_))
    ));
    assert!(matches!(
        store.subscribe(APP_SETTINGS).await,
        Err(Error::Protobuf(_))
    ));

    settings.language = Some("recovered".into());
    write_external(&database, &settings.write_to_bytes().unwrap()).await;
    notifications
        .post_message(&JsValue::from_str(APP_SETTINGS.name()))
        .unwrap();
    assert_eq!(
        changes.next().await.unwrap().unwrap().language.as_deref(),
        Some("recovered")
    );

    settings.language = Some("resumed".into());
    write_external(&database, &settings.write_to_bytes().unwrap()).await;
    web_sys::window()
        .unwrap()
        .dispatch_event(&Event::new("pageshow").unwrap())
        .unwrap();
    assert_eq!(
        changes.next().await.unwrap().unwrap().language.as_deref(),
        Some("resumed")
    );
    drop(changes);
    store
        .update(APP_SETTINGS, |value| {
            value.language = Some("without-observers".into());
            Ok(())
        })
        .await
        .unwrap();
    let mut replay = store.subscribe(APP_SETTINGS).await.unwrap();
    assert_eq!(
        replay.next().await.unwrap().unwrap().language.as_deref(),
        Some("without-observers")
    );
    notifications.close();
    database.close();
}
