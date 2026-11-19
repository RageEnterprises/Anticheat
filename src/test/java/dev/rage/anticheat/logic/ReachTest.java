package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReachTest {

    // A player-sized hitbox at the origin: 0.6 wide, 1.8 tall.
    private static double distance(double x, double y, double z) {
        return Reach.distanceToBox(x, y, z, -0.3, 0, -0.3, 0.3, 1.8, 0.3);
    }

    @Test
    void measuresToNearestFaceNotCentre() {
        assertEquals(2.7, distance(3.0, 1.62, 0), 1e-9);
    }

    @Test
    void measuresToNearestCorner() {
        assertEquals(Math.sqrt(2 * 2 + 2 * 2), distance(2.3, 1.0, 2.3), 1e-9);
    }

    @Test
    void insideBoxIsZero() {
        assertEquals(0, distance(0, 1, 0), 1e-9);
    }
}
