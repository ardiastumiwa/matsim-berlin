package org.matsim.analysis.eventHandler;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistancesHandler implements ActivityEndEventHandler, ActivityStartEventHandler {

    private final Map<Id<Person>, Coord> personToDepartureCoord = new HashMap<>();
    private final Map<Id<Person>, List<Double>> tripDistances = new HashMap<>();

    private final Network network;
    private boolean isInteraction(String type) {
        return type.endsWith("interaction");
    }

    public DistancesHandler(Network network) {
        this.network = network;
    }

    public Map<Id<Person>, List<Double>> getTripDistances() {
        return tripDistances;
    }


    @Override
    public void handleEvent(ActivityEndEvent activityEndEvent) {
        if (isInteraction(activityEndEvent.getActType())) return;
        var coord = network.getLinks().get(activityEndEvent.getLinkId()).getCoord();
        personToDepartureCoord.put(activityEndEvent.getPersonId(), coord);
    }

    @Override
    public void handleEvent(ActivityStartEvent activityStartEvent) {
        if (isInteraction(activityStartEvent.getActType())) return;
        var startCoord = personToDepartureCoord.remove(activityStartEvent.getPersonId());
        var endCoord = network.getLinks().get(activityStartEvent.getLinkId()).getCoord();
        var distance = CoordUtils.calcEuclideanDistance(startCoord, endCoord);
        var personId = activityStartEvent.getPersonId();
        tripDistances.computeIfAbsent(personId, id -> new ArrayList<>()).add(distance);
    }
}
