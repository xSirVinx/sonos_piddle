package piddle.sonos.si;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
	public static void main(String args[]) {

		// Test configuration variables
		int numInquiries = 1000;
		int numQAWorkers = 3;
		int timeToCompleteJobMils = 50;
		int testsPerWindow = 5;
		int windowLength = 600;
		int salesPerInquery = 5;

		/*
		 * Optimally, we dont create more threads that we have CPUs/cores. There are two
		 * default threads taken up by the test framework: the main thread, and the
		 * thread that creates all of the requests. We can give all of the other
		 * cores/threads to the QA team
		 */
		int maxConcurThreads = Runtime.getRuntime().availableProcessors() - 1;

		QAManager manager = new QAManager(numQAWorkers, maxConcurThreads, timeToCompleteJobMils,
				testsPerWindow, windowLength);
		ArrayList<Request> requests = new ArrayList<Request>();
		ExecutorService exec = Executors.newFixedThreadPool(1);

		requests.addAll(runTest(numInquiries, exec, manager));
		exec.shutdown();

		// Retrieve all the responses from the requests (i.e., join the futures) and
		// sort the responses by time they were sent
		List<Response> responses = requests.stream().map(Request::checkResponse).sorted()
				.collect(Collectors.toList());

		assert checkTestLengthRespected(responses,
				timeToCompleteJobMils) : "Found sales or button-show action that occured while QA was busy";
		assert checkBreaksRespected(responses, timeToCompleteJobMils, testsPerWindow,
				windowLength) : "QA member did not take the breaks he was owed.";

		long attendedInqueries = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.TEST_ACCEPT).sorted()
				.count();

		long attendedSales = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED).sorted().count();

		System.out.println("//////////////////Sales Stats///////////////////////////");
		System.out.println(String.format("Total Sales requests: %s",
				String.valueOf(numInquiries / salesPerInquery)));
		System.out.println(
				String.format("Num attended Sales requests: %s", String.valueOf(attendedSales)));

		getMissedSales(responses, timeToCompleteJobMils, testsPerWindow, windowLength);

		System.out.println("");
		System.out.println("//////////////////Inquery Stats/////////////////////////");
		System.out.println(String.format("Total sales inqueries: %s",
				String.valueOf(numInquiries - numInquiries / salesPerInquery)));
		System.out.println(String.format("Num inqueries shown buy button: %s",
				String.valueOf(attendedInqueries)));

		getMissedInqueries(responses, timeToCompleteJobMils, testsPerWindow, windowLength);

		manager.shutDown();
	}

	public static ArrayList<Request> runTest(int numSales, ExecutorService exec,
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

						if (manager.sellHydrant(curTime, req)) {
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

						if (manager.canSellHydrant(curTime, req)) {
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

	public static boolean checkTestLengthRespected(List<Response> responses,
			int timeToCompleteJobMils) {

		Map<String, List<Response>> attendedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED
						|| resp.getResponseType() == ResponseType.TEST_ACCEPT)
				.collect(Collectors.groupingBy(resp -> resp.getFulfilledBy()));

		for (String key : attendedResponses.keySet()) {
			long time = 0;
			List<Response> sorted = attendedResponses.get(key).stream().sorted()
					.collect(Collectors.toList());

			for (Response resp : sorted) {
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
		}
		return true;
	}

	public static boolean checkBreaksRespected(List<Response> responses, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {

		Map<String, List<Response>> attendedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED)
				.collect(Collectors.groupingBy(resp -> resp.getFulfilledBy()));

		for (String key : attendedResponses.keySet()) {
			List<Response> fulfilledByResponses = attendedResponses.get(key).stream().sorted()
					.collect(Collectors.toList());

			if (fulfilledByResponses.size() <= testsPerWindow) {
				break;
			}

			for (int x = testsPerWindow; x < fulfilledByResponses.size(); x++) {
				if ((fulfilledByResponses.get(x).getTime()
						- fulfilledByResponses.get(x - testsPerWindow).getTime()) < windowLength) {
					return false;
				}
			}
		}

		return true;
	}

	public static void getMissedSales(List<Response> responses, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {

		long totalAvgInefficiency = 0;
		int totalMissedSales = 0;

		Map<String, List<Response>> filteredResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED)
				.collect(Collectors.groupingBy(resp -> resp.getFulfilledBy()));

		List<Response> rejectedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.REJECTED)
				.collect(Collectors.toList());

		for (String key : filteredResponses.keySet()) {

			long memberAvgInefficiency = 0;
			int memberMissedSales = 0;
			ArrayList<Long> window = new ArrayList<Long>();

			filteredResponses.get(key).addAll(rejectedResponses);
			List<Response> fulfilledResponses = filteredResponses.get(key).stream().sorted()
					.collect(Collectors.toList());

			for (Response resp : fulfilledResponses) {
				if (resp.getResponseType() == ResponseType.REJECTED) {
					if (window.size() == 0) {
						System.out.println("Sale rejected when no requests have been made");
					} else if (resp.getTime() - window.get(0).longValue() > timeToCompleteJobMils
							&& (window.size() < testsPerWindow
									|| window.get(testsPerWindow - 1).longValue()
											+ windowLength < resp.getTime().longValue())) {
						memberMissedSales += 1;
						// Calculates the amount of time over the timeToCompleteJob that the QA
						// member
						// was busy.
						memberAvgInefficiency += resp.getTime() - window.get(0).longValue()
								- timeToCompleteJobMils;
					}
				}

				if (resp.getResponseType() == ResponseType.CONSUMED) {
					if (window.size() == testsPerWindow) {
						window.remove(testsPerWindow - 1);
					}

					window.add(0, resp.getTime());
				}
			}

			System.out
					.println(String.format("QA Member %s missed sales due to code inefficiency %s",
							key, String.valueOf(memberMissedSales)));
			if (memberMissedSales != 0) {
				System.out.println(String.format(
						"QA Member %s average inefficiency causing missed sale: %s milisecond", key,
						String.valueOf(memberAvgInefficiency / memberMissedSales)));
			}

			if (memberMissedSales != 0) {
				totalAvgInefficiency += memberAvgInefficiency / memberMissedSales;
			}
			totalMissedSales += memberMissedSales;

		}

		System.out.println(String.format("Total missed sales due to code inefficiency %s",
				String.valueOf(totalMissedSales)));
		if (totalMissedSales != 0) {
			System.out.println(String.format(
					"Average inefficiency causing missed sale: %s milisecond",
					String.valueOf(totalAvgInefficiency / filteredResponses.keySet().size())));
		}

	}

	public static void getMissedInqueries(List<Response> responses, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {

		long totalAvgInefficiency = 0;
		int totalMissedSales = 0;

		Map<String, List<Response>> filteredResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED)
				.collect(Collectors.groupingBy(resp -> resp.getFulfilledBy()));

		List<Response> rejectedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.TEST_REJ)
				.collect(Collectors.toList());

		for (String key : filteredResponses.keySet()) {

			long memberAvgInefficiency = 0;
			int memberMissedSales = 0;
			ArrayList<Long> window = new ArrayList<Long>();

			filteredResponses.get(key).addAll(rejectedResponses);
			List<Response> fulfilledResponses = filteredResponses.get(key).stream().sorted()
					.collect(Collectors.toList());

			for (Response resp : fulfilledResponses) {
				if (resp.getResponseType() == ResponseType.TEST_REJ) {
					if (window.size() == 0) {
						System.out.println("Sale rejected when no requests have been made");
					} else if (resp.getTime() - window.get(0).longValue() > timeToCompleteJobMils
							&& (window.size() < testsPerWindow
									|| window.get(testsPerWindow - 1).longValue()
											+ windowLength < resp.getTime().longValue())) {
						memberMissedSales += 1;
						// Calculates the amount of time over the timeToCompleteJob that the QA
						// member
						// was busy.
						memberAvgInefficiency += resp.getTime() - window.get(0).longValue()
								- timeToCompleteJobMils;
					}
				}

				if (resp.getResponseType() == ResponseType.CONSUMED) {
					if (window.size() == testsPerWindow) {
						window.remove(testsPerWindow - 1);
					}

					window.add(0, resp.getTime());
				}
			}

			System.out.println(
					String.format("QA Member %s missed inqueries due to code inefficiency %s", key,
							String.valueOf(memberMissedSales)));
			if (memberMissedSales != 0) {
				System.out.println(String.format(
						"QA Member %s average inefficiency causing missed inquery: %s milisecond",
						key, String.valueOf(memberAvgInefficiency / memberMissedSales)));
			}

			totalAvgInefficiency += memberAvgInefficiency / memberMissedSales;
			totalMissedSales += memberMissedSales;

		}

		System.out.println(String.format("Total missed inqueries due to code inefficiency %s",
				String.valueOf(totalMissedSales)));
		if (totalMissedSales != 0) {
			System.out.println(String.format(
					"Average inefficiency causing missed inquery: %s milisecond",
					String.valueOf(totalAvgInefficiency / filteredResponses.keySet().size())));
		}
	}

}
