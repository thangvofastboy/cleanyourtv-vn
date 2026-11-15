package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

public class StarTest {

    private static final float EPS = 1e-4f;

    @Test
    public void tenPointsAlternatingOuterAndInnerStartingAtTheTop() {
        float[] p = Star.points(10f, 20f, 8f, 3f, 0f);
        assertEquals(20, p.length);
        assertEquals(10f, p[0], EPS);
        assertEquals(12f, p[1], EPS);
        for (int i = 0; i < 10; i++) {
            float r = (float) Math.hypot(p[2 * i] - 10f, p[2 * i + 1] - 20f);
            assertEquals(i % 2 == 0 ? 8f : 3f, r, EPS);
        }
    }

    @Test
    public void aTurnOfOnePointGivesTheSameStar() {
        float[] a = Star.points(0f, 0f, 1f, 0.4f, 0f);
        float[] b = Star.points(0f, 0f, 1f, 0.4f, 72f);
        for (int i = 0; i < 20; i++) assertEquals(a[(i + 4) % 20], b[i], EPS);
    }

    @Test
    public void flagIsThreeByTwoCentredWithTheStarAFifthOfItsLength() {
        float[] f = Star.flag(24f, 24f, 1f);
        assertEquals(24f, f[2], EPS);
        assertEquals(16f, f[3], EPS);
        assertEquals(0f, f[0], EPS);
        assertEquals(4f, f[1], EPS);
        assertEquals(24f / 5f, f[4], EPS);
        float[] tall = Star.flag(30f, 10f, 1f);
        assertEquals(15f, tall[2], EPS);
        assertEquals(10f, tall[3], EPS);
        assertEquals(7.5f, tall[0], EPS);
    }

    @Test
    public void theReusedArraysGiveTheSameShapes() {
        float[] out = new float[20];
        assertSame(out, Star.points(out, 3f, 4f, 5f, 2f, 10f));
        assertArrayEquals(Star.points(3f, 4f, 5f, 2f, 10f), out, 0f);
        float[] box = new float[5];
        assertSame(box, Star.flag(box, 30f, 10f, 0.9f));
        assertArrayEquals(Star.flag(30f, 10f, 0.9f), box, 0f);
    }
}
