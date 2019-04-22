package comp1206.sushi.common;

import comp1206.sushi.common.Postcode;
import comp1206.sushi.common.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User extends Model {
	
	private String name;
	private String password;
	private String address;
	private Postcode postcode;
	private Map<Dish, Number> basket = new HashMap<>();
	private List<Order> orders = new ArrayList<>();

	public User(String username, String password, String address, Postcode postcode) {
		this.name = username;
		this.password = password;
		this.address = address;
		this.postcode = postcode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Number getDistance() {
		return postcode.getDistance();
	}

	public Postcode getPostcode() {
		return this.postcode;
	}
	
	public void setPostcode(Postcode postcode) {
		this.postcode = postcode;
	}

	public void setAddress(String address) { this.address = address; }

	public String getAddress() { return this.address; }

	public char[] getPassword() { return this.password.toCharArray(); }

	public void addToBasket(Dish dish, int quantity) { basket.put(dish, quantity); }

	public Map<Dish, Number> getBasket() { return this.basket; }

	public void placeOrder(Order order) { this.orders.add(order); }

	public List<Order> getOrders() { return this.orders; }
}
