package eis.percepts.things;

import utils.Position;

public class Block extends Thing {

    private static final String THING_TYPE = "block";

    protected Block(Position pos, String details)
    {
        super(pos, THING_TYPE, details);
    }

    protected Block(int x, int y, String details)
    {
        this(new Position(x, y), details);
    }


    @Override
    public Thing clone() {
        return new Block(this.getPosition(), this.getDetails());
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    public static boolean IsBlockPercept(String l)
    {
        return l != null && l.equalsIgnoreCase(THING_TYPE);
    }


}