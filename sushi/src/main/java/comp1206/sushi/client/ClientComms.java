package comp1206.sushi.client;

import comp1206.sushi.common.*;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientComms extends Thread
{
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT_NUMBER = 2066;
    private Socket socket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private final Client client;
    private boolean sendingData = false;

    public ClientComms(Client client)
    {
        super();

        this.client = client;

        try
        {
            socket = new Socket(SERVER_ADDRESS, PORT_NUMBER);

            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public void run()
    {
        try
        {
            while (true)
            {
                synchronized (client)
                {
                    while (sendingData)
                    {
                        try
                        {
                            client.wait();
                        }
                        catch (InterruptedException ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }

                receiveMessage();
            }
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public void sendMessage(String message, Model model)
    {
        synchronized (client)
        {
            sendingData = true;
            message = message.toUpperCase().trim();

            try
            {
                output.writeObject(message);

                switch (message)
                {
                    case "LOAD DATA":
                        loadData();
                        break;

                    case "LOGIN":
                    case "REGISTER USER":
                        output.writeObject((User)model);
                        break;

                    default:
                        throw new IOException("Attempting to send unrecognised command - " + message);
                }
            }
            catch (ClassNotFoundException ex)
            {
                ex.printStackTrace();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            sendingData = false;
            client.notify();
        }
    }

    public void sendMessage(String message, Model model, User user)
    {
        synchronized (client)
        {
            sendingData = true;
            message = message.toUpperCase().trim();

            try
            {
                output.writeObject(message);

                switch (message)
                {
                    case "NEW ORDER":
                    case "CANCEL ORDER":
                        output.writeObject((Order)model);
                        output.writeObject(user);
                        break;

                    default:
                        throw new IOException("Attempting to send unrecognised command - " + message);
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            sendingData = false;
            client.notify();
        }
    }

    private void loadData() throws ClassNotFoundException, IOException
    {
        client.setRestaurant((Restaurant)input.readObject());
        client.getPostcodes().clear();
        client.getPostcodes().addAll((List<Postcode>)input.readObject());
        client.getDishes().clear();
        client.getDishes().addAll((List<Dish>)input.readObject());
        client.getUsers().clear();
        client.getUsers().addAll((List<User>)input.readObject());
    }

    private void receiveMessage()
    {
        try
        {
            String message = (String)input.readObject();
            message = message.toUpperCase().trim();

            switch (message)
            {
                case "ADD DISH":
                    addDish();
                    break;

                case "EDIT DISH":
                    editDish();
                    break;

                case "REMOVE DISH":
                    removeDish();
                    break;

                case "ADD POSTCODE":
                    addPostcode();
                    break;

                case "EDIT POSTCODE":
                    editPostcode();
                    break;

                case "REMOVE POSTCODE":
                    removePostcode();
                    break;

                case "CHANGE ORDER STATUS":
                    updateOrder();
                    break;

                case "COMPLETE ORDER":
                    completeOrder();
                    break;

                default:
                    throw new IOException("Unrecognised message received - " + message);
            }
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        if (client.getLoggedInUser() != null)
            client.notifyUpdate();
    }

    private void addDish() throws IOException, ClassNotFoundException
    {
        client.getDishes().add((Dish)input.readObject());
    }

    private void editDish() throws IOException, ClassNotFoundException
    {
        Dish dish = (Dish)input.readObject();

        client.getDishes().remove(dish);
        client.getDishes().add(dish);
    }

    private void removeDish() throws IOException, ClassNotFoundException
    {
        client.getDishes().remove((Dish)input.readObject());
    }

    private void addPostcode() throws IOException, ClassNotFoundException
    {
        client.getPostcodes().add((Postcode)input.readObject());
    }

    private void editPostcode() throws IOException, ClassNotFoundException
    {
        Postcode postcode = (Postcode)input.readObject();

        client.getPostcodes().remove(postcode);
        client.getPostcodes().add(postcode);
    }

    private void removePostcode() throws IOException, ClassNotFoundException
    {
        client.getPostcodes().remove((Postcode)input.readObject());
    }

    private void updateOrder() throws IOException, ClassNotFoundException
    {
        Order order = (Order)input.readObject();

        client.getOrders(client.getLoggedInUser()).remove(order);
        client.getOrders(client.getLoggedInUser()).add(order);
    }

    private void completeOrder() throws IOException, ClassNotFoundException
    {
        Order order = (Order)input.readObject();

        client.getOrders(client.getLoggedInUser()).remove(order);
    }
}
