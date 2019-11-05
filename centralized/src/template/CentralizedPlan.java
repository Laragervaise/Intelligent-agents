package template;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import logist.simulation.Vehicle;
import logist.task.Task;

public class CentralizedPlan implements Cloneable {

	private HashMap<Vehicle, LinkedList<State>> nextStates;

	public HashMap<Vehicle, LinkedList<State>> getNextStates() {
		return nextStates;
	}

	public void setNextStates(HashMap<Vehicle, LinkedList<State>> nextStates) {
		this.nextStates = nextStates;
	}

	public void removeCorrespondingDeliverState(Vehicle v1, State state) {
		
		Task task = state.getCurrentTask();
		LinkedList<State> stateList = nextStates.get(v1);
		
		for (int i = 0; i < stateList.size(); i++) {
			State currentTask = stateList.get(i);
			if (currentTask.getCurrentTask() == task) {
				stateList.remove(i);
			}
		}
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		
		CentralizedPlan o = null;
		
		try {
			o = (CentralizedPlan) super.clone();
		} catch (CloneNotSupportedException e) {
			System.out.println(e.toString());
		}

		o.nextStates = new HashMap<Vehicle, LinkedList<State>>();
		for (Iterator<Vehicle> keyIt = nextStates.keySet().iterator(); keyIt.hasNext();) {
			Vehicle key = keyIt.next();
			o.nextStates.put(key, (LinkedList<State>) nextStates.get(key).clone());
		}

		return o;
	}
}
