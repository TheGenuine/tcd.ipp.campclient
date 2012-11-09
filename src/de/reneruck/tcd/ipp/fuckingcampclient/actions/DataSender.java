package de.reneruck.tcd.ipp.fuckingcampclient.actions;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.reneruck.tcd.ipp.datamodel.Callback;
import de.reneruck.tcd.ipp.datamodel.Datagram;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.Transition;

public class DataSender extends Thread {

	private Map<Long, Transition> dataset;
	private ObjectOutputStream out;
	private Callback callback;

	public DataSender(ObjectOutputStream out, Map<Long, Transition> dataset, Callback callback) {
		this.out = out;
		this.dataset = dataset;
		this.callback = callback;
	}
	
	@Override
	public void run() {
		do {
			Collection<Transition> values = this.dataset.values();
			for (Transition transition : values) {
				Map<String, Object> datagramContent = new HashMap<String, Object>();
				datagramContent.put(Statics.CONTENT_TRANSITION, transition);
				try {
					this.out.writeObject(new Datagram(Statics.DATA, datagramContent));
					this.out.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (!this.dataset.isEmpty());
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		this.callback.handleCallback();
	}

}
