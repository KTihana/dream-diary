package com.afterglow.diary;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import java.util.ArrayList;

/** Test APK only. Java keeps this separate service process independent of the app's Kotlin runtime. */
public class TestDictationService extends RecognitionService {
    private boolean croatian;
    @Override protected void onStartListening(Intent intent, Callback listener) {
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
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onStopListening(Callback listener) {
        try {
            listener.endOfSpeech();
            listener.results(words(croatian ? "Šetala sam uz rijeku i čula šapat. Đurđica je nosila žuti ključ, a noć je bila tiha." : "I was walking through a violet city."));
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onCancel(Callback listener) {}
    private Bundle words(String value) {
        Bundle result = new Bundle();
        ArrayList<String> words = new ArrayList<>();
        words.add(value);
        result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, words);
        return result;
    }
}
