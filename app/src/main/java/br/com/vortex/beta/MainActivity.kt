package br.com.vortex.beta

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File

class MainActivity : AppCompatActivity() {
    data class SystemInfo(val name: String, val core: String, val extensions: String)
    private val systems = listOf(
        SystemInfo("Atari 2600", "libstella2014_libretro_android.so", "a26|bin|rom"),
        SystemInfo("Nintendo Entertainment System", "libfceumm_libretro_android.so", "nes|unf|unif|fds"),
        SystemInfo("Super Nintendo", "libsnes9x_libretro_android.so", "smc|sfc|swc|fig"),
        SystemInfo("Game Boy", "libgambatte_libretro_android.so", "gb|gbc"),
        SystemInfo("Game Boy Advance", "libmednafen_gba_libretro_android.so", "gba"),
        SystemInfo("Sega Genesis / Mega Drive", "libgenesis_plus_gx_libretro_android.so", "bin|gen|md|smd"),
        SystemInfo("PC Engine / TurboGrafx-16", "libmednafen_pce_fast_libretro_android.so", "pce|cue|ccd"),
        SystemInfo("Atari Lynx", "libmednafen_lynx_libretro_android.so", "lnx"),
        SystemInfo("Neo Geo Pocket", "libmednafen_ngp_libretro_android.so", "ngp|ngc"),
        SystemInfo("WonderSwan", "libmednafen_wswan_libretro_android.so", "ws|wsc|pc2")
    )
    private val neon = Color.rgb(53, 255, 20)
    private val bg = Color.rgb(5, 10, 8)
    private val picker = 431
    private var selected: SystemInfo? = null
    private var retro: GLRetroView? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showHome() }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(14, 10, 14, 10)
    }

    private fun styledButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(neon); setBackgroundColor(Color.rgb(15, 28, 19)); textSize = 16f
        setOnClickListener { onClick() }
    }

    private fun showHome() {
        retro = null
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 20) }
        column.addView(label("VORTEX", 32f, neon, true))
        column.addView(label("BETA CLÁSSICA  •  Android 6+  •  ARM64", 14f, Color.LTGRAY))
        column.addView(label("10 sistemas clássicos · escolha um e abra sua ROM hack", 17f, Color.WHITE))
        systems.forEach { system ->
            val b = styledButton("▶  ${system.name}") { selected = system; chooseRom() }
            column.addView(b, LinearLayout.LayoutParams(-1, 56).apply { setMargins(4, 5, 4, 5) })
        }
        column.addView(label("Não inclui jogos ou BIOS. Abra somente arquivos de jogos que você tem direito de usar. ROM hacks devem ser arquivos já aplicados pelo próprio usuário.", 12f, Color.LTGRAY))
        scroll.addView(column); setContentView(scroll)
    }

    private fun chooseRom() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        startActivityForResult(intent, picker)
    }

    @Deprecated("legacy picker callback for Android 6+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != picker || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val system = selected ?: return
        try {
            val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
            val allowed = system.extensions.split('|')
            if (ext !in allowed && ext != "zip" && ext != "7z") {
                Toast.makeText(this, "Formato não reconhecido para ${system.name}; vou tentar abrir mesmo assim.", Toast.LENGTH_LONG).show()
            }
            val file = copyToPrivateCache(uri)
            launchGame(system, file)
        } catch (e: Exception) { Toast.makeText(this, "Não consegui ler o arquivo: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun copyToPrivateCache(uri: Uri): File {
        val name = (uri.lastPathSegment ?: "rom.bin").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = name.substringAfterLast('.', "bin").take(8)
        val target = File(cacheDir, "selected_rom.$ext")
        contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Arquivo inacessível" }.copyTo(target.outputStream()) }
        require(target.length() > 0) { "Arquivo vazio" }
        return target
    }

    private fun launchGame(system: SystemInfo, rom: File) {
        selected = system
        val data = GLRetroViewData(this).apply {
            coreFilePath = system.core
            gameFilePath = rom.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = File(filesDir, "saves").apply { mkdirs() }.absolutePath
            shader = ShaderConfig.Default
            preferLowLatencyAudio = true
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view = GLRetroView(this, data)
        retro = view
        lifecycle.addObserver(view)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val menu = TextView(this).apply {
            text = "‹ BIBLIOTECA"; textSize = 12f; setTextColor(neon); setBackgroundColor(0x99000000.toInt()); setPadding(16, 10, 16, 10)
            setOnClickListener { view.onPause(); lifecycle.removeObserver(view); retro = null; showHome() }
        }
        root.addView(menu, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val pad = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; alpha = .78f }
        listOf("◀" to KeyEvent.KEYCODE_DPAD_LEFT, "▲" to KeyEvent.KEYCODE_DPAD_UP, "▼" to KeyEvent.KEYCODE_DPAD_DOWN, "▶" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "B" to KeyEvent.KEYCODE_BUTTON_B, "A" to KeyEvent.KEYCODE_BUTTON_A, "SELECT" to KeyEvent.KEYCODE_BUTTON_SELECT, "START" to KeyEvent.KEYCODE_BUTTON_START).forEach { (caption, key) ->
            val button = TextView(this).apply {
                text = caption; textSize = if (caption.length > 1) 11f else 18f; gravity = Gravity.CENTER; setTextColor(neon)
                setBackgroundColor(0x99202A20.toInt()); setPadding(10, 16, 10, 16)
                setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_UP) {
                        view.sendKeyEvent(if (event.action == android.view.MotionEvent.ACTION_DOWN) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key); true
                    } else true
                }
            }
            pad.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3, 0, 3, 0) })
        }
        root.addView(pad, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    override fun onPause() { retro?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); retro?.onResume() }
    override fun onBackPressed() { if (retro != null) { retro?.onPause(); retro?.let { lifecycle.removeObserver(it) }; retro = null; showHome() } else super.onBackPressed() }
}
