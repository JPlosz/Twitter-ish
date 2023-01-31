import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.net.InetAddress;
import java.net.MalformedURLException;

/**
 * Contains two threaded classes for UDP communication.
 * One that listens for incoming messages from peers (PeerListener) and
 * one that sends outgoing messages to a peer (PeerReplier).
 * Provides an interface for CommManager to use threaded 
 * communication channels.
 * @author joshuaplosz
 *
 */
public class UDPServer {
	
	private int port;
	private DatagramSocket socket;
	private byte[] buff = new byte[1028];
	
	private String peerIp;
	private String peerPort;
	
	private CommManager cm;
	
	public boolean connectionOpen = true;
	
	UDPServer(CommManager cm) {
		this.cm = cm;

		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(10 * 1000);
			MyUtil.log("UDP server established.");
			MyUtil.log("My UDP port " + getLocalUdpPort());
		} catch (SocketException e) {
			MyUtil.log("Unable to establish UDP socket", 2);
		}
	}
	
	public String getVisibleIp() {
		try {
			URL checkMyIp = new URL("http://checkip.amazonaws.com");
			BufferedReader sc = new BufferedReader(new InputStreamReader(checkMyIp.openStream()));
			String externalIp = sc.readLine();
			sc.close();
			MyUtil.log("UDP address for remote registry: " + externalIp, 2);
			return externalIp;
			
		} catch (MalformedURLException e) {
			MyUtil.log("Bad URL when checking external IP using https://api.my-ip.io/ip", 2);
		} catch (IOException e) {
			MyUtil.log("Error establishing input stream when checking external IP using https://api.my-ip/io/ip", 2);
			e.printStackTrace();
		}
		return "";

	}
	
	public String getLocalUdpPort() {
		return String.valueOf(socket.getLocalPort());
	}
	
	/**
	 * Thread that waits for a DatagramPacket to arrive, and 
	 * sends the packet as a string to the CommManager
	 * to be parsed.
	 * @author joshuaplosz
	 *
	 */
	private class PeerReceiver implements Runnable {

		@Override
		public void run() {
			while (connectionOpen) {
				DatagramPacket packet = new DatagramPacket(buff, buff.length);
				try {
					socket.receive(packet);
					peerIp = packet.getAddress().toString().substring(1);
					peerPort = String.valueOf(packet.getPort());
					
					String msg = new String(packet.getData(), 0, packet.getLength());
					MyUtil.log("udp message received");
					cm.parse(msg, peerIp, peerPort);
					
				} catch (SocketTimeoutException e) {
					// ignore
					listen();
				} catch (IOException e) {
					MyUtil.log("Unable to receive packet from UDP socket.", 2);					
				}
			}
		}
	}
	
	/**
	 * Method used to start a new PeerListener thread
	 */
	public void listen() {
		Thread t = new Thread(new PeerReceiver(), "UDP Server Thread");
		t.start();
	}
	
	/**
	 * Thread that sends a message to a peer via DatagramPacket if the 
	 * DatagramSocket is already connected and has not been closed.
	 * @author joshuaplosz
	 *
	 */
	private class PeerSender implements Runnable {
		private byte[] out_msg = new byte[1024];
		private int out_port;
		private InetAddress out_ip;
		private String out_ip_string;
		
		PeerSender (String msg, String ip, String port) {
			out_ip_string = ip;
			out_msg = msg.getBytes();
			out_port = Integer.parseInt(port);
			try {
				out_ip = InetAddress.getByName(ip);
			} catch (UnknownHostException e) {
				MyUtil.log("Unable to find host at IP: " + ip, 2);
			}
		}
		
		@Override
		public void run() {
			DatagramPacket packet = new DatagramPacket(out_msg, out_msg.length, out_ip, out_port);
			try {
				MyUtil.log("sending packet to " + out_ip + ":" + out_port + " with msg: " + new String(out_msg, "UTF-8"));
				socket.send(packet);
			} catch (IOException e) {
				MyUtil.log("Peer " + out_ip.toString() + ":" + out_port + " no longer available", 2);
				cm.drop(out_ip_string, String.valueOf(out_port));
			}
		}
	}
	
	/**
	 * Method used to start a new PeerReplier thread
	 * @param msg - message to be send as a string
	 * @param ip - IP address of destination peer as a string
	 * @param port - port number of destination peer as a string
	 */
	public void toPeer(String msg, String ip, String port) {
		Thread t = new Thread(new PeerSender(msg, ip, port), "UDP Sending Thread");
		t.start();
	}
	
	/**
	 * Closes the DatagramSocket and sets the connection flag to closed.
	 */
	public void close() {
		socket.close();
		connectionOpen = false;
	}
}
