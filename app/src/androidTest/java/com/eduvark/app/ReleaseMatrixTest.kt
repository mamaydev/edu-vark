package com.eduvark.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduvark.app.core.datastore.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-matrix driver. Real-touch navigation through the lecturer flow using
 * UiAutomation (the MIUI emulator blocks `adb shell input`), one scenario per run.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseMatrixTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val args = InstrumentationRegistry.getArguments()
    private val scenario = args.getString("scenario") ?: "hapus"
    private val expectText = args.getString("expect") ?: ""
    private val materialTitle = args.getString("title") ?: ""
    private val baseUrl = args.getString("base") ?: "http://127.0.0.1:3000/api/v1/"
    private val ui get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private val shotsDir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "matrix-shots")
            .apply { mkdirs() }
    }

    private data class ANode(
        val text: String,
        val desc: String,
        val bounds: Rect,
        val clickable: Boolean,
        val node: AccessibilityNodeInfo,
    )

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap = ui.takeScreenshot()
        File(shotsDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("MATRIX_SHOT $name")
    }

    private fun seedBaseUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { SettingsStore(context).saveBaseUrl(baseUrl) }
    }

    // ------------------------------------------------------- accessibility IO

    private fun a11yNodes(): List<ANode> {
        val out = mutableListOf<ANode>()
        val root = ui.rootInActiveWindow ?: return out
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40) return
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.isNotBlank() || desc.isNotBlank()) {
                val r = Rect()
                node.getBoundsInScreen(r)
                out += ANode(text, desc, r, node.isClickable, node)
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun dumpA11y(label: String) {
        val sb = StringBuilder()
        a11yNodes().forEach { sb.append("A11Y text=[${it.text}] desc=[${it.desc}] bounds=${it.bounds} clickable=${it.clickable}\n") }
        File(shotsDir, "a11y-$label.txt").writeText(sb.toString())
    }

    private fun exists(text: String): Boolean = a11yNodes().any { it.text.contains(text) || it.desc.contains(text) }

    /** Waits using Compose semantics first (authoritative), then accessibility nodes. */
    private fun waitApp(text: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val composeCount = try {
                rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
            } catch (t: Throwable) {
                0
            }
            if (composeCount > 0 || exists(text)) return
            if (System.currentTimeMillis() - deadline < -timeoutMs + 8_000) {
                try {
                    rule.onAllNodes(hasScrollAction())[0]
                        .performScrollToNode(hasText(text, substring = true))
                } catch (t: Throwable) { /* not scrollable yet */ }
            }
            Thread.sleep(300)
        }
        dumpA11y("timeout-${text.replace(' ', '_')}")
        shot("timeout-${text.replace(' ', '_').take(24)}")
        throw AssertionError("text not visible: $text")
    }

    private fun waitFor(text: String, timeoutMs: Long = 30_000) {
        val start = System.currentTimeMillis()
        val deadline = start + timeoutMs
        var lastLog = 0L
        while (System.currentTimeMillis() < deadline) {
            if (exists(text)) return
            val elapsed = System.currentTimeMillis() - start
            if (elapsed - lastLog > 5_000) {
                lastLog = elapsed
                val lifecycle = try { rule.activity.lifecycle.currentState.name } catch (t: Throwable) { "?" }
                val root = ui.rootInActiveWindow
                println("MATRIX_WAIT [$text] elapsed=${elapsed}ms lifecycle=$lifecycle window=${root?.packageName} nodes=${a11yNodes().size}")
            }
            Thread.sleep(400)
        }
        dumpA11y("timeout-${text.replace(' ', '_')}")
        shot("timeout-${text.replace(' ', '_').take(24)}")
        throw AssertionError("text not visible: $text")
    }

    /** Clicks through the accessibility action (works for our app and system picker). */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    private fun tapReal(text: String, index: Int = 0, exact: Boolean = false) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val matches = a11yNodes().filter {
                if (exact) it.text == text || it.desc == text else it.text.contains(text) || it.desc.contains(text)
            }
            if (matches.size > index) {
                if (clickNode(matches[index].node)) {
                    Thread.sleep(600)
                    return
                }
                android.util.Log.w("MATRIX", "a11y click failed for [$text] #$index")
            }
            Thread.sleep(300)
        }
        throw AssertionError("tappable node not found: $text")
    }

    private fun tapCompose(text: String, index: Int = 0, exact: Boolean = false) {
        val nodes = if (exact) rule.onAllNodesWithText(text) else rule.onAllNodesWithText(text, substring = true)
        rule.waitUntil(10_000) { nodes.fetchSemanticsNodes().size > index }
        try { nodes[index].performScrollTo() } catch (t: Throwable) { /* already visible */ }
        nodes[index].performClick()
        rule.waitForIdle()
        Thread.sleep(700)
    }

    private fun tapComposeLast(text: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        val nodes = rule.onAllNodesWithText(text)
        val count = nodes.fetchSemanticsNodes().size
        nodes[count - 1].performClick()
        rule.waitForIdle()
        Thread.sleep(700)
    }

    private fun tapApp(text: String, index: Int = 0, exact: Boolean = false) {
        try {
            tapCompose(text, index, exact)
        } catch (t: Throwable) {
            android.util.Log.w("MATRIX", "compose click failed for [$text]: ${t.message}")
            tapReal(text, index, exact)
        }
    }

    private fun tapRealLast(text: String) {
        waitFor(text)
        val matches = a11yNodes().filter { it.text == text || it.desc == text }
        check(matches.isNotEmpty()) { "no node $text" }
        if (!clickNode(matches.last().node)) throw AssertionError("cannot click $text")
        Thread.sleep(600)
    }

    private fun typeCredentials() {
        val user = args.getString("user") ?: "1990010001"
        val pass = args.getString("pass") ?: "123456"
        for (attempt in 0 until 3) {
            val fields = rule.onAllNodes(hasSetTextAction())
            if (fields.fetchSemanticsNodes().size < 2) {
                Thread.sleep(500)
                continue
            }
            fields[0].performTextClearance()
            fields[1].performTextClearance()
            fields[0].performClick()
            fields[0].performTextInput(user)
            fields[1].performClick()
            fields[1].performTextInput(pass)
            Thread.sleep(500)
            if (attempt >= 1) return
            android.util.Log.w("MATRIX", "credentials retry $attempt")
        }
    }

    private fun loginLecturer() {
        waitApp("Masuk untuk belajar")
        Thread.sleep(1_200)
        typeCredentials()
        try {
            tapApp("Masuk", exact = true)
        } catch (t: AssertionError) {
            // Login may have been triggered while we were looking for the button.
            if (!exists("Bantu setiap cara belajar bertumbuh.")) throw t
        }
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (exists("Bantu setiap cara belajar bertumbuh.")) return
            if (exists("Masuk untuk belajar")) {
                Thread.sleep(1_000)
                typeCredentials()
                tapApp("Masuk", exact = true)
            }
            Thread.sleep(1_000)
        }
        dumpA11y("login-failed")
        throw AssertionError("login did not reach home")
    }

    private fun openMaterials() {
        tapApp("Buka daftar topik")
        waitApp("Kelola materi")
        shot("01b-topics")
        tapApp("Kelola materi")
        waitApp("Kelola setiap format secara independen.")
        // Reveal + expand every VARK group: expand visible toggles, otherwise scroll down.
        val wantTitle = materialTitle.isNotBlank()
        fun titleComposed(): Boolean = if (!wantTitle) false else try {
            rule.onAllNodesWithText(materialTitle, substring = true).fetchSemanticsNodes().isNotEmpty()
        } catch (t: Throwable) { false }
        for (step in 0 until 12) {
            if (wantTitle && titleComposed()) break
            val collapsed = try {
                rule.onAllNodesWithText("Lihat materi").fetchSemanticsNodes().size
            } catch (t: Throwable) { 0 }
            if (collapsed > 0) {
                val node = rule.onAllNodesWithText("Lihat materi")[0]
                try { node.performScrollTo() } catch (t: Throwable) { /* visible */ }
                node.performClick()
                rule.waitForIdle()
                Thread.sleep(500)
            } else {
                try {
                    rule.onAllNodes(hasScrollAction())[0].performTouchInput { swipeUp() }
                } catch (t: Throwable) {
                    android.util.Log.w("MATRIX", "swipe failed: ${t.message}")
                    break
                }
                rule.waitForIdle()
                Thread.sleep(500)
            }
        }
        if (wantTitle && !titleComposed()) {
            try {
                rule.onAllNodes(hasScrollAction())[0]
                    .performScrollToNode(hasText(materialTitle, substring = true))
            } catch (t: Throwable) {
                android.util.Log.w("MATRIX", "scrollToNode failed: ${t.message}")
            }
        }
        shot("02-materials-before")
        if (materialTitle.isNotBlank()) waitApp(materialTitle)
    }

    private fun pickFixtureFile(fileName: String) {
        Thread.sleep(3_000)
        shot("picker-initial")
        dumpA11y("picker")
        val stem = fileName.substringBeforeLast('.')
        fun find(nodes: List<ANode>): ANode? =
            nodes.firstOrNull { it.text.contains(stem) || it.desc.contains(stem) }

        var nodes = a11yNodes()
        if (find(nodes) == null) {
            // Open the storage-roots drawer and pick Downloads/Unduhan.
            val roots = nodes.firstOrNull { it.desc.contains("Tampilkan root") || it.desc.contains("Show roots") }
            if (roots != null) {
                clickNode(roots.node)
                Thread.sleep(1_500)
                nodes = a11yNodes()
                dumpA11y("picker-roots")
                val download = nodes.firstOrNull {
                    it.text.equals("Unduhan", true) || it.text.equals("Downloads", true) ||
                        it.desc.equals("Unduhan", true) || it.desc.equals("Downloads", true)
                }
                if (download != null) {
                    clickNode(download.node)
                    Thread.sleep(2_500)
                    nodes = a11yNodes()
                    dumpA11y("picker-downloads")
                }
            }
        }
        val file = find(nodes) ?: throw AssertionError("fixture $fileName not visible in picker")
        if (!clickNode(file.node)) throw AssertionError("cannot click picker file")
        Thread.sleep(3_000)
        shot("picker-after-tap")
    }

    // ------------------------------------------------------------------ test

    @Test
    fun runScenario() {
        seedBaseUrl()
        shot("00-login")
        loginLecturer()
        shot("01-lecturer-home")
        openMaterials()
        when (scenario) {
            "hapus" -> {
                tapApp("Hapus", exact = true)
                waitApp("Hapus materi?")
                shot("03-delete-dialog")
                tapComposeLast("Hapus")
                if (expectText.isNotBlank()) {
                    waitApp(expectText, 30_000)
                    shot("04-delete-notice")
                } else {
                    Thread.sleep(2_500)
                    shot("04-delete-after")
                }
            }
            "ganti" -> {
                tapApp("Ganti file", exact = true)
                pickFixtureFile(args.getString("file") ?: "ganti-b.pdf")
                Thread.sleep(3_000)
                shot("03-replace-after")
                if (expectText.isNotBlank()) waitApp(expectText, 30_000)
                shot("04-replace-final")
            }
            else -> error("unknown scenario $scenario")
        }
        dumpA11y("final")
        println("MATRIX_SCENARIO $scenario DONE")
    }
}
