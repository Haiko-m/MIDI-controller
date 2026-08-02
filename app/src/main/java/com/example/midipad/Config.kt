package com.example.midipad

/**
 * Everything you are likely to want to change lives here.
 *
 * Channels are 1..16. Notes are 0..127; 36 is C1, the note Live's drum racks
 * start on, so the four buttons land on the bottom-left corner of a rack.
 */
object Config {

    /** Horizontal axis of the pad. */
    val X_TARGET: MidiTarget = MidiTarget.PitchBend(channel = 1)

    /** Vertical axis of the pad, bottom to top. */
    val Y_TARGET: MidiTarget = MidiTarget.PitchBend(channel = 2)

    /**
     * Swap the two lines above for these if you would rather have plain 7-bit
     * CCs, e.g. to hit a device that only listens to CC:
     *
     *   val X_TARGET: MidiTarget = MidiTarget.Cc7(channel = 1, controller = 20)
     *   val Y_TARGET: MidiTarget = MidiTarget.Cc7(channel = 1, controller = 21)
     */

    const val BUTTON_CHANNEL = 1

    data class ButtonSpec(val label: String, val note: Int, val velocity: Int = 100)

    val BUTTONS = listOf(
        ButtonSpec("1", 36),
        ButtonSpec("2", 37),
        ButtonSpec("3", 38),
        ButtonSpec("4", 39)
    )
}
