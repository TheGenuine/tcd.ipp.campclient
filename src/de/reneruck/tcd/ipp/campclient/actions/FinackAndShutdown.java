package de.reneruck.tcd.ipp.campclient.actions;

import java.io.ObjectOutputStream;
import java.net.Socket;

import de.reneruck.tcd.ipp.datamodel.Datagram;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.TransitionExchangeBean;
import de.reneruck.tcd.ipp.fsm.Action;
import de.reneruck.tcd.ipp.fsm.TransitionEvent;

public class FinackAndShutdown implements Action {

	private TransitionExchangeBean bean;

	public FinackAndShutdown(TransitionExchangeBean transitionExchangeBean) {
		this.bean = transitionExchangeBean;
	}

	@Override
	public void execute(TransitionEvent event) throws Exception {
		ObjectOutputStream out = this.bean.getOut();
		out.writeObject(new Datagram(Statics.FINACK));
		out.flush();
		
		Socket connection = this.bean.getConnection();
		if(connection != null) {
			connection.close();
		} else {
			System.err.println(this.getClass().getCanonicalName() + ": connection not availbale");
		}
	}

}
