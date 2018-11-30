package piddle.sonos.si;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
	public static void main(String args[]) {

		// Test configuration variables
		int numTests = 1000;
		int numQAWorkers = 1;
		int timeToCompleteJobMils = 10;
		int maxConcurThreads = Runtime.getRuntime().availableProcessors();

		ExecutorService exec = Executors.newFixedThreadPool(maxConcurThreads - 1);
		QAManager manager = new QAManager(numQAWorkers, exec, timeToCompleteJobMils);

		ArrayList<Request> requests = runTests(numTests, manager, exec);
		HashMap<String, Long> stats = parseTestStats(requests);

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

		assert stats.get("min") >= timeToCompleteJobMils : String.format(
				"Asynchronous access to QA tester detected. Min time between tended requests was: %s. It should have been: %s ",
				stats.get("min").toString(), String.valueOf(timeToCompleteJobMils));

		exec.shutdown();
	}

	public static ArrayList<Request> runTests(int numTests, QAManager manager, ExecutorService exec) {

		ArrayList<Request> requests = new ArrayList<Request>();

		for (int x = 0; x < numTests; x++) {

			Request req = new Request();
			requests.add(req);

			exec.submit(() -> {
				try {
					// simulate random user access by sleeping each requesting thread for a
					// random amount of time
					int randomNum = ThreadLocalRandom.current().nextInt(1, 6);
					TimeUnit.MILLISECONDS.sleep(randomNum);

					long curTime = ZonedDateTime.now().toInstant().toEpochMilli();

					if (manager.runTest()) {
						req.end(ResponseType.CONSUMED, curTime);
					} else {
						req.end(ResponseType.REJECTED, curTime);
					}
				} catch (InterruptedException e) {
					req.error(e);
				}

			});
		}
		return requests;
	}

	public static HashMap<String, Long> parseTestStats(ArrayList<Request> reqs) {
		HashMap<String, Long> stats = new HashMap<String, Long>();

		List<Response> responses = reqs.stream().map(Request::getResponse).collect(Collectors.toList());

		List<Response> tendedQARequestStartTimes = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED).sorted().collect(Collectors.toList());

		long numUntendedQARequests = responses.stream().filter(resp -> resp.getResponseType() == ResponseType.REJECTED)
				.count();

		stats.put("untendedRequests", new Long(numUntendedQARequests));
		stats.put("tendedRequests", new Long(tendedQARequestStartTimes.size()));
		stats.put("min", tendedQARequestStartTimes.get(0).getTime());
		stats.put("max", new Long(0));
		stats.put("average", new Long(0));

		for (int x = 0; x < tendedQARequestStartTimes.size() - 1; x++) {
			long timeLocked = tendedQARequestStartTimes.get(x + 1).getTime()
					- tendedQARequestStartTimes.get(x).getTime();

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
