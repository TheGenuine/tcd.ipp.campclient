package de.reneruck.tcd.ipp.campclient.actions;

import java.net.Socket;

import de.reneruck.tcd.ipp.datamodel.transition.TransitionExchangeBean;
import de.reneruck.tcd.ipp.fsm.Action;
import de.reneruck.tcd.ipp.fsm.TransitionEvent;

public class ShutdownConnection implements Action {

	private TransitionExchangeBean bean;

	public ShutdownConnection(TransitionExchangeBean bean) {
		this.bean = bean;
	}

	@Override
	public void execute(TransitionEvent event) throws Exception {
		Socket connection = this.bean.getConnection();
		if(connection != null) {
			connection.close();
		}
	}

}
