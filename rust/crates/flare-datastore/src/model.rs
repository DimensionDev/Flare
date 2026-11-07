//! Protobuf records and the serialization names of Kotlin's sealed payloads.

use std::sync::LazyLock;

use crate::{ProtoError, WireMessage};

#[allow(clippy::pedantic)]
mod generated {
    include!(concat!(env!("OUT_DIR"), "/wire.rs"));
}
pub use generated::flare::datastore::*;
include!(concat!(env!("OUT_DIR"), "/wire_contract.rs"));

/// Associates a persisted sealed variant with its original serialization name.
pub trait VariantPayload: WireMessage {
    const SERIAL_NAME: &'static str;
}

macro_rules! variants {
    ($($message:ident => $name:expr),+ $(,)?) => {
        $(
            impl VariantPayload for $message {
                const SERIAL_NAME: &'static str = $name;
            }
        )+
    };
}

variants! {
    OpenAiConfig => "dev.dimension.flare.data.datastore.model.AppSettings.AiConfig.Type.OpenAI",
    OnDeviceAiConfig => "dev.dimension.flare.data.datastore.model.AppSettings.AiConfig.Type.OnDevice",
    AiTranslationProvider => "AI",
    GoogleWebTranslationProvider => "Google",
    DeepLTranslationProvider => "dev.dimension.flare.data.datastore.model.AppSettings.TranslateConfig.Provider.DeepL",
    GoogleCloudTranslationProvider => "dev.dimension.flare.data.datastore.model.AppSettings.TranslateConfig.Provider.GoogleCloud",
    LibreTranslationProvider => "dev.dimension.flare.data.datastore.model.AppSettings.TranslateConfig.Provider.LibreTranslate",
    OpenLinkInAccount => "Account",
    OpenLinkInBrowser => "Browser",
    TimelineSource => "source",
    TimelineGroup => "group",
    ExternalText => "dev.dimension.flare.ui.model.UiText.ExternalRef",
    LocalizedText => "dev.dimension.flare.ui.model.UiText.Localized",
    RawText => "dev.dimension.flare.ui.model.UiText.Raw",
    AvatarIcon => "dev.dimension.flare.data.model.IconType.Avatar",
    Favicon => "dev.dimension.flare.data.model.IconType.FavIcon",
    MaterialIcon => "dev.dimension.flare.data.model.IconType.Material",
    MixedIcon => "dev.dimension.flare.data.model.IconType.Mixed",
    UrlIcon => "dev.dimension.flare.data.model.IconType.Url",
    BooleanTextArgument => "dev.dimension.flare.ui.model.UiTextArgument.BooleanValue",
    NumberTextArgument => "dev.dimension.flare.ui.model.UiTextArgument.NumberValue",
    StringTextArgument => "dev.dimension.flare.ui.model.UiTextArgument.StringValue",
}

impl KotlinVariant {
    /// Wrap a typed variant for persistence.
    ///
    /// # Errors
    ///
    /// Returns an error if the payload is missing a required field.
    pub fn from_payload<T: VariantPayload>(payload: &T) -> Result<Self, ProtoError> {
        let mut variant = Self::default();
        variant.replace_payload(payload)?;
        Ok(variant)
    }

    /// Replace a typed payload while retaining unknown fields in the envelope.
    ///
    /// # Errors
    ///
    /// Returns an error if the payload is missing a required field.
    pub fn replace_payload<T: VariantPayload>(&mut self, payload: &T) -> Result<(), ProtoError> {
        let bytes = payload.write_to_bytes()?;
        self.serial_name = Some(T::SERIAL_NAME.to_owned());
        self.payload = Some(bytes);
        Ok(())
    }

    /// Decode the payload if its original serialization name matches.
    ///
    /// # Errors
    ///
    /// Returns an error if the matching payload is invalid.
    pub fn payload_as<T: VariantPayload>(&self) -> Result<Option<T>, ProtoError> {
        self.check_required()?;
        self.payload
            .as_deref()
            .filter(|_| self.serial_name.as_deref() == Some(T::SERIAL_NAME))
            .map(T::parse_from_bytes)
            .transpose()
    }
}

static DEFAULT_AI_MODEL: LazyLock<KotlinVariant> = LazyLock::new(|| {
    let model = OpenAiConfig {
        server_url: Some(String::new()),
        api_key: Some(String::new()),
        ..OpenAiConfig::default()
    };
    KotlinVariant::from_payload(&model).expect("the default OpenAI config has all required fields")
});

static DEFAULT_TRANSLATION_PROVIDER: LazyLock<KotlinVariant> = LazyLock::new(|| {
    KotlinVariant::from_payload(&GoogleWebTranslationProvider::default())
        .expect("the default Google provider has no required fields")
});

static DEFAULT_GLOBAL_PROXY_POLICY: LazyLock<ProxyPolicy> = LazyLock::new(|| ProxyPolicy {
    mode: Some(ProxyMode::PROXY_MODE_SYSTEM.into()),
    ..ProxyPolicy::default()
});

impl AiConfig {
    /// Resolve the legacy default model while preserving field presence on disk.
    #[must_use]
    pub fn effective_model_type(&self) -> &KotlinVariant {
        self.model_type.as_option().unwrap_or(&DEFAULT_AI_MODEL)
    }
}

impl TranslateConfig {
    /// Resolve an omitted provider to Google Web.
    #[must_use]
    pub fn effective_provider(&self) -> &KotlinVariant {
        self.provider
            .as_option()
            .unwrap_or(&DEFAULT_TRANSLATION_PROVIDER)
    }
}

impl ProxySettings {
    /// The omitted global policy defaults to system, while account policies inherit.
    #[must_use]
    pub fn global_policy(&self) -> &ProxyPolicy {
        self.global
            .as_option()
            .unwrap_or(&DEFAULT_GLOBAL_PROXY_POLICY)
    }
}
