import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Communication manager is responsible for:
 * 		initiating tcp connection to registry
 * 		initiating udp server for peer communication
 * 		running a periodic multicast that broadcasts to all peers
 * 		handling all incoming and outgoing messages
 * @author joshuaplosz
 *
 */
public class CommManager {
	
	private String teamName = "";
	private String language = "java";
	private long t = 2000; // 2 second broadcast interval
	
	private String registryIP;
	private String registryPort;
	private String udpPort;
	private String visibleIp;
	private TCPConnection tcp;
	private UDPServer udp;
	
	private GroupManager gm;	
	private AtomicInteger timestamp = new AtomicInteger(0);
	
	// timer to schedule re-sending of snippets
	Timer timer;
	// mapping of peer's address and timestamp to the number of times we have sent the snippet
	HashMap<String, Integer> expectingAcks = new HashMap<>();
	// mapping of peer's address and timestamp to the timer that controls the sending of the snippet
	HashMap<String, Timer> timers = new HashMap<>();
	
	LinkedList<String> acksReceived = new LinkedList<>();

	CommManager(String registryIP, String registryPort, String teamName, String registryLocation) {
		this.registryIP = registryIP;
		this.registryPort = registryPort;
		this.teamName = teamName;

		gm = new GroupManager();
		
		udp = new UDPServer(this);
		udp.listen();
		
		// add myself to the list of peers in the system
		// gm.addSingleSource(visibleIp + ":" + udpPort, visibleIp, udpPort);
		
		tcp = new TCPConnection(registryIP, registryPort, this);
		tcp.listen();
		
		if (registryLocation.equals("remote")) {
			visibleIp = udp.getVisibleIp();
		} else {
			visibleIp = tcp.getLocalIp();
		}
		udpPort = udp.getLocalUdpPort();

		// start periodic thread that broadcasts to all peers at 't' intervals
		Thread tm = new Thread(new TimedMulticast(t));
		tm.start();
	}
	
	/**
	 * Any incoming message from either the UDP server or TCP connection
	 * gets parsed, line by line, and handled appropriately.
	 * @param msg - single line from UDP/TCP message as a string
	 * @param in_ip - IP address from message sender
	 * @param in_port - Port number from message sender
	 */
	public void parse(String msg, String in_ip, String in_port) {	

		/////////// udp msgs ////////////
		if (msg.substring(0, 4).equals("peer")) {
			MyUtil.log("Received PEER msg from " + in_ip + ":" + in_port + "  MSG: " + msg, 1);
			String peer = msg.substring(4);
			String[] comp = peer.split(":");
			
			// if the source peer is new send it past snippets
			if (!gm.addSingleSource(peer, in_ip, in_port)) {
				MyUtil.log("New source!  Send catchup messages", 1);
				catchUpSnippets(comp[0], comp[1]);
			}
			
			// if the source peer was previously inactive send it past snippets 
			if (!gm.peerActive(in_ip, in_port)) {
				MyUtil.log("Source re-activated!  Send catchup messages", 1);
				catchUpSnippets(in_ip, in_port);
			}
			
			// update the latest time we have heard from the source
			gm.updatePeer(in_ip, in_port);
		
		} else if (msg.substring(0, 4).equals("snip")) {
			MyUtil.log("Received SNIP msg", 1);
			gm.updatePeer(in_ip, in_port);
			
			// "snip"_<timestamp>_<content>  <--- incoming snippet format
			String[] line = msg.substring(4).trim().split(" ");
			
			// line = <timestamp><content>
			String content = "";
			for (int i = 1; i < line.length; i++) 
				content += line[i] + " ";
			
			MyUtil.log(line[0] + " " + content + " " + in_ip + ":" + in_port, 2);
			int msgTimestamp = Integer.parseInt(line[0]);
			compareToCurrTimestamp(msgTimestamp);
			gm.storeSnippet(line[0], content, in_ip, in_port);
			
			String ack = "ack " + msgTimestamp;
			udp.toPeer(ack, in_ip, in_port);
		
		} else if (msg.substring(0, 4).equals("stop")) {
			MyUtil.log("Received STOP msg", 1);
			registryStop(in_ip, in_port);
			
		} else if (msg.substring(0, 4).equals("ctch")) {
			MyUtil.log("Received CTCH msg", 1);
			catchUp(msg.substring(4));
		
		} else if (msg.substring(0, 3).equals("ack")) {
			MyUtil.log("Received ACK msg", 1);
			String ackMsg = in_ip + ":" + in_port + " " + msg.substring(3).trim();
			acksReceived.add(msg.substring(3).trim() + " " + in_ip + ":" + in_port + "\n");
			processAck(ackMsg);
		
		/////////////////////////////////

		} else if (msg.substring(0, 3).equals("get")) {
			String[] line = msg.split(" ");
			
			// get team name
			if (line[1].equals("team") && line[2].equals("name")) {
				MyUtil.log("Received GET NAME msg");
				getTeamName();

			// get code
			} else if (line[1].equals("code")){
				MyUtil.log("Received GET CODE msg");
				getCode();
			
			// get report
			} else if (line[1].equals("report")) {
				MyUtil.log("Received GET REPORT msg");
				getReport();
			
			// get location
			} else if (line[1].equals("location")){
				MyUtil.log("Received GET LOCATION msg");
				getLocation();
				
			// unknown get request
			} else {
				MyUtil.log("Unusual request", 2);
			}
			
		} else if (msg.substring(0, 5).equals("close")) {
			// close registry connection
			MyUtil.log("Received CLOSE msg", 1);
			close();
			
		} else if (msg.substring(0, 7).equals("receive")) {
			// receive peers
			MyUtil.log("Received RECEIVE PEERS msg", 1);
			int numOfPeers = Integer.parseInt(tcp.readLineFromRegistry());
			Queue<String> more = new LinkedList<String>();
			more.add(String.valueOf(numOfPeers));
			for (int i = 0; i < numOfPeers; i++) {
				more.add(tcp.readLineFromRegistry());						
			}
			receivePeers(more);
		}
	}
			
	/**
	 * Checks if the provided snippet already exists in our collection
	 * and adds the snippet if not.
	 * @param msg
	 */
	private void catchUp(String msg) {
		MyUtil.log("Catchup msg: " + msg, 2);
		String[] line = msg.trim().split(" ", 3);
		// line = <original sender><timestamp><content>
		String[] msgAddr = line[0].split(":");
		
		MyUtil.log("catchup msg content: " + line[2], 2);
		
		gm.storeSnippet(line[1], line[2], msgAddr[0], msgAddr[1]);
	}

	/**
	 * TODO
	 * @param string
	 * @param string2
	 */
	private void catchUpSnippets(String ip, String port) {
		for (String snip : gm.getSnippets()) {
			String msg = "ctch";
			String[] comp = snip.trim().split(" ");
			// comp = <timestamp><content><ip>":"<port>"\n"
			String content = "";
			for (int i = 1; i < comp.length - 1; i++) {
				content += comp[i] + " ";
			}
			msg += comp[comp.length-1] + " " + comp[0] + " " + content;
			udp.toPeer(msg, ip, port);
		}
	}
	
	/**
	 * TODO
	 * @param substring
	 */
	private void processAck(String msg) {
		MyUtil.log("Ack msg: " + msg);
		
		expectingAcks.remove(msg);
		if (expectingAcks.get(msg) == null) {
			Timer t = timers.get(msg);
			if (t != null) {
				t.cancel();
				t.purge();
				timers.remove(msg);
			}
		}
	}

	/**
	 * Compares the foregin timestamp to the local timestamp.
	 * Synchronized to make timestamp thread safe between the two operations.
	 * @param msgTimestamp - foreign timestamp
	 */
	private synchronized void compareToCurrTimestamp(int msgTimestamp) {
		synchronized (timestamp) {
			if (timestamp.get() < msgTimestamp)
				timestamp.set(msgTimestamp);
		}
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 */
	private void registryStop(String ip, String port) {
		MyUtil.log("Received stop from registry at ip: " + ip + ":" + port, 2);
		String msg = "ack" + teamName;
		udp.toPeer(msg, ip, port);

		shutdown();
	}
	
	/**
	 * Add the list of peers received from the registry to the collection
	 * of list sources held by the group manager.
	 * @param msg - list of peers from registry
	 */
	private void receivePeers(Queue<String> msg) {
		String srcIP = tcp.getIp();
		String srcPORT = tcp.getPort();
		
		gm.addListSource(srcIP, srcPORT, msg);
	}

	/**
	 * The collection and sending of the following data, in order, 
	 * to the registry via TCP connection.
	 * 		- number of peers in system
	 * 		- list of peers in system
	 * 		- number of sources providing a list of peers
	 * 		- list of sources providing a list of peers with the 
	 * 		  date the source was received, the number peers in list,
	 * 		  and the list of peers
	 * 		- number of sources providing a single peer
	 * 		- list of sources providing a single peer
	 * 		- number of messages this process broadcasted to system peers
	 * 		- list of messages this process broadcasted to system peers
	 * 		- number of snippets present in system
	 * 		- list of snippets present in system
	 */
	private void getReport() {
		// number of currentPeers
		LinkedList<String> currentPeers = gm.getCurrentPeers();
		MyUtil.log("Reporting number of current peers");
		tcp.toRegistry(currentPeers.size() + "\n");
		
		// all peers in currentPeers
		MyUtil.log("Reporting current peers");
		for (String p : currentPeers) {
			tcp.toRegistry(p);
		}
		
		// number of listSources
		LinkedList<String> listSources = gm.getListSources();
		MyUtil.log("Reporting number of list sources");
		tcp.toRegistry(listSources.size() + "\n");
		
		// all listSources + date the list was received + number of peers in list + peers in list
		MyUtil.log("Reporting list sources and peers provided");
		for (String src : listSources) {
			tcp.toRegistry(src);
		}
		
		// number of singleSources
		LinkedList<String> singleSources = gm.getSingleSources();
		MyUtil.log("Reporting number of sources from peers");
		tcp.toRegistry(singleSources.size() + "\n");
		
		// all singleSources
		MyUtil.log("Reporting sources from peers");
		for (String src : singleSources) {
			tcp.toRegistry(src);
		}

		// number of messages I sent
		MyUtil.log("Reporting number of messages sent");
		tcp.toRegistry(msgsSent.size() + "\n");
		
		// my sent messages
		MyUtil.log("Reporting messages sent");
		for (String msg : msgsSent) {
			tcp.toRegistry(msg);
		}
		
		// number of known snippets
		MyUtil.log("Reporting number of known snippets");
		LinkedList<String> snippets = gm.getSnippets();
		tcp.toRegistry(snippets.size() + "\n");
		
		// list of known snippets
		MyUtil.log("Reporting all snippets received");
		snippets.forEach(snip -> {
			tcp.toRegistry(snip);
		});
		
		// number of acks received
		MyUtil.log("Reporting number of acks received");
		tcp.toRegistry(acksReceived.size() + "\n");
		
		// acks received
		MyUtil.log("Reporting acks received");
		acksReceived.forEach(ack -> {
			tcp.toRegistry(ack);
		});
	}

	/**
	 * Opens the current src directory, reads each src file, line
	 * by line, and sends the contents to the registry.  Signaling
	 * completion by sending "..." on a single line.
	 */
	private void getCode() {
		tcp.toRegistry(language + "\n");
		String projDir = new File("").getAbsolutePath();
		File srcCodeDir = new File(projDir + "/src");
		File[] files = srcCodeDir.listFiles();
		for (File file : files) {
			BufferedReader fileReader;
			try {
				fileReader = new BufferedReader(new FileReader(file.toString()));

				String line;
				while((line = fileReader.readLine()) != null) {
					tcp.toRegistry(line + "\n");
				}
				
			} catch (IOException e) {
				MyUtil.log("Unable to read from file when sending code.", 2);
				e.printStackTrace();
			}
		}
		tcp.toRegistry("..." + "\n");
	}

	private void getTeamName() {
		tcp.toRegistry(teamName + "\n");
	}
	
	private void getLocation() {
		String myLoc = visibleIp + ":" + udpPort + "\n";
		tcp.toRegistry(myLoc);
	}
	
	/**
	 * Closes connection with registry via TCP
	 */
	private void close() {
		MyUtil.log("Closing connection to registry.");
		tcp.close();
	}
	
	boolean reportedToRegistry = false;
	
	/**
	 * Closes connection to peers via UDP. If registry has
	 * not yet requested a report then a connection is 
	 * reestablished with the registry via TCP.
	 */
	public void shutdown() {
		MyUtil.log("Shutting down connection to peers.");
		timers.forEach((s, t) -> {
			t.cancel();
			t.purge();
		});
		
		udp.close();
		
		if (!reportedToRegistry) {		
			reportedToRegistry = true;
			MyUtil.log("Re-establishing connection with registry.");
			tcp = new TCPConnection(registryIP, registryPort, this);
			tcp.listen();
		}
	}
	
	/**
	 * Removes a peer from the list of current system peers.
	 * @param ip - IP address of peer to be removed
	 * @param port - Port number of peer to be removed
	 */
	public void drop(String ip, String port) {
		gm.removePeer(ip, port);
	}
	
	// <outgoing peer>_<me as peer>_<date><newline>
	private Vector<String> msgsSent = new Vector<String>();
	
	/**
	 * A thread that broadcasts, in intervals of 't' milliseconds, a 
	 * random peer in this processes list of current peers to all 
	 * system peers.  After 5 broadcasts we refresh the list of 
	 * current peers to remove peers we have not heard from since
	 * last refresh (5 broadcasts ago).  
	 * @author joshuaplosz
	 *
	 */
	private class TimedMulticast implements Runnable {
		long interval;
		
		TimedMulticast (long interval) {
			this.interval= interval;
		}
		
		@Override
		public void run() {			
			try {
				while(udp.connectionOpen) {
					// wait a determined amount of time to send peer messages 
					Thread.sleep(interval);
					
					// update the status of our list of peers
					gm.refreshPeers();
					
					String peer = gm.getRandomPeer();
					if (peer != null) {
						timestamp.incrementAndGet();
						LinkedList<String> peers = gm.getCurrentActivePeers();
						String msg = "peer" + peer;
						for (String p : peers) {
							String comp[] = p.replace("\n", "").split(":");
							String p_ip = comp[0];
							String p_port = comp[1];
							udp.toPeer(msg, p_ip, p_port);
						}
					}
				}
				
			} catch (InterruptedException e) {
				MyUtil.log("Timed broadcast interrupted.", 2);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Sends the current timestamp and snippet content to all system peers
	 * as a "snip" message.
	 * @param msg - snippet content as a string
	 */
	public void sendSnippet(String msg) {
		if (udp.connectionOpen) {
			int snipTimestamp = timestamp.incrementAndGet();
			
			LinkedList<String> peers = gm.getCurrentActivePeers();
			for (String p : peers) {
				p = p.trim();
				timer = new Timer();
				TimerTask task = new SnippetSender(msg, p, snipTimestamp);
				timers.put(p + " " + snipTimestamp, timer);
				timer.schedule(task, 0, 10 * 1000);
			}
		}
	}
	
	private class SnippetSender extends TimerTask {
		String msg;
		String addr;
		String snipTimestamp;
		String mapping;
		
		public SnippetSender(String msg, String addr, int snipTimeStamp) {
			this.msg = msg;
			this.addr = addr;
			snipTimestamp = String.valueOf(snipTimeStamp);
			mapping = this.addr + " " + snipTimestamp;
		}

		@Override
		public void run() {
			if (expectingAcks.containsKey(mapping)) {
				Integer count = expectingAcks.get(mapping);
				if (count <= 3) {
					expectingAcks.replace(mapping, ++count);
				} else {
					expectingAcks.remove(mapping);
					gm.setPeerInactive(addr);
					this.cancel();
					canceltTimer(this);
				}
			} else {
				expectingAcks.put(mapping, 1);				
			}
			String[] ip_port = addr.split(":");
			udp.toPeer("snip " + snipTimestamp + " " + msg, ip_port[0], ip_port[1]);
			
			LocalDateTime dateTime = LocalDateTime.now();
			DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			msgsSent.add(ip_port[0] + ":" + ip_port[1] + " " +
						 visibleIp + ":" + udpPort + " " + 
						 dateTime.format(format) + "\n");
		}

		private void canceltTimer(SnippetSender task) {
			Timer timer = timers.get(task);
			timer.cancel();
			timer.purge();
		}
	}
}
