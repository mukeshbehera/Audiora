#!/usr/bin/env bash
# scripts/update-ffmpeg-size.sh
#
# Generates ffmpeg_size.txt and version.txt for each flavor's ffmpeg binary.
# Run this after placing or updating binaries in per-flavor asset directories.
#
# Usage: bash scripts/update-ffmpeg-size.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Updating FFmpeg binary metadata ==="

for flavor_dir in "$PROJECT_DIR/app/src/"*/assets/ffmpeg; do
  [ -d "$flavor_dir" ] || continue
  flavor=$(basename "$(dirname "$(dirname "$flavor_dir")")")
  binary="$flavor_dir/ffmpeg"

  if [ -f "$binary" ] && [ ! -L "$binary" ]; then
    # Check it's a real binary, not a placeholder script
    if head -c 2 "$binary" | grep -q 'MZ\|\x7fELF'; then
      size=$(stat -c%s "$binary" 2>/dev/null || stat -f%z "$binary" 2>/dev/null)
      echo "$size" > "$flavor_dir/ffmpeg_size.txt"
      echo "$flavor: $size bytes"
    else
      echo "$flavor: placeholder detected, skipping size update"
    fi
  else
    echo "$flavor: no real binary found"
  fi
done

echo "Done."
