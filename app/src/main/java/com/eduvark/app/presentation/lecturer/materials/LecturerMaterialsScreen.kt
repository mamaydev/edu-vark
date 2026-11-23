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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

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
    val fileReadError = stringResource(R.string.lec_material_file_read_error)
    val defaultFileName = stringResource(R.string.lec_material_default_file_name)
    val titleRequiredError = stringResource(R.string.lec_material_error_title)
    val linkRequiredError = stringResource(R.string.lec_material_error_link)
    val fileRequiredError = stringResource(R.string.lec_material_error_file)

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
            val fileName = queryFileName(context, uri, defaultFileName)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()

            if (bytes == null) {
                formError = fileReadError
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
        title = if (formOpen) {
            stringResource(R.string.lec_material_screen_add)
        } else {
            stringResource(R.string.lec_material_screen_manage)
        },
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
                    .padding(dimen(R.dimen.space_24)),
            ) {
                ErrorBlock(message = error, onRetry = viewModel::load)
            }

            topic == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                EmptyBlock(
                    title = stringResource(R.string.lec_material_topic_missing_title),
                    message = stringResource(R.string.lec_material_topic_missing_body),
                    actionLabel = stringResource(R.string.lec_material_retry),
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
                            formError = titleRequiredError

                        formMode == SOURCE_LINK && formLink.trim().isEmpty() ->
                            formError = linkRequiredError

                        formMode == SOURCE_FILE && file == null ->
                            formError = fileRequiredError

                        formMode == SOURCE_LINK -> {
                            baselineIds = state.materials.map { it.id }.toSet()
                            sawSaving = false
                            pendingSave = true
                            viewModel.addLink(formTitle.trim(), formVark, formLink.trim())
                        }

                        else -> {
                            val selected = file
                            if (selected == null) {
                                formError = fileRequiredError
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
            title = { Text(text = stringResource(R.string.lec_material_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.lec_material_delete_body,
                        material.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.delete(material.id)
                    },
                ) {
                    Text(text = stringResource(R.string.lec_material_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.lec_material_delete_cancel))
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
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        if (actionError != null) {
            NoticeCard(text = actionError, tone = TintTone.Danger)
            Spacer(Modifier.height(dimen(R.dimen.space_16)))
        }

        TintBar(
            text = stringResource(
                R.string.lec_material_chip,
                topic.sortOrder.toString().padStart(2, '0'),
                topic.status.label.uppercase(),
            ),
            tone = if (topic.status == TopicStatus.PUBLISHED) TintTone.Teal else TintTone.Amber,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Text(text = topic.title, style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_material_header_body), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        VarkType.entries.forEach { vark ->
            CategoryCard(
                vark = vark,
                materials = materials.filter { it.vark == vark },
                expanded = vark.wireValue in expandedKeys,
                onToggle = { onToggle(vark) },
                onAdd = { onAdd(vark) },
                onDelete = onDelete,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
        }

        Spacer(Modifier.height(dimen(R.dimen.space_4)))
        NoticeCard(
            text = stringResource(R.string.lec_material_notice),
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
    val metaSeparator = stringResource(R.string.lec_material_meta_separator)
    val meta = when {
        materials.isEmpty() && vark == VarkType.KINESTHETIC ->
            stringResource(R.string.lec_material_none_case)

        materials.isEmpty() -> stringResource(R.string.lec_material_none)

        else -> {
            val labels = mutableListOf<String>()
            for (material in materials) labels += sourceLabel(material)
            stringResource(
                R.string.lec_material_meta_count,
                materials.size,
                labels.distinct().joinToString(metaSeparator),
            )
        }
    }

    EduCard(modifier = Modifier.fillMaxWidth(), radius = dimen(R.dimen.dimen_20), padding = dimen(R.dimen.dimen_18)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = categoryLetter(vark),
                style = EduTheme.type.headline.copy(
                    color = EduTheme.colors.lavender,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Spacer(Modifier.size(dimen(R.dimen.space_14)))
            ArrowTitle(text = categoryLabel(vark), color = EduTheme.colors.lavender)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Text(text = meta, style = EduTheme.type.bodySmall)

        if (expanded && materials.isNotEmpty()) {
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            materials.forEach { material ->
                MaterialRow(material = material, onDelete = { onDelete(material) })
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (materials.isNotEmpty()) {
                TextLink(
                    text = if (expanded) {
                        stringResource(R.string.lec_material_link_close)
                    } else {
                        stringResource(R.string.lec_material_link_open)
                    },
                    onClick = onToggle,
                    color = EduTheme.colors.lime,
                    icon = false,
                )
                Text(
                    text = stringResource(R.string.lec_material_link_separator),
                    style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.mutedSoft),
                )
            }
            TextLink(
                text = stringResource(R.string.lec_material_link_add),
                onClick = onAdd,
                color = EduTheme.colors.lime,
                icon = false,
            )
        }

        if (vark == VarkType.KINESTHETIC) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.lec_material_kinesthetic_note),
                style = EduTheme.type.bodySmall,
            )
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimen(R.dimen.space_10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = material.title, style = EduTheme.type.bodyStrong)
            Text(text = materialMeta(material), style = EduTheme.type.bodySmall)
        }
        Spacer(Modifier.size(dimen(R.dimen.space_8)))
        TextLink(
            text = stringResource(R.string.lec_material_action_delete),
            onClick = onDelete,
            color = EduTheme.colors.danger,
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
    val unknownFileLabel = stringResource(R.string.lec_material_source_file_unknown)
    val uploadFileLabel = stringResource(R.string.lec_material_source_upload)
    val linkSourceLabel = stringResource(R.string.lec_material_source_link)
    val pickedMimeLabel = pickedFile?.let { mimeLabel(it.mime) }
    val sourceValue = if (mode == SOURCE_LINK) {
        linkSourceLabel
    } else {
        pickedFile?.let {
            stringResource(
                R.string.lec_material_source_file_mime,
                (pickedMimeLabel ?: unknownFileLabel).lowercase(),
            )
        } ?: uploadFileLabel
    }

    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(
                R.string.lec_material_chip,
                topic.sortOrder.toString().padStart(2, '0'),
                categoryLabel(vark).uppercase(),
            ),
            tone = TintTone.Primary,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Eyebrow(stringResource(R.string.lec_material_form_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_material_form_headline), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_material_form_body),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        FieldLabel(stringResource(R.string.lec_material_form_title_label))
        EduField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = stringResource(R.string.lec_material_form_title_placeholder),
            enabled = !isSaving,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        FieldLabel(stringResource(R.string.lec_material_form_source_label))
        EduDropdown(
            value = sourceValue,
            options = listOf(uploadFileLabel, linkSourceLabel),
            onSelect = { selected ->
                onModeChange(if (selected == linkSourceLabel) SOURCE_LINK else SOURCE_FILE)
            },
            enabled = !isSaving,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        FileDropzone(
            file = pickedFile,
            enabled = !isSaving,
            onPick = onPickFile,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_4)))
        FieldHelper(
            text = if (mode == SOURCE_LINK) {
                stringResource(R.string.lec_material_form_file_helper_link)
            } else {
                stringResource(R.string.lec_material_form_file_helper)
            },
        )

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        FieldLabel(stringResource(R.string.lec_material_form_link_label))
        EduField(
            value = link,
            onValueChange = onLinkChange,
            placeholder = stringResource(R.string.lec_material_form_link_placeholder),
            enabled = !isSaving,
        )
        FieldHelper(
            text = if (mode == SOURCE_LINK) {
                stringResource(R.string.lec_material_form_link_helper_required)
            } else {
                stringResource(R.string.lec_material_form_link_helper)
            },
        )

        if (error != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = error, tone = TintTone.Danger)
        }
        if (actionError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.lec_material_form_notice),
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = if (mode == SOURCE_LINK) {
                stringResource(R.string.lec_material_form_save_link)
            } else {
                stringResource(R.string.lec_material_form_save_file)
            },
            enabled = !isSaving,
            loading = isSaving,
            onClick = onSubmit,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_14)))
        EduCard(modifier = Modifier.fillMaxWidth(), radius = dimen(R.dimen.space_20), padding = dimen(R.dimen.space_18)) {
            Text(
                text = stringResource(R.string.lec_material_form_meta_category, categoryLabel(vark)),
                style = EduTheme.type.bodySmall,
            )
            Text(
                text = stringResource(R.string.lec_material_form_meta_uploaded),
                style = EduTheme.type.bodySmall,
            )
        }
    }
}

@Composable
private fun FileDropzone(
    file: PickedFile?,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(dimen(R.dimen.radius_16))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EduTheme.colors.purpleTint, shape)
            .clickable(enabled = enabled, onClick = onPick)
            .padding(dimen(R.dimen.space_18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = EduIcons.ArrowUp,
                contentDescription = null,
                tint = EduTheme.colors.lavender,
                modifier = Modifier.size(dimen(R.dimen.size_18)),
            )
            Spacer(Modifier.size(dimen(R.dimen.space_8)))
            Text(
                text = file?.fileName ?: stringResource(R.string.lec_material_file_pick),
                style = EduTheme.type.bodyStrong.copy(color = EduTheme.colors.lavender),
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(
            text = file?.let { fileMeta(it) }
                ?: stringResource(R.string.lec_material_file_hint),
            style = EduTheme.type.bodySmall,
        )
        if (file != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            TextLink(
                text = stringResource(R.string.lec_material_file_change),
                onClick = onPick,
                color = EduTheme.colors.lavender,
            )
        }
    }
}

@Composable
private fun categoryLetter(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> stringResource(R.string.lec_material_letter_visual)
    VarkType.AURAL -> stringResource(R.string.lec_material_letter_aural)
    VarkType.READ_WRITE -> stringResource(R.string.lec_material_letter_read_write)
    VarkType.KINESTHETIC -> stringResource(R.string.lec_material_letter_kinesthetic)
}

@Composable
private fun categoryLabel(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> stringResource(R.string.lec_material_vark_visual)
    VarkType.AURAL -> stringResource(R.string.lec_material_vark_aural)
    VarkType.READ_WRITE -> stringResource(R.string.lec_material_vark_read_write)
    VarkType.KINESTHETIC -> stringResource(R.string.lec_material_vark_kinesthetic)
}

@Composable
private fun mimeLabel(mime: String?): String? = when {
    mime == null -> null
    mime.startsWith("video/") -> stringResource(R.string.lec_material_mime_video)
    mime.startsWith("audio/") -> stringResource(R.string.lec_material_mime_audio)
    mime.startsWith("image/") -> stringResource(R.string.lec_material_mime_image)
    mime == "application/pdf" -> stringResource(R.string.lec_material_mime_pdf)
    mime.contains("word") || mime.contains("document") ->
        stringResource(R.string.lec_material_mime_document)

    mime.startsWith("text/") -> stringResource(R.string.lec_material_mime_text)
    else -> null
}

@Composable
private fun sourceLabel(material: Material): String {
    val mime = material.mime ?: return material.kind.label
    return mimeLabel(mime) ?: material.kind.label
}

@Composable
private fun materialMeta(material: Material): String {
    val join = stringResource(R.string.lec_material_meta_join)
    val parts = mutableListOf<String>()
    parts += material.kind.label
    material.mime?.let { mimeLabel(it) }?.let { parts += it }
    formatBytes(material.sizeBytes)?.let { parts += it }
    formatShortDate(material.uploadedAt)?.let { parts += it }
    return parts.distinct().joinToString(join)
}

@Composable
private fun fileMeta(file: PickedFile): String {
    val join = stringResource(R.string.lec_material_meta_join)
    val parts = mutableListOf<String>()
    val kind = mimeLabel(file.mime)
    val extension = file.fileName.substringAfterLast('.', "").uppercase()
    when {
        kind != null && extension.isNotBlank() ->
            parts += stringResource(R.string.lec_material_file_meta_kind_ext, kind, extension)
        kind != null -> parts += kind
        extension.isNotBlank() -> parts += extension
    }
    formatBytes(file.bytes.size.toLong())?.let { parts += it }
    return parts.joinToString(join)
}

private fun queryFileName(context: Context, uri: Uri, defaultName: String): String {
    var name = defaultName
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
