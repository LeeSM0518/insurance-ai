package com.insuranceai.search.service

import kotlin.test.Test
import kotlin.test.assertEquals

class MinMaxNormalizerTest {
    @Test
    fun `최소값은 0으로, 최대값은 1로 정규화된다`() {
        val normalizer = MinMaxNormalizer(listOf(0.2f, 0.5f, 0.8f))
        assertEquals(0.0f, normalizer.normalize(0.2f), 0.001f)
        assertEquals(0.5f, normalizer.normalize(0.5f), 0.001f)
        assertEquals(1.0f, normalizer.normalize(0.8f), 0.001f)
    }

    @Test
    fun `모든 값이 동일하면 1을 반환한다`() {
        val normalizer = MinMaxNormalizer(listOf(0.5f, 0.5f, 0.5f))
        assertEquals(1.0f, normalizer.normalize(0.5f), 0.001f)
    }

    @Test
    fun `빈 리스트면 1을 반환한다`() {
        val normalizer = MinMaxNormalizer(emptyList())
        assertEquals(1.0f, normalizer.normalize(0.5f), 0.001f)
    }

    @Test
    fun `단일 값이면 1을 반환한다`() {
        val normalizer = MinMaxNormalizer(listOf(0.7f))
        assertEquals(1.0f, normalizer.normalize(0.7f), 0.001f)
    }
}
