package comp1206.sushi.server;

import comp1206.sushi.common.*;

import java.io.*;
import java.util.NoSuchElementException;

// Configuration class: Handles the configuration file, which can be used to load a server configuration.
public class Configuration
{
    private final File configFile;
    private final Server server;

    public Configuration(String filename, Server server)
    {
        configFile = new File(filename);
        this.server = server;
    }

    // loadConfigFile(): Creates a BufferedReader object on the configFile File object.
    public void loadConfigFile() throws FileNotFoundException
    {
        BufferedReader reader = new BufferedReader(new FileReader(configFile));

        parseConfigFile(reader);
    }

    // parseConfigFile(): Parses each line of the configuration file using a passed BufferedReader, calling the addModel() method when appropriate.
    private void parseConfigFile(BufferedReader reader)
    {
        try
        {
            while (reader.ready())
            {
                String line = reader.readLine().trim();

                if (!line.equals(""))
                {
                    String[] object = line.split(":");
                    String[] parameters = new String[object.length - 1];

                    System.arraycopy(object, 1, parameters, 0, object.length - 1);

                    try
                    {
                        addModel(object[0], parameters);
                    }
                    catch (NoSuchElementException ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
        catch (IOException ex)
        {
            ex.getStackTrace();
        }
    }

    // addModel(): Creates a new object of the appropriate model and adds it to the server.
    public void addModel(String modelName, String[] parameters) throws NoSuchElementException
    {
        switch (modelName.toUpperCase())
        {
            case "DISH":
                Dish dish = server.addDish(parameters[0], parameters[1], Double.parseDouble(parameters[2]), Integer.parseInt(parameters[3]), Integer.parseInt(parameters[4]));
                addIngredientsToDish(dish, parameters[5]);
                break;

            case "DRONE":
                server.addDrone(Integer.parseInt(parameters[0]));
                break;

            case "INGREDIENT":
                server.addIngredient(parameters[0], parameters[1], getSupplier(parameters[2]), Integer.parseInt(parameters[3]), Integer.parseInt(parameters[4]), Double.parseDouble(parameters[5]));
                break;

            case "ORDER":
                Order order = server.addOrder(getUser(parameters[0]));
                addDishesToOrder(order, parameters[1]);
                break;

            case "POSTCODE":
                server.addPostcode(parameters[0]);
                break;

            case "RESTAURANT":
                server.setRestaurant(parameters[0], getPostcode(parameters[1]));
                break;

            case "STAFF":
                server.addStaff(parameters[0]);
                break;

            case "STOCK":
                setStock(parameters[0], Integer.parseInt(parameters[1]));
                break;

            case "SUPPLIER":
                server.addSupplier(parameters[0], getPostcode(parameters[1]));
                break;

            case "USER":
                server.addUser(parameters[0], parameters[1], parameters[2], getPostcode(parameters[3]));
                break;
        }
    }

    // addIngredientsToDish(): Supplementary method that adds ingredients to a particular dish.
    private void addIngredientsToDish(Dish dish, String ingredients)
    {
        for (String ingredient : ingredients.split(","))
        {
            int quantity = Integer.parseInt(ingredient.split("/*")[0].trim());

            String name = ingredient.split("/*")[1].trim();

            for (Ingredient i : server.getIngredients())
            {
                if (i.getName().equals(name))
                    server.addIngredientToDish(dish, i, quantity);
            }
        }
    }

    // addDishesToOrder(): Supplementary method that adds dishes to a particular order.
    private void addDishesToOrder(Order order, String dishes)
    {
        for (String dish : dishes.split(","))
        {
            int quantity = Integer.parseInt(dish.split("/*")[0].trim());

            String name = dish.split("/*")[1].trim();

            for (Dish d : server.getDishes())
            {
                if (d.getName().equals(name))
                    server.addDishToOrder(order, d, quantity);
            }
        }
    }

    // getPostcode(): Supplementary method that returns the Postcode object associated to the passed String name from the server.
    private Postcode getPostcode(String name) throws NoSuchElementException
    {
        for (Postcode postcode : server.getPostcodes())
        {
            if (postcode.getName().equals(name))
                return postcode;
        }

        throw new NoSuchElementException("Cannot find Postcode object called \"" + name + "\"");
    }

    // getSupplier(): Supplementary method that returns the Supplier object associated to the passed String name from the server.
    private Supplier getSupplier(String name) throws NoSuchElementException
    {
        for (Supplier supplier : server.getSuppliers())
        {
            if (supplier.getName().equals(name))
                return supplier;
        }

        throw new NoSuchElementException("Cannot find Supplier object called \"" + name + "\"");
    }

    // getUser(): Supplementary method that returns the User object associated to the passed String name from the server.
    private User getUser(String name) throws NoSuchElementException
    {
        for (User user : server.getUsers())
        {
            if (user.getName().equals(name))
                return user;
        }

        throw new NoSuchElementException("Cannot find User object called \"" + name + "\"");
    }

    // setStock(): Supplementary method that sets the stock of either a Dish or Ingredient that is associated to the String item passed.
    private void setStock(String item, int stock) throws NoSuchElementException
    {
        for (Dish dish : server.getDishes())
        {
            if (dish.getName().equals(item))
            {
                server.setStock(dish, stock);
                return;
            }
        }

        for (Ingredient ingredient : server.getIngredients())
        {
            if (ingredient.getName().equals(item))
            {
                server.setStock(ingredient, stock);
                return;
            }
        }

        throw new NoSuchElementException("Cannot find Dish or Ingredient object called \"" + item + "\"");
    }
}
