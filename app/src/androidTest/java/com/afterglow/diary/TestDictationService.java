package com.afterglow.diary;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import java.util.ArrayList;

/** Test APK only. Java keeps this separate service process independent of the app's Kotlin runtime. */
public class TestDictationService extends RecognitionService {
    private boolean croatian;
    private final Handler levels = new Handler(Looper.getMainLooper());
    private int sample;
    private final float[] speechLevels = {1f, 2f, 5f, 7f, 4f, 2f, 0f, 1f, 4f, 8f, 6f, 3f, 1f, 0f};
    @Override protected void onStartListening(Intent intent, Callback listener) {
        levels.removeCallbacksAndMessages(null);
        sample = 0;
        try {
            String language = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
            croatian = "hr-HR".equals(language);
            if ((!croatian && !"en-US".equals(language)) ||
                    (croatian && intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))) {
                listener.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
                return;
            }
            listener.readyForSpeech(new Bundle());
            listener.beginningOfSpeech();
            listener.partialResults(words(croatian ? "Šetala sam uz rijeku" : "I was walking"));
            // First no level callbacks, then silence, then a bounded speech envelope.
            // This is test input only; production never invents microphone activity.
            levels.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        listener.rmsChanged(sample < 20 ? -2f : speechLevels[(sample - 20) % speechLevels.length]);
                        sample++;
                        levels.postDelayed(this, 70);
                    } catch (RemoteException ignored) { levels.removeCallbacksAndMessages(null); }
                }
            }, 1200);
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onStopListening(Callback listener) {
        levels.removeCallbacksAndMessages(null);
        try {
            listener.endOfSpeech();
            final String result = croatian ? "Šetala sam uz rijeku i čula šapat. Đurđica je nosila žuti ključ, a noć je bila tiha." : "I was walking through a violet city.";
            // Leave a real finishing window so clearing pending transcription can be exercised.
            levels.postDelayed(() -> {
                try { listener.results(words(result)); }
                catch (RemoteException ignored) { }
            }, 1200);
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onCancel(Callback listener) { levels.removeCallbacksAndMessages(null); }
    @Override public void onDestroy() { levels.removeCallbacksAndMessages(null); super.onDestroy(); }
    private Bundle words(String value) {
        Bundle result = new Bundle();
        ArrayList<String> words = new ArrayList<>();
        words.add(value);
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, words);
        return result;
    }
}
