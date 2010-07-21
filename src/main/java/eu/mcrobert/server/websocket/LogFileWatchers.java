package eu.mcrobert.server.websocket;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import org.jboss.netty.channel.ChannelHandlerContext;

public class LogFileWatchers {
	private static Timer timer = new Timer();
	private static Map<String, FileWatcher> watchers = new HashMap<String, FileWatcher>();

	public static void addWatcher(String filename) {
		if (!watchers.containsKey(filename)) {
			FileWatcher task = new LogWatcher(new File(filename));
			watchers.put(filename, task);
			// repeat the check every second
			timer.schedule(task, new Date(), 1000);
		}
	}

	public static void addListener(String filename, ChannelHandlerContext ctx) {
		if (watchers.containsKey(filename)) {
			watchers.get(filename).addListener(ctx);
		}
	}

	public static boolean hasListener(String filename, ChannelHandlerContext ctx) {
		if (watchers.containsKey(filename)) {
			return watchers.get(filename).hasListener(ctx);
		}
		return false;
	}

	public static void removeListener(String filename, ChannelHandlerContext ctx) {
		if (watchers.containsKey(filename)) {
			watchers.get(filename).removeListener(ctx);
		}
	}

}