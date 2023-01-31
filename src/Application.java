import java.util.HashMap;
import java.util.Scanner;

public class Application {
	
	private static String defaultRegistryIp 		= "localhost";
	private static String defaultRegistryPort 		= "55921";
	private static String defaultTeamName 			= "JPlosz";
	private static String defaultRegistryLocation 	= "local";
	
	private static CommManager cm;

	/**
	 * Creates a new communication manager and provides it a registry ip and
	 * port to connect to via tcp/ip connection.
	 * @param ip - ip of registry
	 * @param port - port of registry
	 */
	private static void connectToRegistry(String ip, String port, String teamName, String registryLocation) {
		cm = new CommManager(ip, port, teamName, registryLocation);
	}
	
	/**
	 * System entry point.  Connects to registry then sits in idle loop
	 * to wait for snippet from user via command line.
	 * @param args
	 */
	public static void main(String[] args) {
		MyUtil.disableDebugMsgs();
		
		// Set command line/default values for registry ip, registry port, team name
		HashMap<String, String> params = MyUtil.parseCommandLine(args);
		String rIp = 	params.getOrDefault("-ip", defaultRegistryIp);
		String rPort = 	params.getOrDefault("-port", defaultRegistryPort);
		String tName = 	params.getOrDefault("-tn",  defaultTeamName);
		String regLoc=	params.getOrDefault("-l", defaultRegistryLocation);
		
		Application.connectToRegistry(rIp, rPort, tName, regLoc);
		
		// run loop
		Scanner sc = new Scanner(System.in);
		String snippet;
		
		while (true) {
			snippet = sc.nextLine();
			
			if (snippet.equals("")) {
				continue; // don't send empty msg to CommManager
			}
			
			cm.sendSnippet(snippet);

			if (snippet.equals("stop")) { 
				cm.reportedToRegistry = true; // skip reporting if shutting down manually
				cm.shutdown();
				break;
			}
		}
		sc.close();
	}
}
