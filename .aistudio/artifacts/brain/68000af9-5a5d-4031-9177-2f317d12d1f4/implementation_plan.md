# Floating Quiz & Math AI Solver (Gemini Flash HUD)

A high-speed, floating overlay Android application powered by Google's Gemini Flash model, designed to solve multiple-choice quizzes, math equations, and bilingual (Arabic & English) educational questions in real time with an instant flash answer popup, zero UI freezing via dedicated Kotlin Coroutine dispatchers, and a battery-saving Start/Stop master toggle.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions incorporate your selections and the sub-1.5 second asynchronous Coroutine pipeline:

- **Confirmed Decision 1 (Floating Master Control Panel)**: The floating overlay features an instant dual-state master switch:
  - **Start / Play (`تشغيل`)**: Activates the solver engine with a vibrant glowing emerald status indicator (`Active / جاهز`), ready to capture and solve quizzes in under 1.5 seconds upon tapping.
  - **Stop / Pause (`إيقاف`)**: Immediately suspends screen capture, background polling, and AI network queries, switching the indicator to a dim crimson/slate state (`Standby / متوقف`) to preserve battery, CPU, and network quota.
- **Confirmed Decision 2 (Zero UI Freezing via Asynchronous Coroutines)**:
  - **Main Thread Isolation (`Dispatchers.Main.immediate`)**: The floating button, dragging interactions, and animations run completely decoupled from data processing, ensuring fluid 60/120 FPS without any frame drops.
  - **Image Processing on `Dispatchers.Default`**: Fast downscaling and JPEG compression (~70ms) to generate a lightweight image payload under 80 KB.
  - **Network I/O on `Dispatchers.IO`**: OkHttp requests to Gemini Flash run with pre-warmed HTTP/2 persistent connections and aggressive timeouts.
  - **Strict Latency Budget (< 1.5s total)**:
    - Capture & JPEG compression: ~60ms (`Dispatchers.Default`)
    - Gemini Flash HTTP/2 payload transfer & generation: ~750ms (`Dispatchers.IO`)
    - Token extraction & popup reveal: ~10ms (`Dispatchers.Main.immediate`)
    - **Total latency: ~820ms – well below your 1.5-second threshold.**
- **Confirmed Decision 3 (Instant Flash Answer Display)**: High-speed, high-contrast flash badge (e.g., **`[ B ]`** or **`x = 7`**) right beside the floating button, with tap-to-expand explanation drawer in Arabic or English.
- **Confirmed Decision 4 (Bilingual Arabic & English Engine)**: Optimized Gemini Flash prompt schema for right-to-left Arabic multiple choice quizzes and English math/science questions.
- **Mobile-First Direct Verification**: Includes both the Floating Foreground Service (`SYSTEM_ALERT_WINDOW`) and an interactive In-App Quiz & Math Simulator in `MainActivity` for safe local testing and verification without needing a PC.

---

## 1. Overview & Core Concept

### What It Does
The app runs a high-performance Android Foreground Service with a floating draggable overlay (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`). While running quizzes in other apps (such as Kahoot, Quizizz, Telegram quiz bots, educational test portals, or math PDFs), users simply tap the floating button. The solver extracts the problem text/screenshot, transmits it to Google Gemini Flash via an optimized lightweight coroutine HTTP client, and flashes the correct answer badge beside the trigger button in under 1.5 seconds.

### Target Audience & Persona
Students, competitive quiz participants, and mobile learners who need instant math verification, multiple-choice trivia resolution, or bilingual educational assistance without switching away from their active quiz app.

### Key Value
- **Sub-1.5-Second Total Turnaround**: Ultra-low latency Gemini Flash configuration with deterministic JSON/text parsing.
- **100% Fluid Floating HUD**: Pure asynchronous coroutine execution with zero main-thread blocking.
- **Zero-Disruption Screen Footprint**: Draggable anywhere along screen edges; collapsible at will.
- **Battery & Quota Protection**: Dedicated Start/Stop toggle halts all background activity instantly.

---

## 2. User Experience & Visual Design

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│                          USER JOURNEY FLOW                             │
└────────────────────────────────────────────────────────────────────────┘

1. Launch Companion Hub ──► 2. Grant Overlay Permission ──► 3. Start Floating HUD
   (MainActivity Compose)      (Settings.canDrawOverlays)     (FloatingSolverService)
            │                                                         │
            ▼                                                         ▼
4. Test In-App Solver   ◄────────────────────────────── 5. Overlay on Screen
   (Instant Sandbox,                                       [● Stop | Start]
    API key check & sample                                 [⚡ Solve Target]
    Arabic/English questions)                                         │
                                                                      ▼
                                                        6. Tap Solve in Quiz App
                                                                      │ (Async Coroutines)
                                                                      ▼
                                                        7. Instant Flash Answer (<1.5s)
                                                           ┌──────────────────┐
                                                           │  [ B ] (98% conf)│
                                                           │  Tap for steps   │
                                                           └──────────────────┘
```

### Visual Identity & Theme
- **Aesthetic Direction**: Tactical Neon HUD / Cyberpunk Glassmorphism. Dark background (`#0B0F17`) with glowing emerald accents for active solver states, high-visibility amber for warnings, and crisp white typography for instantaneous readability.
- **Color Palette**:
  - `HudBackground`: `#101726` (Deep Navy / Midnight)
  - `HudSurface`: `#1A233A` (Translucent Slate Card)
  - `StateActiveGreen`: `#00E676` (Glowing Emerald for Play / تشغيل)
  - `StateInactiveRed`: `#EF4444` (Standby Crimson for Stop / إيقاف)
  - `AccentAmber`: `#F59E0B` (Instant Answer Flash Highlight)
  - `TextPrimary`: `#FFFFFF` (High-contrast pure white)
  - `TextSecondary`: `#94A3B8` (Muted silver)
- **Typography & Hierarchy**:
  - Answer Flash Badge: 28sp Bold Monospace for instantaneous letter/number recognition at a glance.
  - Controls & Buttons: 14sp Semi-Bold with Arabic and English bilingual labels.
- **Interactive Feedback**:
  - Smooth haptic click on overlay drag and solve trigger.
  - Pulsing micro-glow around the floating trigger button while Gemini is thinking.
  - Flash animation when the answer card pops up next to the floating trigger.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Alternatives Considered |
|---|---|---|---|
| **Threading Model** | Structured Coroutines (`SupervisorJob + Dispatchers.Default + Dispatchers.IO`) | Eliminates UI frame drops on 60Hz/120Hz screens; runs compression and network completely off the main thread. | Synchronous calls or AsyncTask (deprecated, causes UI freezes) |
| **Overlay Window Type** | `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` | Allows smooth touch passthrough to the background quiz app so the quiz timer doesn't lose focus. | Focusable dialogs (blocks quiz app interactions) |
| **API Client** | Direct OkHttp with pre-warmed connection pool calling `gemini-3.5-flash` / `gemini-flash-latest` | Keeps APK ultra-small (<15MB), ensures persistent TCP/TLS handshake reuse, and avoids bulky unused dependencies. | Heavy generic SDKs or serverless middle-layers |
| **Answer Extraction Mode** | Direct Gemini Flash JSON/text prompt with strict single-token schema | Guarantees instant letter extraction (e.g. `A/B/C/D`) and direct formula answer in ~700ms without post-processing latency. | Full markdown essays (too slow to read during 5-second countdowns) |
| **Master Control Switch** | Play/Pause toggle directly in the floating overlay header | Solves battery drain and unnecessary API token consumption when the quiz is paused or between rounds. | Dismissing the entire service (requires re-opening main app) |
| **Dual Testing Interface** | Full in-app simulator + Real system overlay service | Allows the user to test questions, Arabic OCR, and API responses directly inside the app on Android without needing a second device or PC. | Overlay-only (hard to debug without PC) |

---

## 4. Technical Architecture & Data Strategy

### System Architecture & Coroutine Pipeline

```
┌────────────────────────────────────────────────────────────────────────┐
│               ASYNCHRONOUS COROUTINE PIPELINE (<1.5s)                  │
└────────────────────────────────────────────────────────────────────────┘

 [Floating HUD UI]  (Dispatchers.Main.immediate)
      │
      ├─ User Taps "Solve" ──► Instant Haptic & Pulsing Indicator (0ms)
      │
      ├─ launch(Dispatchers.Default)
      │    └── Downscale & Compress Image to 75% JPEG (~60ms)
      │
      ├─ withContext(Dispatchers.IO)
      │    └── Send HTTP/2 Request to Gemini Flash via OkHttp (~750ms)
      │    └── Receive & Parse Token Stream / JSON Response
      │
      └─ withContext(Dispatchers.Main.immediate)
           └── Display Flash Answer Badge [ B ] (<10ms)
```

### Prompt Engineering for Instant Answers
The system prompt instructed to Gemini Flash:
```
You are an ultra-fast quiz and math solver. Analyze the question and options (in Arabic or English).
Output format MUST be strictly:
ANSWER: [Option Letter or exact math result]
EXPLANATION: [1 short sentence in question's language]
Example:
ANSWER: B
EXPLANATION: In photosynthesis, chlorophyll absorbs red and blue light.
```

### Permissions Required (`AndroidManifest.xml`)
- `android.permission.SYSTEM_ALERT_WINDOW`: Display floating overlay over other apps.
- `android.permission.FOREGROUND_SERVICE`: Run ongoing overlay service.
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: Android 14+ foreground service type compliance.
- `android.permission.INTERNET`: Direct Gemini Flash API calls.
- `android.permission.VIBRATE`: Tactile haptic feedback on solve.
- `android.permission.POST_NOTIFICATIONS`: Foreground service notification requirement.

---

## Step-by-Step Implementation Strategy

1. **Step 1 - Core Configuration & Secrets**: Enable `GEMINI_API_KEY` in `.env.example`, configure `AndroidManifest.xml` with overlay & foreground permissions, and add descriptive strings in `strings.xml`.
2. **Step 2 - Ultra-Fast Coroutine Gemini Solver Engine**: Implement `GeminiFlashClient` with OkHttp connection pooling, pre-warmed HTTP/2 client, `Dispatchers.IO` network execution, bilingual system instructions, and sub-1.5s parsing.
3. **Step 3 - Floating Service & WindowManager HUD**: Build `FloatingSolverService` featuring the draggable touch listener, the Start/Stop master toggle with real-time green/red visual indicators, and the floating single-letter flash card powered by `Dispatchers.Main.immediate` and `Dispatchers.Default`.
4. **Step 4 - Companion Control & Testing App**: Build a polished Material 3 dark dashboard in `MainActivity` with live permission checks, overlay launch button, in-app test quiz solver (supporting Arabic & English trivia and math formulas), and real-time latency benchmark showing timing in milliseconds.
5. **Step 5 - Mobile-First Guide**: Provide detailed on-device running instructions for building and running directly on Android phones.
