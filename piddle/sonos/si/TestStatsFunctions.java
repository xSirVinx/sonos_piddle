package piddle.sonos.si;

/**
 * A Runnable that takes as part of its constructor a HashMap<String, T> where T
 * can be any useful object. The idea here is to make a "utility" runnable
 * object for use in testing. The generic HashMap gives some flexibility to the
 * types of data that can be passed into each runnable and thus used for
 * diagnostics.
 * 
 * @author Scott
 *
 * @param <T>
 */
public class TestStatsFunctions {
	private long avrg = 0;
	private long min, max = 0;

	public static Long average(Long prev, Long cur) {

		return cur;
	}
}