package dev.rage.anticheat.logic;

/**
 * Ratio of hidden ores (no air or liquid touching them when mined) to stone mined. A player with
 * X-ray tunnels straight to ores, so they mine far less stone per hidden ore than a normal miner.
 * Only the first ore of a vein counts as hidden: once it is mined, the rest of the vein touches air.
 */
public final class XrayTracker {

    private int stone;
    private int hiddenOres;

    public void mineStone() {
        stone++;
    }

    /** Records a hidden ore and returns true if the pattern is now suspicious. */
    public boolean mineHiddenOre(int minOres, double maxOresPerStone) {
        hiddenOres++;
        return hiddenOres >= minOres && ratio() > maxOresPerStone;
    }

    public double ratio() {
        return hiddenOres / (double) Math.max(1, stone);
    }

    public int stone() {
        return stone;
    }

    public int hiddenOres() {
        return hiddenOres;
    }

    public void reset() {
        stone = 0;
        hiddenOres = 0;
    }
}
