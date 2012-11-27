package de.reneruck.tcd.ipp.campclient;


import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import de.reneruck.tcd.ipp.datamodel.Airport;
import de.reneruck.tcd.ipp.datamodel.Booking;
import de.reneruck.tcd.ipp.datamodel.Statics;
import de.reneruck.tcd.ipp.datamodel.transition.NewBookingTransition;

public class BookingProcess {

	private String user;
	private Airport startLoc;
	private String travelDate;
	private List<InetAddress> databaseServers = new LinkedList<InetAddress>();
	private DatabaseDiscoverer databaseDiscoverer;

	public BookingProcess(String user, Airport start) {
		this.user = user;
		this.startLoc = start;
		this.databaseDiscoverer = new DatabaseDiscoverer(databaseServers, Airport.city);
		this.databaseDiscoverer.setRunning(true);
		this.databaseDiscoverer.start();
	}

	public void createNewBooking() {

		SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		Calendar calendar = Calendar.getInstance();

		try {
			Date parsedDate = formater.parse(this.travelDate);
			calendar.setTime(parsedDate);
			
			Booking booking = new Booking(this.user, calendar.getTime(), this.startLoc);
			
			NewBookingTransition bookingTransition = new NewBookingTransition(booking);
			
			storeToDatabase(bookingTransition);
			System.out.println("Trying direct connection to DB server");
			if(this.databaseServers.size() > 0) {
				sendBookingToServer(booking);
				receiveBookingsFromServer();
			} else {
				System.out.println("No booking server available, storing it locally");
			}
			System.out.println("Stored Booking " + booking.toString());

			this.databaseDiscoverer.setRunning(false);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
	
	private void receiveBookingsFromServer() {
		TransitionExchange transitionExchange = new TransitionExchange(new SharedPreferences(), this.databaseServers, Statics.RX_HELI, Airport.city);
		transitionExchange.startExchange();
	}

	private void sendBookingToServer(Object bo) {
		TransitionExchange transitionExchange = new TransitionExchange(new SharedPreferences(), this.databaseServers, Statics.RX_SERVER, Airport.city);
		transitionExchange.startExchange();
	}

	private void storeToDatabase(NewBookingTransition bookingTransition) {
		SharedPreferences pref = new SharedPreferences();
		pref.put("T" + bookingTransition.getTransitionId(), bookingTransition.toString());
		pref.apply();
	}

	public String printCurrentBookings() {
		return "============== Booking request ============== \n" +
				"Name: " + this.user + "\n" +
				"From: " + this.startLoc + "\n" +
				"At: " + this.travelDate + "h \n" +
				"============================================= \n";
	}

	public void setTime(String time) {
		this.travelDate += " " + time;
	}

	public void setDate(Date inputDate) {
		SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy");
		String format = formater.format(inputDate);
		this.travelDate = format;
	}

}
