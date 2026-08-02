package com.example.midipad

/**
 * Where a continuous 0..16383 value gets sent.
 *
 * Pitch bend is the default because it is the only message Ableton Live maps
 * with full 14-bit resolution: 16384 steps instead of the 128 you get from a
 * plain CC, which is the difference between a smooth filter sweep and a
 * staircase you can hear.
 */
sealed class MidiTarget {

    /** Writes the message(s) into [out] and returns the number of bytes used. */
    abstract fun encode(value14: Int, out: ByteArray): Int

    /** Human readable name, shown in the status line. */
    abstract fun label(): String

    /** 14-bit, one message. Channel is 1..16. */
    data class PitchBend(val channel: Int) : MidiTarget() {
        override fun encode(value14: Int, out: ByteArray): Int {
            out[0] = (0xE0 or (channel - 1)).toByte()
            out[1] = (value14 and 0x7F).toByte()
            out[2] = ((value14 shr 7) and 0x7F).toByte()
            return 3
        }

        override fun label() = "bend ch$channel"
    }

    /** 7-bit control change. 128 steps. Channel is 1..16, controller 0..127. */
    data class Cc7(val channel: Int, val controller: Int) : MidiTarget() {
        override fun encode(value14: Int, out: ByteArray): Int {
            out[0] = (0xB0 or (channel - 1)).toByte()
            out[1] = controller.toByte()
            out[2] = ((value14 shr 7) and 0x7F).toByte()
            return 3
        }

        override fun label() = "cc$controller ch$channel"
    }

    /**
     * 14-bit control change: MSB on [msbController], LSB on msbController + 32.
     * Standard MIDI, but Live ignores the LSB half, so this only helps with
     * hosts that pair them. Use [PitchBend] for Live.
     */
    data class Cc14(val channel: Int, val msbController: Int) : MidiTarget() {
        override fun encode(value14: Int, out: ByteArray): Int {
            val status = (0xB0 or (channel - 1)).toByte()
            out[0] = status
            out[1] = msbController.toByte()
            out[2] = ((value14 shr 7) and 0x7F).toByte()
            out[3] = status
            out[4] = (msbController + 32).toByte()
            out[5] = (value14 and 0x7F).toByte()
            return 6
        }

        override fun label() = "cc$msbController+${msbController + 32} ch$channel"
    }
}
