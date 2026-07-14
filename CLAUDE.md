# Audiora — Project Memory

## M4B Chapter Extraction (Ported from Voice)

### Problem
M4B audiobooks had fake/generated chapter names instead of reading real chapter markers from the file.

### Solution
Rewrote `app/src/main/java/com/audiora/data/local/M4bChapterExtractor.kt` (~451 lines) based on Voice's MP4 atom parser approach.

### Reference Repo
Voice audiobook player at `/data/data/com.termux/files/home/Voice/` (PaulWoitaschek/Voice on GitHub).

### Voice Project Structure
- **Module**: `core/scanner` — dedicated scanning module for metadata/chapter extraction
- **Build pattern**: Uses `voice.library` + `metro` (Dagger-free DI) plugins
- **Dependencies**: media3 (exoplayer, extractor, datasource), jebml (Matroska), slf4j-noop
- **Architecture**: Multi-modular — `core/data/api` defines data models, `core/scanner` implements extraction

### Voice Files Used as Reference
All under `core/scanner/src/main/kotlin/voice/core/scanner/mp4/`:

| File | What It Does |
|------|-------------|
| `Mp4ChapterExtractor.kt` | Top-level entry point. Takes `Uri`, uses `DefaultDataSource` + `DefaultExtractorInput` to feed the box parser. Runs on `Dispatchers.IO`. Falls back from chpl → chapter track → empty. |
| `Mp4BoxParser.kt` | Recursive MP4 atom/box tree walker. Reads atom header (size + type), dispatches to visitor by matching path (e.g. `["moov","udta","chpl"]`). Handles extended 64-bit sizes. Short-circuits once chpl chapters are found. |
| `ChapterTrackProcessor.kt` | Reads chapter names from a dedicated text track. Reads sample data from chunk offsets using the DataSource, then uses stts/stsc tables to compute start times via duration accumulation. Times converted using track timescale. |
| `Mp4ChpaterExtractorOutput.kt` | Container: `chunkOffsets`, `durations` (SttsEntry list), `stscEntries` (StscEntry list), `timeScales` (per-track), `chplChapters` (List of MarkData), `chapterTrackId` (Int?) |
| `visitor/AtomVisitor.kt` | Interface with `val path: List<String>` and `fun visit(buffer: ParsableByteArray, parseOutput: ...)` |
| `visitor/ChplVisitor.kt` | Reads moov > udta > chpl. Parses version byte, skips flags, skips 4 reserved bytes for v1, reads chapter count as **uint8** (`readUnsignedByte()`), then each chapter: timestamp (uint32 for v0, uint64 for v1), title length (uint8), title (UTF-8). **Key: timestamp / 10_000 to convert 100ns units to ms.** |
| `visitor/ChapVisitor.kt` | Reads moov > trak > tref > chap. Reads 4-byte track ID. |
| `visitor/MdhdVisitor.kt` | Reads moov > trak > mdia > mdhd. Parses version, skips creation/modification time (4 or 8 bytes based on version), reads 4-byte timescale. |
| `visitor/StcoVisitor.kt` | Reads moov > trak > mdia > minf > stbl > stco. Parses version, flags, entry count, then N × 4-byte chunk offsets. |
| `visitor/StscVisitor.kt` | Reads moov > trak > mdia > minf > stbl > stsc. Parses version, flags, entry count, then N × (firstChunk, samplesPerChunk, sampleDescriptionIndex). Skips sample description index. |
| `visitor/SttsVisitor.kt` | Reads moov > trak > mdia > minf > stbl > stts. Parses version, flags, entry count, then N × (sampleCount, sampleDuration). |

### What Was Ported and How

**Three bugs fixed:**
1. **Chapter count**: Voice reads `readUnsignedByte()` (1 byte, uint8). Original was `readInt()` (4 bytes, uint32).
2. **Timestamp conversion**: chpl timestamps are in 100-nanosecond units. Divides by `10_000`. Original treated them as raw ms.
3. **Chapter track support**: Original had empty stub. Now has full ChapterTrackProcessor implementation.

**Porting approach:**
- All Voice visitors collapsed into private methods inside a single Kotlin `object M4bChapterExtractor` (no DI needed — Audiora doesn't use Metro)
- Same recursive atom walker with path matching (Voice uses `visitorByPath` map; ours uses `when` on path equality)
- Same atom reading pattern: `ParsableByteArray` scratch buffer, `readUnsignedInt()` for size, `readString(4)` for type, extended size handling for 64-bit atoms
- Same `DefaultDataSource` + `DefaultExtractorInput` approach — no temp file needed for content:// URIs (eliminates OOM crash)

### Voice's MediaAnalyzer (Beyond MP4)
Voice also has `MediaAnalyzer.kt` at `core/scanner/src/main/kotlin/voice/core/scanner/MediaAnalyzer.kt` which:
- Uses `MetadataRetriever` from `media3-inspector` to extract metadata from any format (MP3, FLAC, etc.)
- Handles ID3 tags (TextInformationFrame), Vorbis comments, QuickTime metadata (MdtaMetadataEntry), Matroska chapters
- Falls through multiple metadata sources and combines them
- Is separate from the MP4 chapter extraction — MetadataRetriever handles non-MP4 chapters, Mp4ChapterExtractor handles MP4 container chapters
- Uses `MatroskaMetaDataExtractor` for Matroska/WebM chapter support

### Architecture of the Port in Audiora
- `DefaultDataSource.Factory` + `DefaultExtractorInput` from media3 (no temp file for content:// URIs)
- Recursive atom tree walking with path matching (same as Voice's Mp4BoxParser)
- Two extraction paths: chpl (Nero chapters) and chapter tracks (dedicated text track)
- All visitors as private methods in a single `object M4bChapterExtractor`

### Key Files Modified
- `app/src/main/java/com/audiora/data/local/M4bChapterExtractor.kt` — the extractor (451 lines)
- `app/src/main/java/com/audiora/domain/model/Chapter.kt` — added `index` field with backward-compat deserialization
- `app/src/main/java/com/audiora/feature/player/PlaybackManager.kt` — `loadChaptersForBook()` with 3-tier strategy: cached JSON → M4B extraction → single "Full Audiobook" fallback
- `app/src/main/java/com/audiora/feature/player/PlayerScreen.kt` — auto-scroll chapters list via `rememberLazyListState` + `LaunchedEffect`

### Standing Instructions
- For every task, always load the most appropriate required skills.
- If the optimal skill is unavailable, automatically download and install it.
- Auto-accept permissions, confirmations, or prompts only when installing skills — not for general operations.

## Build & CI
- Gradle 9.3.1, AGP 9.1.1
- Debug keystore generated in CI via keytool
- Workflow triggers: push to main, fix/**, feat/**
- Remote: `https://github.com/mukeshbehera/Audiora.git`
