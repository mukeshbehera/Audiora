# Voice Reference Repository

The Voice audiobook player (PaulWoitaschek/Voice) is cloned at `/root/Voice/` for reference.

## Key Reference Files

### MP4 Chapter Extraction
All under `core/scanner/src/main/kotlin/voice/core/scanner/mp4/`:

| File | Purpose |
|------|---------|
| `Mp4ChapterExtractor.kt` | Top-level entry point — takes Uri, uses DefaultDataSource + DefaultExtractorInput |
| `Mp4BoxParser.kt` | Recursive MP4 atom/box tree walker with path-based visitor dispatch |
| `ChapterTrackProcessor.kt` | Reads chapter names from dedicated text tracks |
| `Mp4ChpaterExtractorOutput.kt` | Data container for parsed output |

### Visitors (`mp4/visitor/`)
| File | Purpose |
|------|---------|
| `AtomVisitor.kt` | Interface with `val path: List<String>` and `fun visit(buffer, parseOutput)` |
| `ChplVisitor.kt` | Nero chapter list (moov > udta > chpl) |
| `ChapVisitor.kt` | Chapter track reference (moov > trak > tref > chap) |
| `MdhdVisitor.kt` | Media header timescale (moov > trak > mdia > mdhd) |
| `StcoVisitor.kt` | Chunk offsets (moov > trak > mdia > minf > stbl > stco) |
| `StscVisitor.kt` | Sample-to-chunk mapping (moov > trak > mdia > minf > stbl > stsc) |
| `SttsVisitor.kt` | Sample durations (moov > trak > mdia > minf > stbl > stts) |

### Media Analyzer (beyond MP4)
- `/root/Voice/core/scanner/src/main/kotlin/voice/core/scanner/MediaAnalyzer.kt` — metadata extraction for MP3, FLAC, Matroska, etc.

## Usage
- Voice is cloned at `/root/Voice/` — use standard Read/grep/Bash tools to explore it
- Serena's code intelligence is scoped to the Audiora project only
- Voice is a reference only — we do not modify it
- Referenced by `mem:m4b_chapter_extraction` for the ported M4B chapter extraction logic