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
	private Long time = null;
	private ResponseType type = null;
	private String fulfilledBy = null;

	public Response(Long time, ResponseType type) {
		this.time = time;
		this.type = type;
	}

	public Response() {

	}

	public Long getTime() {
		return this.time;
	}

	public void setResponseTime(long time) {
		this.time = new Long(time);
	}

	public String getFulfilledBy() {
		return this.fulfilledBy;
	}

	public void setFulfilledBy(String uuid) {
		this.fulfilledBy = uuid;
	}

	public void setResponseType(ResponseType type) {
		this.type = type;
	}

	public ResponseType getResponseType() {
		return this.type;
	}

	@Override
	public int compareTo(Response resp) {
		if (this.time - resp.getTime().longValue() < 0) {
			return -1;
		} else if (this.time - resp.getTime().longValue() > 0) {
			return 1;
		} else {
			return 0;
		}
	}

}
