package com.dostt.autodialer

/**
 * Pure Kotlin screen detector and retry state machine for live.friend.dostt.
 */
enum class DosttScreen {
    HOME_COLLAPSED,
    HOME_EXPANDED,
    MATCHING,
    UNAVAILABLE,
    CALL_CONNECTED,
    CALL_OR_OTHER
}

enum class DialerAction {
    NONE,
    TAP_AUDIO_COLLAPSED,
    TAP_AUDIO_EXPANDED,
    TAP_GO_BACK_HOME,
    STOP_CALL_CONNECTED,
    STOP_MAX_ATTEMPTS_REACHED
}

object DosttScreenDetector {

    private val CALL_TIMER_REGEX = Regex("""\b\d{2}:\d{2}\b""")

    fun classifyScreen(
        visibleTexts: Collection<String>,
        isAudioCallActive: Boolean = false
    ): DosttScreen {
        if (isAudioCallActive) {
            return DosttScreen.CALL_CONNECTED
        }

        val normalized = visibleTexts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) {
            return DosttScreen.CALL_OR_OTHER
        }

        val hasUnavailable = normalized.any {
            it.contains("dostts unavailable") ||
                it.contains("all dostts are currently unavailable") ||
                it == "go back home"
        }
        if (hasUnavailable) {
            return DosttScreen.UNAVAILABLE
        }

        val hasMatching = normalized.any {
            it.contains("finding a dostt for you") ||
                it.contains("please wait while we find you someone")
        }
        if (hasMatching) {
            return DosttScreen.MATCHING
        }

        // Explicit in-call indicators (Mute / Speaker / End Call / active call timer)
        val hasCallControls = normalized.any {
            it == "mute" ||
                it == "unmute" ||
                it == "speaker" ||
                it == "end call" ||
                it.contains("call connected") ||
                CALL_TIMER_REGEX.containsMatchIn(it)
        }
        if (hasCallControls) {
            return DosttScreen.CALL_CONNECTED
        }

        val hasCollapsedBar = normalized.any { it.contains("find me the right dostt") }
        if (hasCollapsedBar) {
            return DosttScreen.HOME_COLLAPSED
        }

        val hasHomeTabs = normalized.any { it == "recents" } && normalized.any { it == "profile" }
        val hasExpandedAudio = normalized.any { it == "audio" || it.contains("1/min") }
        if (hasHomeTabs || hasExpandedAudio) {
            return DosttScreen.HOME_EXPANDED
        }

        return DosttScreen.CALL_OR_OTHER
    }
}

class DialerSessionState(private val maxAttempts: Int) {

    var attempts: Int = 0
        private set

    var sawMatchingScreen: Boolean = false
        private set

    fun reset() {
        attempts = 0
        sawMatchingScreen = false
    }

    fun onScreenObserved(screen: DosttScreen): DialerAction {
        return when (screen) {
            DosttScreen.CALL_CONNECTED -> {
                DialerAction.STOP_CALL_CONNECTED
            }

            DosttScreen.MATCHING -> {
                sawMatchingScreen = true
                DialerAction.NONE
            }

            DosttScreen.UNAVAILABLE -> {
                sawMatchingScreen = false
                DialerAction.TAP_GO_BACK_HOME
            }

            DosttScreen.HOME_COLLAPSED -> {
                if (attempts >= maxAttempts) {
                    DialerAction.STOP_MAX_ATTEMPTS_REACHED
                } else {
                    attempts += 1
                    DialerAction.TAP_AUDIO_COLLAPSED
                }
            }

            DosttScreen.HOME_EXPANDED -> {
                if (attempts >= maxAttempts) {
                    DialerAction.STOP_MAX_ATTEMPTS_REACHED
                } else {
                    attempts += 1
                    DialerAction.TAP_AUDIO_EXPANDED
                }
            }

            DosttScreen.CALL_OR_OTHER -> {
                if (sawMatchingScreen) {
                    DialerAction.STOP_CALL_CONNECTED
                } else {
                    DialerAction.NONE
                }
            }
        }
    }
}
