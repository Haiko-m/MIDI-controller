package com.example.midipad

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.midipad.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var midi: MidiEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nothing kills a set faster than the screen locking mid-sweep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        midi = MidiEngine(this).apply {
            xTarget = Config.X_TARGET
            yTarget = Config.Y_TARGET
        }

        binding.pad.listener = XYPadView.OnValueChangeListener { x, y -> midi.setPad(x, y) }
        binding.pad.setValueSilently(0.5f, 0.5f)

        buildButtons()

        binding.status.setOnClickListener { showDestinationPicker() }
        binding.status.setOnLongClickListener {
            midi.panic()
            binding.status.text = "Panic sent"
            true
        }

        midi.listener = MidiEngine.Listener { state, name -> renderStatus(state, name) }
    }

    override fun onStart() {
        super.onStart()
        midi.start()
    }

    override fun onStop() {
        super.onStop()
        // Release anything still held, then hand the port back to the system.
        midi.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        midi.release()
    }

    private fun buildButtons() {
        val row: LinearLayout = binding.buttonRow
        val gap = (8 * resources.displayMetrics.density).toInt()
        Config.BUTTONS.forEachIndexed { index, spec ->
            val button = PadButton(this).apply {
                label = spec.label
                onPress = { midi.noteOn(spec.note, spec.velocity, Config.BUTTON_CHANNEL) }
                onRelease = { midi.noteOff(spec.note, Config.BUTTON_CHANNEL) }
            }
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) params.marginStart = gap
            row.addView(button, params)
        }
    }

    private fun showDestinationPicker() {
        val destinations = midi.destinations()
        if (destinations.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.picker_title)
                .setMessage(R.string.status_no_destination)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val names = destinations.map { midi.displayName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.picker_title)
            .setItems(names) { _, which -> midi.connect(destinations[which]) }
            .show()
    }

    private fun renderStatus(state: MidiEngine.State, name: String?) {
        val text = when (state) {
            MidiEngine.State.SEARCHING -> getString(R.string.status_no_destination)
            MidiEngine.State.CONNECTING -> getString(R.string.status_connecting, name.orEmpty())
            MidiEngine.State.CONNECTED -> {
                val routing = "X ${Config.X_TARGET.label()}   Y ${Config.Y_TARGET.label()}"
                getString(R.string.status_connected, name.orEmpty()) + "\n" + routing
            }
            MidiEngine.State.FAILED -> getString(R.string.status_failed, name.orEmpty())
            MidiEngine.State.LOST -> getString(R.string.status_lost)
        }
        binding.status.text = text
        binding.status.visibility = View.VISIBLE
    }
}
