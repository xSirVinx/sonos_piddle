package piddle.sonos.si;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QATeamMember {

	private ThreadSafeThreadPoolManager qaThreadPool;
	private AtomicBoolean isFree = new AtomicBoolean(true);
	private int timeToCompleteJob = 0;

	public QATeamMember(ThreadSafeThreadPoolManager exec, int timeToCompleteJob) {
		this.qaThreadPool = exec;
		this.timeToCompleteJob = timeToCompleteJob;
	}

	public boolean canTest() {
		return isFree.get();
	}

	public boolean runTest() {
		if (this.isFree.compareAndSet(true, false)) {
			try {
				qaThreadPool.submit(() -> {
					try {
						TimeUnit.MILLISECONDS.sleep(this.timeToCompleteJob);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						this.isFree.compareAndSet(false, true);
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
			return true;
		} else {
			return false;
		}
	}

}
