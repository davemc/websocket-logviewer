package eu.mcrobert.server.websocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;

public class LogWatcher extends FileWatcher {

	private List<ChannelHandlerContext> listeners = new ArrayList<ChannelHandlerContext>();

	public LogWatcher(File file) {
		super(file);
	}

	protected void onChange(File file) {
		String msg = "{\"msgs\":[";
		try {
			List<String> msgs = getLinesFromFile(file, pos);
			for (int i = 0; i < msgs.size(); i++) {
				String msgStr = msgs.get(i);
				msgStr = msgStr.replaceAll("\"", "");
				msg = msg + "{\"file\":\""
						+ file.getAbsolutePath().replaceAll("\\\\", "/")
						+ "\",\"msg\":\"" + msgStr + "\"},";
			}
			if (msg.charAt(msg.length() - 1) == ',') {
				msg = msg.substring(0, msg.length() - 1);
			}
			msg = msg + "]}";
		} catch (IOException e) {
			e.printStackTrace();
			msg = "{\"error\":\"File " + file.getName()
					+ " has changes! Error reading them: " + e.getMessage()
					+ "\"}";
		}
		for (ChannelHandlerContext listener : listeners) {
			listener.getChannel().write(new DefaultWebSocketFrame(msg));
		}
	}

	// Returns the contents of the file
	public List<String> getLinesFromFile(File file, long startPos)
			throws IOException {
		InputStream is = new FileInputStream(file);
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		in.skip(startPos);
		ArrayList<String> lines = new ArrayList<String>();
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			if (line.length() > 0) {
				lines.add(line);
			}
			line = in.readLine();
		}
		pos = file.length();
		return lines;
	}

	@Override
	public void addListener(Object object) {
		if (object instanceof ChannelHandlerContext) {
			listeners.add((ChannelHandlerContext) object);
		}
		// TODO is it ok to silently ignore non ChannelHandlerContext listeners
	}

	@Override
	public boolean hasListener(Object object) {
		if (object instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) object;
			return listeners.contains(ctx);
		}
		return false;
	}

	@Override
	public void removeListener(Object object) {
		if (object instanceof ChannelHandlerContext) {
			ChannelHandlerContext ctx = (ChannelHandlerContext) object;
			if (listeners.contains(ctx)) {
				listeners.remove(ctx);
			}
		}
	}

}