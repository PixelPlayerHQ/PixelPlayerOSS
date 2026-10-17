package com.lostf1sh.pixelplayeross.presentation.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SegmentedItemShapeDefaults {
    val InnerCornerRadius: Dp = 8.dp
    val LibraryOuterCornerRadius: Dp = 24.dp
    val SongOuterCornerRadius: Dp = LibraryOuterCornerRadius
    val CardOuterCornerRadius: Dp = 16.dp

    val LibraryContainerShape = RoundedCornerShape(LibraryOuterCornerRadius)

    val MiddleShape = RoundedCornerShape(InnerCornerRadius)
    val AlbumArtDefaultShape = RoundedCornerShape(10.dp)

    val SongSingleShape = RoundedCornerShape(SongOuterCornerRadius)
    val SongTopShape = RoundedCornerShape(
        topStart = SongOuterCornerRadius,
        topEnd = SongOuterCornerRadius,
        bottomStart = InnerCornerRadius,
        bottomEnd = InnerCornerRadius
    )
    val SongBottomShape = RoundedCornerShape(
        topStart = InnerCornerRadius,
        topEnd = InnerCornerRadius,
        bottomStart = SongOuterCornerRadius,
        bottomEnd = SongOuterCornerRadius
    )

    val CardSingleShape = RoundedCornerShape(CardOuterCornerRadius)
    val CardTopShape = RoundedCornerShape(
        topStart = CardOuterCornerRadius,
        topEnd = CardOuterCornerRadius,
        bottomStart = InnerCornerRadius,
        bottomEnd = InnerCornerRadius
    )
    val CardBottomShape = RoundedCornerShape(
        topStart = InnerCornerRadius,
        topEnd = InnerCornerRadius,
        bottomStart = CardOuterCornerRadius,
        bottomEnd = CardOuterCornerRadius
    )
}

/**
 * Segmented shape for an item of a [columns]-wide grid: the grid reads as one rectangle with
 * only its four outer corners rounded by [outerRadius]; every other corner gets [innerRadius].
 *
 * The bottom edge is per column, so with a partial last row the item above the empty cell
 * still closes its column and keeps the rectangle's bottom corner. A single item is rounded
 * on all four corners.
 */
fun getSegmentedGridItemShape(
    index: Int,
    count: Int,
    columns: Int,
    outerRadius: Dp,
    innerRadius: Dp = SegmentedItemShapeDefaults.InnerCornerRadius
): RoundedCornerShape {
    if (count <= 1 || columns <= 0) return RoundedCornerShape(outerRadius)
    val column = index % columns
    val isTop = index < columns
    val isBottom = index + columns >= count
    val isLeft = column == 0
    val isRight = column == columns - 1
    return RoundedCornerShape(
        topStart = if (isTop && isLeft) outerRadius else innerRadius,
        topEnd = if (isTop && isRight) outerRadius else innerRadius,
        bottomStart = if (isBottom && isLeft) outerRadius else innerRadius,
        bottomEnd = if (isBottom && isRight) outerRadius else innerRadius
    )
}

/**
 * Returns a [RoundedCornerShape] creating a segmented look for list items.
 *
 * - Single item (or count <= 1): all corners get [outerRadius].
 * - First item (index == 0): top corners get [outerRadius], bottom corners get [innerRadius].
 * - Last item (index == count - 1): top corners get [innerRadius], bottom corners get [outerRadius].
 * - Middle items: all corners get [innerRadius].
 *
 * The default radii return shared shape instances, so list items don't allocate a new shape
 * per composition.
 */
fun getSegmentedItemShape(
    index: Int,
    count: Int,
    outerRadius: Dp = SegmentedItemShapeDefaults.SongOuterCornerRadius,
    innerRadius: Dp = SegmentedItemShapeDefaults.InnerCornerRadius
): RoundedCornerShape {
    val usesDefaultInner = innerRadius == SegmentedItemShapeDefaults.InnerCornerRadius
    if (count <= 1) {
        return when (outerRadius) {
            SegmentedItemShapeDefaults.SongOuterCornerRadius -> SegmentedItemShapeDefaults.SongSingleShape
            SegmentedItemShapeDefaults.CardOuterCornerRadius -> SegmentedItemShapeDefaults.CardSingleShape
            else -> RoundedCornerShape(outerRadius)
        }
    }
    val isFirst = index == 0
    val isLast = index == count - 1

    return when {
        isFirst -> when {
            usesDefaultInner && outerRadius == SegmentedItemShapeDefaults.SongOuterCornerRadius ->
                SegmentedItemShapeDefaults.SongTopShape
            usesDefaultInner && outerRadius == SegmentedItemShapeDefaults.CardOuterCornerRadius ->
                SegmentedItemShapeDefaults.CardTopShape
            else -> RoundedCornerShape(
                topStart = outerRadius,
                topEnd = outerRadius,
                bottomStart = innerRadius,
                bottomEnd = innerRadius
            )
        }
        isLast -> when {
            usesDefaultInner && outerRadius == SegmentedItemShapeDefaults.SongOuterCornerRadius ->
                SegmentedItemShapeDefaults.SongBottomShape
            usesDefaultInner && outerRadius == SegmentedItemShapeDefaults.CardOuterCornerRadius ->
                SegmentedItemShapeDefaults.CardBottomShape
            else -> RoundedCornerShape(
                topStart = innerRadius,
                topEnd = innerRadius,
                bottomStart = outerRadius,
                bottomEnd = outerRadius
            )
        }
        usesDefaultInner -> SegmentedItemShapeDefaults.MiddleShape
        else -> RoundedCornerShape(innerRadius)
    }
}

/**
 * Clips content to [shape] by clipping the canvas with its path, ensuring every corner
 * (including non-uniform corners) is clipped correctly without relying on RenderNode outline clipping.
 */
fun Modifier.clipToShapePath(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
    }
    onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}
