package de.reneruck.tcd.ipp.campclient;

import java.io.IOException;

public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
        CommunicationServer comServer = new CommunicationServer();
        comServer.setRunning(true);
        comServer.start();
        
		DiscoveryService discoveryHandler = new DiscoveryService();
		discoveryHandler.setRunning(true);
		discoveryHandler.start();
		
		InputHandler inputhandler = new InputHandler();
		System.out.println("----------------------------------------------");
		System.out.println("Commandline ticket booking system");
		System.out.println("For a better user experience \n it is advised to use our Smartphone and tablet client");
		System.out.println("----------------------------------------------");
		System.out.println("Please enter your name:");
		
		String input = "";
		byte[] buffer = new byte[100];
		while(!input.equals("exit")) {
			try {
				System.in.read(buffer);
				inputhandler.handleInput(cleanInput(new String(buffer)));
				clearBuffer(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		discoveryHandler.setRunning(false);
		comServer.setRunning(false);
	}
	
	private static void clearBuffer(byte[] buffer) {
		for (int i = 0; i < buffer.length -1 ; i++) {
			buffer[i] = 0;
		}
	}

	private static String cleanInput(String input) {
		input.replace("\\n", " ");
		input.replace("\\r", " ");
		return input.trim();
	}

}
