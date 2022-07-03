package org.matsim.analysis.eventHandler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

import java.util.*;

public class TravelledDistanceHandler implements TransitDriverStartsEventHandler, LinkLeaveEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {

    private final Map<Id<Vehicle>, Id<Person>> personsInCar = new HashMap<>();
    private final Map<Id<Person>, List<Double>> personToTrips = new HashMap<>();
    private final Set<Id<Person>> transitDrivers = new HashSet<>();

    private final Network network;

    public TravelledDistanceHandler(Network network) {
        this.network = network;
    }

    public Map<Id<Person>, List<Double>> getPersonToTrips() {
        return personToTrips;
    }


    @Override
    public void handleEvent(TransitDriverStartsEvent transitDriverStartsEvent) {
        transitDrivers.add(transitDriverStartsEvent.getDriverId());
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {

        // only handle events for non-transit-driver persons
        if (transitDrivers.contains(personEntersVehicleEvent.getPersonId())) return;

        // a person enters a vehicle. We have to remember which vehicle contains which person
        personsInCar.put(personEntersVehicleEvent.getVehicleId(), personEntersVehicleEvent.getPersonId());
        // prepare a new entry in the trips diary of the person and fill it with 0 since the person has travelled 0m yet
        personToTrips.computeIfAbsent(personEntersVehicleEvent.getPersonId(), id -> new ArrayList<>()).add(0.0);
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {

        // only collect linkleaveevents for vehicles where we know who is in there
        if (!personsInCar.containsKey(linkLeaveEvent.getVehicleId())) return;

        // get the person id who is in the vehicle
        var personId = personsInCar.get(linkLeaveEvent.getVehicleId());
        // get the link
        var link = network.getLinks().get(linkLeaveEvent.getLinkId());
        // get the trip diary
        var trips = personToTrips.get(personId);
        // get the current value of the last trip in the trips list
        var currentValue = trips.get(trips.size() - 1);
        // compute the new distance value and set the last entry of the trips list to the new accumulated distance
        trips.set(trips.size() - 1, currentValue + link.getLength());
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {

        // no transit drivers
        if (transitDrivers.contains(personLeavesVehicleEvent.getPersonId())) return;

        // the person is not in the vehicle anymore so remove the record as well
        personsInCar.remove(personLeavesVehicleEvent.getVehicleId());
    }
}