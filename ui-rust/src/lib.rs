use serde_json::Value;

pub fn profile_key(base: &str, profile: Option<&str>, default_profile: &str) -> String {
    match profile {
        Some(profile) if profile != default_profile => format!("{base}@{profile}"),
        _ => base.to_owned(),
    }
}

pub fn preference<'a>(values: &'a Value, base: &str, profile: Option<&str>, default_profile: &str) -> Option<&'a Value> {
    let key = profile_key(base, profile, default_profile);
    values.get(&key).or_else(|| values.get(base))
}

pub fn parity_inventory() -> Result<Value, serde_json::Error> {
    serde_json::from_str(include_str!("../../../docs/rust-ui-parity.json"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn default_account_keeps_existing_keys() {
        assert_eq!(profile_key("public_music", Some("default"), "default"), "public_music");
    }

    #[test]
    fn additional_account_inherits_until_overridden() {
        let values = json!({"public_music": true, "public_music@second": false});
        assert_eq!(preference(&values, "public_music", Some("second"), "default"), Some(&json!(false)));
        assert_eq!(preference(&values, "public_music", Some("third"), "default"), Some(&json!(true)));
    }

    #[test]
    fn captured_inventory_retains_both_looks_and_preferences() {
        let inventory = parity_inventory().unwrap();
        let ui = inventory["ui_files"].as_array().unwrap();
        assert!(ui.iter().any(|entry| entry["path"].as_str().unwrap().contains("immersive/")));
        assert!(ui.iter().any(|entry| entry["path"].as_str().unwrap().ends_with("screens/SettingsScreen.kt")));
        assert_eq!(inventory["preferences"].as_array().unwrap().len(), 15);
    }
}
