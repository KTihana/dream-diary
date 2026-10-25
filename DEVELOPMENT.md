# Dream Diary development guide

A native Kotlin / Jetpack Compose dream diary. Full-screen ivory paper, Lora typography, a discreet lucid marker, and animated notebook pages in portrait and landscape. No green, mood buttons, automatic titles, or AI interpretations.

## Writing and turning pages

- Write your own title and dream directly on the page. Tap the date to change the night. Tap the microphone in the notebook header to speak instead of typing.
- Lined paper follows the measured title and writing rows, keeping text between the rules when you change fonts, text size, or orientation.
- Tap the small **Lucid** checkbox beside the page number. It is a quiet ink mark with a larger touch target; tapping it again marks the dream non-lucid. Untouched dreams remain unclassified. Checking Lucid softly fades this dream’s paper to pastel lavender, including both landscape leaves. Unchecking restores the chosen paper tone; night mode uses a dim purple paper.
- Long dreams flow onto measured sheets. Typing continues onto the next sheet with the cursor, and opening the keyboard keeps sheet boundaries stable. Turn them with the arrows, folded corner, or a horizontal swipe when you aren't editing. After the last sheet, the next arrow opens the next dream.
- **Notebook options → Continue on a new page** inserts a deliberate sheet break. Editing that sheet keeps the rest of the dream intact. Layout changes reflow automatic pages without changing the underlying text.
- The **+ button** starts another dream, including more than one on the same night. Titles stay blank until you write them.
- Paper fills the screen in both orientations. Landscape uses two full-width leaves and a centred fold. Content avoids camera cutouts; the paper extends behind them.
- Edits autosave on the device. Interrupted writes keep the previous notebook. Version 3 stores editable page drawings. Existing version 1 and 2 notebooks retain their text, audio and sketches.

## Your notebook

The **Dreams button** opens four sections. **Practice** is also available directly in the bottom toolbar:

- **Dreams:** entries grouped by month on warm paper cards, with a date gutter, serif titles and text previews. Lucid dreams have a quiet lavender tint; bookmarks, drawings, voice notes and the last-opened entry are marked. The pen button starts a new dream. In landscape, search and filters sit beside compact previews. Search titles, text, symbols, people, places, and dates. All search terms must match; quotation marks keep an exact phrase together. Filter by lucid / non-lucid, bookmarks, and inclusive date range.
- **Patterns:** recorded dreams and nights, lucid percentage among dreams with a known lucid choice, an eight-week activity chart, repeated symbols, people and places, and average optional lucid ratings. Tap a recurring item to find its dreams. Empty pages are excluded from these statistics.
- **Symbols:** your personal dream dictionary. Write and edit your own meanings and open the dreams linked to a symbol. Removing a dictionary meaning keeps the dreams.
- **Practice:** lucid-dreaming routines, reminders, intentions, morning review, history, optional nighttime tools, and tips with research links. Open it from **Practice** in the diary dock or the Dreams tabs.

## Lucid practice

- **Reality-check reminders:** enable a schedule of 1–12 prompts in a chosen daytime window, on selected days. Choose varied times or fixed evenly spaced times, sound, and vibration. Notification actions open the guided check, snooze ten minutes within the allowed window, or pause until tomorrow. Daytime prompts are inexact and may be delayed by Android. Sound and vibration also follow Android channel settings. An independent optional evening reminder opens MILD.
- **Guided check:** twenty seconds of deliberate reflection, with four steps. Guides support pause, optional installed offline English device voice, transition haptics, and a dim screen. Leaving or locking the app pauses the guide. While running, the guide keeps its page awake.
- **Dream signs:** choose symbols, people and places from your own entries, or add a custom sign. Selected signs appear in reminder prompts and rehearsal suggestions.
- **MILD:** choose a recent dream or describe a remembered scene, identify an unusual detail, visualize recognizing the dream, and rehearse your own intention and goal. Move through the guide at your own pace.
- **SSILD:** a timed sequence of four quick sensory cycles followed by three slower cycles through vision, hearing and bodily sensation. The text guide works without a speech engine.
- **Intention and dream goal:** autosaving notes for the waking date of the coming night. A nighttime notification passes its actual waking date into the MILD guide.
- **Morning review:** record recall, lucidity, what prompted awareness, practices and optional sleep quality/notes. Write or record a dream from the review with the selected date. The existing morning notification now opens review and retains a direct Write action.
- **History:** completed practice, night notes and comparisons by method. Future nights are not scored. Unrecalled and unanswered nights remain unknown; a lucid diary entry counts toward its waking date. Multiple methods can share a night, so comparisons describe associations, not causation. Nighttime playback is recorded as playback, not proof of sleeping or waking.
- **Nighttime tools:** wake-back-to-bed and trained sound cues both default off. Start one session explicitly when going to sleep. Wake reminders use alarm volume and sound for at most a minute. Cues use media volume, the selected original bundled tone, a bounded count and gaps of at least fifteen minutes. Cue playback requires a completed rehearsal of the same sound in the preceding 24 hours. Audio runs in a media foreground service only while playing, with a Stop tonight action; no service or wake lock waits all night. Android notification access and, on Android 12+, Alarms & reminders access are needed. Each session ends after its events or a stop; reboot, clock/timezone changes and app updates cancel it. No automatic nightly repeat. Sound cues are experimental and scheduled, without REM detection.
- **Tips & tricks:** a searchable offline guide to recall, attentive checks, dream signs, MILD, SSILD, awareness versus control, cue training, sleep and interpreting history. Evidence labels and links distinguish supported, mixed and experimental techniques.

All practice notes are separate from the existing diary file. Every prompt and night tool starts off on a new installation. No efficacy guarantee, streaks, cloud account or AI interpretation is added.

## Notebook tools and settings

The compact **Notebook options** menu contains Dream details, Bookmark, Continue on a new page, Settings, and Delete. Recording, drawing, and practice have dedicated buttons on the diary. **Settings** has focused Writing and Paper pages, page-marker colors, night paper, and morning/reality-check reminder shortcuts. A live page preview reflects your appearance preferences.

- **Writing:** eight offline styles—Book (Lora), Handwritten (Caveat), Journal (Kalam), Cursive (Dancing Script), Literary (Crimson Text), Typewriter (Courier Prime), Rounded (Nunito), and Simple (Android sans). Choose 16–26 sp text, Compact/Comfortable/Airy spacing, Charcoal/Blue/Sepia/Plum ink, and optionally use the same font for titles. The editor, paginator and preview share a writing style; lines follow actual text layout.
- **Paper:** Ivory, White, Sand or Rose, with Lined/Dotted/Plain ruling. Lucid dreams retain lavender paper; night paper dims the page and uses lighter ink colors. Five page-marker colors are available. Reset appearance changes only appearance, preserving dreams, language, prompts and reminder settings. Existing installations retain their previous choices and get neutral defaults for the new fields.

- **Record or dictate:** open the microphone beside Notebook options. **Start dictation** keeps a take open until you tap **Finish dictation**. Pauses do not send you back to the start button: supported providers stream segments, and providers that end individual phrases are restarted automatically. Silence hints request a ten-second pause window; Android providers may ignore them. A restart can have a brief provider-dependent gap, and permission/network/service failures still stop the take with an explanation. Review accumulated words before adding them. Closing the panel, rotating, or leaving the app stops capture and cancels pending restarts. A compact ink-blue waveform shows sound levels supplied by the speech recognizer. It settles to a flat line during silence and disappears when dictation ends. Providers that do not send level callbacks still show a microphone-ready state, without simulated audio activity. Review or edit the words, dictate more if needed, then tap **Add to dream** to return to your page. The text appends to your dream and keeps your existing writing and title. **Erase all** clears the current dictated draft and stops any active or finishing recognition, ready for a fresh take. Already-saved dream text and audio notes stay intact. You can also save microphone audio with the dream, play it back, or remove it after confirmation. Choose **Hrvatski** or **English** above Start dictation. Croatian (`hr-HR`) is the default, and your language choice is remembered. Every request explicitly uses the chosen language even if the phone's language is different. On-device recognition is preferred; if its selected language is unavailable or unsupported, the app retries through the installed Android speech service with the same language and without forcing offline recognition. This service may need internet. If that service also lacks the language, the app shows a language-specific explanation and an **Open speech settings** action. Croatian letters č, ć, đ, š, ž and their capitals are present in all three bundled notebook font files. That service's availability and processing vary by device. Devices without speech recognition can still record and type. Recording stops and saves when you close the panel, rotate, or leave the app; it does not continue in the background. Individual notes are limited to 30 minutes.
- **Draw on the page:** switch between **Write** and **Draw** in the notebook dock. Use a pen or highlighter, eight colors plus a custom hex color, line thickness, and a true transparent eraser. **Move** selects a drawing: drag to move it, pinch to resize and rotate, or use **Arrange** for size, angle, opacity, transparent/paper background and placement behind the writing. Drawings default to transparent and above the text. **New drawing** creates an independently movable layer; **Layers** selects earlier drawings. Undo/redo covers strokes, erasing, placement and deletion, with up to 50 edits per dream while the app remains open, including rotation. **Done** or **Write** leaves the art on the diary page. Completed edits autosave. A drawing stays on its numbered sheet and keeps its proportions across orientations; text can reflow independently. In landscape the tools sit on the left leaf, leaving the writing leaf clear. Existing sketches appear as editable transparent drawings; their original data is retained until the first drawing edit. Tap **Hide** for more drawing space.
- **Dream details:** optional comma-separated symbols, people, and places. Lucid dreams also have awareness and control scales (1–5; tap again to unmark), technique/trigger, and estimated lucid minutes.
- **Bookmark this dream:** find marked dreams in the index.
- **Settings → Your notebook:** lined, dotted, or plain paper; book, handwritten, or sans-serif writing; four text sizes; linen, rose, or midnight page-marker colour; and dim night paper. Preferences persist after restarting.
- **Settings → Morning recall:** gentle recall prompts, optional prompts on empty pages, and one optional morning notification around a chosen local time. Android can delay reminders; these are not alarm-clock alarms. Enable notifications when asked. Reminders reschedule after reboot, clock, or timezone changes. There are no streaks or missed-day messages.
- **Delete this dream:** confirmation before removal. Its audio files are deleted after the updated notebook is saved successfully.

Add the **Dream Diary quick-capture widget** from your launcher (or its button in Settings → Morning recall), with Write and Record actions. Add **Catch a dream** to Quick Settings to open recording after unlocking. The widget is eligible for lock screens on Android versions/launchers that support lock-screen widgets; availability is controlled by Android.

The app has no account, cloud sync, app lock, backup, export, or AI feature. Dreams and recordings stay in private app storage. The app uses internet access only when you check for or download a GitHub update; update requests contain no diary data or device/account identifiers. The installed speech-recognition service may use its own network access. Uninstalling removes the notebook; Android device backup is disabled.

## Install on your phone

The installable developer build is [Dream Diary 1.6.0](https://github.com/KTihana/dream-diary/releases/download/v1.6.0/Dream-Diary-1.6.0.apk). Transfer the APK to an Android phone running Android 8.0 or newer, open it in Files and tap Install. Android may ask you to allow installation from that Files app. See the [short installation guide](dist/INSTALL.md).

A new installation starts with a blank notebook. The app remembers your last-opened dream across app restarts, as well as your entries and settings. The APK is signed with this machine's Android development key and updates earlier APKs signed with the same key without deleting their notebooks. It is a developer build for direct installation; store distribution needs production signing.

## In-app updates

Install 1.6.0 manually once, then open **Settings → App updates** for future releases. Checks run only on request against the public `KTihana/dream-diary` latest-release endpoint, without authentication or diary content. The updater accepts one uploaded stable-version APK from this repository with a GitHub SHA-256 digest, limits metadata/download sizes and redirects to HTTPS GitHub hosts, and streams the download to private cache. Before installation it checks the file checksum/size, package name, current signing certificates, increasing Android version code, matching release version name, and minimum Android version.

A completed download is revalidated when restored after process death and again before the installer opens. Cancelled/interrupted downloads can be retried. Rotation and closing the panel keep an active download in the ViewModel; process termination can interrupt it, so users are asked to keep the app open. A narrow FileProvider exposes only the update cache, with a temporary read grant for the APK; diary files cannot be shared through it. Android controls the per-app install-source permission and the final confirmation. Declining or cancelling leaves the diary intact. Keep the existing signing key and increase both the Gradle version code and release version for every published update. GitHub tags use `vX.Y.Z`, with asset `Dream-Diary-X.Y.Z.apk`.

Sources: [GitHub latest releases](https://docs.github.com/en/rest/releases/releases#get-the-latest-release), [GitHub asset digests](https://github.blog/changelog/2025-06-03-releases-now-expose-digests-for-release-assets/), [Android install-source permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), and [FileProvider grants](https://developer.android.com/reference/androidx/core/content/FileProvider).

## Open and build the Kotlin project

Open this directory in Android Studio, sync Gradle, and run `app`.

Requirements: JDK 17 or 21 and Android SDK 36. The wrapper uses Gradle 8.14.3. Android Studio configures the SDK path, or create a git-ignored `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`.

```sh
./gradlew :app:packageInstallable
```

The packaging task builds the app and copies its signed APK into `dist`. The package ID remains `com.afterglow.diary` to preserve existing installs. No preview dreams or test fixtures are bundled with the app.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=your-test-device ./gradlew :app:connectedDebugAndroidTest
```

Version 1.7.0 expands notebook customization. All 35 JVM tests pass and Android lint has zero errors. Four targeted device flows pass for appearance persistence/reset, unchanged dream text, rendered font/rule alignment, audio and reminder navigation, and lossless page editing across rotation. Alignment checks cover all eight styles, three spacings and 16/26 sp samples in portrait and landscape; the Rounded subset was rerun after selecting its normal variable-font weight. Native screenshots include writing/paper settings, night mode, and customized lucid pages. Installing over 1.6.0 preserved the original notebook bytes exactly. No physical-phone test was performed. See [current validation details](dist/validation.json).

Version 1.6.0 adds in-app updates. All 35 JVM tests pass and Android lint has zero errors. Three updater device flows pass on the packaged APK, covering real GitHub checks, cancellation and recovery, restored downloads, checksum/package/signature/version checks, read-only installer grants, and diary-file isolation. A fourth flow builds the same updater with older version metadata to download the real public 1.5.2 release, open Android’s install-source settings, handle the permission return, and open/cancel the installer. The test grants install-source access only on the development emulator. Final installation remains a user action; it was not automated. An upgrade to 1.6.0 preserved the notebook bytes exactly. Native screenshots cover portrait, landscape and the current-version result. No physical-phone validation was performed. See [current validation details](dist/validation.json).

Version 1.5.2 simplifies the menu and consolidates appearance preferences and reminder shortcuts in Settings. Android lint has zero errors. Two targeted device flows pass: audio capture and settings changes (paper, font, marker, text size, night mode), persistence after recreation, morning/reality reminder navigation and return paths; plus library search, details, bookmarks, page continuation and drawing. Native captures cover portrait, landscape and night settings layouts. The APK updates 1.5.1 with the same signing key. Broader tests were not rerun for this change.

Version 1.5.1 refreshes the library presentation. Its packaged APK passes Android lint with zero errors and the existing search, dictionary, bookmark, page-continuation, drawing and recreation device flow. Native screenshots verify portrait, landscape and night mode using example entries on an Android 16 / API 36 emulator. The APK updates 1.5.0 with the same signing key. The broader 1.5.0 tests below were not rerun for this visual change.

Version 1.5.0 passes all 30 JVM tests and Android lint with zero errors. Eleven distinct Android device flows pass, including direct page drawing, rendered eraser transparency, colors/thickness, undo/redo, placement, rotation, opacity, saving/recreation, notebook editing, pagination and text alignment. Six voice flows cover English/Croatian, waveform/lifecycle behavior, Erase all, short phrase endpoints followed by silence timeouts, and continuous segments. Both pause flows accumulate the exact words, remain open until manual finish, and do not restart after finishing. Phrase restarts reuse the speech-provider connection. Finish and the meter scroll into view together on short screens. Voice flows were rechecked after their fixes; the test fixture allows time for the initial ready state to be observed during scrolling. An upgrade from 1.4.4 preserved an earlier title, Croatian text and legacy sketch. Speech fixtures supply words and sound levels; physical-phone speech accuracy and provider availability remain untested.

Version 1.4.4 passes all 25 JVM tests and Android lint with zero errors. Four targeted device flows cover English/Croatian dictation and waveform behavior, plus **Erase all** while listening, reviewing and finishing transcription. The clear flow checks portrait/landscape, an empty draft after recreation and pending-result delay, successful restart, preservation of the existing dream title/body, and appending only the new Croatian take. Native screenshots show the clear action and reset state. The test speech service supplies example words, levels and a delayed final result; physical-phone acoustic accuracy was not tested.

Version 1.4.3 passed all 25 JVM tests and Android lint with zero errors. Three targeted device flows cover English and Croatian dictation, the no-level-callback ready state, quiet versus speech RMS feedback, rotation and foreground cleanup, and removal of the waveform after finishing. Native screenshots verify the meter in portrait and landscape. The recognizer fixture supplies example words and timed sound levels; this validates app behavior rather than physical-phone acoustic accuracy.

Version 1.4.2 passed all 25 JVM tests and Android lint with zero errors. Two targeted device flows pass for English and Croatian dictation. The Croatian test verifies an explicit `hr-HR` request, that the default speech service is not forced into offline recognition, persisted language choice, review and saving of Croatian diacritics to the actual UTF-8 notebook file. All bundled fonts contain čćđšžČĆĐŠŽ. The recognizer fixture supplies fixed words; actual Croatian acoustic-model accuracy and physical-phone language availability are not tested.

Version 1.4.1 passed all 25 JVM tests and Android lint with zero errors. Two targeted device flows pass: microphone recording/playback with file validation, and dictation through Android’s default speech-service path. The dictation test uses a service in the test APK to supply fixed partial and final results, verifies review/edit/empty-input handling, appends the words without replacing the title or existing text, and checks the result after activity recreation. The test restores the previous speech-service setting. After validation, the test APK and its leftover automatic provider selection were removed from the emulator. This verifies the app’s transcription flow, not the accuracy of an installed acoustic model.

The earlier version 1.4.0 baseline passes all 25 JVM tests and Android lint with zero errors. Twelve device flows pass on an Android 16 / API 36 emulator. They cover atomic storage and corrupted-file preservation, editing and autosave, lucid toggling and rendered lavender/ivory colors, page turning and lossless pagination, rotation/recreation, text alignment across fonts/sizes, search and filters, dictionary links, sketches, audio recording/playback, appearance, practice guides and history, reminder notifications/actions, and the actual exact-alarm → receiver → foreground cue playback → cleanup path.

The dictionary flow was rerun after updating its scroll selector for the virtualized list. The night-cue fixture accelerates one cue to a few seconds; production bedtime offsets stay at 5–8 hours. Device tests grant the microphone, notification and exact-alarm access they exercise. No physical overnight session or acoustic-model speech recognition was tested; dictation depends on an installed Android speech service. Nighttime tools remain off until the user enables and starts a session.

A separate fresh-install check starts on a blank notebook. A sample dream was typed through the native editor, marked lucid, saved and retained after force-stopping/reopening the app; the last-selected second page reopened correctly.

Updater device tests accept a separate, locally built next-version APK in the development emulator’s private `files/update-fixture.apk`, plus a copy signed with an unrelated test key in `files/update-wrong-signature.apk`. Build the example next-version fixture with `-PdiaryVersionCode=17 -PdiaryVersionName=1.7.1 :app:assembleDebug`; the normal build defaults are 16 / 1.7.0. Do not publish the fixture. Tests without these files skip the fixture-dependent cases. The `liveUpdateCheck=true` and `liveDownloadCheck=true` instrumentation arguments additionally exercise the real public API and APK CDN; `openUpdateInstaller=true` checks the Android confirmation screen on a development emulator with install-source access already granted. `captureUpdates=true` saves native screenshots. The separate `oldUpdaterFlow=true` test runs the updater built as version 1.5.1/code 13 against the real public 1.5.2 release, checks Android’s install-source screen and installer handoff, then cancels. These optional capture/network steps are not included in the normal offline unit suite.

The APK signature and declared Android version compatibility are verified. The GitHub release includes a `SHA256SUMS` file with the installer checksum. Native screenshots use the packaged APK.

## Screenshots and design

Screenshots use example dreams on a development emulator. New installations open with a blank page.

![Writing styles](screenshots/writing-fonts-portrait-1.7.0.png)
![Writing styles in landscape](screenshots/writing-fonts-landscape-1.7.0.png)
![Ink, size and spacing](screenshots/writing-options-portrait-1.7.0.png)
![Paper tones](screenshots/paper-options-portrait-1.7.0.png)
![Custom writing on lucid paper](screenshots/custom-lucid-portrait-1.7.0.png)
![Custom writing in landscape](screenshots/custom-lucid-landscape-1.7.0.png)
![Writing at night](screenshots/writing-night-portrait-1.7.0.png)

![App updates](screenshots/updates-portrait-1.6.0.png)
![App updates in landscape](screenshots/updates-landscape-1.6.0.png)
![Latest-version result](screenshots/updates-current-1.6.0.png)

![Settings in portrait](screenshots/settings-portrait-1.5.2.png)
![Settings in landscape](screenshots/settings-landscape-1.5.2.png)
![Settings at night](screenshots/settings-night-1.5.2.png)

![Your dreams in portrait](screenshots/your-dreams-portrait-1.5.1.png)
![Your dreams in landscape](screenshots/your-dreams-landscape-1.5.1.png)
![Your dreams in night mode](screenshots/your-dreams-night-1.5.1.png)

![Drawing directly on a lucid dream page](screenshots/drawing-tools-portrait-1.5.0.png)
![A drawing saved in the diary](screenshots/drawing-in-diary-1.5.0.png)
![Drawing tools in landscape](screenshots/drawing-tools-landscape-1.5.0.png)
![Dictation stays open through pauses](screenshots/dictation-through-pauses-1.5.0.png)
![Finish and sound meter in landscape](screenshots/dictation-waveform-landscape-1.5.0.png)
![Live speech-to-text sound meter with example Croatian input](screenshots/dictation-waveform-portrait-1.4.3.png)
![Clear a dictated draft and start again](screenshots/dictation-erase-all-portrait-1.4.4.png)
![Practice](screenshots/practice-1.3.png)
![Reminders](screenshots/reminders-1.3.png)
![Tips](screenshots/tips-1.3.png)

The earlier [Figma screens and Canva direction board](design/README.md) established the notebook concept. The current [design revision](design/apple-design-notes.md) refines its navigation, hierarchy, typography and control surfaces. Lora, Caveat, Kalam, Dancing Script, Crimson Text, Courier Prime and Nunito are bundled for offline use under their included OFL licenses. All bundled faces include Croatian ČĆĐŠŽ/čćđšž. Sources and checksums for the five added fonts are in [font sources](design/font-sources.json).

## Structure and storage

- `NotebookDesign.kt`: shared colors, type hierarchy, grouped sections, line icons and practice dial.
- `AfterglowApp.kt`: paper drawing, responsive notebook, text measurement/pagination, page-turn animation, menus, and date picker.
- `PaperRuling.kt`: text-aligned notebook rules, including the editor's scrolling above the keyboard.
- `FeaturePanels.kt`: index, patterns, personal dictionary, lucid details, appearance, and recall preferences.
- `DreamLibraryDesign.kt`: paper entry previews, date typography, search, filters, and library navigation.
- `AppRelease.kt` / `AppUpdates.kt` / `AppUpdatePanel.kt`: GitHub release validation, download/verification, private APK sharing and user-controlled installation.
- `NotebookSettings.kt` / `NotebookAppearance.kt`: font previews, focused writing/paper settings, shared writing styles and palettes, night paper and direct reminder routes.
- `PageDrawing.kt`: editable transparent vector layers, pen/highlighter/eraser, movement and transforms, drawing controls, and undo/redo.
- `VoicePanel.kt` / `DictationWaveform.kt`: microphone recording, playback, live transcription with a sound-level envelope, permission/lifecycle handling.
- `RecallReminder.kt`: daily reminder receiver, capture widget, and Quick Settings tile.
- `LucidPracticeModels.kt` / `LucidPracticeStore.kt`: versioned practice data, reminder calculations, waking-date outcomes and serialized preference transactions.
- `LucidPracticePanel.kt` / `LucidPracticeSettings.kt` / `LucidPracticeRoutines.kt`: practice navigation, settings, guides, morning review and history.
- `LucidPracticeGuide.kt`: offline tips, evidence/source links and optional installed offline device voice.
- `PracticeAlarms.kt` / `LucidNightService.kt`: notification actions, one-night exact alarms, bounded sound playback and cancellation.
- `DiaryViewModel.kt`: current dream, edits, preferences, and a serialized autosave queue of complete snapshots.
- `DreamEntry.kt` / `JournalStore.kt`: versioned JSON and atomic private-file writes.
- `NotebookTools.kt`: search, recurring-label counts, page ranges, dictionary and appearance preferences.

Dreams (including sketch points and audio references) are in `files/dreams.json`. Audio is in `files/audio/*.m4a`; preferences and dictionary meanings use private SharedPreferences. Automatic page boundaries are derived from font and available space; explicit page breaks use a form-feed character preserved in the body. There is no separate copy of dream text to drift out of sync.

Practice data uses the private `lucid-practice` SharedPreferences file with its own versioned JSON. A process-wide transaction lock prevents notification/service writes from overwriting form edits. Unreadable or newer practice data is retained and shown as an error rather than replaced with an empty history. Recent event detail is limited to 1,000 entries; nightly totals and notes are retained separately.

Build/platform references: [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [sound-level callbacks](https://developer.android.com/reference/android/speech/RecognitionListener#onRmsChanged(float)), [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager), [Quick Settings tiles](https://developer.android.com/reference/android/service/quicksettings/TileService).
