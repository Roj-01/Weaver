package net.weaver.minimap.map;

import java.util.Objects;

/**
 * Incremental scorer that chooses a cave Y for automatic cave mode.
 *
 * <p>Candidates start just below the player and move downward in steps of four
 * blocks, never above the lowest nearby surface minus eight blocks, so a
 * surface layer can never be selected. Each candidate samples an 9x9 grid of
 * columns in a 64-block square around the player. Walkable air floors score
 * positively; solid rock, fluids, void, and columns close to the surface score
 * negatively. Ties prefer the Y closest to the player.</p>
 *
 * <p>The scorer is deterministic and spreads candidate evaluation across
 * client ticks. Instances are single-threaded.</p>
 */
public final class CaveAutoY
{
    /** Horizontal stride between sampled columns. */
    public static final int SAMPLE_STEP = 8;

    /** Horizontal radius of the sampled square. */
    public static final int SAMPLE_RADIUS = 32;

    /** Vertical stride between candidate Y levels. */
    public static final int Y_STEP = 4;

    /** How far below the player candidates may go. */
    public static final int MAX_DEPTH = 96;

    private static final int SURFACE_MARGIN = 8;
    private static final int NEAR_SURFACE_MARGIN = 6;

    private final int playerY;
    private final int surfaceMinY;
    private final int[] candidates;
    private final int fallbackY;
    private int nextCandidate;
    private int bestY = Integer.MIN_VALUE;
    private int bestScore = Integer.MIN_VALUE;
    private int bestDistance = Integer.MAX_VALUE;

    /**
     * Creates a scorer.
     *
     * @param playerY     the player block Y
     * @param minY        the dimension minimum Y
     * @param surfaceMinY the lowest surface height near the player
     */
    public CaveAutoY(int playerY, int minY, int surfaceMinY)
    {
        this.playerY = playerY;
        this.surfaceMinY = surfaceMinY;
        this.candidates = buildCandidates(playerY, minY, surfaceMinY);
        int cap = Math.min(playerY - 4, surfaceMinY - SURFACE_MARGIN);
        this.fallbackY = Math.max(minY + 2, cap);
    }

    /**
     * Computes the score of one candidate Y. Higher is better.
     *
     * @param source  the block source, never {@code null}
     * @param y       the candidate Y
     * @param centerX the sample center X
     * @param centerZ the sample center Z
     * @return the score, or {@link Integer#MIN_VALUE} when nothing is loaded
     */
    public static int score(CaveBlockSource source, int y, int centerX, int centerZ)
    {
        Objects.requireNonNull(source, "source");
        long total = 0L;
        int samples = 0;
        int fluids = 0;
        for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP)
        {
            int z = centerZ + dz;
            for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP)
            {
                int x = centerX + dx;
                int surface = source.surfaceY(x, z);
                if (surface == Integer.MIN_VALUE)
                {
                    continue;
                }
                samples++;
                if (y >= surface - NEAR_SURFACE_MARGIN)
                {
                    total -= 30L;
                    continue;
                }
                CaveBlockKind kind = source.kindAt(x, y, z);
                switch (kind)
                {
                    case AIR ->
                    {
                        CaveBlockKind above = source.kindAt(x, y + 1, z);
                        CaveBlockKind below = source.kindAt(x, y - 1, z);
                        if (above != CaveBlockKind.AIR)
                        {
                            total -= 2L;
                        }
                        else if (below == CaveBlockKind.SOLID)
                        {
                            total += 12L;
                        }
                        else if (below == CaveBlockKind.LAVA)
                        {
                            total -= 4L;
                        }
                        else if (below == CaveBlockKind.WATER)
                        {
                            total += 1L;
                        }
                        else
                        {
                            total += 3L;
                        }
                    }
                    case SOLID -> total -= 2L;
                    case WATER -> { total -= 18L; fluids++; }
                    case LAVA -> { total -= 30L; fluids++; }
                    case VOID -> total -= 12L;
                    case NONE -> total -= 2L;
                }
            }
        }
        if (samples == 0) { return Integer.MIN_VALUE; }
        if (fluids * 4 > samples) { return -1000; }
        return (int) (total * 100L / samples);
    }

    /**
     * Evaluates up to {@code budget} candidates.
     *
     * @param source  the block source, never {@code null}
     * @param centerX the sample center X
     * @param centerZ the sample center Z
     * @param budget  the maximum candidates to evaluate
     * @return {@code true} when all candidates have been evaluated
     */
    public boolean tick(CaveBlockSource source, int centerX, int centerZ, int budget)
    {
        Objects.requireNonNull(source, "source");
        int remaining = Math.max(0, budget);
        while (remaining > 0 && nextCandidate < candidates.length)
        {
            int y = candidates[nextCandidate];
            nextCandidate++;
            remaining--;
            int score = score(source, y, centerX, centerZ);
            int distance = Math.abs(y - playerY);
            if (score > bestScore || (score == bestScore && distance < bestDistance))
            {
                bestScore = score;
                bestY = y;
                bestDistance = distance;
            }
        }
        return done();
    }

    /** Returns whether every candidate has been evaluated. */
    public boolean done()
    {
        return nextCandidate >= candidates.length;
    }

    /**
     * Returns the selected Y. Before completion this is the best candidate seen
     * so far; when no candidate scored at all it is the safe fallback.
     *
     * @return the selected Y, or {@link Integer#MIN_VALUE} when there are no
     *         candidates
     */
    public int result()
    {
        if (candidates.length == 0)
        {
            return Integer.MIN_VALUE;
        }
        return bestY == Integer.MIN_VALUE ? fallbackY : bestY;
    }

    /** Returns the best score seen so far, or {@link Integer#MIN_VALUE}. */
    public int bestScore()
    {
        return bestScore;
    }

    /** Returns the number of candidate Y levels. */
    public int candidateCount()
    {
        return candidates.length;
    }

    /** Returns the lowest surface height the scorer was built with. */
    public int surfaceMinY()
    {
        return surfaceMinY;
    }

    /**
     * Returns a Y that is guaranteed to be below the sampled surface, used when
     * no candidate produced a usable score.
     */
    public int fallbackY()
    {
        return fallbackY;
    }

    private static int[] buildCandidates(int playerY, int minY, int surfaceMinY)
    {
        int cap = Math.min(playerY - 4, surfaceMinY - SURFACE_MARGIN);
        int floor = Math.max(minY + 2, playerY - MAX_DEPTH);
        if (cap < minY + 2 || cap < floor)
        {
            return new int[0];
        }
        int count = (cap - floor) / Y_STEP + 1;
        int[] values = new int[count];
        for (int index = 0; index < count; index++)
        {
            values[index] = cap - index * Y_STEP;
        }
        return values;
    }
}
