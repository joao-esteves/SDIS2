package utils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import chord.ChordManager;

public class Utils {
	
	public static final int TIME_MAX_TO_SLEEP = 400;
	public static final String ENCODING_TYPE = "ISO-8859-1";
	public static final int MAX_LENGTH_CHUNK = 64000;
	public static final int BYTE_TO_KBYTE = 1000;
	
	public static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	
	static {
		LOGGER.setUseParentHandlers(false);
		LOGGER.setLevel(Level.ALL);
		try {
			FileHandler h = new FileHandler("backup_%g_log_%u.txt", true);
			h.setFormatter(new SimpleFormatter());
			LOGGER.addHandler(h);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void randonSleep(int time) throws InterruptedException {
		Random r = new Random();
		Thread.sleep(r.nextInt(time));
	}

	/**
	 * Between two limits, excluding the lower one and including the upper one.
	 * @param inf
	 * @param sup
	 * @param value
	 * @return True if value is inbetween limits.
	 */
	public static boolean inBetween(String inf, String sup, String value) {
		BigInteger _inf = new BigInteger(inf,16);
		BigInteger _sup = new BigInteger(sup,16);
		BigInteger _value = new BigInteger(value,16);

		BigInteger aux = new BigInteger((Math.pow(2, ChordManager.getM())+"").getBytes());
		
		if(_sup.compareTo(_inf) <= 0) { //procura no meio do circulo todo
			_sup  = _sup.add(aux);
		}
		if(_value.compareTo(_inf) < 0) {
			_value = _value.add(aux);
		}
		return ((_inf.compareTo(_value) < 0) && (_value.compareTo(_sup) <= 0));
		
	}
	
	public static void log(String message) {
		LOGGER.info(message);
//		The following lists the Log Levels in descending order:
//
//			SEVERE (highest)
//
//			WARNING
//
//			INFO
//
//			CONFIG
//
//			FINE
//
//			FINER
//
//			FINEST
	}
	
	public static String readFile(String filepath) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(filepath));
		return new String(encoded, StandardCharsets.UTF_8);
	}
	
	public static void writeToFile(Path filePath, byte[] body) throws IOException {
		if(!Files.exists(filePath)) { //NOTE: O CHUNk nao Existe
			Files.createFile(filePath);
			AsynchronousFileChannel channel = AsynchronousFileChannel.open(filePath,StandardOpenOption.WRITE);
			CompletionHandler<Integer, ByteBuffer> writter = new CompletionHandler<Integer, ByteBuffer>() {
				@Override
				public void completed(Integer result, ByteBuffer buffer) {
					System.out.println("Finished writing!");
				}
	
				@Override
				public void failed(Throwable arg0, ByteBuffer arg1) {
					System.err.println("Error: Could not write!");
					
				}
				
			};
			ByteBuffer src = ByteBuffer.allocate(body.length);
			src.put(body);
			src.flip();
			channel.write(src, 0, src, writter);
		}
	}
}