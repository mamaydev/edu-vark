package com.eduvark.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduFontFamily
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

@Composable
private fun topBarTitle() = TextStyle(
    fontFamily = EduFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = dimenSp(R.dimen.text_size_18),
    lineHeight = dimenSp(R.dimen.text_size_24),
    letterSpacing = (-0.2).sp,
    color = EduTheme.colors.ink,
)

@Composable
private fun brandTitle() = TextStyle(
    fontFamily = EduFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = dimenSp(R.dimen.text_size_20),
    lineHeight = dimenSp(R.dimen.text_size_26),
    letterSpacing = (-0.3).sp,
    color = EduTheme.colors.ink,
)

/** Transparent (page-coloured) top bar with a centred title, matching the designs. */
@Composable
fun EduTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EduTheme.colors.page)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimen(R.dimen.size_56))
                .padding(horizontal = dimen(R.dimen.space_12)),
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(dimen(R.dimen.size_40))
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EduIcons.ChevronLeft,
                        contentDescription = stringResource(R.string.common_back),
                        tint = EduTheme.colors.ink,
                        modifier = Modifier.size(dimen(R.dimen.size_22)),
                    )
                }
            }
            Text(
                text = title,
                style = if (brand) brandTitle() else topBarTitle(),
                modifier = Modifier.align(Alignment.Center),
            )
            if (action != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    action()
                }
            }
        }
    }
}

/**
 * Standard screen scaffold: dark page background, transparent top bar, floating bottom bar.
 */
@Composable
fun EduScreen(
    title: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
    topBarAction: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    background: Color = EduTheme.colors.page,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (title != null || brand) {
                EduTopBar(
                    title = title ?: stringResource(R.string.app_name),
                    onBack = onBack,
                    brand = brand,
                    action = topBarAction,
                )
            }
        },
        bottomBar = { bottomBar?.invoke() },
        content = content,
    )
}

/** Vertically scrollable page body with the design's 24dp gutters. */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = dimen(R.dimen.dimen_24),
    spacing: Dp = dimen(R.dimen.dimen_16),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = horizontalPadding)
            .padding(top = dimen(R.dimen.space_20), bottom = dimen(R.dimen.space_28)),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Pull-to-refresh wrapper for dynamic list/dashboard bodies. Forms and editors
 * must not use this, so a downward drag can never submit or discard input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EduPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/** Navigation tab of the student shell. */
enum class StudentTab(@StringRes val labelRes: Int) {
    BERANDA(R.string.nav_beranda),
    TOPIK(R.string.nav_topik),
    AKUN(R.string.nav_akun),
}

/** Navigation tab of the lecturer shell. */
enum class LecturerTab(@StringRes val labelRes: Int) {
    BERANDA(R.string.nav_beranda),
    TOPIK(R.string.nav_topik),
    PENILAIAN(R.string.nav_penilaian),
    ANALITIK(R.string.nav_analitik),
    AKUN(R.string.nav_akun),
}

/** Navigation tab of the admin shell. */
enum class AdminTab(@StringRes val labelRes: Int) {
    BERANDA(R.string.nav_beranda),
    AKUN(R.string.nav_akun),
    PROFIL(R.string.nav_profil),
}

private data class BottomItem(@StringRes val labelRes: Int, val icon: ImageVector)

@Composable
private fun EduBottomBar(
    items: List<BottomItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(dimen(R.dimen.radius_24))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dimen(R.dimen.space_24), vertical = dimen(R.dimen.space_16)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimen(R.dimen.size_72))
                .shadow(dimen(R.dimen.size_14), shape, clip = false)
                .clip(shape)
                .background(EduTheme.colors.surface, shape)
                .padding(horizontal = dimen(R.dimen.space_8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val label = stringResource(item.labelRes)
                val selected = index == selectedIndex
                val tint = if (selected) EduTheme.colors.lime else EduTheme.colors.mutedSoft
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(dimen(R.dimen.radius_18)))
                        .clickable { onSelect(index) }
                        .padding(vertical = dimen(R.dimen.radius_8)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(dimen(R.dimen.size_22)),
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_4)))
                    Text(text = label, style = EduTheme.type.navLabel.copy(color = tint))
                }
            }
        }
    }
}

@Composable
fun StudentBottomBar(
    selected: StudentTab,
    onSelect: (StudentTab) -> Unit,
) {
    EduBottomBar(
        items = listOf(
            BottomItem(R.string.nav_beranda, EduIcons.Home),
            BottomItem(R.string.nav_topik, EduIcons.Book),
            BottomItem(R.string.nav_akun, EduIcons.Person),
        ),
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(StudentTab.entries[it]) },
    )
}

@Composable
fun LecturerBottomBar(
    selected: LecturerTab,
    onSelect: (LecturerTab) -> Unit,
) {
    EduBottomBar(
        items = listOf(
            BottomItem(R.string.nav_beranda, EduIcons.Home),
            BottomItem(R.string.nav_topik, EduIcons.Book),
            BottomItem(R.string.nav_penilaian, EduIcons.Checklist),
            BottomItem(R.string.nav_analitik, EduIcons.Chart),
            BottomItem(R.string.nav_akun, EduIcons.Person),
        ),
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(LecturerTab.entries[it]) },
    )
}

@Composable
fun AdminBottomBar(
    selected: AdminTab,
    onSelect: (AdminTab) -> Unit,
) {
    EduBottomBar(
        items = listOf(
            BottomItem(R.string.nav_beranda, EduIcons.Home),
            BottomItem(R.string.nav_akun, EduIcons.People),
            BottomItem(R.string.nav_profil, EduIcons.Person),
        ),
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(AdminTab.entries[it]) },
    )
}
