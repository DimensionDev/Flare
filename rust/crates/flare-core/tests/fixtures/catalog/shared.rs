use flare_presenter_macros::presenter_dto;

#[presenter_dto]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CatalogProfile {
    pub name: String,
    pub avatar: Option<CatalogAvatar>,
}

#[presenter_dto]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CatalogAvatar {
    pub url: String,
}
