package AuctionAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import AuctionAgent.State.Type;
import logist.task.Task;
import logist.task.TaskSet;

public class CentralizedPlan implements Cloneable {

	private HashMap<MyVehicle, LinkedList<State>> vehicleStates = new HashMap<MyVehicle, LinkedList<State>>();

	public HashMap<MyVehicle, LinkedList<State>> getVehicleStates() {
		return vehicleStates;
	}

	public void setVehicleStates(HashMap<MyVehicle, LinkedList<State>> vehicleStates) {
		this.vehicleStates = vehicleStates;
	}

	public void removeTask(Task task) {
		for (MyVehicle vehicle : vehicleStates.keySet()) {
			removeStateFromVehicle(vehicle, new State(Type.PICKUP, task));
			removeStateFromVehicle(vehicle, new State(Type.DELIVERY, task));
		}
	}

	public void removeStateFromVehicle(MyVehicle vehicle, State State) {
		LinkedList<State> StateList = vehicleStates.get(vehicle);
		StateList.remove(State);
		vehicleStates.put(vehicle, StateList);
	}

	public List<CentralizedPlan> insertTask(Task task) {

		List<CentralizedPlan> candidatePlans = new ArrayList<CentralizedPlan>();
		
		for (Map.Entry<MyVehicle, LinkedList<State>> entry : vehicleStates.entrySet()) {
			
			LinkedList<State> StateList = entry.getValue();
			
			// Create states regarding the task
			State pickupState = new State(Type.PICKUP, task);
			State deliveryState = new State(Type.DELIVERY, task);
			MyVehicle vehicle = entry.getKey();

			// clone the new plan and the vehicle states list and edit them
			for (int index1 = 0; index1 <= StateList.size(); index1++) {
				for (int index2 = index1 + 1; index2 <= StateList.size() + 1; index2++) {
					
					CentralizedPlan planClone = null;

					try {
						planClone = (CentralizedPlan) this.clone();
					} catch (CloneNotSupportedException e) {
						e.printStackTrace();
					}

					LinkedList<State> statesClones = (LinkedList<State>) StateList.clone();
					statesClones.add(index1, pickupState);
					statesClones.add(index2, deliveryState);
					planClone.vehicleStates.put(vehicle, statesClones);
					if (!planClone.violateConstraints()) {
						candidatePlans.add(planClone);
					}

				}
			}
		}
		return candidatePlans;
	}

	public double cost() {
		
		double cost = 0;
		
		// iterate across all the vehicles
		for (Map.Entry<MyVehicle, LinkedList<State>> entry : vehicleStates.entrySet()) {
			
			MyVehicle MyVehicle = entry.getKey();
			LinkedList<State> StateList = entry.getValue();
			
			if (StateList.size() > 0) {
				
				Task startTask = StateList.get(0).getCurrentTask();
				double dist = MyVehicle.getInitCity().distanceTo(startTask.pickupCity);

				// for the next tasks, compute the cost according to the current task state and the following one
				for (int i = 0; i < StateList.size() - 1; i++) {
					
					State preState = StateList.get(i);
					State postState = StateList.get(i + 1);
					
					if (postState.type == Type.PICKUP) {
						if (preState.type == Type.PICKUP) {
							dist += preState.getCurrentTask().pickupCity.distanceTo(postState.getCurrentTask().pickupCity);
						} else{
							dist += preState.getCurrentTask().deliveryCity.distanceTo(postState.getCurrentTask().pickupCity);
						}
					} else {
						if (preState.type == Type.PICKUP) {
							dist += preState.getCurrentTask().pickupCity.distanceTo(postState.getCurrentTask().deliveryCity);
						} else {
							dist += preState.getCurrentTask().deliveryCity.distanceTo(postState.getCurrentTask().deliveryCity);
						}
					}

				}
				cost += dist * MyVehicle.getCostPerKm();
			}
		}

		return cost;
	}

	public boolean violateConstraints() {

		boolean isViolate = false;
		
		// iterate across all the task lists for all the vehicles
		for (Map.Entry<MyVehicle, LinkedList<State>> entry : vehicleStates.entrySet()) {
			
			MyVehicle vehicle = entry.getKey();
			int capacity = vehicle.getCapacity();
			LinkedList<State> StateList = entry.getValue();
			
			if (StateList.size() > 0) {
				
				int load = 0;
				
				// iterate across all the tasks for a given vehicle
				for (int i = 0; i < StateList.size(); i++) {
					
					// compute the total load of the vehicle
					if (StateList.get(i).type == Type.PICKUP) {
						load += StateList.get(i).getCurrentTask().weight;
					} else {
						load -= StateList.get(i).getCurrentTask().weight;
					}

					// check if everything fits in the vehicle
					if (load > capacity) {
						isViolate = true;
						break;
					}
				}
			}

			if (isViolate) {
				break;
			}
		}
		return isViolate;
	}

	public int getTaskNum() {
		int taskNum = 0;
		for (Map.Entry<MyVehicle, LinkedList<State>> entry : vehicleStates.entrySet()) {
			taskNum += entry.getValue().size();
		}
		return taskNum;
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		CentralizedPlan o = null;
		try {
			o = (CentralizedPlan) super.clone();
		} catch (CloneNotSupportedException e) {
			System.out.println(e.toString());
		}

		o.vehicleStates = new HashMap<MyVehicle, LinkedList<State>>();
		for (Iterator<MyVehicle> keyIt = vehicleStates.keySet().iterator(); keyIt.hasNext();) {
			MyVehicle key = keyIt.next();
			o.vehicleStates.put(key, (LinkedList<State>) vehicleStates.get(key).clone());
		}

		return o;
	}

	@Override
	public String toString() {
		String output = "";
		for (Map.Entry<MyVehicle, LinkedList<State>> entry : vehicleStates.entrySet()) {
			output += entry.getKey().getVehicle().name() + " " + entry.getValue() + "\n";
		}
		return "CentralizedPlan:MyVehicleStates=" + output + "";
	}


}
