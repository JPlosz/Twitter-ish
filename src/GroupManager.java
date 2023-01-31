import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;

/**
 * Group manager is responsible for the storage system peers, sources, and snippets.
 * It also provides a CRUD interface for those storages for the communication manager.
 * @author joshuaplosz
 *
 */
public class GroupManager {
	
	private Vector<Peer> currentPeers = new Vector<Peer>();
	private Vector<Source> singleSources = new Vector<Source>();
	private Vector<Source> listSources = new Vector<Source>();
	private Vector<Snippet> snippets = new Vector<Snippet>();
	
	/**
	 * A single system peer uniquely identified by IP address
	 * and port number.  A date signature is used to signify
	 * the last time this process received a message from this peer.
	 * @author joshuaplosz
	 *
	 */
	private class Peer {
		public String ip;
		public String port;
		public LocalDateTime lastHeardFrom;
		public boolean active = true;
		
		Peer(String peer) {
			String[] p = peer.split(":");
			this.ip = p[0];
			this.port = p[1];
			lastHeardFrom = LocalDateTime.now();
		}
		
		Peer(String ip, String port) {
			this.ip = ip;
			this.port = port;
			lastHeardFrom = LocalDateTime.now();
		}
		
		/**
		 * Updates the last time this process received a message from this peer.
		 */
		public void heardFrom() {
			lastHeardFrom = LocalDateTime.now();
		}
		
		public void setActive(boolean status) {
			active = status;
		}
		
		public boolean getActive() {
			return active;
		}
		
		@Override
		public String toString() {
			return ip + ":" + port + " " + lastHeardFrom.toString();
		}
	}
	
	/**
	 * A single source providing at least one peer in the system.
	 * The source location is stored as a peer and a date is recorded for when
	 * the source was received.  A formatted copy of the date as a string 
	 * is also recorded.
	 * @author joshuaplosz
	 *
	 */
	private class Source {
		public Peer srcLoc;
		public LinkedList<Peer> peersFromSrc;
		public LocalDateTime dateTime;
		public String dateTimeStr;
		
		Source(String ip, String port) {
			srcLoc = new Peer(ip, port);
			peersFromSrc = null;
			dateTime = null;
		}
		
		Source(String ip, String port, LinkedList<Peer> peers) {
			srcLoc = new Peer(ip, port);
			peersFromSrc = peers;
			
			dateTime = LocalDateTime.now();
			DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			dateTimeStr = dateTime.format(format);
			
			for (Peer peer : peers) {
				addPeer(peer);
			}
		}
	}
	
	/**
	 * A sinlge snippet transmitted through the system.
	 * Senders timestamp is recorded as well at the snippet content and 
	 * source of the snippet.
	 * @author joshuaplosz
	 *
	 */
	private class Snippet {
		public int timestamp;
		public String content;
		public Source src;
		
		Snippet(int ts, String c, Source src) {
			timestamp = ts;
			content = c;
			this.src = src;
		}
	}
	
	/**
	 * Adds a peer to currentPeers if it doesn't already exist.
	 * @param peer
	 * @return true if the provided peer already existed in currentPeers
	 */
	private boolean addPeer(Peer peer) {
		synchronized(currentPeers) {
			if (!currentPeers.isEmpty()) {
				Peer p = findCurrentPeer(peer);
				if (p == null) {
					currentPeers.add(peer);
					MyUtil.log("Adding peer: " + peer, 3);
					return false;
				} else {
					return false;
				}
			} else {
				currentPeers.add(peer);
				MyUtil.log("Adding peer: " + peer, 3);
				return true;
			}
		}
	}
	
	/**
	 * Adds to listSources a source that provided a list of peers.
	 * @param ip - IP address of source
	 * @param port - Port number of source
	 * @param msg - number of peers provided followed by the list of peers
	 */
	public void addListSource(String ip, String port, Queue<String> msg) {
		int numOfPeers = Integer.parseInt(msg.remove());
		LinkedList<Peer> peers = new LinkedList<Peer>();
		while (!msg.isEmpty()) {
			Peer p = new Peer(msg.remove());
			peers.add(p);
		}
		listSources.add(new Source(ip, port, peers));
	}
	
	/**
	 * Creates a list of current peers in the system via colon
	 * separated ip address and port number.
	 * @return list of peers as a string
	 */
	public LinkedList<String> getCurrentPeers() {
		LinkedList<String> cp = new LinkedList<String>();
		synchronized(currentPeers) {
			for (Peer p : currentPeers) {
				String aliveness = (p.getActive()) ? "alive" : "silent";
				cp.add(p.ip + ":" + p.port + " " + aliveness + "\n");
			}
			return cp;
		}
	}
	
	/**
	 * Creates a list of current peers in the system via colon
	 * separated ip address and port number.
	 * @return list of peers as a string
	 */
	public LinkedList<String> getCurrentActivePeers() {
		LinkedList<String> cp = new LinkedList<String>();
		synchronized(currentPeers) {
			for (Peer p : currentPeers) {
				if (p != null && p.active) 
					cp.add(p.ip + ":" + p.port + "\n");
			}
			return cp;
		}
	}
	
	/**
	 * Creates a list of sources that provided a list of peers.
	 * Each source in the list contains the following in the form of a string
	 * 		- <source IP address>":"<source port number>"\n"
	 * 		- <source date>"\n"
	 * 		- <number of peers provided from source>"\n"
	 * 		- <list of peers provided from source> <-- each peer is newline terminated
	 * @return list of string formated data from sources that provided a list of peers
	 */
	public LinkedList<String> getListSources() {
		LinkedList<String> ls = new LinkedList<String>();
		synchronized(listSources) {
			for (Source s : listSources) {
				String msg = s.srcLoc.ip + ":" + s.srcLoc.port + "\n" + s.dateTimeStr + "\n" + s.peersFromSrc.size() + "\n";
				for (Peer p : s.peersFromSrc) {
					msg += p.ip + ":" + p.port + "\n";
				}
				ls.add(msg);
			}
			return ls;
		}
	}
	
	/**
	 * Creates a list of sources that provided a single peer.
	 * Each source in the list contains a colon separated source
	 * IP address and port number, followed by a colon separated 
	 * peer IP address and port number, followed by the sources date.
	 * @return list of string formatted data from sources that provided a single peer
	 */
	public LinkedList<String> getSingleSources() {
		LinkedList<String> ss = new LinkedList<String>();
		synchronized(singleSources) {
			for (Source s : singleSources) {
				String msg = s.srcLoc.ip + ":" + s.srcLoc.port;
				for (Peer p : s.peersFromSrc) {
					msg += " " + p.ip +  ":" + p.port + " " + s.dateTimeStr + "\n";
				}
				ss.add(msg);
			}
			return ss;
		}
	}
	
	/**
	 * Creates a list of snippets found in the system.
	 * Each snippet contains the snippets timestamp, content, 
	 * and colon separated source IP address and port number.
	 * @return
	 */
	public LinkedList<String> getSnippets() {
		LinkedList<String> sn = new LinkedList<String>();
		synchronized(snippets) {
			for (Snippet s : snippets) {
				sn.add(s.timestamp + " " + s.content + " " + s.src.srcLoc.ip + ":" + s.src.srcLoc.port + "\n");
			}
			return sn;
		}
	}
	
	String snippet;
	
	/**
	 * TODO
	 * @param timestamp
	 * @return
	 */
	public String getSnippet(int timestamp) {
		snippet = null;
		synchronized (snippets) {
			snippets.forEach( snip -> {
				if (snip.timestamp == timestamp) {
					snippet = snip.src.srcLoc.ip + ":" + snip.src.srcLoc.port + " " + snip.content;
				}
			});
		}
		return snippet;
	}
	
	/**
	 * Add a source that provided a single peer to singleSources.
	 * @param peer - peer provided from source as a string
	 * @param ip - IP address of source as a string
	 * @param port - port number of source as a string
	 * @return true if the source peer already exists in currentPeers, false otherwise
	 */
	public boolean addSingleSource(String peer, String ip, String port) {
		Peer p = new Peer(peer);
		addPeer(p);

		LinkedList<Peer> peers = new LinkedList<Peer>();
		peers.add(p);
		
		for (Source src : singleSources) {
			if (src.srcLoc.ip.equals(ip) && src.srcLoc.port.equals(port)) {
				return true;
			}
		}
		
		singleSources.add(new Source(ip, port, peers));
		return false;
	}
	
	/**
	 * Adds a snippet to snippets.
	 * @param ts - snippet's timestamp as a string
	 * @param content - snippet's content as a string
	 * @param ip - IP address of source as a string
	 * @param port - port number of source as a string
	 */
	public void storeSnippet(String ts, String content, String ip, String port) {
		int sTimestamp = Integer.parseInt(ts);
		
		// ignore any duplicate snippets from the same source
		snippets.forEach(snip -> {
			if (snip.src.srcLoc.ip == ip && snip.src.srcLoc.port == port && snip.timestamp == sTimestamp) {
				return;
			}
		});
		
		Source src = new Source(ip, port);
		snippets.add(new Snippet(sTimestamp, content, src));
	}
	
	private static Random r = new Random(); // random number generator
	
	/**
	 * Use a random number to select the index in 
	 * which to return a peer from currentPeers.
	 * Only return active peers.
	 * @return a peer's colon separated IP address and port number from currentPeers as a string
	 */
	public String getRandomPeer() {
		synchronized(currentPeers) {
			if (!currentPeers.isEmpty()) {
				Peer peer;
				do {
					int index = r.nextInt(currentPeers.size());
					peer = currentPeers.get(index);
				} while (peer == null || !peer.active);
				return peer.ip + ":" + peer.port;
			} else {
				return null;
			}
		}
	}
	
	/**
	 * Compares the date in which a peer message was last received from all peers and sets the 
	 * status for all peers to active if less than 3 minutes has passed or inactive otherwise.
	 * @param elapsedTime - interval in milliseconds in which to remove peers as a long
	 */
	public void refreshPeers() {
		synchronized(currentPeers) {
			if (!currentPeers.isEmpty()) {
				LocalDateTime now = LocalDateTime.now();
				for (Peer p : currentPeers) {
					if (p == null) continue;
					if (Duration.between(p.lastHeardFrom, now).getSeconds() >= (3 * 60 * 1000)) {
						p.setActive(false);
					} else {
						p.setActive(true);
					}
				}			
			}
		}
	}
	
	/**
	 * Removes a peer from currentPeers
	 * @param p - peer to be removed
	 */
	public void removePeer(Peer p) {
		MyUtil.log("Removing peer " + p);
		synchronized(currentPeers) {
			for (Peer q : currentPeers) {
				//MyUtil.log("Does peer " + q.ip + ":" + q.port + " equal " + p.ip + ":" + p.port, 1);
				if (q.ip.equals(p.ip) && q.port.equals(p.port)) {
					currentPeers.removeElement(q);
					break;
				}
			}
		}
	}
	
	/**
	 * Removes a peer from currentPeers
	 * @param ip - IP address of peer to be removed as a string
	 * @param port - port number of peer to be removed as a string
	 */
	public void removePeer(String ip, String port) {
		Peer p = new Peer(ip, port);
		removePeer(p);
	}
	
	/**
	 * Searches currentPeers for the peer provided.
	 * @param p - peer to search for
	 * @return peer if found in currentPeers; null otherwise
	 */
	private Peer findCurrentPeer(Peer p) {
		synchronized(currentPeers) {
			if (!currentPeers.isEmpty()) {
				for (Peer q : currentPeers) {
					if (q == null) continue;
					//MyUtil.log("Peer in currentPeers: " + q.ip + ":" + q.port, 2);
					//MyUtil.log("Peer to compare: " + p.ip + ":" + p.port, 2);
					//MyUtil.log("Does peer " + q.ip + ":" + q.port + " equal " + p.ip + ":" + p.port, 1);
					if (q.ip.equals(p.ip) && q.port.equals(p.port)) {
						return q;
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Updates the date heard from a peer if the peer exists in currentPeers
	 * @param p - peer to update
	 * @return true if provided peer exists in list of current peers, false otherwise
	 */
	public boolean updatePeer (Peer p) {
		MyUtil.log("Updating peer " + p.toString());
		Peer pExists = findCurrentPeer(p);
		if (pExists != null) {
			if (pExists.active) {
				pExists.heardFrom();
			} else {
				pExists.setActive(true);
				pExists.heardFrom();
			}
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Updates the date heard from a peer if the peer exists in currentPeers
	 * @param ip - IP address of peer to update as a string
	 * @param port - port number of peer to update as a string
	 * @return true if provided peer exists in list of current peers, false otherwise
	 */
	public boolean updatePeer (String ip, String port) {
		Peer p = new Peer(ip, port);
		return updatePeer(p);
	}
	
	/**
	 * Updates the date heard from a peer if the peer exists in currentPeers
	 * @param peer - peer to update as a <ip>":"<port> string
	 * @return true if provided peer exists in list of current peers, false otherwise
	 */
	public boolean updatePeer (String peer) {
		String[] comp = peer.split(":");
		Peer p = new Peer(comp[0], comp[1]);
		return updatePeer(p);
	}

	/**
	 * Checks the status of the provided peer and sets it to active if it were previously inactive.
	 * @param ip
	 * @param port
	 * @return true if the peer was previously active, false otherwise
	 */
	public boolean peerActive(String ip, String port) {
		Peer peer = new Peer(ip, port);
		
		if (findCurrentPeer(peer) != null) {
			if (peer.active) {
				return true;
			}
			
			peer.setActive(true);
			return false;
		} else {
			addPeer(peer);
			return true;
		}
	}
	
	/**
	 * TODO
	 * @param peer
	 */
	public void setPeerInactive(String peer) {
		Peer p = new Peer(peer);
		p = findCurrentPeer(p);
		
		p.setActive(false);
	}
}
