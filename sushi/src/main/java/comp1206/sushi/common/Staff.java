package comp1206.sushi.common;

import comp1206.sushi.server.Server;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Staff extends Model implements Runnable
{
	private String name;
	private String status;
	private Number fatigue;
	private Server server;
	private Stock stock;
	private static Map<Dish, Number> dishStatus = new HashMap<>();

	private static final int UPPER_PREP_TIME = 60;
	private static final int LOWER_PREP_TIME = 20;
	
	public Staff(String name, Server server)
	{
		this.setName(name);
		this.setFatigue(0);
		this.server = server;
		stock = server.getStock();
	}

	public void run()
	{
		try
		{
			while (true)
			{
				setStatus("Idle");

				if (Thread.currentThread().isInterrupted())
					return;

				if (stock.getRestockingDishesEnabled())
				{
					try
					{
						restockDishes();
					}
					catch (ConcurrentModificationException ex)
					{
						if (server.getDishes() == null || server.getIngredients() == null)
						{
							Thread.sleep(1000);
						}
						else if (Thread.currentThread().isInterrupted())
						{
							return;
						}
						else
						{
							ex.printStackTrace();
							break;
						}
					}
				}
			}
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
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

	private synchronized void restockDishes() throws ConcurrentModificationException
	{
		for (Dish dish : server.getDishes())
		{
			if (!dishStatus.containsKey(dish))
				dishStatus.put(dish, 1);

			if (Thread.currentThread().isInterrupted())
				return;

			while ((stock.getStock(dish).intValue() + ((dishStatus.get(dish).intValue() - 1) * dish.getRestockAmount().intValue())) < dish.getRestockThreshold().intValue() &&
					!Thread.currentThread().isInterrupted() && canPrepareDish(dish, dishStatus.get(dish)))
			{
				restockDish(dish);
			}
		}
	}

	private synchronized void restockDish(Dish dish)
	{
		dishStatus.put(dish, dishStatus.get(dish).intValue() + 1);
		setStatus("Preparing " + dish.getName());
		prepareDish(dish);
		dishStatus.put(dish, dishStatus.get(dish).intValue() - 1);
	}

	private synchronized boolean canPrepareDish(Dish dish, Number quantity)
	{
		for (Map.Entry entry : dish.getRecipe().entrySet())
		{
			Ingredient ingredient = (Ingredient)entry.getKey();
			Number noIngredients = (Number)entry.getValue();

			if (stock.getStock(ingredient).intValue() < (noIngredients.intValue() * dish.getRestockAmount().intValue()) * quantity.intValue())
			{
				return false;
			}
		}

		return true;
	}

	private synchronized void prepareDish(Dish dish)
	{
		Random rand = new Random();
		int prepTime = (rand.nextInt(UPPER_PREP_TIME - LOWER_PREP_TIME + 1) + LOWER_PREP_TIME) * 1000;

		try
		{
			Thread.sleep(prepTime);
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}

		for (Map.Entry entry : dish.getRecipe().entrySet())
		{
			Ingredient ingredient = (Ingredient)entry.getKey();
			Number quantity = (Number)entry.getValue();

			server.setStock(ingredient, stock.getStock(ingredient).intValue() - (quantity.intValue() * dish.getRestockAmount().intValue()));
		}

		server.setStock(dish, stock.getStock(dish).intValue() + dish.getRestockAmount().intValue());
	}
}
