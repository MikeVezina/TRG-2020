package eis.agent;

import eis.EISAdapter;
import eis.iilang.Identifier;
import eis.iilang.Percept;
import eis.listeners.ActionHandler;
import eis.map.AgentMap;
import eis.messages.MQSender;
import eis.messages.Message;
import eis.map.MapPercept;
import eis.percepts.containers.AgentPerceptContainer;
import eis.percepts.containers.SharedPerceptContainer;
import jason.asSyntax.Literal;
import massim.protocol.messages.scenario.Actions;
import eis.map.Position;
import utils.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class AgentContainer {


    private AgentLocation agentLocation;
    private AgentMap agentMap;
    private AgentAuthentication agentAuthentication;
    private ConcurrentLinkedQueue<ActionHandler> actionHandler;
    private String agentName;
    private List<Literal> currentStepPercepts;
    private AgentPerceptContainer perceptContainer;
    private Set<Position> attachedBlocks;
    private MQSender mqSender;

    public AgentContainer(String agentName) {
        this.agentName = agentName;
        this.mqSender = new MQSender(agentName);
        this.agentLocation = new AgentLocation();
        this.actionHandler = new ConcurrentLinkedQueue<>();
        this.attachedBlocks = new HashSet<>();
        this.currentStepPercepts = new ArrayList<>();

        this.agentAuthentication = new AgentAuthentication(this);
        this.agentMap = new AgentMap(this);
    }

    public MQSender getMqSender() {
        return mqSender;
    }

    public synchronized AgentLocation getAgentLocation() {
        return agentLocation;
    }

    public synchronized Position getCurrentLocation() {
        return agentLocation.getCurrentLocation();
    }

    public AgentMap getAgentMap() {
        return agentMap;
    }

    public AgentAuthentication getAgentAuthentication() {
        return agentAuthentication;
    }

    public String getAgentName() {
        return agentName;
    }

    /**
     * This method needs to be lightweight and should only be responsible for updating the percept container. Do not call any
     * listeners or perform any GUI updates.
     *
     * @param percepts The current step percepts for this agent.
     */
    public synchronized void updatePerceptions(List<Percept> percepts) {
        this.currentStepPercepts = percepts.parallelStream().map(EISAdapter::perceptToLiteral).collect(Collectors.toList());

        if (this.currentStepPercepts.size() != percepts.size()) {
            System.out.println("There may be an issue with async mapping.");
            throw new NullPointerException("Issues with async mapping. Actual: " + currentStepPercepts.size() + " vs. Expected: " + percepts.size());
        }

        // Create a new percept container for this step.
        perceptContainer = AgentPerceptContainer.parsePercepts(percepts);

        notifyAll(); // Notify any agents that are waiting for perceptions

        Message.createAndSendNewStepMessage(mqSender, perceptContainer.getSharedPerceptContainer().getStep());

        handleLastAction();
    }

    public Position relativeToAbsoluteLocation(Position relative) {
        return getCurrentLocation().add(relative);
    }

    public Position absoluteToRelativeLocation(Position absolute) {
        return absolute.subtract(getCurrentLocation());
    }

    private void handleLastAction() {
        // Update the location
        updateLocation();
    }

    private void updateLocation() {
        if (perceptContainer.getLastAction().equals(Actions.MOVE) && perceptContainer.getLastActionResult().equals("success")) {
            String directionIdentifier = ((Identifier) perceptContainer.getLastActionParams().get(0)).getValue();

            System.out.println(agentName + ": Step " + getCurrentStep() + " performed movement. " + perceptContainer.getLastAction() + " + " + perceptContainer.getLastActionResult());
            try {
                agentLocation.updateAgentLocation(Utils.DirectionToRelativeLocation(directionIdentifier));
            } catch (NullPointerException n) {
                System.out.println(agentName + " encountered movement error on step " + getCurrentStep());
                throw n;
            }
        } else {
            System.out.println(agentName + ": Step " + getCurrentStep() + " did not perform any movement. " + perceptContainer.getLastAction() + " + " + perceptContainer.getLastActionResult());
        }
    }

    public synchronized long getCurrentStep() {
        return getAgentPerceptContainer().getSharedPerceptContainer().getStep();
    }

    public synchronized List<Literal> getCurrentPerceptions() {
        if (this.currentStepPercepts == null) {
            long startWaitTime = System.nanoTime();
            try {
                wait();
                long deltaWaitTime = (System.nanoTime() - startWaitTime) / 1000000;
                if (deltaWaitTime > 500)
                    System.out.println("Thread " + Thread.currentThread().getName() + " waited " + deltaWaitTime + " ms for perceptions.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return this.currentStepPercepts;
    }

    public void attachBlock(Position position) {
        attachedBlocks.add(position);
    }

    public boolean hasAttachedPercepts() {
        return !attachedBlocks.isEmpty();
    }

    public Set<Position> getAttachedPositions() {
        return attachedBlocks;
    }

    public void rotate(Rotation rotation) {
        List<Position> rotatedAttachments = new ArrayList<>();
        for (Position p : attachedBlocks) {
            rotatedAttachments.add(rotation.rotate(p));
        }
        attachedBlocks.clear();
        attachedBlocks.addAll(rotatedAttachments);
    }


    public boolean isAttachedPercept(MapPercept mapPercept) {
        if (mapPercept == null)
            return false;

        Position relativePos = mapPercept.getLocation().subtract(getCurrentLocation());
        return mapPercept.hasBlock() && attachedBlocks.contains(relativePos);
    }

    public void detachBlock(Position position) {
        if (position == null)
            return;

        attachedBlocks.remove(position);
    }

    public void taskSubmitted() {
        attachedBlocks.clear();
    }

    public synchronized AgentPerceptContainer getAgentPerceptContainer() {

        // Wait for percepts if they haven't been set yet.
        if (this.perceptContainer == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return perceptContainer;
    }

    public void attachActionHandler(ActionHandler actionHandler) {
        if (actionHandler == null || this.actionHandler.contains(actionHandler))
            return;

        this.actionHandler.add(actionHandler);
    }

    public void notifyActionHandlers() {
        actionHandler.forEach(aH -> aH.handleNewAction(this));
    }

    public void updateMap() {
        agentMap.updateMap();
    }

    public SharedPerceptContainer getSharedPerceptContainer() {
        return getAgentPerceptContainer().getSharedPerceptContainer();
    }

    public void synchronizeMap() {
        agentAuthentication.pullMapPerceptsFromAgents();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentContainer)) return false;

        AgentContainer that = (AgentContainer) o;

        return Objects.equals(agentName, that.agentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentName);
    }

    @Override
    public String toString() {
        return "Container of " + agentName;
    }
}