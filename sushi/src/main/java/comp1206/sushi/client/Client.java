package comp1206.sushi.client;

import java.util.*;

import comp1206.sushi.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Client implements ClientInterface
{
	private static final Logger logger = LogManager.getLogger("Client");

	private Restaurant restaurant;
	private User loggedInUser = null;
	private final ArrayList<User> users = new ArrayList<>();
	private final ArrayList<Postcode> postcodes = new ArrayList<>();
	private final ArrayList<Dish> dishes = new ArrayList<>();
	private final ArrayList<UpdateListener> listeners = new ArrayList<>();
	private final ClientComms comms = new ClientComms(this);
	
	public Client()
	{
		logger.info("Starting up client...");

		comms.start();

		comms.sendMessage("LOAD DATA", null);

//		Postcode restaurantPostcode = new Postcode("SO17 1BJ");
//
//		restaurant = new Restaurant("Mock Restaurant", restaurantPostcode);
//
//		Postcode postcode1 = new Postcode("SO17 1TJ");
//		Postcode postcode2 = new Postcode("SO17 1BX");
//		Postcode postcode3 = new Postcode("SO17 2NJ");
//		Postcode postcode4 = new Postcode("SO17 1TW");
//		Postcode postcode5 = new Postcode("SO17 2LB");
//
//		postcodes.add(restaurantPostcode);
//		postcodes.add(postcode1);
//		postcodes.add(postcode2);
//		postcodes.add(postcode3);
//		postcodes.add(postcode4);
//		postcodes.add(postcode5);
//
//		User user1 = new User("khadgar", "theb3stw1zard", "1 Dalaran Lane", postcode3);
//		User user2 = new User("jaina", "daughter0fth3s3a", "56 Boralus Avenue", postcode5);
//		User user3 = new User("azshara", "queen", "The Eternal Palace", postcode1);
//
//		users.add(user1);
//		users.add(user2);
//		users.add(user3);
//
//		Dish dish1 = new Dish("Dish 1","Dish 1",1,1,10);
//		Dish dish2 = new Dish("Dish 2","Dish 2",2,1,10);
//		Dish dish3 = new Dish("Dish 3","Dish 3",3,1,10);
//
//		dishes.add(dish1);
//		dishes.add(dish2);
//		dishes.add(dish3);
	}
	
	@Override
	public Restaurant getRestaurant()
	{
		return restaurant;
	}
	
	@Override
	public String getRestaurantName()
	{
		return restaurant.getName();
	}

	@Override
	public Postcode getRestaurantPostcode()
	{
		return restaurant.getLocation();
	}
	
	@Override
	public User register(String username, String password, String address, Postcode postcode)
	{
		// First check that a user with that username does not already exist.
		for (User u : this.users)
		{
			if (u.getName().toLowerCase().equals(username.toLowerCase()))
				return null;
		}

		// If it does not, create a new user and return it.
		User user = new User(username, password, address, postcode);
		this.users.add(user);
		comms.sendMessage("REGISTER USER", user);
		return user;
	}

	@Override
	public User login(String username, String password)
	{
		// Loop through each User object in the users array.
		for (User user : this.users)
		{
			// If the user with the right username is found, check to see if the password is what is expected.
			// In the case that it is, return the user, otherwise return null.
			if (user.getName().equals(username))
			{
				if (Arrays.equals(user.getPassword(), password.toCharArray()))
				{
					loggedInUser = user;
					comms.sendMessage("LOGIN", user);
					return user;
				}
				else
					return null;
			}
		}

		// Return null if the username can't be found at all.
		return null;
	}

	public User getLoggedInUser() { return loggedInUser; }

	public List<User> getUsers() { return this.users; }

	@Override
	public List<Postcode> getPostcodes()
	{
		return this.postcodes;
	}

	@Override
	public List<Dish> getDishes()
	{
		return this.dishes;
	}

	@Override
	public String getDishDescription(Dish dish)
	{
		return dish.getName();
	}

	@Override
	public Number getDishPrice(Dish dish)
	{
		return dish.getPrice();
	}

	@Override
	public Map<Dish, Number> getBasket(User user)
	{
		return user.getBasket();
	}

	@Override
	public Number getBasketCost(User user)
	{
		float cost = 0;

		// Go through each Dish in the user's basket.
		for (Map.Entry entry : user.getBasket().entrySet())
		{
			Dish dish = (Dish)entry.getKey();

			// Add the current cost to the Dish price times by the quantity of dishes.
			cost = cost + (dish.getPrice().floatValue() * (int)entry.getValue());
		}

		return cost;
	}

	@Override
	public void addDishToBasket(User user, Dish dish, Number quantity)
	{
		user.addToBasket(dish, quantity.intValue());
		this.notifyUpdate();
	}

	@Override
	public void updateDishInBasket(User user, Dish dish, Number quantity)
	{
		if (quantity.intValue() == 0)
			user.getBasket().remove(dish);
		else
			user.getBasket().put(dish, quantity);

		this.notifyUpdate();
	}

	@Override
	public Order checkoutBasket(User user)
	{
		Order order = new Order();
		Map<Dish, Number> basket = new HashMap<>();
		basket.putAll(user.getBasket());
		order.setOrderedDishes(basket);
		user.placeOrder(order);
		order.setStatus("Preparing");
		comms.sendMessage("NEW ORDER", order, user);
		this.notifyUpdate();
		return order;
	}

	@Override
	public void clearBasket(User user)
	{
		user.getBasket().clear();
		this.notifyUpdate();
	}

	@Override
	public List<Order> getOrders(User user)
	{
		return user.getOrders();
	}

	@Override
	public boolean isOrderComplete(Order order)
	{
		return order.isComplete();
	}

	@Override
	public String getOrderStatus(Order order)
	{
		return order.getStatus();
	}

	@Override
	public Number getOrderCost(Order order)
	{
		double cost = 0.0;

		// Go through each Dish in the Order.
		for (Map.Entry entry : order.getOrderedDishes().entrySet())
		{
			Dish dish = (Dish)entry.getKey();

			// Add the current cost to the Dish price times by the quantity of dishes.
			cost = cost + (dish.getPrice().doubleValue() * (int)entry.getValue());
		}

		return cost;
	}

	@Override
	public void cancelOrder(Order order)
	{
		order.cancelOrder();
		comms.sendMessage("CANCEL ORDER", order, loggedInUser);
		this.notifyUpdate();
	}

	public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

	@Override
	public void addUpdateListener(UpdateListener listener)
	{
		this.listeners.add(listener);
	}

	@Override
	public void notifyUpdate()
	{
		this.listeners.forEach(listener -> listener.updated(new UpdateEvent()));
	}
}