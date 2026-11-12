package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveAutoYTest
{
    @Test
    void prefersWalkableCaveFloorsBelowTheSurface()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.airFrom = 20;
        source.airTo = 30;

        CaveAutoY scorer = new CaveAutoY(64, -64, 64);
        assertTrue(scorer.tick(source, 0, 0, Integer.MAX_VALUE));

        assertEquals(20, scorer.result());
        assertTrue(scorer.result() <= 56);
        assertTrue(scorer.bestScore() > 0);
    }

    @Test
    void automaticSelectionIsDeterministicAcrossBudgets()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.airFrom = 12;
        source.airTo = 28;

        CaveAutoY whole = new CaveAutoY(80, -64, 70);
        whole.tick(source, 100, -100, Integer.MAX_VALUE);

        CaveAutoY incremental = new CaveAutoY(80, -64, 70);
        int ticks = 0;
        while (!incremental.tick(source, 100, -100, 1))
        {
            ticks++;
            assertTrue(ticks < 100);
        }

        assertEquals(whole.result(), incremental.result());
        assertEquals(whole.bestScore(), incremental.bestScore());
        assertTrue(incremental.done());
    }

    @Test
    void neverSelectsTheSurfaceOrAbove()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.surface = 20;

        CaveAutoY scorer = new CaveAutoY(20, -64, 20);
        scorer.tick(source, 0, 0, Integer.MAX_VALUE);

        assertTrue(scorer.result() <= 12);
        assertTrue(scorer.result() < 20);
    }

    @Test
    void penalizesLavaComparedWithDryCaves()
    {
        FakeCaveBlockSource dry = new FakeCaveBlockSource();
        dry.airFrom = 20;
        dry.airTo = 30;

        FakeCaveBlockSource lava = new FakeCaveBlockSource();
        lava.airFrom = 20;
        lava.airTo = 30;
        lava.lavaFrom = 20;
        lava.lavaTo = 20;

        int dryScore = CaveAutoY.score(dry, 20, 0, 0);
        int lavaScore = CaveAutoY.score(lava, 20, 0, 0);

        assertTrue(dryScore > lavaScore);

        CaveAutoY dryScorer = new CaveAutoY(64, -64, 64);
        dryScorer.tick(dry, 0, 0, Integer.MAX_VALUE);
        CaveAutoY lavaScorer = new CaveAutoY(64, -64, 64);
        lavaScorer.tick(lava, 0, 0, Integer.MAX_VALUE);
        assertTrue(dryScorer.bestScore() > 0);
        assertTrue(lavaScorer.result() != 20);
    }

    @Test
    void unavailableColumnsFallBackBelowTheSurface()
    {
        FakeCaveBlockSource source = new FakeCaveBlockSource();
        source.unloaded = true;

        CaveAutoY scorer = new CaveAutoY(64, -64, 64);
        scorer.tick(source, 0, 0, Integer.MAX_VALUE);

        assertEquals(Integer.MIN_VALUE, scorer.bestScore());
        assertEquals(56, scorer.fallbackY());
        assertEquals(scorer.fallbackY(), scorer.result());
    }

    @Test
    void candidatesStayInsideTheWorldAndDepthBudget()
    {
        CaveAutoY scorer = new CaveAutoY(300, -64, 310);

        assertEquals(24, scorer.candidateCount());
        assertTrue(scorer.result() <= 296);
        CaveAutoY shallow = new CaveAutoY(-60, -64, 64);
        assertEquals(0, shallow.candidateCount());
    }
}
