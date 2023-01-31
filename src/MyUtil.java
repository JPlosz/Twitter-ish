import java.util.HashMap;

/**
 * Set of custom utility functions used for general application management.
 * @author joshuaplosz
 *
 */
public class MyUtil {
	private static boolean showDebug = true; // flag to turn off debug logs

	/**
	 * Display a message on terminal.  Appearance of message is 
	 * depicted by the level provided.  Higher levels are more visible
	 * for large terminal outputs.  Levels 0, 1 are considered "debug"
	 * levels and will not be displayed if the "display" flag is false.
	 * @param msg - message to be displayed, as a string
	 * @param lvl - level of visibility between [0, 3], as an integer
	 */
	public static void log(String msg, int lvl) {
		switch(lvl) {
		case 0:
			print(msg, lvl);
			break;
		case 1:
			print("    " + msg, lvl);
			break;
		case 2:
			print("  **  " + msg + "  **", lvl);
			break;
		case 3:
			print("\n**********************\n", lvl);
			print("    " + msg, lvl);
			print("\n**********************\n", lvl);
		}
	}
	
	/**
	 * No level provided will default to a level 0 debug message.
	 * @param msg
	 */
	public static void log(String msg) {
		log(msg, 0);
	}
	
	/**
	 * Shorthand terminal print function.  Checks if "display" flag
	 * is set to ignore logs with level of visibility under 2.
	 * @param s
	 * @param lvl
	 */
	private static void print(String s, int lvl) {
		if (showDebug || lvl >= 2) System.out.println(s);
	}
	
	/**
	 * Groups command line arguments as a flag/value pair and 
	 * stores them in a hashmap.  Assumes 
	 * @param args - complete array of arguments passed at the command line
	 * @return HashMap of flag/value pairs
	 */
	public static HashMap<String, String> parseCommandLine(String[] args) {
		HashMap<String, String> params = new HashMap<String, String>();
		
		// store the flag/value pairs if a flag indicator('-') is seen
		for (int j = 0; j+1 < args.length; j++) {
			if (args[j].charAt(0) == '-') {
				params.put(args[j], args[j+1]);
			}
		}
		
		return params;
	}
	
	public static void enableDebugMsgs() {
		MyUtil.showDebug = true;
	}
	
	public static void disableDebugMsgs() {
		MyUtil.showDebug = false;
	}
}
