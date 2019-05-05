package comp1206.sushi.common;

import comp1206.sushi.server.DataPersistence;
import comp1206.sushi.server.ServerComms;

import java.io.Serializable;
import java.util.*;

public class Drone extends Model implements Runnable, Serializable
{
	private Number speed;
	private Number progress;
	
	private Number capacity;
	private Number battery;
	
	private String status;
	
	private Postcode source;
	private Postcode destination;

	private final Stock stock;
	private transient ServerComms comms;
	private final List<Ingredient> ingredients;
	private final List<Order> orders;
	private final List<User> users;
	private final Restaurant restaurant;
	private static Map<Ingredient, Number> restocksInProgress = new HashMap<>();
	private static boolean readyToCheck = true;
	private transient DataPersistence dataPersistence;

	private String currentOrder = "";

	private static final double BATTERY_USAGE_RATE = 0.25;
	private static final int DRONE_CAPACITY = 10000;

	public Drone(Number speed, Stock stock, ServerComms comms, List<Ingredient> ingredients, List<Order> orders, List<User> users, Restaurant restaurant, DataPersistence dataPersistence)
	{
		this.setSpeed(speed);
		this.setCapacity(DRONE_CAPACITY);
		this.setBattery(100.0);
		this.setSource(restaurant.getLocation());
		this.setDestination(restaurant.getLocation());
		this.setProgress(0.00);

		this.stock = stock;
		this.comms = comms;
		this.ingredients = ingredients;
		this.orders = orders;
		this.users = users;
		this.restaurant = restaurant;
		this.dataPersistence = dataPersistence;
	}

	public void run()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			this.setSource(restaurant.getLocation());
			this.setDestination(restaurant.getLocation());
			this.setProgress(0.00);
			setStatus("Idle");

			if (stock.getRestockingIngredientsEnabled())
			{
				try
				{
					restockIngredients();
				}
				catch (ConcurrentModificationException ex)
				{
					// Do nothing
				}
				catch (NoSuchElementException ex)
				{
					ex.printStackTrace();
				}
			}

			deliverOrders();
		}
	}

	public Number getSpeed() {
		return speed;
	}

	public Number getProgress() {
		return progress;
	}
	
	public void setProgress(Number progress) {
		this.progress = progress;
	}
	
	public void setSpeed(Number speed) {
		this.speed = speed;
	}
	
	@Override
	public String getName() {
		return "Drone (" + getSpeed() + " speed)";
	}

	public Postcode getSource() {
		return source;
	}

	public void setSource(Postcode source) {
		this.source = source;
	}

	public Postcode getDestination() {
		return destination;
	}

	public void setDestination(Postcode destination) {
		this.destination = destination;
	}

	public Number getCapacity() {
		return capacity;
	}

	public void setCapacity(Number capacity) {
		this.capacity = capacity;
	}

	public Number getBattery() { return battery; }

	public void setBattery(Number battery) {
		this.battery = battery;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status)
	{
		notifyUpdate("status",this.status,status);
		this.status = status;
	}

	private void restockIngredients() throws ConcurrentModificationException
	{
		for (Ingredient ingredient : ingredients)
		{
			if (!restocksInProgress.containsKey(ingredient))
				restocksInProgress.put(ingredient, 0);

			if (Thread.currentThread().isInterrupted())
				return;

			while (canRestockIngredient(ingredient))
			{
				synchronized (stock)
				{
					readyToCheck = true;
					stock.notify();
				}

				restockIngredient(ingredient);
			}

			synchronized (stock)
			{
				readyToCheck = true;
				stock.notify();
			}
		}
	}

	private Map<Ingredient, Number> restockIngredients(int load, Map<Ingredient, Number> loadedIngredients, Postcode source) throws ConcurrentModificationException
	{
		for (Ingredient ingredient : ingredients)
		{
			if (!restocksInProgress.containsKey(ingredient))
				restocksInProgress.put(ingredient, 0);

			if (Thread.currentThread().isInterrupted())
				 return null;

			if ((getCapacity().intValue() - load) < ingredient.getRestockAmount().intValue())
				continue;

			while (canRestockIngredient(ingredient))
			{
				synchronized (stock)
				{
					readyToCheck = true;
					stock.notify();
				}

				return restockIngredient(ingredient, load, loadedIngredients, source);
			}

			synchronized (stock)
			{
				readyToCheck = true;
				stock.notify();
			}
		}

		return loadedIngredients;
	}

	private boolean canRestockIngredient(Ingredient ingredient)
	{
		synchronized (stock)
		{
			try
			{
				Random rand = new Random();

				Thread.sleep(rand.nextInt(100));

				if (!readyToCheck)
				{
					stock.wait();
				}
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
				return false;
			}

			readyToCheck = false;
		}

		if (stock.getStock(ingredient).intValue() + (restocksInProgress.get(ingredient).intValue() * (calculateLoad(ingredient) / ingredient.getWeight().intValue()))
				>= ingredient.getRestockThreshold().intValue() || Thread.currentThread().isInterrupted())
		{
			return false;
		}

		return true;
	}

	private void restockIngredient(Ingredient ingredient)
	{
		int load = Math.min(calculateLoad(ingredient), (ingredient.getRestockThreshold().intValue() * ingredient.getWeight().intValue()) -
				(restocksInProgress.get(ingredient).intValue() * (calculateLoad(ingredient))));

		restocksInProgress.put(ingredient, restocksInProgress.get(ingredient).intValue() + 1);

		Map<Ingredient, Number> loadedIngredients = fly(ingredient, load);

		for (Map.Entry entry : loadedIngredients.entrySet())
		{
			Ingredient i = (Ingredient)entry.getKey();
			Number ingredientLoad = (Number)entry.getValue();

			stock.setStock(i, stock.getStock(i).intValue() + (ingredientLoad.intValue() / ingredient.getWeight().intValue()));

			restocksInProgress.put(i, restocksInProgress.get(i).intValue() - 1);
		}

		// Tell the server to back itself up when the stock level has changed.
		dataPersistence.backupServer();
	}

	private Map<Ingredient, Number> restockIngredient(Ingredient ingredient, int load, Map<Ingredient, Number> loadedIngredients, Postcode source)
	{
		load = Math.min(calculateLoad(ingredient) - load, (ingredient.getRestockThreshold().intValue() * ingredient.getWeight().intValue()) -
				(restocksInProgress.get(ingredient).intValue() * (calculateLoad(ingredient))));

		restocksInProgress.put(ingredient, restocksInProgress.get(ingredient).intValue() + 1);

		return fly(ingredient, load, loadedIngredients, source);
	}

	private void deliverOrders() throws NoSuchElementException
	{
		Order order = null;

		synchronized (orders)
		{
			for (Order o : orders)
			{
				if (Thread.currentThread().isInterrupted())
					return;

				if (o.isComplete() || o.isCancelled() || o.isOutForDelivery() || !orderReady(o))
					continue;

				o.deliverOrder();
				order = o;
			}
		}

		if (order != null)
			deliverOrder(order);
	}

	private void deliverOrder(Order order) throws NoSuchElementException
	{
		User user = findCustomer(order);

		for (Map.Entry entry : order.getOrderedDishes().entrySet())
		{
			Dish dish = (Dish)entry.getKey();
			Number quantity = (Number)entry.getValue();

			stock.setStock(dish, stock.getStock(dish).intValue() - quantity.intValue());
		}

		if (user != null)
			fly(order, user);
		else
			throw new NoSuchElementException("User for " + order.getName() + " order could not be found.");
	}

	private User findCustomer(Order order)
	{
		try
		{
			Thread.sleep(100);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

		synchronized (users)
		{
			for (User user : users)
			{
				for (Order o : user.getOrders())
				{
					if (o.getName().equals(order.getName()))
						return user;
				}
			}

			return null;
		}
	}

	private Map<Ingredient, Number> fly(Ingredient ingredient, int load)
	{
		Map<Ingredient, Number> loadedIngredients = new HashMap<>();

		return fly(ingredient, load, loadedIngredients, restaurant.getLocation());
	}

	private Map<Ingredient, Number> fly(Ingredient ingredient, int load, Map<Ingredient, Number> loadedIngredients, Postcode source)
	{
		setStatus("Retrieving " + ingredient.getName() + " from " + ingredient.getSupplier().getName());
		setSource(source);
		setDestination(ingredient.getSupplier().getPostcode());
		fly();

		loadedIngredients.put(ingredient, load);

		int totalLoad = 0;

		for (Map.Entry entry : loadedIngredients.entrySet())
		{
			Number ingredientLoad = (Number)entry.getValue();
			totalLoad = totalLoad + ingredientLoad.intValue();
		}

		if (totalLoad < getCapacity().intValue())
		{
			loadedIngredients = restockIngredients(totalLoad, loadedIngredients, ingredient.getSupplier().getPostcode());
		}
		else
		{
			setStatus("Returning to " + restaurant.getName() + " with ingredients");
			setSource(ingredient.getSupplier().getPostcode());
			setDestination(restaurant.getLocation());
			fly();
		}

		return loadedIngredients;
	}

	private void fly(Order order, User user)
	{
		currentOrder = order.getName();

		setStatus("Delivering order " + order.getName() + " to " + user.getName());
		setSource(restaurant.getLocation());
		setDestination(user.getPostcode());
		order.setStatus("Out for delivery");

		synchronized (comms)
		{
			comms.sendMessage("CHANGE ORDER STATUS", order, user);
		}

		if (fly())
		{
			currentOrder = "";

			order.setStatus("Complete");
			order.completeOrder();
			setStatus("Returning to " + restaurant.getName());
			setSource(user.getPostcode());
			setDestination(restaurant.getLocation());

			synchronized (comms)
			{
				comms.sendMessage("CHANGE ORDER STATUS", order, user);
			}

			fly();
		}
	}

	private boolean fly()
	{
		Postcode source = getSource();
		Postcode destination = getDestination();

		double distance = Math.abs(destination.getDistance().doubleValue() - source.getDistance().doubleValue());
		float speed = getSpeed().floatValue();

		return fly(distance, speed);
	}

	private boolean fly(double distance, float speed)
	{
		double currentDistance = 0.0;

		while (currentDistance < distance)
		{
			if (getBattery().doubleValue() <= 0.0)
			{
				if (getDestination().equals(restaurant.getLocation()))
				{
					returnForRecharge();
				}
				else
				{
					String status = getStatus();

					returnForRecharge(currentDistance, speed);
					rechargeBattery();

					setStatus(status);

					currentDistance = 0.0;
				}
			}

			for (Order order : orders)
			{
				if (currentOrder.equals(""))
					break;

				if (order.getName().equals(currentOrder) && order.isCancelled())
				{
					cancelOrder(currentDistance, speed);
					return false;
				}
			}

			currentDistance = currentDistance + speed;

			double progress = (((currentDistance / distance) * 100) > 100) ? 100.0 : (currentDistance / distance) * 100;
			progress = Double.valueOf(String.format("%.2f", progress));

			setProgress(progress);

			if (getBattery().doubleValue() > 0.0)
				setBattery(getBattery().doubleValue() - BATTERY_USAGE_RATE);

			// Tell the server to back itself when the progress of the drone has changed.
			dataPersistence.backupServer();

			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}

		if (getBattery().doubleValue() <= 0.0)
			rechargeBattery();

		return true;
	}

	private void cancelOrder(double distance, float speed)
	{
		currentOrder = "";

		setStatus("Order cancelled - returning to " + restaurant.getName());
		setDestination(restaurant.getLocation());

		fly(distance, speed);
	}

	public void recoverDrone(ServerComms comms, DataPersistence dataPersistence)
	{
		this.comms = comms;
		this.dataPersistence = dataPersistence;
	}

	private boolean orderReady(Order order)
	{
		for (Map.Entry entry : order.getOrderedDishes().entrySet())
		{
			Dish dish = (Dish)entry.getKey();
			Number quantity = (Number)entry.getValue();
			Number stockAmount = stock.getStock(dish);

			if (stockAmount.intValue() < quantity.intValue())
				return false;
		}

		return true;
	}

	private void returnForRecharge()
	{
		setStatus("Out of battery, returning to " + restaurant.getName() + " to recharge");
	}

	private void returnForRecharge(double distance, float speed)
	{
		Postcode destination = getDestination();

		setStatus("Out of battery, returning to " + restaurant.getName() + " to recharge");
		setDestination(restaurant.getLocation());
		setSource(restaurant.getLocation());

		fly(distance, speed);

		setDestination(destination);
		setSource(restaurant.getLocation());
	}

	private void rechargeBattery()
	{
		setStatus("Recharging battery");

		try
		{
			Thread.sleep(30000);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

		setBattery(100.0);
	}

	private int calculateLoad(Ingredient ingredient)
	{
		int capacity = getCapacity().intValue();
		double weight = ingredient.getWeight().doubleValue();
		int restockAmount = ingredient.getRestockAmount().intValue();
		int restockThreshold = ingredient.getRestockThreshold().intValue();
		int load = 0;

		while ((load < capacity) && (load < (restockThreshold * weight)))
		{
			load = load + (int)Math.round(restockAmount * weight);
		}

		return load;
	}
}