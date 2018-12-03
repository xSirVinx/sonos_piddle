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
	public QAManager(int teamSize, int threadPoolSize, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {
		this.exec = new ThreadSafeThreadPoolManager(threadPoolSize);
		this.qaTeam = new ArrayList<QATeamMember>();

		for (int x = 0; x < teamSize; x++) {
			qaTeam.add(new QATeamMember(this.exec, timeToCompleteJobMils, testsPerWindow,
					windowLength));
		}

	}

	public boolean canSellHydrant(long curTime, Request req) {
		for (QATeamMember t : this.qaTeam) {
			if (t.canTest(curTime)) {
				req.getResponse().setFulfilledBy(t.getUUID());
				return true;
			}
		}
		return false;
	}

	public boolean sellHydrant(long curTime, Request req) {
		for (QATeamMember t : this.qaTeam) {
			if (t.runTest(curTime)) {
				req.getResponse().setFulfilledBy(t.getUUID());
				return true;
			}
		}
		return false;
	}

	public void shutDown() {
		this.exec.shutDown();
	}

}
