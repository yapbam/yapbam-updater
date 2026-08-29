package net.yapbam.updater;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/** A simple file-based logger.
 * <br>It is {@link AutoCloseable}: the constructor opens the file, {@link #close()} closes it.
 * <br>This file is useful to diagnose update failures that would otherwise be silent
 * (the updater must never write to stdout/stderr, see README).
 */
class Log implements AutoCloseable {
	private final PrintStream ps;

	/** Creates a new log, opening the given file.
	 * @param file The file to write log messages to.
	 * @throws IOException If the file cannot be opened.
	 */
	Log(File file) throws IOException {
		this.ps = new PrintStream(file, "UTF-8"); //$NON-NLS-1$
	}

	/** Logs a message.
	 * @param message The message to log.
	 */
	void log(String message) {
		ps.println(message);
	}

	/** Logs a message followed by an exception's stack trace.
	 * @param message The message to log.
	 * @param e The exception whose stack trace should be logged.
	 */
	void log(String message, Throwable e) {
		ps.println(message);
		e.printStackTrace(ps);
	}

	@Override
	public void close() {
		ps.close();
	}
}
