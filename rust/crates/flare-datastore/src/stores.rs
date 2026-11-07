//! The ten current stores and their persisted types.

use crate::{
    StoreKey,
    model::{
        AccountSelectionPreferences, AppSettings, AppearanceBag, ComposeConfigData, FlareConfig,
        HomeTimelinePreferences, PlatformOAuthPendingData, ProxySettings, TabSettingsV2,
    },
};

pub const HOME_TIMELINE_PREFERENCES: StoreKey<HomeTimelinePreferences> =
    StoreKey::new("home_timeline_preferences.pb");
pub const NOTIFICATION_PREFERENCES: StoreKey<AccountSelectionPreferences> =
    StoreKey::new("notification_preferences.pb");
pub const DISCOVER_PREFERENCES: StoreKey<AccountSelectionPreferences> =
    StoreKey::new("discover_preferences.pb");
pub const FLARE_CONFIG: StoreKey<FlareConfig> = StoreKey::new("flare_config.pb");
pub const COMPOSE_CONFIG: StoreKey<ComposeConfigData> = StoreKey::new("compose_config.pb");
pub const APP_SETTINGS: StoreKey<AppSettings> =
    StoreKey::with_default("app_settings.pb", || AppSettings {
        version: Some(String::new()),
        ..AppSettings::default()
    });
pub const PLATFORM_OAUTH_PENDING: StoreKey<PlatformOAuthPendingData> =
    StoreKey::new("platform_oauth_pending_v2.pb");
pub const APPEARANCE_BAG: StoreKey<AppearanceBag> = StoreKey::new("appearance_bag.pb");
pub const TAB_SETTINGS: StoreKey<TabSettingsV2> = StoreKey::new("tab_settings_v2.pb");
pub const PROXY_SETTINGS: StoreKey<ProxySettings> = StoreKey::new("proxy_settings.pb");
