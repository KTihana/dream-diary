package com.afterglow.diary

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LucidPracticeTest {
    private val zone = ZoneId.of("Europe/Zagreb")
    private val date = LocalDate.of(2026, 10, 22)
    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone)

    @Test fun newNotebookHasNoAutomaticPromptsOrNightAudio() {
        val data = LucidPracticeCodec.decode(LucidPracticeCodec.encode(LucidPracticeData()))
        assertFalse(data.settings.realityEnabled)
        assertFalse(data.settings.bedtimeEnabled)
        assertFalse(data.settings.wakeEnabled)
        assertFalse(data.settings.cuesEnabled)
        assertNull(data.session)
        assertNull(nextRealityCheck(at(date, 10), data.settings))
    }

    @Test fun fixedScheduleSpacesChecksInsideTheWindow() {
        val settings = LucidSettings(realityEnabled = true, variedTimes = false)
        assertEquals(listOf(LocalTime.of(11, 0), LocalTime.of(15, 0), LocalTime.of(19, 0)), realityCheckTimes(date, settings))
        assertEquals(at(date, 15), nextRealityCheck(at(date, 11), settings))
        assertFalse(insideRealityWindow(at(date, 21), settings))
    }

    @Test fun variedScheduleStaysStableAndHasOneCheckPerTimeBucket() {
        val settings = LucidSettings(realityEnabled = true, checksPerDay = 12)
        val times = realityCheckTimes(date, settings)
        assertEquals(times, realityCheckTimes(date, settings))
        assertEquals(12, times.distinct().size)
        times.forEachIndexed { slot, time -> assertEquals(9 + slot, time.hour) }
        assertNotEquals(times, realityCheckTimes(date.plusDays(1), settings))
    }

    @Test fun quietHoursWeekendsAndPauseAreRespected() {
        val friday = LocalDate.of(2026, 10, 23)
        val settings = LucidSettings(realityEnabled = true, variedTimes = false, days = (1..5).toSet())
        assertEquals(at(friday.plusDays(3), 11), nextRealityCheck(at(friday, 22), settings))
        val paused = settings.copy(pausedUntil = at(friday, 16).toInstant().toEpochMilli())
        assertEquals(at(friday, 19), nextRealityCheck(at(friday, 8), paused))
        assertFalse(insideRealityWindow(at(friday.plusDays(1), 12), settings))
        assertFalse(insideRealityWindow(at(friday, 14), paused))
    }

    @Test fun springClockChangeUsesARealLocalTimeWithoutAnEarlyAlert() {
        val spring = LocalDate.of(2027, 3, 28)
        val settings = LucidSettings(realityEnabled = true, variedTimes = false, startMinute = 60, endMinute = 240)
        val next = nextRealityCheck(at(spring, 1, 45), settings)!!
        assertEquals(LocalTime.of(3, 30), next.toLocalTime())
        assertTrue(next.isAfter(at(spring, 1, 45)))
        assertTrue(insideRealityWindow(next, settings))
    }

    @Test fun nightDelaysAreElapsedHoursEvenWhenClocksGoBack() {
        val start = at(LocalDate.of(2026, 10, 24), 23)
        val session = makeNightSession(start, LucidSettings(wakeEnabled = true, cuesEnabled = true))
        assertEquals(5 * 60 * 60_000L, session.wakeAt - session.startedAt)
        assertEquals(6 * 60 * 60_000L, session.cueAt - session.startedAt)
        assertEquals(LocalDate.of(2026, 10, 25), session.night)
        assertEquals(3, session.remainingCues)
        assertEquals(date, upcomingPracticeNight(at(date, 4)))
        assertEquals(date.plusDays(1), upcomingPracticeNight(at(date, 10)))
    }

    @Test fun completingTheSameGuideTwiceDoesNotDuplicateHistory() {
        val event = PracticeEvent(id = "same-run", at = 123, night = date, method = PracticeMethod.RealityCheck)
        val once = LucidPracticeData().record(event)
        val twice = once.record(event)
        assertEquals(once, twice)
        assertEquals(1, twice.night(date).checks)
        assertEquals(setOf(PracticeMethod.RealityCheck), twice.night(date).methods)
    }

    @Test fun trimmingRecentEventsKeepsTheNightlyTotals() {
        var data = LucidPracticeData()
        repeat(1002) { i -> data = data.record(PracticeEvent(id = "run-$i", at = i.toLong(), night = date, method = PracticeMethod.RealityCheck)) }
        assertEquals(1000, data.events.size)
        assertEquals(1002, data.night(date).checks)
    }

    @Test fun unrememberedNightsStayUnknownAndDiaryLucidityIsIncluded() {
        val night = PracticeNight(date, recall = DreamRecall.NoRecall, lucid = false)
        assertNull(nightLucidity(night, emptyList()))
        assertEquals(true, nightLucidity(night, listOf(DreamEntry(date = date, body = "I noticed the impossible sky", lucid = true))))
        assertNull(nightLucidity(PracticeNight(date), listOf(DreamEntry(date = date, body = "A train", lucid = null))))
        assertEquals(false, nightLucidity(PracticeNight(date), listOf(DreamEntry(date = date, body = "A train", lucid = false))))
    }

    @Test fun comparisonsExcludeFutureNightsAndUnansweredOutcomes() {
        val data = LucidPracticeData(nights = listOf(
            PracticeNight(date, methods = setOf(PracticeMethod.MILD), recall = DreamRecall.Remembered, lucid = true),
            PracticeNight(date.minusDays(1), methods = setOf(PracticeMethod.MILD), recall = DreamRecall.NoRecall),
            PracticeNight(date.plusDays(1), methods = setOf(PracticeMethod.MILD), lucid = true)))
        assertEquals(listOf(PracticeComparison(PracticeMethod.MILD, 2, 1, 1)), practiceComparisons(data, emptyList(), date))
    }

    @Test fun cueTrainingBelongsToTheSameSoundAndExpiresAfterADay() {
        val now = 1_000_000_000L
        val data = LucidPracticeData().record(PracticeEvent(at = now - 1000, night = date, method = PracticeMethod.SoundTraining, detail = LucidCue.Bell.name))
        assertTrue(trainedForCue(data, LucidCue.Bell, now))
        assertFalse(trainedForCue(data, LucidCue.Taps, now))
        assertFalse(trainedForCue(data, LucidCue.Bell, now + 24 * 60 * 60_000L))
    }

    @Test fun allNotesSchedulesAndAnArmedSessionRoundTrip() {
        val s = LucidSettings(realityEnabled = true, bedtimeEnabled = true, signs = listOf("train", "old house"), wakeEnabled = true,
            cuesEnabled = true, cue = LucidCue.Taps, cueVolume = .25f)
        val data = LucidPracticeData(settings = s, nights = listOf(PracticeNight(date, intention = "Notice the house", goal = "Look at the sky",
            methods = setOf(PracticeMethod.MILD, PracticeMethod.SSILD), recall = DreamRecall.Remembered, lucid = true,
            realization = "The staircase moved", sleepQuality = 4, sleepNote = "Returned to sleep easily")),
            session = makeNightSession(at(date.minusDays(1), 23), s), snoozeUntil = 999, pendingRealityAt = 1234, pendingBedtimeAt = 5678, notice = "A note")
            .record(PracticeEvent(id = "check", at = 123, night = date, method = PracticeMethod.RealityCheck, detail = "train"))
        assertEquals(data, LucidPracticeCodec.decode(LucidPracticeCodec.encode(data)))
    }

    @Test fun invalidOrFuturePracticeFilesAreRejected() {
        val json = JSONObject(LucidPracticeCodec.encode(LucidPracticeData()))
        json.put("version", 2)
        assertThrows(IllegalArgumentException::class.java) { LucidPracticeCodec.decode(json.toString()) }
        json.put("version", 1).getJSONObject("settings").put("count", 0)
        assertThrows(IllegalArgumentException::class.java) { LucidPracticeCodec.decode(json.toString()) }
        assertFalse(validLucidSettings(LucidSettings(startMinute = 700, endMinute = 710, checksPerDay = 3)))
        assertFalse(validLucidSettings(LucidSettings(days = emptySet())))
        assertThrows(IllegalArgumentException::class.java) { makeNightSession(at(date, 23), LucidSettings()) }
        assertThrows(IllegalArgumentException::class.java) { makeNightSession(at(date, 23), LucidSettings(wakeEnabled = true, cuesEnabled = true, wakeAfterHours = 6, cuesAfterHours = 6)) }
    }
}
