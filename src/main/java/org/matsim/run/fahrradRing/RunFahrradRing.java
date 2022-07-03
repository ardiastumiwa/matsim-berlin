/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.run.fahrradRing;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.inject.Singleton;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.analysis.RunPersonTripAnalysis;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.prepare.population.AssignIncome;
import org.matsim.run.BerlinExperimentalConfigGroup;
import org.matsim.run.RunBerlinScenario;
import org.matsim.run.drt.OpenBerlinIntermodalPtDrtRouterModeIdentifier;
import org.matsim.run.drt.RunDrtOpenBerlinScenario;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

public final class RunFahrradRing {

    private static final Logger log = Logger.getLogger(RunBerlinScenario.class );

    public static void main(String[] args) {

        for (String arg : args) {
            log.info( arg );
        }

        if ( args.length==0 ) {
            args = new String[] {"scenarios/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml"}  ;
        }

        Config config = prepareConfig( args ) ;
        Scenario scenario = prepareScenario( config ) ;

        setUpFahrradRing(scenario); // Different to default scenario

        Controler controler = prepareControler( scenario ) ;
        controler.run();
    }

    private static void setUpFahrradRing(Scenario scenario) {
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
                    link.getValue().setAllowedModes(Set.of(TransportMode.pt, TransportMode.bike));

                    // road to hell
                    // baustelle


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
                                    u.setRoute(null);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("fedsch!");
    }

    public static HashMap<Plan, List<Leg>> createDictionary(Plan plan) {
        var bla = new HashMap<Plan,List<Leg>>();
        var legs = plan.getPlanElements().stream().filter(el -> el instanceof Leg).map(leg -> (Leg) leg).collect(Collectors.toList());
        bla.put(plan, legs);

        return bla;
    }

    public static Controler prepareControler( Scenario scenario ) {
        // note that for something like signals, and presumably drt, one needs the controler object

        Gbl.assertNotNull(scenario);

        final Controler controler = new Controler( scenario );

        if (controler.getConfig().transit().isUseTransit()) {
            // use the sbb pt raptor router
            controler.addOverridingModule( new AbstractModule() {
                @Override
                public void install() {
                    install( new SwissRailRaptorModule() );
                }
            } );
        } else {
            log.warn("Public transit will be teleported and not simulated in the mobsim! "
                    + "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
                    + "Should only be used for testing or car-focused studies with a fixed modal split.  ");
        }



        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule( new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding( TransportMode.ride ).to( networkTravelTime() );
                addTravelDisutilityFactoryBinding( TransportMode.ride ).to( carTravelDisutilityFactoryKey() );
                bind(AnalysisMainModeIdentifier.class).to(OpenBerlinIntermodalPtDrtRouterModeIdentifier.class);

                //use income-dependent marginal utility of money for scoring
                bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).in(Singleton.class);
            }
        } );

        return controler;
    }

    public static Scenario prepareScenario( Config config ) {
        Gbl.assertNotNull( config );

        // note that the path for this is different when run from GUI (path of original config) vs.
        // when run from command line/IDE (java root).  :-(    See comment in method.  kai, jul'18
        // yy Does this comment still apply?  kai, jul'19

        /*
         * We need to set the DrtRouteFactory before loading the scenario. Otherwise DrtRoutes in input plans are loaded
         * as GenericRouteImpls and will later cause exceptions in DrtRequestCreator. So we do this here, although this
         * class is also used for runs without drt.
         */
        final Scenario scenario = ScenarioUtils.createScenario( config );

        RouteFactories routeFactories = scenario.getPopulation().getFactory().getRouteFactories();
        routeFactories.setRouteFactory(DrtRoute.class, new DrtRouteFactory());

        ScenarioUtils.loadScenario(scenario);

        BerlinExperimentalConfigGroup berlinCfg = ConfigUtils.addOrGetModule(config, BerlinExperimentalConfigGroup.class);
        if (berlinCfg.getPopulationDownsampleFactor() != 1.0) {
            downsample(scenario.getPopulation().getPersons(), berlinCfg.getPopulationDownsampleFactor());
        }

        AssignIncome.assignIncomeToPersonSubpopulationAccordingToGermanyAverage(scenario.getPopulation());
        return scenario;
    }

    public static Config prepareConfig( String [] args, ConfigGroup... customModules ){
        return prepareConfig( RunDrtOpenBerlinScenario.AdditionalInformation.none, args, customModules ) ;
    }
    public static Config prepareConfig( RunDrtOpenBerlinScenario.AdditionalInformation additionalInformation, String [] args,
                                        ConfigGroup... customModules ) {
        OutputDirectoryLogging.catchLogEntries();

        String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );

        ConfigGroup[] customModulesToAdd;
        if (additionalInformation == RunDrtOpenBerlinScenario.AdditionalInformation.acceptUnknownParamsBerlinConfig) {
            customModulesToAdd = new ConfigGroup[]{new BerlinExperimentalConfigGroup(true)};
        } else {
            customModulesToAdd = new ConfigGroup[]{new BerlinExperimentalConfigGroup(false)};
        }
        ConfigGroup[] customModulesAll = new ConfigGroup[customModules.length + customModulesToAdd.length];

        int counter = 0;
        for (ConfigGroup customModule : customModules) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        for (ConfigGroup customModule : customModulesToAdd) {
            customModulesAll[counter] = customModule;
            counter++;
        }

        final Config config = ConfigUtils.loadConfig( args[ 0 ], customModulesAll );

        config.controler().setRoutingAlgorithmType( FastAStarLandmarks );

        config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );

        config.plansCalcRoute().setRoutingRandomness( 3. );
        config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride);
        config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt);
        config.plansCalcRoute().removeModeRoutingParams(TransportMode.bike);
        config.plansCalcRoute().removeModeRoutingParams("undefined");

        config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles( true );

        // vsp defaults
        config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info );
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        config.qsim().setUsingTravelTimeCheckInTeleportation( true );
        config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );

        // activities:
        for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
            config.planCalcScore().addActivityParams( new ActivityParams( "home_" + ii + ".0" ).setTypicalDuration( ii ) );
            config.planCalcScore().addActivityParams( new ActivityParams( "work_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(6. * 3600. ).setClosingTime(20. * 3600. ) );
            config.planCalcScore().addActivityParams( new ActivityParams( "leisure_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(9. * 3600. ).setClosingTime(27. * 3600. ) );
            config.planCalcScore().addActivityParams( new ActivityParams( "shopping_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(8. * 3600. ).setClosingTime(20. * 3600. ) );
            config.planCalcScore().addActivityParams( new ActivityParams( "other_" + ii + ".0" ).setTypicalDuration( ii ) );
        }
        config.planCalcScore().addActivityParams( new ActivityParams( "freight" ).setTypicalDuration( 12.*3600. ) );

        ConfigUtils.applyCommandline( config, typedArgs ) ;

        return config ;
    }

    public static void runAnalysis(Controler controler) {
        Config config = controler.getConfig();

        String modesString = "";
        for (String mode: config.planCalcScore().getAllModes()) {
            modesString = modesString + mode + ",";
        }
        // remove last ","
        if (modesString.length() < 2) {
            log.error("no valid mode found");
            modesString = null;
        } else {
            modesString = modesString.substring(0, modesString.length() - 1);
        }

        String[] args = new String[] {
                config.controler().getOutputDirectory(),
                config.controler().getRunId(),
                "null", // TODO: reference run, hard to automate
                "null", // TODO: reference run, hard to automate
                config.global().getCoordinateSystem(),
                "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/projects/avoev/shp-files/shp-bezirke/bezirke_berlin.shp",
                TransformationFactory.DHDN_GK4,
                "SCHLUESSEL",
                "home",
                "10", // TODO: scaling factor, should be 10 for 10pct scenario and 100 for 1pct scenario
                "null", // visualizationScriptInputDirectory
                modesString
        };

        try {
            RunPersonTripAnalysis.main(args);
        } catch (IOException e) {
            log.error(e.getStackTrace());
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void downsample( final Map<Id<Person>, ? extends Person> map, final double sample ) {
        final Random rnd = MatsimRandom.getLocalInstance();
        log.warn( "Population downsampled from " + map.size() + " agents." ) ;
        map.values().removeIf( person -> rnd.nextDouble() > sample ) ;
        log.warn( "Population downsampled to " + map.size() + " agents." ) ;
    }
}

