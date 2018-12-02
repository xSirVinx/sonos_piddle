package piddle.sonos.si;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The ThreadPoolManager is a wrapper that facilitates use of a thread safe
 * ExecutorService.
 * 
 * There were a couple ways to implement the ThreadPool manager. One of them
 * would have been to put the ExecutorService into the QAManager and let the
 * QAManager manage the pool for the QATeam. However, this application uses a
 * thread to generate randomly submitted, simulated requests. The request thread
 * could have its own ExecutorService but it seems like a waste of a thread. The
 * computers that this code runs on will likely have between 2 to 8 cores, with
 * most probably having 4 cores. Generally, an optimized number of threads in a
 * pool is equal to the number of cores. With so few cores, it'd be nice to put
 * the request thread back into the pool once it is done executing, as it should
 * complete pretty quickly, thus giving the QATeam an additional thread to work
 * on.
 * 
 * Putting the request thread in the threadpool does introduce a problem though.
 * The QATeam could consume all the threads in the pool, leaving no thread for
 * the requests to be made on,
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
