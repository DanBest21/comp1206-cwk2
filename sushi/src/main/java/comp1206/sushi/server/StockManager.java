package comp1206.sushi.server;

import comp1206.sushi.common.Dish;
import comp1206.sushi.common.Ingredient;
import comp1206.sushi.common.Stock;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

public class StockManager extends Thread
{
    private Stock stock = new Stock();
    private List<Dish> dishes;
    private List<Ingredient> ingredients;

    private boolean restockIngredients = true;
    private boolean restockDishes = true;

    public StockManager(List<Dish> dishes, List<Ingredient> ingredients)
    {
        this.dishes = dishes;
        this.ingredients = ingredients;
    }

    public void run()
    {
        try
        {
            while (true)
            {
                try
                {
                    if (restockDishes)
                        manageDishes();
                    if (restockIngredients)
                        manageIngredients();
                }
                catch (ConcurrentModificationException ex)
                {
                    if (dishes.isEmpty() || ingredients.isEmpty())
                        Thread.sleep(1000);
                    else
                        ex.printStackTrace();
                }
            }
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }

    private void manageDishes() throws ConcurrentModificationException
    {
        for (Dish dish : dishes)
        {
            while (stock.getStock(dish).intValue() < dish.getRestockThreshold().intValue())
            {
                stock.changeStock(dish, stock.getStock(dish).intValue() + dish.getRestockAmount().intValue());
            }
        }
    }

    private void manageIngredients() throws ConcurrentModificationException
    {
        for (Ingredient ingredient : ingredients)
        {
            while (stock.getStock(ingredient).intValue() < ingredient.getRestockThreshold().intValue())
            {
                stock.changeStock(ingredient, stock.getStock(ingredient).intValue() + ingredient.getRestockAmount().intValue());
            }
        }
    }

    public Map<Dish, Number> getDishStockLevels() { return stock.getDishStockLevels(); }

    public Map<Ingredient, Number> getIngredientStockLevels() { return stock.getIngredientStockLevels(); }

    public void setRestockingIngredientsEnabled(boolean enabled) { restockIngredients = enabled; }

    public void setRestockingDishesEnabled(boolean enabled) { restockDishes = enabled; }

    public void setStock(Dish dish, Number stock) { this.stock.changeStock(dish, stock); }

    public void setStock(Ingredient ingredient, Number stock) { this.stock.changeStock(ingredient, stock); }

    public void removeDish(Dish dish) throws ServerInterface.UnableToDeleteException { this.stock.removeDish(dish); }

    public void removeIngredient(Ingredient ingredient) throws ServerInterface.UnableToDeleteException { this.stock.removeIngredient(ingredient); }
}