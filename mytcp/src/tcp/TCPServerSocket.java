package tcp;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;

import pktmngr.PacketManager;

import java.io.IOException;
import java.net.DatagramPacket;
public class TCPServerSocket {
	private double lossRate;
	private int listeningPort;
	private DatagramSocket udpsocket;
	private int clientISN;
	private int recvBase;
	private ArrayList<DatagramPacket> packets;
	private ArrayList<Byte> packetsStatus;
	private boolean connected;
	private int rtt;
	private int windowSize;
	
	public TCPServerSocket(double _lossRate) {
		lossRate = _lossRate;
		windowSize = 100;
		packets = new ArrayList<DatagramPacket>(Collections.nCopies(windowSize, null));
		packetsStatus = new ArrayList<Byte>(Collections.nCopies(windowSize, (byte)0));
		connected = false;
		rtt = 500;
	}
	
	public void listen(int port) throws SocketException, UnknownHostException {
		listeningPort = port;
		udpsocket = new DatagramSocket(listeningPort, InetAddress.getByName("localhost"));
		udpsocket.setSoTimeout(rtt);
	}
	
	public void accept() throws IOException {
		System.out.println("Waiting for client...");
		while (true) {
			DatagramPacket recvPacket = new DatagramPacket(new byte[1024], 1024);
			try {
				udpsocket.receive(recvPacket);
			}
			catch (Exception e) {
				continue;
			}
			
			if (losePacket()) {
				continue;
			}
			
			System.out.println("RECVBASE");
			System.out.println(recvBase);
			PacketManager.printData(recvPacket, "(SYN) From client");
			System.out.println();
			
			if (PacketManager.isSynPacket(recvPacket)) {
				InetAddress clientAddress = recvPacket.getAddress();
				int clientPort = recvPacket.getPort();
				clientISN = PacketManager.getACK(recvPacket);
				recvBase = clientISN + 1;
				
				// now server knows that client will send packets with numbers starting from clientISN
				// Send packet with info "I know your ISN, am waiting for packet with number recvBase"
				DatagramPacket sendPacket = PacketManager.create(
						(byte)0,
						clientISN+1,
						"",
						clientAddress,
						clientPort
				);
				udpsocket.send(sendPacket);
				
				System.out.println("RECVBASE");
				System.out.println(recvBase);
				PacketManager.printData(sendPacket, "(SYN) To client");
				System.out.println();
				
				connected = true;
				break;
			}
			else {
				continue;
			}
		}
	}
	
	public DatagramPacket receive() throws SocketException, TCPException {
		if (!connected) {
			throw new TCPException("Not connected.");
		}
		// if packet with number recvBase have already been buffered
		if (packetsStatus.get(0).byteValue() == (byte)2) {
			DatagramPacket toReturn = shiftPackets();
			if (toReturn == null) {
				// timerRunning = true used so that the server does not
				// think that the client has stopped sending packets
				connected = false;
			}
			return toReturn;
		}
		/* packet status:
		 *		0 - not received
		 * 		1 - expecting
		 * 		2 - buffered
		 */
		int receiveTry = 0;
		while (true) {
			if (receiveTry == 120) {
				System.out.println("Client wasn't sending anything in a minute, disconnecting...");
				connected = false;
				return null;
			}
			try {
				DatagramPacket recvPacket = new DatagramPacket(new byte[1024], 1024);
				try {
					udpsocket.receive(recvPacket);
					receiveTry = 0;
				}
				catch (Exception e) {
					receiveTry++;
					continue;
				}
				
				if (losePacket()) {
					continue;
				}
				
				
				System.out.println("RECVBASE");
				System.out.println(recvBase);
				PacketManager.printData(recvPacket, "(RECV) From client");
				System.out.println();
				
				InetAddress clientAddress = recvPacket.getAddress();
				int clientPort = recvPacket.getPort();
				int seq = PacketManager.getACK(recvPacket);

				if (recvBase <= seq && seq < recvBase + windowSize) {
					// if received packet is not buffered (its status != 2)
					if (packetsStatus.get(seq - recvBase).byteValue() != (byte)2) {
						// store packet if it is not a FYN packet
						if (!PacketManager.isFinPacket(recvPacket)) {
							packets.set(seq - recvBase, recvPacket);
						}
						packetsStatus.set(seq - recvBase, (byte)2);
					}
					// if received what expected
					if (seq == recvBase) {
						// pass continuous sequence of buffered packets to
						// an upper layer
						int passed = 0;
						for (int i = 0; i < windowSize; ++i) {
							// break on first not buffered packet
							if (packetsStatus.get(i).byteValue() != (byte)2) {
								break;
							}
							passed++;
						}
						
						// send to client that now server expects packet
						// with number recvBase + passed
						DatagramPacket sendPacket = PacketManager.create(
								(byte)0,
								recvBase + passed,
								"",
								clientAddress,
								clientPort
						);
						udpsocket.send(sendPacket);
						
						System.out.println("RECVBASE");
						System.out.println(recvBase + 1); // +1 needed because sendBase will be incremented in shiftPackets
						PacketManager.printData(sendPacket, "(RECV) To client");
						System.out.println();
						
						// return received packet (because function need
						// to return something after successful receiving)
						DatagramPacket toReturn = shiftPackets();
						// packet is null if it was a FYN packet
						if (toReturn == null) {
							connected = false;
						}
						return toReturn;
					}
					else {
						// after buffering the received packet,
						// server still wants to receive packet with number recvBase
						DatagramPacket sendPacket = PacketManager.create(
								(byte)0,
								recvBase,
								"",
								clientAddress,
								clientPort
						);
						udpsocket.send(sendPacket);
						
						System.out.println("RECVBASE");
						System.out.println(recvBase);
						PacketManager.printData(sendPacket, "(Too early) To client");
						System.out.println();
						
					}
				}
				if (recvBase - windowSize <= seq && seq < recvBase) {
					DatagramPacket sendPacket = PacketManager.create(
							(byte)0,
							recvBase,
							"",
							clientAddress,
							clientPort
					);
					udpsocket.send(sendPacket);
					
					System.out.println("RECVBASE");
					System.out.println(recvBase);
					PacketManager.printData(sendPacket, "(Old) To client");
					System.out.println();
				}
			}
			catch (Exception e) {
				continue;
			}
		}
	}
	
	private DatagramPacket shiftPackets() {
		DatagramPacket toReturn = packets.remove(0);
		packetsStatus.remove(0);
		packets.add(null);
		packetsStatus.add((byte)0);
		recvBase++;
		return toReturn;
	}
	
	private boolean losePacket() {
		if (Math.random() < lossRate) {
			return true;
		}
		return false;
	}
	
	public boolean isConnected() {
		return connected;
	}
}
