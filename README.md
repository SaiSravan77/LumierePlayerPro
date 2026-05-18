# LUMIÈRE Player Pro — Android

A fully-featured native Android video player with real-time 4K remaster enhancement,
MediaPipe face enhancement, TFLite scene classification, 5-band EQ, and more.

---

## Feature List

### Playback
- ExoPlayer Media3 — plays MP4, MKV, AVI, MOV, WebM, FLV, 3GP
- Playback speed: 0.25× to 2×
- Skip ±10s, previous/next in playlist
- Seekbar with position memory (resumes where you left off)
- Multiple audio track switching
- Subtitle support: embedded SRT/ASS/VTT + load external file
- Playlist: open multiple files at once
- Loop support via ExoPlayer repeat modes
- Picture-in-Picture (auto on home press)
- Lock screen controls via MediaSession
- Chromecast / Cast support

### Enhancement Engine (GPU GLSL)
- Sharpness (unsharp mask kernel)
- Noise reduction (bilateral filter approximation)
- Contrast, brightness, saturation
- Warmth (red/blue channel shift)
- Shadow lift (crushed black recovery)
- HDR simulation (Reinhard extended tonemapping)
- Deinterlace (VHS/broadcast interlaced footage)
- Master on/off toggle
- 8 presets: Classic 90s, VHS, Cinema, Vivid, B&W, Neutral, Night, Outdoor

### AI Enhancement
- **Face Enhancement (MediaPipe)** — detects up to 4 faces per frame,
  applies extra sharpening and skin-tone warmth in face regions only
- **Scene Classification (TFLite)** — classifies outdoor/indoor/night
  and auto-adjusts shader parameters accordingly
- Both have graceful fallbacks if models aren't bundled

### Audio
- 5-band equalizer: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
- 6 EQ presets: Flat, Bass, Treble, Vocal, Cinema, Classic
- Volume boost (LoudnessEnhancer)
- Virtual surround (Virtualizer)
- Bass boost

### Video Display
- 5 aspect ratios: Fit, Fill, 4:3, 16:9, 21:9
- Fullscreen toggle
- Brightness control (swipe left side)

### Gestures
- Swipe left side up/down → brightness
- Swipe right side up/down → volume
- Swipe horizontal → seek
- Double tap → play/pause
- Single tap → show/hide controls

### Library
- Scans device for all video files
- Watch history with progress bars
- Recent files with resume position

### Other
- Screenshot capture (saves to gallery)
- Controls lock
- Settings screen

---

## Build Instructions

### Requirements
- **Android Studio Hedgehog** (2023.1.1) or newer
  Download: https://developer.android.com/studio
- **JDK 17** (bundled with Android Studio)
- **Android SDK API 34** (auto-installs on first sync)
- Internet connection (Gradle downloads ~300MB of dependencies on first build)

### Step 1 — Install Android Studio
Download and install. On first launch, run the Setup Wizard to install the Android SDK.

### Step 2 — Open the Project
1. Launch Android Studio
2. Click **Open** (not New Project)
3. Navigate to the `LumierePlayerPro` folder → click **OK**
4. Wait for Gradle sync (progress bar at bottom) — takes 2–5 min on first run

### Step 3 — Handle SDK location (if prompted)
- Go to **File → Project Structure → SDK Location**
- Mac default: `~/Library/Android/sdk`
- Windows default: `C:\Users\YOUR_NAME\AppData\Local\Android\Sdk`

### Step 4 — Build APK
**Menu: Build → Build Bundle(s)/APK(s) → Build APK(s)**

Output file: `app/build/outputs/apk/debug/app-debug.apk`

Click **"locate"** in the notification that appears at the bottom right.

### Step 5 — Install on your phone
**Option A (USB):**
1. Enable Developer Options on phone: Settings → About → tap Build Number 7 times
2. Enable USB Debugging in Developer Options
3. Connect phone, allow USB debugging prompt
4. Click the green ▶ Run button in Android Studio

**Option B (file transfer):**
1. Copy `app-debug.apk` to your phone
2. Open the file on your phone
3. Allow "Install unknown apps" if prompted in Settings → Security

---

## Adding AI Models (Optional but recommended)

The app works without models using heuristic fallbacks.
For full AI enhancement, add these files to `app/src/main/assets/`:

### MediaPipe Face Detection
Download: https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite
Save as: `face_detection_short_range.tflite`

### Scene Classification (TFLite MobileNet)
Download any MobileNetV2 scene classification model from TensorFlow Hub:
https://tfhub.dev/google/tf2-preview/mobilenet_v2/classification/4
Convert to TFLite and save as: `scene_classification.tflite`
(Or use any 4-class classifier: outdoor/indoor/night/other)

---

## Troubleshooting

**Gradle sync fails with "Could not resolve..."**
→ Check internet connection. Gradle needs to download dependencies.
→ File → Invalidate Caches → Restart

**"Kotlin plugin not found"**
→ File → Settings → Plugins → search Kotlin → Install

**Build error: "Duplicate class"**
→ Build → Clean Project → then Build APK again

**App crashes immediately on launch**
→ View → Tool Windows → Logcat → look for red ERROR lines
→ Most common: missing permission or Android version mismatch

**Enhancement not visible**
→ The GLSL pipeline requires OpenGL ES 2.0 (all Android 4.3+ devices)
→ Toggle the enhance switch off and back on
→ Older devices fall back to ColorMatrix mode automatically

**Face enhancement not working**
→ Add the MediaPipe model to assets/ (see above)
→ Without the model file, face detection is silently disabled

**No audio tracks shown**
→ Only files with multiple embedded audio tracks show options
→ Most MP4 files have only one track

---

## Architecture

```
ui/
  MainActivity.kt       — Full player UI + all controls
  LibraryActivity.kt    — File browser + watch history
  SettingsActivity.kt   — Preferences screen

player/
  PlayerManager.kt      — ExoPlayer wrapper + effect pipeline
  PlaybackService.kt    — Background playback (MediaSession)

enhancement/
  LumiereShaderProgram.kt  — GLSL GPU shader (all visual effects)
  FaceEnhancementManager.kt — MediaPipe face detection
  SceneClassifier.kt       — TFLite scene classification
  AudioEqualizer.kt        — 5-band EQ + surround + bass
  EnhanceParams.kt         — Enhancement parameter data class

db/
  LumiereDatabase.kt    — Room DB: watch history + playlist

utils/
  GestureController.kt  — Swipe gestures
  FileScanner.kt        — MediaStore video scanner
  ScreenshotHelper.kt   — Save frame to gallery
  CastOptionsProvider.kt — Chromecast configuration
```

## Enhancement Pipeline

```
Video frame
    ↓
ExoPlayer Media3 texture
    ↓
LumiereShaderProgram (GLSL on GPU)
    ├── Deinterlace (optional)
    ├── Bilateral noise reduction
    ├── Shadow lift
    ├── Brightness × Contrast
    ├── Warmth (R/B shift)
    ├── Saturation (Rec.709 luma)
    ├── Unsharp mask sharpening
    ├── Scene-type adjustment (outdoor/night)
    ├── Face-region boost (MediaPipe regions)
    └── HDR tonemapping (optional)
    ↓
Display
```

---

## Version
2.0 — Lumière Player Pro
Engine: ExoPlayer Media3 1.2.1 · MediaPipe 0.10.9 · TFLite 2.14.0 · OpenGL ES 2.0
