package piddle.sonos.si;

import java.util.ArrayList;

/**
 * The QA manager is an entry point to submitting jobs to the QA team.
 * 
 * The managers main goal is distributing jobs to available QA team members and
 * keeping the threadpool, shared across all QA team members, thread safe.
 * 
 * @author Scott
 *
 */
public class QAManager {
	private ThreadSafeThreadPoolManager exec = null;
	private ArrayList<QATeamMember> qaTeam = null;

	/**
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

	/**
	 * Returns true if a QA worker is currently available. false otherwise. Does NOT
	 * count against the QA worker capacity.
	 * 
	 * @param curTime
	 * @param req
	 * @return
	 */
	public boolean canSellHydrant(long curTime, Request req) {
		for (QATeamMember t : this.qaTeam) {
			if (t.canTest(curTime)) {
				req.getResponse().setFulfilledBy(t.getUUID());
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if a QA worker is available. false otherwise. DOES count against
	 * QA worker capacity.
	 * 
	 * @param curTime
	 * @param req
	 * @return
	 */
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
