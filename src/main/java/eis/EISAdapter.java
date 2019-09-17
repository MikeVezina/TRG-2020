package eis;

import eis.listeners.SynchronizedPerceptWatcher;
import eis.map.AgentMap;
import eis.map.MapPercept;
import eis.agent.*;
import eis.percepts.things.Block;
import jason.JasonException;
import jason.NoValueException;
import jason.asSyntax.*;
import eis.exceptions.*;
import eis.iilang.*;
import jason.environment.Environment;
import massim.eismassim.EnvironmentInterface;
import eis.map.Direction;
import utils.LiteralUtils;
import utils.PerceptUtils;
import eis.map.Position;
import utils.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * This class functions as a Jason environment, using EISMASSim to connect to a MASSim server.
 * (see http://cig.in.tu-clausthal.de/eis)
 * (see also https://multiagentcontest.org)
 *
 * @author Jomi
 * - adapted by ta10
 */
public class EISAdapter extends Environment implements AgentListener {

    private Logger logger = Logger.getLogger("EISAdapter." + EISAdapter.class.getName());

    private static EISAdapter singleton;
    private EnvironmentInterface ei;
    private SynchronizedPerceptWatcher perceptWatcher;

    private int lastUpdateStep = -1;

    public EISAdapter() {
        super(20);
        singleton = this;
        perceptWatcher = SynchronizedPerceptWatcher.getInstance();
    }

    public static EISAdapter getSingleton() {
        return singleton;
    }

    @Override
    public void init(String[] args) {

        ei = new EnvironmentInterface("conf/eismassimconfig.json");

        try {
            ei.start();
        } catch (ManagementException e) {
            e.printStackTrace();
        }

        for (String e : ei.getEntities()) {
            System.out.println("Register agent " + e);

            try {
                ei.registerAgent(e);
            } catch (AgentException e1) {
                e1.printStackTrace();
            }

            ei.attachAgentListener(e, this);

            try {
                ei.associateEntity(e, e);
            } catch (RelationException e1) {
                e1.printStackTrace();
            }
        }

        perceptWatcher.start();
    }

    @Override
    public void handlePercept(String agent, Percept percept) {
        // We do not use notifications.
    }

    public AgentContainer getAgentContainer(String agentName) {
        return perceptWatcher.getAgentContainer(agentName);
    }

    @Override
    public List<Literal> getPercepts(String agName) {
        Structure strcEnt = ASSyntax.createStructure("entity", ASSyntax.createAtom(agName));

        Collection<Literal> ps = super.getPercepts(agName);
        List<Literal> percepts = ps == null ? new ArrayList<>() : new ArrayList<>(ps);

        if (ei == null) {
            throw new RuntimeException("Failed to get environment.");
        }

        clearPercepts(agName);

        // The operator should rely on it's own beliefs and internal actions.
        if (agName.equals("operator")) {
            return percepts;
        }

        AgentContainer agentContainer = getAgentContainer(agName);

        if (agentContainer == null)
            throw new RuntimeException("Failed to get agent container for: " + agName);

        List<Literal> agentPercepts = agentContainer.getCurrentPerceptions();

        Literal perceptLit = perceptToLiteral(agentContainer.getAgentLocation()).addAnnots(strcEnt);
        percepts.add(perceptLit);

        for (Literal p : agentPercepts) {
            p = mapEntityPerceptions(agentContainer, p);

            // Do not include perceptions that are filtered out
            if (p == null)
                continue;

            percepts.add(p);
        }

        // Add team mate relative perceptions
        percepts.addAll(addAuthenticatedTeammates(agName));

        return percepts;
    }


    private synchronized List<Literal> addAuthenticatedTeammates(String entity) {
        AgentContainer agentContainer = getAgentContainer(entity);
        Map<AgentContainer, Position> agentLocations = agentContainer.getAgentAuthentication().getAuthenticatedTeammatePositions();


        return agentLocations.entrySet().stream().map((entry) -> {
            AgentContainer otherAgentContainer = entry.getKey();
            Position position = entry.getValue();

            // Convert
            Position relativePosition = agentContainer.absoluteToRelativeLocation(position);
            return ASSyntax.createLiteral("teamAgent", new NumberTermImpl(relativePosition.getX()), new NumberTermImpl(relativePosition.getY()), new Atom(otherAgentContainer.getAgentName()));
        }).collect(Collectors.toList());
    }


    public AgentMap getAgentMap(String agentName) {
        return getAgentContainer(agentName).getAgentMap();
    }

    private synchronized Literal mapEntityPerceptions(AgentContainer agentContainer, Literal perceptLiteral) {
        if(perceptLiteral == null)
            return null;

        String perceptName = perceptLiteral.getFunctor();

        if(!perceptName.equals("thing"))
            return perceptLiteral;

        String thingType = LiteralUtils.GetStringParameter(perceptLiteral, 2);

        if(!thingType.equals("entity"))
            return perceptLiteral;

        String entity = agentContainer.getAgentName();

        String team = LiteralUtils.GetStringParameter(perceptLiteral, 3);

        // Other team perception
        if (!team.equals(agentContainer.getAgentPerceptContainer().getSharedPerceptContainer().getTeamName()))
            return perceptLiteral;

        Position relativePosition = new Position(LiteralUtils.GetNumberParameter(perceptLiteral, 0).intValue(), LiteralUtils.GetNumberParameter(perceptLiteral, 1).intValue());

        if (relativePosition.equals(new Position(0, 0)))
            // Modify self perception
            return ASSyntax.createLiteral("self", new NumberTermImpl(relativePosition.getX()), new NumberTermImpl(relativePosition.getY()), new Atom(entity));

        Position perceptAbsolutePosition = agentContainer.relativeToAbsoluteLocation(relativePosition);

        List<Position> matchingAgents = agentContainer.getAgentAuthentication().getAuthenticatedTeammatePositions()
                .values()
                .stream()
                .filter(absTeamPos -> absTeamPos.equals(perceptAbsolutePosition)).collect(Collectors.toList());

        int numberOfMatchingAgents = matchingAgents.size();

        if (numberOfMatchingAgents == 1)
            return null; // Filter out team percepts, as we add them in later

        else if (numberOfMatchingAgents > 1) {
            logger.warning("There are multiple agents with the same translation coordinates for " + entity);
            logger.warning("Authenticated Agents: " + agentContainer.getAgentAuthentication().getAuthenticatedAgents());
            return null;
        }

        return perceptLiteral;
    }


    private void setAuthenticatedAgent(String agent1, String agent2, Position pos) {
        if (agent1.equals(agent2)) {
            logger.info("Attempting to authenticate the same agent.");
            return;
        }

        AgentContainer agentContainer1 = getAgentContainer(agent1);
        AgentContainer agentContainer2 = getAgentContainer(agent2);

        if (pos != null) {
            agentContainer1.getAgentAuthentication().authenticateAgent(agentContainer2, pos);
            agentContainer2.getAgentAuthentication().authenticateAgent(agentContainer1, pos.negate());
        }

    }

    @Override
    public boolean executeAction(String agName, Structure action) {

        if (ei == null) {
            logger.warning("There is no environment loaded! Ignoring action " + action);
            return false;
        }

        if (action.getFunctor().equalsIgnoreCase("authenticateAgents")) {
            Atom origin = (Atom) action.getTerm(0);
            Atom authAgent = (Atom) action.getTerm(1);
            Atom translation = (Atom) action.getTerm(2);

            Position pos = new Position(getNumberTermInt(translation.getTerm(0)), getNumberTermInt(translation.getTerm(1)));
            setAuthenticatedAgent(origin.getFunctor(), authAgent.getFunctor(), pos);

            return true;
        }

        if (action.getFunctor().equalsIgnoreCase("taskSubmitted")) {
            AgentContainer ent = getAgentContainer(agName);
            ent.taskSubmitted();

            return true;
        }

        if (action.getFunctor().equalsIgnoreCase("blockAttached")) {
            AgentContainer ent = getAgentContainer(agName);
            Literal dirLiteral = (Literal) action.getTerm(0);
            Direction dir = Utils.DirectionToRelativeLocation(dirLiteral.getFunctor());
            MapPercept mapPercept = getAgentContainer(agName).getAgentMap().getRelativePerception(dir);
            if (dir == null || mapPercept == null || !mapPercept.hasBlock())
                return false;

            Block block = mapPercept.getBlock();

            if (block == null)
                return false;

            ent.attachBlock(dir.getPosition());

            return true;
        }

        if (action.getFunctor().equalsIgnoreCase("blockDetached")) {
            AgentContainer ent = getAgentContainer(agName);
            Literal dirLiteral = (Literal) action.getTerm(0);
            Direction dir = Utils.DirectionToRelativeLocation(dirLiteral.getFunctor());
            MapPercept mapPercept = getAgentContainer(agName).getAgentMap().getRelativePerception(dir);

            if (dir == null || mapPercept == null || !mapPercept.hasBlock())
                return false;

            Block block = mapPercept.getBlock();

            if (block == null)
                return false;

            ent.detachBlock(dir.getPosition());

            return true;
        }

        if (action.getFunctor().equalsIgnoreCase("agentRotated")) {
            AgentContainer ent = getAgentContainer(agName);
            Atom rotationLiteral = (Atom) action.getTerm(0);
            Rotation rotate = Rotation.getRotation(rotationLiteral);
            ent.rotate(rotate);
            return true;
        }

        if (action.getFunctor().equals("addForbiddenDirection")) {
            Atom direction = (Atom) action.getTerm(0);
            Position dirPos = Utils.DirectionToRelativeLocation(direction.getFunctor()).getPosition();
            getAgentMap(agName).addForbiddenLocation(dirPos);
            return true;

        }

        try {
            System.err.println(action.getFunctor());
            ei.performAction(agName, literalToAction(action));
            return true;
        } catch (ActException e) {
            e.printStackTrace();
        }

        return false;
    }

    private static int getNumberTermInt(Term t) {
        return (int) ((NumberTermImpl) t).solve();
    }

    /**
     * Called before the end of MAS execution
     */
    @Override
    public void stop() {
        if (ei != null) {
            try {
                if (ei.isKillSupported()) ei.kill();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.stop();
    }

    public static Literal perceptToLiteral(Atom namespace, Percept per) {
        Literal l;
        if (namespace == null)
            l = ASSyntax.createLiteral(per.getName());
        else
            l = ASSyntax.createLiteral(namespace, per.getName());

        for (Parameter par : per.getParameters())
            l.addTerm(parameterToTerm(par));
        return l;
    }

    public static Literal perceptToLiteral(Percept per) {

        Atom namespace = new Atom("percept");
        return perceptToLiteral(namespace, per);
    }

    public static Term parameterToTerm(Parameter par) {
        if (par instanceof Numeral) {
            return ASSyntax.createNumber(((Numeral) par).getValue().doubleValue());
        } else if (par instanceof Identifier) {
            try {
                Identifier i = (Identifier) par;
                String a = i.getValue();
                if (!Character.isUpperCase(a.charAt(0)))
                    return ASSyntax.parseTerm(a);
            } catch (Exception ignored) {
            }
            return ASSyntax.createString(((Identifier) par).getValue());
        } else if (par instanceof ParameterList) {
            ListTerm list = new ListTermImpl();
            ListTerm tail = list;
            for (Parameter p : (ParameterList) par)
                tail = tail.append(parameterToTerm(p));
            return list;
        } else if (par instanceof Function) {
            Function f = (Function) par;
            Structure l = ASSyntax.createStructure(f.getName());
            for (Parameter p : f.getParameters())
                l.addTerm(parameterToTerm(p));
            return l;
        }
        throw new RuntimeException("The type of parameter " + par + " is unknown!");
    }

    public static Action literalToAction(Literal action) {
        Parameter[] pars = new Parameter[action.getArity()];
        for (int i = 0; i < action.getArity(); i++)
            pars[i] = termToParameter(action.getTerm(i));
        return new Action(action.getFunctor(), pars);
    }

    public static Parameter termToParameter(Term t) {
        if (t.isNumeric()) {
            try {
                double d = ((NumberTerm) t).solve();
                if ((d == Math.floor(d)) && !Double.isInfinite(d)) return new Numeral((int) d);
                return new Numeral(d);
            } catch (NoValueException e) {
                e.printStackTrace();
            }
            return new Numeral(null);
        } else if (t.isList()) {
            Collection<Parameter> terms = new ArrayList<>();
            for (Term listTerm : (ListTerm) t)
                terms.add(termToParameter(listTerm));
            return new ParameterList(terms);
        } else if (t.isString()) {
            return new Identifier(((StringTerm) t).getString());
        } else if (t.isLiteral()) {
            Literal l = (Literal) t;
            if (!l.hasTerm()) {
                return new Identifier(l.getFunctor());
            } else {
                Parameter[] terms = new Parameter[l.getArity()];
                for (int i = 0; i < l.getArity(); i++)
                    terms[i] = termToParameter(l.getTerm(i));
                return new Function(l.getFunctor(), terms);
            }
        }
        return new Identifier(t.toString());
    }

    public EnvironmentInterface getEnvironmentInterface() {
        return ei;
    }
}
