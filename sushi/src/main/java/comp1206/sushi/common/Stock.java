package comp1206.sushi.common;

import comp1206.sushi.server.ServerInterface;

import java.util.HashMap;
import java.util.Map;

public class Stock
{
    private Map<Dish, Number> dishStock = new HashMap<>();
    private Map<Ingredient, Number> ingredientStock = new HashMap<>();

    public Number getStock(Dish dish)
    {
        if (dishStock.get(dish) == null)
            return 0;

        return dishStock.get(dish).intValue();
    }

    public void changeStock(Dish dish, Number stock) { dishStock.put(dish, stock); }

    public void removeDish(Dish dish) throws ServerInterface.UnableToDeleteException
    {
        if (!dishStock.containsKey(dish))
            throw new ServerInterface.UnableToDeleteException("Dish \"" + "\" could not be removed from the stock as it does not exist.");
        else
            dishStock.remove(dish);
    }

    public Number getStock(Ingredient ingredient)
    {
        if (ingredientStock.get(ingredient) == null)
            return 0;

        return ingredientStock.get(ingredient).intValue();
    }

    public void changeStock(Ingredient ingredient, Number stock) { ingredientStock.put(ingredient, stock); }

    public void removeIngredient(Ingredient ingredient) throws ServerInterface.UnableToDeleteException
    {
        if (!ingredientStock.containsKey(ingredient))
            throw new ServerInterface.UnableToDeleteException("Ingredient \"" + "\" could not be removed from the stock as it does not exist.");
        else
            ingredientStock.remove(ingredient);
    }

    public Map<Dish, Number> getDishStockLevels() { return dishStock; }

    public Map<Ingredient, Number> getIngredientStockLevels() { return ingredientStock; }
}
