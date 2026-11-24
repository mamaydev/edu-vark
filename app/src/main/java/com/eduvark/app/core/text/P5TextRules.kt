package com.eduvark.app.core.text

/**
 * Aturan panjang teks yang dipakai bersama oleh Paket 5 (FR-043, FR-047 / C1-04).
 *
 * SRS C1-04 menetapkan satuan "Unicode code point" setelah trim, sehingga emoji
 * (satu code point, dua UTF-16 unit) dan karakter combining dihitung sama seperti
 * di backend (`[...trimmed].length`) dan constraint PostgreSQL (`char_length`).
 * Jangan memakai `String.length` langsung untuk validasi/counter.
 *
 * Trim yang dipakai adalah [ecmaTrim], yaitu padanan ECMAScript `String.prototype.trim`
 * yang dipakai backend Node. `String.trim()` Kotlin TIDAK setara: Kotlin tidak membuang
 * NBSP (U+00A0), FIGURE SPACE (U+2007), NARROW NBSP (U+202F), dan BOM (U+FEFF),
 * tetapi membuang U+001C..U+001F yang justru dipertahankan JavaScript. Memakai trim
 * Kotlin membuat counter Android bisa berbeda dari backend pada teks bertepi karakter
 * tersebut, jadi semua validasi panjang/nilai di sini memakai [ecmaTrim].
 */
object P5TextRules {

    const val MAX_ANSWER_CODE_POINTS: Int = 10_000
    const val MAX_COMMENT_CODE_POINTS: Int = 1_000

    /**
     * Karakter yang dibuang ECMAScript `String.prototype.trim` (WhiteSpace +
     * LineTerminator): TAB, LF, VT, FF, CR, SP, NBSP, OGHAM SPACE MARK, seluruh
     * Zs (U+2000..U+200A termasuk FIGURE SPACE), LINE/PARAGRAPH SEPARATOR,
     * NARROW NBSP, MEDIUM MATHEMATICAL SPACE, IDEOGRAPHIC SPACE, dan BOM.
     */
    private fun isEcmaTrimWhitespace(codePoint: Int): Boolean = when (codePoint) {
        0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x00A0, 0x1680,
        0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true
        in 0x2000..0x200A -> true
        else -> false
    }

    /** Jumlah Unicode code point pada [value] (bukan jumlah unit UTF-16). */
    fun codePointCount(value: String): Int {
        var count = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            index += Character.charCount(codePoint)
            count += 1
        }
        return count
    }

    /**
     * Padanan persis ECMAScript `String.prototype.trim` untuk tepi teks. Dipakai agar
     * counter Android dan normalisasi backend menghitung teks yang sama.
     */
    fun ecmaTrim(value: String): String {
        var start = 0
        var end = value.length
        while (start < end) {
            val codePoint = value.codePointAt(start)
            if (!isEcmaTrimWhitespace(codePoint)) break
            start += Character.charCount(codePoint)
        }
        while (end > start) {
            val codePoint = value.codePointBefore(end)
            if (!isEcmaTrimWhitespace(codePoint)) break
            end -= Character.charCount(codePoint)
        }
        return value.substring(start, end)
    }

    /**
     * Normalisasi teks jawaban yang dikirim ke backend: buang whitespace tepi dengan
     * aturan ECMAScript. Backend tetap penentu akhir (memvalidasi ulang dan menyimpan
     * bentuk ter-trim); fungsi ini hanya menjaga counter/pratinjau Android konsisten.
     */
    fun normalizeAnswerText(answer: String): String = ecmaTrim(answer)

    /** Normalisasi teks komentar penilaian dengan aturan yang sama seperti backend. */
    fun normalizeCommentText(comment: String): String = ecmaTrim(comment)

    /** Jumlah code point jawaban setelah spasi di awal/akhir dibuang. */
    fun answerLength(answer: String): Int = codePointCount(ecmaTrim(answer))

    /** Jumlah code point komentar setelah spasi di awal/akhir dibuang. */
    fun commentLength(comment: String): Int = codePointCount(ecmaTrim(comment))

    /**
     * Nilai manual hanya boleh integer 0–100. String yang bukan bilangan bulat
     * murni (mis. "85.5", "7e1", "-1", "1 0") mengembalikan null dan tidak boleh
     * diubah diam-diam menjadi angka lain (FR-043).
     */
    fun parseScore(input: String): Int? {
        val trimmed = ecmaTrim(input)
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it !in '0'..'9' }) return null
        val value = trimmed.toIntOrNull() ?: return null
        return value.takeIf { it in 0..100 }
    }

    fun isAnswerLengthValid(answer: String): Boolean =
        answerLength(answer) in 1..MAX_ANSWER_CODE_POINTS

    fun isCommentLengthValid(comment: String): Boolean =
        commentLength(comment) <= MAX_COMMENT_CODE_POINTS
}
