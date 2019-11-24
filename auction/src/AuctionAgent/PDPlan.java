package AuctionAgent;

import java.util.LinkedList;
import java.util.List;

import logist.task.Task;
import logist.task.TaskSet;

public class PDPlan {

	private MyVehicle biggestMyVehicle;
	private CentralizedPlan bestPlan;
	private CentralizedPlan searchPlan;
	private CentralizedPlan newPlan;
	
	public MyVehicle getBiggestVehicle() {
		return biggestMyVehicle;
	}

	public CentralizedPlan getBestPlan() {
		return bestPlan;
	}

	public CentralizedPlan getSearchPlan() {
		return searchPlan;
	}
	
	public void updatePlan() {
		searchPlan = newPlan;
	}
	
	public PDPlan(List<MyVehicle> vehiclesList) {
		
		int capacity = Integer.MIN_VALUE;
		this.searchPlan = new CentralizedPlan();
		
		for (MyVehicle vehicle : vehiclesList) {
			
			//define the searchPlan
			LinkedList<State> StateList = new LinkedList<State>();
			searchPlan.getVehicleStates().put(vehicle, StateList);
			
			// find the vehicle with the maximum capacity
			if (capacity < vehicle.getCapacity()) {
				capacity = vehicle.getCapacity();
				biggestMyVehicle = vehicle;
			}
		}
	}

	public CentralizedPlan solveWithNewTask(Task task) {
		try {
			newPlan = (CentralizedPlan) searchPlan.clone();
			List<CentralizedPlan> planSet = newPlan.insertTask(task);
			newPlan = localChoice(searchPlan, planSet);
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return newPlan;
	}

	public void solveWithTaskSet(TaskSet tasks) {
		for (Task task : tasks) {
			List<CentralizedPlan> planSet = searchPlan.insertTask(task);
			searchPlan = localChoice(searchPlan, planSet);
		}
	}

	private CentralizedPlan localChoice(CentralizedPlan oldPlan, List<CentralizedPlan> planSet) {
		// choose the plan with the lowest cost
		
		CentralizedPlan minCostPlan = oldPlan;
		double minCost = Integer.MAX_VALUE;
		
		for (CentralizedPlan plan : planSet) {
			if (plan.cost() < minCost) {
				minCostPlan = plan;
				minCost = plan.cost();
			}
		}

		this.bestPlan = minCostPlan;
		return minCostPlan;
	}

}
