package piddle.sonos.si;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QATeamMember {

	private ThreadSafeThreadPoolManager qaThreadPool;
	private AtomicBoolean isFree = new AtomicBoolean(true);
	private int timeToCompleteJob = 0;
	private Queue<Long> testQueue = null;
	private String uuid = null;
	private int testsPerWindow = 0;
	private int windowLength = 0;

	public QATeamMember(ThreadSafeThreadPoolManager exec, int timeToCompleteJob, int testsPerWindow,
			int windowLength) {
		this.qaThreadPool = exec;
		this.timeToCompleteJob = timeToCompleteJob;
		this.testQueue = new LinkedList<Long>();
		this.uuid = UUID.randomUUID().toString();
		this.testsPerWindow = testsPerWindow;
		this.windowLength = windowLength;

	}

	public boolean canTest(long curTime) {
		return (!isOnBreak(curTime) && isFree.get());
	}

	public boolean runTest(long curTime) {
		if (!isOnBreak(curTime) && this.isFree.compareAndSet(true, false)) {
			try {
				if (testQueue.size() == this.testsPerWindow) {
					testQueue.remove();
				}
				testQueue.add(curTime + this.windowLength);

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

	public String getUUID() {
		return this.uuid;
	}

	private synchronized boolean isOnBreak(long curTime) {
		if (testQueue.size() < this.testsPerWindow) {
			return false;
		} else {
			return curTime <= testQueue.peek().longValue();
		}
	}

}
