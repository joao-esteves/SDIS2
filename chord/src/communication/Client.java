package communication;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import utils.UnsignedByte;

public class Client {
	private static ArrayList<String> cipher = new ArrayList<String>(Arrays.asList("TLS_DHE_RSA_WITH_AES_128_CBC_SHA"));

	public static String sendMessage(InetAddress addr, int port, String message) {

		SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

		SSLSocket socket;
		try {
			socket = (SSLSocket) socketFactory.createSocket(addr, port);
			socket.setSoTimeout(1000);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		socket.setEnabledCipherSuites(cipher.toArray(new String[0]));
		

		send(message, socket);

		return getResponse(socket);
	}

	/**
	 * Write to the socket (send message)
	 */
	public static void send(String message, SSLSocket socket) {
		byte[] sendData = message.getBytes();

		OutputStream sendStream;
		try {
			sendStream = socket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		try {
			sendStream.write(sendData);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Receives the message's response
	 */
	public static String getResponse(SSLSocket socket) {
		byte[] readData = new byte[1024];
		InputStream readStream;
		try {
			readStream = socket.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		try {
			readStream.read(readData);
		} catch(SocketTimeoutException e) {
			System.err.println("Socket timeout");
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return new String(readData);

	}


}
