package com.eduvark.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thin outline icon set that mirrors the iconography of the UI designs.
 * Drawn locally (1.8dp rounded strokes) instead of pulling the heavy
 * material-icons-extended artifact.
 */
object EduIcons {
    private const val STROKE = 1.8f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    val ChevronLeft: ImageVector = icon("chevronLeft") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
        }
    }

    val ChevronRight: ImageVector = icon("chevronRight") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
        }
    }

    val ChevronDown: ImageVector = icon("chevronDown") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    val ArrowRight: ImageVector = icon("arrowRight") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 12f); lineTo(19f, 12f)
            moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
        }
    }

    val ArrowUpRight: ImageVector = icon("arrowUpRight") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 17f); lineTo(17f, 7f)
            moveTo(9f, 7f); lineTo(17f, 7f); lineTo(17f, 15f)
        }
    }

    val ArrowUp: ImageVector = icon("arrowUp") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    val Plus: ImageVector = icon("plus") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Check: ImageVector = icon("check") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    val Close: ImageVector = icon("close") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Search: ImageVector = icon("search") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(11f, 4f)
            curveTo(14.9f, 4f, 18f, 7.1f, 18f, 11f)
            curveTo(18f, 14.9f, 14.9f, 18f, 11f, 18f)
            curveTo(7.1f, 18f, 4f, 14.9f, 4f, 11f)
            curveTo(4f, 7.1f, 7.1f, 4f, 11f, 4f)
            close()
            moveTo(16f, 16f); lineTo(20.5f, 20.5f)
        }
    }

    val More: ImageVector = icon("more") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 10.4f)
            curveTo(12.9f, 10.4f, 13.6f, 11.1f, 13.6f, 12f)
            curveTo(13.6f, 12.9f, 12.9f, 13.6f, 12f, 13.6f)
            curveTo(11.1f, 13.6f, 10.4f, 12.9f, 10.4f, 12f)
            curveTo(10.4f, 11.1f, 11.1f, 10.4f, 12f, 10.4f)
            close()
            moveTo(19f, 10.4f)
            curveTo(19.9f, 10.4f, 20.6f, 11.1f, 20.6f, 12f)
            curveTo(20.6f, 12.9f, 19.9f, 13.6f, 19f, 13.6f)
            curveTo(18.1f, 13.6f, 17.4f, 12.9f, 17.4f, 12f)
            curveTo(17.4f, 11.1f, 18.1f, 10.4f, 19f, 10.4f)
            close()
            moveTo(5f, 10.4f)
            curveTo(5.9f, 10.4f, 6.6f, 11.1f, 6.6f, 12f)
            curveTo(6.6f, 12.9f, 5.9f, 13.6f, 5f, 13.6f)
            curveTo(4.1f, 13.6f, 3.4f, 12.9f, 3.4f, 12f)
            curveTo(3.4f, 11.1f, 4.1f, 10.4f, 5f, 10.4f)
            close()
        }
    }

    val Home: ImageVector = icon("home") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f)
            moveTo(6f, 10f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 10f)
            moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f)
        }
    }

    val Book: ImageVector = icon("book") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 6.5f)
            curveTo(10.3f, 4.9f, 7.4f, 4.4f, 4f, 5f)
            lineTo(4f, 18.6f)
            curveTo(7.4f, 18f, 10.3f, 18.5f, 12f, 20.1f)
            curveTo(13.7f, 18.5f, 16.6f, 18f, 20f, 18.6f)
            lineTo(20f, 5f)
            curveTo(16.6f, 4.4f, 13.7f, 4.9f, 12f, 6.5f)
            close()
            moveTo(12f, 6.5f); lineTo(12f, 20.1f)
        }
    }

    val Person: ImageVector = icon("person") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 4.2f)
            curveTo(14.1f, 4.2f, 15.8f, 5.9f, 15.8f, 8f)
            curveTo(15.8f, 10.1f, 14.1f, 11.8f, 12f, 11.8f)
            curveTo(9.9f, 11.8f, 8.2f, 10.1f, 8.2f, 8f)
            curveTo(8.2f, 5.9f, 9.9f, 4.2f, 12f, 4.2f)
            close()
            moveTo(4.8f, 20f)
            curveTo(4.8f, 16.4f, 8f, 14.2f, 12f, 14.2f)
            curveTo(16f, 14.2f, 19.2f, 16.4f, 19.2f, 20f)
        }
    }

    val People: ImageVector = icon("people") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 5.2f)
            curveTo(10.8f, 5.2f, 12.2f, 6.6f, 12.2f, 8.4f)
            curveTo(12.2f, 10.2f, 10.8f, 11.6f, 9f, 11.6f)
            curveTo(7.2f, 11.6f, 5.8f, 10.2f, 5.8f, 8.4f)
            curveTo(5.8f, 6.6f, 7.2f, 5.2f, 9f, 5.2f)
            close()
            moveTo(3f, 19f)
            curveTo(3f, 15.9f, 5.6f, 14f, 9f, 14f)
            curveTo(12.4f, 14f, 15f, 15.9f, 15f, 19f)
            moveTo(15.5f, 6.1f)
            curveTo(17f, 6.4f, 18f, 7.6f, 18f, 9.1f)
            curveTo(18f, 10.6f, 17f, 11.8f, 15.5f, 12.1f)
            moveTo(17.4f, 14.3f)
            curveTo(19.6f, 14.9f, 21f, 16.5f, 21f, 19f)
        }
    }

    val Checklist: ImageVector = icon("checklist") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(5f, 4.5f)
            lineTo(19f, 4.5f)
            curveTo(19.6f, 4.5f, 20f, 4.9f, 20f, 5.5f)
            lineTo(20f, 18.5f)
            curveTo(20f, 19.1f, 19.6f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            curveTo(4.4f, 19.5f, 4f, 19.1f, 4f, 18.5f)
            lineTo(4f, 5.5f)
            curveTo(4f, 4.9f, 4.4f, 4.5f, 5f, 4.5f)
            close()
            moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
        }
    }

    val Chart: ImageVector = icon("chart") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(7f, 20f); lineTo(7f, 13f)
            moveTo(12f, 20f); lineTo(12f, 6f)
            moveTo(17f, 20f); lineTo(17f, 10f)
        }
    }

    val Play: ImageVector = icon("play") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5.5f)
            lineTo(19f, 12f)
            lineTo(8f, 18.5f)
            close()
        }
    }

    val Expand: ImageVector = icon("expand") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        }
    }

    val Logout: ImageVector = icon("logout") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(14f, 4.5f); lineTo(6f, 4.5f); lineTo(6f, 19.5f); lineTo(14f, 19.5f)
            moveTo(17f, 12f); lineTo(10f, 12f)
            moveTo(14f, 9f); lineTo(17f, 12f); lineTo(14f, 15f)
        }
    }

    val Eye: ImageVector = icon("eye") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.5f, 12f)
            curveTo(5f, 7.5f, 8.2f, 5.5f, 12f, 5.5f)
            curveTo(15.8f, 5.5f, 19f, 7.5f, 21.5f, 12f)
            curveTo(19f, 16.5f, 15.8f, 18.5f, 12f, 18.5f)
            curveTo(8.2f, 18.5f, 5f, 16.5f, 2.5f, 12f)
            close()
            moveTo(12f, 9f)
            curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f)
            curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f)
            curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f)
            curveTo(9f, 10.3f, 10.3f, 9f, 12f, 9f)
            close()
        }
    }

    val EyeOff: ImageVector = icon("eyeOff") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 3f); lineTo(21f, 21f)
            moveTo(10.6f, 6f)
            curveTo(11.1f, 5.9f, 11.5f, 5.9f, 12f, 5.9f)
            curveTo(15.8f, 5.9f, 19f, 7.9f, 21.5f, 12f)
            curveTo(20.6f, 13.6f, 19.5f, 14.8f, 18.2f, 15.7f)
            moveTo(6.2f, 7.4f)
            curveTo(4.7f, 8.5f, 3.4f, 10f, 2.5f, 12f)
            curveTo(5f, 16.5f, 8.2f, 18.5f, 12f, 18.5f)
            curveTo(13.6f, 18.5f, 15.1f, 18.1f, 16.4f, 17.3f)
            moveTo(9.9f, 9.9f)
            curveTo(10.5f, 9.3f, 11.2f, 9f, 12f, 9f)
            curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f)
        }
    }

    val Info: ImageVector = icon("info") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 4f)
            curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
            curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
            close()
            moveTo(12f, 11f); lineTo(12f, 16f)
            moveTo(12f, 8f); lineTo(12f, 8.01f)
        }
    }
}
