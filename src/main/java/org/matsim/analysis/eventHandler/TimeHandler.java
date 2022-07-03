package org.matsim.analysis.eventHandler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;

import java.util.HashMap;
import java.util.Map;

public class TimeHandler implements PersonArrivalEventHandler, PersonDepartureEventHandler{

    private final Map<Id<Person>, Double> personToDepartureTime = new HashMap<>();
    private final Map<Id<Person>, Double> tripTravelTime = new HashMap<>();
    private final Network network;
    public TimeHandler(Network network) {
        this.network = network;
    }
    public Map<Id<Person>, Double> getTripTime() {
        return tripTravelTime;
    }

    @Override
    public void handleEvent(PersonDepartureEvent personDepartureEvent) {
        var departureTime = personDepartureEvent.getTime();
        var personId = personDepartureEvent.getPersonId();

        personToDepartureTime.put(personId, departureTime);
    }

    @Override
    public void handleEvent(PersonArrivalEvent personArrivalEvent) {
        var arrivalTime = personArrivalEvent.getTime();
        var departureTime = personToDepartureTime.get(personArrivalEvent.getPersonId());
        var travelTime = arrivalTime - departureTime;
        var personId = personArrivalEvent.getPersonId();
        tripTravelTime.put(personId, travelTime);
    }
}
