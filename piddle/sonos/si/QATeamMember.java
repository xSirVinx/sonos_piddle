package piddle.sonos.si;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QATeamMember {

	private ExecutorService qaThreadPool;
	private AtomicBoolean isFree = new AtomicBoolean(true);
	private int timeToCompleteJob = 0;

	public QATeamMember(ExecutorService exec, int timeToCompleteJob) {
		this.qaThreadPool = exec;
		this.timeToCompleteJob = timeToCompleteJob;
	}

	public boolean canTest() {
		return isFree.get();
	}

	public boolean runTest() {
		if (this.isFree.compareAndSet(true, false)) {
			qaThreadPool.submit(() -> {
				System.out.println("Executing");
				try {
					TimeUnit.MILLISECONDS.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					this.isFree.compareAndSet(false, true);
				}
			});
			return true;
		} else {
			return false;
		}
	}

}
