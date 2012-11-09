package de.reneruck.tcd.ipp.fuckingcampclient.actions;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import de.reneruck.tcd.ipp.datamodel.Callback;
import de.reneruck.tcd.ipp.datamodel.Datagram;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.Transition;
import de.reneruck.tcd.ipp.datamodel.transition.TransitionExchangeBean;
import de.reneruck.tcd.ipp.fsm.Action;
import de.reneruck.tcd.ipp.fsm.TransitionEvent;
import de.reneruck.tcd.ipp.fuckingcampclient.SharedPreferences;

public class SendData implements Action, Callback {

	private static final String TAG = "SendData";
	private ObjectOutputStream out;
	private DataSender sender;
	private Map<Long, Transition> dataset = new HashMap<Long, Transition>();
	private SharedPreferences transitionsStore;
	private TransitionExchangeBean bean;
	private Gson gson;

	public SendData(TransitionExchangeBean transitionExchangeBean, SharedPreferences transitionsStore ) {
		this.bean = transitionExchangeBean;
		this.transitionsStore = transitionsStore;
	}

	@Override
	public void execute(TransitionEvent event) throws Exception {
		if(this.out == null) {
			this.out = this.bean.getOut();
		}
		
		if(this.sender == null) {
			send(Statics.RX_HELI_ACK);
			initializeDataSender();
		}
		
		if(Statics.ACK.equals(event.getIdentifier())) {
			Object parameter = event.getParameter(Statics.TRAMSITION_ID);
			if(parameter != null && parameter instanceof Long) {
				this.dataset.remove(parameter);
				System.out.println("Ack for " + parameter);
				System.out.println(this.dataset.size() + " remaining");
			}
		}
	}

	private void initializeDataSender() {
		createDataset();
		this.sender = new DataSender(this.out, this.dataset, this);
		this.sender.start();
	}

	private void createDataset() {
		this.gson = new Gson();
		Map<?, ?> all = this.transitionsStore.getAll();
		Collection<String> allTransitions = (Collection<String>) all.values();
		
		for (String transitionString : allTransitions) {
			if(isPending(transitionString) | isACKED(transitionString)){
				
				Object deserialized = deserialize(transitionString);
				if(deserialized instanceof Transition) {
					this.dataset.put(((Transition) deserialized).getTransitionId(), (Transition) deserialized);
				}
			}
		}
	}

	private boolean isPending(String transitionString) {
		return transitionString.contains("\"state\":\"PENDING\"");
	}
	
	private boolean isACKED(String transitionString) {
		return transitionString.contains("\"state\":\"ACKNOWLEGED\"");
	}

	private Object deserialize(Object readObject) {
		if (readObject instanceof String) {
			String input = (String) readObject;
			String[] split = input.split("=");
			if (split.length > 1) {
				try {
					Class<?> transitionClass = Class.forName(split[0]);
					Object fromJson = this.gson.fromJson(split[1].trim(), transitionClass);
					System.out.println("Successfully deserialized ");
					return fromJson;
				} catch (ClassNotFoundException e) {
					e.getMessage();
					System.out.println("Cannot deserialize, discarding package");
				}
			} else {
				System.out.println("No valid class identifier found, discarding package");
			}
		}
		return readObject;
	}
	
	@Override
	public void handleCallback() {
		this.sender = null;
		try {
			this.bean.getFsm().handleEvent(new TransitionEvent(Statics.FINISH_RX_HELI));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void send(String message) throws IOException {
		this.out.writeObject(new Datagram(message));
		this.out.flush();
	}

}
