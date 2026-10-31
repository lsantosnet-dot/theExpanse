package com.theexpanse.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.theexpanse.game.Host
import com.theexpanse.game.Sfx
import com.theexpanse.render.AppController

/** Hospeda a tela do jogo e faz o que só o Android sabe: escolher arquivos, compartilhar e copiar texto. */
class MainActivity : ComponentActivity(), Host {
    private lateinit var controller: AppController
    private var pendingExport: String? = null
    private val sounds = SoundPlayer()

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importUri(it) } }

    private val creator = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast("Template exportado.")
            } catch (e: Exception) {
                toast("Não consegui gravar o arquivo.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = AppController(FileStorage(filesDir), this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!controller.onBack()) finish()
            }
        })
        setContent { GameScreen(controller) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        sounds.engine(false)
        controller.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { importUri(it) }
    }

    private fun importUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
            var name = "pacote.json"
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i)
            }
            controller.importText(name, text)
        } catch (e: Exception) {
            controller.importText("pacote.json", "")
        }
    }

    // ---- Host ----

    override fun importPackage() {
        picker.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    override fun exportFile(fileName: String, text: String) {
        pendingExport = text
        creator.launch(fileName)
    }

    override fun play(sfx: Sfx) = sounds.play(sfx)

    override fun engine(on: Boolean) = sounds.engine(on)

    override fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("erros", text))
        toast("Erros copiados.")
    }

    override fun promptText(title: String, initial: String, onResult: (String?) -> Unit) {
        runOnUiThread {
            val input = EditText(this).apply {
                setText(initial)
                setSelection(initial.length)
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK") { _, _ -> onResult(input.text.toString()) }
                .setNegativeButton("Cancelar") { _, _ -> onResult(null) }
                .setOnCancelListener { onResult(null) }
                .show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
