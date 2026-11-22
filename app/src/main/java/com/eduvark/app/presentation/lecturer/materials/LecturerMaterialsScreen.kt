package com.eduvark.app.presentation.lecturer.materials

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.ArrowTitle
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.format.formatBytes
import com.eduvark.app.ui.format.formatShortDate
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

private data class PickedFile(
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
)

/**
 * Kelola materi per kategori (mockups 21 and 22).
 *
 * The page lists the four VARK categories with their material counts and opens an
 * in-screen upload form for the category the lecturer tapped.
 */
@Composable
fun LecturerMaterialsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerMaterialsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formVarkKey by rememberSaveable { mutableStateOf(VarkType.VISUAL.wireValue) }
    var formTitle by rememberSaveable { mutableStateOf("") }
    var formLink by rememberSaveable { mutableStateOf("") }
    var formMode by rememberSaveable { mutableStateOf(SOURCE_FILE) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var showActionError by rememberSaveable { mutableStateOf(false) }
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }

    var pendingSave by remember { mutableStateOf(false) }
    var sawSaving by remember { mutableStateOf(false) }
    var baselineIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val formVark = VarkType.fromWire(formVarkKey) ?: VarkType.VISUAL

    val closeForm: () -> Unit = {
        formOpen = false
        formError = null
        showActionError = false
        formTitle = ""
        formLink = ""
        formMode = SOURCE_FILE
        pickedFile = null
    }

    val openForm: (VarkType) -> Unit = { vark ->
        formVarkKey = vark.wireValue
        formTitle = ""
        formLink = ""
        formMode = SOURCE_FILE
        formError = null
        showActionError = false
        pickedFile = null
        formOpen = true
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()

            if (bytes == null) {
                formError = "Gagal membaca file."
            } else {
                pickedFile = PickedFile(fileName = fileName, mime = mime, bytes = bytes)
                formMode = SOURCE_FILE
                if (formTitle.isBlank()) {
                    formTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
                }
                formError = null
            }
        }
    }

    LaunchedEffect(pendingSave, state.isSaving, state.materials, state.actionError) {
        if (!pendingSave) return@LaunchedEffect
        if (state.isSaving) {
            sawSaving = true
            return@LaunchedEffect
        }
        val created = state.materials.any { it.id !in baselineIds }
        when {
            // The refreshed list already contains the new material.
            created -> {
                pendingSave = false
                sawSaving = false
                expandedKeys = expandedKeys + formVark.wireValue
                closeForm()
            }
            // A failure surfaces through actionError once saving has finished.
            sawSaving && state.actionError != null -> {
                pendingSave = false
                sawSaving = false
            }
            // Saving finished without an error and without a visible list change.
            sawSaving -> {
                pendingSave = false
                sawSaving = false
                expandedKeys = expandedKeys + formVark.wireValue
                closeForm()
            }
        }
    }

    EduScreen(
        title = if (formOpen) "Tambah materi" else "Kelola materi",
        onBack = { if (formOpen) closeForm() else onBack() },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        val topic = state.topic
        val error = state.error
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            error != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                ErrorBlock(message = error, onRetry = viewModel::load)
            }

            topic == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                EmptyBlock(
                    title = "Topik tidak ditemukan",
                    message = "Materi tidak dapat dimuat karena topik tidak tersedia.",
                    actionLabel = "Muat ulang",
                    onAction = viewModel::load,
                )
            }

            formOpen -> UploadForm(
                topic = topic,
                vark = formVark,
                title = formTitle,
                onTitleChange = {
                    formTitle = it
                    formError = null
                },
                mode = formMode,
                onModeChange = {
                    formMode = it
                    formError = null
                    if (it == SOURCE_LINK) pickedFile = null
                },
                link = formLink,
                onLinkChange = {
                    formLink = it
                    formError = null
                },
                pickedFile = pickedFile,
                onPickFile = {
                    picker.launch(
                        arrayOf("application/pdf", "image/*", "audio/*", "video/*"),
                    )
                },
                error = formError,
                actionError = state.actionError.takeIf { showActionError },
                isSaving = state.isSaving,
                onSubmit = {
                    showActionError = true
                    val file = pickedFile
                    when {
                        formTitle.trim().isEmpty() ->
                            formError = "Judul materi wajib diisi."

                        formMode == SOURCE_LINK && formLink.trim().isEmpty() ->
                            formError = "Tautan materi wajib diisi."

                        formMode == SOURCE_FILE && file == null ->
                            formError = "Pilih file materi terlebih dahulu."

                        formMode == SOURCE_LINK -> {
                            baselineIds = state.materials.map { it.id }.toSet()
                            sawSaving = false
                            pendingSave = true
                            viewModel.addLink(formTitle.trim(), formVark, formLink.trim())
                        }

                        else -> {
                            val selected = file
                            if (selected == null) {
                                formError = "Pilih file materi terlebih dahulu."
                            } else {
                                baselineIds = state.materials.map { it.id }.toSet()
                                sawSaving = false
                                pendingSave = true
                                viewModel.upload(
                                    title = formTitle.trim(),
                                    vark = formVark,
                                    payload = UploadPayload(
                                        fileName = selected.fileName,
                                        mime = selected.mime,
                                        bytes = selected.bytes,
                                    ),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            )

            else -> MaterialsContent(
                topic = topic,
                materials = state.materials,
                actionError = state.actionError,
                expandedKeys = expandedKeys,
                onToggle = { vark ->
                    expandedKeys = if (vark.wireValue in expandedKeys) {
                        expandedKeys - vark.wireValue
                    } else {
                        expandedKeys + vark.wireValue
                    }
                },
                onAdd = openForm,
                onDelete = { deleteTarget = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    deleteTarget?.let { material ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = "Hapus materi?") },
            text = {
                Text(text = "Materi \"" + material.title + "\" akan dihapus dari topik ini.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.delete(material.id)
                    },
                ) {
                    Text(text = "Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = "Batal")
                }
            },
        )
    }
}

private const val SOURCE_FILE = "FILE"
private const val SOURCE_LINK = "LINK"

@Composable
private fun MaterialsContent(
    topic: Topic,
    materials: List<Material>,
    actionError: String?,
    expandedKeys: Set<String>,
    onToggle: (VarkType) -> Unit,
    onAdd: (VarkType) -> Unit,
    onDelete: (Material) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        if (actionError != null) {
            NoticeCard(text = actionError, tone = TintTone.Danger)
            Spacer(Modifier.height(16.dp))
        }

        TintBar(
            text = "TOPIK ${topic.sortOrder.toString().padStart(2, '0')} • " +
                topic.status.label.uppercase(),
            tone = if (topic.status == TopicStatus.PUBLISHED) TintTone.Teal else TintTone.Amber,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = topic.title, style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(text = "Kelola setiap format secara independen.", style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        VarkType.entries.forEach { vark ->
            CategoryCard(
                vark = vark,
                materials = materials.filter { it.vark == vark },
                expanded = vark.wireValue in expandedKeys,
                onToggle = { onToggle(vark) },
                onAdd = { onAdd(vark) },
                onDelete = onDelete,
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))
        NoticeCard(
            text = "Mahasiswa hanya melihat kategori yang dipilih pada sesi aktif.",
            tone = TintTone.Primary,
        )
    }
}

@Composable
private fun CategoryCard(
    vark: VarkType,
    materials: List<Material>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (Material) -> Unit,
) {
    val meta = when {
        materials.isEmpty() && vark == VarkType.KINESTHETIC -> "Belum ada studi kasus"
        materials.isEmpty() -> "Belum ada materi"
        else -> "${materials.size} materi • " + materials
            .map { sourceLabel(it) }
            .distinct()
            .joinToString(", ")
    }

    EduCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = categoryLetter(vark),
                style = EduText.Headline.copy(
                    color = EduColors.Primary,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Spacer(Modifier.size(14.dp))
            ArrowTitle(text = categoryLabel(vark), color = EduColors.Primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(text = meta, style = EduText.BodySmall)

        if (expanded && materials.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            materials.forEach { material ->
                MaterialRow(material = material, onDelete = { onDelete(material) })
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (materials.isNotEmpty()) {
                TextLink(
                    text = if (expanded) "Tutup materi" else "Lihat materi",
                    onClick = onToggle,
                    icon = false,
                )
                Text(
                    text = "  /  ",
                    style = EduText.BodySmall.copy(color = EduColors.MutedSoft),
                )
            }
            TextLink(text = "+ Tambah", onClick = onAdd, icon = false)
        }

        if (vark == VarkType.KINESTHETIC) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Studi kasus & deadline diatur dari daftar topik.",
                style = EduText.BodySmall,
            )
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = material.title, style = EduText.BodyStrong)
            Text(text = materialMeta(material), style = EduText.BodySmall)
        }
        Spacer(Modifier.size(8.dp))
        TextLink(
            text = "Hapus",
            onClick = onDelete,
            color = EduColors.Danger,
            icon = false,
        )
    }
}

@Composable
private fun UploadForm(
    topic: Topic,
    vark: VarkType,
    title: String,
    onTitleChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    link: String,
    onLinkChange: (String) -> Unit,
    pickedFile: PickedFile?,
    onPickFile: () -> Unit,
    error: String?,
    actionError: String?,
    isSaving: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceValue = if (mode == SOURCE_LINK) {
        "Tautan"
    } else {
        pickedFile?.let { "File " + (mimeLabel(it.mime) ?: "berkas").lowercase() }
            ?: "Unggah berkas"
    }

    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(
            text = "TOPIK ${topic.sortOrder.toString().padStart(2, '0')} • " +
                categoryLabel(vark).uppercase(),
            tone = TintTone.Primary,
        )
        Spacer(Modifier.height(16.dp))
        Eyebrow("Materi baru")
        Spacer(Modifier.height(10.dp))
        Text(text = "Bagikan sumber\npembelajaran", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Satu topik dapat memuat beberapa materi per kategori.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(18.dp))

        FieldLabel("Judul materi")
        EduField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = "Contoh: Memahami bentuk normal",
            enabled = !isSaving,
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("Jenis sumber")
        EduDropdown(
            value = sourceValue,
            options = listOf("Unggah berkas", "Tautan"),
            onSelect = { selected ->
                onModeChange(if (selected == "Tautan") SOURCE_LINK else SOURCE_FILE)
            },
            enabled = !isSaving,
        )
        Spacer(Modifier.height(16.dp))

        FileDropzone(
            file = pickedFile,
            enabled = !isSaving,
            onPick = onPickFile,
        )
        Spacer(Modifier.height(4.dp))
        FieldHelper(
            text = if (mode == SOURCE_LINK) {
                "Unggah berkas tidak dipakai saat jenis sumber berupa tautan."
            } else {
                "Format yang didukung: PDF, gambar, audio, dan video."
            },
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Alternatif tautan")
        EduField(
            value = link,
            onValueChange = onLinkChange,
            placeholder = "https://…",
            enabled = !isSaving,
        )
        FieldHelper(
            text = if (mode == SOURCE_LINK) {
                "Tautan wajib memakai HTTPS agar dapat dibuka mahasiswa."
            } else {
                "Gunakan tipe Tautan untuk menyimpan materi berupa URL."
            },
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = error, tone = TintTone.Danger)
        }
        if (actionError != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Jenis file dan ukuran divalidasi saat unggah. Batas ukuran media masih " +
                "menunggu keputusan teknis.",
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (mode == SOURCE_LINK) {
                "Simpan tautan materi"
            } else {
                "Unggah & simpan materi"
            },
            enabled = !isSaving,
            loading = isSaving,
            onClick = onSubmit,
        )
        Spacer(Modifier.height(14.dp))
        EduCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, padding = 18.dp) {
            Text(text = "Kategori: ${categoryLabel(vark)}", style = EduText.BodySmall)
            Text(text = "Tanggal unggah dicatat otomatis.", style = EduText.BodySmall)
        }
    }
}

@Composable
private fun FileDropzone(
    file: PickedFile?,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EduColors.PrimaryTint, shape)
            .drawBehind {
                drawRoundRect(
                    color = EduColors.Primary.copy(alpha = 0.45f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(9.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            }
            .clickable(enabled = enabled, onClick = onPick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = EduIcons.ArrowUp,
                contentDescription = null,
                tint = EduColors.Primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = file?.fileName ?: "Pilih file materi",
                style = EduText.BodyStrong.copy(color = EduColors.Primary),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = file?.let { fileMeta(it) } ?: "Ketuk untuk memilih berkas dari perangkat.",
            style = EduText.BodySmall,
        )
        if (file != null) {
            Spacer(Modifier.height(10.dp))
            TextLink(text = "Ganti file", onClick = onPick, color = EduColors.Primary)
        }
    }
}

private fun categoryLetter(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> "V"
    VarkType.AURAL -> "A"
    VarkType.READ_WRITE -> "R"
    VarkType.KINESTHETIC -> "K"
}

private fun categoryLabel(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> "Visual"
    VarkType.AURAL -> "Aural"
    VarkType.READ_WRITE -> "Read/Write"
    VarkType.KINESTHETIC -> "Kinesthetic"
}

private fun mimeLabel(mime: String?): String? = when {
    mime == null -> null
    mime.startsWith("video/") -> "Video"
    mime.startsWith("audio/") -> "Audio"
    mime.startsWith("image/") -> "Gambar"
    mime == "application/pdf" -> "PDF"
    mime.contains("word") || mime.contains("document") -> "Dokumen"
    mime.startsWith("text/") -> "Teks"
    else -> null
}

private fun sourceLabel(material: Material): String =
    material.mime?.let { mimeLabel(it) } ?: material.kind.label

private fun materialMeta(material: Material): String {
    val parts = mutableListOf<String>()
    parts += material.kind.label
    material.mime?.let { mimeLabel(it) }?.let { parts += it }
    formatBytes(material.sizeBytes)?.let { parts += it }
    formatShortDate(material.uploadedAt)?.let { parts += it }
    return parts.distinct().joinToString(" • ")
}

private fun fileMeta(file: PickedFile): String {
    val parts = mutableListOf<String>()
    val kind = mimeLabel(file.mime)
    val extension = file.fileName.substringAfterLast('.', "").uppercase()
    when {
        kind != null && extension.isNotBlank() -> parts += "$kind $extension"
        kind != null -> parts += kind
        extension.isNotBlank() -> parts += extension
    }
    formatBytes(file.bytes.size.toLong())?.let { parts += it }
    return parts.joinToString(" • ")
}

private fun queryFileName(context: Context, uri: Uri): String {
    var name = "materi"
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                val value = cursor.getString(index)
                if (!value.isNullOrBlank()) name = value
            }
        }
    }
    return name
}
