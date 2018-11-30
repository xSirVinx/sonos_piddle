package piddle.sonos.si;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

public class QAManager {
	private ArrayList<QATeamMember> qaTeam = null;
	private ExecutorService exec;

	public QAManager(int teamSize, ExecutorService exec, int timeToCompleteJobMils) {
		this.exec = exec;
		qaTeam = new ArrayList<QATeamMember>();

		for (int x = 0; x < teamSize; x++) {
			qaTeam.add(new QATeamMember(this.exec, timeToCompleteJobMils));
		}
	}

	public boolean canTest() {
		for (QATeamMember t : this.qaTeam) {
			if (t.canTest()) {
				return true;
			}
		}
		return false;
	}

	public boolean runTest() {
		for (QATeamMember t : this.qaTeam) {
			if (t.runTest()) {
				return true;
			}
		}
		return false;
	}

}
