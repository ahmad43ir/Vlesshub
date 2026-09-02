package com.chobgroup.admin_vlesshub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-built icons for the admin app — minimal set, no extended library.
 * The Delete icon is unique to this admin app.
 */
object AdminIcons {

    /** Standard Material "delete" glyph — the admin-only remove button. */
    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2L16,7L6,7v12zM19,4h-3.5l-1,-1h-5l-1,1L5,4v2h14L19,4z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "content_copy" glyph. */
    val ContentCopy: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContentCopy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1z" +
                            "M19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2z" +
                            "M19,21L8,21L8,7h11v14z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "folder" glyph. */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M10,4L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2l16,0c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2l-8,0 -2,-2z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Clean speedometer gauge — ping button. */
    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58L5.07,18A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1 10,10 0,0 0,-0.27 -10.44z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material "edit" pencil glyph — rename button. */
    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Material "visibility_off" glyph — hide button. */
    val VisibilityOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VisibilityOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zm4.31,-0.78l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
