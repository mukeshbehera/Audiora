# FFmpeg Integration — Phase 1 Refinement Plan

**Goal:** Refine existing FFmpeg integration with production-grade binary management, asset layout, and integrity checks. Add dev binaries.

**Architecture:** Keep all existing components. Rewrite `FfmpegBinaryManager` for version-aware + size-checked extraction. Add scripts for binary download and CI build. Add exception hierarchy.

**Global Constraints:**
- Single APK with runtime ABI detection (not product flavors)
- Binaries committed to git for development, replaced by CI for production
- Same runtime code for dev and production
- No direct ProcessBuilder outside ProcessExecutor
- All commands as List<String>

---

## File Inventory

### New Files
| # | Path | Purpose |
|---|------|---------|
| 1 | `scripts/download-ffmpeg-dev.sh` | Download pre-built dev binaries |
| 2 | `scripts/update-ffmpeg-size.sh` | Generate ffmpeg_size.txt for all ABIs |
| 3 | `scripts/build-ffmpeg.sh` | Build FFmpeg from source (for CI) |
| 4 | `.github/workflows/build-ffmpeg.yml` | CI workflow |
| 5 | `data/processing/exception/FfmpegException.kt` | Exception hierarchy |

### Modified Files
| # | Path | Change |
|---|------|--------|
| 1 | `data/processing/FfmpegBinaryManager.kt` | New asset layout, version tracking, size check, integrity recovery, ProcessExecutor injection, exception hierarchy |
| 2 | `data/processing/executor/ProcessExecutor.kt` | Add `process.waitFor()` in cleanup, guard against double-destroy |
| 3 | `AudioraApplication.kt` | Update FfmpegBinaryManager constructor |
| 4 | `gradle.properties` | Add `ffmpegVersion=7.1` |
| 5 | `app/build.gradle.kts` | Add `buildConfigField` for FFMPEG_VERSION |

### New Asset Files (committed to git)
| # | Path | Purpose |
|---|------|---------|
| 1 | `assets/ffmpeg/version.txt` | Bundled version string |
| 2 | `assets/ffmpeg/arm64-v8a/ffmpeg_size.txt` | Expected size for integrity check |
| 3 | `assets/ffmpeg/armeabi-v7a/ffmpeg_size.txt` | Expected size for integrity check |
| 4 | `assets/ffmpeg/x86_64/ffmpeg_size.txt` | Expected size for integrity check |

---

### Task 1: Add BuildConfig Version and gradle.properties

- [ ] **Step 1: Add ffmpegVersion to gradle.properties**
```properties
ffmpegVersion=7.1
```

- [ ] **Step 2: Add buildConfigField to app/build.gradle.kts**
```kotlin
// Inside android.defaultConfig block:
buildConfigField("String", "FFMPEG_VERSION", "\"${project.findProperty("ffmpegVersion") ?: "0.0"}\"")
```

- [ ] **Step 3: Commit**
```bash
git add gradle.properties app/build.gradle.kts
git commit -m "feat: add FFMPEG_VERSION build config field"
```

### Task 2: Exception Hierarchy

- [ ] **Step 1: Create exception/FfmpegException.kt**
- [ ] **Step 2: Commit**

### Task 3: Rewrite FfmpegBinaryManager

- [ ] **Step 1: Rewrite FfmpegBinaryManager with:**
  - Asset path: `ffmpeg/{abi}/{name}` (was `{name}-{abi}`)
  - Inject ProcessExecutor (not direct ProcessBuilder)
  - version.txt from assets + SharedPreferences version tracking
  - ffmpeg_size.txt size-based integrity pre-check
  - Integrity recovery: verify → fail → delete → re-extract → retry once
  - Use new exception hierarchy

- [ ] **Step 2: Commit**

### Task 4: Update ProcessExecutor

- [ ] **Step 1: Add process.waitFor() in cleanup block to prevent zombies**
- [ ] **Step 2: Commit**

### Task 5: Update AudioraApplication

- [ ] **Step 1: Update FfmpegBinaryManager constructor call to pass ProcessExecutor**
- [ ] **Step 2: Commit**

### Task 6: Download Script and Asset Placeholders

- [ ] **Step 1: Create scripts/download-ffmpeg-dev.sh**
- [ ] **Step 2: Create scripts/update-ffmpeg-size.sh** (same pattern as reference project)
- [ ] **Step 3: Create assets/ffmpeg/version.txt**
- [ ] **Step 4: Run download script to populate assets**
- [ ] **Step 5: Run update-ffmpeg-size.sh**
- [ ] **Step 6: Commit everything**

### Task 7: CI Pipeline (Phase 2)

- [ ] **Step 1: Create scripts/build-ffmpeg.sh**
- [ ] **Step 2: Create .github/workflows/build-ffmpeg.yml**
- [ ] **Step 3: Commit**
