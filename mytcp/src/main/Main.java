package main;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import pktmngr.PacketManager;
import tcp.TCPException;
import tcp.TCPServerSocket;
import tcp.TCPSocket;



public class Main {
	
	private static String WORKING_MODE = "Client";
	
	private static void runClient() throws TCPException, IOException {
		// path of a file that is wanted to be sent
		String fileToSend = "PATH OF A FILE TO SEND";

		// create socket on arbitrary port
		TCPSocket socket = new TCPSocket();

		// connect to a loopback interface on port 25565
		socket.connect("localhost", 25565);
		System.out.println("Client connected!");

		// read whole file
		String text = new String(
			Files.readAllBytes(Paths.get(fileToSend)),
			StandardCharsets.UTF_8
		);
		
		socket.send(text);
		
		// disconnect from server
		socket.disconnect();
		System.out.println("Client disconnected!");
	}
	
	private static void runServer() throws TCPException, IOException {
		// path of a file where will be stored received information
		String fileToReceiveInto = "PATH OF A FILE TO RECEIVE INTO";
	
		// define loss rate of unreliable channel (from 0.0 to 1.0)
		double lossRate = 0.8;

		// create a server socket
		TCPServerSocket socket = new TCPServerSocket(lossRate);

		// set the socket to listen on the port 25565
		socket.listen(25565);

		// buffer packet
		DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
		while (true) {
			// accept incoming connection
			socket.accept();
			
			// writers
			FileOutputStream fos = new FileOutputStream(fileToReceiveInto);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			BufferedWriter out = new BufferedWriter(osw);
			
			pkt = socket.receive();
			while (socket.isConnected()) {
				// extract data (text) from the buffer packet
				String data = PacketManager.getData(pkt);
			    out.write(data);
			    
			    pkt = socket.receive();
			}

			// close writers
			out.close();
			fos.close();

			System.out.println("closed connection");
		}
		
	}
	
	public static void main(String[] args){
		
		if (WORKING_MODE.equals("Client")) {
			
			try {
				runClient();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.exit(0);
		}
		if (WORKING_MODE.equals("Server")) {
			try {
				runServer();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
			System.exit(0);
		}
	}

}
