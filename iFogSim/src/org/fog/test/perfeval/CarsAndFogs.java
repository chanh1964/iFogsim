package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for case study 1 - EEG Beam Tractor Game
 * @author Harshit Gupta
 *
 */
public class CarsAndFogs {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
//	static boolean CLOUD = false;
	static boolean CLOUD = true;
	
	//static int numOfAreas = 2;
	static int numOfFogNodes = 2;
	static int numOfCarsPerFogNode = 30;
	static double EEG_TRANSMISSION_TIME = 100;
	//static double EEG_TRANSMISSION_TIME = 10;
	
	public static void main(String[] args) {

		Log.printLine("Starting DragonFly ITS...");
		Log.printLine("==========================");
		Log.printLine("EXECUTION IN " + ((CLOUD)? "CLOUD" : "FOG") + " MODE");
		Log.printLine("==========================");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "dragonfly_its"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			
			if(CLOUD){
				// if the mode of deployment is cloud-based
				/*moduleMapping.addModuleToDevice("connector", "cloud", numOfDepts*numOfMobilesPerDept); // fixing all instances of the Connector module to the Cloud
				moduleMapping.addModuleToDevice("fog", "cloud", numOfDepts*numOfMobilesPerDept); // fixing all instances of the Concentration Calculator module to the Cloud
*/				moduleMapping.addModuleToDevice("central", "cloud"); // fixing all instances of the Connector module to the Cloud
				moduleMapping.addModuleToDevice("fog", "cloud"); // fixing all instances of the Concentration Calculator module to the Cloud
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){
						//moduleMapping.addModuleToDevice("client", device.getName(), 1);  // fixing all instances of the Client module to the Smartphones
						moduleMapping.addModuleToDevice("client", device.getName());  // fixing all instances of the Client module to the Smartphones
					}
				}
			}else{
				// if the mode of deployment is cloud-based
				//moduleMapping.addModuleToDevice("connector", "cloud", numOfDepts*numOfMobilesPerDept); // fixing all instances of the Connector module to the Cloud
				moduleMapping.addModuleToDevice("central", "cloud"); // fixing all instances of the Connector module to the Cloud
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){
						//moduleMapping.addModuleToDevice("client", device.getName(), 1);  // fixing all instances of the Client module to the Smartphones
						moduleMapping.addModuleToDevice("client", device.getName());  // fixing all instances of the Client module to the Smartphones
						//moduleMapping.addModuleToDevice("fog", device.getName());
					}
					else if(device.getName().startsWith("d")){
						moduleMapping.addModuleToDevice("fog", device.getName());  // fixing all instances of the Client module to the Smartphones
					}
				}
				// rest of the modules will be placed by the Edge-ward placement policy
			}
			
			
			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 0, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("VRGame finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 684000, 128000, 18750, 18750, 0, 0.01, 120, 50); // creates the fog device Cloud at the apex of the hierarchy with level=0
		cloud.setParentId(-1);
		/*FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		*/
		fogDevices.add(cloud);
		//fogDevices.add(proxy);
		
		for(int i=0;i<numOfFogNodes;i++){
			addGw(i+"", userId, appId, cloud.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}
		
	}

	private static FogDevice addGw(String id, int userId, String appId, int parentId){
		FogDevice fogNode = createFogDevice("d-"+id, 2200, 1000, 6250, 6250, 1, 0.01, 5.1, 1.9);
		fogDevices.add(fogNode);
		fogNode.setParentId(parentId);
		fogNode.setUplinkLatency(100); // latency of connection between gateways and proxy server is 4 ms
		for(int i=0;i<numOfCarsPerFogNode;i++){
			String carId = id+"-"+i;
			FogDevice car = addMobile(carId, userId, appId, fogNode.getId()); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
			car.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
			fogDevices.add(car);
		}
		Actuator display = new Actuator("a-"+id, userId, appId, "TRAFFIC_LIGHTS");
		actuators.add(display);		
		display.setGatewayDeviceId(fogNode.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
		return fogNode;
	}
	
	private static FogDevice addMobile(String id, int userId, String appId, int parentId){
		FogDevice mobile = createFogDevice("m-"+id, 1000, 500, 1250, 1250, 2, 0.01, 1.9, 0.5);
		mobile.setParentId(parentId);
		for(int i=0;i<1;i++){
			Sensor eegSensor = new Sensor("s-"+id+"-"+i, "GPS", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
			sensors.add(eegSensor);
			eegSensor.setGatewayDeviceId(mobile.getId());
			eegSensor.setLatency(1.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
		}		
		/*Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);		
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
*/		return mobile;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the EEG Tractor Beam game application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
		
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("client", 10); // adding module Client to the application model
		application.addAppModule("fog", 10); // adding module Concentration Calculator to the application model
		application.addAppModule("central", 10); // adding module Connector to the application model
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */		
		application.addAppEdge("GPS", "client", 2, 1, "GPS", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("client", "fog", 3, 500, "REQUEST", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		//application.addAppEdge("fog", "central", 100, 10, 1000, "PLAYER_GAME_STATE", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("fog", "client", 2, 500, "UPDATE_NODE_ID", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
		//application.addAppEdge("central", "client", 100, 28, 1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge("fog", "TRAFFIC_LIGHTS", 500, 2, 500, "TRAFFIC_LIGHTS_CTRL", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
		//application.addAppEdge("client", "DISPLAY", 1, 1, "DISPLAY", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("client", "GPS", "REQUEST", new FractionalSelectivity(1.0)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG 
		//application.addTupleMapping("client", "UPDATE_NODE_ID", "DISPLAY", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
		//application.addTupleMapping("fog", "REQUEST", "TRAFFIC_LIGHTS_CTRL", new FractionalSelectivity(1.0));
		application.addTupleMapping("fog", "REQUEST", "UPDATE_NODE_ID", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR 
		//application.addTupleMapping("client", "GLOBAL_GAME_STATE", "GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE 
		//application.addTupleMapping("client", "GPS", "TRAFFIC_LIGHTS_CTRL", new FractionalSelectivity(1.0));
		/*
		 * Defining application loops to monitor the latency of. 
		 * Here, we add only one loop for monitoring : GPS(sensor) -> Client -> Concentration Calculator -> Client -> TRAFFIC_LIGHTS (actuator)
		 */
		if(!CLOUD){
			application.addAppEdge("fog", "central", 600, 30, 1000, "CONCENTRATION", Tuple.UP, AppEdge.MODULE);
			//application.addTupleMapping("fog", "REQUEST", "CONCENTRATION", new FractionalSelectivity(1.0));
		}
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("GPS");add("client");add("fog");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("fog");add("client");}});
		final AppLoop loop3 = new AppLoop(new ArrayList<String>(){{add("fog");add("TRAFFIC_LIGHTS");}});
		final AppLoop loop4 = new AppLoop(new ArrayList<String>(){{add("fog");add("central");}});

		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);add(loop3);add(loop4);}};
		application.setLoops(loops);
		
		return application;
	}
}