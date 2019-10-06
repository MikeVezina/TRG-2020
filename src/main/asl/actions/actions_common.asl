getLastActionResult(RES) :-
    percept::lastActionResult(RES).

getLastAction(ACTION) :-
    percept::lastAction(ACTION).

getLastActionParams(PARAMS) :-
    percept::lastActionParams(PARAMS).


didActionSucceed :-
    getLastActionResult(success) & not(getLastAction(connect)) & not(getLastAction(submit)) & not(getLastAction(attach)) & not(getLastAction(detach)) & not(getLastAction(rotate)).

/* This is where we include action and plan failures */
@performAction[atomic]
+!performAction(ACTION)
    :   percept::step(STEP)
    <-  +lastAttemptedAction(ACTION); // Remember last action in case we need to re-attempt it
	    .print(STEP, ": Sending action: ", ACTION);
	    ACTION;
	    .wait("+percept::step(_)"); // Wait for the next simulation step
	    ?percept::step(STEP_AFTER);
	    .print("New Step: ", STEP_AFTER);
	    !handleActionResult;
//	    ?didActionSucceed;
	    -lastAttemptedAction(ACTION).


+!handleActionResult
    :   getLastActionResult(RESULT) &
        getLastAction(ACTION) &
        getLastActionParams(PARAMS)
    <-  !handleActionResult(ACTION, PARAMS, RESULT).

//+!doNothing
//    :   randomDirection(DIR) &
//        directionToXY(DIR, X, Y) &
//        not(lastClearLocation(X, Y))
//    <-  !performAction(clear(X,Y));
//        .abolish(lastClearLocation(_,_));
//        +lastClearLocation(X, Y).
//
//+!doNothing
//    :   randomDirection(DIR) &
//        directionToXY(DIR, X, Y) &
//        lastClearLocation(X, Y)
//    <-  !doNothing.

+?didActionSucceed
    :   getLastAction(detach) &
        getLastActionResult(FAILURE) &
        (FAILURE == failed | FAILURE == failed_target)
    <-  .print("Failed to attempt detach. Failure: ", FAILURE).

+?didActionSucceed
    :   getLastAction(detach) &
        getLastActionResult(success) &
        getLastActionParams([DIR])
    <-  blockDetached(DIR).

+!reattemptLastAction
    :   lastAttemptedAction(ACTION)
    <-  .print("Re-attempting last action");
        !performAction(ACTION).

+!reattemptLastAction
    :   not(lastAttemptedAction(ACTION))
    <-  .print("Error: No Action Attempted.");
        .fail.

+?didActionSucceed
    :   getLastActionResult(failed_random)
    <-  .print("The action failed randomly.");
        !reattemptLastAction.

// This action failure occurs when an entity is blocking the dispenser.
// For now we just rotate and try again (assuming it is our own attached block that is blocking the dispenser)
+?didActionSucceed
    :   getLastAction(request) &
        getLastActionResult(failed_blocked)
    <-  .print("Blocked!");
        !performAction(rotate(cw));
        !reattemptLastAction.

+?didActionSucceed
    :   getLastAction(attach) &
        getLastActionResult(success) &
        getLastActionParams([DIR])
    <-  blockAttached(DIR).

+?didActionSucceed
    :   getLastAction(rotate) &
        getLastActionResult(success) &
        getLastActionParams([DIR])
    <-  .print("Rotate success").

+?didActionSucceed
    :   getLastAction(rotate) &
        getLastActionResult(failed) &
        getLastActionParams([DIR])
    <-  .print("Rotate Failed");
        .fail.


+?didActionSucceed
    :   getLastAction(connect) &
        getLastActionResult(failed_partner)
    <-  !reattemptLastAction.

+?didActionSucceed
    :   getLastAction(connect) &
        getLastActionResult(RESULT) &
        getLastActionParams(PARAMS)
    <-  .print("Connected Result: ", RESULT, ". Parameters: ", PARAMS).

+?didActionSucceed
    :   getLastAction(submit) &
        getLastActionResult(success) &
        getLastActionParams([TASK_NAME])
    <-  .print("Submit Success!");
        taskSubmitted;
        .send(operator, tell, taskSubmitted(TASK_NAME)).

+?didActionSucceed
    :   getLastAction(submit) &
        getLastActionResult(RESULT) &
        RESULT \== success
    <-  .print("Failed to submit. Reason: ", RESULT).

+?didActionSucceed
    :   getLastAction(attach) &
        getLastActionResult(failed_blocked)
    <-  .print("Blocked!");
        !performAction(rotate(cw));
        !reattemptLastAction.



+?didActionSucceed
    :   getLastAction(move) &
        getLastActionResult(failed_forbidden) &
        getLastActionParams([DIR])
    <-  addForbiddenDirection(DIR).

+?didActionSucceed
    :   getLastAction(move) &
        (getLastActionResult(failed_path))
    <-  .fail.

+?didActionSucceed
    :   getLastAction(move) &
        (getLastActionResult(failed_status))
    <-  !reattemptLastAction.


-!handleActionResult(ACTION, PARAMS, success)
    <-  .print("(Warning): No handler for successful Action: ", ACTION).

-!handleActionResult(_, _, failed_random)
    <-  .print("Failed randomly. Retrying.");
        !reattemptLastAction.

-!handleActionResult(ACTION, PARAMS, RESULT)
    <-  .print("Error: The action ", ACTION, "(", PARAMS, ") result ", RESULT, " was not handled.");
        .fail.

-?didActionSucceed
    :   percept::lastActionResult(FAILURE)
    <-  .print("Error: The action failed with: ", FAILURE, ". This should not have occurred.").