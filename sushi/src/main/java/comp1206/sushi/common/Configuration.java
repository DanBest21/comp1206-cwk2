package comp1206.sushi.common;

import comp1206.sushi.server.Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Configuration
{
    File configFile;
    Server server;

    public Configuration(String filename, Server server)
    {
        configFile = new File(filename);
        this.server = server;
    }

    public void parseConfigFile() throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader(configFile));

        while (reader.ready())
        {
            String line = reader.readLine().trim();

            if (!line.equals(""))
            {
                String[] object = line.split(":");
                String[] parameters = new String[object.length - 1];

                System.arraycopy(object, 1, parameters, 0, object.length - 1);

                addModel(object[0], parameters);
            }
        }
    }

    public void addModel(String modelName, String[] parameters)
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

    private Postcode getPostcode(String name)
    {
        for (Postcode postcode : server.getPostcodes())
        {
            if (postcode.getName().equals(name))
                return postcode;
        }

        return null;
    }

    private Supplier getSupplier(String name)
    {
        for (Supplier supplier : server.getSuppliers())
        {
            if (supplier.getName().equals(name))
                return supplier;
        }

        return null;
    }

    private User getUser(String name)
    {
        for (User user : server.getUsers())
        {
            if (user.getName().equals(name))
                return user;
        }

        return null;
    }

    private void setStock(String item, int stock)
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
    }
}
