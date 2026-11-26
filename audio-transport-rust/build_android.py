import os
from pathlib import Path
import platform
import shutil
import subprocess

__author__ = "carunodev"

base = Path(__file__).resolve().parent.parent
hosts = {
    "Linux": "linux-x86_64",
    "Darwin": "darwin-x86_64",
    "Windows": "windows-x86_64",
}
system = platform.system()
sdk_defaults = {
    "Linux": Path.home() / "Android/Sdk",
    "Darwin": Path.home() / "Library/Android/sdk",
    "Windows": Path.home() / "AppData/Local/Android/Sdk",
}
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or sdk_defaults[system])
ndk = Path(os.environ.get("ANDROID_NDK_HOME") or sdk / "ndk/28.0.13004108")
toolchain = ndk / "toolchains/llvm/prebuilt" / hosts[system] / "bin"
clang_suffix = ".cmd" if system == "Windows" else ""
ar_suffix = ".exe" if system == "Windows" else ""
manifest = base / "audio-transport-rust/Cargo.toml"
env = os.environ.copy()

targets = [
    ("aarch64-linux-android", "arm64-v8a", "aarch64-linux-android"),
    ("armv7-linux-androideabi", "armeabi-v7a", "armv7a-linux-androideabi"),
]
for rust_target, abi, clang_target in targets:
    clang = toolchain / f"{clang_target}29-clang{clang_suffix}"
    if not clang.is_file():
        raise SystemExit(f"Android NDK compiler not found: {clang}")
    env[f"CARGO_TARGET_{rust_target.upper().replace('-', '_')}_LINKER"] = str(clang)
    env[f"CC_{rust_target.replace('-', '_')}"] = str(clang)
    env[f"AR_{rust_target.replace('-', '_')}"] = str(toolchain / f"llvm-ar{ar_suffix}")

subprocess.run(["cargo", "test", "--locked", "--manifest-path", str(manifest)], env=env, check=True, timeout=600)

for rust_target, abi, _ in targets:
    command = ["cargo", "rustc", "--locked", "--release", "--target", rust_target, "--manifest-path", str(manifest)]
    if abi == "arm64-v8a":
        command.extend(["--", "-C", "link-arg=-Wl,-z,max-page-size=16384"])
    subprocess.run(command, env=env, check=True, timeout=600)

target_dir = Path(env.get("CARGO_TARGET_DIR", manifest.parent / "target"))
for rust_target, abi, _ in targets:
    out = base / f"engine-android/src/main/jniLibs/{abi}/liborpheon_audio.so"
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target_dir / rust_target / "release/liborpheon_audio.so", out)
    print(out)
