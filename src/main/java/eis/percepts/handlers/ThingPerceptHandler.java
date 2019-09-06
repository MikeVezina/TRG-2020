package eis.percepts.handlers;

import eis.iilang.Percept;
import eis.percepts.agent.AgentMap;
import eis.percepts.things.Thing;

import java.util.List;

public class ThingPerceptHandler extends PerceptMapper<Thing> {

    private AgentMap agentMap;

    public ThingPerceptHandler(String agentName, AgentMap agentMap) {
        super(agentName);
        this.agentMap = agentMap;
    }

    @Override
    protected boolean shouldHandlePercept(Percept p) {
        return Thing.canParse(p);
    }

    @Override
    public void perceptProcessingFinished() {
    }

    public List<Thing> getPerceivedThings()
    {
        return getMappedPercepts();
    }

    @Override
    protected Thing mapPercept(Percept p) {
        return Thing.ParseThing(p);
    }
}
