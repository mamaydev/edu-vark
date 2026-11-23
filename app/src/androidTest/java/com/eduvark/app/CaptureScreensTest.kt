package com.eduvark.app

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduvark.app.core.datastore.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Design-QA harness: drives the real screens through their main flows and saves
 * full-device screenshots so they can be compared with `docs/Desain/redesain`.
 *
 * MIUI blocks `adb install -g`, so install both APKs manually and run with:
 * adb shell am instrument -w \
 *   -e class com.eduvark.app.CaptureScreensTest \
 *   com.eduvark.app.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val shotsDir: File by lazy {
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "shots",
        ).apply { mkdirs() }
    }

    // ------------------------------------------------------------------ helpers

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(900)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(shotsDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("SHOT $name")
    }

    private fun exists(text: String): Boolean =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String, timeout: Long = 30_000) {
        rule.waitUntil(timeout) { exists(text) }
    }

    private fun waitForFields(count: Int, timeout: Long = 30_000) {
        rule.waitUntil(timeout) {
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= count
        }
    }

    private fun tap(text: String, allowMissing: Boolean = false) {
        if (!exists(text)) {
            if (allowMissing) return
            waitFor(text)
        }
        try {
            rule.onNode(hasClickAction() and hasText(text, substring = true)).performScrollTo()
        } catch (ignored: AssertionError) {
            // already visible or parent does not scroll
        }
        try {
            rule.onNode(hasClickAction() and hasText(text, substring = true))
                .performTouchInput { click() }
        } catch (error: AssertionError) {
            rule.onAllNodesWithText(text, substring = true)[0].performTouchInput { click() }
        }
        rule.waitForIdle()
    }

    /** Taps the [index]-th node carrying [text]; used for repeated card action chips. */
    private fun tapAt(text: String, index: Int) {
        waitFor(text)
        val target = rule.onAllNodesWithText(text, substring = true)[index]
        try {
            target.performScrollTo()
        } catch (ignored: AssertionError) {
            // not in a scrollable parent
        }
        target.performTouchInput { click() }
        rule.waitForIdle()
    }

    private fun fields(): List<SemanticsNodeInteraction> {
        val count = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        return (0 until count).map { rule.onAllNodes(hasSetTextAction())[it] }
    }

    private fun type(index: Int, value: String) {
        fields()[index].performTextInput(value)
        rule.waitForIdle()
    }

    private fun clear(index: Int) {
        fields()[index].performTextClearance()
        rule.waitForIdle()
    }

    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        Thread.sleep(600)
    }

    /** Taps the top-bar back arrow (in-screen state machines use it instead of system back). */
    private fun tapBackArrow() {
        val nodes = rule.onAllNodesWithContentDescription("Kembali").fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "no back arrow on screen" }
        rule.onAllNodesWithContentDescription("Kembali")[0].performTouchInput { click() }
        rule.waitForIdle()
        Thread.sleep(700)
    }

    /** Taps the [index]-th node with content description [desc] (icon-only controls). */
    private fun tapContentDesc(desc: String, index: Int) {
        val nodes = rule.onAllNodesWithContentDescription(desc)
        rule.waitUntil(30_000) { nodes.fetchSemanticsNodes().size > index }
        val target = nodes[index]
        try {
            target.performScrollTo()
        } catch (ignored: AssertionError) {
            // not in a scrollable parent
        }
        target.performTouchInput { click() }
        rule.waitForIdle()
        Thread.sleep(600)
    }

    /** Points the debug build at the host backend exposed through `adb reverse`. */
    private fun seedBaseUrl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { SettingsStore(context).saveBaseUrl("http://127.0.0.1:3000/api/v1/") }
    }

    private fun login(user: String, password: String = "eduvark123") {
        waitFor("Masuk untuk belajar")
        waitForFields(2)
        fields().forEachIndexed { index, _ -> clear(index) }
        type(0, user)
        type(1, password)
        tap("Masuk")
    }

    private fun chooseVark(label: String) {
        waitFor("caramu hari ini")
        tap(label)
        tap("Lanjut dengan $label")
        rule.waitForIdle()
    }

    private fun loginAs(user: String, varkLabel: String) {
        login(user)
        chooseVark(varkLabel)
    }

    private fun logout() {
        if (!exists("Akun & sesi")) {
            tap("Profil", allowMissing = true)
        }
        if (!exists("Akun & sesi")) {
            tap("Akun", allowMissing = true)
        }
        if (!exists("Akun & sesi")) {
            tap("Profil", allowMissing = true)
        }
        if (exists("Akun & sesi")) {
            tap("Keluar dari akun", allowMissing = true)
        }
        waitFor("Masuk untuk belajar")
    }

    private fun ensureLoggedOut() {
        Thread.sleep(1_500)
        if (exists("Masuk untuk belajar")) return
        logout()
    }

    // ------------------------------------------------------------------- tests

    @Test
    fun test00_captureStudentVisualFlow() {
        seedBaseUrl()
        ensureLoggedOut()
        shot("01_login")

        // --- mockup 02: rejected credentials
        waitForFields(2)
        type(0, "2410511001")
        type(1, "salah123")
        tap("Masuk")
        rule.waitUntil(30_000) {
            exists("tidak valid") || exists("Terjadi kesalahan") || exists("wajib diisi")
        }
        shot("02_login_rejected")
        waitForFields(2)
        clear(1)

        // --- mockup 03: VARK selection
        type(1, "eduvark123")
        tap("Masuk")
        waitFor("caramu hari ini")
        shot("03_vark_selection")
        tap("Visual")
        shot("03b_vark_selected")
        tap("Lanjut dengan Visual")

        // --- mockup 05: student home (visual)
        waitFor("Siap memahami hal baru?")
        shot("05_home_student")

        // --- mockup 06: topic list
        tap("Topik")
        waitFor("Ruang belajarmu")
        shot("06_student_topics")

        // --- mockup 07: material player + feedback
        tap("Normalisasi basis data")
        waitFor("Materi Visual")
        shot("07_material_visual")
        shot("08_material_feedback")
        tap("Simpan feedback", allowMissing = true)
        Thread.sleep(2_500)
        shot("10_feedback_saved")

        // --- mockup 04: account & session
        tap("Akun")
        waitFor("Akun & sesi")
        shot("04_account")
        logout()
    }

    @Test
    fun test01_captureStudentAuralFlow() {
        loginAs("2410511002", "Audio")
        waitFor("Siap memahami hal baru?")
        tap("Topik")
        waitFor("Ruang belajarmu")
        shot("06b_student_topics_aural")
        tap("Normalisasi basis data")
        waitFor("Materi Aural")
        shot("08_material_aural")
        logout()
    }

    @Test
    fun test02_captureStudentReadWriteFlow() {
        loginAs("2410511003", "Read/Write")
        waitFor("Siap memahami hal baru?")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("Materi Read/Write")
        shot("09_material_readwrite")
        back()
        waitFor("Ruang belajarmu")
        tap("Transaksi & konkurensi")
        waitFor("harap hubungi dosen")
        shot("11_material_unavailable")
        logout()
    }

    @Test
    fun test03_captureStudentKinestheticFlow() {
        loginAs("2410511003", "Kinesthetic")
        waitFor("Saatnya mencoba")
        shot("12_home_kinesthetic")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("Instruksi studi kasus")
        shot("13_case_study")
        waitForFields(1)
        type(0, "Normalisasi basis data perpustakaan dilakukan dengan memisahkan entitas buku, " +
            "anggota, dan peminjaman ke tabel sendiri agar tidak ada data berulang.")
        tap("Kirim jawaban final")
        waitFor("Sudah yakin dengan")
        shot("14_confirm_submission")
        tap("Ya, kirim jawaban final")
        waitFor("Jawabanmu")
        shot("15_submission_status")
        logout()
    }

    @Test
    fun test04_captureLecturerFlow() {
        login("20240140033")
        waitFor("Bantu setiap cara belajar bertumbuh.")
        shot("18_home_lecturer")

        tap("Topik")
        waitFor("Susun perjalanan")
        shot("19_lecturer_topics")

        tapAt("Edit detail", 3)
        waitFor("Atur detail topik")
        shot("20_topic_detail")
        tapBackArrow()
        waitFor("Susun perjalanan")

        tapAt("Kelola materi", 3)
        waitFor("Kelola setiap format")
        shot("21_manage_materials")
        tap("+ Tambah")
        waitFor("Bagikan sumber")
        shot("22_add_material")
        tapBackArrow()
        waitFor("Kelola setiap format")
        tapBackArrow()
        waitFor("Susun perjalanan")

        tapContentDesc("Aksi topik", 3)
        tap("Atur studi kasus")
        waitFor("Rancang studi kasus")
        shot("23_case_editor")
        tapBackArrow()
        waitFor("Susun perjalanan")

        tapContentDesc("Aksi topik", 3)
        tap("Hapus topik")
        waitFor("Hapus topik ini?")
        shot("24_delete_topic_confirm")
        tap("Batal, pertahankan topik")
        waitFor("Susun perjalanan")

        // --- submissions and grading
        tap("Penilaian")
        waitFor("Pilih topik untuk")
        tap("Normalisasi basis data")
        waitFor("Pengumpulan")
        shot("25_submissions")
        tap("Raka Pratama")
        waitFor("BELUM DINILAI")
        shot("26_grade_form")
        waitForFields(2)
        type(0, "85")
        type(1, "Jawaban runtut dan contoh kasusnya tepat.")
        tap("Tinjau penilaian final")
        waitFor("Simpan nilai")
        shot("27_confirm_grade")
        tap("Ya, simpan penilaian final")
        waitFor("Nilai tersimpan")
        shot("28_grade_detail")

        // --- analytics
        back()
        back()
        waitFor("Pilih topik untuk")
        tap("Analitik")
        waitFor("Pilih topik untuk")
        tapAt("Buka analitik", 0)
        waitFor("Belum ada data")
        shot("31_analytics_empty")
        back()
        waitFor("Pilih topik untuk")
        tapAt("Buka analitik", 3)
        waitFor("Dengarkan data")
        shot("29_analytics_detail")
        tap("Lihat feedback mahasiswa")
        waitFor("Lihat setiap suara")
        shot("30_analytics_feedback")

        logout()
    }

    @Test
    fun test20_verifyGradingSubmissionsV2() {
        seedBaseUrl()
        ensureLoggedOut()
        login("1990010001", "123456")
        waitFor("Bantu setiap cara belajar bertumbuh.")
        tap("Penilaian")
        waitFor("Pilih topik untuk")
        shot("qa_v2_grading_hub")
        tap("Normalisasi basis data")
        waitFor("Pengumpulan")
        waitFor("Mahasiswa Satu")
        waitFor("Mahasiswa Dua")
        shot("qa_v2_submissions")

        // The answer itself must open for grading.
        tap("Mahasiswa Dua")
        waitFor("BELUM DINILAI")
        waitFor("normalisasi 1NF-3NF")
        shot("qa_v2_grade_form")
        tapBackArrow()
        waitFor("Pengumpulan")
    }

    @Test
    fun test21_verifyLoginLabelsV2() {
        seedBaseUrl()
        ensureLoggedOut()
        waitFor("NIM/NIDN")
        waitFor("NIM / NIDN")
        shot("qa_v2_login")
    }

    @Test
    fun test13_verifyFreshAdminLogin() {
        seedBaseUrl()
        ensureLoggedOut()
        login("admin", "123456")
        waitFor("Beranda admin")
        shot("qa_admin_after_reset")
    }

    @Test
    fun test12_captureWaitingSubmission() {
        loginAs("2410511003", "Kinesthetic")
        waitFor("Saatnya mencoba")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("Menunggu penilaian")
        shot("15_submission_status")
    }

    @Test
    fun test11_captureKinestheticHome() {
        loginAs("2410511001", "Kinesthetic")
        waitFor("Saatnya mencoba")
        waitFor("Studi kasus untukmu")
        shot("12_home_kinesthetic")
        logout()
    }

    @Test
    fun test10_captureCaseEditor() {
        loginAs("2410511001", "Kinesthetic")
        waitFor("Saatnya mencoba")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("Instruksi studi kasus")
        shot("13_case_study")
        logout()
    }

    @Test
    fun test05_captureStudentGradedResult() {
        loginAs("2410511002", "Kinesthetic")
        waitFor("Saatnya mencoba")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("Hasil kerja kerasmu.")
        shot("16_graded_result")
        logout()
    }

    @Test
    fun test07_captureDeadlineExpired() {
        loginAs("20240140022", "Kinesthetic")
        waitFor("Saatnya mencoba")
        tap("Topik")
        waitFor("Ruang belajarmu")
        tap("Normalisasi basis data")
        waitFor("TENGGAT BERAKHIR")
        shot("17_deadline_expired")
        logout()
    }

    @Test
    fun test08_captureInactiveLogin() {
        waitFor("Masuk untuk belajar")
        waitForFields(2)
        fields().forEachIndexed { index, _ -> clear(index) }
        type(0, "20240140022")
        type(1, "eduvark123")
        tap("Masuk")
        waitFor("LOGIN DITOLAK")
        shot("38_login_inactive")
    }

    @Test
    fun test09_captureEmptyTopics() {
        loginAs("2410511001", "Visual")
        waitFor("Beranda")
        tap("Topik")
        waitFor("segera dimulai")
        shot("39_topics_empty")
        logout()
    }

    @Test
    fun test06_captureAdminFlow() {
        login("admin")
        waitFor("Akun tertata")
        shot("32_admin_home")

        tap("Akun")
        waitFor("Kelola akses pengguna.")
        shot("33_admin_users")

        tap("+ Buat akun")
        waitFor("Siapkan akses pengguna.")
        shot("34_admin_create_user")
        waitForFields(4)
        type(0, "Nadia Duplikat")
        type(1, "2410511001")
        type(2, "eduvark123")
        type(3, "eduvark123")
        tap("Simpan akun baru")
        waitFor("Gunakan identitas yang berbeda.")
        shot("37_admin_validation")
        tap("Kembali ke daftar akun")
        waitFor("Kelola akses pengguna.")

        waitForFields(1)
        type(0, "20240140022")
        waitFor("ridha novita")
        tap("ridha novita")
        waitFor("Identitas dan akses akun")
        shot("35_admin_user_detail")
        tap("Nonaktifkan akun")
        waitFor("Nonaktifkan akun ini?")
        shot("36_admin_deactivate_confirm")
        tap("Batal")
        waitFor("Identitas dan akses akun")

        tap("Profil")
        waitFor("Akun & sesi")
        logout()
    }
}
