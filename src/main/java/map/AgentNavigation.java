package map;

import eis.agent.AgentContainer;
import eis.agent.Rotation;
import messages.Message;
import es.usc.citius.hipster.algorithm.ADStarForward;
import es.usc.citius.hipster.algorithm.Hipster;
import es.usc.citius.hipster.graph.GraphSearchProblem;
import es.usc.citius.hipster.model.ADStarNode;
import es.usc.citius.hipster.model.Node;
import es.usc.citius.hipster.model.problem.SearchComponents;
import utils.Stopwatch;

import java.util.*;
import java.util.stream.Collectors;

public class AgentNavigation {
    private AgentContainer agentContainer;
    private Map<Position, List<Position>> pathCache;

    public AgentNavigation(AgentContainer agentContainer) {
        this.agentContainer = agentContainer;
        pathCache = new HashMap<>();
    }

    private Graph getMapGraph() {
        return agentContainer.getAgentMap().getMapGraph();
    }

    /**
     * This function determines the rotations that are not blocked by any attachments.
     * This is only reliable if agents have at most one block attached to zero or more sides.
     * TODO: In the future, we can include multiple attachments by checking the "diagonal" blocks between the original and rotated positions. This is how the server does it.
     *
     * @return A list of unblocked rotations.
     */
    public List<Rotation> getRotationDirections() {
        List<Rotation> rotations = new ArrayList<>();

        for (Rotation r : Rotation.values()) {
            boolean isBlocked = false;

            for (Position perceptPosition : agentContainer.getAttachedPositions()) {
                MapPercept attachedPercept = agentContainer.getAgentMap().getMapPercept(agentContainer.getCurrentLocation().add(perceptPosition));

                Position rotatedRelativePosition = r.rotate(perceptPosition);
                Position rotatedAbsolutePosition = agentContainer.getCurrentLocation().add(rotatedRelativePosition);
                MapPercept rotatedPercept = agentContainer.getAgentMap().getMapPercept(rotatedAbsolutePosition);


                //getDiagonals(perceptPosition, rotatedPosition);

                // Checks to see if the destination position (after the percept is rotated) is blocked by a non-attached entity.
                if (rotatedPercept.isBlocking(attachedPercept) && !agentContainer.getAttachedPositions().contains(rotatedRelativePosition)) {
                    isBlocked = true;
                    break;
                }
            }

            if (!isBlocked)
                rotations.add(r);
        }

        return rotations;
    }

    /**
     * Gets the diagonal positions between the origin and the destination.
     * This is used for determining whether or not a rotation is blocked.
     *
     * @param origin The current location of the attachment
     * @param dest   The desired location after rotation
     * @return A list of diagonals between the current location and the rotated location.
     */
    private List<Position> getDiagonals(Position origin, Position dest) {
        throw new RuntimeException("This method is not implemented.");
    }

    /**
     * Performs path finding.
     *
     * @param absoluteDestination The absolute destination that the agent should navigate to.
     * @return A list of absolute positions that the agent must navigate to get to the absoluteDestination, or null if no
     * path could be found between the agent and the destination.
     */
    public synchronized List<Position> getNavigationPath(Position absoluteDestination) {
        return createADStarNavigation(absoluteDestination);
    }

    /**
     * Checks to see if the agent's attachments are blocked. This does not check if the agent itself is blocked.
     *
     * @param direction The direction in which to check if attachments are blocked.
     * @return True if the attachments are blocked, false otherwise.
     */
    public boolean areAttachmentsBlocked(Direction direction) {
        if (direction == null || !agentContainer.hasAttachedPercepts())
            return false;

        Position dirResult = agentContainer.getCurrentLocation().add(direction.getPosition());
        MapPercept dirPercept = getMapGraph().get(dirResult);

        if (dirPercept == null)
            return false;

        for (Position relative : agentContainer.getAttachedPositions()) {
            if (isAttachedThingBlocked(relative, direction))
                return true;
        }

        return false;
    }

    /**
     * Checks if the agent can move in the provided direction. This method checks if either the agent or attachments are blocked.
     *
     * @param direction The direction that the agent is attempting to move in.
     * @return True if the agent and all attachments are unblocked, false otherwise.
     */
    public boolean canAgentMove(Direction direction) {
        return !agentContainer.getAgentMap().isAgentBlocked(direction) && !areAttachmentsBlocked(direction);
    }

    public boolean isAttachedThingBlocked(Position attachedPosition, Direction direction) {
        if (direction == null || attachedPosition == null)
            return false;

        Position attachedPerceptPosition = agentContainer.getCurrentLocation().add(attachedPosition);
        MapPercept attachedPercept = getMapGraph().get(attachedPerceptPosition);

        Position nextAttachmentPosition = attachedPosition.add(direction.getPosition());
        Position nextAbsolutePosition = attachedPerceptPosition.add(direction.getPosition());
        MapPercept nextPercept = getMapGraph().get(nextAbsolutePosition);

        return nextPercept != null && (nextPercept.hasBlock() || !agentContainer.getAgentMap().getSelfPercept().equals(nextPercept)) && nextPercept.isBlocking(attachedPercept) && !agentContainer.getAttachedPositions().contains(nextAttachmentPosition);
    }

    /**
     * Checks if there is an unexplored edge near the current agent's perception. This helps with exploration.
     * This will only check one unit outside the agents current perceptions.
     *
     * @param edgeDirection The direction to check whether or not the agent has explored.
     * @return True if we have already explored in the edgeDirection
     */
    public boolean containsEdge(Direction edgeDirection) {
        int vision = agentContainer.getAgentPerceptContainer().getSharedPerceptContainer().getVision();
        if (vision == -1)
            return false;

        int edgeScalar = vision + 1;
        Position absolute = agentContainer.getCurrentLocation().add(edgeDirection.multiply(edgeScalar));
        return this.getMapGraph().containsKey(absolute);
    }


    private synchronized List<Position> getExistingPath(Position startingPoint, Position destination) {

        if(!pathCache.containsKey(destination))
            return null;

        // Create a copy of the path
        List<Position> existingPath = new ArrayList<>(pathCache.get(destination));

        // Check if we are on the cached path to the destination
        if (!existingPath.contains(startingPoint)) {
            // Remove the existing path, since we are no longer using it.
            pathCache.remove(destination);
            return null;
        }

        Iterator<Position> pathIterator = existingPath.iterator();



        // Remove any initial positions that aren't our starting point.
        Position nextPos = null;
        while (pathIterator.hasNext() && !(nextPos = pathIterator.next()).equals(startingPoint))
            pathIterator.remove();

        if(nextPos == null || !pathIterator.hasNext())
            throw new NullPointerException("Why is this not true?");

        Position nextStepPosition = pathIterator.next();
        Direction dir = Direction.GetDirection(nextStepPosition.subtract(startingPoint));

        if(agentContainer.getAgentMap().isAgentBlocked(dir))
            return null;

        return existingPath;
    }

    private synchronized List<Position> generatePath(Position destination)
    {

        Position startingPoint = agentContainer.getCurrentLocation();

        if(startingPoint.equals(destination))
            return null;

        List<Position> cachedPath = getExistingPath(startingPoint, destination);

        if(cachedPath != null)
            return cachedPath;

        // Create the search components (starting point, destination, etc.)
        SearchComponents<Double, Position, ?> components = GraphSearchProblem.startingFrom(startingPoint)
                .goalAt(destination)
                .in(agentContainer.getAgentMap().getMapGraph())
                .takeCostsFromEdges()
                .useHeuristicFunction(state -> Math.abs(destination.subtract(state).getDistance()))
                .components();
        ;

        ADStarForward adStarForward = Hipster.createADStar(components);
        Iterator<Node<Void, Position, ? extends ADStarNode<Void, Position, ?, ?>>> iterator = adStarForward.iterator();

        Node<Void, Position, ? extends ADStarNode<Void, Position, ?, ?>> node;
        do {
            node = iterator.next();
        } while (iterator.hasNext() && !node.state().equals(destination));

        if (!node.state().equals(destination)) {
            System.out.println("Failed to obtain a valid path to the destination: " + destination + ". Is the destination blocked?");
            return null;
        }

        // Convert AD Node to Position (aka states)
        return node.path().stream().map(ADStarNode::state).collect(Collectors.toList());
    }

    /**
     * Creates the shortest path from the current agent location to the destination.
     *
     * @param destination   The destination.
     * @return An array list of path positions to navigate to the destination, or null if a path could not be generated.
     * This occurs when there may be no path to the destination.
     */
    private synchronized List<Position> createADStarNavigation(Position destination) {
        Stopwatch stopwatch = Stopwatch.startTiming();
        List<Position> path = generatePath(destination);

        if(path == null)
            return null;

        pathCache.put(destination, new ArrayList<>(path));

        Message.createAndSendPathMessage(agentContainer.getMqSender(), path);

        long timedSearch = stopwatch.stopMS();
        System.out.println("Took " + timedSearch + " ms to search: " + path.size());

        return path;

    }

}
