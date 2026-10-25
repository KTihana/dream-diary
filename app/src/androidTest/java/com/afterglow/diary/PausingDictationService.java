package com.afterglow.diary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Ignores silence hints, like a provider with short automatic endpoints. Test APK only. */
public class PausingDictationService extends RecognitionService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    protected boolean segmented() { return false; }
    @Override protected void onStartListening(Intent intent, Callback callback) {
        handler.removeCallbacksAndMessages(null);
        // A disk counter survives service rebinding, without a cross-process preferences cache.
        int count = segmented() ? 0 : nextPhase();
        try {
            callback.readyForSpeech(new Bundle());
            if (segmented()) {
                callback.segmentResults(words("The river was silver."));
                handler.postDelayed(() -> {
                    try { callback.segmentResults(words("Then I saw the moon.")); }
                    catch (RemoteException ignored) { }
                }, 1700);
            } else if (count == 1) {
                callback.partialResults(words("The river was silver"));
                handler.postDelayed(() -> {
                    try { callback.endOfSpeech(); callback.results(words("The river was silver.")); }
                    catch (RemoteException ignored) { }
                }, 700);
            } else if (count == 2) {
                handler.postDelayed(() -> {
                    try { callback.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); }
                    catch (RemoteException ignored) { }
                }, 1400);
            } else {
                callback.beginningOfSpeech(); callback.rmsChanged(5f);
                callback.partialResults(words("Then I saw the moon"));
            }
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    private int nextPhase() {
        File counter = new File(getFilesDir(), "pause-starts");
        int count = 1;
        try {
            if (counter.exists()) try (FileInputStream input = new FileInputStream(counter)) { count = input.read() + 1; }
            try (FileOutputStream output = new FileOutputStream(counter)) { output.write(count); }
            return count;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onStopListening(Callback callback) {
        handler.removeCallbacksAndMessages(null);
        try {
            if (segmented()) callback.endOfSegmentedSession();
            else { callback.endOfSpeech(); callback.results(words("Then I saw the moon.")); }
        } catch (RemoteException ignored) { }
    }
    @Override protected void onCancel(Callback callback) { handler.removeCallbacksAndMessages(null); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private Bundle words(String text) {
        Bundle result = new Bundle(); ArrayList<String> words = new ArrayList<>(); words.add(text);
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, words); return result;
    }
}
