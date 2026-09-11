use std::io::Cursor;

use fst::{Map, MapBuilder};
use pyo3::exceptions::PyValueError;
use pyo3::prelude::*;
use pyo3::types::PyBytes;
use sha2::{Digest, Sha256};

fn value_error(message: impl Into<String>) -> PyErr {
    PyValueError::new_err(message.into())
}

#[pyfunction]
fn build_fst(py: Python<'_>, entries: Vec<(String, u64)>) -> PyResult<Py<PyBytes>> {
    if entries.is_empty() {
        return Err(value_error("FST needs at least one entry"));
    }
    let mut bytes = Vec::new();
    {
        let mut builder =
            MapBuilder::new(&mut bytes).map_err(|error| value_error(error.to_string()))?;
        let mut previous: Option<String> = None;
        for (key, value) in entries {
            if key.is_empty() || key.as_bytes().contains(&0) {
                return Err(value_error("FST key is empty or contains NUL"));
            }
            if previous.as_deref().is_some_and(|item| item >= key.as_str()) {
                return Err(value_error("FST keys must be strictly sorted"));
            }
            builder
                .insert(key.as_str(), value)
                .map_err(|error| value_error(error.to_string()))?;
            previous = Some(key);
        }
        builder
            .finish()
            .map_err(|error| value_error(error.to_string()))?;
    }
    Map::new(&bytes).map_err(|error| value_error(error.to_string()))?;
    Ok(PyBytes::new(py, &bytes).unbind())
}

#[pyfunction]
fn fst_lookup(data: &[u8], key: &str) -> PyResult<Option<u64>> {
    if key.is_empty() || key.as_bytes().contains(&0) {
        return Ok(None);
    }
    let map = Map::new(data).map_err(|error| value_error(error.to_string()))?;
    Ok(map.get(key))
}

fn push_varint(output: &mut Vec<u8>, mut value: u32) {
    while value >= 0x80 {
        output.push((value as u8 & 0x7f) | 0x80);
        value >>= 7;
    }
    output.push(value as u8);
}

fn read_varint(data: &[u8], cursor: &mut usize) -> Result<u32, String> {
    let mut value = 0u32;
    for shift in (0..35).step_by(7) {
        if *cursor >= data.len() {
            return Err("postings varint is truncated".to_string());
        }
        let byte = data[*cursor];
        *cursor += 1;
        let part = (byte & 0x7f) as u32;
        if shift >= 32 && part != 0 {
            return Err("postings varint overflows u32".to_string());
        }
        value |= part
            .checked_shl(shift)
            .ok_or_else(|| "postings varint overflows u32".to_string())?;
        if byte & 0x80 == 0 {
            return Ok(value);
        }
    }
    Err("postings varint is too long".to_string())
}

#[pyfunction]
fn encode_postings_u32(py: Python<'_>, raw_little_endian_u32: &[u8]) -> PyResult<Py<PyBytes>> {
    if raw_little_endian_u32.len() & 3 != 0 {
        return Err(value_error("postings u32 input is not aligned"));
    }
    let mut output = Vec::with_capacity(raw_little_endian_u32.len());
    let mut previous = 0u32;
    for (index, bytes) in raw_little_endian_u32.chunks_exact(4).enumerate() {
        let value = u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]);
        if index > 0 && value <= previous {
            return Err(value_error("postings IDs must be strictly increasing"));
        }
        let delta = if index == 0 { value } else { value - previous };
        push_varint(&mut output, delta);
        previous = value;
    }
    Ok(PyBytes::new(py, &output).unbind())
}

#[pyfunction]
fn decode_postings(data: &[u8], max_count: usize) -> PyResult<Vec<u32>> {
    if max_count == 0 {
        return Err(value_error("postings max_count must be positive"));
    }
    let mut output = Vec::new();
    let mut cursor = 0usize;
    let mut previous = 0u32;
    while cursor < data.len() {
        if output.len() >= max_count {
            return Err(value_error("postings count exceeds bound"));
        }
        let delta = read_varint(data, &mut cursor).map_err(value_error)?;
        let value = if output.is_empty() {
            delta
        } else {
            previous
                .checked_add(delta)
                .ok_or_else(|| value_error("postings ID overflows u32"))?
        };
        if !output.is_empty() && value <= previous {
            return Err(value_error("postings IDs are not strictly increasing"));
        }
        output.push(value);
        previous = value;
    }
    Ok(output)
}

#[pyfunction]
fn zstd_compress(py: Python<'_>, data: &[u8], level: i32) -> PyResult<Py<PyBytes>> {
    if !(1..=19).contains(&level) {
        return Err(value_error("zstd level must be between 1 and 19"));
    }
    let compressed = zstd::stream::encode_all(Cursor::new(data), level)
        .map_err(|error| value_error(error.to_string()))?;
    Ok(PyBytes::new(py, &compressed).unbind())
}

#[pyfunction]
fn zstd_decompress(data: &[u8], max_bytes: usize) -> PyResult<Vec<u8>> {
    if max_bytes == 0 {
        return Err(value_error("zstd max_bytes must be positive"));
    }
    let decoded = zstd::stream::decode_all(Cursor::new(data))
        .map_err(|error| value_error(error.to_string()))?;
    if decoded.len() > max_bytes {
        return Err(value_error("zstd output exceeds bound"));
    }
    Ok(decoded)
}

#[pyfunction]
fn sha256_matches(data: &[u8], expected_hex: &str) -> PyResult<bool> {
    if expected_hex.len() != 64 || !expected_hex.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(value_error("SHA-256 digest is invalid"));
    }
    let actual = Sha256::digest(data);
    let expected = expected_hex.as_bytes();
    let mut actual_hex = [0u8; 64];
    const HEX: &[u8; 16] = b"0123456789abcdef";
    for (index, byte) in actual.iter().enumerate() {
        actual_hex[index * 2] = HEX[(byte >> 4) as usize];
        actual_hex[index * 2 + 1] = HEX[(byte & 0x0f) as usize];
    }
    Ok(actual_hex.eq_ignore_ascii_case(expected))
}

#[pymodule]
fn searchpack_native(module: &Bound<'_, PyModule>) -> PyResult<()> {
    module.add_function(wrap_pyfunction!(build_fst, module)?)?;
    module.add_function(wrap_pyfunction!(fst_lookup, module)?)?;
    module.add_function(wrap_pyfunction!(encode_postings_u32, module)?)?;
    module.add_function(wrap_pyfunction!(decode_postings, module)?)?;
    module.add_function(wrap_pyfunction!(zstd_compress, module)?)?;
    module.add_function(wrap_pyfunction!(zstd_decompress, module)?)?;
    module.add_function(wrap_pyfunction!(sha256_matches, module)?)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn postings_round_trip_is_delta_encoded() {
        let ids = [0u32, 2, 9, 1024, 65_000];
        let mut raw = Vec::new();
        for id in ids {
            raw.extend_from_slice(&id.to_le_bytes());
        }
        let mut encoded = Vec::new();
        let mut previous = 0u32;
        for (index, id) in ids.iter().enumerate() {
            push_varint(&mut encoded, if index == 0 { *id } else { *id - previous });
            previous = *id;
        }
        let mut cursor = 0usize;
        let mut decoded = Vec::new();
        let mut last = 0u32;
        while cursor < encoded.len() {
            let delta = read_varint(&encoded, &mut cursor).unwrap();
            let value = if decoded.is_empty() {
                delta
            } else {
                last + delta
            };
            decoded.push(value);
            last = value;
        }
        assert_eq!(decoded, ids);
        assert!(encoded.len() < raw.len());
    }

    #[test]
    fn varint_rejects_truncation() {
        let mut cursor = 0;
        assert!(read_varint(&[0x80], &mut cursor).is_err());
    }
}
