package piddle.sonos.si;

/**
 * The response class tracks what type of response was issued and the time at
 * which it was issued. We use the time values to validate that the QA team
 * was/was not busy when the request was received.
 * 
 * @author Scott
 *
 */
public class Response {
	private Long time;
	ResponseType type;

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

}
