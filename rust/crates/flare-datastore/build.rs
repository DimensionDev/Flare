use std::{env, fmt, fmt::Write as _, fs, path::PathBuf};

use prost_types::{
    FieldDescriptorProto, FileDescriptorProto,
    field_descriptor_proto::{Label, Type},
};
use protox::prost::Message as _;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    const SCHEMA: &str = "proto/datastore.proto";
    println!("cargo:rerun-if-changed={SCHEMA}");
    let mut descriptors = protox::compile([SCHEMA], ["proto"])?;
    let [schema] = descriptors.file.as_mut_slice() else {
        panic!("expected a self-contained DataStore schema");
    };
    let contract = generate_contract(schema)?;
    let out = PathBuf::from(env::var_os("OUT_DIR").expect("Cargo sets OUT_DIR"));
    let descriptor_path = out.join("wire_descriptor.bin");
    let descriptor_bytes = descriptors.encode_to_vec();
    if fs::read(&descriptor_path).ok().as_deref() != Some(descriptor_bytes.as_slice()) {
        fs::write(&descriptor_path, descriptor_bytes)?;
    }
    fs::write(out.join("wire_contract.rs"), contract)?;
    buffa_build::Config::new()
        .files(&["datastore.proto"])
        .descriptor_set(descriptor_path)
        .generate_views(false)
        .generate_with_setters(false)
        .open_enums_in(&[".flare.datastore"])
        .include_file("wire.rs")
        .compile()?;
    Ok(())
}

fn generate_contract(schema: &mut FileDescriptorProto) -> Result<String, fmt::Error> {
    let mut output = String::new();
    for message in &mut schema.message_type {
        let name = message.name().to_owned();
        assert!(
            message.nested_type.is_empty() && message.oneof_decl.is_empty(),
            "DataStore uses top-level messages and explicit map entries: {name}"
        );
        writeln!(
            output,
            "impl WireMessage for {name} {{ fn check_required(&self) -> Result<(), ProtoError> {{"
        )?;
        for field in &mut message.field {
            let member = field.name();
            let label = field.label();
            match (field.r#type(), label) {
                (Type::Message, Label::Required) => writeln!(
                    output,
                    "self.{member}.as_option().ok_or(ProtoError::MissingRequired(\"{name}.{member}\"))?.check_required()?;"
                )?,
                (Type::Message, Label::Repeated) => writeln!(
                    output,
                    "for child in &self.{member} {{ child.check_required()?; }}"
                )?,
                (Type::Message, Label::Optional) => writeln!(
                    output,
                    "if let Some(child) = self.{member}.as_option() {{ child.check_required()?; }}"
                )?,
                (_, Label::Required) => writeln!(
                    output,
                    "if self.{member}.is_none() {{ return Err(ProtoError::MissingRequired(\"{name}.{member}\")); }}"
                )?,
                _ => {}
            }
            if label == Label::Required {
                // Buffa's required scalars are bare values. Generate explicit
                // presence so the checks can distinguish absent from zero.
                field.label = Some(Label::Optional.into());
            }
        }
        output.push_str("Ok(()) } }\n");
        for field in &message.field {
            if let Some(default) = field.default_value.as_deref() {
                write_default_accessor(&mut output, &name, field, default)?;
            }
        }
    }
    Ok(output)
}

fn write_default_accessor(
    output: &mut String,
    message: &str,
    field: &FieldDescriptorProto,
    default: &str,
) -> fmt::Result {
    let name = field.name();
    let (ty, access, value) = match field.r#type() {
        Type::String => ("&str".to_owned(), ".as_deref()", format!("{default:?}")),
        Type::Bool => ("bool".to_owned(), "", default.to_owned()),
        Type::Int32 => ("i32".to_owned(), "", default.to_owned()),
        Type::Enum => {
            let (_, enum_name) = field.type_name().rsplit_once('.').expect("qualified enum");
            (
                format!("::buffa::EnumValue<{enum_name}>"),
                "",
                format!("::buffa::EnumValue::Known({enum_name}::{default})"),
            )
        }
        _ => panic!("unsupported DataStore default for {message}.{name}"),
    };
    writeln!(
        output,
        "impl {message} {{ #[must_use] pub fn {name}(&self) -> {ty} {{ self.{name}{access}.unwrap_or({value}) }} }}"
    )
}
