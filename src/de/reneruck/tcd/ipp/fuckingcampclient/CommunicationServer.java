package de.reneruck.tcd.ipp.fuckingcampclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import de.reneruck.tcd.ipp.datamodel.Datagram;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.TransitionExchangeBean;
import de.reneruck.tcd.ipp.fsm.Action;
import de.reneruck.tcd.ipp.fsm.FiniteStateMachine;
import de.reneruck.tcd.ipp.fsm.SimpleState;
import de.reneruck.tcd.ipp.fsm.Transition;
import de.reneruck.tcd.ipp.fsm.TransitionEvent;
import de.reneruck.tcd.ipp.fuckingcampclient.actions.FinackAndShutdown;
import de.reneruck.tcd.ipp.fuckingcampclient.actions.ReceiveData;
import de.reneruck.tcd.ipp.fuckingcampclient.actions.SendControlSignal;
import de.reneruck.tcd.ipp.fuckingcampclient.actions.SendData;
import de.reneruck.tcd.ipp.fuckingcampclient.actions.ShutdownConnection;

public class CommunicationServer extends Thread {

	private boolean running;
	private ServerSocket socket;
	private ObjectOutputStream out;
	private Socket connection;
	private ObjectInputStream in;
	private FiniteStateMachine fsm;
	private TransitionExchangeBean transitionExchangeBean;
	private SharedPreferences transitionStore;
	
	public CommunicationServer() {
		this.transitionStore = new SharedPreferences();
		this.transitionExchangeBean = new TransitionExchangeBean();
		setupFSM();	
	}
	
	private void setupFSM() {
		this.fsm = new FiniteStateMachine();
		SimpleState state_start = new SimpleState("start");
		SimpleState state_syn = new SimpleState("syn");
		SimpleState state_waitRxMode = new SimpleState("waitRxMode");
		SimpleState state_ReceiveData = new SimpleState("ReceiveData");
		SimpleState state_SendData = new SimpleState("SendData");
		SimpleState state_fin = new SimpleState("finish");

		Action sendACK = new SendControlSignal(this.transitionExchangeBean, Statics.ACK);
		Action sendRxServerAck = new SendControlSignal(this.transitionExchangeBean, Statics.RX_SERVER_ACK);
		Action receiveData = new ReceiveData(this.transitionExchangeBean, this.transitionStore);
		Action sendData = new SendData(this.transitionExchangeBean, this.transitionStore);
		Action sendFIN = new SendControlSignal(this.transitionExchangeBean, Statics.FIN);
		Action shutdownConnection = new ShutdownConnection(this.transitionExchangeBean);
		Action sendFinackAndShutdon = new FinackAndShutdown(this.transitionExchangeBean);

		Transition rxSyn = new Transition(new TransitionEvent(Statics.SYN), state_syn, sendACK);
		Transition rxSynAck = new Transition(new TransitionEvent(Statics.SYNACK), state_waitRxMode, null);
		Transition rxAck = new Transition(new TransitionEvent(Statics.ACK), state_SendData, sendData);

		Transition rxSendData = new Transition(new TransitionEvent(Statics.RX_HELI), state_SendData, sendData);
		Transition rxReceiveData = new Transition(new TransitionEvent(Statics.RX_SERVER), state_ReceiveData, sendRxServerAck);
		Transition rxData = new Transition(new TransitionEvent(Statics.DATA), state_ReceiveData, receiveData);

		Transition finishedSending = new Transition(new TransitionEvent(Statics.FINISH_RX_HELI), state_fin, sendFIN);
		Transition rxFin = new Transition(new TransitionEvent(Statics.FIN), null, sendFinackAndShutdon);
		Transition rxFinACK = new Transition(new TransitionEvent(Statics.FINACK), null, shutdownConnection);

		state_start.addTranstion(rxSyn);
		state_syn.addTranstion(rxSyn);
		state_syn.addTranstion(rxSynAck);
		state_waitRxMode.addTranstion(rxReceiveData);
		state_waitRxMode.addTranstion(rxSendData);
		state_SendData.addTranstion(rxAck);
		state_SendData.addTranstion(finishedSending);
		state_ReceiveData.addTranstion(rxData);
		state_ReceiveData.addTranstion(rxFin);
		state_fin.addTranstion(rxFinACK);

		this.fsm.setStartState(state_start);
		this.transitionExchangeBean.setFsm(this.fsm);
	}
	
	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}
	
	private void setupSocket() {
		try {
			this.socket = new ServerSocket(Statics.CLIENT_PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		setupSocket();
		while(this.running) {
			try {
				Thread.sleep(1000);
				this.connection = this.socket.accept();
				InputStream inputStream = this.connection.getInputStream();
				this.out = new ObjectOutputStream(this.connection.getOutputStream());
				this.out.flush();
				this.in = new ObjectInputStream(inputStream);
				this.transitionExchangeBean.setIn(this.in);
				this.transitionExchangeBean.setOut(this.out);
				this.transitionExchangeBean.setConnection(this.connection);
				
				while(this.connection != null && !this.connection.isClosed()) {
					handleInput(this.in.readObject());
				}
				
				if(this.fsm == null ||this.fsm.getCurrentState() == null) {
					setupFSM();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	private void handleInput(Object input) {
		if (input instanceof Datagram) {
			TransitionEvent event = getTransitionEventFromDatagram((Datagram) input);
			try {
				this.fsm.handleEvent(event);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("Unknown type " + input.getClass() + " discarding package");
		}
	}
	
	private TransitionEvent getTransitionEventFromDatagram(Datagram input) {
		TransitionEvent event = new TransitionEvent(input.getType());
		for (String key : input.getKeys()) {
			event.addParameter(key, input.getPayload(key));
		}
		return event;
	}
	
	public void shutdown() {
		try {
			this.running = false;
			
			if(this.socket != null) {
				this.socket.close();
			}
			if(this.connection != null) {
				this.connection.close();
			}
		} catch (IOException e) {
			e.fillInStackTrace();
		}
	}
}
