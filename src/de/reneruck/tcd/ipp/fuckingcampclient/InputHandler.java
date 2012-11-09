package de.reneruck.tcd.ipp.fuckingcampclient;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import de.reneruck.tcd.ipp.datamodel.Airport;
import de.reneruck.tcd.ipp.datamodel.Booking;
import de.reneruck.tcd.ipp.datamodel.transition.CancelBookingTransition;
import de.reneruck.tcd.ipp.datamodel.transition.NewBookingTransition;
import de.reneruck.tcd.ipp.datamodel.transition.Transition;
import de.reneruck.tcd.ipp.datamodel.transition.TransitionState;

public class InputHandler {

	private static final Date THREE_DAYS = new Date(System.currentTimeMillis() + (3 * 86400000));
	private State currentState = State.init;
	private String currentUser;
	private BookingProcess bookingProcess;
	private Map<String, Object> editMap = new HashMap<String, Object>();
	private Gson gson = new Gson();
	
	public void handleInput(String command) {
		
		switch (currentState) {
		case init:
			System.out.println("Welcome " + command);
			this.currentUser = command;
			printMenu();
			this.currentState = State.menu;
			break;
		case menu:
			if (command.contains("1")) // encampment -> city
			{
				this.bookingProcess = new BookingProcess(this.currentUser, Airport.camp);
				printBookingMenu();
				this.currentState = State.booking;
			} else if (command.contains("2")) // city -> encampment
			{
				this.bookingProcess = new BookingProcess(this.currentUser, Airport.city);
				printBookingMenu();
				this.currentState = State.booking;
			} else if (command.contains("3")) // view bookings
			{
				System.out.println("Which booking do you want to cancel");
				printBookings();
				this.currentState = State.cancelBooking;
			} else if (command.contains("4")) {
				System.out.println("Goodby");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.exit(0);
			}
			break;
		case booking:
			Date inputDate = parseInputDate(command);
			if(inputDate != null)
			{
				this.bookingProcess.setDate(inputDate);
				printTimeSelection();
				this.currentState = State.timeselection;
			} else {
				System.out.println("Invalid Date input " + command);
				printBookingMenu();
			}
			break;
		case timeselection:
			int parseInt = Integer.parseInt(command);

			if(parseInt <= 6) {
				switch (parseInt) {
				case 1:
					this.bookingProcess.setTime("06:00");
					this.currentState = State.confirm;
					break;
				case 2:
 					this.bookingProcess.setTime("09:00");
 					this.currentState = State.confirm;
					break;
				case 3:
					this.bookingProcess.setTime("12:00");
					this.currentState = State.confirm;
					break;					
				case 4:
					this.bookingProcess.setTime("15:00");
					this.currentState = State.confirm;
					break;
				case 5:
					this.bookingProcess.setTime("18:00");
					this.currentState = State.confirm;
					break;
				case 6:
					this.bookingProcess.setTime("12:00");
					this.currentState = State.confirm;
					break;
				default:
					this.bookingProcess.setTime("06:00");
					this.currentState = State.confirm;
					break;
				}
				printConfirmationRequest();
				
			} else {
				System.out.println("Invalid input, try it again");
				printTimeSelection();
			}
			break;
		case confirm:
			if("yes".equals(command.toLowerCase()))
			{
				this.bookingProcess.createNewBooking();
				printMenu();
				this.currentState = State.menu;
			} else {
				System.out.println("Booking aborted");
				printBookingMenu();
				this.currentState = State.booking;
			}
			break;
		case cancelBooking:
			if(this.editMap.containsKey("T"+command)){
				SharedPreferences sharedPreferences = new SharedPreferences();
				String key = (String) this.editMap.get("T" + command);
				Transition transitionForId = getTransitionForId(key, sharedPreferences);
				handleTransition(sharedPreferences, transitionForId);
				System.out.println("The cancel request for the booking has been created and will be processed as soon as possible \n");
			}
			printMenu();
			this.currentState = State.menu;
			break;
		default:
			break;
		}
	}

	/**
	 * Determines if the chosen {@link Transition} is cancel able or not and if
	 * yes it will create a new {@link CancelBookingTransition}
	 * 
	 * @param sharedPreferences
	 * @param transition
	 */
	private void handleTransition(SharedPreferences sharedPreferences, Transition transition) {
		if(TransitionState.PENDING.equals(transition.getTransitionState())){
			if(transition.getBooking().getTravelDate().after(THREE_DAYS)) { // no booking cancelation if flight < three days 
				createAndSaveCancelBooking(transition.getBooking(), sharedPreferences);
			} else {
				System.err.println("No cancelation of bookings nearer than 3 days");
			}
		} else {
			System.err.println("The selected booking cannot be canceled");
		}
	}

	private void createAndSaveCancelBooking(Booking booking, SharedPreferences sharedPreferences) {
		CancelBookingTransition cancelTransition = new CancelBookingTransition(booking);
		sharedPreferences.putString("T" + cancelTransition.getTransitionId(), cancelTransition.toString());
		sharedPreferences.commit();
	}
	
	private Transition getTransitionForId(String selectedItemTag, SharedPreferences sharedPreferences) {
		String serializedTransition = sharedPreferences.getString(selectedItemTag, "");
		Object transition = deserialize(serializedTransition);
		if(transition instanceof NewBookingTransition) {
			return (Transition)transition;
		}
		return null;
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
					System.err.println("Cannot deserialize, discarding package");
				}
			} else {
				System.err.println("No valid class identifier found, discarding package");
			}
		}
		return readObject;
	}
	
	private void printBookings() {
		SharedPreferences pref = new SharedPreferences();
		this.editMap.clear();
		Map<?, ?> all = pref.getAll();
		Set<String> keySet = (Set<String>) all.keySet();
		int i = 1;
		for (String key : keySet) {
			this.editMap.put("T" + i, key);
			System.out.println(i + ". " + key);
			i++;
		}
		System.out.println(i+1 + ". abort");
	}


	private void printTimeSelection() {
		System.out.println("Please choose at what time you want to travel");
		System.out.println("1. 06:00h");
		System.out.println("2. 09:00h");
		System.out.println("3. 12:00h");
		System.out.println("4. 15:00h");
		System.out.println("5. 18:00h");
		System.out.println("6. 21:00h");
	}
	
	private void printConfirmationRequest() {
		System.out.println(this.bookingProcess.printCurrentBookings());
		System.out.println("Is this correct (yes/no): ");
	}

	private Date parseInputDate(String command) {
		DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
		Date parse = null;
		try {
			parse = df.parse(command);
		} catch (ParseException e) {
			return null;
		}
		return parse;
	}

	private void printBookingMenu() {
		System.out.println("===== Booking Encampment -> City =====");
		System.out.println("Please specify the date you want to travel (DD.MM.YYYY)");
		System.out.println(": ");
	}

	private void printNotYetImplemented() {
		System.out.println("Not yet implemented, please choose another option");
		printMenu();
	}

	private void printMenu() {
		System.out.println("====== Menu ========");
		System.out.println("What do you want to do? \n" 
							+ "1. Book a flight encampment -> city \n"
							+ "2. Book a flight city -> encampment \n"
							+ "3. Cancel a booking \n" 
							+ "4. exit");
		System.out.println(": ");
	}
}