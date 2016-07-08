package org.jboss.tools.vscode.ipc;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Message;

/**
 * I/O stream based transport for JSON RPC
 *
 * @author Gorkem Ercan
 *
 */
final public class IPC implements Transport {

	private static final boolean PROTOCOL_LOG = Boolean.valueOf(System.getProperty("log.protocol", "false")).booleanValue();
	private static FileWriter protocolLog;

	
	private BufferedReader input;
	private PrintStream output;
	private List<DataListener> listeners = new ArrayList<DataListener>();
	private static String OS = System.getProperty("os.name").toLowerCase();
	
	
	public void addListener(DataListener listener){
		if(listeners.isEmpty()){
			install();
		}
		if(listeners.contains(listener)) 
			return;
		listeners.add(listener);
	}
	
	public void removeListener(DataListener listener){
		listeners.remove(listener);
	}
	
	private void install(){
		if(input != null && output != null ) return;
		
		final String stdInName = System.getenv("STDIN_PIPE_NAME");
		final String stdOutName = System.getenv("STDOUT_PIPE_NAME");
		
		IPCPlugin.logInfo("Installing IPC: out pipe: "+stdInName +" in pipe: " + stdOutName);	
		
			try {
				
				Thread t = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try{
							PrintStream tmpStream = null;
							if (stdInName == null) {
								input = new BufferedReader(new InputStreamReader(System.in));
							} else {
								final File outFile = new File(stdInName);
								if(isWindows()){
									RandomAccessFile raf = new RandomAccessFile(outFile, "rwd");
									tmpStream = new PrintStream(Channels.newOutputStream(raf.getChannel())); 
									input = new BufferedReader(new InputStreamReader(Channels.newInputStream(raf.getChannel())));
								}else{
									AFUNIXSocket sock = AFUNIXSocket.newInstance();
									sock.connect(new AFUNIXSocketAddress(outFile));
									tmpStream = new PrintStream(sock.getOutputStream());
									input = new BufferedReader(new InputStreamReader(sock.getInputStream()));
								}
								tmpStream.print("ready");
								tmpStream.flush();
							}
							IPCPlugin.logInfo("Connected input ");
							startListening();
						}catch (Exception e) {
							IPCPlugin.logException("failed to create IPC Input", e);
							// TODO: handle exception
						}
					}
				});
				t.setName("IPC Listener Thread");
				t.start();
				if (stdOutName == null) {
					output = System.out;
				} else {
					final File inFile = new File(stdOutName);
					if(isWindows()){
						RandomAccessFile raf = new RandomAccessFile(inFile, "rwd");
						output = new PrintStream(Channels.newOutputStream(raf.getChannel()));
					}else{
						AFUNIXSocket sock = AFUNIXSocket.newInstance();
						sock.connect(new AFUNIXSocketAddress(inFile));
						output = new PrintStream(sock.getOutputStream());
					}
					output.println("ready");
					output.flush();
				}
			} catch ( IOException e) {
				IPCPlugin.logException("failed to create IPC I/O", e);
			}
	}


	private void startListening() {
		StringBuilder headers = new StringBuilder();
		while(true){
			try {
				char current = (char) input.read();
				plog(current);
				headers.append(current);
				if (current == '\r') {
					char c1 = (char) input.read();
					plog(c1);
					headers.append(c1); // adds \n
					current = (char) input.read();
					plog(current);
					if (current == '\r') {
						headers.append((char) input.read());// write the '\n'
						int l = getContentLength(headers.toString());
						if( l>0 ){
							final char[] cbuf = new char[l];
							int readChars = input.read(cbuf, 0, l);
							IPCPlugin.logInfo("Read " + readChars + " content-lenght was " + l);
							plog(cbuf);
							notifyListeners(String.valueOf(cbuf));
						}
						headers = new StringBuilder();
					} else {
						headers.append(current);
					}
				}
			}catch (Exception e) {
				IPCPlugin.logException("exception on receive loop", e);
			}
		}
	}
	
	private int getContentLength(String rawHeaders){
		String[] headersArray = rawHeaders.split("\r\n");
	    for (int i = 0; i < headersArray.length; i++) {
	    	final String[] split = headersArray[i].split(":");
	    	if(split[0].equals("Content-Length")){
	    		return Integer.parseInt(split[1].trim());
	    	}
	    }
	    return -1;
	}
	
	private void notifyListeners(final String data){
		if(listeners.isEmpty())
			return;
		listeners.forEach((listener) -> listener.dataReceived(data));
	}

	@Override
	public void send(JSONRPC2Message response) {
		final String jsonString = response.toJSONString();
		StringBuilder r = new StringBuilder();
		r.append("Content-Length:");
		r.append(jsonString.getBytes().length);
		r.append("\r\n");
		r.append("\r\n");
		r.append(jsonString);
		plog("\n>>>> SERVER RESPONSE: " +jsonString) ;
		output.print(r);
		output.flush();
	}
	
	private boolean isWindows() {
	        return (OS.indexOf("win") >= 0);
	}
	
	public static void plog (String buf){
		if(PROTOCOL_LOG)
			plog(buf.toCharArray());
	}
	
	public static void plog (char buf){
		if(PROTOCOL_LOG)
			plog(new char[] {buf});
	}
	
	public static void plog(char[] buf ){
		if(!PROTOCOL_LOG) return;
		try {
			if (protocolLog == null) {
				Path cwd = Paths. get(System.getProperty("user.home"));
				Path logPath = cwd.toAbsolutePath().normalize().resolve("protocol.log");
				protocolLog = new FileWriter(logPath.toFile());
			}
			protocolLog.write(buf);
			protocolLog.flush();
		}
		catch (IOException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}	
	
}
