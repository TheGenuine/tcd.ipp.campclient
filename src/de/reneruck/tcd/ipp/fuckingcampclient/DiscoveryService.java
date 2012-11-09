package de.reneruck.tcd.ipp.fuckingcampclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import de.reneruck.tcd.ipp.datamodel.Statics;

public class DiscoveryService extends Thread {

	private boolean running;
	private DatagramSocket socket;

	public DiscoveryService() {
		System.out.println("Starting DatabaseDiscoveryServiceHandler");
	}

	@Override
	public void run() {
		System.out.println("Starting broadcasting service");
		while (this.running) {
			if (this.socket != null) {
				sendBroadcast();
			} else {
				setupSocket();
			}
		}
	}

	private void setupSocket() {
		try {
			this.socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void sendBroadcast() {
		try {
			InetAddress group = InetAddress.getByName("230.0.0.1");
			DatagramPacket packet = new DatagramPacket(new byte[100], 100, group, Statics.CLIENT_DISCOVERY_PORT);
			this.socket.send(packet);
			
			Thread.sleep(5000);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public void shutdown() {
		if(this.socket != null) {
			this.socket.close();
		}
		this.running = false;
	}
}
