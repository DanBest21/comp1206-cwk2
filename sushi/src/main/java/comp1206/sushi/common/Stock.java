package comp1206.sushi.common;

import comp1206.sushi.server.ServerInterface;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

// Stock class: Stores all information about the stock of dishes and ingredients.
public class Stock implements Serializable
{
    private Map<Dish, Number> dishStock = new HashMap<>();
    private Map<Ingredient, Number> ingredientStock = new HashMap<>();

    private boolean restockIngredients = true;
    private boolean restockDishes = true;

    // getStock(Dish): Returns the stock of a particular dish.
    public synchronized Number getStock(Dish dish)
    {
        if (dishStock.get(dish) == null)
        {
            for (Map.Entry entry : dishStock.entrySet())
            {
                Dish d = (Dish)entry.getKey();

                if (d.getName().equals(dish.getName()))
                {
                    dish = d;
                    break;
                }
            }
        }

        return (dish == null) ? 0 : dishStock.get(dish).intValue();
    }

    // setStock(Dish, Number): Sets the stock of a particular dish.
    public synchronized void setStock(Dish dish, Number stock)
    {
        // Remove any objects found with the same name.
        dishStock.entrySet().removeIf(e -> e.getKey().getName().equals(dish.getName()));

        dishStock.put(dish, stock);
    }

    // removeDish(Dish): Removes a dish should it exist in the dishStock Map, otherwise returns an UnableToDeleteException.
    public synchronized void removeDish(Dish dish) throws ServerInterface.UnableToDeleteException
    {
        if (!dishStock.containsKey(dish))
            throw new ServerInterface.UnableToDeleteException("Dish \"" + "\" could not be removed from the stock as it does not exist.");
        else
            dishStock.remove(dish);
    }

    // getStock(Ingredient): Returns the stock of a particular ingredient.
    public synchronized Number getStock(Ingredient ingredient)
    {
        if (ingredientStock.get(ingredient) == null)
        {
            for (Map.Entry entry : ingredientStock.entrySet())
            {
                Ingredient i = (Ingredient)entry.getKey();

                if (i.getName().equals(ingredient.getName()))
                {
                    ingredient = i;
                    break;
                }
            }
        }

        return (ingredient == null) ? 0 : ingredientStock.get(ingredient).intValue();
    }

    // setStock(Ingredient, Number): Sets the stock of a particular ingredient.
    public synchronized void setStock(Ingredient ingredient, Number stock)
    {
        // Remove any objects found with the same name.
        ingredientStock.entrySet().removeIf(e -> e.getKey().getName().equals(ingredient.getName()));

        ingredientStock.put(ingredient, stock);
    }

    // removeDish(Ingredient): Removes an ingredient should it exist in the ingredientStock Map, otherwise returns an UnableToDeleteException.
    public synchronized void removeIngredient(Ingredient ingredient) throws ServerInterface.UnableToDeleteException
    {
        if (!ingredientStock.containsKey(ingredient))
            throw new ServerInterface.UnableToDeleteException("Ingredient \"" + "\" could not be removed from the stock as it does not exist.");
        else
            ingredientStock.remove(ingredient);
    }

    // getDishStockLevels(): Returns the dishStock Map.
    public Map<Dish, Number> getDishStockLevels() { return dishStock; }

    // getIngredientStockLevels(): Returns the ingredientStock Map.
    public Map<Ingredient, Number> getIngredientStockLevels() { return ingredientStock; }

    // setRestockingIngredientsEnabled(boolean): Sets the restockIngredients, which determines whether ingredients are to be restocked, to the value of the passed boolean.
    public void setRestockingIngredientsEnabled(boolean enabled) { restockIngredients = enabled; }

    // setRestockingDishesEnabled(boolean): Sets the restockDishes, which determines whether dishes are to be restocked, to the value of the passed boolean.
    public void setRestockingDishesEnabled(boolean enabled) { restockDishes = enabled; }

    // getRestockingIngredientsEnabled(): Gets the restockIngredients boolean, which determines if ingredients should be restocked by drones.
    public boolean getRestockingIngredientsEnabled() { return restockIngredients; }

    // getRestockingDishesEnabled(): Gets the restockDishes boolean, which determines if dishes should be restocked by staff.
    public boolean getRestockingDishesEnabled() { return restockDishes; }
}
