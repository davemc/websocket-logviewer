package eu.mcrobert.server.websocket;

import java.io.File;
import java.util.TimerTask;

public abstract class FileWatcher extends TimerTask {
	private long timeStamp;
	protected long pos;
	private File file;

	public FileWatcher(File file) {
		this.file = file;
		this.pos = file.length();
		this.timeStamp = file.lastModified();
	}

	public final void run() {
		long timeStamp = file.lastModified();

		if (this.timeStamp != timeStamp) {
			this.timeStamp = timeStamp;
			onChange(file);
		}
	}

	protected abstract void onChange(File file);

	public abstract void addListener(Object object);

	public abstract boolean hasListener(Object object);

	public abstract void removeListener(Object object);

}