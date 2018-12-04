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

		/*@formatter:off
		 * 
		 * Test configuration variables
		 * 
		 * numInquiries - number of "requests" sent to the "server" 
		 * numQAWorkers - the number of pups that are testing hydrants 
		 * timeToCompleteJobMils - the time it* takes a QA worker to test a hydrant 
		 * testsPerWindow - the negotiated number of tests a qa worker can perform in a given time period 
		 * windowLength - the given time period during which only testPerWindow tests can be performed
		 * salesPerInquery - the number of canSellHydrant calls per sellHydrant call
		 * 
		 * Application timescale:
		 * 
		 * 10mS in app = 1 min scenario 
		 * ...
		 * 50mS   = 5 min
		 * 600mS  = 1 hr
		 * 1200mS = 2 hrs
		 * 
		 *
		 *@formatter:on
		 */

		int numInquiries = 1200;
		int numQAWorkers = 3;
		int timeToCompleteJobMils = 50;
		int testsPerWindow = 5;
		int windowLength = 600;
		int salesPerInquery = 5;

		/*@formatter:off
		 * 
		 * Other setup variables
		 * 
		 * maxConcurThreads - The number of threads the application can use. There are
		 *    two default threads taken up by the test framework: the main thread, and the
		 *    thread that creates all of the requests. Rule of thumb is that thread count
		 *    should be numCores or numCores+1 to avoid resource thrashing. On my 4 core machine,
		 *    there are 2 cores unused by the test harness, essentially making it a 2 core
		 *    machine from the application's standpoint. Running the app several times, i
		 *    got the best performance when i gave the application 3 threads, which is the
		 *    number of unused cores plus 1.
		 * 
		 * QAManager - manages the QA team members. Also implements the canSellHydrant and sellHydrant methods
		 * 
		 * requests - an array that contains all of the "requests" made to the "server." Its really just a wrapper for a CompleatableFuture
		 * 
		 * exec - the threadpool used by the test harness (note this is NOT the threadpool used by the QAManager and the QA team members). 
		 *         I specifically wanted two separate threadpools so that i didnt run into deadlock issues. If the threadpool responsible 
		 *         for making threads was shared with the QAWorkers, it is possible the QA workers would consume all the threads and make 
		 *         request submission impossible. Likewise, its possible that request loop would consume all the threads and QAWorkers wouldnt
		 *         be able to perform any test jobs. Both of these situations could lead to a deadlock. 
		 * 
		 * @formatter:on
		 */
		int maxConcurThreads = Runtime.getRuntime().availableProcessors() - 1;

		QAManager manager = new QAManager(numQAWorkers, maxConcurThreads, timeToCompleteJobMils,
				testsPerWindow, windowLength);
		ArrayList<Request> requests = new ArrayList<Request>();
		ExecutorService exec = Executors.newFixedThreadPool(1);

		requests.addAll(runTest(numInquiries, exec, manager, salesPerInquery));
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

	/**
	 * Generates "requests" that are sent to the server. sellHydrant calls are made
	 * once per salesPerInquery number of canSellHydrant calls
	 * 
	 * This method uses the exec threadpool, anonymous functions, and random-time
	 * sleep to send "requests" asynchronously at random intervals.
	 * 
	 * @param numSales
	 * @param exec
	 * @param manager
	 * @return
	 */
	public static ArrayList<Request> runTest(int numSales, ExecutorService exec, QAManager manager,
			int salesPerInquery) {

		ArrayList<Request> requests = new ArrayList<Request>();

		for (int x = 0; x < numSales; x++) {

			Request req = new Request();
			requests.add(req);

			// sellHydrant counts against qa worker capacity
			if (x % salesPerInquery == 0) {
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
				// canSellHydrant just checks to see if the qa workers are busy
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

	/**
	 * Unit test that ensures the thread lock associated with performing the QA task
	 * is working properly (i.e., it ties up a QA worker for the appropriate amount
	 * of time, rendering them unable to consume other requests).
	 * 
	 * For each QA worker, this method checks that the worker did not consume a sale
	 * or positively acknowledge a canSellHydrant inquiry during the time a QA
	 * worker should have been testing a hydrant (i.e., from the moment of each
	 * consumed request to timeToCompleteJobMils later).
	 * 
	 * @param responses
	 * @param timeToCompleteJobMils
	 * @return
	 */
	public static boolean checkTestLengthRespected(List<Response> responses,
			int timeToCompleteJobMils) {

		// filter the response array to consumed sales and positively acknowledged
		// inquiries. Organize the arrays by QA worker UUID
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

	/**
	 * Ensures that no more than the testPerWindow tests were performed by each qa
	 * worker during any period of windowLength
	 * 
	 * Uses a sliding window across a sorted array of CONSUMED responses.
	 * 
	 * @param responses
	 * @param timeToCompleteJobMils
	 * @param testsPerWindow
	 * @param windowLength
	 * @return
	 */
	public static boolean checkBreaksRespected(List<Response> responses, int timeToCompleteJobMils,
			int testsPerWindow, int windowLength) {

		// filter the response array to consumed sales. Organize the arrays by QA worker
		// UUID
		Map<String, List<Response>> attendedResponses = responses.stream()
				.filter(resp -> resp.getResponseType() == ResponseType.CONSUMED)
				.collect(Collectors.groupingBy(resp -> resp.getFulfilledBy()));

		for (String key : attendedResponses.keySet()) {
			// ensure each qa worker's array of consumed responses is sorted properly
			List<Response> fulfilledByResponses = attendedResponses.get(key).stream().sorted()
					.collect(Collectors.toList());

			// if the worker hasnt filled up the window then there is no need for a break;
			if (fulfilledByResponses.size() <= testsPerWindow) {
				break;
			}

			// ensures that each consumed response is windowLength away from the next
			// consumed response added to the sliding window (i.e., no more than
			// testPerWindow performed in windowLength).
			for (int x = testsPerWindow; x < fulfilledByResponses.size(); x++) {
				if ((fulfilledByResponses.get(x).getTime()
						- fulfilledByResponses.get(x - testsPerWindow).getTime()) < windowLength) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * 
	 * Searches through the REJECTED (rejected sellHydrant calls) to identify denied
	 * requests that should have been consumed.
	 * 
	 * There is some wasted time in generating requests, testing conditionals, and
	 * context switching between threads, so not every QA worker will finish a QA
	 * test in exactly timeToCompleteJobMils. The method below tests to see if a
	 * rejected sellHydrant call could have been attended to by a QA worker had the
	 * system been 100% efficient.
	 * 
	 * 
	 * @param responses
	 * @param timeToCompleteJobMils
	 * @param testsPerWindow
	 * @param windowLength
	 */
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
						// Calculates the amount of time beyond the timeToCompleteJobMils that the
						// QA thread was still locked.
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

	/**
	 * 
	 * Searches through the TEST_REJ (rejected canSellHydrant calls) to identify
	 * denied requests that should have been accepted.
	 * 
	 * There is some wasted time in generating requests, testing conditionals, and
	 * context switching between threads, so not every QA worker will finish a QA
	 * test after exactly timeToCompleteJobMils. The method below tests to see if a
	 * rejected canSellHydrant call could have been accepted by a QA worker had the
	 * system been 100% efficient.
	 * 
	 * 
	 * @param responses
	 * @param timeToCompleteJobMils
	 * @param testsPerWindow
	 * @param windowLength
	 */
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
						// Calculates the amount of time beyond the timeToCompleteJobMils that the
						// QA thread was still locked.
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
