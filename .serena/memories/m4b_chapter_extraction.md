# M4B Chapter Extraction

Ported from Voice (PaulWoitaschek/Voice) MP4 atom parser. See `CLAUDE.md` for full reference.

## Key Facts
- **File**: `app/src/main/java/com/audiora/data/local/M4bChapterExtractor.kt` (~451 lines)
- **Two strategies**: Nero chpl chapters (moov > udta > chpl) → chapter tracks (moov > trak > tref > chap)
- **Three bugs fixed from original Audiora code**:
  1. Chapter count: Voice reads `readUnsignedByte()` (1 byte, uint8). Original was `readInt()` (4 bytes, uint32).
  2. Timestamp conversion: chpl timestamps are 100-nanosecond units, divide by 10_000. Original treated as raw ms.
  3. Chapter track support: Original had empty stub. Now has full `ChapterTrackProcessor` implementation.

## Architecture
- Single `object M4bChapterExtractor` (no DI needed)
- Recursive atom walker with path matching (same as Voice's `Mp4BoxParser`)
- `DefaultDataSource` + `DefaultExtractorInput` from Media3 (no temp file for content:// URIs)
- All visitors collapsed into private methods (Voice uses separate visitor classes)

## Chapter Loading Strategy (PlaybackManager)
1. Cached `chaptersJson` on the book entity (extracted at scan time)
2. Fallback: single "Full Audiobook" entry
