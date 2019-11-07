package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;

public class PDP {
	
	public final static boolean RAND_VEHICLE = true;
	
	public static final double NEW_PLAN_PROB = 0.35;
	private static final int MAX_ITER = 1000;
	
	private List<Vehicle> vehicles;
	private TaskSet tasks;
	private static double prop = NEW_PLAN_PROB*100;
	private CentralizedPlan bestPlan;
	private int minCost = Integer.MAX_VALUE;
	
	public PDP(List<Vehicle> vehicles, TaskSet tasks) {
		super();
		this.vehicles = vehicles;
		this.tasks = tasks;
	}
	
	public CentralizedPlan getBestPlan() {
		return bestPlan;
	}
	
	public CentralizedPlan SLS() {
		CentralizedPlan plan = SelectInitialSolution();
		
		for (int i = 0 ; i < MAX_ITER ; i++) {
			CentralizedPlan oldPlan = plan;
			ArrayList<CentralizedPlan> candidatePlans = ChooseNeighbours(oldPlan);
			plan = LocalChoice(candidatePlans, oldPlan);
		}
		return plan;
	}

	private CentralizedPlan LocalChoice(ArrayList<CentralizedPlan> candidatePlans, CentralizedPlan oldPlan) {
		CentralizedPlan returnPlan = oldPlan;
		CentralizedPlan minCostPlan = null;
		
		int minCost = Integer.MAX_VALUE;
		
		// find the cheapest plan across the neighboring plans
		for (CentralizedPlan plan : candidatePlans) {
			int cost = caculatePlanCost(plan);
			
			if (cost < minCost) {
				minCostPlan = plan;
				minCost = cost;
			}
		}
		
		// generate a random number
		Random random = new Random();
		int num = random.nextInt(100);
		
		
		// decide which plan to use according to the number
		
		// choose the cheapest neighboring plan
		if (num < prop) {
			returnPlan = minCostPlan;
			if (minCost < this.minCost) {
				this.bestPlan = returnPlan;
				this.minCost = minCost;
			}
		// keep the current plan
		} else if (num < 2*prop) {
			returnPlan = oldPlan;
		// choose a random plan across the neighboring plans
		} else { 
			returnPlan = candidatePlans.get(random.nextInt(candidatePlans.size()));
		}
		return returnPlan;
	}

	public int caculatePlanCost(CentralizedPlan plan) {
		
		int cost = 0;
		HashMap<Vehicle, LinkedList<State>> vehicleToState = plan.getNextStates();
		
		// iterate across all the vehicles
		for (Map.Entry<Vehicle, LinkedList<State>> entry : vehicleToState.entrySet()) {
			
			Vehicle vehicle = entry.getKey();
			LinkedList<State> nextState = entry.getValue();
			
			if (nextState != null && nextState.size() > 0) {
				
				// cost of the first task to its pick up city
				Task startTask = nextState.get(0).getCurrentTask();
				cost += vehicle.homeCity().distanceTo(startTask.pickupCity);
				
				for (int i = 0; i < nextState.size() - 1; i++) {

					State preState = nextState.get(i);
					State postState = nextState.get(i + 1);
					
					// for the next tasks, compute the cost according to the current task state and the one of the following
					if (postState.isPickup() == true) {
						if (preState.isPickup() == true) {
							cost += preState.getCurrentTask().pickupCity.distanceTo(postState.getCurrentTask().pickupCity)
								* vehicle.costPerKm();
						} else{
						cost += preState.getCurrentTask().deliveryCity.distanceTo(postState.getCurrentTask().pickupCity)
								* vehicle.costPerKm();
						}
					} else {
						if (preState.isPickup() == true) {
							cost += preState.getCurrentTask().pickupCity.distanceTo(postState.getCurrentTask().deliveryCity)
								* vehicle.costPerKm();
						} else {
							cost += preState.getCurrentTask().deliveryCity
								.distanceTo(postState.getCurrentTask().deliveryCity) * vehicle.costPerKm();
						}
					}

				}
			}
		}

		return cost;
	}	
	
	private ArrayList<CentralizedPlan> ChooseNeighbours(CentralizedPlan oldPlan) {
		ArrayList<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();
		
		// go across all vehicles
		for (Vehicle vehicle1 : vehicles) {
			if (oldPlan.getNextStates() != null) {
				for (Vehicle vehicle2 : vehicles) {
					if (vehicle2 != vehicle1) {

						LinkedList<State> vehicle1States = oldPlan.getNextStates().get(vehicle1);

						// go across all states of vehicle1 if it exists
						if (vehicle1States != null && vehicle1States.size() > 0) {
							for (int i = 0; i < oldPlan.getNextStates().get(vehicle1).size(); i++) {
								
								// get the state we want to move to another vehicle
								State exchangeState = oldPlan.getNextStates().get(vehicle1).get(0);

								// create a new list of plans by transferring the first task of vehicle1 to vehicle2
								// if the state exists and that the vehicle2 maximum capacity isn't reached yet
								if (exchangeState != null
										&& exchangeState.getCurrentTask().weight <= vehicle2.capacity()) {
									List<CentralizedPlan> planList = changeVehicle(oldPlan, vehicle1,
											vehicle2);

									// add the new plans to our list of candidate plans if they don't violate our constraints
									for (CentralizedPlan plan : planList) {
										if (!violatesConstraints(plan)) {
											candidatePlans.add(plan);
										}
									}
								}
							}
						}
						
						
						if (!RAND_VEHICLE) {
							if (vehicle1States != null) {
								int vehicle1StatesNum = vehicle1States.size();
								if (vehicle1StatesNum > 1) {
									for (int stateId = 0 ; stateId < vehicle1StatesNum ; stateId++) {

										// if it's a state where the task has to be picked up
										if (vehicle1States.get(stateId).isPickup()) {

											// create a new list of plans by reversing the vehicle1 tasks
											List<CentralizedPlan> planList = changeTaskOrder(oldPlan, vehicle1, stateId);

											// add the new plans to our list of candidate plans if they don't violate our constraints
											for (CentralizedPlan plan : planList) {
												if (!violatesConstraints(plan)) {
													candidatePlans.add(plan);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}	
		
		if (RAND_VEHICLE) {
		
			Random random = new Random();
			int randomVehicleNum = random.nextInt(vehicles.size());
			Vehicle randomVehicle = vehicles.get(randomVehicleNum);
			LinkedList<State> randomVehicleStates = oldPlan.getNextStates().get(randomVehicle);

			if (randomVehicleStates != null) {

				int length = randomVehicleStates.size();

				// if the vehicle has multiple tasks
				if (length > 2) {
					for (int stateId = 0; stateId < length; stateId++) {
						// if it's a state where the task has to be picked up
						if (randomVehicleStates.get(stateId).isPickup()) {

							// create a new list of plans by reversing the vehicle1 tasks
							List<CentralizedPlan> planList = changeTaskOrder(oldPlan, randomVehicle, stateId);

							for (CentralizedPlan plan : planList) {
								// add the new plans to our list of candidate plans if they don't violate our constraints
								if (!violatesConstraints(plan)) {
									candidatePlans.add(plan);
								}
							}
						}
					}
				}
			}
		}
		
		return candidatePlans;
	}


	public boolean violatesConstraints(CentralizedPlan plan) {

		HashMap<Vehicle, LinkedList<State>> planMap = plan.getNextStates();
		
		// iterate across all the task lists for all the vehicles
		for (Map.Entry<Vehicle, LinkedList<State>> entry : planMap.entrySet()) {
			
			Vehicle vehicle = entry.getKey();
			LinkedList<State> nextState = entry.getValue();
			HashMap<Task, Integer> map = new HashMap<Task, Integer>();
			
			if (nextState != null) {
				int load = 0;
				
				// iterate across all the tasks for a given vehicle
				for (int i = 0; i < nextState.size(); i++) {
					
					Task task = nextState.get(i).getCurrentTask();

					
					if (map.containsKey(task)) {
						
						int value = map.get(task);
						value++;
						
						// it cannot deliver twice the same task
						if (value > 2) {
							return true;
						}
						map.put(task, value);
						
					} else {
						map.put(task, 0);
					}

					// add the task weight if it's in the vehicle
					if (nextState.get(i).isPickup()) {
						load += nextState.get(i).getCurrentTask().weight;
						for (int j = 0; j < i; j++) {
							if (nextState.get(j).getCurrentTask() == task) {
								return true;
							}
						}
					} else {
						load -= nextState.get(i).getCurrentTask().weight;
						for (int j = i + 1; j < nextState.size(); j++) {
							if (nextState.get(j).getCurrentTask() == task) {
								return true;
							}
						}
					}

					//check if everything fits in the vehicle
					if (load > vehicle.capacity()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private List<CentralizedPlan> changeTaskOrder(CentralizedPlan oldPlan, Vehicle vehicle, int stateId) {
		
		// create a list of candidate plans
		List<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();
		CentralizedPlan newPlan = null;
		try {
			// clone the old plan
			newPlan = (CentralizedPlan) oldPlan.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		
		// get the list of states of the vehicle
		LinkedList<State> states = newPlan.getNextStates().get(vehicle);
		
		// get the state to move of the vehicle
		State movedState = states.get(stateId);
		// and create a state with this task to be delivered
		State deliveryState = new State(false, movedState.getCurrentTask());

		// remove the state to move of the vehicle from its states list
		for (int i = states.size() - 1; i >= 0; i--) {
			if (states.get(i).getCurrentTask() == movedState.getCurrentTask()) {
				states.remove(i);
			}
		}

		for (int index1 = 0 ; index1 <= states.size() ; index1++) {
			for (int index2 = index1 + 1 ; index2 <= states.size() + 1 ; index2++) {
				CentralizedPlan newPlanClone;
				
				// clone the new plan and the vehicle states list and edit them
				try {
					newPlanClone = (CentralizedPlan) newPlan.clone();
					@SuppressWarnings("unchecked")
					
					LinkedList<State> statesClone = (LinkedList<State>) states.clone();
					
					statesClone.add(index1, movedState);
					statesClone.add(index2, deliveryState);
					newPlanClone.getNextStates().put(vehicle, statesClone);
					candidatePlans.add(newPlanClone);

				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
				}
			}
		}

		return candidatePlans;
	}

	private List<CentralizedPlan> changeVehicle(CentralizedPlan oldPlan, Vehicle vehicle1, Vehicle vehicle2) {
		CentralizedPlan newPlan = null;
		
		try {
			newPlan = (CentralizedPlan) oldPlan.clone();
		}
		catch(CloneNotSupportedException e) {
			e.printStackTrace();
		}
		
		State firstStateVehicle1 = newPlan.getNextStates().get(vehicle1).get(0);
		LinkedList<State> statesVehicle1 = newPlan.getNextStates().get(vehicle1);
		
		// remove the first state of Vehicle1 from its list of states
		for (int i = statesVehicle1.size() - 1; i >= 0; i--) {
			if (statesVehicle1.get(i).getCurrentTask() == firstStateVehicle1.getCurrentTask()) {
				statesVehicle1.remove(i);
			}
		}
		
		LinkedList<State> statesVehicle2 = newPlan.getNextStates().get(vehicle2);
		
		//initialize the list of states of Vehicle2 in case it was empty
		if (statesVehicle2 == null)
			statesVehicle2 = new LinkedList<State>();
		
		// add the vehicle1 first state to the beginning of the vehicle2 states list
		statesVehicle2.addFirst(firstStateVehicle1);
		
		State deliveryState = new State(false, firstStateVehicle1.getCurrentTask());
		
		List<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();
		
		// for each state of Vehicle2 starting after the state of Vehicle1 that just got added 
		for (int i = 1 ; i <= statesVehicle2.size() ; i++) {
			@SuppressWarnings("unchecked")
			// clone the list of states of Vehicle2
			LinkedList<State> statesVehicle2Clone = (LinkedList<State>) statesVehicle2.clone();
			
			// add the deliveryState of the added task that was in Vehicle1 at position i 
			//(in order to deliver it after picking it up)
			statesVehicle2Clone.add(i, deliveryState);
			
			CentralizedPlan newPlanClone;

			try {
				// clone the new plan
				newPlanClone = (CentralizedPlan) newPlan.clone();
				
				// associate the created states list for Vehicle2 to Vehicle2 in the new plan clone
				newPlanClone.getNextStates().put(vehicle2, statesVehicle2Clone);
				
				// add the edited clone of the new plan to the list of candidate plans
				candidatePlans.add(newPlanClone);
			
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
		
		}
		
		return candidatePlans;		
	}

	private CentralizedPlan SelectInitialSolution() {
		
		int minCapacity = Integer.MIN_VALUE;
		Vehicle selectedVehicle = null;
		
		// select the vehicle with biggest capacity
		for (Vehicle vehicle : vehicles) {
			if (vehicle.capacity() > minCapacity) {
				minCapacity = vehicle.capacity();
				selectedVehicle = vehicle;
			}
		}

		HashMap<Vehicle, LinkedList<State>> stateMap = new HashMap<Vehicle, LinkedList<State>>();
		LinkedList<State> nextState = new LinkedList<State>();

		// assign all the tasks to the selected vehicle
		for (Task task : tasks) {
			State pickupState = new State(true, task);
			State deliverState = new State(false, task);
			nextState.addLast(pickupState);
			nextState.addLast(deliverState);
		}

		//link the list of tasks states to the vehicle
		stateMap.put(selectedVehicle, nextState);
		
		//this vehicle carrying all the tasks is the first plan
		CentralizedPlan initialPlan = new CentralizedPlan();
		initialPlan.setNextStates(stateMap);
		
		return initialPlan;
	}

}
