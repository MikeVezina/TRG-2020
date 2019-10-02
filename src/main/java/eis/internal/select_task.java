package eis.internal;

import eis.watcher.SynchronizedPerceptWatcher;
import eis.percepts.Task;
import eis.percepts.containers.SharedPerceptContainer;
import jason.JasonException;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.*;

import java.util.*;
import java.util.stream.Collectors;

public class select_task extends DefaultInternalAction {

    private static final long serialVersionUID = -6214881485708125130L;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {

        // execute the internal action

        ts.getAg().getLogger().fine("Executing internal action 'select_task'");


        List<Literal> taskPercepts = new LinkedList<>();
        SharedPerceptContainer sharedPerceptContainer = SynchronizedPerceptWatcher.getInstance().getSharedPerceptContainer();
        long curStep = sharedPerceptContainer.getStep();

        List<Task> tasks = sharedPerceptContainer.getTaskMap().values().stream().filter(t -> t.getRequirementList().size() == 2).collect(Collectors.toList());

        if (tasks.isEmpty())
            return false;

        final Task chosenOne = tasks.stream().filter(t -> {
            return !t.getRequirementList().get(0).getBlockType().equals(t.getRequirementList().get(1).getBlockType());

        }).findFirst().orElse(tasks.get(0));


        // TODO: Change this.

        try {
            // Unify
            boolean directionResult = un.unifiesNoUndo(chosenOne.getTaskLiteral(), args[0]);

            // Return result
            return (directionResult);
        }
        // Deal with error cases
        catch (ArrayIndexOutOfBoundsException e) {
            throw new JasonException("The internal action 'select_task' received the wrong number of arguements.");
        } catch (ClassCastException e) {
            throw new JasonException(
                    "The internal action 'rel_to_direction' received arguements that are of the wrong type.");
        } catch (Exception e) {
            throw new JasonException("Error in 'rel_to_direction'.");
        }
    }

}
