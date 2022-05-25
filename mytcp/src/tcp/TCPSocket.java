package tcp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import pktmngr.PacketManager;

public class TCPSocket {
	// SENDBASE IS A NUMBER OF PACKET CLIENT WANTS TO SEND

	private DatagramSocket udpsocket;
	private InetAddress serverAddr;
	private int serverPort;
	private int rtt;
	private boolean connected;

	private int packetSize;
	private DatagramPacket recvPacket;
	
	private int isn;
	private int sendBase;
	private int previouslySent;
	private int triesPerPacket;
	private boolean timerRunning;
	private Timer timer;

	public TCPSocket() throws SocketException {
		connected = false;
		previouslySent = 0;
		triesPerPacket = 30;
		
		packetSize = 32;
		recvPacket = new DatagramPacket(new byte[1024], 1024);

		timerRunning = false;
		timer = new Timer();	
	}
	
	/*
	 * sendBase - number of first non-acked packet
	 * 
	 * After a connect, private variable sendBase is set to ISN.
	 * When N packets successfully sent, sendBase increases by N.
	 * So, the next time the send function is called, 
	 * sendBase+N will be used as the starting number
	 */
	
	private void send0(DatagramPacket[] packets, int numberOfPackets) throws TCPException{
		System.out.println("Sending packets from [" + Integer.toString(sendBase) + "] to (" + Integer.toString(sendBase + numberOfPackets) + ")");
		
		/* 
		 * Packet status meanings:
		 *		0 - not sent (therefore not acked)
		 *		1 - sent, not acked
		 *		2 - acked (server received the packet)
		*/
		byte[] packetStatus = new byte[numberOfPackets];
		byte[] packetSendTry = new byte[numberOfPackets];
		for (int i = 0; i < numberOfPackets; ++i){
			packetStatus[i] = (byte)0;
			packetSendTry[i] = (byte)0;
		}
		int sentPackets = 0;

		while (true) {
			if (triesPerPacket > 0) {
				for (int i = 0; i < numberOfPackets; ++i) {
					if (packetSendTry[i] == triesPerPacket) {
						throw new TCPException("Server is not responding");
					}
				}
			}
			// Event: data received from application above
			if (sentPackets < numberOfPackets) {
				try {
					// send a packet via unreliable channel
					udpsocket.send(packets[sentPackets]);
					if (triesPerPacket > 0) {
						packetSendTry[sentPackets]++;
					}
					
					System.out.println("SENDBASE");
					System.out.println(sendBase);
					PacketManager.printData(packets[sentPackets], "(Not sent) To server");
					System.out.println();
					
					// mark the packet as "sent, not acked"
					packetStatus[sentPackets] = (byte)1;
					// have sent one more packet
					sentPackets += 1;

					
					if (timerRunning == false) {
						startTimer();
					}
				}
				catch (IOException e) {
					// something went terribly wrong
					e.printStackTrace();
					System.exit(1);
				}
			}
			
			// Event: timer timeout (timerRunning == false)
			if (timerRunning == false) {
				// retransmit first not yet acked packet
				try {
					udpsocket.send(packets[sendBase - isn - previouslySent]);
					if (triesPerPacket > 0) {
						packetSendTry[sendBase - isn - previouslySent]++;
					}
					
					System.out.println("SENDBASE");
					System.out.println(sendBase);
					PacketManager.printData(packets[sendBase - isn - previouslySent], "(Timeout) To server");
					System.out.println();
					
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
				startTimer();
			}
			
			
			// event: ACK received, with ACK field of serverACK
			try {
				udpsocket.receive(recvPacket);
				int serverACK = PacketManager.getACK(recvPacket);
				
				System.out.println("SENDBASE");
				System.out.println(sendBase);
				PacketManager.printData(recvPacket, "From server");
				System.out.println();
				
				// if server received packet with number > sendBase
				if (serverACK > sendBase) {
					// All packet numbers start from isn: isn, isn+1, isn+2, ...
					// Therefore if server received packet with number > sendBase, client must
					// 	ack packets with numbers [sendBase, serverACK - 1].
					// Packet with number "serverACK" excluded because it is a packet
					// 	that server want to receive
					int rightEdge = ((serverACK - isn - previouslySent > numberOfPackets) ? numberOfPackets : serverACK - isn - previouslySent);
					System.out.println("ACKING from " + Integer.toString(0) + " to " + Integer.toString(rightEdge));
					for (int i = 0; i < rightEdge; ++i) {
						packetStatus[i] = (byte)2;
						System.out.println("    " + Integer.toString(i + sendBase) + " ");
					}
					// now client want to send packet with number "serverACK"
					sendBase = serverACK;

					boolean somePacketsAreNotAcked = false;
					for (int i = 0; i < numberOfPackets; ++i) {
						if (packetStatus[i] != (byte)2) {
							somePacketsAreNotAcked = true;
							startTimer();
							break;
						}
					}
					// if all packets are acked, stop timer and step out of loop
					if (somePacketsAreNotAcked == false) {
						timerRunning = false;
						previouslySent += numberOfPackets;
						return;
					}
				}
			}
			catch (Exception e) {/*Do nothing*/}
		}
	}

	public void connect(String strRemoteAddress, int remotePort) throws SocketException, UnknownHostException, TCPException{
		// open socket
		udpsocket = new DatagramSocket();
		rtt = 500; // ms
		udpsocket.setSoTimeout(rtt);

		// generate new ISN
		isn = getNewInitialSequenceNumber(0, 1000);
		// now client will transmit packets from packet with number = isn
		sendBase = isn;

		// initialize server address and server port
		serverAddr = InetAddress.getByName(strRemoteAddress);
		serverPort = remotePort;

		// need to send one synchronization packet
		DatagramPacket[] packetsToSend = new DatagramPacket[1];

		// make SYN packet
		packetsToSend[0] = makeSynPacket();

		send0(packetsToSend, 1);
		// set connected flag as true
		connected = true;
	}
	
	public void send(String strData) throws TCPException{
		if (!connected) {
			throw new TCPException("Not connected.");
		}

		// determine how much packets are needed to send data
		int dataLength = strData.length();
		int numberOfPackets = dataLength / packetSize;
		if (dataLength % packetSize != 0) {
			numberOfPackets++;
		}
		
		DatagramPacket[] packets = new DatagramPacket[numberOfPackets]; 
		
		// split input into packets
		for (int i = 0; i < numberOfPackets; ++i) {
			packets[i] = makeRegularPacket(strData, i);
		}
		
		send0(packets, numberOfPackets);
	}

	public void disconnect() throws TCPException{
		if (!connected) {
			throw new TCPException("Not connected.");
		}
		// need to send one synchronization packet
		DatagramPacket[] packetsToSend = new DatagramPacket[1];

		// make FIN packet
		packetsToSend[0] = makeFinPacket();
		
		send0(packetsToSend, 1);
		// set connected flag as false
		connected = false;
		// close socket
		udpsocket.close();
	}
	
	private void startTimer(){
		timerRunning = true;
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				timerRunning = false;
			}
		}, rtt);
	}

	private DatagramPacket makeRegularPacket(String strData, int pktIdx) {
		String msg;
		int begin = pktIdx * packetSize;
		int end = (pktIdx + 1) * packetSize;

		if (end > strData.length()) {
			msg = strData.substring(begin);
		}
		else {
			msg = strData.substring(begin, end);
		}

		return PacketManager.create(
				(byte)0,
				pktIdx + sendBase,
				msg,
				serverAddr,
				serverPort
		);
	}

	private DatagramPacket makeSynPacket() {
		return PacketManager.create(
				(byte)1,
				isn,
				"",
				serverAddr,
				serverPort
		);
	}

	private DatagramPacket makeFinPacket() {
		return PacketManager.create(
				(byte)2,
				sendBase,
				"",
				serverAddr,
				serverPort
		);
	}
	
	private int getNewInitialSequenceNumber(int min, int max) {
		return (int)(Math.random() * (max - min)) + min;
	}
}
