use crux_core::{
    App,
    type_generation::facet::{Export, TypeGenError, TypeRegistry},
};
use facet::Facet;

pub struct ViewModelDescriptor {
    pub id: &'static str,
    pub ffi_class: &'static str,
    pub swift_module: &'static str,
    pub kotlin_package: &'static str,
    register: fn(&mut TypeRegistry) -> Result<(), TypeGenError>,
}

inventory::collect!(ViewModelDescriptor);

impl ViewModelDescriptor {
    pub(crate) const fn new<A: App>(
        id: &'static str,
        ffi_class: &'static str,
        swift_module: &'static str,
        kotlin_package: &'static str,
    ) -> Self
    where
        A::Effect: Export,
        A::Event: Facet<'static>,
        A::ViewModel: Facet<'static> + 'static,
    {
        Self {
            id,
            ffi_class,
            swift_module,
            kotlin_package,
            register: |registry| {
                registry.register_app::<A>()?;
                Ok(())
            },
        }
    }

    /// # Errors
    /// Returns an error if Crux cannot register the app's types.
    pub fn type_registry(&self) -> Result<TypeRegistry, TypeGenError> {
        let mut registry = TypeRegistry::new();
        (self.register)(&mut registry)?;
        Ok(registry)
    }
}

pub fn registered_view_models() -> impl Iterator<Item = &'static ViewModelDescriptor> {
    inventory::iter::<ViewModelDescriptor>.into_iter()
}
