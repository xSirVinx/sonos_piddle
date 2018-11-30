package piddle.sonos.si;

/**
 * The ResponseType enum represents the four types of responses that are
 * possible for this test.
 * 
 * @formatter:off
 * 
 * 				CONSUMED -- a QA team member is acting on the request.
 * 
 *                REJECTED -- a QA team member was asked to act on the request
 *                but could not.
 * 
 *                TEST_ACCEPT -- a QA team member was asked if they are
 *                currently available (and they were) but not asked to consume a
 *                request.
 * 
 *                TEST_REJECT -- a QA team member was asked if they are
 *                currently available (and they were not) but not asked to
 *                consume a request.
 * 
 * @formatter:on
 * @author Scott
 *
 */
public enum ResponseType {
	CONSUMED, REJECTED, TEST_ACCEPT, TEST_REJ, ERROR;
}
