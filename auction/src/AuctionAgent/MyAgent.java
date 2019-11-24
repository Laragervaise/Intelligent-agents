package AuctionAgent;

//the list of imports
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import AuctionAgent.State.Type;
import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;


@SuppressWarnings("unused")
public class MyAgent implements AuctionBehavior {
	
	private static final double FACTOR = 0.75;
	private static final int NB_FIRST_BIDS = 6;
	private static final int LARGE_ITER = 2000;
	private static final int MEDIUM_ITER = 850;
	private static final int SMALL_ITER = 500;

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;

	private PDPlan myPlan;
	private PDPlan oppPlan;

	private double myCost;
	private double myNewCost;
	private double oppCost;
	private double oppNewCost;

	private List<MyVehicle> vehiclesList;
	private List<MyVehicle> oppVehicles;

	private List<City> allCities;
	private List<City> vehicleCities;

	private double initialBidRatio = 0.5;
	private double oppRatio = 0.85;
	private double marginBidRatio = 0.8;

	final static double oppRatioUpper = 0.9;
	final static double oppRatioLower = 0.8;
	final static double myRatioUpper = 0.85;
	final static double myRatioLower = 0.75;

	private double bidOppMin = Double.MAX_VALUE;
	private int round = 0;
	private long allowedTime = 40000L;

	private double bidAboutPositionMin = 0.9;
	private double bidAboutPositionMax = 1.1;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;

		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		allowedTime = ls.get(LogistSettings.TimeoutKey.PLAN);

		List<Vehicle> vehicles = agent.vehicles();
		vehiclesList = new ArrayList<MyVehicle>(vehicles.size());
		oppVehicles = new ArrayList<MyVehicle>(vehicles.size());

		allCities = topology.cities();
		vehicleCities = new ArrayList<Topology.City>();

		for (Vehicle vehicle : vehicles) {
			MyVehicle myVehicle = new MyVehicle(vehicle);
			vehiclesList.add(myVehicle);
			vehicleCities.add(vehicle.homeCity());
		}

		for (Vehicle vehicle : vehicles) {
			Random random = new Random();
			City randomCity;
			do {
				int randomNum = random.nextInt(allCities.size());
				randomCity = allCities.get(randomNum);
			} while (vehicleCities.contains(randomCity));

			MyVehicle oppVehicle = new MyVehicle(null, randomCity, vehicle.capacity(), vehicle.costPerKm());
			oppVehicles.add(oppVehicle);
		}

		this.myPlan = new PDPlan(vehiclesList);
		this.oppPlan = new PDPlan(oppVehicles);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		double myBid = bids[agent.id()];
		double oppBid = bids[1 - agent.id()];
		
		// update the opponent overall lowest bid
		if (oppBid < bidOppMin) {
			bidOppMin = oppBid;
		}
		
		// update oppRatio and marginBidRatio according to the outcome of the round
		if (winner == agent.id()) {
			
			myCost = myNewCost;
			myPlan.updatePlan();

			marginBidRatio = Math.min(myRatioLower, marginBidRatio + 0.01);
			oppRatio = Math.min(oppRatioLower, oppRatio + 0.01);

		} else {
			
			oppCost = oppNewCost;
			oppPlan.updatePlan();

			marginBidRatio = Math.max(myRatioUpper, marginBidRatio - 0.01);
			oppRatio = Math.max(oppRatioUpper, oppRatio - 0.01);
		}

		// some computations for the first round
		if (round == 1) {
			
			City predictCity = null;
			double costDiff = Double.MAX_VALUE;
			
			for (City city : allCities) {
				
				if (!vehicleCities.contains(city)) {
					double diff = Math.abs((city.distanceTo(previous.pickupCity)
							+ previous.pickupCity.distanceTo(previous.deliveryCity)) * oppVehicles.get(0).getCostPerKm() - oppBid);
					
					if (diff < costDiff) {
						costDiff = diff;
						predictCity = city;
					}
				}
			}
			oppVehicles.get(0).setInitCity(predictCity);
		}
	}

	@Override
	public Long askPrice(Task task) {

		// if none of the vehicles can carry the task, no bid
		if (myPlan.getBiggestVehicle().getCapacity() < task.weight)
			return null;

		myNewCost = myPlan.solveWithNewTask(task).cost();
		oppNewCost = oppPlan.solveWithNewTask(task).cost();

		double myMarginalCost = myNewCost - myCost;
		double oppMarginalCost = oppNewCost - oppCost;
		
		// adjust the bid relatively to the opponent 
		double mybid = oppMarginalCost * oppRatio;

		// adjust with the lower limit if necessary
		if (mybid < marginBidRatio * myMarginalCost) {
			mybid = marginBidRatio * myMarginalCost;
		}

		// adjust the bid when it's lower than the overall lowest opponent bid 
		if (round > 0 && mybid < bidOppMin) {
			mybid = Math.max(bidOppMin - 1, 0);
		}

		// lower the bid for the first rounds
		int firstRounds = NB_FIRST_BIDS;
		
		if (round < firstRounds) {
			mybid = initialBidRatio * mybid;
		}

		round++;

		return (long) Math.floor(mybid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
		PDPlan pdplan = new PDPlan(vehiclesList);
		pdplan.solveWithTaskSet(tasks);
		List<Plan> plans = new ArrayList<Plan>();
		PDP pdpAlg = new PDP(vehiclesList, tasks);
		
		int nbIter = LARGE_ITER;
		
		if (tasks.size()>35) {
			nbIter = SMALL_ITER;
		} else if (tasks.size()>25){
			nbIter = MEDIUM_ITER;
		}
		
		pdpAlg.SLS(allowedTime*FACTOR, pdplan.getBestPlan(), nbIter);
		pdpAlg.SLSfirst(allowedTime*FACTOR, nbIter);
		CentralizedPlan selectedPlan = pdplan.getBestPlan().cost() < pdpAlg.getBestPlan().cost() ? 
				pdplan.getBestPlan() : pdpAlg.getBestPlan();

		System.out.println(myPlan.getBestPlan().cost() + " VS " + selectedPlan.cost());

		for (MyVehicle vehicle : vehiclesList) {
			plans.add(makePlan(vehicle, selectedPlan.getVehicleStates().get(vehicle)));
		}

		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		return plans;
	}

	private Plan makePlan(MyVehicle vehicle, LinkedList<State> linkedList) {

		City currentCity = vehicle.getInitCity();
		Plan plan = new Plan(currentCity);

		for (State State : linkedList) {
			
			// move to the pickup location
			if (State.type == Type.PICKUP) {
				City nextCity = State.getCurrentTask().pickupCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;
				plan.appendPickup(State.getCurrentTask());
			
			// move to the delivery location
			} else {
				City nextCity = State.getCurrentTask().deliveryCity;
				for (City city : currentCity.pathTo(nextCity)) {
					plan.appendMove(city);
				}
				currentCity = nextCity;
				plan.appendDelivery(State.getCurrentTask());
			}
		}

		return plan;
	}
}
