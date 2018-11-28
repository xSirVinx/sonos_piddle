package piddle.sonos.si;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class main {
	public static void main(String args[]) {

		// Test configuration variables
		int numTests = 1000;
		int milsBetweenTest = 1;
		int milsOpTime = 10;
		int numQAWorkers = 1;
		int maxConcurThreads = Runtime.getRuntime().availableProcessors();
		int testThreads = maxConcurThreads - numQAWorkers - 1;

		ArrayList<CompletableFuture<Long>> futures = new ArrayList<CompletableFuture<Long>>();

		ReentrantLock lock = new ReentrantLock();

		ExecutorService exec = Executors.newFixedThreadPool(testThreads);

		for (int x = 0; x < numTests; x++) {
			CompletableFuture<Long> future = new CompletableFuture<Long>();
			futures.add(future);

			exec.submit(() -> {
				try {
					// simulate random user access by sleeping the thread for a
					// random amount of time
					int randomNum = ThreadLocalRandom.current()
							.nextInt(milsBetweenTest, 5 + 1);
					TimeUnit.MILLISECONDS.sleep(randomNum);

					if (lock.tryLock()) {
						try {
							long curTime = ZonedDateTime.now().toInstant()
									.toEpochMilli();
							TimeUnit.MILLISECONDS.sleep(milsOpTime);
							future.complete(curTime);
						} finally {
							lock.unlock();
						}
					} else {
						future.complete(null);
					}
				} catch (InterruptedException e) {
					future.completeExceptionally(e);
				}

			});
		}

		List<Long> timeValues = futures.stream().map(CompletableFuture::join)
				.filter(val -> val != null).sorted()
				.collect(Collectors.toList());

		long min = timeValues.get(0);
		long max = 0;
		long average = 0;
		for (int x = 0; x < timeValues.size() - 1; x++) {
			long timeLocked = timeValues.get(x + 1).longValue()
					- timeValues.get(x).longValue();

			average += timeLocked;

			if (timeLocked > max) {
				max = timeLocked;
			}

			if (timeLocked < min) {
				min = timeLocked;
			}
		}

		System.out.println(String.format("Num accessed: %s",
				String.valueOf(timeValues.size())));
		System.out.println(
				String.format("Max time between ops: %s", String.valueOf(max)));
		System.out.println(
				String.format("Min time between ops: %s", String.valueOf(min)));
		System.out.println(String.format("Average time between ops: %s",
				String.valueOf(average / timeValues.size())));

		exec.shutdown();
	}
}
