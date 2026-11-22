package com.eduvark.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduFontFamily
import com.eduvark.app.ui.theme.EduText

private val TopBarTitle = TextStyle(
    fontFamily = EduFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp,
    color = EduColors.Ink,
)

private val BrandTitle = TextStyle(
    fontFamily = EduFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.3).sp,
    color = EduColors.Ink,
)

/** White top bar with an optional back chevron, matching the designs. */
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
            .background(EduColors.Surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = EduIcons.ChevronLeft,
                        contentDescription = "Kembali",
                        tint = EduColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = if (brand) BrandTitle else TopBarTitle,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        Hairline()
    }
}

/**
 * Standard screen scaffold: light page background, white chrome, edge-to-edge safe.
 */
@Composable
fun EduScreen(
    title: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
    topBarAction: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    background: Color = EduColors.Page,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (title != null || brand) {
                EduTopBar(
                    title = title ?: "EduVARK",
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
    horizontalPadding: Dp = 24.dp,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Navigation tab of the student shell. */
enum class StudentTab(val label: String) { BERANDA("Beranda"), TOPIK("Topik"), AKUN("Akun") }

/** Navigation tab of the lecturer shell. */
enum class LecturerTab(val label: String) {
    BERANDA("Beranda"),
    TOPIK("Topik"),
    PENILAIAN("Penilaian"),
    ANALITIK("Analitik"),
    AKUN("Akun"),
}

/** Navigation tab of the admin shell. */
enum class AdminTab(val label: String) { BERANDA("Beranda"), AKUN("Akun"), PROFIL("Profil") }

private data class BottomItem(val label: String, val icon: ImageVector)

@Composable
private fun EduBottomBar(
    items: List<BottomItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EduColors.Surface)
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint = if (selected) EduColors.Primary else EduColors.MutedSoft
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = item.label, style = EduText.NavLabel.copy(color = tint))
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
            BottomItem("Beranda", EduIcons.Home),
            BottomItem("Topik", EduIcons.Book),
            BottomItem("Akun", EduIcons.Person),
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
            BottomItem("Beranda", EduIcons.Home),
            BottomItem("Topik", EduIcons.Book),
            BottomItem("Penilaian", EduIcons.Checklist),
            BottomItem("Analitik", EduIcons.Chart),
            BottomItem("Akun", EduIcons.Person),
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
            BottomItem("Beranda", EduIcons.Home),
            BottomItem("Akun", EduIcons.People),
            BottomItem("Profil", EduIcons.Person),
        ),
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(AdminTab.entries[it]) },
    )
}
