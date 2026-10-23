package br.com.auron.ui

import org.junit.Assert.*
import org.junit.Test

class OpeningCameraTest {
    @Test fun closeCentersChestInPortraitAndLandscape() {
        listOf(800f to 1200f, 1280f to 800f).forEach { (width, height) ->
            val frame = OpeningCamera.frame(width, height, 0f)
            assertTrue(frame.scale > 1f)
            assertEquals(width / 2f, frame.pivotX * width + frame.translationX, 0.01f)
            assertEquals(height / 2f, frame.pivotY * height + frame.translationY, 0.01f)
            val fit = minOf(width / OpeningCamera.IMAGE_WIDTH, height / OpeningCamera.IMAGE_HEIGHT)
            val pictureWidth = OpeningCamera.IMAGE_WIDTH * fit
            val pictureHeight = OpeningCamera.IMAGE_HEIGHT * fit
            val left = width / 2f - pictureWidth * OpeningCamera.CHEST_X * frame.scale
            val top = height / 2f - pictureHeight * OpeningCamera.CHEST_Y * frame.scale
            assertTrue(left <= 0f && top <= 0f)
            assertTrue(left + pictureWidth * frame.scale >= width)
            assertTrue(top + pictureHeight * frame.scale >= height)
        }
    }

    @Test fun finalFrameFitsTheWholeUncroppedArtwork() {
        listOf(800f to 1200f, 1280f to 800f).forEach { (width, height) ->
            val frame = OpeningCamera.frame(width, height, 1f)
            assertEquals(1f, frame.scale, 0.0001f)
            assertEquals(0f, frame.translationX, 0.0001f)
            assertEquals(0f, frame.translationY, 0.0001f)
        }
    }

    @Test fun cameraOnlyZoomsOutAndInvalidViewportIsSafe() {
        val scales = (0..100).map { OpeningCamera.frame(1280f, 800f, it / 100f).scale }
        assertTrue(scales.zipWithNext().all { (a, b) -> b <= a && b >= 1f })
        assertEquals(1f, OpeningCamera.frame(0f, Float.NaN, 0f).scale, 0f)
        assertEquals(1f, OpeningCamera.frame(800f, 1200f, Float.NaN).scale, 0f)
    }
}
