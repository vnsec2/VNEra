# Revera changes to NewPipe Extractor

This directory vendors the production sources from
[`TeamNewPipe/NewPipeExtractor` `v0.26.5`](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5).
It remains licensed under GPL-3.0-or-later; see `LICENSE` and the source file headers.

## Android compatibility

`Utils.encodeUrlUtf8` and `Utils.decodeUrlUtf8` use the `(String, String)`
`URLEncoder` and `URLDecoder` overloads with `"UTF-8"`. The upstream
`(String, Charset)` overloads are unavailable before Android API 33 and cause
`NoSuchMethodError` on Android 11 and 12.

`Utils.isBlank` does not call `String.isBlank`, which is unavailable before
Android API 31.

## Build host

The upstream build requested a JDK 11 toolchain. Revera compiles it with the
installed JDK 17 while setting `--release 11`, preserving the upstream Java
bytecode and API target.
