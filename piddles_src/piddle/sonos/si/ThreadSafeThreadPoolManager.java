package piddle.sonos.si;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The ThreadPoolManager is a wrapper that facilitates use of a thread safe
 * ExecutorService. All the QA team members are executing jobs on
 * asynchronously. In order to ensure synchronous access to the shared
 * ThreadPool, this method uses synchronized functions to wrap submission of
 * runnables.
 * 
 * @author Scott
 *
 */

public class ThreadSafeThreadPoolManager {

	private ExecutorService exec = null;
	private int maxConcurThreads = 0;

	public ThreadSafeThreadPoolManager(int numThreads) {
		this.maxConcurThreads = Runtime.getRuntime().availableProcessors();

		if (numThreads > maxConcurThreads) {
			System.out.println(
					"Warning: ThreadPool size is larger than available threads. This can lead to resource thrashing.");
		}

		this.exec = Executors.newFixedThreadPool(numThreads);
	}

	public synchronized Future<?> submit(Runnable task) throws Exception {
		return this.exec.submit(task);
	}

	public synchronized void shutDown() {
		if (!this.exec.isShutdown()) {
			this.exec.shutdown();
		}
	}
}
