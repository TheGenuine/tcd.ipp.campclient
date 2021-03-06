package de.reneruck.tcd.ipp.campclient;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeoutException;

import de.reneruck.tcd.ipp.campclient.actions.FinackAndShutdown;
import de.reneruck.tcd.ipp.campclient.actions.ReceiveData;
import de.reneruck.tcd.ipp.campclient.actions.SendControlSignal;
import de.reneruck.tcd.ipp.campclient.actions.SendData;
import de.reneruck.tcd.ipp.campclient.actions.SendData2;
import de.reneruck.tcd.ipp.campclient.actions.ShutdownConnection;
import de.reneruck.tcd.ipp.datamodel.Airport;
import de.reneruck.tcd.ipp.datamodel.Callback;
import de.reneruck.tcd.ipp.datamodel.Datagram;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.TransitionExchangeBean;
import de.reneruck.tcd.ipp.fsm.Action;
import de.reneruck.tcd.ipp.fsm.FiniteStateMachine;
import de.reneruck.tcd.ipp.fsm.SimpleState;
import de.reneruck.tcd.ipp.fsm.Transition;
import de.reneruck.tcd.ipp.fsm.TransitionEvent;

/**
 * The {@link TransitionExchange} is responsible for all communication to and
 * from the server in the city or a client in the camp.<br>
 * A {@link TransitionExchange} gets started with the knowlege which kind of
 * communication partner he has to expect, whether a server or a client. After a
 * successfull data exchange the {@link TransitionExchange} shuts itself down.
 * 
 * @author Rene Ruck
 * 
 */
public class TransitionExchange implements Callback{

	private static int MAX_TRIES = 5;

	private SharedPreferences transitionStore;
	private Socket socket;
	private boolean listen = true;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private List<InetAddress> dbServers;
	private String mode;
	private TransitionExchangeBean transitionExchangeBean;
	private Airport targetAirport;

	private FiniteStateMachine fsm;

	/**
	 * Creates a new {@link TransitionExchange} and configures everything for
	 * the communication.
	 */
	public TransitionExchange(SharedPreferences transitionStore, List<InetAddress> dbServers, String mode, Airport target) {
		this.transitionStore = transitionStore;
		this.dbServers = dbServers;
		this.mode = mode;
		this.transitionExchangeBean = new TransitionExchangeBean();
		this.targetAirport = target;
		setupFSM();
	}

	/**
	 * Creates the Finite State Machine to handle the communication protocol.
	 */
	private void setupFSM() {
		this.fsm = new FiniteStateMachine();
		
		SimpleState state_start = new SimpleState("start");
		SimpleState state_syn = new SimpleState("syn");
		SimpleState state_acked = new SimpleState("acked");
		SimpleState state_waitRxModeAck = new SimpleState("waitRxModeAck");
		SimpleState state_ReceiveData = new SimpleState("ReceiveData");
		SimpleState state_SendData = new SimpleState("SendData");
		SimpleState state_finished = new SimpleState("finished");

		Action sendSYN = new SendControlSignal(this.transitionExchangeBean, Statics.SYN);
		Action sendSYNACK = new SendControlSignal(this.transitionExchangeBean, Statics.SYNACK);
		Action sendRxServer = new SendControlSignal(this.transitionExchangeBean, Statics.RX_SERVER);
		Action sendRxHeli = new SendControlSignal(this.transitionExchangeBean, Statics.RX_HELI);
		Action receiveData = new ReceiveData(this.transitionExchangeBean, this.transitionStore);
		Action sendData = new SendData2(this.transitionExchangeBean, this.transitionStore);
		Action sendFIN = new SendControlSignal(this.transitionExchangeBean, Statics.FIN);
		Action shutdownConnection = new ShutdownConnection(this.transitionExchangeBean);
		Action sendFinackAndShutdon = new FinackAndShutdown(this.transitionExchangeBean);

		Transition txSyn = new Transition(new TransitionEvent("sendSyn"), state_syn, sendSYN);
		Transition rxAck = new Transition(new TransitionEvent(Statics.ACK), state_acked, sendSYNACK);
		
		Transition txMode_send = new Transition(new TransitionEvent("sendMode_send"), state_waitRxModeAck, sendRxServer);
		Transition txMode_receive = new Transition(new TransitionEvent("sendMode_receive"), state_waitRxModeAck, sendRxHeli);
		
		Transition rxMode_Heli_ack = new Transition(new TransitionEvent(Statics.RX_HELI_ACK), state_ReceiveData, null);
		Transition rxMode_Server_ack = new Transition(new TransitionEvent(Statics.RX_SERVER_ACK), state_SendData, sendData);

		Transition rxDataAck = new Transition(new TransitionEvent(Statics.ACK), state_SendData, sendData);
		Transition rxData = new Transition(new TransitionEvent(Statics.DATA), state_ReceiveData, receiveData);

		Transition finishedSending = new Transition(new TransitionEvent(Statics.FINISH_RX_SERVER), state_finished, sendFIN);
		Transition rxFin = new Transition(new TransitionEvent(Statics.FIN), null, sendFinackAndShutdon);
		Transition rxFinACK = new Transition(new TransitionEvent(Statics.FINACK), null, shutdownConnection);

		state_start.addTranstion(txSyn);
		state_syn.addTranstion(rxAck);
		state_syn.addTranstion(txSyn);
		state_acked.addTranstion(txMode_send);
		state_acked.addTranstion(txMode_receive);
		
		state_waitRxModeAck.addTranstion(rxMode_Heli_ack);
		state_waitRxModeAck.addTranstion(rxMode_Server_ack);
		
		state_SendData.addTranstion(rxDataAck);
		state_SendData.addTranstion(finishedSending);
		
		state_ReceiveData.addTranstion(rxData);
		state_ReceiveData.addTranstion(rxFin);
		
		state_finished.addTranstion(rxFinACK);
		state_finished.addTranstion(rxFin);

		this.fsm.setStartState(state_start);
		
		this.transitionExchangeBean.setFsm(this.fsm);
	}

	/**
	 * Start the data exchange process with the in the constructor specified
	 * parameters
	 */
	public void startExchange() {
		try {
			waitForServer();
			establishConnection();
			kickOffFSM();
			waitForAnswer();
		} catch (TimeoutException e) {
			System.out.println("No Server found");
		} catch (Exception e) {
			System.err.println("Error in the FSM");
		}

	}

	private void kickOffFSM() throws Exception {
		this.fsm.handleEvent(new TransitionEvent("sendSyn"));
	}

	private void waitForServer() throws TimeoutException {
		System.out.print("Looking for available DB servers ");
		int tries = 0;
		while (this.dbServers.isEmpty()) {
			if (tries > MAX_TRIES)
				break;
			tries++;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.print(". ");
		}
		if (tries < MAX_TRIES) {
			System.out.println(" ");
			System.out.println("Found a server ");
		} else {
			throw new TimeoutException("No DB server found in reasonable time");
		}
	}

	private void establishConnection() {
		try {
			System.out.println("Establishing connection to " + this.dbServers.get(0));
			this.socket = new Socket(this.dbServers.get(0), getPortToConnect());
			this.in = new ObjectInputStream(this.socket.getInputStream());
			this.out = new ObjectOutputStream(this.socket.getOutputStream());
			this.out.flush();
			
			this.transitionExchangeBean.setIn(this.in);
			this.transitionExchangeBean.setOut(this.out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int getPortToConnect() {
		switch (this.targetAirport) {
		case camp:
			return Statics.CLIENT_PORT;
		case city:
			return Statics.DB_SERVER_PORT;
		default:
			return Statics.DB_SERVER_PORT;
		}
	}

	private void waitForAnswer() {
		try {
			while (this.listen){
				Thread.sleep(500);
				handle(this.in.readObject());
			}
		} catch (IOException e) {
			System.err.println(e.getCause());
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			shutdown();
		}
	}

	/**
	 * Verify incoming messages and pass them into the FSM to be processed.
	 * 
	 * @param input
	 *            incoming message
	 */
	private void handle(Object input) {
		if (input instanceof Datagram) {
			TransitionEvent event = getTransitionEventFromDatagram((Datagram) input);
			try {
				this.fsm.handleEvent(event);
				
				if(this.fsm.getCurrentState() != null && "acked".equals(this.fsm.getCurrentState().getIdentifier())) {
					if(Statics.RX_HELI.equals(this.mode)) {
						this.fsm.handleEvent(new TransitionEvent("sendMode_receive"));					
					} else {
						this.fsm.handleEvent(new TransitionEvent("sendMode_send"));					
					}
				}
				if(this.fsm.getCurrentState() != null && "finished".equals(this.fsm.getCurrentState().getIdentifier())) {
					this.fsm.handleEvent(new TransitionEvent(Statics.SHUTDOWN));
				}
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
		System.out.println("Shutting connection down");
		this.listen = false;
		try {
			this.out.close();
			this.in.close();
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void handleCallback() {
		shutdown();
	}
}
