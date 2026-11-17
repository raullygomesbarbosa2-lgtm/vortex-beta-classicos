package br.com.vortex.beta

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    data class SystemInfo(val name: String, val core: String, val extensions: String)
    private val systems = listOf(
        SystemInfo("Atari 2600", "libstella2014_libretro_android.so", "a26|bin|rom"),
        SystemInfo("Nintendo Entertainment System", "libfceumm_libretro_android.so", "nes|unf|unif|fds"),
        SystemInfo("Super Nintendo", "libsnes9x_libretro_android.so", "smc|sfc|swc|fig"),
        SystemInfo("Game Boy", "libgambatte_libretro_android.so", "gb|gbc"),
        SystemInfo("Game Boy Advance", "libmednafen_gba_libretro_android.so", "gba"),
        SystemInfo("Mega Drive Hack", "libgenesis_plus_gx_libretro_android.so", "bin|gen|md|smd"),
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()

    private fun circleBackground(alpha: Int = 205): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(alpha, 9, 22, 13))
        setStroke(dp(2), neon)
    }

    private fun gameButton(view: GLRetroView, text: String, key: Int, diameter: Int = 76): TextView = TextView(this).apply {
        this.text = text; textSize = if (text.length > 1) 13f else 23f
        gravity = Gravity.CENTER; setTextColor(neon); typeface = Typeface.DEFAULT_BOLD
        background = circleBackground(205); isClickable = true
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { view.sendKeyEvent(KeyEvent.ACTION_DOWN, key); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.sendKeyEvent(KeyEvent.ACTION_UP, key); true }
                else -> true
            }
        }
        layoutParams = GridLayout.LayoutParams().apply { width = dp(diameter); height = dp(diameter); setMargins(dp(5), dp(4), dp(5), dp(4)) }
    }

    private inner class VirtualStick(view: GLRetroView) : View(this@MainActivity) {
        private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 15, 36, 21); style = Paint.Style.FILL }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = neon; style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat() }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 45, 205, 38); style = Paint.Style.FILL }
        private var ax = 0f; private var ay = 0f
        private val radius get() = min(width, height) * .44f
        init { contentDescription = "Joystick virtual" }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f; val r = radius
            canvas.drawCircle(cx, cy, r, outer); canvas.drawCircle(cx, cy, r, ring)
            canvas.drawLine(cx-r*.68f, cy, cx+r*.68f, cy, ring); canvas.drawLine(cx, cy-r*.68f, cx, cy+r*.68f, ring)
            canvas.drawCircle(cx+ax*r*.58f, cy+ay*r*.58f, r*.34f, thumbPaint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val r = radius.coerceAtLeast(1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    ax = ((event.x-width/2f)/(r*.64f)).coerceIn(-1f,1f)
                    ay = ((event.y-height/2f)/(r*.64f)).coerceIn(-1f,1f)
                    view.sendMotionEvent(LibretroDroid.MOTION_SOURCE_DPAD, ax, ay)
                    invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ax=0f; ay=0f; view.sendMotionEvent(LibretroDroid.MOTION_SOURCE_DPAD, 0f, 0f); invalidate(); performClick(); return true
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    private fun launchGame(system: SystemInfo, rom: File) {
        selected = system
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
        view.viewport = RectF(0f, 0f, 1f, 1f)
        retro = view
        lifecycle.addObserver(view)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(5), dp(8), dp(5)); background = roundedPanel() }
        val menu = TextView(this).apply {
            text = "‹  BIBLIOTECA"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(neon); gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12)); background = circleBackground(230)
            setOnClickListener { view.onPause(); lifecycle.removeObserver(view); retro = null; showHome() }
        }
        top.addView(menu)
        top.addView(label(system.name, 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(5), dp(15), dp(7)); background = roundedPanel(); alpha = .94f
        }
        val stick = VirtualStick(view)
        controls.addView(stick, LinearLayout.LayoutParams(dp(186), dp(186)).apply { setMargins(dp(6), 0, dp(8), 0) })
        val spacer = View(this); controls.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val buttons = GridLayout(this).apply { rowCount = 2; columnCount = 2; useDefaultMargins = false }
        buttons.addView(gameButton(view, "Y", KeyEvent.KEYCODE_BUTTON_Y, 68))
        buttons.addView(gameButton(view, "X", KeyEvent.KEYCODE_BUTTON_X, 68))
        buttons.addView(gameButton(view, "B", KeyEvent.KEYCODE_BUTTON_B, 78))
        buttons.addView(gameButton(view, "A", KeyEvent.KEYCODE_BUTTON_A, 78))
        controls.addView(buttons)
        val aux = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8),0,0,0) }
        aux.addView(gameButton(view, "SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, 66))
        aux.addView(gameButton(view, "START", KeyEvent.KEYCODE_BUTTON_START, 66))
        controls.addView(aux)
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(174), Gravity.BOTTOM))
        setContentView(root)
    }

    private fun roundedPanel(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(170, 0, 0, 0)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.argb(125, 53, 255, 20))
    }

    override fun onPause() { retro?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); retro?.onResume() }
    override fun onBackPressed() { if (retro != null) { retro?.onPause(); retro?.let { lifecycle.removeObserver(it) }; retro = null; showHome() } else super.onBackPressed() }
}
