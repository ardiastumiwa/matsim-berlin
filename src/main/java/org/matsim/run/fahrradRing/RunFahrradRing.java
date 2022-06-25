package org.matsim.run.fahrradRing;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.util.stream.Collectors;

public final class RunFahrradRing {

    public static void main(String[] args) {
        Config config;
        if ( args==null || args.length==0 || args[0]==null ){
            config = ConfigUtils.loadConfig( "scenarios/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml" );
        } else {
            config = ConfigUtils.loadConfig( args );
        }

        config.controler().setOverwriteFileSetting( OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists );

        // possibly modify config here

        // ---

        Scenario scenario = ScenarioUtils.loadScenario(config) ;

        // possibly modify scenario here
        setUpFahrradRing(scenario);
        // ---

        Controler controler = new Controler( scenario ) ;

        // possibly modify controler here

        //	controler.addOverridingModule( new OTFVisLiveModule() ) ;

        // ---

        controler.run();
    }

    private static void setUpFahrradRing(Scenario scenario) {
        var sbahnRingFilePath = "original-input-data/Umweltzone_-_Berlin-shp/Umweltzone.shp";
        var features = ShapeFileReader.getAllFeatures(sbahnRingFilePath);
        var sbahnRing = features.stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .collect(Collectors.toList()).get(0);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");

        // find streets within sbahn ring and remove car as allowed transport mode
        for (var link: scenario.getNetwork().getLinks().entrySet()) {
            System.out.println(link.getKey() + " " + link.getValue());
            var from = MGC.coord2Point(transformation.transform(link.getValue().getFromNode().getCoord()));
            var to = MGC.coord2Point(transformation.transform(link.getValue().getToNode().getCoord()));

            if (sbahnRing.contains(from) || sbahnRing.contains(to)) {
                if (link.getValue().getAllowedModes().stream().count() == 1 && link.getValue().getAllowedModes().toArray()[0] == "car") {
                    // Basically delete link by making it super slow
                    link.getValue().setFreespeed(0.0000000001); // arbitrary but super small number
                } else  {
                    var allowedModes = link.getValue().getAllowedModes();
                    var newModes = allowedModes.stream().filter(mode -> mode != "car").collect(Collectors.toSet());
                    link.getValue().setAllowedModes(newModes);
                }
            }
        }
    }
}