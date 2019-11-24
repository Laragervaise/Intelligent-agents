package AuctionAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import AuctionAgent.State.Type;
import logist.task.Task;
import logist.task.TaskSet;

public class PDP {
	
	private static final double PROB = 0.8;

	private List<MyVehicle> vehicles;
	private TaskSet tasks;
	private static double prop = PROB*100;
	private CentralizedPlan bestPlan;
	private double minCost = Integer.MAX_VALUE;

	public CentralizedPlan getBestPlan() {
		return bestPlan;
	}
	
	public PDP(List<MyVehicle> vehicles, TaskSet tasks) {
		super();
		this.vehicles = vehicles;
		this.tasks = tasks;
	}

	public void SLS(double allowedTime, CentralizedPlan plan, int nbIter) {

		long startTime = System.currentTimeMillis();
		bestPlan = plan;
		for (int i = 0; i < nbIter; i++) {

			// To prevent from time out
			if (System.currentTimeMillis() - startTime > allowedTime) {
				return;
			}
			
			CentralizedPlan oldPlan = plan;
			ArrayList<CentralizedPlan> candidatePlans = ChooseNeighbours(oldPlan);
			plan = localChoice(oldPlan, candidatePlans);
		}
	}

	public CentralizedPlan SLSfirst(double allowedTime, int nbIter) {

		long startTime = System.currentTimeMillis();
		CentralizedPlan plan = SelectInitialSolution();
		bestPlan = plan;

		for (int i = 0; i < nbIter*10; i++) {

			// To prevent from time out
			if (System.currentTimeMillis() - startTime > allowedTime) {
				return bestPlan;
			}
			
			CentralizedPlan oldPlan = plan;
			ArrayList<CentralizedPlan> candidatePlans = ChooseNeighbours(oldPlan);
			plan = localChoice(oldPlan, candidatePlans);
		}
		return plan;
	}
	
	public CentralizedPlan SelectInitialSolution() {

		int minCapacity = Integer.MIN_VALUE;
		MyVehicle selectedVehicle = null;
		
		// select the vehicle with the biggest capacity
		for (MyVehicle vehicle : vehicles) {
			if (vehicle.getCapacity() > minCapacity) {
				minCapacity = vehicle.getCapacity();
				selectedVehicle = vehicle;
			}
		}

		HashMap<MyVehicle, LinkedList<State>> stateMap = new HashMap<MyVehicle, LinkedList<State>>();
		LinkedList<State> StateList = new LinkedList<State>();

		// assign all the tasks to the selected vehicle
		for (Task task : tasks) {
			State pickupState = new State(Type.PICKUP, task);
			State deliverState = new State(Type.DELIVERY, task);
			StateList.addLast(pickupState);
			StateList.addLast(deliverState);
		}

		for (MyVehicle vehicle : vehicles) {
			stateMap.put(vehicle, new LinkedList<State>());
		}

		//link the tasks states list to the vehicle
		stateMap.put(selectedVehicle, StateList);
		
		//this vehicle carrying all the tasks is the first plan
		CentralizedPlan initialPlan = new CentralizedPlan();
		initialPlan.setVehicleStates(stateMap);
		return initialPlan;
	}
	
	private CentralizedPlan localChoice(CentralizedPlan oldPlan, ArrayList<CentralizedPlan> candidatePlans) {

		CentralizedPlan returnPlan = oldPlan;
		CentralizedPlan minCostPlan = null;
		double minCost = Integer.MAX_VALUE;
		
		// find the cheapest plan across the neighboring plans
		for (CentralizedPlan plan : candidatePlans) {
			double tmpCost = plan.cost();
			if (tmpCost < minCost) {
				minCostPlan = plan;
				minCost = tmpCost;
			}
		}

		if (minCost < this.minCost) {
			this.bestPlan = minCostPlan;
			this.minCost = minCost;
		}

		// generate a random number
		Random random = new Random();
		int num = random.nextInt(100);
		
		if (num < prop) {
			returnPlan = minCostPlan;
		} else {
			returnPlan = candidatePlans.get(random.nextInt(candidatePlans.size()));
		}
		return returnPlan;
	}

	public ArrayList<CentralizedPlan> ChooseNeighbours(CentralizedPlan oldPlan) {
		ArrayList<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();

		// compare all the vehicles to each other
		for (MyVehicle vehicle1 : vehicles) {
			for (MyVehicle vehicle2 : vehicles) {
				LinkedList<State> vehicleStates = oldPlan.getVehicleStates().get(vehicle1);
				
				// go across all states of vehicle1 if it exists
				if (vehicle2 != vehicle1 && vehicleStates.size() > 0) {
					for (int i = 0; i < oldPlan.getVehicleStates().get(vehicle1).size(); i++) {
						
						// get the state we want to move to another vehicle
						State exchangeState = oldPlan.getVehicleStates().get(vehicle1).get(0);
						
						// create a new list of plans by transferring the first task of vehicle1 to vehicle2
						// if the vehicle2 maximum capacity isn't reached yet
						if (exchangeState.getCurrentTask().weight <= vehicle2.getCapacity()) {
							List<CentralizedPlan> planList = changingVehicle(oldPlan, vehicle1, vehicle2);
							
							// add the new plans to our list of candidate plans if they don't violate our constraints
							for (CentralizedPlan plan : planList) {
								if (!plan.violateConstraints()) {
									candidatePlans.add(plan);
								}
							}
						}
					}
				}
			}
		}

		Random random = new Random();
		MyVehicle randomVehicle = vehicles.get(random.nextInt(vehicles.size()));
		LinkedList<State> vehicleState = oldPlan.getVehicleStates().get(randomVehicle);

		int length = vehicleState.size();
		
		// if the vehicle has multiple tasks
		if (length > 2) {
			for (int stateId = 0; stateId < length; stateId++) {
				
				// if it's a state where the task has to be picked up
				if (vehicleState.get(stateId).type == Type.PICKUP) {
					
					// create a new list of plans by reversing the vehicle1 tasks
					List<CentralizedPlan> planList = changingTaskOrder(oldPlan, randomVehicle, stateId);
					
					// add the new plans to our list of candidate plans if they don't violate our constraints
					for (CentralizedPlan plan : planList) {
						if (!plan.violateConstraints()) {
							candidatePlans.add(plan);
						}
					}
				}
			}
		}
		return candidatePlans;
	}

	public List<CentralizedPlan> changingVehicle(CentralizedPlan oldPlan, MyVehicle v1, MyVehicle v2) {
		// transfer the first task of v1 to v2

		CentralizedPlan newPlan = null;
		
		try {
			newPlan = (CentralizedPlan) oldPlan.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

		// get vehicle 1 first task  and remove it from the list
		Task startTask = newPlan.getVehicleStates().get(v1).get(0).getCurrentTask();																				
		newPlan.removeTask(startTask);

		State pickupState = new State(Type.PICKUP, startTask);
		State deliverState = new State(Type.DELIVERY, startTask);

		// get vehicle 2 State list add at its beginning the task that has just been removed
		LinkedList<State> stateListV2 = newPlan.getVehicleStates().get(v2);
		stateListV2.addFirst(pickupState);

		List<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();
		
		// handle corresponding delivery state
		for (int i = 1; i <= stateListV2.size(); i++) {

			LinkedList<State> stateListClone = (LinkedList<State>) stateListV2.clone();
			stateListClone.add(i, deliverState);
			CentralizedPlan clonePlan;
			
			try {
				clonePlan = (CentralizedPlan) newPlan.clone();
				clonePlan.getVehicleStates().put(v2, stateListClone);
				candidatePlans.add(clonePlan);
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}

		}
		return candidatePlans;

	}

	private List<CentralizedPlan> changingTaskOrder(CentralizedPlan oldPlan, MyVehicle vehicle, int stateId) {

		List<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();

		// clone the old plan
		CentralizedPlan newPlan = null;
		try {
			newPlan = (CentralizedPlan) oldPlan.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}

		// get the first stateID task of the vehicle and remove it from the list
		LinkedList<State> StateList = newPlan.getVehicleStates().get(vehicle);
		Task task = StateList.get(stateId).getCurrentTask();
		newPlan.removeTask(task);

		State pickupState = new State(Type.PICKUP, task);
		State deliveryState = new State(Type.DELIVERY, task);

		// clone the new plan and the vehicle states list and edit them
		for (int index1 = 0; index1 <= StateList.size(); index1++) {
			for (int index2 = index1 + 1; index2 <= StateList.size() + 1; index2++) {

				CentralizedPlan ClonePlan;
				try {

					ClonePlan = (CentralizedPlan) newPlan.clone();
					LinkedList<State> CloneList = (LinkedList<State>) StateList.clone();
					CloneList.add(index1, pickupState);
					CloneList.add(index2, deliveryState);
					ClonePlan.getVehicleStates().put(vehicle, CloneList);
					candidatePlans.add(ClonePlan);

				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
				}
			}
		}

		return candidatePlans;
	}

}
