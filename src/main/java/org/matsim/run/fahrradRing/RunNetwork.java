package org.matsim.run.fahrradRing;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.run.RunBerlinScenario;

import java.util.Set;

import static org.matsim.run.fahrradRing.RunFahrradRing.*;

public class RunNetwork {
    private static final Logger log = Logger.getLogger(RunBerlinScenario.class );

    public static void main(String[] args) {

        for (String arg : args) {
            log.info( arg );
        }

        if ( args.length==0 ) {
            args = new String[] {"scenarios/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml"}  ;
        }

        Config config = prepareConfig( args ) ;
        config.network().setInputFile("berlin-1pct_nein.config.xml");
        //config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.);
        //config.plansCalcRoute().addParam("insertingAccessEgressWalk", "true");

        Scenario scenario = prepareScenario( config ) ;

        var cleaner = new MultimodalNetworkCleaner(scenario.getNetwork());
        cleaner.run(Set.of(TransportMode.car));
        cleaner.removeNodesWithoutLinks();


        Controler controler = prepareControler( scenario ) ;
        controler.getConfig().plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        controler.getConfig().controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);
        controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
        controler.run();
    }
}
