package br.com.auron

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import br.com.auron.ui.AuronApp
import br.com.auron.ui.AuronViewModel
import br.com.auron.voice.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: AuronViewModel by viewModels()
    private lateinit var voice: VoiceController
    private var exportProfileId: String? = null
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val expectedProfile = exportProfileId
        exportProfileId = null
        if (uri != null && expectedProfile != null) lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = viewModel.exportJson(expectedProfile)
                    check(viewModel.state.value.profileId == expectedProfile)
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: error("Destino indisponível")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                Toast.makeText(this@MainActivity, "Dados exportados para o local escolhido.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@MainActivity, "Falha na exportação. Confira o destino e tente novamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportProfileId = savedInstanceState?.getString("export_profile")
        enableEdgeToEdge()
        voice = VoiceController(this) { transcript -> viewModel.send(transcript, "voice") }
        setContent {
            AuronApp(viewModel, voice) {
                exportProfileId = viewModel.state.value.profileId
                exportDocument.launch("auron-${LocalDate.now()}.json")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        exportProfileId?.let { outState.putString("export_profile", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        if (::voice.isInitialized) voice.pause()
        super.onStop()
    }

    override fun onDestroy() {
        if (::voice.isInitialized) voice.close()
        super.onDestroy()
    }
}
