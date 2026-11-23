package com.eduvark.app.presentation.lecturer.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.ui.components.CardEyebrow
import com.eduvark.app.ui.components.DangerButton
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduPullToRefresh
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EduStepper
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

/**
 * Kelola topik (mockups 19, 20 and 24).
 *
 * One screen, three in-screen states: the ordered topic list, the topic editor and the
 * delete confirmation. The top bar and the back affordance follow the active state.
 */
@Composable
fun LecturerTopicsScreen(
    onBack: () -> Unit,
    onOpenMaterials: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenAnalytics: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerTopicsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    // Bridges "Simpan & terbitkan" for a brand new topic: publish once the refreshed list
    // contains the topic that create() just sent to the server.
    var pendingPublish by remember { mutableStateOf<PendingPublish?>(null) }

    val editingTopic = state.topics.firstOrNull { it.id == editingTopicId }
    val deleteTarget = state.topics.firstOrNull { it.id == deleteTargetId }

    LaunchedEffect(editorOpen, editingTopicId, state.topics, state.isLoading) {
        if (editorOpen && editingTopicId != null && editingTopic == null && !state.isLoading) {
            editorOpen = false
            editingTopicId = null
        }
    }

    LaunchedEffect(deleteTargetId, state.topics, state.isLoading) {
        if (deleteTargetId != null && deleteTarget == null && !state.isLoading) {
            deleteTargetId = null
        }
    }

    LaunchedEffect(state.topics, pendingPublish) {
        val pending = pendingPublish ?: return@LaunchedEffect
        val created = state.topics.firstOrNull {
            it.id !in pending.knownIds && it.title == pending.title
        }
        if (created != null) {
            pendingPublish = null
            viewModel.setPublished(created.id, true)
        }
    }

    LaunchedEffect(state.actionError) {
        if (state.actionError != null) pendingPublish = null
    }

    val screenTitle = when {
        deleteTarget != null -> "Hapus topik"
        editorOpen -> "Detail topik"
        else -> "Topik pembelajaran"
    }

    val handleBack: () -> Unit = {
        when {
            deleteTargetId != null -> deleteTargetId = null
            editorOpen -> {
                editorOpen = false
                editingTopicId = null
                viewModel.clearActionError()
            }

            else -> onBack()
        }
    }

    EduScreen(
        title = screenTitle,
        onBack = handleBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
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

            deleteTarget != null -> DeleteTopicState(
                topic = deleteTarget,
                position = state.topics.indexOfFirst { it.id == deleteTarget.id } + 1,
                isSaving = state.isSaving,
                actionError = state.actionError,
                onCancel = {
                    deleteTargetId = null
                    viewModel.clearActionError()
                },
                onConfirm = {
                    viewModel.delete(deleteTarget.id)
                    deleteTargetId = null
                },
                modifier = Modifier.padding(padding),
            )

            editorOpen -> TopicEditorState(
                topic = editingTopic,
                position = editingTopic?.let { topic ->
                    state.topics.indexOfFirst { it.id == topic.id } + 1
                } ?: (state.topics.size + 1),
                isSaving = state.isSaving,
                actionError = state.actionError,
                onSave = { title, description, published ->
                    if (editingTopic == null) {
                        pendingPublish = if (published) {
                            PendingPublish(title, state.topics.map { it.id }.toSet())
                        } else {
                            null
                        }
                        viewModel.create(title, description)
                    } else {
                        viewModel.setPublished(editingTopic.id, published)
                    }
                    editorOpen = false
                    editingTopicId = null
                },
                onMove = { delta ->
                    val topic = editingTopic
                    if (topic != null && !state.isSaving) {
                        val index = state.topics.indexOfFirst { it.id == topic.id }
                        if (index >= 0) viewModel.move(index, delta)
                    }
                },
                onDelete = {
                    editingTopic?.let { topic ->
                        deleteTargetId = topic.id
                        editorOpen = false
                    }
                },
                modifier = Modifier.padding(padding),
            )

            else -> TopicListState(
                topics = state.topics,
                publishedCount = state.topics.count { it.status == TopicStatus.PUBLISHED },
                draftCount = state.topics.count { it.status == TopicStatus.DRAFT },
                isSaving = state.isSaving,
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                actionError = state.actionError,
                query = query,
                onQueryChange = { query = it },
                onCreate = {
                    viewModel.clearActionError()
                    editingTopicId = null
                    editorOpen = true
                },
                onEdit = { topicId ->
                    viewModel.clearActionError()
                    editingTopicId = topicId
                    editorOpen = true
                },
                onMaterials = onOpenMaterials,
                onCase = onOpenCase,
                onAnalytics = onOpenAnalytics,
                onTogglePublish = { topic ->
                    viewModel.setPublished(
                        topic.id,
                        topic.status != TopicStatus.PUBLISHED,
                    )
                },
                onDelete = { topicId -> deleteTargetId = topicId },
                onMove = { index, delta -> viewModel.move(index, delta) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private data class PendingPublish(val title: String, val knownIds: Set<String>)

@Composable
private fun TopicListState(
    topics: List<Topic>,
    publishedCount: Int,
    draftCount: Int,
    isSaving: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    actionError: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onMaterials: (String) -> Unit,
    onCase: (String) -> Unit,
    onAnalytics: (String) -> Unit,
    onTogglePublish: (Topic) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indexed = topics.mapIndexed { index, topic -> index to topic }
    val visible = if (query.isBlank()) {
        indexed
    } else {
        indexed.filter { (_, topic) -> topic.title.contains(query, ignoreCase = true) }
    }

    EduPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
    ScrollColumn(spacing = 0.dp) {
        if (actionError != null) {
            NoticeCard(text = actionError, tone = TintTone.Danger)
            Spacer(Modifier.height(16.dp))
        }

        Eyebrow("Kelas A • IBD")
        Spacer(Modifier.height(10.dp))
        Text(text = "Susun perjalanan\nbelajar kelas.", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "$publishedCount terbit · $draftCount draf",
            style = EduText.Body,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = "+ Buat topik", onClick = onCreate)
        Spacer(Modifier.height(18.dp))
        FieldLabel("Cari topik")
        EduField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Cari judul topik…",
            trailing = {
                Icon(
                    imageVector = EduIcons.Search,
                    contentDescription = null,
                    tint = EduColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            NoticeCard(
                text = if (topics.isEmpty()) {
                    "Belum ada topik. Buat topik pertama untuk kelas ini."
                } else {
                    "Tidak ada topik yang cocok dengan \"$query\"."
                },
                tone = TintTone.Neutral,
            )
        } else {
            visible.forEach { (index, topic) ->
                key(topic.id) {
                    LecturerTopicCard(
                        topic = topic,
                        order = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < topics.lastIndex,
                        enabled = !isSaving,
                        onEdit = { onEdit(topic.id) },
                        onMaterials = { onMaterials(topic.id) },
                        onCase = { onCase(topic.id) },
                        onAnalytics = { onAnalytics(topic.id) },
                        onTogglePublish = { onTogglePublish(topic) },
                        onDelete = { onDelete(topic.id) },
                        onMoveUp = { onMove(index, -1) },
                        onMoveDown = { onMove(index, 1) },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (topics.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Gunakan kontrol urutan untuk mengatur daftar yang akan dilihat " +
                    "mahasiswa.",
                style = EduText.BodySmall,
            )
        }
    }
    }
}

@Composable
private fun LecturerTopicCard(
    topic: Topic,
    order: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMaterials: () -> Unit,
    onCase: () -> Unit,
    onAnalytics: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val published = topic.status == TopicStatus.PUBLISHED

    EduCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, padding = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardEyebrow(
                text = "Urutan ${order.toString().padStart(2, '0')}",
                modifier = Modifier.weight(1f),
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EduIcons.More,
                        contentDescription = "Aksi topik",
                        tint = EduColors.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = EduColors.Surface,
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Atur studi kasus", style = EduText.BodyStrong) },
                        onClick = {
                            menuOpen = false
                            onCase()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Analitik topik", style = EduText.BodyStrong) },
                        onClick = {
                            menuOpen = false
                            onAnalytics()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (published) "Tarik ke draf" else "Terbitkan topik",
                                style = EduText.BodyStrong,
                            )
                        },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onTogglePublish()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Naikkan urutan", style = EduText.BodyStrong) },
                        enabled = enabled && canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Turunkan urutan", style = EduText.BodyStrong) },
                        enabled = enabled && canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Hapus topik", style = EduText.BodyStrong) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(text = topic.title, style = EduText.CardTitle)
        Spacer(Modifier.height(10.dp))
        TintBar(
            text = if (published) "Terbit" else "Draf",
            tone = if (published) TintTone.Teal else TintTone.Amber,
        )
        Spacer(Modifier.height(12.dp))
        AvailabilityRow(available = topic.materialAvailable)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextLink(
                text = "Edit detail",
                onClick = onEdit,
                color = EduColors.Primary,
            )
            TextLink(
                text = "Kelola materi",
                onClick = onMaterials,
                color = EduColors.Primary,
            )
        }
    }
}

/**
 * "V ✓  A ✓  R ✓  K —" availability row. The API only exposes one availability flag for
 * the V/A/R media, while Kinesthetic runs through the case study editor, so K stays empty.
 */
@Composable
private fun AvailabilityRow(available: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvailabilityMark(letter = "V", available = available)
        AvailabilityMark(letter = "A", available = available)
        AvailabilityMark(letter = "R", available = available)
        AvailabilityMark(letter = "K", available = false)
    }
}

@Composable
private fun AvailabilityMark(letter: String, available: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = letter,
            style = EduText.BodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = EduColors.Muted,
            ),
        )
        Spacer(Modifier.size(4.dp))
        if (available) {
            Icon(
                imageVector = EduIcons.Check,
                contentDescription = null,
                tint = EduColors.Teal,
                modifier = Modifier.size(13.dp),
            )
        } else {
            Text(
                text = "—",
                style = EduText.BodySmall.copy(color = EduColors.MutedSoft),
            )
        }
    }
}

@Composable
private fun TopicEditorState(
    topic: Topic?,
    position: Int,
    isSaving: Boolean,
    actionError: String?,
    onSave: (String, String, Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCreate = topic == null
    val focusKey = topic?.id

    var title by rememberSaveable(focusKey) { mutableStateOf(topic?.title.orEmpty()) }
    var description by rememberSaveable(focusKey) {
        mutableStateOf(topic?.description.orEmpty())
    }
    var published by rememberSaveable(focusKey) {
        mutableStateOf(topic?.status == TopicStatus.PUBLISHED)
    }
    var localError by rememberSaveable(focusKey) { mutableStateOf<String?>(null) }

    val availableCount = if (topic?.materialAvailable == true) 3 else 0

    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Editor topik")
        Spacer(Modifier.height(10.dp))
        Text(text = "Atur detail topik", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(text = "Buat atau perbarui informasi pembelajaran.", style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        FieldLabel("Judul topik")
        EduField(
            value = title,
            onValueChange = {
                title = it
                localError = null
            },
            placeholder = "Contoh: Normalisasi basis data",
            enabled = isCreate && !isSaving,
            error = localError != null,
        )
        if (localError != null) {
            FieldHelper(text = localError.orEmpty(), isError = true)
        }
        Spacer(Modifier.height(16.dp))

        FieldLabel("Deskripsi")
        EduField(
            value = description,
            onValueChange = { description = it },
            placeholder = "Ringkas tujuan topik untuk mahasiswa",
            enabled = isCreate && !isSaving,
            singleLine = false,
            minHeight = 96.dp,
        )
        if (!isCreate) {
            FieldHelper(
                text = "Judul dan deskripsi topik hanya dapat diatur saat topik dibuat.",
            )
        }
        Spacer(Modifier.height(16.dp))

        FieldLabel("Urutan topik")
        if (isCreate) {
            EduCard(modifier = Modifier.fillMaxWidth(), radius = 12.dp, padding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = position.toString().padStart(2, '0'),
                        style = EduText.BodyStrong,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "otomatis", style = EduText.BodySmall)
                }
            }
            FieldHelper(text = "Topik baru masuk di urutan terakhir.")
        } else {
            EduStepper(
                value = position,
                onDecrease = { onMove(-1) },
                onIncrease = { onMove(1) },
            )
            FieldHelper(
                text = "Urutan menentukan tampilan daftar untuk mahasiswa.",
            )
        }
        Spacer(Modifier.height(16.dp))

        FieldLabel("Status publikasi")
        EduDropdown(
            value = if (published) "Terbit" else "Draf",
            options = listOf("Terbit", "Draf"),
            onSelect = { published = it == "Terbit" },
            enabled = !isSaving,
        )
        Spacer(Modifier.height(16.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (availableCount == 0) {
                    "Belum ada kategori tersedia"
                } else {
                    "$availableCount dari 4 kategori tersedia"
                },
                style = EduText.CardTitle,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvailability(label = "Visual", available = availableCount > 0)
                CategoryDot()
                CategoryAvailability(label = "Aural", available = availableCount > 0)
                CategoryDot()
                CategoryAvailability(label = "Read/Write", available = availableCount > 0)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (availableCount == 0) {
                    "Lengkapi materi setelah topik dibuat. Kinesthetic memakai studi kasus."
                } else {
                    "Kinesthetic belum tersedia • diatur lewat studi kasus"
                },
                style = EduText.BodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        NoticeCard(
            text = "Topik boleh diterbitkan meskipun belum semua format tersedia.",
            tone = TintTone.Primary,
        )

        if (actionError != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = if (published) "Simpan & terbitkan" else "Simpan sebagai draf",
            enabled = !isSaving,
            loading = isSaving,
            onClick = {
                if (isCreate && title.trim().isEmpty()) {
                    localError = "Judul topik wajib diisi."
                } else {
                    onSave(title.trim(), description.trim(), published)
                }
            },
        )
        if (!isCreate) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Hapus topik",
                enabled = !isSaving,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CategoryDot() {
    Text(
        text = "  •  ",
        style = EduText.BodySmall.copy(color = EduColors.MutedSoft),
    )
}

@Composable
private fun CategoryAvailability(label: String, available: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = EduText.BodySmall)
        Spacer(Modifier.size(4.dp))
        if (available) {
            Icon(
                imageVector = EduIcons.Check,
                contentDescription = null,
                tint = EduColors.Teal,
                modifier = Modifier.size(13.dp),
            )
        } else {
            Text(text = "—", style = EduText.BodySmall.copy(color = EduColors.MutedSoft))
        }
    }
}

@Composable
private fun DeleteTopicState(
    topic: Topic,
    position: Int,
    isSaving: Boolean,
    actionError: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Konfirmasi tindakan")
        Spacer(Modifier.height(10.dp))
        Text(text = "Hapus topik ini?", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(text = "Pastikan topik yang dipilih sudah benar.", style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduColors.DangerTint,
        ) {
            Eyebrow(
                text = "Topik ${position.toString().padStart(2, '0')} • " +
                    topic.status.label,
                color = EduColors.Danger,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = topic.title,
                style = EduText.Headline.copy(color = EduColors.Danger),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Topik yang dihapus tidak akan tampil dalam daftar pembelajaran.",
                style = EduText.BodySmall.copy(color = EduColors.Danger),
            )
        }

        Spacer(Modifier.height(16.dp))
        TonePanel(
            title = null,
            body = "Periksa kembali materi dan data yang terkait sebelum melanjutkan.",
            tone = TintTone.Amber,
        )

        if (actionError != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Batal, pertahankan topik",
            enabled = !isSaving,
            onClick = onCancel,
        )
        Spacer(Modifier.height(12.dp))
        DangerButton(
            text = "Ya, hapus topik",
            enabled = !isSaving,
            onClick = onConfirm,
        )
    }
}
