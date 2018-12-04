package piddle.sonos.si;

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
	private Response resp = null;

	public Request() {
		this.future = new CompletableFuture<Response>();
		this.resp = new Response();
	}

	public void end(ResponseType resp, long curTime) {
		this.getResponse().setResponseTime(curTime);
		switch (resp) {
		case CONSUMED:
			this.getResponse().setResponseType(ResponseType.CONSUMED);
			this.future.complete(this.resp);
			break;
		case REJECTED:
			this.getResponse().setResponseType(ResponseType.REJECTED);
			this.future.complete(this.resp);
			break;
		case TEST_ACCEPT:
			this.getResponse().setResponseType(ResponseType.TEST_ACCEPT);
			this.future.complete(this.resp);
			break;
		case TEST_REJ:
			this.getResponse().setResponseType(ResponseType.TEST_REJ);
			this.future.complete(this.resp);
			break;
		}
	}

	public void error(Exception e) {
		this.future.completeExceptionally(e);
	}

	public Response getResponse() {
		return this.resp;
	}

	public Response checkResponse() {
		return this.future.join();
	}
}
