package org.jboss.tools.vscode.java;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.jboss.tools.vscode.ipc.JsonRpcConnection;
import org.jboss.tools.vscode.ipc.MessageType;

/**
 * The LogHandler hooks in the Eclipse log and forwards all Eclipse log messages to
 * the the client. In VSCode you can see all the messages in the Output view, in the
 * 'Java' channel.
 */
public class LogHandler {
	
	// if set, the all log entries will be written to USER_DIR/langserver.log as well.
	public static final boolean LOG_TO_FILE = true;
	
	private ILogListener logListener;
	private FileWriter logWriter;
	private DateFormat dateFormat;

	public void install(JsonRpcConnection connection) {
	    dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
		
		this.logListener = new ILogListener() {
			@Override
			public void logging(IStatus status, String bundleId) {
				Date date = Calendar.getInstance().getTime();
				String message = dateFormat.format(date) + ' ' + status.getMessage();
				if (status.getException() != null) {
					message = message + '\n' + status.getException().getMessage();
					StringWriter sw = new StringWriter();
					status.getException().printStackTrace(new PrintWriter(sw));
					String exceptionAsString = sw.toString();
					message = message + '\n' + exceptionAsString;
				}

				connection.logMessage(getMessageTypeFromSeverity(status.getSeverity()), message);
				if (LOG_TO_FILE) {
					logToFile("[" + getLabelFromSeverity(status.getSeverity()) + "] " + message);
				}

			}
		};
		Platform.addLogListener(this.logListener);
	}

	public void uninstall() {
		Platform.removeLogListener(logListener);
	}
	
	private MessageType getMessageTypeFromSeverity(int severity) {
		switch (severity) {
		case IStatus.ERROR:
			return MessageType.Error;
		case IStatus.WARNING:
			return MessageType.Warning;
		case IStatus.INFO:
			return MessageType.Info;
		default:
			return MessageType.Log;
		}
	}

	private String getLabelFromSeverity(int severity) {
		switch (severity) {
		case IStatus.ERROR:
			return "error";
		case IStatus.WARNING:
			return "warning";
		case IStatus.INFO:
			return "info";
		case IStatus.OK:
			return "ok";
		default:
			return "cancel";
		}
	}

	private void logToFile(String log) {
		try {
			if (logWriter == null) {
				Path cwd = Paths.get(System.getProperty("user.home"));
				Path logPath = cwd.toAbsolutePath().normalize().resolve("langserver.log");
				logWriter = new FileWriter(logPath.toFile());
			}
			logWriter.write(log + "\n");
			logWriter.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
