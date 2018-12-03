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
		int numSales = 10000;
		int numQAWorkers = 1;
		int timeToCompleteJobMils = 50;
		int testsPerWindow = 5;
		int windowLength = 600;

		/*
		 * Optimally, we dont create more threads that we have CPUs/cores. There are two
		 * default threads taken up by the test framework: the main thread, and the
		 * thread that creates all of the requests. We can give all of the other
		 * cores/threads to the QA team
		 */
		int maxConcurThreads = Runtime.getRuntime().availableProcessors() - 2;

		QAManager manager = new QAManager(numQAWorkers, maxConcurThreads, timeToCompleteJobMils,
				testsPerWindow, windowLength);
		ArrayList<Request> requests = new ArrayList<Request>();
		ExecutorService exec = Executors.newFixedThreadPool(1);

		requests.addAll(runSales(numSales, exec, manager));
		exec.shutdown();

		// Retrieve all the responses from the requests (i.e., join the futures) and
		// sort the responses by time they were sent
		List<Response> responses = requests.stream().map(Request::getResponse).sorted()
				.collect(Collectors.toList());

		assert checkResponseOrder(responses,
				timeToCompleteJobMils) : "Found sales or button-show action that occured while QA was busy";
		assert checkBreaksRespected(responses, timeToCompleteJobMils, testsPerWindow,
				windowLength) : "QA member did not take the breaks he was owed.";

		HashMap<String, Long> salesStats = parseSalesStats(responses);
		HashMap<String, Long> inqueryStats = parseInqueryStats(responses);

		System.out.println("//////////////////Sales Stats///////////////////////////");
		System.out.println(String.format("Total Sales requests: %s",
				String.valueOf(salesStats.get("attendedSalesRequests")
						+ salesStats.get("unattendedSalesRequests"))));
		System.out.println(String.format("Num attended Sales requests: %s",
				String.valueOf(salesStats.get("attendedSalesRequests"))));
		System.out.println(String.format("Num unattended Sales requests: %s",
				String.valueOf(salesStats.get("unattendedSalesRequests"))));
		System.out.println(String.format("Max time between attended sales requests: %s",
				String.valueOf(salesStats.get("max_qa_time"))));
		System.out.println(String.format("Min time between attended sales requests: %s",
				String.valueOf(salesStats.get("min_qa_time"))));
		System.out.println(String.format("Average time between attended sales requests: %s",
				String.valueOf(salesStats.get("average_qa_time"))));

		System.out.println("//////////////////Inquery Stats/////////////////////////");
		System.out.println(String.format("Num inqueries shown buy button: %s",
				String.valueOf(inqueryStats.get("shown_button"))));
		System.out.println(String.format("Num inqueries NOT shown buy button: %s",
				String.valueOf(inqueryStats.get("not_shown_button"))));

		assert salesStats.get("attendedSalesRequests")
				+ salesStats.get("unattendedSalesRequests") == numSales / 5 : String.format(
						"total sales tests does not equal num of sales tests run. attended: %s, Unattended: %s, num sales tests: %s",
						salesStats.get("attendedSalesRequests").toString(),
						salesStats.get("unattendedSalesRequests").toString(),
						String.valueOf(numSales / 5));

		assert salesStats.get("min_qa_time") >= timeToCompleteJobMils : String.format(
				"Asynchronous access to QA tester detected. Min time between attended requests was: %s. It should have been: %s ",
				salesStats.get("min_qa_time").toString(), String.valueOf(timeToCompleteJobMils));

		assert inqueryStats.get("shown_button") + inqueryStats.get("not_shown_button") == numSales
				- (numSales / 5) : String.format(
						"total tested inquiries does not equal num of inquery tests run. shown button: %s, not shown button: %s, num inquery tests: %s",
						inqueryStats.get("shown_button").toString(),
						inqueryStats.get("not_shown_button").toString(),
						String.valueOf(numSales - (numSales / 5)));

		manager.shutDown();
	}

	public static ArrayList<Request> runSales(int numSales, ExecutorService exec,
			QAManager manager) {

		ArrayList<Request> requests = new ArrayList<Request>();

		for (int x = 0; x < numSales; x++) {

			Request req = new Request();
			requests.add(req);

			if (x % 5 == 0) {
				exec.submit(() -> {
					try {
						// simulate random user access by sleeping each requesting thread for a
						// random amount of time
						int randomNum = ThreadLocalRandom.current().nextInt(1, 6);
						TimeUnit.MILLISECONDS.sleep(randomNum);
						long curTime = ZonedDateTime.now().toInstant().toEpochMilli();

						if (manager.sellHydrant()) {
							req.end(ResponseType.CONSUMED, curTime);
						} else {
							req.end(ResponseType.REJECTED, curTime);
						}
					} catch (InterruptedException e) {
						req.error(e);
					}

				});
			} else {
				exec.submit(() -> {
					try {
						// simulate random user access by sleeping each requesting thread for a
						// random amount of time
						int randomNum = ThreadLocalRandom.current().nextInt(1, 6);
						TimeUnit.MILLISECONDS.sleep(randomNum);
						long curTime = ZonedDateTime.now().toInstant().toEpochMilli();

						if (manager.canSellHydrant()) {
							req.end(ResponseType.TEST_ACCEPT, curTime);
						} else {
							req.end(ResponseType.TEST_REJ, curTime);
						}
					} catch (InterruptedException e) {
						req.error(e);
					}

				});
			}

		}
		return requests;
	}

	public static HashMap<String, Long> parseSalesStats(List<Response> responses) {
		HashMap<String, Long> stats = new HashMap<String, Long>();

		List<Response> attendedSalesRequestTimes = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED).sorted()
				.collect(Collectors.toList());

		List<Response> unattendedSalesRequestTimes = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.REJECTED).sorted()
				.collect(Collectors.toList());

		stats.put("unattendedSalesRequests", new Long(unattendedSalesRequestTimes.size()));
		stats.put("attendedSalesRequests", new Long(attendedSalesRequestTimes.size()));
		stats.put("min_qa_time", attendedSalesRequestTimes.get(0).getTime());
		stats.put("max_qa_time", new Long(0));
		stats.put("average_qa_time", new Long(0));

		for (int x = 0; x < attendedSalesRequestTimes.size() - 1; x++) {
			long timeLocked = attendedSalesRequestTimes.get(x + 1).getTime()
					- attendedSalesRequestTimes.get(x).getTime();

			stats.put("average_qa_time", stats.get("average_qa_time").longValue() + timeLocked);

			if (timeLocked > stats.get("max_qa_time").longValue()) {
				stats.put("max_qa_time", timeLocked);
			}

			if (timeLocked < stats.get("min_qa_time")) {
				stats.put("min_qa_time", timeLocked);
			}
		}

		stats.put("average_qa_time",
				stats.get("average_qa_time") / attendedSalesRequestTimes.size());

		return stats;
	}

	public static HashMap<String, Long> parseInqueryStats(List<Response> responses) {
		HashMap<String, Long> stats = new HashMap<String, Long>();

		List<Response> attendedInqueriesRequestTimes = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.TEST_ACCEPT).sorted()
				.collect(Collectors.toList());

		List<Response> unattendedInqueriesRequestTimes = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.TEST_REJ).sorted()
				.collect(Collectors.toList());

		stats.put("not_shown_button", new Long(unattendedInqueriesRequestTimes.size()));
		stats.put("shown_button", new Long(attendedInqueriesRequestTimes.size()));

		return stats;
	}

	public static boolean checkResponseOrder(List<Response> responses, int timeToCompleteJobMils) {
		long time = 0;

		for (Response resp : responses) {
			if ((resp.getResponseType() == ResponseType.CONSUMED)
					|| (resp.getResponseType() == ResponseType.TEST_ACCEPT)) {
				if (resp.getTime() - time < timeToCompleteJobMils) {
					return false;
				}
			}

			if (resp.getResponseType() == ResponseType.CONSUMED) {
				time = resp.getTime();
			}
		}
		return true;
	}

	public static boolean checkBreaksRespected(List<Response> responses, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {

		List<Response> attendedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED).sorted()
				.collect(Collectors.toList());

		if (attendedResponses.size() <= testsPerWindow) {
			return true;
		}

		for (int x = testsPerWindow; x < attendedResponses.size(); x++) {
			if ((attendedResponses.get(x).getTime()
					- attendedResponses.get(x - testsPerWindow).getTime()) < windowLength) {
				return false;
			}
		}
		return true;
	}

}
