package net.weaver.minimap.map;

final class FakeCaveBlockSource implements CaveBlockSource
{
    int minY = -64;
    int maxY = 320;
    int surface = 64;
    int airFrom = Integer.MIN_VALUE;
    int airTo = Integer.MIN_VALUE;
    int waterFrom = Integer.MIN_VALUE;
    int waterTo = Integer.MIN_VALUE;
    int lavaFrom = Integer.MIN_VALUE;
    int lavaTo = Integer.MIN_VALUE;
    boolean unloaded;
    int solidColor = 0xFF808080;

    @Override
    public int minY()
    {
        return minY;
    }

    @Override
    public int maxY()
    {
        return maxY;
    }

    @Override
    public CaveBlockKind kindAt(int x, int y, int z)
    {
        if (unloaded)
        {
            return CaveBlockKind.NONE;
        }
        if (y < minY || y >= maxY)
        {
            return CaveBlockKind.VOID;
        }
        if (y >= surface)
        {
            return CaveBlockKind.AIR;
        }
        if (between(y, lavaFrom, lavaTo))
        {
            return CaveBlockKind.LAVA;
        }
        if (between(y, waterFrom, waterTo))
        {
            return CaveBlockKind.WATER;
        }
        if (between(y, airFrom, airTo))
        {
            return CaveBlockKind.AIR;
        }
        return CaveBlockKind.SOLID;
    }

    @Override
    public int blockColor(int x, int y, int z)
    {
        return solidColor;
    }

    @Override
    public int surfaceY(int x, int z)
    {
        return unloaded ? Integer.MIN_VALUE : surface;
    }

    private static boolean between(int value, int from, int to)
    {
        return from != Integer.MIN_VALUE && value >= from && value <= to;
    }
}
