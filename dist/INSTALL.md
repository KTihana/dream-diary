# Install Dream Diary on Android

1. [Download Dream Diary 1.6.0](https://github.com/KTihana/dream-diary/releases/download/v1.6.0/Dream-Diary-1.6.0.apk) on your Android phone (Android 8.0 or newer).
2. Open the APK in your phone's Files app. If Android asks, allow that app to install unknown apps, then tap Install.
3. Open **Dream Diary** from your launcher. A new installation starts with your own blank notebook.

Write a title and dream on the page. Edits save automatically. To speak instead, tap the microphone at the top, select **Hrvatski** or **English**, choose **Start dictation**, and tap **Finish dictation** when you are done. You can pause between phrases; the app keeps the take open. Then review the words and tap **Add to dream**. Tap **Erase all** to discard the current dictated words and start again; saved entries stay intact. A small waveform reacts to the sound levels reported by your speech service while you dictate. The speech service and language model must be available on your phone; audio recording is also available. Check **Lucid** for pastel lavender paper, and turn pages with the arrows or a swipe. Open **Practice → Reminders** to set reality-check notifications. Microphone, notification and timed-night permissions are requested when their features need them.

To draw, tap **Draw** beside **Write**. Choose a color and line thickness, then draw directly on the page. Use **Move** to select and drag a drawing, or pinch it to resize and rotate. **Arrange** has transparency, opacity and placement behind the writing. Tap **Write** when finished; your drawing stays on the page.

This is an installable developer build, signed with the same local Android development key as the earlier APKs. It can update those builds while keeping their data. Future updates need the same signing key. The Android Studio project remains in the parent folder.

The app stores dreams and recordings on the phone. It has no account or cloud sync. Keep the app installed to retain your notebook; uninstalling deletes its local data.

To package another copy from source:

```sh
./gradlew :app:packageInstallable
```

App-store publication requires a production signing key and a store listing.

Croatian dictation requests Croatian (Croatia), independently of your phone’s display language. If your Android speech provider does not have Croatian available, add or download it in that provider’s voice-input settings. Online recognition may need internet; ordinary audio notes do not need a speech language model.

## Future updates

After installing version 1.6.0, open **Notebook options → Settings → App updates → Check for updates**. Tap **Download update** when a newer version is available. Android may ask you to allow updates from Dream Diary once; return to the app and tap **Install update**, then confirm Android’s prompt. Your existing notebook and settings stay in place.

Updates are downloaded from the public GitHub releases. Keep the app open while downloading. You can cancel or retry; no uninstall is needed.
