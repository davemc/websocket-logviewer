package eu.mcrobert.server.websocket;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_LOCATION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_ORIGIN;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_PROTOCOL;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;

public class WebSocketServerHandler extends SimpleChannelUpstreamHandler {

	public static final String WEBSOCKET_PATH = "/websocket";
	private List<String> allowedLogFiles = null;

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		Object msg = e.getMessage();
		if (msg instanceof HttpRequest) {
			handleHttpRequest(ctx, e);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		super.channelConnected(ctx, e);
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, MessageEvent e) {
		// Allow only GET methods.
		HttpRequest req = (HttpRequest) e.getMessage();
		if (req.getMethod() != GET) {
			System.out.println("WebSocketServerHandler.handleHttpRequest() "
					+ req.getMethod() + " not allowed");
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					FORBIDDEN));
			return;
		}

		// Serve the WebSocket handshake request.
		if (req.getUri().equals(WEBSOCKET_PATH)
				&& Values.UPGRADE.equalsIgnoreCase(req.getHeader(CONNECTION))
				&& WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {
			if (!req.getHeader(ORIGIN)
					.equals(WebSocketServer.getServerOrigin())) {
				System.out.println("WebSocket Upgrade: illegal origin: "
						+ req.getHeader(ORIGIN));
				sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
						FORBIDDEN));
				return;
			}
			System.out.println("WebSocketServerHandler.handleHttpRequest() "
					+ req.getUri() + " upgrade");
			// Create the WebSocket handshake response.
			HttpResponse res = new DefaultHttpResponse(
					HTTP_1_1,
					new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
			res.addHeader(Names.UPGRADE, WEBSOCKET);
			res.addHeader(CONNECTION, Values.UPGRADE);
			System.out
					.println("Upgrading for Origin: " + req.getHeader(ORIGIN));
			res.addHeader(WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
			res.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(req));
			String protocol = req.getHeader(WEBSOCKET_PROTOCOL);
			if (protocol != null) {
				res.addHeader(WEBSOCKET_PROTOCOL, protocol);
			}

			// Upgrade the connection and send the handshake response.
			ChannelPipeline p = ctx.getChannel().getPipeline();
			p.remove("aggregator");
			p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());

			ctx.getChannel().write(res);
			// this.ctx = ctx;

			p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
			return;
		} else {
			try {
				sendFile(ctx, e);
				return;
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		// Send an error page otherwise.
		System.out.println("WebSocketServerHandler.handleHttpRequest() "
				+ req.getUri() + " not allowed");
		sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
	}

	/*
	 * This is the meat of the server event processing Here we square the number
	 * and return it
	 */
	private void handleWebSocketFrame(ChannelHandlerContext ctx,
			WebSocketFrame frame) {
		// System.out.println(frame.getTextData());
		String filename = frame.getTextData();
		String result = "WS: ";
		if (getAllowedLogFiles().contains(filename)) {
			File file = new File(filename);
			System.out.println("handleWebSocketFrame for " + filename);
			if (!file.exists()) {
				result = result + filename + " does not exist.";
			} else {
				if (LogFileWatchers.hasListener(filename, ctx)) {
					LogFileWatchers.removeListener(filename, ctx);
					result = result + "Removed listener for " + filename;
				} else {
					LogFileWatchers.addWatcher(filename);
					LogFileWatchers.addListener(filename, ctx);
					result = result + "Added listener for " + filename;
				}
			}
		} else {
			result = result + "Not allowed: " + filename;
		}
		ctx.getChannel().write(new DefaultWebSocketFrame(result));
	}

	private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req,
			HttpResponse res) {
		// Generate an error page if response status code is not OK (200).
		if (res.getStatus().getCode() != 200) {
			res.setContent(ChannelBuffers.copiedBuffer(res.getStatus()
					.toString(), CharsetUtil.UTF_8));
			setContentLength(res, res.getContent().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.getChannel().write(res);
		if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		e.getCause().printStackTrace();
		e.getChannel().close();
	}

	private String getWebSocketLocation(HttpRequest req) {
		return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + WEBSOCKET_PATH;
	}

	private void sendFile(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		HttpRequest req = (HttpRequest) e.getMessage();
		if (req.getMethod() != GET) {
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					METHOD_NOT_ALLOWED));
			return;
		}

		final String path = sanitizeUri(req.getUri());
		if (null == path) {
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					FORBIDDEN));
			return;
		}
		URL url = this.getClass().getResource(path);
		if (null == url) {
			System.out
					.println("WebSocketServerHandler.sendFile() URL not found = "
							+ path);
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					HttpResponseStatus.NOT_FOUND));
			return;
		}
		System.out.println("WebSocketServerHandler.sendFile() URL = " + url.getFile());
		InputStream is = this.getClass().getResourceAsStream(path);
		if (is == null) {
			System.out
					.println("WebSocketServerHandler.sendFile() input stream is null for url "
							+ url.getFile());
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					HttpResponseStatus.NOT_FOUND));
			return;
		}
		File tmp = null;
		if (path.endsWith(".html")) {
			// we want to do a replace
			String contents = convertStreamToString(is);
			contents = contents.replaceAll("HOSTNAME",
					WebSocketServer.properties.getProperty("HOSTNAME"));
			contents = contents.replaceAll("PORT",
					WebSocketServer.properties.getProperty("PORT"));
			List<String> files = getAllowedLogFiles();
			String fileHtml = "";
			if (files.size() > 0) {
				fileHtml = "<select id=\"filename\" name=\"filename\">";
				for (String file : files) {
					fileHtml = fileHtml + "<option value=\"" + file + "\">"
							+ file + "</option>";
				}
				fileHtml = fileHtml + "</select>";
			} else {
				fileHtml = "<input id=\"filename\" value=\"/tmp/text.txt\" />";
			}
			contents = contents.replaceAll("FILES", fileHtml);

			tmp = File.createTempFile("websocket", ".tmp");
			OutputStream out = new FileOutputStream(tmp);
			out.write(contents.getBytes("UTF-8"));
			out.close();
		} else {
			tmp = File.createTempFile("websocket", ".tmp");
			OutputStream out = new FileOutputStream(tmp);
			byte buf[] = new byte[1024];
			int len;
			while ((len = is.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.close();
			is.close();
		}
		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(tmp, "r");
		} catch (Exception fnfe) {
			fnfe.printStackTrace();
			System.out.println("RandomAccessFile is null: " + url.getFile());
			sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1,
					HttpResponseStatus.NOT_FOUND));
			return;
		}

		long fileLength = raf.length();
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1,
				HttpResponseStatus.OK);
		setContentLength(response, fileLength);

		Channel ch = e.getChannel();

		// Write the initial line and the header.
		ch.write(response);

		// Write the content.
		ChannelFuture writeFuture;
		if (ch.getPipeline().get(SslHandler.class) != null) {
			// Cannot use zero-copy with HTTPS.
			writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
		} else {
			// No encryption - use zero-copy.
			final FileRegion region = new DefaultFileRegion(raf.getChannel(),
					0, fileLength);
			writeFuture = ch.write(region);
			writeFuture.addListener(new ChannelFutureProgressListener() {
				public void operationComplete(ChannelFuture future) {
					region.releaseExternalResources();
				}

				public void operationProgressed(ChannelFuture future,
						long amount, long current, long total) {
					System.out.printf("%s: %d / %d (+%d)%n", path, current,
							total, amount);
				}
			});
		}
		// Decide whether to close the connection or not.
		if (!isKeepAlive(req)) {
			// Close the connection when the whole content is written out.
			writeFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private List<String> getAllowedLogFiles() {
		if (null == allowedLogFiles) {
			allowedLogFiles = new ArrayList<String>();
			Properties props = WebSocketServer.properties;
			for (Entry<Object, Object> entry : props.entrySet()) {
				String key = (String) entry.getKey();
				if (key.startsWith("filename")) {
					allowedLogFiles.add((String) entry.getValue());
				}
			}
		}
		return allowedLogFiles;
	}

	private String sanitizeUri(String uri) {
		// System.out.println("WebSocketServerHandler.sanitizeUri() " + uri);
		String result = null;
		if (null == uri) {
			//
		} else {
			if (("".equals(uri)) || ("/".equals(uri))) {
				result = "/client.html";
			} else if ("/jquery.js".equals(uri)) {
				result = uri;
			} else if ("/favicon.ico".equals(uri)) {
				result = uri;
			} else if (uri.startsWith("/flexigrid/")) {
				result = uri;
			}
		}
		System.out.println("sanitizeUri() result = " + result);
		return result;
	}

	// Returns the contents of the file in a byte array.
	public static byte[] getBytesFromInputStream(File file, InputStream is)
			throws IOException {
		// InputStream is = new FileInputStream(file);
		long length = file.length();
		if (length > Integer.MAX_VALUE) {
			throw new RuntimeException("File is too large: "
					+ file.getAbsolutePath());
		}
		byte[] bytes = new byte[(int) length];
		int offset = 0;
		int numRead = 0;
		while (offset < bytes.length
				&& (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
			offset += numRead;
		}
		if (offset < bytes.length) {
			throw new IOException("Could not completely read file "
					+ file.getName());
		}
		is.close();
		return bytes;
	}

	public String convertStreamToString(InputStream is) throws IOException {
		if (is != null) {
			StringBuilder sb = new StringBuilder();
			String line;

			try {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(is, "UTF-8"));
				while ((line = reader.readLine()) != null) {
					sb.append(line).append("\n");
				}
			} finally {
				is.close();
			}
			return sb.toString();
		} else {
			return "";
		}
	}
}
