{ include("tasks/tasks.asl") }
{ include("tasks/requirements.asl") }
{ include("operator/location.asl") }
{ include("auth/auth.asl") }

/*
The operator is going to be responsible for being the central point of communication for our agents.
A few things that the operator should keep track of:
- Absolute position reference for every agent, and the ability to translate between two agents' point of reference.
- Maintaining the overall mental model of the map.
- Task Parsing and requirement assignments.
*/

getAgents(AGENTS)
    :-  .all_names(NAMES) &
        .delete(operator, NAMES, AGENTS).


getAgent(AGENT)
    :-  not(.ground(AGENT)) &
        getAgents(ALL_AGENTS) &
        .member(AGENT, ALL_AGENTS).

getFreeAgents(FREE_AGENTS)
    :-  not(.ground(FREE_AGENTS)) &
        .setof(AGENT, getAgent(AGENT) & not(taskAssignment(AGENT, _, _)), FREE_AGENTS).

!assignTasks.


@step_auth[atomic]
+step(CUR_STEP)
    :   .findall(agent(AGENT, MY_POS, X, Y), hasFriendly(CUR_STEP - 1, X, Y, MY_POS)[source(AGENT)], AGENTS) &
        .length(AGENTS, SIZE) & SIZE > 0 &
        eis.internal.authenticate_agents(AGENTS)
    <-  .abolish(hasFriendly(CUR_STEP - 1, _, _, _)[source(_)]). // Remove any hasFriendly notifications


+taskAssignment(AGENT, TASK, REQ)
    <-  .print("Agent has been assigned ", REQ, " of task: ", TASK).

+!assignTasks
    :   getFreeAgents(FREE_AGENTS) &
        eis.internal.select_task(FREE_AGENTS, RESULTS)
    <-  for (.member([AGENT, TASK, REQ], RESULTS) ) { // I can't believe I'm using a for loop. This is disgusting but much better than the alternative. :).
                +taskAssignment(AGENT, TASK, REQ);
        };
        !assignTasks.


+!assignTasks
    <-  .print("No Free Agents Available.").















/** OLD **/


+finishedRequirement(TASK, REQ)
    <-  .print("Finished TASK: ", REQ).

+!coordinateAgents([AGENT, REQ], [AGENT_O, REQ_2])
    <-  .send(AGENT, achieve, meetAgent([AGENT_O, REQ_2], REQ, master));
        .send(AGENT_O, achieve, meetAgent(AGENT, REQ_2, slave)).


+obtained(TASK, BLOCK)[source(AGENT)]
    :   taskAssignment(TASK, AGENT, req(X, Y, BLOCK)) &
        taskAssignment(TASK, AGENT_O, req(X_O, Y_O, B_O)) &
        AGENT \== AGENT_O &
        obtained(TASK, B_O)[source(AGENT_O)] // Other agent also obtained block.
    <-  .print(AGENT, " obtained ", TASK, " block: ", BLOCK);
        .send(AGENT, askOne, hasBlockAttached(B), REPLY);
        .send(AGENT_O, askOne, hasBlockAttached(B_O), REPLY_O);
        !coordinateAgents([AGENT, req(X,Y,BLOCK)],[AGENT_O, req(X_O,Y_O,B_O)]).


+taskSubmitted(TASK_NAME)
    <- .print("Congrats on completing ", TASK_NAME);
        !assignTasks.

//// TODO NOTE: it's possible to assign tasks to sub-teams of two agents.
//// (That way we can have multiple tasks being completed at the same time.)


+friendly(X, Y)
<- .print("Found friendly: ", X, Y).


