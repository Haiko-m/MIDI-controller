package com.example.midipad

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.roundToInt

/**
 * Owns the MIDI output. Everything that touches the port runs on a single
 * background thread so the UI thread never blocks on a USB write.
 *
 * Continuous values are coalesced: touch events arrive at 120-240 Hz, but only
 * the most recent value per axis is sent, at [FRAME_MS] intervals, and only
 * when it actually changed. Notes bypass the coalescer and go out immediately.
 */
class MidiEngine(context: Context) {

    enum class State { SEARCHING, CONNECTING, CONNECTED, FAILED, LOST }

    fun interface Listener {
        fun onStateChanged(state: State, deviceName: String?)
    }

    private val appContext = context.applicationContext
    private val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ioThread = HandlerThread("midi-out", Process.THREAD_PRIORITY_URGENT_AUDIO)
        .apply { start() }
    private val io = Handler(ioThread.looper)

    @Volatile
    private var device: MidiDevice? = null

    @Volatile
    private var port: MidiInputPort? = null

    /** Only touched on the io thread. */
    private val scratch = ByteArray(6)

    private val axisValues = AtomicIntegerArray(intArrayOf(UNSET, UNSET))
    private val axisSent = intArrayOf(UNSET, UNSET)

    var xTarget: MidiTarget = MidiTarget.PitchBend(1)
    var yTarget: MidiTarget = MidiTarget.PitchBend(2)

    var listener: Listener? = null
        set(value) {
            field = value
            value?.onStateChanged(state, deviceName)
        }

    var state: State = State.SEARCHING
        private set
    var deviceName: String? = null
        private set

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            if (port == null) autoConnect()
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == device?.info?.id) {
                closePort()
                setState(State.LOST, deviceName)
                autoConnect()
            }
        }
    }

    fun start() {
        registerCallback()
        io.post(ticker)
        autoConnect()
    }

    /** Gives the port back while the app is in the background. Reversible: call [start] again. */
    fun stop() {
        unregisterCallback()
        io.removeCallbacks(ticker)
        panic()
        io.post { closePort() }
        setState(State.SEARCHING, null)
    }

    /** One-way teardown. Nothing works after this. */
    fun release() {
        ioThread.quitSafely()
    }

    // --- destinations -------------------------------------------------------

    /** Every MIDI device we can write to, newest platform API where available. */
    fun destinations(): List<MidiDeviceInfo> {
        val all = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            midiManager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            midiManager.devices.toList()
        }
        return all.filter { it.inputPortCount > 0 }
    }

    /**
     * Picks the USB link to the host if there is one. When the phone is plugged
     * into a computer and USB mode is set to MIDI, that shows up here as
     * "Android USB Peripheral".
     */
    fun autoConnect() {
        val candidates = destinations()
        if (candidates.isEmpty()) {
            setState(State.SEARCHING, null)
            return
        }
        val best = candidates.firstOrNull { it.type == MidiDeviceInfo.TYPE_USB }
            ?: candidates.first()
        connect(best)
    }

    fun connect(info: MidiDeviceInfo) {
        val name = displayName(info)
        setState(State.CONNECTING, name)
        midiManager.openDevice(info, { opened ->
            if (opened == null) {
                setState(State.FAILED, name)
                return@openDevice
            }
            io.post {
                closePort()
                val newPort = try {
                    opened.openInputPort(0)
                } catch (e: Exception) {
                    null
                }
                if (newPort == null) {
                    try { opened.close() } catch (_: IOException) { }
                    setState(State.FAILED, name)
                } else {
                    device = opened
                    port = newPort
                    axisSent[X] = UNSET
                    axisSent[Y] = UNSET
                    setState(State.CONNECTED, name)
                }
            }
        }, mainHandler)
    }

    fun displayName(info: MidiDeviceInfo): String {
        val props = info.properties
        props.getString(MidiDeviceInfo.PROPERTY_NAME)?.let { return it }
        val manufacturer = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER).orEmpty()
        val product = props.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
        val combined = "$manufacturer $product".trim()
        return combined.ifEmpty { "MIDI device ${info.id}" }
    }

    // --- sending ------------------------------------------------------------

    /** [x] and [y] are 0..1, bottom-left origin. Cheap enough to call per touch event. */
    fun setPad(x: Float, y: Float) {
        axisValues.set(X, to14Bit(x))
        axisValues.set(Y, to14Bit(y))
    }

    fun noteOn(note: Int, velocity: Int = 100, channel: Int = 1) = io.post {
        val p = port ?: return@post
        scratch[0] = (0x90 or (channel - 1)).toByte()
        scratch[1] = note.toByte()
        scratch[2] = velocity.toByte()
        write(p, 3)
    }

    fun noteOff(note: Int, channel: Int = 1) = io.post {
        val p = port ?: return@post
        scratch[0] = (0x80 or (channel - 1)).toByte()
        scratch[1] = note.toByte()
        scratch[2] = 0
        write(p, 3)
    }

    /** All notes off plus bend back to centre, on every channel. */
    fun panic() = io.post {
        val p = port ?: return@post
        for (channel in 0..15) {
            scratch[0] = (0xB0 or channel).toByte()
            scratch[1] = 123
            scratch[2] = 0
            if (!write(p, 3)) return@post
            scratch[0] = (0xE0 or channel).toByte()
            scratch[1] = 0
            scratch[2] = 64
            if (!write(p, 3)) return@post
        }
        axisSent[X] = UNSET
        axisSent[Y] = UNSET
    }

    private val ticker = object : Runnable {
        override fun run() {
            flush()
            io.postDelayed(this, FRAME_MS)
        }
    }

    private fun flush() {
        val p = port ?: return
        for (axis in 0..1) {
            val value = axisValues.get(axis)
            if (value == UNSET || value == axisSent[axis]) continue
            val target = if (axis == X) xTarget else yTarget
            val length = target.encode(value, scratch)
            if (!write(p, length)) return
            axisSent[axis] = value
        }
    }

    /** Returns false if the port died, in which case it has already been torn down. */
    private fun write(p: MidiInputPort, length: Int): Boolean = try {
        p.send(scratch, 0, length)
        true
    } catch (e: IOException) {
        closePort()
        setState(State.LOST, deviceName)
        false
    }

    private fun closePort() {
        try { port?.close() } catch (_: IOException) { }
        try { device?.close() } catch (_: IOException) { }
        port = null
        device = null
    }

    // --- plumbing -----------------------------------------------------------

    private fun registerCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val executor = Executor { command -> mainHandler.post(command) }
            midiManager.registerDeviceCallback(
                MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                executor,
                deviceCallback
            )
        } else {
            @Suppress("DEPRECATION")
            midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        }
    }

    private fun unregisterCallback() {
        midiManager.unregisterDeviceCallback(deviceCallback)
    }

    private fun setState(newState: State, name: String?) {
        mainHandler.post {
            state = newState
            deviceName = name
            listener?.onStateChanged(newState, name)
        }
    }

    private fun to14Bit(normalized: Float): Int =
        (normalized.coerceIn(0f, 1f) * MAX_14BIT).roundToInt()

    companion object {
        const val X = 0
        const val Y = 1
        private const val UNSET = -1
        private const val MAX_14BIT = 16383f

        /** 5 ms between frames: 200 Hz, far smoother than any ear needs. */
        private const val FRAME_MS = 5L
    }
}
