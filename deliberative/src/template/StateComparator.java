package template;

import java.util.Comparator;

import logist.task.Task;
import logist.topology.Topology.City;

public class StateComparator implements Comparator<State> {

	private static final char NOTASK = '0';
	private static final char PICKEDUP = '1';

	private Task[] tasksArray;
	private double costPerKm;

	public StateComparator(Task[] tasksArray, double costPerKm) {
		this.tasksArray = tasksArray;
		this.costPerKm = costPerKm;
	}

	@Override // compute the costs of two different states to compare them to find the "cheapest" one
	public int compare(State state1, State state2) {

		// heuristic
		String state1TaskStatus = state1.getTaskStatus();
		String state2TaskStatus = state2.getTaskStatus();

		double shortestPath1 = 0;
		double shortestPath2 = 0;

		for (int i = 0; i < state1TaskStatus.length(); i++) {
			helper(state1, state1TaskStatus, shortestPath1, i);
			helper(state2, state2TaskStatus, shortestPath2, i);
		}

		double state1Cost = state1.getCost() + shortestPath1 * costPerKm;
		double state2Cost = state2.getCost() + shortestPath2 * costPerKm;

		return Double.compare(state1Cost, state2Cost);
	}

	private void helper(State state, String taskStatus, double shortestPath, int i) {
		if (taskStatus.charAt(i) == NOTASK) {
			City pickUpCity = state.getTask().pickupCity;
			double pathLength = state.getPosition().distanceTo(pickUpCity) + tasksArray[i].pathLength();
			if (shortestPath < pathLength) {
				shortestPath = pathLength;
			}
		} else if (taskStatus.charAt(i) == PICKEDUP) {
			City deliveryCity = state.getTask().deliveryCity;
			double pathLength = state.getPosition().distanceTo(deliveryCity);
			if (shortestPath < pathLength) {
				shortestPath = pathLength;
			}
		}
	}
}