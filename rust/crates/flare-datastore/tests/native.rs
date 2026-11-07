#![cfg(not(target_arch = "wasm32"))]

use std::{
    fs,
    sync::{Arc, mpsc},
    time::Duration,
};

use buffa::{EnumValue, MessageField, UnknownField, UnknownFieldData, UnknownFields};
use flare_datastore::{
    DataStore, Error, ProtoError, StoreKey, WireMessage,
    model::{
        AppSettings, DeepLTranslationProvider, ExternalText, GoogleWebTranslationProvider,
        MicroBlogKey, MixedIcon, NumberTextArgument, OpenAiConfig, OpenLinkInAccount,
        OpenLinkInBrowser, ProxyMode, TabSettingsV2, TimelineAutoRefreshInterval, TimelineGroup,
        TimelineSource, UiIcon, Visibility,
    },
    stores::{
        APP_SETTINGS, APPEARANCE_BAG, COMPOSE_CONFIG, DISCOVER_PREFERENCES, FLARE_CONFIG,
        HOME_TIMELINE_PREFERENCES, NOTIFICATION_PREFERENCES, PLATFORM_OAUTH_PENDING,
        PROXY_SETTINGS, TAB_SETTINGS,
    },
};
use futures::{FutureExt as _, StreamExt as _, executor::block_on, future::try_join_all};

// Synthetic data emitted by the Flare f8b303e02 serializers with kotlinx ProtoBuf 1.12.0.
const SETTINGS: &[u8] = include_bytes!("fixtures/app_settings.pb");
const TABS: &[u8] = include_bytes!("fixtures/tab_settings_v2.pb");

#[cfg(target_os = "macos")]
#[test]
fn opens_the_existing_macos_datastore() {
    use std::process::Command;

    // Foundation caches the home directory, so isolate its override in a child.
    if std::env::var_os("FLARE_DATASTORE_TEST_HOME").is_some() {
        let stores = block_on(try_join_all((0..8).map(|_| DataStore::open()))).unwrap();
        let store = &stores[0];
        let settings = block_on(store.read(APP_SETTINGS)).unwrap();
        assert_eq!(settings.write_to_bytes().unwrap(), SETTINGS);
        let mut first = block_on(store.subscribe(APP_SETTINGS)).unwrap();
        let mut second = block_on(stores[1].subscribe(APP_SETTINGS)).unwrap();
        assert!(Arc::ptr_eq(
            &block_on(first.next()).unwrap().unwrap(),
            &block_on(second.next()).unwrap().unwrap(),
        ));
        return;
    }

    let user_home = tempfile::tempdir().unwrap();
    let root = user_home.path().join(".flare");
    fs::create_dir(&root).unwrap();
    fs::write(root.join(APP_SETTINGS.name()), SETTINGS).unwrap();
    let status = Command::new(std::env::current_exe().unwrap())
        .args([
            "--exact",
            "opens_the_existing_macos_datastore",
            "--nocapture",
        ])
        .env("CFFIXED_USER_HOME", user_home.path())
        .env("FLARE_DATASTORE_TEST_HOME", user_home.path())
        .status()
        .unwrap();
    assert!(status.success());
}

#[cfg(target_os = "android")]
#[test]
fn opens_the_existing_android_datastore() {
    assert!(matches!(
        block_on(DataStore::open()),
        Err(Error::Directory(_))
    ));
    assert!(DataStore::initialize_android("relative").is_err());

    let files_dir = tempfile::tempdir().unwrap();
    let root = files_dir.path().join("datastore");
    fs::create_dir(&root).unwrap();
    fs::write(root.join(APP_SETTINGS.name()), SETTINGS).unwrap();

    DataStore::initialize_android(files_dir.path()).unwrap();
    DataStore::initialize_android(files_dir.path()).unwrap();
    assert!(DataStore::initialize_android(files_dir.path().join("other")).is_err());

    let store = block_on(DataStore::open()).unwrap();
    let settings = block_on(store.read(APP_SETTINGS)).unwrap();
    assert_eq!(settings.write_to_bytes().unwrap(), SETTINGS);
}

fn round_trip<T: WireMessage>(key: StoreKey<T>, fixture: &[u8], edit: fn(&mut T)) {
    let directory = tempfile::tempdir().unwrap();
    let name = key.name();
    let path = directory.path().join(name);
    fs::write(&path, fixture).unwrap();
    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    let mut changes = block_on(store.subscribe(key.clone())).unwrap();
    let initial = block_on(changes.next()).unwrap().unwrap();
    assert_eq!(initial.write_to_bytes().unwrap(), fixture, "{name}");
    let mut expected = block_on(store.read(key.clone())).unwrap();
    assert_eq!(expected.write_to_bytes().unwrap(), fixture, "{name}");
    edit(&mut expected);
    block_on(store.update(key.clone(), move |record| {
        edit(record);
        Ok(())
    }))
    .unwrap();
    let published = changes
        .next()
        .now_or_never()
        .expect("updates publish before returning")
        .unwrap()
        .unwrap();
    assert!(published.as_ref() == &expected, "{name}");
    let persisted = fs::read(&path).unwrap();
    assert!(
        T::parse_from_bytes(&persisted).unwrap() == expected,
        "{name}"
    );
    drop(changes);
    drop(store);
    let reopened = block_on(DataStore::open_in(directory.path())).unwrap();
    assert!(block_on(reopened.read(key)).unwrap() == expected, "{name}");
    assert_ne!(persisted, fixture);
}

#[test]
fn reads_and_updates_every_current_kotlin_store() {
    round_trip(
        NOTIFICATION_PREFERENCES,
        include_bytes!("fixtures/notification_preferences.pb"),
        |record| record.selected_account_key = MessageField::none(),
    );
    round_trip(
        DISCOVER_PREFERENCES,
        include_bytes!("fixtures/discover_preferences.pb"),
        |record| {
            record.selected_account_key.as_option_mut().unwrap().id = Some("updated".into());
        },
    );
    round_trip(
        HOME_TIMELINE_PREFERENCES,
        include_bytes!("fixtures/home_timeline_preferences.pb"),
        |record| {
            assert_eq!(
                record.positions[0].value.position.offset.unwrap().to_bits(),
                (-3.25_f64).to_bits()
            );
            record.selected_tab_id = Some("updated".into());
        },
    );
    round_trip(
        FLARE_CONFIG,
        include_bytes!("fixtures/flare_config.pb"),
        |record| record.server_url = Some("https://rust.example".into()),
    );
    round_trip(
        COMPOSE_CONFIG,
        include_bytes!("fixtures/compose_config.pb"),
        |record| {
            assert_eq!(
                record.visibility,
                Some(Visibility::VISIBILITY_PUBLIC.into())
            );
            record.visibility = Some(Visibility::VISIBILITY_SELF_ONLY.into());
        },
    );
    round_trip(APP_SETTINGS, SETTINGS, |record| {
        record.language = Some("en".into());
    });
    round_trip(
        PLATFORM_OAUTH_PENDING,
        include_bytes!("fixtures/platform_oauth_pending_v2.pb"),
        |record| {
            assert_eq!(record.entries[0].flow_id(), "OAuth");
            record.entries[0].flow_id = Some("updated".into());
        },
    );
    round_trip(
        APPEARANCE_BAG,
        include_bytes!("fixtures/appearance_bag.pb"),
        |record| {
            assert_eq!(record.schema_version(), 2);
            record.entries[0].value = Some("light".into());
        },
    );
    round_trip(TAB_SETTINGS, TABS, |record| {
        let content = record.home_slots[0].content.as_option_mut().unwrap();
        let mut group = content.payload_as::<TimelineGroup>().unwrap().unwrap();
        group.children[0]
            .presentation
            .as_option_mut()
            .unwrap()
            .enabled = Some(true);
        content.replace_payload(&group).unwrap();
    });
    round_trip(
        PROXY_SETTINGS,
        include_bytes!("fixtures/proxy_settings.pb"),
        |record| {
            assert_eq!(
                record.accounts[0].value.mode(),
                EnumValue::Known(ProxyMode::PROXY_MODE_INHERIT)
            );
            record.profiles[0].port = Some(8080);
        },
    );
}

#[test]
fn decodes_nested_kotlin_variants() {
    let settings = AppSettings::parse_from_bytes(SETTINGS).unwrap();
    assert!(!settings.refresh_home_timeline_on_launch());
    assert_eq!(
        settings.home_timeline_auto_refresh_interval,
        Some(TimelineAutoRefreshInterval::TIMELINE_AUTO_REFRESH_INTERVAL_ONE_MINUTE.into()),
    );
    let model = settings
        .ai_config
        .effective_model_type()
        .payload_as::<OpenAiConfig>()
        .unwrap()
        .unwrap();
    assert_eq!(model.context_window, Some(4096));
    assert_eq!(settings.ai_config.translate_prompt(), "");
    let provider = settings
        .translate_config
        .effective_provider()
        .payload_as::<DeepLTranslationProvider>()
        .unwrap()
        .unwrap();
    assert_eq!(provider.use_pro, Some(true));
    let rules = &settings.link_open_defaults.rules;
    let account = rules[0]
        .method
        .payload_as::<OpenLinkInAccount>()
        .unwrap()
        .unwrap();
    assert_eq!(account.account_key.id.as_deref(), Some("用戶@alice"));
    assert!(
        rules[1]
            .method
            .payload_as::<OpenLinkInBrowser>()
            .unwrap()
            .is_some()
    );

    let tabs = TabSettingsV2::parse_from_bytes(TABS).unwrap();
    assert!(tabs.home_slots[0].presentation.enabled());
    let group = tabs.home_slots[0]
        .content
        .payload_as::<TimelineGroup>()
        .unwrap()
        .unwrap();
    let child = &group.children[0];
    assert!(!child.presentation.enabled());
    let source = child
        .content
        .payload_as::<TimelineSource>()
        .unwrap()
        .unwrap();
    let icon = source
        .source
        .icon
        .payload_as::<MixedIcon>()
        .unwrap()
        .unwrap();
    assert_eq!(icon.icon, Some(UiIcon::UI_ICON_MASTODON.into()));
    let text = source
        .source
        .title
        .payload_as::<ExternalText>()
        .unwrap()
        .unwrap();
    let count = text
        .args
        .iter()
        .find(|arg| arg.key.as_deref() == Some("count"))
        .unwrap();
    let number = count
        .value
        .payload_as::<NumberTextArgument>()
        .unwrap()
        .unwrap();
    assert_eq!(number.value.unwrap().to_bits(), 2.5_f64.to_bits());
}

#[test]
fn absent_records_and_fields_resolve_to_kotlin_defaults() {
    let directory = tempfile::tempdir().unwrap();
    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    block_on(async {
        let settings = store.read(APP_SETTINGS).await.unwrap();
        let fixture = include_bytes!("fixtures/app_settings_defaults.pb");
        assert_eq!(settings.write_to_bytes().unwrap(), fixture);
        assert!(settings.refresh_home_timeline_on_launch());
        assert!(settings.ai_config.agent());
        assert_eq!(
            settings.ai_config.translate_prompt(),
            include_str!("fixtures/default-translate-prompt.txt"),
        );
        assert_eq!(
            settings.ai_config.tldr_prompt(),
            include_str!("fixtures/default-tldr-prompt.txt"),
        );
        assert!(
            settings
                .ai_config
                .effective_model_type()
                .payload_as::<OpenAiConfig>()
                .unwrap()
                .is_some()
        );
        assert!(
            settings
                .translate_config
                .effective_provider()
                .payload_as::<GoogleWebTranslationProvider>()
                .unwrap()
                .is_some()
        );
        assert_eq!(
            store.read(FLARE_CONFIG).await.unwrap().server_url(),
            "https://api.flareapp.moe"
        );
        assert_eq!(
            store
                .read(PROXY_SETTINGS)
                .await
                .unwrap()
                .global_policy()
                .mode(),
            EnumValue::Known(ProxyMode::PROXY_MODE_SYSTEM)
        );
        store.update(APP_SETTINGS, |_| Ok(())).await.unwrap();
        assert!(!directory.path().join(APP_SETTINGS.name()).exists());
    });
}

#[test]
fn retains_unknown_fields_in_messages_and_map_entries() {
    fn add_unknown(fields: &mut UnknownFields, number: u32) {
        fields.push(UnknownField {
            number,
            data: UnknownFieldData::Varint(u64::from(number)),
        });
    }

    let directory = tempfile::tempdir().unwrap();
    let mut settings = AppSettings::parse_from_bytes(SETTINGS).unwrap();
    add_unknown(&mut settings.__buffa_unknown_fields, 100);
    let config = settings.image_translation_config.as_option_mut().unwrap();
    add_unknown(&mut config.__buffa_unknown_fields, 101);
    add_unknown(
        &mut config.settings[0].values[0].__buffa_unknown_fields,
        102,
    );
    let ai_config = settings.ai_config.as_option_mut().unwrap();
    let variant = ai_config.model_type.as_option_mut().unwrap();
    add_unknown(&mut variant.__buffa_unknown_fields, 103);
    let mut model = variant.payload_as::<OpenAiConfig>().unwrap().unwrap();
    add_unknown(&mut model.__buffa_unknown_fields, 104);
    variant.replace_payload(&model).unwrap();
    let path = directory.path().join(APP_SETTINGS.name());
    fs::write(&path, settings.write_to_bytes().unwrap()).unwrap();
    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    block_on(store.update(APP_SETTINGS, |value| {
        value.language = Some("en".into());
        Ok(())
    }))
    .unwrap();
    settings.language = Some("en".into());
    assert_eq!(block_on(store.read(APP_SETTINGS)).unwrap(), settings);
    assert_eq!(
        AppSettings::parse_from_bytes(&fs::read(path).unwrap()).unwrap(),
        settings
    );
}

#[test]
fn corrupt_records_and_failed_edits_keep_the_original() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(APP_SETTINGS.name());
    let store = block_on(DataStore::open_in(directory.path())).unwrap();

    for damaged in [&[][..], &[0x0a, 0xff][..]] {
        fs::write(&path, damaged).unwrap();
        assert!(matches!(
            block_on(store.read(APP_SETTINGS)),
            Err(Error::Protobuf(_))
        ));
        assert!(matches!(
            block_on(store.subscribe(APP_SETTINGS)),
            Err(Error::Protobuf(_))
        ));
        assert!(matches!(
            block_on(store.update(APP_SETTINGS, |_| panic!("edit must not run"))),
            Err::<(), _>(Error::Protobuf(_)),
        ));
        assert_eq!(fs::read(&path).unwrap(), damaged);
    }

    fs::write(&path, SETTINGS).unwrap();
    let result = block_on(store.update(APP_SETTINGS, |value| {
        value.language = Some("discarded".into());
        Err::<(), _>(Error::Aborted)
    }));
    assert!(matches!(result, Err(Error::Aborted)));
    assert_eq!(fs::read(&path).unwrap(), SETTINGS);

    let invalid = block_on(store.update(APP_SETTINGS, |value| {
        value.version = None;
        Ok(())
    }));
    assert!(matches!(
        invalid,
        Err(Error::Protobuf(ProtoError::MissingRequired(
            "AppSettings.version"
        )))
    ));
    assert_eq!(fs::read(path).unwrap(), SETTINGS);
}

#[test]
fn validates_required_fields_inside_messages_and_repeated_entries() {
    let directory = tempfile::tempdir().unwrap();
    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    let notification = directory.path().join(NOTIFICATION_PREFERENCES.name());
    let missing_host = [0x0a, 0x02, 0x0a, 0x00];
    fs::write(&notification, missing_host).unwrap();
    assert!(matches!(
        block_on(store.read(NOTIFICATION_PREFERENCES)),
        Err(Error::Protobuf(ProtoError::MissingRequired(
            "MicroBlogKey.host"
        )))
    ));
    assert_eq!(fs::read(notification).unwrap(), missing_host);

    let compose = directory.path().join(COMPOSE_CONFIG.name());
    let original = include_bytes!("fixtures/compose_config.pb");
    fs::write(&compose, original).unwrap();
    let update = block_on(store.update(COMPOSE_CONFIG, |record| {
        record.last_accounts[0].host = None;
        Ok(())
    }));
    assert!(matches!(
        update,
        Err(Error::Protobuf(ProtoError::MissingRequired(
            "MicroBlogKey.host"
        )))
    ));
    assert_eq!(fs::read(compose).unwrap(), original);

    let home = directory.path().join(HOME_TIMELINE_PREFERENCES.name());
    let original = include_bytes!("fixtures/home_timeline_preferences.pb");
    fs::write(&home, original).unwrap();
    let update = block_on(store.update(HOME_TIMELINE_PREFERENCES, |record| {
        record.positions[0].value.as_option_mut().unwrap().position = MessageField::none();
        Ok(())
    }));
    assert!(matches!(
        update,
        Err(Error::Protobuf(ProtoError::MissingRequired(
            "HomeTimelinePosition.position"
        )))
    ));
    assert_eq!(fs::read(home).unwrap(), original);
}

#[test]
fn keeps_unknown_enum_values_until_the_field_is_replaced() {
    let directory = tempfile::tempdir().unwrap();
    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    let path = directory.path().join(COMPOSE_CONFIG.name());
    fs::write(&path, [0x08, 99]).unwrap();
    block_on(async {
        assert_eq!(
            store.read(COMPOSE_CONFIG).await.unwrap().visibility,
            Some(EnumValue::Unknown(99))
        );
        store
            .update(COMPOSE_CONFIG, |record| {
                record.last_accounts.push(MicroBlogKey {
                    id: Some("account".into()),
                    host: Some("example.org".into()),
                    ..MicroBlogKey::default()
                });
                Ok(())
            })
            .await
            .unwrap();
        assert_eq!(
            store.read(COMPOSE_CONFIG).await.unwrap().visibility,
            Some(EnumValue::Unknown(99))
        );
        store
            .update(COMPOSE_CONFIG, |record| {
                record.visibility = Some(Visibility::VISIBILITY_SELF_ONLY.into());
                record.last_accounts.clear();
                Ok(())
            })
            .await
            .unwrap();
    });
    assert_eq!(fs::read(path).unwrap(), [0x08, 6]);
}

#[test]
fn concurrent_handles_edit_the_latest_record() {
    let directory = tempfile::tempdir().unwrap();
    let stores: Vec<_> = (0..24)
        .map(|_| block_on(DataStore::open_in(directory.path())).unwrap())
        .collect();
    let mut changes = block_on(stores[0].subscribe(PROXY_SETTINGS)).unwrap();
    block_on(changes.next()).unwrap().unwrap();
    block_on(try_join_all(stores.iter().enumerate().map(
        |(index, store)| {
            store.update(PROXY_SETTINGS, move |record| {
                record.removed_accounts.push(format!("account-{index}"));
                Ok(())
            })
        },
    )))
    .unwrap();
    let published = changes.next().now_or_never().unwrap().unwrap().unwrap();
    assert_eq!(published.removed_accounts.len(), 24);
    let reopened = block_on(DataStore::open_in(directory.path())).unwrap();
    let mut removed_accounts = block_on(reopened.read(PROXY_SETTINGS))
        .unwrap()
        .removed_accounts;
    removed_accounts.sort();
    let mut expected: Vec<_> = (0..24).map(|index| format!("account-{index}")).collect();
    expected.sort();
    assert_eq!(removed_accounts, expected);
}

#[test]
fn shared_handles_replay_conflate_and_keep_subscriptions_alive() {
    fn require_send<T: Send>(_: T) {}
    fn require_send_sync<T: Send + Sync>() {}

    require_send_sync::<DataStore>();
    require_send(DataStore::open());
    let directory = tempfile::tempdir().unwrap();
    block_on(async {
        let first = DataStore::open_in(directory.path()).await.unwrap();
        let second = DataStore::open_in(directory.path().join("."))
            .await
            .unwrap();
        require_send(first.read(APP_SETTINGS));
        require_send(first.subscribe(APP_SETTINGS));
        require_send(first.update(APP_SETTINGS, |_| Ok(())));
        let mut changes = first.subscribe(APP_SETTINGS).await.unwrap();
        let mut other_changes = second.subscribe(APP_SETTINGS).await.unwrap();
        require_send(changes.next());
        let initial = changes.next().await.unwrap().unwrap();
        let other_initial = other_changes.next().await.unwrap().unwrap();
        assert!(Arc::ptr_eq(&initial, &other_initial));
        first.update(APP_SETTINGS, |_| Ok(())).await.unwrap();
        assert!(changes.next().now_or_never().is_none());
        assert!(!directory.path().join(APP_SETTINGS.name()).exists());

        let clone = first.clone();
        drop(first);
        drop(second);
        for language in ["en", "zh-Hans"] {
            clone
                .update(APP_SETTINGS, move |value| {
                    value.language = Some(language.into());
                    Ok(())
                })
                .await
                .unwrap();
        }
        let latest = changes.next().await.unwrap().unwrap();
        assert_eq!(latest.language.as_deref(), Some("zh-Hans"));
        assert!(Arc::ptr_eq(
            &latest,
            &other_changes.next().await.unwrap().unwrap()
        ));
        assert!(changes.next().now_or_never().is_none());
        clone
            .update(APP_SETTINGS, |value| {
                value.language = Some("zh-Hans".into());
                Ok(())
            })
            .await
            .unwrap();
        assert!(changes.next().now_or_never().is_none());
        drop(clone);

        let reopened = DataStore::open_in(directory.path()).await.unwrap();
        let mut replay = reopened.subscribe(APP_SETTINGS).await.unwrap();
        assert!(Arc::ptr_eq(&latest, &replay.next().await.unwrap().unwrap()));
        drop(changes);
        drop(other_changes);
        drop(replay);
        reopened
            .update(APP_SETTINGS, |value| {
                value.language = Some("ja".into());
                Ok(())
            })
            .await
            .unwrap();
        let mut replay = reopened.subscribe(APP_SETTINGS).await.unwrap();
        assert_eq!(
            replay.next().await.unwrap().unwrap().language.as_deref(),
            Some("ja")
        );
    });
}

#[test]
fn same_message_type_has_separate_record_streams() {
    let directory = tempfile::tempdir().unwrap();
    block_on(async {
        let store = DataStore::open_in(directory.path()).await.unwrap();
        let mut notification = store.subscribe(NOTIFICATION_PREFERENCES).await.unwrap();
        let mut discover = store.subscribe(DISCOVER_PREFERENCES).await.unwrap();
        notification.next().await.unwrap().unwrap();
        discover.next().await.unwrap().unwrap();
        store
            .update(NOTIFICATION_PREFERENCES, |value| {
                value.selected_account_key = MessageField::some(MicroBlogKey {
                    id: Some("account".into()),
                    host: Some("example.org".into()),
                    ..MicroBlogKey::default()
                });
                Ok(())
            })
            .await
            .unwrap();
        assert_eq!(
            notification
                .next()
                .await
                .unwrap()
                .unwrap()
                .selected_account_key
                .id
                .as_deref(),
            Some("account")
        );
        assert!(discover.next().now_or_never().is_none());
    });
}

#[test]
fn first_subscribers_racing_updates_receive_the_latest_commit() {
    let directory = tempfile::tempdir().unwrap();
    block_on(async {
        let store = DataStore::open_in(directory.path()).await.unwrap();
        let subscriptions = try_join_all((0..16).map(|_| store.subscribe(PROXY_SETTINGS)));
        let writes = try_join_all((0..16).map(|index| {
            store.update(PROXY_SETTINGS, move |value| {
                value.removed_accounts.push(format!("account-{index}"));
                Ok(())
            })
        }));
        let (subscriptions, writes) = futures::join!(subscriptions, writes);
        writes.unwrap();
        for mut subscription in subscriptions.unwrap() {
            let snapshot = subscription
                .next()
                .now_or_never()
                .unwrap()
                .unwrap()
                .unwrap();
            assert_eq!(snapshot.removed_accounts.len(), 16);
        }
    });
}

#[test]
fn failed_writes_keep_the_published_snapshot_and_can_retry() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join(APP_SETTINGS.name());
    fs::write(&path, SETTINGS).unwrap();
    block_on(async {
        let store = DataStore::open_in(directory.path()).await.unwrap();
        let mut changes = store.subscribe(APP_SETTINGS).await.unwrap();
        let initial = changes.next().await.unwrap().unwrap();
        let staging = directory
            .path()
            .join(format!("{}.rust.tmp", APP_SETTINGS.name()));
        fs::create_dir(&staging).unwrap();
        let failed = store
            .update(APP_SETTINGS, |value| {
                value.language = Some("retried-write".into());
                Ok(())
            })
            .await;
        assert!(matches!(failed, Err(Error::Io(_))), "{failed:?}");
        assert_eq!(store.read(APP_SETTINGS).await.unwrap(), *initial);
        assert_eq!(fs::read(&path).unwrap(), SETTINGS);
        assert!(changes.next().now_or_never().is_none());

        fs::remove_dir(staging).unwrap();
        store
            .update(APP_SETTINGS, |value| {
                value.language = Some("retried-write".into());
                Ok(())
            })
            .await
            .unwrap();
        let published = changes.next().await.unwrap().unwrap();
        assert_eq!(published.language.as_deref(), Some("retried-write"));
        assert_eq!(fs::read(path).unwrap(), published.write_to_bytes().unwrap());
    });
}

#[test]
fn cancelled_updates_still_commit_and_publish() {
    let directory = tempfile::tempdir().unwrap();
    block_on(async {
        let store = DataStore::open_in(directory.path()).await.unwrap();
        let mut changes = store.subscribe(APP_SETTINGS).await.unwrap();
        changes.next().await.unwrap().unwrap();
        let (started_sender, started_receiver) = mpsc::channel();
        let (release_sender, release_receiver) = mpsc::channel();
        let mut update = Box::pin(store.update(APP_SETTINGS, move |value| {
            value.language = Some("committed".into());
            started_sender.send(()).unwrap();
            release_receiver.recv().unwrap();
            Ok(())
        }));
        assert!(futures::poll!(update.as_mut()).is_pending());
        started_receiver
            .recv_timeout(Duration::from_secs(5))
            .unwrap();
        assert!(changes.next().now_or_never().is_none());
        assert!(store.read(APP_SETTINGS).await.unwrap().language.is_none());
        drop(update);
        release_sender.send(()).unwrap();
        // This transaction waits behind the cancelled write and its publication.
        store.update(FLARE_CONFIG, |_| Ok(())).await.unwrap();
        let published = changes.next().now_or_never().unwrap().unwrap().unwrap();
        assert_eq!(published.language.as_deref(), Some("committed"));
        assert_eq!(
            fs::read(directory.path().join(APP_SETTINGS.name())).unwrap(),
            published.write_to_bytes().unwrap()
        );
    });
}

#[cfg(not(any(target_os = "android", target_os = "ios")))]
#[test]
fn cancelled_queued_updates_still_commit_and_publish() {
    // Isolate the blocking pool's thread limit from the other tests.
    if std::env::var_os("FLARE_DATASTORE_TEST_QUEUED_CANCELLATION").is_none() {
        let status = std::process::Command::new(std::env::current_exe().unwrap())
            .args([
                "--exact",
                "cancelled_queued_updates_still_commit_and_publish",
                "--nocapture",
            ])
            .env("BLOCKING_MAX_THREADS", "1")
            .env("FLARE_DATASTORE_TEST_QUEUED_CANCELLATION", "1")
            .status()
            .unwrap();
        assert!(status.success());
        return;
    }

    let directory = tempfile::tempdir().unwrap();
    block_on(async {
        let store = DataStore::open_in(directory.path()).await.unwrap();
        let mut changes = store.subscribe(APP_SETTINGS).await.unwrap();
        changes.next().await.unwrap().unwrap();
        let (started_sender, started_receiver) = mpsc::channel();
        let (release_sender, release_receiver) = mpsc::channel();
        let mut running = Box::pin(store.update(FLARE_CONFIG, move |_| {
            started_sender.send(()).unwrap();
            release_receiver.recv().unwrap();
            Ok(())
        }));
        assert!(futures::poll!(running.as_mut()).is_pending());
        started_receiver
            .recv_timeout(Duration::from_secs(5))
            .unwrap();
        let mut queued = Box::pin(store.update(APP_SETTINGS, |value| {
            value.language = Some("queued".into());
            Ok(())
        }));
        assert!(futures::poll!(queued.as_mut()).is_pending());
        drop(queued);
        release_sender.send(()).unwrap();
        running.await.unwrap();
        store.update(FLARE_CONFIG, |_| Ok(())).await.unwrap();
        assert_eq!(
            store.read(APP_SETTINGS).await.unwrap().language.as_deref(),
            Some("queued")
        );
        let published = changes.next().now_or_never().unwrap().unwrap().unwrap();
        assert_eq!(published.language.as_deref(), Some("queued"));
        assert_eq!(
            fs::read(directory.path().join(APP_SETTINGS.name())).unwrap(),
            published.write_to_bytes().unwrap()
        );
    });
}

#[test]
fn a_leftover_staging_file_is_reclaimed_on_the_next_write() {
    let directory = tempfile::tempdir().unwrap();
    let staging = directory
        .path()
        .join(format!("{}.rust.tmp", FLARE_CONFIG.name()));
    fs::write(&staging, [0xff]).unwrap();

    let store = block_on(DataStore::open_in(directory.path())).unwrap();
    assert_eq!(
        block_on(store.read(FLARE_CONFIG)).unwrap().server_url(),
        "https://api.flareapp.moe"
    );
    block_on(store.update(FLARE_CONFIG, |record| {
        record.server_url = Some("https://retry.example".into());
        Ok(())
    }))
    .unwrap();
    assert!(!staging.exists());
    assert_eq!(
        block_on(store.read(FLARE_CONFIG)).unwrap().server_url(),
        "https://retry.example"
    );
}
