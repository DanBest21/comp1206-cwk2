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
	private final List<Ingredient> ingredients;
	private final List<Order> orders;
	private final List<User> users;
	private final Restaurant restaurant;
    private transient ServerComms comms;
	private transient static final Map<Ingredient, Number> RESTOCKS_IN_PROGRESS = new HashMap<>();
	private transient static boolean readyToCheck = true;
	private transient DataPersistence dataPersistence;

	private String currentOrder = "";

	private static final double BATTERY_USAGE_RATE = 0.25;
	private static final int DRONE_CAPACITY = 10000;
	private static final long RECHARGE_TIME = 120000;

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

    // run(): Primary method called by the Thread when started.
	public void run()
	{
		// Stop the thread if it's been interrupted.
	    while (!Thread.currentThread().isInterrupted())
		{
			this.setSource(restaurant.getLocation());
			this.setDestination(restaurant.getLocation());
			this.setProgress(0.00);
			setStatus("Idle");

			// If the restock ingredients setting is enabled.
			if (stock.getRestockingIngredientsEnabled())
			{
				// Try to restock ingredients.
			    try
				{
					restockIngredients();
				}
				catch (ConcurrentModificationException ex)
				{
                    // Do nothing since another thread is working that will fix this issue.
				}
				catch (NoSuchElementException ex)
				{
					ex.printStackTrace();
				}
			}

			// If there are no ingredients to restock, then try to deliver orders.
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

	// restockIngredients(): Method that loops through each ingredient and checks if it is below the restock threshold, and then go to collect some from a Supplier if so.
	private void restockIngredients() throws ConcurrentModificationException
	{
		for (Ingredient ingredient : ingredients)
		{
            // Add the key if it's not already in the RESTOCKS_IN_PROGRESS map.
		    if (!RESTOCKS_IN_PROGRESS.containsKey(ingredient))
				RESTOCKS_IN_PROGRESS.put(ingredient, 0);

		    // Stop the thread if it's been interrupted.
			if (Thread.currentThread().isInterrupted())
				return;

			// While the ingredient can be restocked:
			while (canRestockIngredient(ingredient))
			{
                // Notify the other threads that they can now carry on with their check to see if an ingredient can be restocked.
			    synchronized (stock)
				{
					readyToCheck = true;
					stock.notify();
				}

				// Restock the ingredient.
				restockIngredient(ingredient);
			}

            // Notify the other threads that they can now carry on with their check to see if an ingredient can be restocked.
			synchronized (stock)
			{
				readyToCheck = true;
				stock.notify();
			}
		}
	}

	// restockIngredients(Map<Ingredient, Number>, int, Postcode): Recursive version of the restockIngredients function that is used to go to multiple suppliers along the same trip.
	private Map<Ingredient, Number> restockIngredients(Map<Ingredient, Number> loadedIngredients, int load, Postcode source) throws ConcurrentModificationException
	{
		for (Ingredient ingredient : ingredients)
		{
            // Add the key if it's not already in the RESTOCKS_IN_PROGRESS map.
		    if (!RESTOCKS_IN_PROGRESS.containsKey(ingredient))
				RESTOCKS_IN_PROGRESS.put(ingredient, 0);

            // Stop the thread if it's been interrupted.
			if (Thread.currentThread().isInterrupted())
				 return null;

			// If the drone is unable to carry one restock package with its remaining load, as determined by the restock amount, continue.
			if ((getCapacity().intValue() - load) < ingredient.getRestockAmount().intValue())
				continue;

            // While the ingredient can be restocked:
			while (canRestockIngredient(ingredient))
			{
                // Notify the other threads that they can now carry on with their check to see if an ingredient can be restocked.
			    synchronized (stock)
				{
					readyToCheck = true;
					stock.notify();
				}

                // Restock the ingredient and return the loadedIngredients map.
				return restockIngredient(ingredient, load, loadedIngredients, source);
			}

            // Notify the other threads that they can now carry on with their check to see if an ingredient can be restocked.
			synchronized (stock)
			{
				readyToCheck = true;
				stock.notify();
			}
		}

		return loadedIngredients;
	}

	// canRestockIngredient(Ingredient): Method that determines if an ingredient can be restocked.
	private boolean canRestockIngredient(Ingredient ingredient)
	{
		// Synchronise this based on the stock object.
	    synchronized (stock)
		{
			try
			{
				Random rand = new Random();

				Thread.sleep(rand.nextInt(100));

				// If the thread is not ready to check if a dish can be prepared, wait.
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

            // Set the readyToCheck boolean variable to false whilst this thread is checking if this ingredient can be prepared.
			readyToCheck = false;
		}

		// If the stock level of an ingredient plus any ingredients that are already coming via other drones is higher than or equal to the restock threshold, or the thread has been interrupted, return false.
		if (stock.getStock(ingredient).intValue() + (RESTOCKS_IN_PROGRESS.get(ingredient).intValue() * (calculateMaxLoad(ingredient) / ingredient.getWeight().intValue()))
				>= ingredient.getRestockThreshold().intValue() || Thread.currentThread().isInterrupted())
		{
			return false;
		}

		// Otherwise the drone can collect the ingredient so return true.
		return true;
	}

	// restockIngredient(Ingredient): Method that restocks the ingredient based on the capacity of the drone, and the weight of the ingredient.
	private void restockIngredient(Ingredient ingredient)
	{
	    int load = 0;

	    synchronized (RESTOCKS_IN_PROGRESS)
        {
            // Determine the load - either the max load possible of that ingredient on the drone, or the max load possible given the restock threshold minus the
            // load of the current drone collections of the specific ingredient, rounded to the nearest restock amount possible.
            load = Math.min(calculateMaxLoad(ingredient), Math.round(((ingredient.getRestockThreshold().intValue() * ingredient.getWeight().intValue()) -
                    (RESTOCKS_IN_PROGRESS.get(ingredient).intValue() * calculateMaxLoad(ingredient)))
                            / ingredient.getRestockAmount().intValue()) * ingredient.getRestockAmount().intValue());

            // Add one to the RESTOCKS_IN_PROGRESS counter for this ingredient.
            RESTOCKS_IN_PROGRESS.put(ingredient, RESTOCKS_IN_PROGRESS.get(ingredient).intValue() + 1);
        }

		// Call the fly() method and put the returning map into a variable.
		Map<Ingredient, Number> loadedIngredients = fly(ingredient, load);

		// For each loaded ingredient, set the stock of the ingredient to the current stock plus the amount of ingredients returned by the drone
        // (the load weight divided by the weight of a singular ingredient).
		for (Map.Entry entry : loadedIngredients.entrySet())
		{
			Ingredient i = (Ingredient)entry.getKey();
			Number ingredientLoad = (Number)entry.getValue();

			stock.setStock(i, stock.getStock(i).intValue() + (ingredientLoad.intValue() / ingredient.getWeight().intValue()));

			// Subtract one from the RESTOCKS_IN_PROGRESS counter for this ingredient.
			RESTOCKS_IN_PROGRESS.put(i, RESTOCKS_IN_PROGRESS.get(i).intValue() - 1);
		}

		// Tell the server to back itself up when the stock level has changed.
		dataPersistence.backupServer();
	}

	// restockIngredient(Ingredient, int, Map<Ingredient, Number>, Postcode): Recursive version of the restockIngredient() method that is used when travelling to multiple suppliers on a singular trip.
	private Map<Ingredient, Number> restockIngredient(Ingredient ingredient, int load, Map<Ingredient, Number> loadedIngredients, Postcode source)
	{
	    synchronized (RESTOCKS_IN_PROGRESS)
        {
            // Determine the load - either the max load possible of that ingredient on the drone minus the current load, or the max load possible given the restock threshold minus the
            // load of the current drone collections of the specific ingredient, rounded to the nearest restock amount possible.
            load = Math.min(calculateMaxLoad(ingredient) - load, Math.round(((ingredient.getRestockThreshold().intValue() * ingredient.getWeight().intValue()) -
                    (RESTOCKS_IN_PROGRESS.get(ingredient).intValue() * (calculateMaxLoad(ingredient))))
                            / ingredient.getRestockAmount().intValue()) * ingredient.getRestockAmount().intValue());

            // Add one to the RESTOCKS_IN_PROGRESS counter for this ingredient.
            RESTOCKS_IN_PROGRESS.put(ingredient, RESTOCKS_IN_PROGRESS.get(ingredient).intValue() + 1);
        }

        // Call the fly() function and return the Map object it returns.
		return fly(ingredient, load, loadedIngredients, source);
	}

	// deliverOrders(): Method that checks to see if any order can be delivered.
	private void deliverOrders() throws NoSuchElementException
	{
		Order order = null;

		// Synchronise on the orders object.
		synchronized (orders)
		{
			// For every order in the orders list:
		    for (Order o : orders)
			{
				// Return if the thread has been interrupted.
			    if (Thread.currentThread().isInterrupted())
					return;

			    // If the order is complete, cancelled, already out for delivery, or not yet ready to go out for delivery, ignore it.
				if (o.isComplete() || o.isCancelled() || o.isOutForDelivery() || !orderReady(o))
					continue;

				// Otherwise set the isOutForDelivery boolean to true and set it to the order object.
				o.deliverOrder();
				order = o;
			}
		}

		// If the order object is not null, then deliver the order.
		if (order != null)
			deliverOrder(order);
	}

	// deliverOrder(Order): Function that simulates the delivery of the passed order.
	private void deliverOrder(Order order) throws NoSuchElementException
	{
		User user = findCustomer(order);

		// For every ordered dish, reduce the stock based on the quantity ordered.
		for (Map.Entry entry : order.getOrderedDishes().entrySet())
		{
			Dish dish = (Dish)entry.getKey();
			Number quantity = (Number)entry.getValue();

			stock.setStock(dish, stock.getStock(dish).intValue() - quantity.intValue());
		}

		// If the customer could be found, simulate the flying process.
		if (user != null)
			fly(order, user);
		else
			throw new NoSuchElementException("User for " + order.getName() + " order could not be found.");
	}

	// findCustomer(Order): Method that finds the customer of any given order.
	private User findCustomer(Order order)
	{
		// Sleep for 100 ms just to ensure that the data is now on the server.
	    try
		{
			Thread.sleep(100);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

	    // Synchronise on the users object.
		synchronized (users)
		{
			// For every user, search every one of their orders and check to see if they made the order.
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

	// fly(Ingredient, int): Function that initiates the flying process when restocking ingredients.
	private Map<Ingredient, Number> fly(Ingredient ingredient, int load)
	{
		// Create a new Map object for loadedIngredients.
	    Map<Ingredient, Number> loadedIngredients = new HashMap<>();

		return fly(ingredient, load, loadedIngredients, restaurant.getLocation());
	}

	// fly(Ingredient, int, Map<Ingredient, Number>, Postcode): Method that simulates the flying process for restocking ingredients.
	private Map<Ingredient, Number> fly(Ingredient ingredient, int load, Map<Ingredient, Number> loadedIngredients, Postcode source)
	{
		// Set the details of the flight - the status, source and destination, and then call the core fly() method.
	    setStatus("Retrieving " + ingredient.getName() + " from " + ingredient.getSupplier().getName());
		setSource(source);
		setDestination(ingredient.getSupplier().getPostcode());
		fly();

		// Load the ingredients.
		loadedIngredients.put(ingredient, load);

		int totalLoad = 0;

		// For each ingredient loaded, add up the load to determine the total load.
		for (Map.Entry entry : loadedIngredients.entrySet())
		{
			Number ingredientLoad = (Number)entry.getValue();
			totalLoad = totalLoad + ingredientLoad.intValue();
		}

		// If the total load is below the capacity, call the recursive function to determine if more ingredients can be collected and loaded before returning to the restaurant.
		if (totalLoad < getCapacity().intValue())
		{
			loadedIngredients = restockIngredients(loadedIngredients, load, ingredient.getSupplier().getPostcode());
		}
		// Otherwise, simulate returning the ingredients the drone has to the restaurant.
		else
		{
			setStatus("Returning to " + restaurant.getName() + " with ingredients");
			setSource(ingredient.getSupplier().getPostcode());
			setDestination(restaurant.getLocation());
			fly();
		}

		return loadedIngredients;
	}

	// fly(Order, User): Function that simulates the flying process for orders.
	private void fly(Order order, User user)
	{
		currentOrder = order.getName();

		// Set up the details of the flight.
		setStatus("Delivering order " + order.getName() + " to " + user.getName());
		setSource(restaurant.getLocation());
		setDestination(user.getPostcode());
		order.setStatus("Out for delivery");

		// Update the client on the details of the order (i.e. it is now out for delivery).
		synchronized (comms)
		{
			comms.sendMessage("CHANGE ORDER STATUS", order, user);
		}

		// If the flight went off without issue, return to the restaurant and set the order to complete, sending this information to the client.
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

	// fly(): Method that starts the core flight simulation by calculating the distance of the flight and the speed of the drone.
	private boolean fly()
	{
	    Postcode source = getSource();
		Postcode destination = getDestination();

		// Determine the distance by either getting the already calculated distance to the restaurant if the source or the destination is the restaurant, or by calculating the
		// distance between the source and destination using the calculateDistance() method.
		double distance = (source == restaurant.getLocation()) ? destination.getDistance().doubleValue() :
				(destination == restaurant.getLocation()) ? source.getDistance().doubleValue() : source.calculateDistance(destination);
		float speed = getSpeed().floatValue();

		return fly(distance, speed);
	}

	// fly(double, speed): Core flight method which performs the flight simulation in seconds.
	private boolean fly(double distance, float speed)
	{
		double currentDistance = 0.0;

		// While the current distance is below the distance that needs to be covered:
		while (currentDistance < distance)
		{
			// If the drone is out of battery:
		    if (getBattery().doubleValue() <= 0.0)
			{
				// Just change the status if already going to the restaurant.
			    if (getDestination().equals(restaurant.getLocation()))
				{
					returnForRecharge();
				}
			    // Otherwise stop the current flight, saving the status beforehand, and then return to the restaurant to recharge before continuing.
				else
				{
					String status = getStatus();

					returnForRecharge(currentDistance, speed);
					rechargeBattery();

					setStatus(status);

					currentDistance = 0.0;
				}
			}

		    // For every order, check to see if the order the drone is delivering is cancelled, in which case simulate this accordingly.
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

			// Sleep for a second to simulate a second of passing.
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }

			// Recalculate the current distance.
			currentDistance = currentDistance + speed;

            // Determine the progress percentage and set it.
			double progress = (((currentDistance / distance) * 100) > 100) ? 100.0 : (currentDistance / distance) * 100;
			progress = Double.valueOf(String.format("%.2f", progress));

			setProgress(progress);

			// Reduce the battery if it is not already 0.
			if (getBattery().doubleValue() > 0.0)
				setBattery(getBattery().doubleValue() - BATTERY_USAGE_RATE);

			// Tell the server to back itself when the progress of the drone has changed.
			dataPersistence.backupServer();
		}

		// Recharge the battery now if the flight was already going back to the restaurant.
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

	// recoverDrone(ServerComms, DataPersistence): Sets the ServerComms object and the DataPersistence object to the passed variables to send further backups when recovered.
	public void recoverDrone(ServerComms comms, DataPersistence dataPersistence)
	{
		this.comms = comms;
		this.dataPersistence = dataPersistence;
	}

	// orderReady(Order): Method that checks if an order is ready to be sent out for delivery.
	private boolean orderReady(Order order)
	{
		// For every ordered dish, check the stock levels to see if there is enough stock of that dish to satisfy the quantity ordered.
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

	// returnForRecharge(): Function that updates the status to indicate that a drone is returning to recharge.
	private void returnForRecharge()
	{
		setStatus("Out of battery, returning to " + restaurant.getName() + " to recharge");
	}

	// returnForRecharge(double, float): Method that simulates flying back to the restaurant from wherever it currently was in its journey.
	private void returnForRecharge(double distance, float speed)
	{
		// Remember the destination of the current journey before interrupting it.
	    Postcode destination = getDestination();

	    // Change the status, destination and source appropriately (the source is likely currently not at a postcode, so just default to the restaurant).
		setStatus("Out of battery, returning to " + restaurant.getName() + " to recharge");
		setDestination(restaurant.getLocation());
		setSource(restaurant.getLocation());

		// Fly back to the restaurant.
		fly(distance, speed);

		// Set the destination back so that flight restarts as before.
		setDestination(destination);
		setSource(restaurant.getLocation());
	}

	// rechargeBattery(): Function that simulates recharging the battery of the drone back at the restaurant.
	private void rechargeBattery()
	{
		setStatus("Recharging battery");

		// Sleep for the time indicated by the RECHARGE_TIME long constant.
		try
		{
			Thread.sleep(RECHARGE_TIME);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

		// Set the battery to 100% charge.
		setBattery(100.0);
	}

	// calculateMaxLoad(Ingredient): Method that determines the maximum load the drone can carry of an ingredient.
	private int calculateMaxLoad(Ingredient ingredient)
	{
		int capacity = getCapacity().intValue();
		double weight = ingredient.getWeight().doubleValue();
		int restockAmount = ingredient.getRestockAmount().intValue();
		int restockThreshold = ingredient.getRestockThreshold().intValue();
		int load = 0;

		// While the load is below the capacity of the drone, or the restock threshold times the weight of the ingredient,
        // increase the load by the restock amount multiplied by the weight of a singular ingredient.
		while ((load < capacity) && (load < (restockThreshold * weight)))
		{
			load = load + (int)Math.round(restockAmount * weight);
		}

		return load;
	}
}