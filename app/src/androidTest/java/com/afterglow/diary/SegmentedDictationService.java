package com.afterglow.diary;

/** Supported segmented recognition keeps the same session open through pauses. */
public class SegmentedDictationService extends PausingDictationService {
    @Override protected boolean segmented() { return true; }
}
