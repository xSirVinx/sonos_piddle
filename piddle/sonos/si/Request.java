package piddle.sonos.si;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * The request class is just a wrapper for a CompletableFuture. It helps with
 * readability given that the scenario being imagined is an HTTP request being
 * made to a server.
 * 
 * @author Scott
 *
 */
public class Request {
	private CompletableFuture<Response> future;

	public Request() {
		this.future = new CompletableFuture<Response>();
	}

	/**
	 * Simulates a response to a request. In reality this would be some serialized
	 * data consumable by a browser/application (JSON, HTML, images, etc) but for
	 * this test we just need to know if a QA member consumed the request or not.
	 */
	public void end(ResponseType resp) {
		long curTime = ZonedDateTime.now().toInstant().toEpochMilli();
		switch (resp) {
		case CONSUMED:
			this.future.complete(new Response(curTime, resp));
			break;
		case REJECTED:
			this.future.complete(new Response(curTime, resp));
			break;
		case TEST_ACCEPT:
			this.future.complete(new Response(curTime, resp));
			break;
		case TEST_REJ:
			this.future.complete(new Response(curTime, resp));
			break;
		}
	}

	public Response checkCompletion() {
		return future.join();
	}
}
