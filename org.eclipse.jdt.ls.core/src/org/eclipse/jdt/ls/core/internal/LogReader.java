/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Copied from org.eclipse.ui.internal.views.log.LogReader
 */
public class LogReader {
	private static final int SESSION_STATE = 10;
	private static final int ENTRY_STATE = 20;
	private static final int SUBENTRY_STATE = 30;
	private static final int MESSAGE_STATE = 40;
	private static final int STACK_STATE = 50;
	private static final int TEXT_STATE = 60;
	private static final int UNKNOWN_STATE = 70;

	public static List<LogEntry> parseLogFile(File file, String lastEntryDateString) {
		List<LogEntry> tmp = new ArrayList<>();
		List<LogEntry> entries = new ArrayList<>();
		Date lastEntryDate = null;
		try {
			lastEntryDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).parse(lastEntryDateString);
		} catch (ParseException e) {
		}

		if (!file.exists()) {
			return null;
		}

		ArrayList<LogEntry> parents = new ArrayList<>();
		LogEntry current = null;
		LogSession session = null;
		int writerState = UNKNOWN_STATE;
		StringWriter swriter = null;
		PrintWriter writer = null;
		int state = UNKNOWN_STATE;
		LogSession currentSession = null;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			for (;;) {
				String line0 = reader.readLine();
				if (line0 == null) {
					break;
				}
				String line = line0.trim();

				if (line.startsWith(LogSession.SESSION)) {
					state = SESSION_STATE;
				} else if (line.startsWith("!ENTRY")) { //$NON-NLS-1$
					state = ENTRY_STATE;
				} else if (line.startsWith("!SUBENTRY")) { //$NON-NLS-1$
					state = SUBENTRY_STATE;
				} else if (line.startsWith("!MESSAGE")) { //$NON-NLS-1$
					state = MESSAGE_STATE;
				} else if (line.startsWith("!STACK")) { //$NON-NLS-1$
					state = STACK_STATE;
				} else {
					state = TEXT_STATE;
				}

				if (state == TEXT_STATE) {
					if (writer != null) {
						if (swriter.getBuffer().length() > 0) {
							writer.println();
						}
						writer.print(line0);
					}
					continue;
				}

				if (writer != null) {
					setData(current, session, writerState, swriter);
					writerState = UNKNOWN_STATE;
					swriter = null;
					writer.close();
					writer = null;
				}

				switch (state) {
					case STACK_STATE:
						swriter = new StringWriter();
						writer = new PrintWriter(swriter, true);
						writerState = STACK_STATE;
						break;
					case SESSION_STATE:
						session = new LogSession();
						session.processLogLine(line);
						swriter = new StringWriter();
						writer = new PrintWriter(swriter, true);
						writerState = SESSION_STATE;
						currentSession = updateCurrentSession(currentSession, session);
						break;
					case ENTRY_STATE:
						if (currentSession == null) { // create fake session if there was no any
							currentSession = new LogSession();
						}
						try {
							LogEntry entry = new LogEntry();
							entry.setSession(currentSession);
							entry.processEntry(line);
							setNewParent(parents, entry, 0);
							current = entry;
							if (current.getDate().before(lastEntryDate)) {
								addEntry(current, tmp);
							}
						} catch (IllegalArgumentException pe) {
							//do nothing, just toss the entry
						}
						break;
					case SUBENTRY_STATE:
						if (parents.size() > 0) {
							try {
								LogEntry entry = new LogEntry();
								entry.setSession(session);
								int depth = entry.processSubEntry(line);
								setNewParent(parents, entry, depth);
								current = entry;
								LogEntry parent = parents.get(depth - 1);
								parent.addChild(entry);
							} catch (IllegalArgumentException pe) {
								//do nothing, just toss the bad entry
							}
						}
						break;
					case MESSAGE_STATE:
						swriter = new StringWriter();
						writer = new PrintWriter(swriter, true);
						String message = ""; //$NON-NLS-1$
						if (line.length() > 8) {
							message = line.substring(9);
						}
						if (current != null) {
							current.setMessage(message);
						}
						writerState = MESSAGE_STATE;
						break;
					default:
						break;
				}
			}
		} catch (IOException e) { // do nothing
		} finally {
			if (writer != null) {
				setData(current, session, writerState, swriter);
				writer.close();
			}
		}

		for (LogEntry e : tmp) {
			if (e.getSession().equals(currentSession)) {
				entries.add(e);
			}
		}
		return entries;
	}

	/**
	 * Assigns data from writer to appropriate field of current Log Entry or
	 * Session, depending on writer state.
	 */
	private static void setData(LogEntry current, LogSession session, int writerState, StringWriter swriter) {
		if (writerState == STACK_STATE && current != null) {
			current.setStack(swriter.toString());
		} else if (writerState == SESSION_STATE && session != null) {
			session.setSessionData(swriter.toString());
		} else if (writerState == MESSAGE_STATE && current != null) {
			StringBuilder sb = new StringBuilder(current.getMessage());
			String continuation = swriter.toString();
			if (continuation.length() > 0) {
				sb.append(System.lineSeparator()).append(continuation);
			}
			current.setMessage(sb.toString());
		}
	}

	/**
	 * Updates the currentSession to be the one that is not null or has most recent
	 * date.
	 */
	private static LogSession updateCurrentSession(LogSession currentSession, LogSession session) {
		if (currentSession == null) {
			return session;
		}
		Date currentDate = currentSession.getDate();
		Date sessionDate = session.getDate();
		if (currentDate == null && sessionDate != null) {
			return session;
		} else if (currentDate != null && sessionDate == null) {
			return session;
		} else if (currentDate != null && sessionDate != null && sessionDate.after(currentDate)) {
			return session;
		}

		return currentSession;
	}

	/**
	 * Adds entry to the list if it's not filtered. Removes entries exceeding the
	 * count limit.
	 */
	private static void addEntry(LogEntry entry, List<LogEntry> entries) {
		entries.add(entry);
	}

	private static void setNewParent(ArrayList<LogEntry> parents, LogEntry entry, int depth) {
		if (depth + 1 > parents.size()) {
			parents.add(entry);
		} else {
			parents.set(depth, entry);
		}
	}

	/**
	 * Copied from org.eclipse.ui.internal.views.log.LogSession
	 */
	public static class LogSession {

		public static final String SESSION = "!SESSION"; //$NON-NLS-1$
		private String data;
		private Date date;

		public void setSessionData(String data) {
			this.data = data;
		}

		public Date getDate() {
			return this.date;
		}

		private void setDate(String dateString) {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); //$NON-NLS-1$
			try {
				this.date = formatter.parse(dateString);
			} catch (ParseException e) { // do nothing
			}
		}

		public void processLogLine(String line) {
			// process "!SESSION <dateUnknownFormat> ----------------------------"
			if (line.startsWith(SESSION)) {
				line = line.substring(SESSION.length()).trim(); // strip "!SESSION "
				int delim = line.indexOf("----"); //$NON-NLS-1$ // single "-" may be in date, so take few for sure
				if (delim == -1) {
					return;
				}
				String dateBuffer = line.substring(0, delim).trim();
				setDate(dateBuffer);
			}
		}
	}

	/**
	 * Copied from org.eclipse.ui.internal.views.log.LogEntry
	 */
	public static class LogEntry {

		public static final String F_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"; //$NON-NLS-1$
		private static final DateTimeFormatter GREGORIAN_SDF = DateTimeFormatter.ofPattern(F_DATE_FORMAT, Locale.ENGLISH).withZone(ZoneId.systemDefault());
		public static final String SPACE = " "; //$NON-NLS-1$
		private LogSession session;
		private String stack;
		private String message;
		private List<LogEntry> children = new ArrayList<>();
		private int severity;
		private int code;
		private String pluginId;
		private Date fDate;

		public void setSession(LogSession currentSession) {
			this.session = currentSession;
		}

		public LogSession getSession() {
			return this.session;
		}

		public String getMessage() {
			return this.message;
		}

		public void setStack(String stack) {
			this.stack = stack;
		}

		public String getStack() {
			return stack;
		}

		public int processSubEntry(String line) throws IllegalArgumentException {
			//!SUBENTRY <depth> <pluginID> <severity> <code> <date>
			//!SUBENTRY  <depth> <pluginID> <date>if logged by the framework!!!
			StringTokenizer stok = new StringTokenizer(line, SPACE);
			StringBuilder dateBuffer = new StringBuilder();
			int depth = 0;
			String token = null;
			int tokens = stok.countTokens();
			for (int i = 0; i < tokens; i++) {
				token = stok.nextToken();
				switch (i) {
					case 0 : {
						break;
					}
					case 1 : {
						try {
							depth = Integer.parseInt(token);
						} catch (NumberFormatException e) {
							throw new IllegalArgumentException("Failed to parse '" + token + "'", e); //$NON-NLS-1$//$NON-NLS-2$
						}
						break;
					}
					case 2 : {
						pluginId = token;
						break;
					}
					case 3 : {
						try {
							severity = Integer.parseInt(token);
						} catch (NumberFormatException nfe) {
							appendToken(dateBuffer, token);
						}
						break;
					}
					case 4 : {
						try {
							code = Integer.parseInt(token);
						} catch (NumberFormatException nfe) {
							appendToken(dateBuffer, token);
						}
						break;
					}
					default : {
						appendToken(dateBuffer, token);
					}
				}
			}
			try {
				Date date = Date.from(Instant.from(GREGORIAN_SDF.parse(dateBuffer.toString())));
				if (date != null) {
					fDate = date;
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Failed to parse '" + dateBuffer + "'", e); //$NON-NLS-1$//$NON-NLS-2$
			}
			return depth;
		}

		public void processEntry(String line) throws IllegalArgumentException {
			//!ENTRY <pluginID> <severity> <code> <date>
			//!ENTRY <pluginID> <date> if logged by the framework!!!
			StringTokenizer stok = new StringTokenizer(line, SPACE);
			severity = 0;
			code = 0;
			StringBuilder dateBuffer = new StringBuilder();
			int tokens = stok.countTokens();
			String token = null;
			for (int i = 0; i < tokens; i++) {
				token = stok.nextToken();
				switch (i) {
					case 0: {
						break;
					}
					case 1: {
						pluginId = token;
						break;
					}
					case 2: {
						try {
							severity = Integer.parseInt(token);
						} catch (NumberFormatException nfe) {
							appendToken(dateBuffer, token);
						}
						break;
					}
					case 3: {
						try {
							code = Integer.parseInt(token);
						} catch (NumberFormatException nfe) {
							appendToken(dateBuffer, token);
						}
						break;
					}
					default: {
						appendToken(dateBuffer, token);
					}
				}
			}
			String stringToParse = dateBuffer.toString();
			try {
				Date date = Date.from(Instant.from(GREGORIAN_SDF.parse(stringToParse)));
				if (date != null) {
					fDate = date;
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Failed to parse '" + dateBuffer + "'", e); //$NON-NLS-1$//$NON-NLS-2$
			}
		}

		void appendToken(StringBuilder buffer, String token) {
			if (buffer.length() > 0) {
				buffer.append(SPACE);
			}
			buffer.append(token);
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public void addChild(LogEntry child) {
			children.add(child);
		}

		public Date getDate() {
			return fDate;
		}

		public int getSeverity() {
			return severity;
		}
	}
}
