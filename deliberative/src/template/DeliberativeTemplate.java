package template;

/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	private static final boolean PICKUP = true;
	private static final boolean DELIVERY = false;
	private static final char NOTASK = '0';
	private static final char PICKEDUP = '1';
	private static final char DELIVERED = '2';

	enum Algorithm { BFS, ASTAR }

	/* Environment */
	Topology topology;
	TaskDistribution td;

	/* the properties of the agent */
	Agent agent;
	int capacity;
	double costPerKm;

	/* useful when the plan has to be recomputed*/
	TaskSet carriedTasks;

	/* the planning class */
	Algorithm algorithm;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;

		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");

		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());

		this.costPerKm = agent.vehicles().get(0).costPerKm();
		this.capacity = agent.vehicles().get(0).capacity();
	}

	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {

		City currentCity = vehicle.getCurrentCity();
		Plan plan = new Plan(currentCity);

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
			case ASTAR:
				// compute the search with the A* algorithm
				plan = ASTARPlan(currentCity, tasks);
				break;
			case BFS:
				// compute the search with the Breadth-First Search algorithm
				plan = BFSPlan(currentCity, tasks);
				break;
			default:
				throw new AssertionError("Should not happen.");
		}
		return plan;
	}


	private Plan BFSPlan(City city, TaskSet tasks) {

		// to record the performance
		long startTime = System.currentTimeMillis();

		Plan plan = new Plan(city);
		double minCost = Double.POSITIVE_INFINITY;
		State minState = null;

		// when the plan has to be recomputed in the middle of the simulation,
		// store the tasks picked up but not delivered
		if (carriedTasks != null) 
			tasks.addAll(carriedTasks);

		int size = tasks.size();
		Task[] tasksArray = tasks.toArray(new Task[size]);

		// initialize the status of the different tasks
		String intialtaskStatus = "";
		for (int i = 0; i < size; i++) {
			if (carriedTasks != null && carriedTasks.contains(tasksArray[i])) 
				intialtaskStatus = intialtaskStatus + PICKEDUP;
			else 
				intialtaskStatus = intialtaskStatus + NOTASK;
		}

		// initialize the tree
		HashSet<State> searchStateSet = new HashSet<State>();
		State startState = new State(null, city, null, intialtaskStatus, 0, 0, true);
		LinkedList<State> queue = new LinkedList<State>();
		queue.add(startState);

		do {
			if (queue.isEmpty()) break;

			State currentState = queue.pop();
			String curenttaskStatus = currentState.getTaskStatus();

			// if all the tasks have been delivered
			if (curenttaskStatus.replace("2", "").length() == 0) {
				double currentCost = currentState.getCost();
				//if the total cost is better (e.g. lower) than everything found before, it's the new "best" cost
				if (currentCost < minCost) {
					minCost = currentCost;
					minState = currentState;
				}
				continue;
			}

			if (currentState == null) break;
			else {
				if (!searchStateSet.contains(currentState)) {

					City currentLocation = currentState.getPosition();
					double currentCost = currentState.getCost();
					int currentWeight = currentState.getTotalWeight();
					searchStateSet.add(currentState);

					// create the child states according to the task status
					for (int i = 0; i < size; i++) {

						Task possibleTask = tasksArray[i];

						if (curenttaskStatus.charAt(i) == NOTASK) {
							int updatedWeight = possibleTask.weight + currentWeight;
							if (updatedWeight <= capacity) {
								City departure = possibleTask.pickupCity;
								double childCost = currentCost + costPerKm * currentLocation.distanceTo(departure);
								char[] childStaskStatus = curenttaskStatus.toCharArray();
								childStaskStatus[i] = PICKEDUP;
								State childState = new State(currentState, departure, possibleTask,
										new String(childStaskStatus), updatedWeight, childCost, PICKUP);
								queue.add(childState);
							}

						} else if (curenttaskStatus.charAt(i) == PICKEDUP) {
							City destination = possibleTask.deliveryCity;
							double childCost = currentCost + costPerKm * currentLocation.distanceTo(destination);
							char[] childStaskStatus = curenttaskStatus.toCharArray();
							childStaskStatus[i] = DELIVERED;
							int updatedWeight = currentWeight - possibleTask.weight;
							State childState = new State(currentState, destination, possibleTask,
									new String(childStaskStatus), updatedWeight, childCost, DELIVERY);
							queue.add(childState);
						}
					}
				}
			}

		} while (true);

		getPlanFromTree(minState, plan);

		// record and print the algorithm performance
		long endTime = System.currentTimeMillis();
		System.out.println("BFS execution time: " + (endTime - startTime) +  "ms Minumum Cost: " + minCost);

		return plan;
	}

	private Plan ASTARPlan(City city, TaskSet tasks) {

		// to record the performance
		long startTime = System.currentTimeMillis();

		Plan plan = new Plan(city);
		double minCost = Double.POSITIVE_INFINITY;
		State minCostState = null;

		// when the plan has to be recomputed in the middle of the simulation,
		// store the tasks picked up but not delivered
		if (carriedTasks != null) 
			tasks.addAll(carriedTasks);

		int numOfTasks = tasks.size();
		Task[] tasksArray = tasks.toArray(new Task[numOfTasks]);
		String initTaskStatus = "";

		// initialize the status of the different tasks
		for (int i = 0; i < numOfTasks; i++) {
			if (carriedTasks != null && carriedTasks.contains(tasksArray[i])) 
				initTaskStatus = initTaskStatus + PICKEDUP;
			else 
				initTaskStatus = initTaskStatus + NOTASK;
		}

		// initialize the tree
		HashSet<State> searchStateSet = new HashSet<State>();
		StateComparator stateComparator = new StateComparator(tasksArray, costPerKm);
		PriorityQueue<State> queue = new PriorityQueue<State>(100000, stateComparator);
		State startState = new State(null, city, null, initTaskStatus, 0, 0, true);

		queue.add(startState);

		do {
			if (queue.isEmpty()) break;

			State currentState = queue.poll();
			String currenttaskStatus = currentState.getTaskStatus();

			// if all the tasks have been delivered
			if (currenttaskStatus.replaceAll("2", "").length() == 0) {
				//if the total cost is better (e.g. lower) than everything found before, it's the new "best" cost
				minCost = currentState.getCost();
				minCostState = currentState;
				break;
			}

			if (currentState == null) break;
			else {
				if (!searchStateSet.contains(currentState)) {

					City currentposition = currentState.getPosition();
					double currentCost = currentState.getCost();
					int currentWeight = currentState.getTotalWeight();
					searchStateSet.add(currentState);

					// create the child states according to the task status
					for (int i = 0 ; i < numOfTasks ; i++) {
						Task possibleTask = tasksArray[i];

						if (currenttaskStatus.charAt(i) == NOTASK) {
							int possibleWeight = possibleTask.weight + currentState.getTotalWeight();
							if (possibleWeight <= capacity) {
								City possibleDeparture = possibleTask.pickupCity;
								double possibleCost = currentState.getCost() + costPerKm*currentposition.distanceTo(possibleDeparture);
								char[] possibletaskStatus = currenttaskStatus.toCharArray();
								possibletaskStatus[i] = PICKEDUP;

								State possibleState = new State(currentState, possibleDeparture, possibleTask,
										new String(possibletaskStatus), possibleWeight, possibleCost, PICKUP);
								queue.add(possibleState);
							}

						} else if (currenttaskStatus.charAt(i) == PICKEDUP) {
							City possibleDestination = possibleTask.deliveryCity;
							double possibleCost = currentCost + costPerKm * currentposition.distanceTo(possibleDestination);
							char[] possibletaskStatus = currenttaskStatus.toCharArray();
							possibletaskStatus[i] = DELIVERED;
							int possibleWeight = currentWeight - possibleTask.weight;
							State possibleState = new State(currentState, possibleDestination, possibleTask,
									new String(possibletaskStatus), possibleWeight, possibleCost, DELIVERY);
							queue.add(possibleState);
						}
					}
				}
			}
		} while (true);

		// record and print the algorithm performance
		long endTime = System.currentTimeMillis();
		System.out.println("Astar execution Time: " + (endTime - startTime) + " ms Minumum Cost: " + minCost);

		getPlanFromTree(minCostState, plan);

		return plan;
	}

	public void getPlanFromTree(State state, Plan plan) {
		State parentState = state.getParentSate();
		if (parentState != null) {
			getPlanFromTree(parentState, plan);
			Task rationTask = state.getTask();
			City parentCity = parentState.getPosition();
			City currentCity = state.getPosition();

			for (City city : parentCity.pathTo(currentCity)) 
				plan.appendMove(city);
			
			if (state.pickUp()) 
				plan.appendPickup(rationTask);
			else 
				plan.appendDelivery(rationTask);
		}
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup position
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup position => delivery position
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {

		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
			this.carriedTasks = carriedTasks;
		}
	}
}