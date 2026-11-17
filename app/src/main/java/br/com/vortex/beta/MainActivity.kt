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
import android.view.InputDevice
import android.hardware.input.InputManager
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
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    data class SystemInfo(val name: String, val core: String, val extensions: String)
    private val systems: List<SystemInfo> by lazy {
        val catalog = assets.open("cores.json").bufferedReader().use { it.readText() }
        val entries = JSONArray(catalog)
        (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            SystemInfo(entry.getString("name"), entry.getString("core"), entry.getString("extensions"))
        }
    }
    private val neon = Color.rgb(53, 255, 20)
    private val bg = Color.rgb(5, 10, 8)
    private val picker = 431
    private var selected: SystemInfo? = null
    private var retro: GLRetroView? = null
    private var controllerStatus: TextView? = null
    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }
    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateControllerStatus()
        override fun onInputDeviceRemoved(deviceId: Int) = updateControllerStatus()
        override fun onInputDeviceChanged(deviceId: Int) = updateControllerStatus()
    }
    private var inputListenerRegistered = false

    private fun isGameController(device: InputDevice?): Boolean {
        if (device == null) return false
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun connectedControllerNames(): List<String> {
        val names = mutableListOf<String>()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (isGameController(device) && device != null) names.add(device.name)
        }
        return names.distinct()
    }

    private fun updateControllerStatus() {
        val names = connectedControllerNames()
        runOnUiThread {
            controllerStatus?.text = if (names.isEmpty()) "Controle Bluetooth/USB: nenhum conectado" else "Controle conectado: ${names.joinToString(", ")}"
        }
    }

    override fun onStart() {
        super.onStart()
        if (!inputListenerRegistered) {
            inputManager.registerInputDeviceListener(inputListener, null)
            inputListenerRegistered = true
        }
        updateControllerStatus()
    }

    override fun onStop() {
        if (inputListenerRegistered) {
            inputManager.unregisterInputDeviceListener(inputListener)
            inputListenerRegistered = false
        }
        super.onStop()
    }

    private fun controllerPort(event: KeyEvent) = (event.device?.controllerNumber ?: 1).minus(1).coerceAtLeast(0)
    private fun controllerPort(event: MotionEvent) = (event.device?.controllerNumber ?: 1).minus(1).coerceAtLeast(0)

    private fun mappedGamepadKey(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
        else -> keyCode
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = retro
        if (view != null && isGameController(event.device)) {
            val supported = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR -> true
                else -> false
            }
            if (supported) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return true
                if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                    view.sendKeyEvent(event.action, mappedGamepadKey(event.keyCode), controllerPort(event))
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = retro
        val device = event.device
        if (view != null && isGameController(device) && (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD))) {
            val port = controllerPort(event)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val rx = if (device?.getMotionRange(MotionEvent.AXIS_RX, event.source) != null) event.getAxisValue(MotionEvent.AXIS_RX) else event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = if (device?.getMotionRange(MotionEvent.AXIS_RY, event.source) != null) event.getAxisValue(MotionEvent.AXIS_RY) else event.getAxisValue(MotionEvent.AXIS_RZ)
            view.sendMotionEvent(LibretroDroid.MOTION_SOURCE_DPAD, if (hatX != 0f) hatX else x, if (hatY != 0f) hatY else y, port)
            view.sendMotionEvent(LibretroDroid.MOTION_SOURCE_ANALOG_LEFT, x, y, port)
            view.sendMotionEvent(LibretroDroid.MOTION_SOURCE_ANALOG_RIGHT, rx, ry, port)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

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
        column.addView(label("MEGA DRIVE HACK", 32f, neon, true))
        column.addView(label("BETA CLÁSSICA  •  Android 6+  •  ARM64", 14f, Color.LTGRAY))
        column.addView(label("100 núcleos clássicos · selecione um e abra sua ROM", 17f, Color.WHITE))
        controllerStatus = label("", 14f, neon)
        column.addView(controllerStatus)
        updateControllerStatus()
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

    private inner class VirtualStick(private val retroView: GLRetroView) : View(this@MainActivity) {
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
                    retroView.sendMotionEvent(LibretroDroid.MOTION_SOURCE_DPAD, ax, ay)
                    invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ax=0f; ay=0f; retroView.sendMotionEvent(LibretroDroid.MOTION_SOURCE_DPAD, 0f, 0f); invalidate(); performClick(); return true
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
        view.isFocusable = true
        view.isFocusableInTouchMode = true
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
        view.requestFocus()
    }

    private fun roundedPanel(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(170, 0, 0, 0)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.argb(125, 53, 255, 20))
    }

    override fun onPause() { retro?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); retro?.onResume() }
    override fun onBackPressed() { if (retro != null) { retro?.onPause(); retro?.let { lifecycle.removeObserver(it) }; retro = null; showHome() } else super.onBackPressed() }
}
