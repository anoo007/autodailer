package com.dostt.autodialer

import org.junit.Assert.assertEquals
import org.junit.Test

class DialerStateMachineTest {

    @Test
    fun classifiesObservedDosttScreensCorrectly() {
        assertEquals(
            DosttScreen.HOME_COLLAPSED,
            DosttScreenDetector.classifyScreen(listOf("Find me the right dostt", "Home", "Recents", "Profile"))
        )
        assertEquals(
            DosttScreen.HOME_EXPANDED,
            DosttScreenDetector.classifyScreen(listOf("Audio", "1/min", "Video", "6/min", "Home", "Recents", "Profile"))
        )
        assertEquals(
            DosttScreen.MATCHING,
            DosttScreenDetector.classifyScreen(listOf("Finding a dostt for you...", "Please wait while we find you someone who is available", "Cancel"))
        )
        assertEquals(
            DosttScreen.UNAVAILABLE,
            DosttScreenDetector.classifyScreen(listOf("Dostts unavailable", "All dostts are currently unavailable. Please try again in some time", "Go back home"))
        )
        assertEquals(
            DosttScreen.CALL_CONNECTED,
            DosttScreenDetector.classifyScreen(listOf("Mute", "Speaker", "00:04"))
        )
        assertEquals(
            DosttScreen.CALL_CONNECTED,
            DosttScreenDetector.classifyScreen(listOf("Home"), isAudioCallActive = true)
        )
    }

    @Test
    fun retriesUnavailableLoopAndStopsImmediatelyWhenCallConnects() {
        val session = DialerSessionState(maxAttempts = 3)

        assertEquals(DialerAction.TAP_AUDIO_COLLAPSED, session.onScreenObserved(DosttScreen.HOME_COLLAPSED))
        assertEquals(1, session.attempts)

        assertEquals(DialerAction.NONE, session.onScreenObserved(DosttScreen.MATCHING))
        assertEquals(DialerAction.TAP_GO_BACK_HOME, session.onScreenObserved(DosttScreen.UNAVAILABLE))

        assertEquals(DialerAction.TAP_AUDIO_EXPANDED, session.onScreenObserved(DosttScreen.HOME_EXPANDED))
        assertEquals(2, session.attempts)

        // Stops immediately on explicit call screen or audio mode active
        assertEquals(DialerAction.STOP_CALL_CONNECTED, session.onScreenObserved(DosttScreen.CALL_CONNECTED))
    }

    @Test
    fun stopsWhenMaxAttemptsReached() {
        val session = DialerSessionState(maxAttempts = 1)
        assertEquals(DialerAction.TAP_AUDIO_EXPANDED, session.onScreenObserved(DosttScreen.HOME_EXPANDED))
        assertEquals(DialerAction.STOP_MAX_ATTEMPTS_REACHED, session.onScreenObserved(DosttScreen.HOME_EXPANDED))
    }
}
