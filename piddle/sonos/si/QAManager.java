package piddle.sonos.si;

import java.util.ArrayList;

public class QAManager {
	private ThreadSafeThreadPoolManager exec = null;
	private ArrayList<QATeamMember> qaTeam = null;

	/**
	 * 
	 * @param teamSize
	 * @param threadPoolSize
	 * @param timeToCompleteJobMils
	 */
	public QAManager(int teamSize, int threadPoolSize, int timeToCompleteJobMils) {
		this.exec = new ThreadSafeThreadPoolManager(threadPoolSize);
		this.qaTeam = new ArrayList<QATeamMember>();

		for (int x = 0; x < teamSize; x++) {
			qaTeam.add(new QATeamMember(this.exec, timeToCompleteJobMils));
		}

	}

	public boolean canSellHydrant() {
		for (QATeamMember t : this.qaTeam) {
			if (t.canTest()) {
				return true;
			}
		}
		return false;
	}

	public boolean sellHydrant() {
		for (QATeamMember t : this.qaTeam) {
			if (t.runTest()) {
				return true;
			}
		}
		return false;
	}

	public void shutDown() {
		this.exec.shutDown();
	}

}
