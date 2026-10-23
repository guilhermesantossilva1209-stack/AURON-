package br.com.auron.ui

/** Camera coordinates for the unmodified 853×1280 owner-provided illustration. */
internal object OpeningCamera {
    const val IMAGE_WIDTH = 853f
    const val IMAGE_HEIGHT = 1280f
    // Center between the chest emblem and the AURON lettering, not the face.
    const val CHEST_X = 0.545f
    const val CHEST_Y = 0.413f

    data class Frame(
        val scale: Float,
        val translationX: Float,
        val translationY: Float,
        val pivotX: Float,
        val pivotY: Float,
    )

    fun frame(width: Float, height: Float, progress: Float): Frame {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return Frame(1f, 0f, 0f, 0.5f, 0.5f)
        }
        val t = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val fit = minOf(width / IMAGE_WIDTH, height / IMAGE_HEIGHT)
        val pictureWidth = IMAGE_WIDTH * fit
        val pictureHeight = IMAGE_HEIGHT * fit
        val focalX = (width - pictureWidth) / 2f + pictureWidth * CHEST_X
        val focalY = (height - pictureHeight) / 2f + pictureHeight * CHEST_Y
        // Fill the viewport with the chest at first, including landscape tablets.
        val closeScale = maxOf(width / (pictureWidth * 0.36f), height / (pictureHeight * 0.26f))
        return Frame(
            scale = closeScale + (1f - closeScale) * t,
            translationX = (width / 2f - focalX) * (1f - t),
            translationY = (height / 2f - focalY) * (1f - t),
            pivotX = focalX / width,
            pivotY = focalY / height,
        )
    }
}
