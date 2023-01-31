import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Contains a thread for TCP connection with registry.
 * Interface for CommManager to communicate to registry.
 * @author joshuaplosz
 *
 */
public class TCPConnection {
	
	private String ip;
	private String port;
	private String localIp;
	private String localPort;
	private CommManager cm;
	
	private Socket registry;
	private BufferedReader tcp_in;
	private BufferedWriter tcp_out;
	
	public boolean connectionOpen;

	TCPConnection(String ip, String port, CommManager cm) {
		this.ip = ip;
		this.port = port;
		this.cm = cm;
		connectionOpen = true;
	
		try {
			registry = new Socket(InetAddress.getByName(ip), Integer.parseInt(port));
			localIp = registry.getLocalAddress().getHostAddress();
			localPort = String.valueOf(registry.getLocalPort());
			
			tcp_in 	= new BufferedReader(new InputStreamReader(registry.getInputStream()));
			tcp_out	= new BufferedWriter(new OutputStreamWriter(registry.getOutputStream()));
			
			MyUtil.log("TCP connection with registry established.");
			MyUtil.log("My TCP port " + getLocalTcpPort());
			
		} catch (NumberFormatException | IOException e) {
			MyUtil.log("Unable to establish TCP connection with registry with ip: " + ip + " and port: " + port, 2);
			e.printStackTrace();
		}
	}
	
	public String getLocalIp() {
		return localIp;
	}
	
	public String getLocalTcpPort() {
		return localPort;
	}
	
	/**
	 * Thread for listening for requests from registry.
	 * @author joshuaplosz
	 *
	 */
	private class RegistryListener implements Runnable {
		
		@Override
		public void run() {
			while (connectionOpen) {
				String line = readLineFromRegistry();
				cm.parse(line, ip, port);
			}
		}
	}
	
	/**
	 * Method used to start a new RegistryListener thread.
	 */
	public void listen() {
		Thread rl = new Thread(new RegistryListener());
		rl.start();
	}

	/**
	 * Used what parsing a registry request requires additional information.
	 * @return next line read from registry as a string
	 */
	public String readLineFromRegistry() {
		String line = "";
		try {
			line = tcp_in.readLine();
			MyUtil.log(line, 2);
		} catch (IOException e) {
			MyUtil.log("Unable to ready line from registry.", 2);
			e.printStackTrace();
		}
		return line;
	}

	/**
	 * Sends a message to registry over TCP connection.
	 * @param msg - message to be sent to registry as a string
	 */
	public void toRegistry(String msg) {
		if (msg != null) {
			try {
				tcp_out.write(msg);
				tcp_out.flush();
			} catch (IOException e) {
				MyUtil.log("Error when sending tcp message to registry.", 2);
				e.printStackTrace();
			}		
		}
	}
	
	/**
	 * Closes TCP buffers and socket. Sets the connection flag to closed.
	 */
	public void close() {
		try {
			tcp_in.close();
			tcp_out.close();
			registry.close();
			connectionOpen = false;
		} catch (IOException e) {
			MyUtil.log("Error when closing tcp streams/socket to registry.", 2);
			e.printStackTrace();
		}
	}
	
	public String getIp() {
		return registry.getInetAddress().getHostAddress();
	}
	
	public String getPort() {
		return String.valueOf(registry.getPort());
	}
}
