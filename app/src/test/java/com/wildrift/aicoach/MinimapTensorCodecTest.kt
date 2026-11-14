package com.wildrift.aicoach

import com.wildrift.aicoach.vision.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class MinimapTensorCodecTest {
    @Test fun `int8 RGB preserves black white endpoints`() {
        val encoded = MinimapTensorCodec.packRgb(intArrayOf(0x000000, 0xffffff), false, TensorEncoding.INT8, 1f / 255f, -128)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f), MinimapTensorCodec.unpack(encoded, TensorEncoding.INT8, 1f / 255f, -128), .0001f)
    }
    @Test fun `NCHW puts red green blue in separate planes`() {
        val b = MinimapTensorCodec.packRgb(intArrayOf(0xff0000, 0x0000ff), true, TensorEncoding.UINT8, 1f / 255f, 0)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f), MinimapTensorCodec.unpack(b, TensorEncoding.UINT8, 1f / 255f, 0), .0001f)
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid quantization fails explicitly`() {
        MinimapTensorCodec.packRgb(intArrayOf(0), false, TensorEncoding.INT8, 0f, 0)
    }
    @Test fun `normalized coordinates remain normalized`() {
        val values = floatArrayOf(.5f, .25f, .1f, .1f, .1f, .9f, .1f, .1f)
        val detection = MinimapYoloDecoder.decode(values, intArrayOf(1, 1, 8), true).single()
        assertEquals(.5f, detection.xNormalized, .0001f)
        assertEquals(.25f, detection.yNormalized, .0001f)
    }
    @Test fun `NMS suppresses duplicate anchors without merging different classes`() {
        val a = floatArrayOf(.5f, .5f, .2f, .2f, .1f, .9f, 0f, 0f)
        val b = floatArrayOf(.5f, .5f, .2f, .2f, .1f, .8f, 0f, 0f)
        val c = floatArrayOf(.5f, .5f, .2f, .2f, .9f, .1f, 0f, 0f)
        assertEquals(2, MinimapYoloDecoder.decode(a + b + c, intArrayOf(1, 3, 8), true).size)
    }
    @Test fun `nonfinite output never creates a detection`() {
        assertTrue(MinimapYoloDecoder.decode(floatArrayOf(Float.NaN, .5f, .2f, .2f, 0f, .9f, 0f, 0f), intArrayOf(1, 1, 8), true).isEmpty())
    }
}
