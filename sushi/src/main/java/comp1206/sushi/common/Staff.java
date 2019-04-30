package comp1206.sushi.common;

import comp1206.sushi.server.Server;

import java.util.*;

public class Staff extends Model implements Runnable
{
	private String name;
	private String status;
	private Number fatigue;
	private final Stock stock;
	private final List<Dish> dishes;
	private static Map<Dish, Number> restocksInProgress = new HashMap<>();
	private static boolean readyToCheck = true;

	private static final int UPPER_PREP_TIME = 60;
	private static final int LOWER_PREP_TIME = 20;
	
	public Staff(String name, Stock stock, List<Dish> dishes)
	{
		this.setName(name);
		this.setFatigue(0);
		this.stock = stock;
		this.dishes = dishes;
	}

	// run(): Primary method called by the Thread when started.
	public void run()
	{
		while (true)
		{
			setStatus("Idle");

			// Stop the thread if it's been interrupted.
			if (Thread.currentThread().isInterrupted())
				return;

			// If the restock dishes setting is enabled:
			if (stock.getRestockingDishesEnabled())
			{
				// Try to restock dishes.
				try
				{
					restockDishes();
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
		}
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Number getFatigue()
	{
		return fatigue;
	}

	public void setFatigue(Number fatigue)
	{
		this.fatigue = fatigue;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(String status)
	{
		notifyUpdate("status",this.status,status);
		this.status = status;
	}

	// restockDishes(): Function that loops through each dish and checks if it can be prepared, and then prepares it if it can.
	private void restockDishes() throws ConcurrentModificationException
	{
		for (Dish dish : dishes)
		{
			// Add the key if it's not already in the restocksInProgress map.
			if (!restocksInProgress.containsKey(dish))
				restocksInProgress.put(dish, 0);

			// Stop the thread if it's been interrupted.
			if (Thread.currentThread().isInterrupted())
				return;

			// While the dish can be prepared:
			while (canPrepareDish(dish))
			{
				// Notify the other threads that they can now carry on with their check to see if a dish can be prepared.
				synchronized (stock)
				{
					readyToCheck = true;
					stock.notify();
				}

				// Restock the dish.
				restockDish(dish);
			}

			// Notify the other threads that they can now carry on with their check to see if a dish can be prepared.
			synchronized (stock)
			{
				readyToCheck = true;
				stock.notify();
			}
		}
	}

	// canPrepareDish(Dish): Method that checks if the dish can be prepared at this current time by the Staff member.
	private boolean canPrepareDish(Dish dish)
	{
		// Synchronise this based on the stock object.
		synchronized (stock)
		{
			try
			{
				// If the thread is not ready to check if a dish can be prepared, wait.
				if (!readyToCheck)
				{
					stock.wait();
				}
			}
			catch (InterruptedException ex)
			{
				return false;
			}

			// Set the readyToCheck boolean variable to false whilst this thread is checking if this dish can be prepared.
			readyToCheck = false;

			// If the stock level of a dish plus any dishes in preparation is high enough or equal to the restock threshold, or the thread has been interrupted, return false.
			if ((stock.getStock(dish).intValue() + (restocksInProgress.get(dish).intValue() * dish.getRestockAmount().intValue()) >= dish.getRestockThreshold().intValue()) ||
					Thread.currentThread().isInterrupted())
			{
				return false;
			}

			// For each ingredient in the recipe:
			for (Map.Entry entry : dish.getRecipe().entrySet())
			{
				Ingredient ingredient = (Ingredient) entry.getKey();
				Number quantity = (Number) entry.getValue();

				// If the stock level of an ingredient is below the number of ingredients required for a dish, times the restock amount, multiplied by
				// the number of restocks of this dish in progress plus one for the restock that would occur if this returned true, then return false.
				if (stock.getStock(ingredient).intValue() < (quantity.intValue() * dish.getRestockAmount().intValue()) * (restocksInProgress.get(dish).intValue() + 1))
				{
					return false;
				}
			}

			// Otherwise the dish can be prepared, so return true.
			return true;
		}
	}

	// restockDish(Dish): Function that restocks the dish by the restock amount.
	private void restockDish(Dish dish)
	{
		// Add one to the restocksInProgress counter for this dish and set the status to preparing this dish.
		restocksInProgress.put(dish, restocksInProgress.get(dish).intValue() + 1);
		setStatus("Preparing " + dish.getName());

		// Generate a random preparation time between the upper and lower bounds (in milliseconds).
		Random rand = new Random();
		int prepTime = (rand.nextInt(UPPER_PREP_TIME - LOWER_PREP_TIME + 1) + LOWER_PREP_TIME) * 1000;

		// Sleep for the randomly generated preparation time.
		try
		{
			Thread.sleep(prepTime);
		}
		catch (InterruptedException ex)
		{
			return;
		}

		// For each ingredient in the dish's recipe:
		for (Map.Entry entry : dish.getRecipe().entrySet())
		{
			Ingredient ingredient = (Ingredient)entry.getKey();
			Number quantity = (Number)entry.getValue();

			// Set the stock of the ingredient to the current stock minus the number of that ingredient required for the dish times the amount of dishes that are created.
			stock.setStock(ingredient, stock.getStock(ingredient).intValue() - (quantity.intValue() * dish.getRestockAmount().intValue()));
		}

		// Set the stock of the dish to the current stock plus the restock amount.
		stock.setStock(dish, stock.getStock(dish).intValue() + dish.getRestockAmount().intValue());

		// Subtract one from the restocksInProgress counter for this dish.
		restocksInProgress.put(dish, restocksInProgress.get(dish).intValue() - 1);
	}
}