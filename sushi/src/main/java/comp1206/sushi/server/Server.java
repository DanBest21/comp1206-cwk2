package comp1206.sushi.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import comp1206.sushi.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;

public class Server implements ServerInterface {

    private static final Logger logger = LogManager.getLogger("Server");
	
	private Restaurant restaurant;
	private final ArrayList<Dish> dishes = new ArrayList<Dish>();
	private final ArrayList<Drone> drones = new ArrayList<Drone>();
	private final ArrayList<Ingredient> ingredients = new ArrayList<Ingredient>();
	private final ArrayList<Order> orders = new ArrayList<Order>();
	private final ArrayList<Staff> staff = new ArrayList<Staff>();
	private final Map<Staff, Thread> staffThreads = new HashMap<>();
	private final ArrayList<Supplier> suppliers = new ArrayList<Supplier>();
	private final ArrayList<User> users = new ArrayList<User>();
	private final ArrayList<Postcode> postcodes = new ArrayList<Postcode>();
	private final Stock stock = new Stock();
	private final ArrayList<UpdateListener> listeners = new ArrayList<>();
	private ServerComms comms = new ServerComms(this);
	
	public Server() {
        logger.info("Starting up server...");

        addPostcode("SO17 1BJ");
        restaurant = new Restaurant("Southampton Sushi", postcodes.get(0));

		comms.start();
	}
	
	@Override
	public List<Dish> getDishes() {
		return this.dishes;
	}

	@Override
	public Dish addDish(String name, String description, Number price, Number restockThreshold, Number restockAmount) {
		Dish newDish = new Dish(name,description,price,restockThreshold,restockAmount);
		this.dishes.add(newDish);
		stock.setStock(newDish, 0);
		comms.sendMessage("ADD DISH", newDish);
		this.notifyUpdate();
		return newDish;
	}
	
	@Override
	public void removeDish(Dish dish) throws UnableToDeleteException {
		if (!this.dishes.contains(dish))
			throw new UnableToDeleteException("Unable to delete Dish \"" + dish.getName() + "\" as it does not exist on the server.");
		this.dishes.remove(dish);
		stock.removeDish(dish);
		comms.sendMessage("REMOVE DISH", dish);
		this.notifyUpdate();
	}

	@Override
	public Map<Dish, Number> getDishStockLevels() {
		return stock.getDishStockLevels();
	}
	
	@Override
	public void setRestockingIngredientsEnabled(boolean enabled) {
		stock.setRestockingIngredientsEnabled(enabled);
		this.notifyUpdate();
	}

	@Override
	public void setRestockingDishesEnabled(boolean enabled) {
		stock.setRestockingDishesEnabled(enabled);
		this.notifyUpdate();
	}

	@Override
	public void setStock(Dish dish, Number stock) {
		this.stock.setStock(dish, stock);
		this.notifyUpdate();
	}

	@Override
	public void setStock(Ingredient ingredient, Number stock) {
		this.stock.setStock(ingredient, stock);
		this.notifyUpdate();
	}

	@Override
	public List<Ingredient> getIngredients() {
		return this.ingredients;
	}

	@Override
	public Ingredient addIngredient(String name, String unit, Supplier supplier,
			Number restockThreshold, Number restockAmount, Number weight) {
		Ingredient ingredient = new Ingredient(name,unit,supplier,restockThreshold,restockAmount,weight);
		this.ingredients.add(ingredient);
		stock.setStock(ingredient, 0);
		this.notifyUpdate();
		return ingredient;
	}

	@Override
	public void removeIngredient(Ingredient ingredient) throws UnableToDeleteException {
		if (!this.ingredients.contains(ingredient))
			throw new UnableToDeleteException("Unable to delete Ingredient \"" + ingredient.getName() + "\" as it does not exist on the server.");
		this.ingredients.remove(ingredient);
		stock.removeIngredient(ingredient);
		this.notifyUpdate();
	}

	@Override
	public List<Supplier> getSuppliers() {
		return this.suppliers;
	}

	@Override
	public Supplier addSupplier(String name, Postcode postcode) {
		Supplier supplier = new Supplier(name,postcode);
		this.suppliers.add(supplier);
		this.notifyUpdate();
		return supplier;
	}


	@Override
	public void removeSupplier(Supplier supplier) throws UnableToDeleteException {
		if (!this.suppliers.contains(supplier))
			throw new UnableToDeleteException("Unable to delete Supplier \"" + supplier.getName() + "\" as it does not exist on the server.");
		this.suppliers.remove(supplier);
		this.notifyUpdate();
	}

	@Override
	public List<Drone> getDrones() {
		return this.drones;
	}

	@Override
	public Drone addDrone(Number speed) {
		Drone drone = new Drone(speed);
		this.drones.add(drone);
		this.notifyUpdate();
		return drone;
	}

	@Override
	public void removeDrone(Drone drone) throws UnableToDeleteException {
		if (!this.drones.contains(drone))
			throw new UnableToDeleteException("Unable to delete Drone \"" + drone.getName() + "\" as it does not exist on the server.");
		this.drones.remove(drone);
		this.notifyUpdate();
	}

	@Override
	public List<Staff> getStaff() {
		return this.staff;
	}

	@Override
	public Staff addStaff(String name) {
		Staff staff = new Staff(name, stock, dishes);
		this.staff.add(staff);

        Thread thread = new Thread(staff);
        thread.setName(staff.getName());
        thread.start();
        this.staffThreads.put(staff, thread);

		this.notifyUpdate();
		return staff;
	}

	@Override
	public void removeStaff(Staff staff) throws UnableToDeleteException {
		if (!this.staff.contains(staff))
			throw new UnableToDeleteException("Unable to delete Staff \"" + staff.getName() + "\" as it does not exist on the server.");
		this.staff.remove(staff);
		this.staffThreads.get(staff).interrupt();
		this.staffThreads.remove(staff);
		this.notifyUpdate();
	}

	public Order addOrder(User customer)
	{
		Order order = new Order();
		this.orders.add(order);
		customer.placeOrder(order);
		this.notifyUpdate();
		return order;
	}

	public void addDishToOrder(Order order, Dish dish, Number quantity)
	{
		if(quantity == Integer.valueOf(0)) {
			removeDishFromOrder(order, dish);
		} else {
			order.getOrderedDishes().put(dish, quantity);
		}

		this.notifyUpdate();
	}

	public void removeDishFromOrder(Order order, Dish dish) {
		order.getOrderedDishes().remove(dish);
		this.notifyUpdate();
	}

	@Override
	public List<Order> getOrders() {
		return this.orders;
	}

	@Override
	public void removeOrder(Order order) throws UnableToDeleteException {
		if (!this.orders.contains(order))
			throw new UnableToDeleteException("Unable to delete Order \"" + order.getName() + "\" as it does not exist on the server.");
		if (!order.isComplete())
			throw new UnableToDeleteException("Unable to delete Order \"" + order.getName() + "\" as it has not yet been completed.");
		this.orders.remove(order);
		comms.sendMessage("COMPLETE ORDER", order);
		this.notifyUpdate();
	}

	@Override
	public Number getOrderCost(Order order) {
		double cost = 0.0;

		if (order.getOrderedDishes() == null)
			return 0.0;

		for (Map.Entry entry : order.getOrderedDishes().entrySet())
		{
			Dish dish = (Dish)entry.getKey();
			cost = cost + (dish.getPrice().doubleValue() * (int)entry.getValue());
		}

		return cost;
	}

	@Override
	public Map<Ingredient, Number> getIngredientStockLevels() {
		return stock.getIngredientStockLevels();
	}

	@Override
	public Number getSupplierDistance(Supplier supplier) {
		return supplier.getDistance();
	}

	@Override
	public Number getDroneSpeed(Drone drone) {
		return drone.getSpeed();
	}

	@Override
	public Number getOrderDistance(Order order) {
		return order.getDistance();
	}

	@Override
	public void addIngredientToDish(Dish dish, Ingredient ingredient, Number quantity) {
		if(quantity == Integer.valueOf(0)) {
			removeIngredientFromDish(dish,ingredient);
		} else {
			dish.getRecipe().put(ingredient,quantity);
		}

		this.notifyUpdate();
	}

	@Override
	public void removeIngredientFromDish(Dish dish, Ingredient ingredient) {
		dish.getRecipe().remove(ingredient);
		this.notifyUpdate();
	}

	@Override
	public Map<Ingredient, Number> getRecipe(Dish dish) {
		return dish.getRecipe();
	}

	@Override
	public List<Postcode> getPostcodes() {
		return this.postcodes;
	}

	@Override
	public Postcode addPostcode(String code) {
        Postcode postcode;
	    if (restaurant == null)
	        postcode = new Postcode(code);
	    else
		    postcode = new Postcode(code, restaurant);
		this.postcodes.add(postcode);
		comms.sendMessage("ADD POSTCODE", postcode);
		this.notifyUpdate();
		return postcode;
	}

	@Override
	public void removePostcode(Postcode postcode) throws UnableToDeleteException {
		if (!this.postcodes.contains(postcode))
			throw new UnableToDeleteException("Unable to delete Postcode \"" + postcode.getName() + "\" as it does not exist on the server.");
		this.postcodes.remove(postcode);
		comms.sendMessage("REMOVE POSTCODE", postcode);
		this.notifyUpdate();
	}

	public User addUser(String username, String password, String address, Postcode postcode)
	{
		User user = new User(username, password, address, postcode);
		this.users.add(user);
		this.notifyUpdate();
		return user;
	}

	@Override
	public List<User> getUsers() {
		return this.users;
	}
	
	@Override
	public void removeUser(User user) throws UnableToDeleteException {
		if (!this.users.contains(user))
			throw new UnableToDeleteException("Unable to delete User \"" + user.getName() + "\" as it does not exist on the server.");
		this.users.remove(user);
		this.notifyUpdate();
	}

	// loadConfiguration(): Loads the configuration file into the server using the Configuration helper class.
	@Override
	public void loadConfiguration(String filename) throws FileNotFoundException
	{
		Configuration configuration = new Configuration(filename, this);

		clearData();

		configuration.loadConfigFile();

		System.out.println("Loaded configuration: " + filename);
	}

	@Override
	public void setRecipe(Dish dish, Map<Ingredient, Number> recipe) {
		for(Entry<Ingredient, Number> recipeItem : recipe.entrySet()) {
			addIngredientToDish(dish,recipeItem.getKey(),recipeItem.getValue());
		}
		comms.sendMessage("EDIT DISH", dish);
		this.notifyUpdate();
	}

	@Override
	public boolean isOrderComplete(Order order) {
		return order.isComplete();
	}

	@Override
	public String getOrderStatus(Order order) {
		return order.getStatus();
	}

	@Override
	public String getDroneStatus(Drone drone) {
		return drone.getStatus();
	}

	@Override
	public String getStaffStatus(Staff staff) {
		return staff.getStatus();
	}

	@Override
	public void setRestockLevels(Dish dish, Number restockThreshold, Number restockAmount) {
		dish.setRestockThreshold(restockThreshold);
		dish.setRestockAmount(restockAmount);
		this.notifyUpdate();
	}

	@Override
	public void setRestockLevels(Ingredient ingredient, Number restockThreshold, Number restockAmount) {
		ingredient.setRestockThreshold(restockThreshold);
		ingredient.setRestockAmount(restockAmount);
		this.notifyUpdate();
	}

	@Override
	public Number getRestockThreshold(Dish dish) {
		return dish.getRestockThreshold();
	}

	@Override
	public Number getRestockAmount(Dish dish) {
		return dish.getRestockAmount();
	}

	@Override
	public Number getRestockThreshold(Ingredient ingredient) {
		return ingredient.getRestockThreshold();
	}

	@Override
	public Number getRestockAmount(Ingredient ingredient) {
		return ingredient.getRestockAmount();
	}

	@Override
	public void addUpdateListener(UpdateListener listener) {
		this.listeners.add(listener);
	}
	
	@Override
	public void notifyUpdate() {
		this.listeners.forEach(listener -> listener.updated(new UpdateEvent()));
	}

	@Override
	public Postcode getDroneSource(Drone drone) {
		return drone.getSource();
	}

	@Override
	public Postcode getDroneDestination(Drone drone) {
		return drone.getDestination();
	}

	@Override
	public Number getDroneProgress(Drone drone) {
		return drone.getProgress();
	}

	public Restaurant setRestaurant(String name, Postcode postcode) {
		restaurant = new Restaurant(name, postcode);
		this.notifyUpdate();
		return restaurant;
	}

	@Override
	public String getRestaurantName() {
		return restaurant.getName();
	}

	@Override
	public Postcode getRestaurantPostcode() {
		return restaurant.getLocation();
	}
	
	@Override
	public Restaurant getRestaurant() {
		return restaurant;
	}

	// clearData(): Clears all of the data on the server.
	private void clearData()
	{
		restaurant = null;
		dishes.clear();
		drones.clear();
		ingredients.clear();
		orders.clear();
		staff.clear();
		suppliers.clear();
		users.clear();
		postcodes.clear();

		for (Map.Entry entry : staffThreads.entrySet())
        {
            Thread thread = (Thread)entry.getValue();
            thread.interrupt();
        }

		staffThreads.clear();

		this.notifyUpdate();
	}
}
