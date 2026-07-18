#!/usr/bin/env bash
# scripts/build-ffmpeg.sh
#
# Builds FFmpeg and FFprobe from official source for Android.
# Produces minimal, stripped static binaries optimized for audiobook processing.
#
# Usage:
#   bash scripts/build-ffmpeg.sh arm64-v8a          # Build single ABI
#   bash scripts/build-ffmpeg.sh all                # Build all ABIs
#
# The binary is placed in the per-flavor asset directory so each APK
# only contains its target architecture's binary.
#
# Prerequisites:
#   - Android NDK (r23b+) installed
#   - Set ANDROID_NDK_HOME environment variable
#
# Output:
#   app/src/arm64v8a/assets/ffmpeg/ffmpeg
#   app/src/arm64v8a/assets/ffmpeg/ffprobe
#   app/src/arm64v8a/assets/ffmpeg/ffmpeg_size.txt
#   app/src/arm64v8a/assets/ffmpeg/version.txt
#   (and similarly for armeabiv7a, x8664)

set -euo pipefail

FFMPEG_VERSION="7.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/ffmpeg-android"
API_LEVEL=24

# Map ABI name → Gradle flavor directory
declare -A FLAVOR_DIR
FLAVOR_DIR["arm64-v8a"]="arm64v8a"
FLAVOR_DIR["armeabi-v7a"]="armeabiv7a"
FLAVOR_DIR["x86_64"]="x8664"

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║       FFmpeg Android Cross-Compiler — v${FFMPEG_VERSION}            ║"
echo "╚═══════════════════════════════════════════════════════════════╝"

# ─── Find NDK ──────────────────────────────────────────────────────────

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  for candidate in "$ANDROID_HOME/ndk"/* "$HOME/Android/Sdk/ndk"/* /usr/local/lib/android/sdk/ndk/*; do
    if [ -d "$candidate" ]; then
      ANDROID_NDK_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "ERROR: Android NDK not found. Set ANDROID_NDK_HOME."
  exit 1
fi

echo "Using NDK: $ANDROID_NDK_HOME"

# ─── Target ABIs ──────────────────────────────────────────────────────

TARGET_ABIS=()
if [ "$#" -eq 0 ] || [ "$1" = "all" ]; then
  TARGET_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
else
  TARGET_ABIS=("$1")
fi

echo "Target ABIs: ${TARGET_ABIS[*]}"

# ─── Download source ──────────────────────────────────────────────────

SOURCE_DIR="$BUILD_DIR/source"
if [ ! -d "$SOURCE_DIR" ]; then
  echo "Downloading FFmpeg ${FFMPEG_VERSION} source..."
  mkdir -p "$BUILD_DIR"
  curl -L -o "$BUILD_DIR/ffmpeg.tar.xz" "$FFMPEG_URL"
  tar -xf "$BUILD_DIR/ffmpeg.tar.xz" -C "$BUILD_DIR"
  mv "$BUILD_DIR/ffmpeg-${FFMPEG_VERSION}" "$SOURCE_DIR"
  rm "$BUILD_DIR/ffmpeg.tar.xz"
  echo "Source ready at $SOURCE_DIR"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

# ─── Build per ABI ────────────────────────────────────────────────────

for ABI in "${TARGET_ABIS[@]}"; do
  FLAVOR="${FLAVOR_DIR[$ABI]}"
  OUTPUT_DIR="$PROJECT_DIR/app/src/$FLAVOR/assets/ffmpeg"

  echo ""
  echo "===== Building $ABI → $FLAVOR flavor ====="

  # Map ABI to NDK target components
  case "$ABI" in
    arm64-v8a)
      SYS_ARCH="aarch64"
      CLANG_TRIPLE="aarch64-linux-android${API_LEVEL}"
      CROSS_PREFIX="${TOOLCHAIN}/bin/aarch64-linux-android-"
      ;;
    armeabi-v7a)
      SYS_ARCH="arm"
      CLANG_TRIPLE="armv7a-linux-androideabi${API_LEVEL}"
      CROSS_PREFIX="${TOOLCHAIN}/bin/arm-linux-androideabi-"
      ;;
    x86_64)
      SYS_ARCH="x86_64"
      CLANG_TRIPLE="x86_64-linux-android${API_LEVEL}"
      CROSS_PREFIX="${TOOLCHAIN}/bin/x86_64-linux-android-"
      ;;
    *)
      echo "Unknown ABI: $ABI"
      exit 1
      ;;
  esac

  CC="${TOOLCHAIN}/bin/${CLANG_TRIPLE}-clang"
  CXX="${TOOLCHAIN}/bin/${CLANG_TRIPLE}-clang++"
  AR="${TOOLCHAIN}/bin/llvm-ar"
  RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
  STRIP="${TOOLCHAIN}/bin/llvm-strip"
  NM="${TOOLCHAIN}/bin/llvm-nm"

  BUILD_ABI_DIR="$BUILD_DIR/build-$ABI"

  if [ ! -f "$CC" ]; then
    echo "ERROR: Compiler not found: $CC"
    exit 1
  fi

  mkdir -p "$BUILD_ABI_DIR"
  cd "$SOURCE_DIR"

  echo "Configuring..."
  ./configure \
    --prefix="$BUILD_ABI_DIR/build" \
    --cross-prefix="$CROSS_PREFIX" \
    --cc="$CC" \
    --cxx="$CXX" \
    --ar="$AR" \
    --ranlib="$RANLIB" \
    --nm="$NM" \
    --strip="$STRIP" \
    --arch="$SYS_ARCH" \
    --target-os=android \
    --enable-cross-compile \
    --sysroot="$TOOLCHAIN/sysroot" \
    --enable-pic \
    --enable-static \
    --disable-shared \
    \
    --disable-all \
    --enable-ffmpeg \
    --enable-ffprobe \
    \
    --enable-encoder=aac \
    --enable-decoder=aac,mp3,mp3float \
    --enable-muxer=mp4 \
    --enable-demuxer=aac,mp3,mov \
    \
    --enable-protocol=file \
    --enable-parser=aac,mpegaudio \
    --enable-filter=concat,aresample,atempo \
    \
    --enable-small \
    --optflags="-Os" \
    \
    --disable-doc \
    --disable-debug \
    --disable-ffplay \
    --disable-avdevice \
    --disable-postproc \
    --disable-network \
    --disable-iconv \
    --disable-bzlib \
    --disable-lzma \
    --disable-zlib \
    --disable-xlib \
    --disable-sdl2 \
    --disable-vaapi \
    --disable-vulkan \
    --disable-cuda \
    --disable-cuvid \
    --disable-nvenc \
    --disable-mediacodec \
    --disable-indevs \
    --disable-outdevs \
    --disable-devices \
    --disable-programs \
    --disable-demuxer=hls,dash,mpegts \
    --disable-muxer=segment,hls,dash \
    --disable-encoder=libx264,libx265,libvpx \
    --disable-decoder=h264,hevc,mpeg4,mpeg2video,vp8,vp9 \
    --disable-bsf=aac_adtstoasc \
    2>&1 | tail -3

  echo "Building..."
  make -j"$(nproc)" 2>&1 | tail -3

  echo "Installing..."
  make install 2>&1 | tail -3

  FFMPEG_BIN="$BUILD_ABI_DIR/build/bin/ffmpeg"
  FFPROBE_BIN="$BUILD_ABI_DIR/build/bin/ffprobe"

  echo "Stripping..."
  "$STRIP" "$FFMPEG_BIN" 2>/dev/null || true
  "$STRIP" "$FFPROBE_BIN" 2>/dev/null || true

  mkdir -p "$OUTPUT_DIR"
  cp "$FFMPEG_BIN" "$OUTPUT_DIR/ffmpeg"
  cp "$FFPROBE_BIN" "$OUTPUT_DIR/ffprobe"
  chmod +x "$OUTPUT_DIR/ffmpeg" "$OUTPUT_DIR/ffprobe"
  stat -c%s "$OUTPUT_DIR/ffmpeg" > "$OUTPUT_DIR/ffmpeg_size.txt" 2>/dev/null || \
    stat -f%z "$OUTPUT_DIR/ffmpeg" > "$OUTPUT_DIR/ffmpeg_size.txt" 2>/dev/null
  echo "$FFMPEG_VERSION" > "$OUTPUT_DIR/version.txt"

  echo "Binaries placed in $OUTPUT_DIR"
  cd "$SOURCE_DIR"
  make clean 2>/dev/null || true
done

echo ""
echo "=== Build Complete ==="
for ABI in "${TARGET_ABIS[@]}"; do
  FLAVOR="${FLAVOR_DIR[$ABI]}"
  echo "  $ABI: app/src/$FLAVOR/assets/ffmpeg/"
done
