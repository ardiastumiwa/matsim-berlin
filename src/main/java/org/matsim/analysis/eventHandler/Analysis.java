package org.matsim.analysis.eventHandler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Analysis {

    public static void main(String[] args) {

        var network = NetworkUtils.readNetwork("D:\\Documents\\MATSim\\matsim-berlin\\scenarios\\berlin-v5.5-1pct\\output\\berlin-v5.5.3-1pct.output_network.xml.gz");

        var distanceHandler = new DistancesHandler(network);
        var travelledDistanceHandler = new TravelledDistanceHandler(network);
        var timeHandler = new TimeHandler(network);

        var manager = EventsUtils.createEventsManager();

        manager.addHandler(distanceHandler);
        manager.addHandler(travelledDistanceHandler);
        manager.addHandler(timeHandler);

        EventsUtils.readEvents(manager, "D:\\Documents\\MATSim\\matsim-berlin\\scenarios\\berlin-v5.5-1pct\\output\\berlin-v5.5.3-1pct.output_events.xml.gz");

        var tripsByPerson = distanceHandler.getTripDistances();
        var timeByPerson = timeHandler.getTripTime();

        printDistanceBins(tripsByPerson);

        printNumberOfTrips(tripsByPerson);

        printAverageTime(timeByPerson);

        printAverageDistance(travelledDistanceHandler.getPersonToTrips());

    }

    private static void printDistanceBins(Map<Id<Person>, List<Double>> tripsByPerson) {

        // we want 5 distance classes
        var distances = new int[5];

        // iterate over evey person id
        for (var entry : tripsByPerson.entrySet()) {
            var tripsList = entry.getValue();

            // iterate over each beeline distance that was collected for each trip of a person
            for (var distance : tripsList) {
                // convert the distance into the index to which the distance belongs. I.e. into which bucket does this trip fall
                var index = distanceToIndex(distance);
                // increment the count for the corresponding bucket because we have one more trips that falls into that bucket
                distances[index]++;
            }
        }

        System.out.println("# Distances #");
        System.out.println("< 1000m: " + distances[0]);
        System.out.println("< 5000m: " + distances[1]);
        System.out.println("< 10000m: " + distances[2]);
        System.out.println("< 20000m: " + distances[3]);
        System.out.println("> 20000m: " + distances[4]);
    }

    private static void printNumberOfTrips(Map<Id<Person>, List<Double>> tripsByPerson) {
        var averageNumberOfTrips = tripsByPerson.values().stream()
                .mapToInt(List::size)
                .average()
                .getAsDouble();

        System.out.println("Average number of trips: " + averageNumberOfTrips);
    }

    private static void printAverageTime(Map<Id<Person>, Double> travelTime) {
        var averageTime = travelTime.values().stream()
                .mapToDouble(value -> (double) value)
                .average()
                .getAsDouble();

        System.out.println("Average time of trips: " + averageTime/60 + " minutes");
    }

    private static void printAverageDistance(Map<Id<Person>, List<Double>> tripsByPerson) {
        var averageDistanceTravelled = tripsByPerson.values().stream()
                .flatMap(Collection::stream)
                .mapToDouble(value -> value)
                .average()
                .orElseThrow();

        System.out.println("Average distance of trips: " + averageDistanceTravelled/1000 + " km");
    }

    private static int distanceToIndex(double distance) {
        if (distance < 1000) return 0;
        if (distance < 5000) return 1;
        if (distance < 10000) return 2;
        if (distance < 20000) return 3;
        return 4;
    }

    private static String distanceToKey(double distance) {
        if (distance < 1000) return "< 1000: ";
        if (distance < 5000) return "< 5000: ";
        if (distance < 10000) return "< 10000: ";
        if (distance < 20000) return "< 20000: ";
        return "> 20000: ";
    }

}
