/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.langs.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 *
 * Adopted from org.eclipse.wst.jsdt.chromium.internal.transport.Message
 *
 */
public class TransportMessage {

	private static final Logger LOGGER = Logger.getLogger(TransportMessage.class.getName());


	/*
	 * Each header field is comprised of a name and a value, separated by ': ' (a colon and a space).
	 */
	private static byte[] FIELD_SEPARATOR_BYTES = ": ".getBytes();

	/*
	 * Each header field is terminated by '\r\n'
	 */
	private static final byte[] HEADER_TERMINATOR_BYTES = "\r\n".getBytes();

	private static final String CT_VSCODE = "application/vscode-jsonrpc";

	private static final String CONTENT_TYPE = "Content-Type";
	private static final String CONTENT_LENGTH = "Content-Length";

	private final String content;

	public TransportMessage(String content) {
		this.content = content;
	}

	public String getContent() {
		return content;
	}


	void send(OutputStream outputStream, Charset charset) throws IOException {
		byte[] contentBytes = content.getBytes(charset);
		if (!"UTF-8".equalsIgnoreCase(charset.name())) {
			writeHeaderField(CONTENT_TYPE, CT_VSCODE + ";charset=" + charset.name(), outputStream, charset);
		}
		writeHeaderField(CONTENT_LENGTH, String.valueOf(contentBytes.length), outputStream, charset);
		outputStream.write(HEADER_TERMINATOR_BYTES);
		outputStream.write(contentBytes);
	}

	private static void writeHeaderField(String name, String value, OutputStream outputStream, Charset charset)
			throws IOException {
		outputStream.write(name.getBytes(charset));
		outputStream.write(FIELD_SEPARATOR_BYTES);
		outputStream.write(value.getBytes(charset));
		outputStream.write(HEADER_TERMINATOR_BYTES);
	}

	static TransportMessage fromStream(InputStream inputStream, Charset charset)
			throws IOException {

		Map<String, String> headers = new LinkedHashMap<>();

		String contentLengthValue = null;
		LineReader reader = new LineReader(inputStream);
		while (true) { // read headers
			String line = reader.readLine(charset);
			if (line == null) {
				LOGGER.fine("End of stream");
				return null;
			}
			if (line.length() == 0) {
				break; // end of headers
			}
			int semiColonPos = line.indexOf(':');
			if (semiColonPos == -1) {
				LOGGER.log(Level.SEVERE, "Bad header line: {0}", line);
				return null;
			}
			String name = line.substring(0, semiColonPos);
			String value = line.substring(semiColonPos + 1);
			String trimmedValue = value.trim();
			if (CONTENT_LENGTH.equals(name)) {
				contentLengthValue = trimmedValue;
			} else {
				headers.put(name, trimmedValue);
			}
		}

		// Read payload if applicable
		int contentLength = Integer.parseInt(contentLengthValue.trim());
		byte[] contentBytes = new byte[contentLength];
		int totalRead = 0;
		LOGGER.log(Level.FINER, "Reading payload: {0} bytes", contentLength);
		while (totalRead < contentLength) {
			int readBytes = reader.read(contentBytes, totalRead, contentLength - totalRead);
			if (readBytes == -1) {
				// End-of-stream (browser closed?)
				LOGGER.fine("End of stream while reading content");
				return null;
			}
			totalRead += readBytes;
		}

		String contentString = new String(contentBytes, charset);
		return new TransportMessage(contentString);
	}

}
