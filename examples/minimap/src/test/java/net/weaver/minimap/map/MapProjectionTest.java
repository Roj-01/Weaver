package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MapProjectionTest
{
    @Test
    void centerMapsToViewportCenter()
    {
        MapProjection projection = new MapProjection(100.0D, -50.0D, 10, 20, 100, 2.0D);

        assertEquals(60.0D, projection.screenX(100.0D), 0.0001D);
        assertEquals(70.0D, projection.screenY(-50.0D), 0.0001D);
        assertEquals(100.0D, projection.worldX(60.0D), 0.0001D);
        assertEquals(-50.0D, projection.worldZ(70.0D), 0.0001D);
    }

    @Test
    void edgesMapToViewportEdges()
    {
        MapProjection projection = new MapProjection(0.0D, 0.0D, 10, 20, 100, 2.0D);

        assertEquals(10.0D, projection.screenX(projection.minWorldX()), 0.0001D);
        assertEquals(110.0D, projection.screenX(projection.maxWorldX()), 0.0001D);
        assertEquals(20.0D, projection.screenY(projection.minWorldZ()), 0.0001D);
        assertEquals(120.0D, projection.screenY(projection.maxWorldZ()), 0.0001D);
        assertEquals(-100.0D, projection.minWorldX(), 0.0001D);
        assertEquals(100.0D, projection.maxWorldX(), 0.0001D);
    }

    @Test
    void roundTripsNegativeCoordinates()
    {
        MapProjection projection = new MapProjection(-1234.5D, 987.25D, 4, 8, 160, 0.5D);

        double screenX = projection.screenX(-1300.75D);
        double screenY = projection.screenY(1100.0D);

        assertEquals(-1300.75D, projection.worldX(screenX), 0.0001D);
        assertEquals(1100.0D, projection.worldZ(screenY), 0.0001D);
    }

    @Test
    void regionSizeUsesCurrentScale()
    {
        assertEquals(128, new MapProjection(0.0D, 0.0D, 0, 0, 100, 2.0D).regionScreenSize());
        assertEquals(512, new MapProjection(0.0D, 0.0D, 0, 0, 100, 0.5D).regionScreenSize());
    }

    @Test
    void copyChangesOnlyTheRequestedAxis()
    {
        MapProjection projection = new MapProjection(5.0D, 6.0D, 2, 3, 80, 1.5D);
        MapProjection moved = projection.withCenter(20.0D, 21.0D);
        MapProjection zoomed = moved.withZoom(3.0D);

        assertEquals(20.0D, moved.centerX());
        assertEquals(21.0D, moved.centerZ());
        assertEquals(3.0D, zoomed.zoom());
        assertEquals(80, zoomed.size());
        assertEquals(2, zoomed.originX());
    }

    @Test
    void rejectsInvalidGeometry()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new MapProjection(0.0D, 0.0D, 0, 0, 0, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new MapProjection(0.0D, 0.0D, 0, 0, 64, 0.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new MapProjection(Double.NaN, 0.0D, 0, 0, 64, 1.0D));
    }
}
