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
import androidx.compose.ui.res.stringResource
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
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

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
        deleteTarget != null -> stringResource(R.string.lec_topic_screen_title_delete)
        editorOpen -> stringResource(R.string.lec_topic_screen_title_editor)
        else -> stringResource(R.string.lec_topic_screen_title_list)
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
                    .padding(dimen(R.dimen.space_24)),
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
                    val currentTopic = editingTopic
                    if (currentTopic == null) {
                        pendingPublish = if (published) {
                            PendingPublish(title, state.topics.map { it.id }.toSet())
                        } else {
                            null
                        }
                        viewModel.create(title, description)
                    } else {
                        // FR-009 / DEC-002: simpan judul + deskripsi pada topik
                        // yang sama (PATCH), bukan hanya status publikasi.
                        viewModel.updateTopic(
                            topicId = currentTopic.id,
                            title = title,
                            description = description,
                            published = published,
                        )
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
    ScrollColumn(spacing = dimen(R.dimen.dimen_0)) {
        if (actionError != null) {
            NoticeCard(text = actionError, tone = TintTone.Danger)
            Spacer(Modifier.height(dimen(R.dimen.space_16)))
        }

        Eyebrow(stringResource(R.string.lec_topic_eyebrow_class))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_topic_headline), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(
                R.string.lec_topic_count_summary,
                publishedCount,
                draftCount,
            ),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        PrimaryButton(text = stringResource(R.string.lec_topic_create), onClick = onCreate)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        FieldLabel(stringResource(R.string.lec_topic_search_label))
        EduField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.lec_topic_search_placeholder),
            trailing = {
                Icon(
                    imageVector = EduIcons.Search,
                    contentDescription = null,
                    tint = EduTheme.colors.muted,
                    modifier = Modifier.size(dimen(R.dimen.size_18)),
                )
            },
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        if (visible.isEmpty()) {
            NoticeCard(
                text = if (topics.isEmpty()) {
                    stringResource(R.string.lec_topic_empty)
                } else {
                    stringResource(R.string.lec_topic_empty_search, query)
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
                Spacer(Modifier.height(dimen(R.dimen.space_14)))
            }
        }

        if (topics.isNotEmpty()) {
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            Text(
                text = stringResource(R.string.lec_topic_order_hint),
                style = EduTheme.type.bodySmall,
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

    EduCard(modifier = Modifier.fillMaxWidth(), radius = dimen(R.dimen.dimen_20), padding = dimen(R.dimen.dimen_18)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardEyebrow(
                text = stringResource(
                    R.string.lec_topic_card_order,
                    order.toString().padStart(2, '0'),
                ),
                modifier = Modifier.weight(1f),
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(dimen(R.dimen.size_30))
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EduIcons.More,
                        contentDescription = stringResource(R.string.lec_topic_menu_content_desc),
                        tint = EduTheme.colors.muted,
                        modifier = Modifier.size(dimen(R.dimen.size_18)),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = EduTheme.colors.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.lec_topic_menu_case), style = EduTheme.type.bodyStrong) },
                        onClick = {
                            menuOpen = false
                            onCase()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.lec_topic_menu_analytics), style = EduTheme.type.bodyStrong) },
                        onClick = {
                            menuOpen = false
                            onAnalytics()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (published) {
                                    stringResource(R.string.lec_topic_menu_unpublish)
                                } else {
                                    stringResource(R.string.lec_topic_menu_publish)
                                },
                                style = EduTheme.type.bodyStrong,
                            )
                        },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onTogglePublish()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.lec_topic_menu_move_up), style = EduTheme.type.bodyStrong) },
                        enabled = enabled && canMoveUp,
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.lec_topic_menu_move_down), style = EduTheme.type.bodyStrong) },
                        enabled = enabled && canMoveDown,
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.lec_topic_action_delete), style = EduTheme.type.bodyStrong) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Text(text = topic.title, style = EduTheme.type.cardTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        TintBar(
            text = if (published) {
                stringResource(R.string.lec_topic_status_published)
            } else {
                stringResource(R.string.lec_topic_status_draft)
            },
            tone = if (published) TintTone.Teal else TintTone.Amber,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Row(horizontalArrangement = Arrangement.spacedBy(dimen(R.dimen.space_16))) {
            TextLink(
                text = stringResource(R.string.lec_topic_link_edit),
                onClick = onEdit,
                color = EduTheme.colors.lavender,
            )
            TextLink(
                text = stringResource(R.string.lec_topic_link_materials),
                onClick = onMaterials,
                color = EduTheme.colors.lavender,
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
    val publishedLabel = stringResource(R.string.lec_topic_status_published)
    val draftLabel = stringResource(R.string.lec_topic_status_draft)
    val titleRequiredError = stringResource(R.string.lec_topic_editor_title_error)

    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_topic_editor_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_topic_editor_headline), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_topic_editor_body), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        FieldLabel(stringResource(R.string.lec_topic_editor_title_label))
        EduField(
            value = title,
            onValueChange = {
                title = it
                localError = null
            },
            placeholder = stringResource(R.string.lec_topic_editor_title_placeholder),
            enabled = !isSaving,
            error = localError != null,
        )
        if (localError != null) {
            FieldHelper(text = localError.orEmpty(), isError = true)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        FieldLabel(stringResource(R.string.lec_topic_editor_desc_label))
        EduField(
            value = description,
            onValueChange = { description = it },
            placeholder = stringResource(R.string.lec_topic_editor_desc_placeholder),
            enabled = !isSaving,
            singleLine = false,
            minHeight = dimen(R.dimen.dimen_96),
        )
        if (!isCreate) {
            FieldHelper(
                text = stringResource(R.string.p5_topic_editor_edit_helper),
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        FieldLabel(stringResource(R.string.lec_topic_editor_order_label))
        if (isCreate) {
            EduCard(modifier = Modifier.fillMaxWidth(), radius = dimen(R.dimen.size_20), padding = dimen(R.dimen.dimen_16)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = position.toString().padStart(2, '0'),
                        style = EduTheme.type.bodyStrong,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = stringResource(R.string.lec_topic_editor_order_auto), style = EduTheme.type.bodySmall)
                }
            }
            FieldHelper(text = stringResource(R.string.lec_topic_editor_order_auto_helper))
        } else {
            EduStepper(
                value = position,
                onDecrease = { onMove(-1) },
                onIncrease = { onMove(1) },
            )
            FieldHelper(
                text = stringResource(R.string.lec_topic_editor_order_helper),
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        FieldLabel(stringResource(R.string.lec_topic_editor_status_label))
        EduDropdown(
            value = if (published) publishedLabel else draftLabel,
            options = listOf(publishedLabel, draftLabel),
            onSelect = { published = it == publishedLabel },
            enabled = !isSaving,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (availableCount == 0) {
                    stringResource(R.string.lec_topic_editor_availability_empty_title)
                } else {
                    stringResource(R.string.lec_topic_editor_availability_title, availableCount)
                },
                style = EduTheme.type.cardTitle,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvailability(
                    label = stringResource(R.string.lec_topic_vark_visual),
                    available = availableCount > 0,
                )
                CategoryDot()
                CategoryAvailability(
                    label = stringResource(R.string.lec_topic_vark_aural),
                    available = availableCount > 0,
                )
                CategoryDot()
                CategoryAvailability(
                    label = stringResource(R.string.lec_topic_vark_read_write),
                    available = availableCount > 0,
                )
            }
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = if (availableCount == 0) {
                    stringResource(R.string.lec_topic_editor_availability_empty_helper)
                } else {
                    stringResource(R.string.lec_topic_editor_availability_helper)
                },
                style = EduTheme.type.bodySmall,
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        NoticeCard(
            text = stringResource(R.string.lec_topic_editor_notice),
            tone = TintTone.Primary,
        )

        if (actionError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = if (published) {
                stringResource(R.string.lec_topic_editor_save_publish)
            } else {
                stringResource(R.string.lec_topic_editor_save_draft)
            },
            enabled = !isSaving,
            loading = isSaving,
            onClick = {
                if (title.trim().isEmpty()) {
                    localError = titleRequiredError
                } else {
                    onSave(title.trim(), description.trim(), published)
                }
            },
        )
        if (!isCreate) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            SecondaryButton(
                text = stringResource(R.string.lec_topic_action_delete),
                enabled = !isSaving,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CategoryDot() {
    Text(
        text = stringResource(R.string.lec_topic_availability_dot),
        style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.mutedSoft),
    )
}

@Composable
private fun CategoryAvailability(label: String, available: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = EduTheme.type.bodySmall)
        Spacer(Modifier.size(dimen(R.dimen.space_4)))
        if (available) {
            Icon(
                imageVector = EduIcons.Check,
                contentDescription = null,
                tint = EduTheme.colors.muted,
                modifier = Modifier.size(dimen(R.dimen.size_13)),
            )
        } else {
            Text(
                text = stringResource(R.string.lec_topic_availability_none),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.mutedSoft),
            )
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
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_topic_delete_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_topic_delete_headline), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_topic_delete_body), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduTheme.colors.dangerTint,
        ) {
            Eyebrow(
                text = stringResource(
                    R.string.lec_topic_delete_card_eyebrow,
                    position.toString().padStart(2, '0'),
                    topic.status.label,
                ),
                color = EduTheme.colors.dangerMuted,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = topic.title,
                style = EduTheme.type.headline.copy(color = EduTheme.colors.dangerMuted),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.lec_topic_delete_card_body),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.dangerMuted),
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        TonePanel(
            title = null,
            body = stringResource(R.string.lec_topic_delete_check),
            tone = TintTone.Amber,
        )

        if (actionError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = actionError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = stringResource(R.string.lec_topic_delete_cancel),
            enabled = !isSaving,
            onClick = onCancel,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        DangerButton(
            text = stringResource(R.string.lec_topic_delete_confirm),
            enabled = !isSaving,
            onClick = onConfirm,
        )
    }
}
