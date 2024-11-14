package com.yd.signai

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.reflect.KFunction3

class GestureAdapter(
    private var gesture: Gesture,
    private val languages: Map<String, String>,
    private val onTranslate: (String, String) -> Unit,
    private val onPlayVoice: (String) -> Unit,
    private val onReset: () -> Unit // Callback untuk reset
) : RecyclerView.Adapter<GestureAdapter.GestureViewHolder>() {

    // Menyimpan pilihan bahasa yang terakhir dipilih
    private var selectedLangKey: String = languages.keys.first()

    inner class GestureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val gestureResultText: TextView = itemView.findViewById(R.id.gestureResultText)
        val gestureResultTextTranslate: TextView = itemView.findViewById(R.id.gestureResultTextTranslate)
        val targetLangSpinner: Spinner = itemView.findViewById(R.id.targetLangSpinner)
        val translateButton: Button = itemView.findViewById(R.id.translateButton)
        val playVoiceButton: Button = itemView.findViewById(R.id.playVoiceButton)
        val resetButton: Button = itemView.findViewById(R.id.resetButton)

        init {
            // Setup spinner untuk pemilihan bahasa
            val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, languages.keys.toList())
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            targetLangSpinner.adapter = adapter

            // Set pilihan default spinner
            targetLangSpinner.setSelection(languages.keys.indexOf(selectedLangKey))

            translateButton.setOnClickListener {
                val selectedLangKey = targetLangSpinner.selectedItem.toString()
                val selectedLangCode = languages[selectedLangKey]

                if (selectedLangCode != null) {
                    onTranslate(gesture.detectedGesture, selectedLangCode)
                    this@GestureAdapter.selectedLangKey = selectedLangKey // Simpan bahasa yang dipilih
                } else {
                    Log.e("GestureAdapter", "Bahasa yang dipilih tidak ditemukan di map")
                }
            }

            playVoiceButton.setOnClickListener {
                onPlayVoice(gesture.translatedText)
            }

            // Set listener untuk tombol reset
            resetButton.setOnClickListener {
                resetGesture() // Memanggil fungsi reset
                onReset()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GestureViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.gesture_item, parent, false)
        return GestureViewHolder(view)
    }

    override fun onBindViewHolder(holder: GestureViewHolder, position: Int) {
        holder.gestureResultText.text = gesture.detectedGesture
        holder.gestureResultTextTranslate.text = gesture.translatedText

        // Set spinner ke pilihan sebelumnya
        holder.targetLangSpinner.setSelection(languages.keys.indexOf(selectedLangKey))
    }

    override fun getItemCount(): Int = 1 // Hanya satu item

    fun updateGesture(newGesture: String, newTranslation: String) {
        gesture = gesture.copy(detectedGesture = newGesture, translatedText = newTranslation)
        notifyItemChanged(0) // Notifikasi perubahan
    }

    // Fungsi untuk mereset gesture dan panggil callback
    private fun resetGesture() {
        gesture = gesture.copy(detectedGesture = "No Gesture Detected", translatedText = "--")
        onReset() // Panggil callback untuk reset
        notifyItemChanged(0) // Notifikasi perubahan
    }
}

// Data class untuk menyimpan gesture dan terjemahan
data class Gesture(var detectedGesture: String = "No Gesture Detected", var translatedText: String = "--")




