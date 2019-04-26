package comp1206.sushi.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import comp1206.sushi.common.Order;

public class Order extends Model {

	private String status;
	private Map<Dish, Number> orderedDishes = new HashMap<>();
	private boolean isComplete = false;
	private boolean isCancelled = false;
	
	public Order() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/YYYY HH:mm:ss");  
		LocalDateTime now = LocalDateTime.now();  
		this.name = dtf.format(now);
	}

	public Number getDistance() {
		return 1;
	}

	@Override
	public String getName() {
		return this.name;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		notifyUpdate("status",this.status,status);
		this.status = status;
	}

	public Map<Dish, Number> getOrderedDishes() { return this.orderedDishes; }

	public void setOrderedDishes(Map<Dish, Number> orderedDishes) { this.orderedDishes = orderedDishes; }

	public boolean isComplete() { return isComplete; }

	public void completeOrder() { isComplete = true; }

	public boolean isCancelled() { return isCancelled; }

	public void cancelOrder() { isCancelled = true; }
}
