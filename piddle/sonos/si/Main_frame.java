package piddle.sonos.si;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class Main_frame {
	public static void main_frame(String args[]) {

		// Test configuration variables
		int numTests = 1000;
		int milsBetweenTest = 1;
		int milsOpTime = 10;
		int numQAWorkers = 1;
		int maxConcurThreads = Runtime.getRuntime().availableProcessors();
		int testThreads = maxConcurThreads - numQAWorkers - 1;

		ArrayList<CompletableFuture<Long>> futures = runTests(numTests, testThreads, milsBetweenTest, milsOpTime);
		HashMap<String, Long> stats = parseTestStats(futures);

		System.out.println(String.format("Total QA requests: %s",
				String.valueOf(stats.get("tendedRequests") + stats.get("untendedRequests"))));
		System.out.println(String.format("Num tended QA requests: %s", String.valueOf(stats.get("tendedRequests"))));
		System.out
				.println(String.format("Num untended QA requests: %s", String.valueOf(stats.get("untendedRequests"))));
		System.out.println(String.format("Max time between tended requests: %s", String.valueOf(stats.get("max"))));
		System.out.println(String.format("Min time between tended requests: %s", String.valueOf(stats.get("min"))));
		System.out.println(
				String.format("Average time between tended requests: %s", String.valueOf(stats.get("average"))));

		assert stats.get("tendedRequests") + stats.get("untendedRequests") == numTests : String.format(
				"total tests does not equal num of run tests. Tended: %s, Untended: %s",
				stats.get("tendedRequests").toString(), stats.get("untendedRequests").toString());

		assert stats.get("min") >= milsOpTime : String.format(
				"Asynchronous access to QA tester detected. Min time between tended requests was: %s. It should have been: %s ",
				stats.get("min").toString(), String.valueOf(milsOpTime));

	}

	public static ArrayList<CompletableFuture<Long>> runTests(int numTests, int numThreads, int milsBetweenTests,
			int milsOpTime) {

		// Create a lock that will be used to ensure the work of a single QA team member
		// is parallelizable
		ReentrantLock lock = new ReentrantLock();
		// Create a place to put futures which will be completed/rejected by the QA team
		// member
		ArrayList<CompletableFuture<Long>> futures = new ArrayList<CompletableFuture<Long>>();
		// Create an executor with a thread pool that is equal to the number of cores on
		// the machine less the core(s) used by the QA team member and the core used to
		// execute the main thread.
		ExecutorService exec = Executors.newFixedThreadPool(numThreads);

		for (int x = 0; x < numTests; x++) {
			// Create a future per qa request and store it in the futures array
			CompletableFuture<Long> future = new CompletableFuture<Long>();
			futures.add(future);

			exec.submit(() -> {
				try {
					// simulate random user access by sleeping each requesting thread for a
					// random amount of time
					int randomNum = ThreadLocalRandom.current().nextInt(milsBetweenTests, 5 + 1);
					TimeUnit.MILLISECONDS.sleep(randomNum);

					// if the lock is free, then a QA team member is available to tend a request
					if (lock.tryLock()) {
						try {
							long curTime = ZonedDateTime.now().toInstant().toEpochMilli();
							TimeUnit.MILLISECONDS.sleep(milsOpTime);
							future.complete(curTime);
						} finally {
							lock.unlock();
						}
					} else {
						// if the lock is not free, then immediately complete the request.
						future.complete(null);
					}
				} catch (InterruptedException e) {
					future.completeExceptionally(e);
				}

			});
		}

		exec.shutdown();
		return futures;
	}

	public static HashMap<String, Long> parseTestStats(ArrayList<CompletableFuture<Long>> futures) {
		HashMap<String, Long> stats = new HashMap<String, Long>();

		List<Long> completeFuts = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

		List<Long> tendedQARequestStartTimes = completeFuts.stream().filter(val -> val != null).sorted()
				.collect(Collectors.toList());

		long numUntendedQARequests = completeFuts.stream().filter(val -> val == null).count();

		stats.put("untendedRequests", new Long(numUntendedQARequests));
		stats.put("tendedRequests", new Long(tendedQARequestStartTimes.size()));
		stats.put("min", tendedQARequestStartTimes.get(0));
		stats.put("max", new Long(0));
		stats.put("average", new Long(0));

		for (int x = 0; x < tendedQARequestStartTimes.size() - 1; x++) {
			long timeLocked = tendedQARequestStartTimes.get(x + 1).longValue()
					- tendedQARequestStartTimes.get(x).longValue();

			stats.put("average", stats.get("average").longValue() + timeLocked);

			if (timeLocked > stats.get("max").longValue()) {
				stats.put("max", timeLocked);
			}

			if (timeLocked < stats.get("min")) {
				stats.put("min", timeLocked);
			}
		}

		stats.put("average", stats.get("average") / tendedQARequestStartTimes.size());

		return stats;

	}

}
