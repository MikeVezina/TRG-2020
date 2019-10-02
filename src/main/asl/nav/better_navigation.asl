/**
There are various types of navigation, each driving their own requirements:
Both types of navigation should call the path finding module.

1. Typical navigation to a specific destination.
    - We want the agent to land on the destination
    - Used when navigating to a specific meeting point

2. Navigation beside a destination
    - This is useful when we want to navigate TO other "things"
    - i.e. we want to navigate BESIDE a dispenser, and not on it.
    - i.e. We want to navigate BESIDE a block drop-off point

================
Additional Notes:
- Navigation plans should call the movement component.
- Navigation plans should invoke path finding on every cycle (??) to ensure the current path
    is up-to-date with the perceptions.
- Navigation plans should monitor for failed_path, as it may signify an issue with path finding, or it
    could potentially be that we are hitting a forbidden area with a block?

============
Assumptions:
    - Path finding does not consider any attached blocks, so it is possible that the path finding may
      find a path that is impossible to traverse. This currently is handled when we use movement.asl. When
      the agent attempts to move, it will try to rotate itself to traverse the path generated by path finding.
      If it cannot traverse, even after rotating, the action plan will fail. This failure will bubble up to
      the navigation plans.
**/

// Checks if an agent is at an absolute X, Y position
isAtLocation(X, Y)
    :-  isCurrentLocation(absolute(X, Y)).

generatePath(X, Y, FIRST, RESULT)
    :-  navigationPath(X, Y, [FIRST | PATH], RESULT).

+!navigateToDestination(X, Y, RES)
    :   isAtLocation(X, Y) &
        RES = success // Set Result to success since we arrived at the destination
    <-  .print("Agent has arrived at destination: [", X, ", ", Y, "]").

+!navigateToDestination(X, Y, RESULT)
    :   not(isAtLocation(X, Y)) &
        generatePath(X, Y, DIR, success)
    <-  .print("Navigating: ", DIR);
        !move(DIR);
        !navigateToDestination(X, Y, RESULT).

+!navigateToDestination(X, Y, RESULT)
    :   not(isAtLocation(X, Y)) &
        generatePath(X, Y, DIR, RESULT) &
        RESULT \== success
    <-  .print("Could not generate a path for (", X, ", ", Y, "). Reason: ", RESULT).