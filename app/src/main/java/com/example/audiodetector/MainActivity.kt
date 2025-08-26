package com.example.audiodetector
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tvTranscript: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val requestAudioPermission = registerForActivityResult(
        //permissão para a entrada de audio
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captura_microfone() else {
            Toast.makeText(this, "Permissão de microfone negada.", Toast.LENGTH_LONG).show()
            updateStatus("Permissão negada")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTranscript = findViewById(R.id.tvTranscript)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { permissão() }
        btnStop.setOnClickListener { voz_em_andamento() }

        // cria o recognizer (serve para transformar fala em texto) uma vez
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(listener)
        } else {
            Toast.makeText(this, "Reconhecimento de voz indisponível.", Toast.LENGTH_LONG).show()
            updateStatus("Reconhecimento indisponível")
        }
    }

    private fun permissão() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            captura_microfone()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun captura_microfone() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // indica ao sistema que queremos ditado contínuo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }
        tvTranscript.text = tvTranscript.text // mantém o que já tem
        updateStatus("Ouvindo… fale próximo ao microfone")
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao iniciar: ${e.message}", Toast.LENGTH_SHORT).show()
            updateStatus("Erro ao iniciar")
        }
    }

    private fun voz_em_andamento() {
        // atualiza a interface para indicar que parou.
        try {
            speechRecognizer?.stopListening()
            updateStatus("Parado")
        } catch (_: Exception) {
            updateStatus("Parado")
        }
    }

    private val listener = object : RecognitionListener {

        // objeto que recebe eventos do reconhecimento de voz
        // pronto para ouvir
        override fun onReadyForSpeech(params: Bundle?) {
            updateStatus("Pronto para ouvir…")
        }

        // detectou início da fala
        override fun onBeginningOfSpeech() {
            updateStatus("Captando fala…")
        }

        // volume da voz
        override fun onRmsChanged(rmsdB: Float) {
        }

        // buffer de áudio bruto recebido
        override fun onBufferReceived(buffer: ByteArray?) {}

        // terminou de falar
        override fun onEndOfSpeech() {
            updateStatus("Processando…")
        }

        // capturou um erro e exibe a mensagem
        override fun onError(error: Int) {
            updateStatus(
                "Erro: " + when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Falha de áudio"
                    SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão insuficiente"
                    SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo de rede esgotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Sem correspondência"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Erro no servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Sem fala detectada"
                    else -> "Desconhecido"
                }
            )
        }

        // resultado final do texto reconhecido
        override fun onResults(results: Bundle) {
            val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val finalText = texts?.firstOrNull().orEmpty()
            if (finalText.isNotBlank()) {
                appendTranscript(finalText)
            }
            updateStatus("Toque em Iniciar para continuar")
        }

        // resultados parciais enquanto o usuário fala
        override fun onPartialResults(partialResults: Bundle) {
            val partial = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()

            if (partial.isNotBlank()) {
                // Mostra parcial em itálico (só visual, não fixa no texto final)
                tvStatus.text = "Parcial: $partial"
            }
        }

        // eventos extras
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // adiciona texto ao tvTranscript sem apagar o que já estava
    private fun appendTranscript(text: String) {
        val current = tvTranscript.text?.toString().orEmpty()
        val sep = if (current.isBlank()) "" else "\n\n"
        tvTranscript.text = current + sep + text
    }

    private fun updateStatus(s: String) {
        tvStatus.text = s
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}