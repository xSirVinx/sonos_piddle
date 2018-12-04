package piddle.sonos.si;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uses a threadsafe threadpool, shared across all instances of QATeamMembers to
 * run QA tests.
 * 
 * Uses an AtomicBoolean as a locking mechanism while the worker is testing.
 * 
 * Uses a synchronized method to test if the worker should be on break.
 * 
 * @author Scott
 *
 */
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

	/**
	 * Returns true if a test can be consumed. False otherwise. Does not consume a
	 * test.
	 * 
	 * @param curTime
	 * @return
	 */
	public boolean canTest(long curTime) {
		return (!isOnBreak(curTime) && isFree.get());
	}

	/**
	 * Returns true if a test can be consumed. False otherwise
	 * 
	 * Ensure exceptions are caught so that we dont get thread leaks.
	 * 
	 * @param curTime
	 * @return
	 */
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

	/**
	 * A worker can only perform testsPerWindow jobs every windowLength. This method
	 * tests the sliding window of jobs to determine if the worker should be on
	 * break.
	 * 
	 * The thread in the Main function, which generates requests, will call runTest
	 * and canTest (via the QAManager) asyncronously. Both of those methods call
	 * isOnBreak to test if the QA worker is on break. To prevent errors associated
	 * with async calls, isOnBreak is synchonized so that only one thread at a time
	 * can access it. The result will be that the request thread will block a little
	 * longer waiting to access this method.
	 * 
	 * A different option for managing the worker breaks could have been to use the
	 * executor threadpool to schedule an event that would flip a second
	 * AtomicBoolean that indicated onBreak. This would block the request loop less
	 * because it would not have to (potentially) wait for access to a synchronized
	 * function. I chose not to do this because the scheduler would have added a
	 * runnable to the thread's execution queue, possibly behind several sellHydrant
	 * runnables. The sellHydrant runnables sitting in front of the scheduled
	 * runnable would be rejected because the qa worker is not yet off break even
	 * though it should be. I think (although i do not know) that this would result
	 * in more lost sales than the presumably insignificant time that a request
	 * blocks due to waiting for access to the synchronized function isOnBreak.
	 * 
	 * @param curTime
	 * @return
	 */
	private synchronized boolean isOnBreak(long curTime) {
		if (testQueue.size() < this.testsPerWindow) {
			return false;
		} else {
			return curTime <= testQueue.peek().longValue();
		}
	}

}
