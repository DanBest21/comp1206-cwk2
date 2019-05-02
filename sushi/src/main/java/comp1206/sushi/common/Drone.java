package comp1206.sushi.common;

import comp1206.sushi.server.ServerComms;

import java.util.*;

public class Drone extends Model implements Runnable
{
	private Number speed;
	private Number progress;
	
	private Number capacity;
	private Number battery;
	
	private String status;
	
	private Postcode source;
	private Postcode destination;

	private final Stock stock;
	private final ServerComms comms;
	private final List<Ingredient> ingredients;
	private final List<Order> orders;
	private final List<User> users;
	private final Restaurant restaurant;
	private static Map<Ingredient, Number> restocksInProgress = new HashMap<>();
	private static boolean readyToCheck = true;

	public Drone(Number speed, Stock stock, ServerComms comms, List<Ingredient> ingredients, List<Order> orders, List<User> users, Restaurant restaurant)
	{
		this.setSpeed(speed);
		this.setCapacity(1);
		this.setBattery(100);

		this.stock = stock;
		this.comms = comms;
		this.ingredients = ingredients;
		this.orders = orders;
		this.users = users;
		this.restaurant = restaurant;
	}

	public void run()
	{
		while (!Thread.currentThread().isInterrupted())
		{
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

	public Number getBattery() {
		return battery;
	}

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

	private boolean canRestockIngredient(Ingredient ingredient)
	{
		synchronized (stock)
		{
			try
			{
				if (!readyToCheck)
				{
					stock.wait();
				}
			}
			catch (InterruptedException ex)
			{
				return false;
			}

			readyToCheck = false;
		}

		if (stock.getStock(ingredient).intValue() + (restocksInProgress.get(ingredient).intValue() * ingredient.getRestockAmount().intValue())
				>= ingredient.getRestockThreshold().intValue() || Thread.currentThread().isInterrupted())
		{
			return false;
		}

		return true;
	}

	private void restockIngredient(Ingredient ingredient)
	{
		restocksInProgress.put(ingredient, restocksInProgress.get(ingredient).intValue() + 1);

		fly(ingredient.getSupplier());

		stock.setStock(ingredient, stock.getStock(ingredient).intValue() + ingredient.getRestockAmount().intValue());

		restocksInProgress.put(ingredient, restocksInProgress.get(ingredient).intValue() - 1);
	}

	private void deliverOrders() throws NoSuchElementException
	{
		synchronized (orders)
		{
			for (Order order : orders)
			{
				if (Thread.currentThread().isInterrupted())
					return;

				if (order.isComplete() || order.isCancelled())
					continue;

				deliverOrder(order);
			}
		}
	}

	private void deliverOrder(Order order) throws NoSuchElementException
	{
		order.deliverOrder();
		User user = findCustomer(order);

		if (user != null)
			fly(order, user);
		else
			throw new NoSuchElementException("User for " + order.getName() + " order could not be found.");
	}

	// TODO: Solve the synchronisation issue with loading the user from the server simultaneously.
	private User findCustomer(Order order)
	{
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

	private void fly(Supplier supplier)
	{
		setStatus("Retrieving ingredients from " + supplier.getName());
		setSource(restaurant.getLocation());
		setDestination(supplier.getPostcode());
		fly();

		setStatus("Returning to " + restaurant.getName());
		setSource(supplier.getPostcode());
		setDestination(restaurant.getLocation());
		fly();
	}

	private void fly(Order order, User user)
	{
		setStatus("Delivering order " + order.getName() + " to " + user.getName());
		setSource(restaurant.getLocation());
		setDestination(user.getPostcode());
		order.setStatus("Out for delivery");

		synchronized (comms)
		{
			comms.sendMessage("CHANGE ORDER STATUS", order, user);
		}

		fly();

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

	private void fly()
	{
		Postcode source = getSource();
		Postcode destination = getDestination();

		double distance = Math.abs(destination.getDistance().doubleValue() - source.getDistance().doubleValue());
		float speed = getSpeed().floatValue();
		double currentDistance = 0.0;

		while (currentDistance < distance)
		{
			currentDistance = currentDistance + speed;

			setProgress((currentDistance / distance) * 100);

			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException ex)
			{
				ex.printStackTrace();
			}
		}
	}
}