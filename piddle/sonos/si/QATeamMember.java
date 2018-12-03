package piddle.sonos.si;

import java.time.ZonedDateTime;
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
	private int windowTime = 0;

	public QATeamMember(ThreadSafeThreadPoolManager exec, int timeToCompleteJob, int testsPerWindow,
			int windowLength) {
		this.qaThreadPool = exec;
		this.timeToCompleteJob = timeToCompleteJob;
		this.testQueue = new LinkedList<Long>();
		this.uuid = UUID.randomUUID().toString();
		this.testsPerWindow = testsPerWindow;
		this.windowTime = windowLength;
	}

	public boolean canTest() {
		return (!isOnBreak() && isFree.get());
	}

	public boolean runTest() {
		if (!isOnBreak() && this.isFree.compareAndSet(true, false)) {
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

	public String getUUID() {
		return this.uuid;
	}

	private synchronized boolean isOnBreak() {
		long curTime = ZonedDateTime.now().toInstant().toEpochMilli();
		if (testQueue.size() == this.testsPerWindow
				&& curTime - testQueue.peek().longValue() < this.windowTime) {
			return true;
		} else if (testQueue.size() == this.testsPerWindow) {
			testQueue.remove();
		}
		testQueue.add(curTime);
		return false;
	}

}
