package eis.percepts.things;

import eis.iilang.Percept;
import utils.Position;

public class Entity extends Thing {

    private static final String THING_TYPE = "entity";

    protected Entity(Position pos, String details)
    {
        super(pos, THING_TYPE, details);
    }

    public Entity(int x, int y, String details)
    {
        this(new Position(x, y), details);
    }

    @Override
    public Thing clone() {
        return new Entity(this.getPosition(), this.getDetails());
    }

    @Override
    public boolean isBlocking() {
        return !isSelf();
    }

    public static boolean IsEntityPercept(String l)
    {
        return l != null && l.equalsIgnoreCase(THING_TYPE);
    }
}
