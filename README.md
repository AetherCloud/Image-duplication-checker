# Image Duplication Checker

An Android app that finds duplicate and near-duplicate images on your device using
perceptual hashing. Instead of comparing file checksums (which only catches exact copies),
it computes a perceptual hash for each image and groups images that look the same even when
they have different resolutions, compression, or minor colour changes.

## Features

- **Perceptual duplicate detection** — uses a DCT-based pHash (64-bit) that is resistant to
  scaling, recompression and mild colour shifts. Images that look the same hash close
  together and are grouped via Hamming-distance comparison.
- **Folder whitelisting and blacklisting** — pick which folders to scan (whitelist) and
  which subfolders to exclude (blacklist). The closest matching ancestor wins, so a
  blacklisted subfolder overrides its whitelisted parent.
- **Grouped results** — duplicates are presented as groups sorted by size, with the
  highest-resolution image listed first in each group.
- **Image details and viewer** — tap an image to see its dimensions, file size and dates, or
  open it in a zoomable full-screen view.
- **Delete from within the app** — confirm and delete a duplicate directly from the result
  list; the group is updated automatically.
- **Cancellable scans** — long scans can be stopped mid-flight, with live progress reporting
  (images scanned / total).
- **Thumbnail caching** — keeps scrolling smooth when browsing large result sets.

## How it works

1. Images are queried from the `MediaStore` and filtered by the configured folder rules.
2. Each image is decoded to a small 32×32 grayscale bitmap and a 64-bit perceptual hash is
   computed from the low-frequency DCT coefficients.
3. Hashes are bucketed (LSH-style) and verified with a real Hamming-distance check so that
   near-duplicates are grouped without an O(n²) all-pairs comparison.
4. Groups with two or more images whose similarity is at or above the threshold (default
   90%) are reported as duplicates.

## Tech notes

- Written in Kotlin, built with Gradle (Kotlin DSL).
- Targets Android 13+ (`minSdk = 33`, `targetSdk = 36`).
- Uses AndroidX, Material Components, Kotlin Coroutines, ViewBinding and LiveData.
- Permission model: `READ_MEDIA_IMAGES` on Android 13+ (falls back to
  `READ_EXTERNAL_STORAGE` on older versions, though the minSdk makes that path effectively
  unused).

## Project layout

```
app/src/main/java/dk/ftb/imageduplicationchecker/
├── MainActivity.kt           # Entry point, scan UI and result list
├── FilterActivity.kt         # Folder whitelist/blacklist editor
├── ImageDetailActivity.kt    # Image metadata view
├── FullImageActivity.kt      # Zoomable full-screen image viewer
├── ScanViewModel.kt          # Scan state and orchestration
├── core/
│   ├── DuplicateScanner.kt   # MediaStore query, hashing pipeline, grouping
│   └── PHash.kt              # DCT-based perceptual hash
├── data/                     # Domain models and folder preferences
├── ui/                       # RecyclerView adapters and the zoomable image view
└── util/                     # Bitmap decoding, path utilities, thumbnail cache, dialogs
```

## Building

Open the project in Android Studio or build from the command line:

```bash
./gradlew assembleDebug
```

## Origin of the code

This project is **mostly AI-generated code**. The implementation was produced with the
assistance of an AI coding assistant and has not been exhaustively reviewed or tested by a
human author. Treat it accordingly: verify the behaviour on your own device and data before
relying on it, and review the code (especially the deletion path) before doing anything
destructive.

## License

GPL-3.0-only — see [LICENSE](LICENSE).
