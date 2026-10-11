package com.rahulanim.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

class Actor(val who: String, val kind: String, var x: Float, var y: Float) {
    var tx = x; var ty = y
    var speaking = false; var mouth = false
}

class Prop(val type: String, val px: Float)

class MainActivity : Activity() {
    private lateinit var scene: SceneView
    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())
    private var presets = listOf("স্কুল রাস্তা", "শহর", "রাতের গ্রাম", "ভূতের বাড়ি")
    private var styles = listOf("কার্টুন", "হরর")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B1220"))
        root.setPadding(16, 20, 16, 12)

        val title = TextView(this)
        title.text = "🎬 RAHUL ANIM STUDIO"
        title.textSize = 20f; title.setTextColor(Color.parseColor("#00E5A0"))
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        root.addView(title)

        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val spPreset = Spinner(this)
        spPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets)
        row1.addView(spPreset, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val spStyle = Spinner(this)
        spStyle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
        row1.addView(spStyle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row1)

        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        val prompt = EditText(this)
        prompt.hint = "AI background prompt (e.g. village school anime)"
        prompt.setTextColor(Color.WHITE); prompt.setHintTextColor(Color.parseColor("#8FA3C0"))
        row2.addView(prompt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btnBg = Button(this); btnBg.text = "AI BG"
        btnBg.setOnClickListener {
            val p = prompt.text.toString().trim()
            if (p.isNotEmpty()) loadAiBg(p)
        }
        row2.addView(btnBg)
        root.addView(row2)

        val script = EditText(this)
        script.setLines(4)
        script.setText("ছেলে: আজ আমি স্কুলে যাবো!\nমেয়ে: আচ্ছা চলো একসাথে যাই।\nছেলে: দাও তাহলে বাসে উঠি।\nভূত: হা হা হা... আমিও আসবো!")
        script.setTextColor(Color.WHITE); script.setHintTextColor(Color.parseColor("#8FA3C0"))
        script.setBackgroundColor(Color.parseColor("#172338"))
        root.addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val btnPlay = Button(this)
        btnPlay.text = "▶️ ভিডিও বানাও"
        btnPlay.setOnClickListener {
            val lines = parseScript(script.text.toString())
            if (lines.isEmpty()) Toast.makeText(this, "স্ক্রিপ্ট লেখো!", Toast.LENGTH_SHORT).show()
            else scene.play(lines)
        }
        root.addView(btnPlay)

        scene = SceneView(this)
        root.addView(scene, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        spPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { scene.setPreset(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { scene.style = pos }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        setContentView(root)

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale("bn", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val r2 = tts.setLanguage(Locale("hi", "IN"))
                    if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.ENGLISH)
                }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { id?.let { scene.pending.remove(it)?.invoke() } }
            override fun onDone(id: String?) { id?.let { scene.pending.remove(it)?.invoke() } }
        })
        scene.tts = tts
        scene.setPreset(0)
        startFrameLoop()
    }

    private fun parseScript(s: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (raw in s.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf(":")
            if (idx < 0) continue
            out.add(Pair(line.substring(0, idx).trim(), line.substring(idx + 1).trim()))
        }
        return out
    }

    private fun loadAiBg(p: String) {
        Toast.makeText(this, "AI background banacche...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val u = URL("https://image.pollinations.ai/prompt/" + Uri.encode(p) + "?width=720&height=1280&nologo=true")
                val c = u.openConnection() as HttpURLConnection
                c.doInput = true; c.connect()
                val bmp = BitmapFactory.decodeStream(c.inputStream)
                c.disconnect()
                if (bmp != null) runOnUiThread { scene.bgBitmap = bmp; scene.invalidate() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "AI bg fail", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun startFrameLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                scene.tickFrame()
                handler.postDelayed(this, 60)
            }
        }, 60)
    }
}

class SceneView(ctx: android.content.Context) : View(ctx) {
    var tts: TextToSpeech? = null
    var style = 0
    var bgBitmap: Bitmap? = null
    var preset = 0
    val pending = HashMap<String, () -> Unit>()
    private val actors = ArrayList<Actor>()
    private var props = ArrayList<Prop>()
    private var playing = false
    private val P = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setPreset(pos: Int) {
        preset = pos
        bgBitmap = null
        props = when (pos) {
            0 -> arrayListOf(Prop("sun", 0.82f), Prop("cloud", 0.25f), Prop("tree", 0.1f), Prop("house", 0.45f), Prop("bus", 0.72f))
            1 -> arrayListOf(Prop("sun", 0.85f), Prop("cloud", 0.3f), Prop("house", 0.12f), Prop("house", 0.4f), Prop("car", 0.68f), Prop("tree", 0.9f))
            2 -> arrayListOf(Prop("moon", 0.8f), Prop("house", 0.35f), Prop("tree", 0.1f))
            else -> arrayListOf(Prop("moon", 0.78f), Prop("cloud", 0.3f), Prop("house", 0.35f), Prop("tree", 0.08f))
        }
        invalidate()
    }

    private fun kindFor(who: String): String {
        val w = who.lowercase()
        return when {
            w.contains("ভূত") || w.contains("ghost") -> "ghost"
            w.contains("জম") || w.contains("zombie") -> "zombie"
            w.contains("মেয়ে") || w.contains("girl") -> "girl"
            w.contains("মহিলা") || w.contains("woman") -> "woman"
            else -> "boy"
        }
    }

    fun play(lines: List<Pair<String, String>>) {
        if (playing) return
        playing = true
        actors.clear()
        for ((who, _) in lines) {
            if (actors.none { it.who == who }) actors.add(Actor(who, kindFor(who), -80f, 0f))
        }
        actors.forEachIndexed { i, a ->
            a.tx = width * (0.2f + 0.6f * i / maxOf(1, actors.size - 1).toFloat())
            a.ty = groundY()
            a.x = -100f - i * 60
            a.y = a.ty
        }
        var idx = 0
        fun step() {
            if (idx >= lines.size) {
                playing = false
                actors.forEach { it.speaking = false }
                invalidate()
                return
            }
            val (who, msg) = lines[idx]
            val a = actors.firstOrNull { it.who == who } ?: actors[0]
            actors.forEach { it.speaking = false }
            a.speaking = true
            val uid = "u" + System.nanoTime()
            pending[uid] = {
                idx++
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ step() }, 350)
            }
            tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, uid)
            invalidate()
        }
        step()
    }

    fun groundY(): Float = height * 0.8f

    fun tickFrame() {
        var moving = false
        for (a in actors) {
            val dx = a.tx - a.x
            if (abs(dx) > 2f) { a.x += dx * 0.12f; moving = true }
            a.mouth = a.speaking && (System.currentTimeMillis() / 130) % 2 == 0L
        }
        if (moving || actors.any { it.speaking } || playing) invalidate()
    }

    override fun onDraw(c: Canvas) {
        drawBackground(c)
        val gy = groundY()
        P.color = if (night()) Color.parseColor("#1E2B22") else Color.parseColor("#3E8E4E")
        c.drawRect(0f, gy, width.toFloat(), height.toFloat(), P)
        for (pr in props) drawProp(c, pr, gy)
        for (a in actors) drawActor(c, a, gy)
    }

    private fun night(): Boolean = style == 1 || preset >= 2

    private fun drawBackground(c: Canvas) {
        if (bgBitmap != null) {
            c.drawBitmap(bgBitmap!!, null, android.graphics.Rect(0, 0, width, height), P)
            return
        }
        if (night()) {
            c.drawColor(Color.parseColor("#0A0F1E"))
            P.color = Color.WHITE
            for (i in 0..20) {
                val sx = ((i * 137) % 100) / 100f * width
                val sy = ((i * 89) % 45) / 100f * height
                c.drawCircle(sx, sy, 3f, P)
            }
        } else {
            c.drawColor(Color.parseColor("#8EC9E8"))
        }
    }

    private fun drawProp(c: Canvas, pr: Prop, gy: Float) {
        val x = pr.px * width
        when (pr.type) {
            "sun" -> { P.color = Color.parseColor("#FFD94A"); c.drawCircle(x, height * 0.14f, 40f, P) }
            "moon" -> { P.color = Color.parseColor("#F4F1DE"); c.drawCircle(x, height * 0.14f, 36f, P); P.color = Color.parseColor("#0A0F1E"); c.drawCircle(x + 16, height * 0.14f - 8, 30f, P) }
            "cloud" -> { P.color = Color.parseColor("#EAF4FB"); c.drawCircle(x, height * 0.12f, 26f, P); c.drawCircle(x + 30, height * 0.12f + 6, 22f, P); c.drawCircle(x - 30, height * 0.12f + 8, 20f, P) }
            "tree" -> {
                P.color = Color.parseColor("#6B4423"); c.drawRect(x - 8, gy - 70, x + 8, gy, P)
                P.color = if (preset == 3) Color.parseColor("#2B2B2B") else Color.parseColor("#2F7D3B")
                c.drawCircle(x, gy - 95, 34f, P); c.drawCircle(x - 22, gy - 75, 24f, P); c.drawCircle(x + 22, gy - 75, 24f, P)
            }
            "house" -> {
                P.color = if (preset == 3) Color.parseColor("#3A3A4A") else Color.parseColor("#D9C6A5"); c.drawRect(x - 60, gy - 110, x + 60, gy, P)
                P.color = if (preset == 3) Color.parseColor("#1E1E2E") else Color.parseColor("#B5552D")
                c.drawRect(x - 70, gy - 130, x + 70, gy - 108, P)
                P.color = Color.parseColor("#5B4636"); c.drawRect(x - 14, gy - 48, x + 14, gy, P)
                P.color = Color.parseColor("#F5E6A8"); c.drawRect(x + 24, gy - 90, x + 46, gy - 66, P)
            }
            "bus" -> {
                P.color = Color.parseColor("#F2B705"); c.drawRoundRect(x - 90, gy - 100, x + 90, gy - 20, 16f, 16f, P)
                P.color = Color.parseColor("#BFE6F5")
                for (i in 0..3) c.drawRect(x - 72 + i * 38, gy - 88, x - 48 + i * 38, gy - 58, P)
                P.color = Color.BLACK; c.drawCircle(x - 50, gy - 16, 18f, P); c.drawCircle(x + 50, gy - 16, 18f, P)
                P.color = Color.DKGRAY; c.drawCircle(x - 50, gy - 16, 8f, P); c.drawCircle(x + 50, gy - 16, 8f, P)
            }
            "car" -> {
                P.color = Color.parseColor("#C94F4F"); c.drawRoundRect(x - 70, gy - 60, x + 70, gy - 22, 12f, 12f, P)
                c.drawRoundRect(x - 40, gy - 88, x + 30, gy - 56, 10f, 10f, P)
                P.color = Color.BLACK; c.drawCircle(x - 40, gy - 18, 14f, P); c.drawCircle(x + 40, gy - 18, 14f, P)
            }
            else -> {
                val s = if (pr.type == "bike") 1f else 0.8f
                P.color = Color.BLACK; P.strokeWidth = 6f
                c.drawCircle(x - 30 * s, gy - 20 * s, 18 * s, P); c.drawCircle(x + 30 * s, gy - 20 * s, 18 * s, P)
                P.strokeWidth = 5f
                c.drawLine(x - 30 * s, gy - 20 * s, x, gy - 55 * s, P)
                c.drawLine(x + 30 * s, gy - 20 * s, x, gy - 55 * s, P)
                c.drawLine(x - 30 * s, gy - 20 * s, x + 30 * s, gy - 20 * s, P)
                P.strokeWidth = 1f
            }
        }
    }

    private fun drawActor(c: Canvas, a: Actor, gy: Float) {
        val x = a.x; var y = a.y
        val hor = style == 1 || a.kind == "ghost" || a.kind == "zombie"
        if (a.kind == "ghost") y += sin(System.currentTimeMillis() / 300.0).toFloat() * 8f - 30f
        val skin = when {
            a.kind == "zombie" -> Color.parseColor("#7DA87B")
            hor -> Color.parseColor("#E8E4DA")
            else -> Color.parseColor("#F2C99B")
        }
        when (a.kind) {
            "girl" -> {
                P.color = Color.parseColor("#5A3A1E"); c.drawCircle(x, y - 118, 26f, P); c.drawRect(x - 26, y - 110, x - 16, y - 70, P); c.drawRect(x + 16, y - 110, x + 26, y - 70, P)
                P.color = skin; c.drawCircle(x, y - 112, 20f, P)
                P.color = Color.parseColor("#C2537A"); c.drawRect(x - 16, y - 88, x + 16, y - 40, P)
                drawFace(c, x, y - 112, a); drawLegs(c, x, y, a)
            }
            "woman" -> {
                P.color = Color.parseColor("#2B2B2B"); c.drawCircle(x, y - 122, 24f, P); c.drawRect(x - 24, y - 110, x - 14, y - 60, P); c.drawRect(x + 14, y - 110, x + 24, y - 60, P)
                P.color = skin; c.drawCircle(x, y - 114, 19f, P)
                P.color = Color.parseColor("#8E44AD"); c.drawRect(x - 14, y - 92, x + 14, y - 40, P)
                drawFace(c, x, y - 114, a); drawLegs(c, x, y, a)
            }
            "ghost" -> {
                P.color = Color.parseColor("#F1F1F1")
                c.drawCircle(x, y - 95, 30f, P)
                c.drawRect(x - 30, y - 95, x + 30, y - 30, P)
                var xx = x - 30f
                while (xx < x + 30) { c.drawCircle(xx, y - 28, 9f, P); xx += 15f }
                P.color = Color.BLACK; c.drawCircle(x - 10, y - 100, 5f, P); c.drawCircle(x + 10, y - 100, 5f, P)
                if (a.mouth) { P.color = Color.BLACK; c.drawOval(x - 10, y - 88, x + 10, y - 74, P) }
                else { P.color = Color.BLACK; c.drawRect(x - 8, y - 82, x + 8, y - 78, P) }
            }
            "zombie" -> {
                P.color = skin; c.drawCircle(x, y - 115, 21f, P)
                P.color = Color.parseColor("#4A5D3A"); c.drawRect(x - 17, y - 92, x + 17, y - 40, P)
                drawFace(c, x, y - 115, a); drawLegs(c, x, y, a)
            }
            else -> {
                P.color = Color.parseColor("#2B2B2B"); c.drawCircle(x, y - 122, 22f, P)
                P.color = skin; c.drawCircle(x, y - 112, 20f, P)
                P.color = if (hor) Color.parseColor("#3A3A3A") else Color.parseColor("#3D6FB4")
                c.drawRect(x - 17, y - 90, x + 17, y - 40, P)
                drawFace(c, x, y - 112, a); drawLegs(c, x, y, a)
            }
        }
    }

    private fun drawFace(c: Canvas, x: Float, hy: Float, a: Actor) {
        val hor = style == 1 || a.kind == "zombie"
        P.color = if (hor) Color.parseColor("#D63031") else Color.BLACK
        c.drawCircle(x - 8, hy - 4, 3.5f, P); c.drawCircle(x + 8, hy - 4, 3.5f, P)
        if (a.mouth) { P.color = Color.parseColor("#7A2E2E"); c.drawOval(x - 10, hy + 6, x + 10, hy + 20, P) }
        else { P.color = Color.BLACK; c.drawRect(x - 8, hy + 10, x + 8, hy + 13, P) }
    }

    private fun drawLegs(c: Canvas, x: Float, y: Float, a: Actor) {
        P.color = Color.parseColor("#2B3A55")
        val moving = abs(a.tx - a.x) > 3f
        val ph = if (moving) sin(System.currentTimeMillis() / 130.0).toFloat() * 10f else 0f
        c.drawRect(x - 13, y - 40, x - 3, y - 2 + ph, P)
        c.drawRect(x + 3, y - 40, x + 13, y - 2 - ph, P)
    }
}
