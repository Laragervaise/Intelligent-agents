package template;

import logist.task.Task;
import logist.topology.Topology.City;

/*
 * This class has 7 properties:
 * parentState : the previous state
 * position : the current city
 * task : task in delivery or going to be picked up
 * taskStatus : 0 = waiting for pick up, 1 = in the vehicle for delivery and 2 = delivered
 * totalWeigth : weight of all the tasks carried by the agent
 * cost : total cost until the current city
 * pickUp : true = agent is on its way to pick up a new task ; false = it's going to deliver one
 */
public class State {

	private State parentSate;
	private City position;
	private Task task;
	private String taskStatus;
	private int totalWeight;
	private double cost;
	private boolean pickUp;

	public State(State parentSate, City position, Task task, String taskStatus, int totalWeight, double cost,
				 boolean pickUp) {
		super();
		this.parentSate = parentSate;
		this.position = position;
		this.task = task;
		this.taskStatus = taskStatus;
		this.totalWeight = totalWeight;
		this.cost = cost;
		this.pickUp = pickUp;
	}

	public State getParentSate() { return parentSate;}

	public void setParentSate(State parentSate) { this.parentSate = parentSate;}

	public City getPosition() { return position;}

	public void setPosition(City position) { this.position = position;}

	public Task getTask() { return task; }

	public void setTask(Task task) { this.task = task;}

	public String getTaskStatus() { return taskStatus;}

	public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus;}

	public int getTotalWeight() { return totalWeight;}

	public void setTotalWeight(int accumulateWeight) { this.totalWeight = accumulateWeight;}

	public double getCost() { return cost;}

	public void setCost(double cost) { this.cost = cost;}

	public boolean pickUp() { return pickUp;}

	public void setPickUp(boolean pickUp) { this.pickUp = pickUp;}

	@Override
	public String toString() { return taskStatus + " " + cost + "";}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (pickUp ? 1231 : 1237);
		long temp;
		temp = Double.doubleToLongBits(cost);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((position == null) ? 0 : position.hashCode());
		result = prime * result + ((taskStatus == null) ? 0 : taskStatus.hashCode());
		result = prime * result + totalWeight;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		State other = (State) obj;
		if (pickUp != other.pickUp)
			return false;
		if (Double.doubleToLongBits(cost) != Double.doubleToLongBits(other.cost))
			return false;
		if (position == null) {
			if (other.position != null)
				return false;
		} else if (!position.equals(other.position))
			return false;
		if (taskStatus == null) {
			if (other.taskStatus != null)
				return false;
		} else if (!taskStatus.equals(other.taskStatus))
			return false;
		if (totalWeight != other.totalWeight)
			return false;
		return true;
	}
}