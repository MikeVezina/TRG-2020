package eis.percepts.parsers;

import eis.iilang.Percept;
import eis.percepts.Task;
import eis.percepts.containers.TaskMap;

import java.util.List;

public class TaskMapper extends PerceptMapper<Task> {

    @Override
    protected Task mapPercept(Percept p) {
        return Task.parseTask(p);
    }

    @Override
    protected boolean shouldHandlePercept(Percept p) {
        return Task.canParse(p);
    }

    public TaskMap mapTaskList(List<Percept> rawPercepts, long currentStep)
    {
        // Create a task list object based on the mapped list
        TaskMap taskMapInstance = TaskMap.getInstance();
        taskMapInstance.updateFromPercepts(super.mapAllPercepts(rawPercepts), currentStep);

        return taskMapInstance;
    }
}
