package eu.mcrobert.server.websocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * An HTTP server which serves Web Socket requests at:
 * 
 * http://localhost:9000/websocket
 * 
 */
public class WebSocketServer {
	public static final String LOGFILES_PROPERTIES = "logfiles.properties";
	public static Properties properties;
	public static String SERVER_ORIGIN = "";

	public static void main(String[] args) {
		properties = new Properties();
		String path = WebSocketServer.class.getProtectionDomain()
				.getCodeSource().getLocation().toString();
		if (path.charAt(8) == ':') {
			// windows file:/D:/
			path = path.substring(6);
		} else {
			// *nix
			path = path.substring(5);
		}
		if (path.endsWith(".jar")) {
			path = path.substring(0, path.lastIndexOf("/"));
		}
		System.out.println("Path: " + path);
		File file = new java.io.File(path + "/" + LOGFILES_PROPERTIES);
		System.out.println("File: " + file.getAbsolutePath());
		InputStream is = null;
		try {
			if (file.exists()) {
				is = new java.io.FileInputStream(file);
				System.out.println("Using Properties file: "
						+ file.getAbsolutePath());
			} else {
				is = ClassLoader.getSystemResourceAsStream(LOGFILES_PROPERTIES);
				if (null != is) {
					System.out.println("Using Resource Properties file: "
							+ ClassLoader
									.getSystemResource(LOGFILES_PROPERTIES));
				}
			}
			if (null != is) {
				properties.load(is);
			}
		} catch (IOException e1) {
			// ignore
			e1.printStackTrace();
		} finally {
			if (null != is) {
				try {
					is.close();
				} catch (Exception ee) {
				}
			}
		}
		int port = 9000;
		String portStr = null;
		if (properties.containsKey("PORT")) {
			portStr = properties.getProperty("PORT");
			System.out.println("WebSocketServer.main() prop = " + portStr);
		}
		if (args.length > 0) {
			portStr = args[0];
			properties.put("PORT", portStr);
			System.out.println("WebSocketServer.main() args = " + portStr);
		}
		if (null != portStr) {
			try {
				port = Integer.parseInt(portStr);
			} catch (NumberFormatException e) {
				System.out.println(e.getMessage());
				System.out
						.println("Usage: java -jar websocket.jar <PORT> <LOGFILE>");
				return;
			}
		}
		String serverStr = "127.0.0.1";
		if (properties.containsKey("HOSTNAME")) {
			serverStr = properties.getProperty("HOSTNAME");
			System.out.println("WebSocketServer.main() server prop = "
					+ serverStr);
		}
		// Configure the server.
		ServerBootstrap bootstrap = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool()));

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(new WebSocketServerPipelineFactory());

		// Bind and start to accept incoming connections.
		bootstrap.bind(new InetSocketAddress(port));

		System.out.println("WebSocket Server Started");
		SERVER_ORIGIN = "http://" + serverStr + ":" + port;
		System.out.println("WebSocket Accessible on " + getServerOrigin()
				+ WebSocketServerHandler.WEBSOCKET_PATH);
	}

	public static String getServerOrigin() {
		return SERVER_ORIGIN;
	}
}
