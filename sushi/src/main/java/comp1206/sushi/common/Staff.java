package comp1206.sushi.common;

import comp1206.sushi.server.DataPersistence;

import java.io.Serializable;
import java.util.*;

public class Staff extends Model implements Runnable, Serializable
{
	private String name;
	private String status;
	private Number fatigue;
	private final Stock stock;
	private final List<Dish> dishes;
	private transient static boolean readyToCheck = true;
	private transient DataPersistence dataPersistence;

	private static final int UPPER_PREP_TIME = 60;
	private static final int LOWER_PREP_TIME = 20;
	private static final double FATIGUE_RATE = 1;
	private static final long RECHARGE_TIME = 60000;

    private transient static final Map<Dish, Number> RESTOCKS_IN_PROGRESS = new HashMap<>();
	
	public Staff(String name, Stock stock, List<Dish> dishes, DataPersistence dataPersistence)
	{
		this.setName(name);
		this.setFatigue(0.0);
		this.stock = stock;
		this.dishes = dishes;
		this.dataPersistence = dataPersistence;
	}

	// run(): Primary method called by the Thread when started.
	public void run()
	{
		// Stop the thread if it's been interrupted.
		while (!Thread.currentThread().isInterrupted())
		{
			setStatus("Idle");

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
			// Add the key if it's not already in the RESTOCKS_IN_PROGRESS map.
			if (!RESTOCKS_IN_PROGRESS.containsKey(dish))
				RESTOCKS_IN_PROGRESS.put(dish, 0);

			// Stop the thread if it's been interrupted.
			if (Thread.currentThread().isInterrupted())
				return;

			// While the dish can be prepared:
			while (canPrepareDish(dish))
			{
				// Add one to the RESTOCKS_IN_PROGRESS counter for this dish.
				RESTOCKS_IN_PROGRESS.put(dish, RESTOCKS_IN_PROGRESS.get(dish).intValue() + 1);

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
				// Sleep for a random time just to ensure everything is place.
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

			// Set the readyToCheck boolean variable to false whilst this thread is checking if this dish can be prepared.
			readyToCheck = false;

			// If the stock level of a dish plus any dishes in preparation is high enough or equal to the restock threshold, or the thread has been interrupted, return false.
			if ((stock.getStock(dish).intValue() + (RESTOCKS_IN_PROGRESS.get(dish).intValue() * dish.getRestockAmount().intValue()) >= dish.getRestockThreshold().intValue()) ||
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
				if (stock.getStock(ingredient).intValue() < (quantity.intValue() * dish.getRestockAmount().intValue()) * (RESTOCKS_IN_PROGRESS.get(dish).intValue() + 1))
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
		// Set the status to preparing this dish.
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
			Thread.currentThread().interrupt();
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

		// Tell the server to back itself up when the stock level has changed.
		dataPersistence.backupServer();

		// Subtract one from the RESTOCKS_IN_PROGRESS counter for this dish.
		RESTOCKS_IN_PROGRESS.put(dish, RESTOCKS_IN_PROGRESS.get(dish).intValue() - 1);

		// Calculate the fatigue that the staff member has gained, and then set the fatigue to this value (or 100 if above 100).
		double fatigue = getFatigue().doubleValue() + (FATIGUE_RATE * (prepTime / 1000.0));
		setFatigue((fatigue >= 100) ? 100.0 : fatigue);

		// If the fatigue is above or equal to 100, call the recharge method.
		if (getFatigue().doubleValue() >= 100.0)
			recharge();
	}

	// recoverStaff(DataPersistence): Sets the DataPersistence object to the passed variable to send further backups when recovered.
	public void recoverStaff(DataPersistence dataPersistence)
	{
		this.dataPersistence = dataPersistence;
	}

	// recharge(): Method that simulates a staff member taking a break.
	private void recharge()
	{
		setStatus("Taking a break to recharge");

		// Recharge for the time indicated by the RECHARGE_TIME constant.
		try
		{
			Thread.sleep(RECHARGE_TIME);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

		// Reset the fatigue levels back to 0.
		setFatigue(0.0);
	}
}