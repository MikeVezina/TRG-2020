package eis.percepts;

import eis.iilang.Percept;
import eis.percepts.terrain.Obstacle;
import eis.percepts.terrain.Terrain;
import eis.percepts.things.Thing;
import utils.Graph;
import utils.PerceptUtils;
import utils.Position;
import utils.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AgentMap {
    private static Logger LOG = Logger.getLogger(AgentMap.class.getName());
    private String agent;
    private static int vision = -1;
    private Graph mapKnowledge;
    private ConcurrentMap<Position, MapPercept> currentStepKnowledge;
    private Map<String, AgentMap> knownAgentMaps;
    private Map<String, Position> translationPositions;
    private Position currentStepAgentPosition;

    public AgentMap(String agent) {
        this.agent = agent;
        this.mapKnowledge = new Graph();
        this.knownAgentMaps = new HashMap<>();
        this.translationPositions = new HashMap<>();
        this.currentStepAgentPosition = new Position();
    }

    public static void SetVision(int vision) {
        AgentMap.vision = vision;
    }

    public static int GetVision() {
        return vision;
    }

    public void prepareCurrentStep(long currentStep, Position agentPosition) {
        currentStepKnowledge = new ConcurrentHashMap<>();
        currentStepAgentPosition = agentPosition;

        // Generates positions for the current agent's perception
        for (Position p : new Utils.Area(agentPosition, vision)) {
            currentStepKnowledge.put(p, new MapPercept(p, agent, currentStep));

        }

    }

    public void updateMap(Percept p) {
        if (!Thing.canParse(p) && !Obstacle.canParse(p))
            return;

        // The first two parameters will be X, Y for both obstacle and thing perceptions.
        int x = PerceptUtils.GetNumberParameter(p, 0).intValue();
        int y = PerceptUtils.GetNumberParameter(p, 1).intValue();


        // Create a position for the percept and get the existing MapPercept
        Position curPosition = new Position(x, y);

        // Convert to an absolute position
        curPosition = curPosition.add(currentStepAgentPosition);

        MapPercept currentMapPercept = currentStepKnowledge.get(curPosition);

        if (currentMapPercept == null) {
            LOG.info("Null: " + curPosition);
        }

        if (Thing.canParse(p)) {
            Thing thing = Thing.ParseThing(p);
            currentMapPercept.setThing(thing);
        } else if (Terrain.canParse(p)) {
            currentMapPercept.setTerrain(Terrain.parseTerrain(p));
        }
    }

    public void agentAuthenticated(String agentName, Position translation, AgentMap agentMap) {
        knownAgentMaps.put(agentName, agentMap);
        translationPositions.put(agentName, translation);

        agentMap.knownAgentMaps.put(this.agent, this);
        agentMap.translationPositions.put(this.agent, translation.negate());
        for (MapPercept percept : getMapKnowledge().values()) {
            MapPercept translatedPercept = percept.copyToAgent(translation);
            agentMap.agentFinalizedPercept(this.agent, translatedPercept);
        }
    }

    private void updateMapLocation(MapPercept updatePercept) {
        MapPercept currentPercept = mapKnowledge.getOrDefault(updatePercept.getLocation(), null);

        // If we dont have a percept at the location, set it.
        if (currentPercept == null) {
            mapKnowledge.put(updatePercept.getLocation(), updatePercept);
            return;
        }

        // If we do have a perception at the location, but ours is older, then update/overwrite it.
        if (currentPercept.getLastStepPerceived() < updatePercept.getLastStepPerceived())
            mapKnowledge.put(updatePercept.getLocation(), updatePercept);
    }

    private void agentFinalizedPercept(String agent, MapPercept updatedPercept) {
        updateMapLocation(updatedPercept);
    }

    public List<MapPercept> getRelativePerceptions(int range) {
        if (range <= 0)
            return new ArrayList<>();

        List<MapPercept> perceptList = new ArrayList<>();

        for (Position p : new Utils.Area(currentStepAgentPosition, range)) {
            MapPercept relativePercept = new MapPercept(mapKnowledge.get(p));
            relativePercept.setLocation(p.subtract(currentStepAgentPosition));

            perceptList.add(mapKnowledge.get(p));
        }

        return perceptList;
    }

    public List<MapPercept> getRelativeBlockingPerceptions(int range) {
        if (range <= 0)
            return new ArrayList<>();

        List<MapPercept> perceptList = getRelativePerceptions(range);

        return perceptList.parallelStream().filter(MapPercept::isBlocking).collect(Collectors.toList());
    }

    public Map<Position, MapPercept> getMapKnowledge() {
        return Collections.unmodifiableMap(mapKnowledge);
    }

    /**
     * @return
     */
    public List<Position> getNavigationPath(Position absoluteDestination) {
        return mapKnowledge.getShortestPath(currentStepAgentPosition, absoluteDestination);
    }

    public Position relativeToAbsoluteLocation(Position relative) {
        return currentStepAgentPosition.add(relative);
    }

    public Position absoluteToRelativeLocation(Position absolute) {
        return absolute.subtract(currentStepAgentPosition);
    }

    private MapPercept getTranslatedPercept(String agent, MapPercept percept)
    {
        Position translation = translationPositions.get(agent);
        return percept.copyToAgent(translation);
    }

    public synchronized void finalizeStep() {
        mapKnowledge.putAll(currentStepKnowledge);

        for (MapPercept percept : currentStepKnowledge.values()) {
            for (AgentMap map : knownAgentMaps.values()) {
                map.agentFinalizedPercept(this.agent, getTranslatedPercept(map.agent, percept));
            }
        }

//        // Next steps: get rid of sy
//        for (Map.Entry<Position, MapPercept> updatedPercepts : getMapKnowledge().entrySet()) {
//            // Update agents

//        }
    }

}
