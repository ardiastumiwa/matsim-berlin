package org.matsim.run.fahrradRing;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.matsim.run.fahrradRing.RunFahrradRing.*;

public class CreateNetwork {
    public static void main(String[] args) {
        args = new String[] {"scenarios/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml"}  ;

        Config config = prepareConfig( args );
        Scenario scenario = prepareScenario( config );

        var sbahnRingFilePath = "original-input-data/Umweltzone_-_Berlin-shp/Umweltzone.shp";
        var features = ShapeFileReader.getAllFeatures(sbahnRingFilePath);
        var sbahnRing = features.stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .collect(Collectors.toList()).get(0);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
        var plansToDelete = new HashMap<Map.Entry<Id<Person>, ? extends Person>, Plan>();
        var counter = 0;
        var neuerRun = scenario.getPopulation().getPersons().values().stream()
                .map(it -> it.getPlans()
                        .stream()
                        .map(plan -> createDictionary(plan))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        // find streets within sbahn ring and remove car as allowed transport mode
        for (var link : scenario.getNetwork().getLinks().entrySet()) {
            counter++;
            System.out.println(counter);
            // System.out.println(link.getKey() + " " + link.getValue());
            var from = MGC.coord2Point(transformation.transform(link.getValue().getFromNode().getCoord()));
            var to = MGC.coord2Point(transformation.transform(link.getValue().getToNode().getCoord()));
            if (sbahnRing.contains(from) || sbahnRing.contains(to)) {
                if (link.getValue().getAllowedModes().stream().count() == 1 && link.getValue().getAllowedModes().toArray()[0] == TransportMode.car) {
                    // Basically delete link by making it super slow
                    link.getValue().setFreespeed(0.000000001); // arbitrary but super small number
                } else {
                    var allowedModes = link.getValue().getAllowedModes();
                    var newModes = allowedModes.stream().filter(mode -> mode != TransportMode.car).collect(Collectors.toSet());
                    link.getValue().setAllowedModes(newModes);

                    // clean up routes => remove routes that used links within sbahn ring
                    var neunudel = neuerRun.stream()
                            .filter(person -> person.stream()
                                    .filter(plan -> plan.values().stream()
                                            .filter(legs -> legs.stream()
                                                    .filter(leg -> leg.getRoute() != null && (leg.getRoute().getStartLinkId() == link.getKey() || leg.getRoute().getEndLinkId() == link.getKey()))
                                                    .count() > 0)
                                            .count() > 0)
                                    .count() > 0)
                            .collect(Collectors.toList());
                    for (var per: neunudel) {
                        for (var plan: per) {
                            for (var bla: plan.keySet()) {
                                var udo = bla.getPlanElements().stream().filter(act -> act instanceof Leg).map(leg -> (Leg) leg).collect(Collectors.toList());
                                for (var u: udo) {
                                    u.setMode(TransportMode.pt);
                                    u.setRoute(new GenericRouteImpl(null, null));
                                }
                            }
                        }
                    }
                }
            }
        }
        NetworkUtils.writeNetwork(scenario.getNetwork(), "scenarios/berlin-v5.5-1pct/input/berlin-1pct_nein.config.xml");
    }
}
