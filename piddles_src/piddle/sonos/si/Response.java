package piddle.sonos.si;

/**
 * The response class tracks what type of response was issued, the time at which
 * it was issued, as well as the QA worker that consumed/accepted it (if
 * applicable). The time values are used by the unit tests to validate that the
 * QA team was or was not busy when the request was received.
 * 
 * Note: If i did this again, i wouldnt use the Response object to record
 * information. I'd setup a SQLite database and write to it on Request creation
 * and end (i.e., record the request creation time and the response time). In a
 * real system, you'd want to log requests anyway both for security auditability
 * and to harvest click-through data. I think it would have been easier to query
 * a SQL database than deal with streams of Responses (as i did in the unit
 * tests in the Main function). I also feel like recording the information in
 * the Response toes the line of pushing Unit testing code into the application
 * logic, which i dont like.
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

	/**
	 * Method required to use stream.sort
	 */
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
