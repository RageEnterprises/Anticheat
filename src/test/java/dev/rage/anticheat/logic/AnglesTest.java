package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnglesTest {

    @Test
    void sameDirectionIsZero() {
        assertEquals(0, Angles.between(1, 0, 0, 3, 0, 0), 1e-9);
    }

    @Test
    void perpendicularAndOpposite() {
        assertEquals(90, Angles.between(1, 0, 0, 0, 0, 1), 1e-9);
        assertEquals(180, Angles.between(1, 0, 0, -2, 0, 0), 1e-9);
    }

    @Test
    void zeroVectorIsZero() {
        assertEquals(0, Angles.between(0, 0, 0, 1, 0, 0), 1e-9);
    }
}
