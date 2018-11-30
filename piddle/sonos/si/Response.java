package piddle.sonos.si;

/**
 * The response class tracks what type of response was issued and the time at
 * which it was issued. We use the time values to validate that the QA team
 * was/was not busy when the request was received.
 * 
 * @author Scott
 *
 */
public class Response implements Comparable<Response> {
	private Long time;
	private ResponseType type;

	public Response(Long time, ResponseType type) {
		this.time = time;
		this.type = type;
	}

	public Long getTime() {
		return this.time;
	}

	public ResponseType getResponseType() {
		return this.type;
	}

	@Override
	public int compareTo(Response resp) {
		if (this.time - resp.getTime() < 0) {
			return -1;
		} else if (this.time - resp.getTime() > 0) {
			return 1;
		} else {
			return 0;
		}
	}

}
