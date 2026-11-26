use jni::objects::{JClass, JString};
use jni::sys::jbyteArray;
use jni::JNIEnv;
use reqwest::blocking::Client;
use reqwest::header::{HeaderMap, HeaderName, HeaderValue, RANGE};
use std::collections::BTreeMap;
use std::sync::OnceLock;
use std::time::Duration;

fn response_bytes(status: u16, bytes: Vec<u8>, start: u64, end: u64) -> Result<Vec<u8>, String> {
    let count = end.checked_sub(start).and_then(|n| n.checked_add(1))
        .and_then(|n| usize::try_from(n).ok()).ok_or("invalid audio range")?;
    match status {
        206 if bytes.len() == count => Ok(bytes),
        206 => Err(format!("short CDN range: expected {count}, received {}", bytes.len())),
        200 => {
            let from = usize::try_from(start).map_err(|_| "range overflow")?;
            let to = from.checked_add(count).ok_or("range overflow")?;
            bytes.get(from..to).map(|s| s.to_vec()).ok_or("truncated full CDN response".into())
        }
        _ => Err(format!("CDN HTTP {status}")),
    }
}

static CLIENT: OnceLock<Result<Client, String>> = OnceLock::new();

fn fetch(url: &str, headers: &str, start: u64, end: u64) -> Result<Vec<u8>, String> {
    let client = CLIENT.get_or_init(|| Client::builder()
        .connect_timeout(Duration::from_secs(3))
        .timeout(Duration::from_secs(6))
        .pool_idle_timeout(Duration::from_secs(15))
        .tcp_nodelay(true)
        .build().map_err(|e| e.to_string())).as_ref().map_err(Clone::clone)?;
    let parsed = reqwest::Url::parse(url).map_err(|e| e.to_string())?;
    if parsed.scheme() != "https" { return Err("audio CDN must use HTTPS".into()); }
    let fields: BTreeMap<String, String> = serde_json::from_str(headers).map_err(|e| e.to_string())?;
    let mut h = HeaderMap::new();
    for (name, value) in fields {
        h.insert(HeaderName::from_bytes(name.as_bytes()).map_err(|e| e.to_string())?, HeaderValue::from_str(&value).map_err(|e| e.to_string())?);
    }
    h.insert(RANGE, HeaderValue::from_str(&format!("bytes={start}-{end}")).map_err(|e| e.to_string())?);
    let response = client.get(parsed).headers(h).send().map_err(|e| format!("CDN transport: {e}"))?;
    let status = response.status().as_u16();
    let bytes = response.bytes().map_err(|e| e.to_string())?.to_vec();
    response_bytes(status, bytes, start, end)
}

#[no_mangle]
pub extern "system" fn Java_app_myspoti_engine_android_RustAudioTransport_fetchNative(
    mut env: JNIEnv, _class: JClass, url: JString, headers: JString, start: i64, end: i64,
) -> jbyteArray {
    let result = (|| -> Result<_, String> {
        if start < 0 || end < start { return Err("invalid audio range".into()); }
        let url: String = env.get_string(&url).map_err(|e| e.to_string())?.into();
        let headers: String = env.get_string(&headers).map_err(|e| e.to_string())?.into();
        fetch(&url, &headers, start as u64, end as u64)
    })();
    match result {
        Ok(bytes) => match env.byte_array_from_slice(&bytes) {
            Ok(array) => array.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error);
            std::ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn partial_content_must_have_the_requested_length() {
        assert_eq!(response_bytes(206, vec![1,2,3], 10, 12).unwrap(), vec![1,2,3]);
        assert!(response_bytes(206, vec![1,2], 10, 12).is_err());
    }
    #[test]
    fn full_response_is_sliced_by_absolute_range() {
        assert_eq!(response_bytes(200, vec![0,1,2,3,4], 2, 3).unwrap(), vec![2,3]);
        assert!(response_bytes(200, vec![0,1], 2, 3).is_err());
    }
    #[test]
    fn rejects_server_errors_and_invalid_ranges() {
        assert!(response_bytes(503, vec![], 0, 1).is_err());
        assert!(response_bytes(206, vec![], 9, 8).is_err());
    }
}
