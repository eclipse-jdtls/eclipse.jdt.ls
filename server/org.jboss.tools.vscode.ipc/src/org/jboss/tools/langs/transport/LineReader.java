// Copyright (c) 2010 The Chromium Authors. All rights reserved.
// This program and the accompanying materials are made available
// under the terms of the Eclipse Public License v1.0 which accompanies
// this distribution, and is available at
// http://www.eclipse.org/legal/epl-v10.html

package org.jboss.tools.langs.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 
 * A helper reader class that allows to read LF-terminated lines and fixed-sized blocks of bytes
 * in turn. To keep it fast it stores the data inside its internal buffer.
 * 
 * Copied from Eclipse JSDT project.
 */
class LineReader {
  private static final byte LF_BYTE = '\n';
  private static final byte CR_BYTE = '\r';

  // A source stream.
  private final InputStream inputStream;

   // Internal main buffer. Should be kept in 'read' (flipped) state.
  private final ByteBuffer buffer;
  {
    buffer = ByteBuffer.allocate(1024);
    buffer.flip();
  }

  // A cached buffer instance used for constructing a string.
  private ByteBuffer lineBuffer = ByteBuffer.allocate(20);

  LineReader(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  /**
   * Method has similar semantics to {@link BufferedReader#read(char[], int, int)} method.
   */
  public int read(byte[] cbuf, int off, int len) throws IOException {
    if (buffer.hasRemaining()) {
      len = Math.min(len, buffer.remaining());
      buffer.get(cbuf, off, len);
      return len;
    } else {
      return inputStream.read(cbuf, off, len);
    }
  }

  /**
   * Method has similar semantics to {@link BufferedReader#readLine()} method.
   */
  public String readLine(Charset charset) throws IOException {
    lineBuffer.clear();

    while (true) {
      if (buffer.hasRemaining()) {
        boolean lineEndFound = false;
        int pos;
        findingLineEnd:
        for (pos = buffer.position(); pos < buffer.limit(); pos++) {
          if (buffer.get(pos) == LF_BYTE) {
            lineEndFound = true;
            break findingLineEnd;
          }
        }
        int chunkLen = pos - buffer.position();
        if (chunkLen > 0) {
          if (lineBuffer.remaining() < chunkLen) {
            int newSize = Math.max(lineBuffer.capacity() * 2, lineBuffer.position() + chunkLen);
            ByteBuffer newLineBuffer = ByteBuffer.allocate(newSize);
            lineBuffer.flip();
            newLineBuffer.put(lineBuffer);
            lineBuffer = newLineBuffer;
          }
          buffer.get(lineBuffer.array(), lineBuffer.position(), chunkLen);
          lineBuffer.position(lineBuffer.position() + chunkLen);
        }
        if (lineEndFound) {
          // Shift position.
          buffer.get();
          break;
        }
      }
      assert !buffer.hasRemaining();
      buffer.clear();
      int readRes = inputStream.read(buffer.array());
      if (readRes <= 0) {
        if (lineBuffer.position() == 0) {
          return null;
        } else {
          throw new IOException("End of stream while expecting line end");
        }
      }
      buffer.position(readRes);
      buffer.flip();
    }
    if (lineBuffer.position() > 0 && lineBuffer.get(lineBuffer.position() - 1) == CR_BYTE) {
      lineBuffer.position(lineBuffer.position() - 1);
    }
    return new String(lineBuffer.array(), 0, lineBuffer.position(), charset);
  }
}
