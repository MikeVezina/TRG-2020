package eis.percepts.containers;

import eis.iilang.ParameterList;
import eis.iilang.Percept;
import map.Position;
import eis.percepts.attachments.AttachedThing;
import eis.percepts.parsers.PerceptMapperFactory;
import eis.percepts.terrain.Goal;
import eis.percepts.terrain.Obstacle;
import eis.percepts.terrain.Terrain;
import eis.percepts.things.Thing;
import utils.PerceptUtils;

import java.util.*;

public class AgentPerceptContainer extends PerceptContainer {
    private static final String ENERGY_PERCEPT_NAME = "energy";
    private static final String DISABLED_PERCEPT_NAME = "disabled";
    private static final String THING_PERCEPT_NAME = Thing.PERCEPT_NAME;
    private static final String OBSTACLE_PERCEPT_NAME = Obstacle.PERCEPT_NAME;
    private static final String GOAL_PERCEPT_NAME = Goal.PERCEPT_NAME;
    private static final String ATTACHED_PERCEPT_NAME = AttachedThing.ATTACHED_PERCEPT_NAME;
    private static final String LAST_ACTION_PERCEPT_NAME = "lastAction";
    private static final String LAST_ACTION_RESULT_PERCEPT_NAME = "lastActionResult";
    private static final String LAST_ACTION_PARAMS_PERCEPT_NAME = "lastActionParams";

    // Percept names that are contained within this container.
    private static final Set<String> VALID_PERCEPT_NAMES = Set.of(ATTACHED_PERCEPT_NAME, ENERGY_PERCEPT_NAME, DISABLED_PERCEPT_NAME, THING_PERCEPT_NAME, OBSTACLE_PERCEPT_NAME,
            GOAL_PERCEPT_NAME, LAST_ACTION_PERCEPT_NAME, LAST_ACTION_RESULT_PERCEPT_NAME, LAST_ACTION_PARAMS_PERCEPT_NAME);

    // Required percept names (all raw percept lists should have these).
    private static final Set<String> REQUIRED_PERCEPT_NAMES = Set.of(LAST_ACTION_PERCEPT_NAME, LAST_ACTION_PARAMS_PERCEPT_NAME, LAST_ACTION_RESULT_PERCEPT_NAME, ENERGY_PERCEPT_NAME, DISABLED_PERCEPT_NAME);

    private SharedPerceptContainer sharedPerceptContainer;

    // Individual agent percepts
    private int energy;
    private boolean disabled;
    private List<Thing> thingList;
    private List<Terrain> terrainList;
    private List<Position> rawAttachments;
    private String lastAction;
    private String lastActionResult;
    private ParameterList lastActionParams;

    @Override
    protected Set<String> getRequiredPerceptNames() {
        return REQUIRED_PERCEPT_NAMES;
    }

    protected AgentPerceptContainer(Map<String, List<Percept>> filteredPerceptMap, SharedPerceptContainer sharedPerceptContainer) {
        super(filteredPerceptMap);
        setFilteredPerceptMap(filteredPerceptMap);
        setSharedPerceptContainer(sharedPerceptContainer);

        // Set Agent info
        setEnergy();
        setDisabled();
        setLastActionInfo();

        // Set Other percept info
        setRawAttachments();
        setThingList();
        setTerrainList();
    }

    public SharedPerceptContainer getSharedPerceptContainer() {
        return sharedPerceptContainer;
    }

    public int getEnergy() {
        return energy;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public List<Thing> getThingList() {
        return thingList;
    }

    public List<Terrain> getTerrainList() {
        return terrainList;
    }

    public String getLastAction() {
        return lastAction;
    }

    public String getLastActionResult() {
        return lastActionResult;
    }

    public ParameterList getLastActionParams() {
        return lastActionParams;
    }

    public List<Position> getRawAttachments() {
        return rawAttachments;
    }

    private void setRawAttachments() {
        List<Percept> attachedPercepts = getFilteredPerceptMap().get(ATTACHED_PERCEPT_NAME);

        rawAttachments = new ArrayList<>();

        if(attachedPercepts == null)
            return;

        for(Percept p : attachedPercepts)
        {
            int x = PerceptUtils.GetNumberParameter(p,0).intValue();
            int y = PerceptUtils.GetNumberParameter(p,1).intValue();

            rawAttachments.add(new Position(x, y));
        }
    }

    private void setSharedPerceptContainer(SharedPerceptContainer sharedPerceptContainer) {
        this.sharedPerceptContainer = sharedPerceptContainer;
    }

    private void setEnergy() {
        this.energy = parseSingleNumberPercept(getFilteredPerceptMap().get(ENERGY_PERCEPT_NAME)).intValue();
    }

    private void setDisabled() {
        this.disabled = parseSingleBooleanPercept(getFilteredPerceptMap().get(DISABLED_PERCEPT_NAME));
    }


    private void setThingList() {
        List<Percept> mappedPerceptList = getFilteredPerceptMap().get(THING_PERCEPT_NAME);
        this.thingList = PerceptMapperFactory.getThingPerceptMapper().mapAllPercepts(mappedPerceptList);
    }

    private void setTerrainList() {
        // Terrain list is an aggregation of perceived terrain (i.e. goals and obstacles). Free Spaces and forbidden cells are not explicitly perceived.
        List<Percept> terrainPercepts = new ArrayList<>();
        terrainPercepts.addAll(getFilteredPerceptMap().getOrDefault(OBSTACLE_PERCEPT_NAME, new ArrayList<>()));
        terrainPercepts.addAll(getFilteredPerceptMap().getOrDefault(GOAL_PERCEPT_NAME, new ArrayList<>()));

        this.terrainList = PerceptMapperFactory.getTerrainPerceptMapper().mapAllPercepts(terrainPercepts);
    }

    private void setLastActionInfo() {

        this.lastAction = parseSingleStringPercept(getFilteredPerceptMap().get(LAST_ACTION_PERCEPT_NAME));
        this.lastActionResult = parseSingleStringPercept(getFilteredPerceptMap().get(LAST_ACTION_RESULT_PERCEPT_NAME));
        this.lastActionParams = parseSingleParameterListPercept(getFilteredPerceptMap().get(LAST_ACTION_PARAMS_PERCEPT_NAME));
    }

    public static AgentPerceptContainer parsePercepts(List<Percept> rawPercepts) {
        SharedPerceptContainer sharedPerceptContainer = SharedPerceptContainer.parsePercepts(rawPercepts);
        return new AgentPerceptContainer(filterValidPercepts(rawPercepts, VALID_PERCEPT_NAMES), sharedPerceptContainer);
    }

}
