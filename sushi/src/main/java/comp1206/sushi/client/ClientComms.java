package comp1206.sushi.client;

import comp1206.sushi.common.*;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

// ClientComms class: Handles the client-side communication between the client and the server.
public class ClientComms extends Thread implements Comms
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
        catch (ConnectException ex)
        {
            System.err.println("Error: Server at " + SERVER_ADDRESS +  " on port number " + PORT_NUMBER + " could not be reached.");
            System.exit(1);
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
            // While the thread hasn't been interrupted.
            while (!Thread.currentThread().isInterrupted())
            {
                // Synchronise the code with the client so that the thread can be stopped whilst sending data.
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

                // Otherwise receive a message.
                receiveMessage();
            }
        }
        finally
        {
            // Always close the socket, input and output streams when the thread is stopped, and then exit the application.
            try
            {
                input.close();
                output.close();
                socket.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            System.err.println("Error: Connection to server at " + SERVER_ADDRESS +  " on port number " + PORT_NUMBER + " lost - terminating client application.");
            System.exit(2);
        }
    }

    // sendMessage(String, Model): Sends the passed message and the model to the server.
    public void sendMessage(String message, Model model)
    {
        // Synchronised on the client in order to stop data from being received at the same time it is sent.
        synchronized (client)
        {
            sendingData = true;
            message = message.toUpperCase().trim();

            try
            {
                // Send the message to the server.
                output.writeObject(message);

                // Send the appropriate object to the server.
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

                // Reset the ObjectOutputStream to ensure changes to the objects are recognised.
                output.reset();
            }
            catch (ClassNotFoundException ex)
            {
                ex.printStackTrace();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            // Call the notify method to begin receiving messages again.
            sendingData = false;
            client.notify();
        }
    }

    // sendMessage(String, Model, User): Sends data to the server in regards to the specified User.
    public void sendMessage(String message, Model model, User user)
    {
        // Synchronised on the client in order to stop data from being received at the same time it is sent.
        synchronized (client)
        {
            sendingData = true;
            message = message.toUpperCase().trim();

            try
            {
                // Send the message to the server.
                output.writeObject(message);

                // Send the appropriate object to the server.
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

                // Reset the ObjectOutputStream to ensure changes to the objects are recognised.
                output.reset();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            // Call the notify method to begin receiving messages again.
            sendingData = false;
            client.notify();
        }
    }

    // loadData(): Loads the initial data based on the response from the server.
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

    // receiveMessage(): Receives any messages from the Server.
    private void receiveMessage()
    {
        try
        {
            // Get the message from the server.
            String message = (String)input.readObject();
            message = message.toUpperCase().trim();

            // Call the correct auxiliary method based on the message from the server.
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

                case "ADD USER":
                    addUser();
                    break;

                case "REMOVE USER":
                    removeUser();
                    break;

                case "CHANGE ORDER STATUS":
                    updateOrder();
                    break;

                case "COMPLETE ORDER":
                    removeOrder();
                    break;

                case "CLEAR DATA":
                    clearData();
                    break;

                default:
                    throw new IOException("Unrecognised message received - " + message);
            }
        }
        catch (SocketException ex)
        {
            // If a SocketException is found and contains the word reset, then the connection to the server has been lost and should be handled appropriately.
            if (ex.getMessage().contains("reset"))
            {
                interrupt();
            }
            else
            {
                ex.printStackTrace();
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

        // If the client is logged in, then we can safely call the notifyUpdate() function to update the user interface.
        if (client.getLoggedInUser() != null)
            client.notifyUpdate();
    }

    // addDish(): Adds the passed Dish object to the client.
    private void addDish() throws IOException, ClassNotFoundException
    {
        client.getDishes().add((Dish)input.readObject());
    }

    // editDish(): Removes the passed Dish object and then adds it back to the client.
    private void editDish() throws IOException, ClassNotFoundException
    {
        Dish dish = removeDish();
        client.getDishes().add(dish);
    }

    // removeDish(): Removes the passed Dish object based on it's name.
    private Dish removeDish() throws IOException, ClassNotFoundException
    {
        Dish dish = (Dish)input.readObject();
        Dish clientDish = null;

        for (Dish d : client.getDishes())
        {
            if (d.getName().equals(dish.getName()))
            {
                clientDish = d;
            }
        }

        client.getDishes().remove(clientDish);
        return dish;
    }

    // addPostcode(): Adds the passed Postcode object to the client.
    private void addPostcode() throws IOException, ClassNotFoundException
    {
        client.getPostcodes().add((Postcode)input.readObject());
    }

    // editPostcode(): Removes the passed Postcode object and then adds it back to the client.
    private void editPostcode() throws IOException, ClassNotFoundException
    {
        Postcode postcode = removePostcode();
        client.getPostcodes().add(postcode);
    }

    // removePostcode(): Removes the passed Postcode object based on it's name.
    private Postcode removePostcode() throws IOException, ClassNotFoundException
    {
        Postcode postcode = (Postcode)input.readObject();
        Postcode clientPostcode = null;

        for (Postcode p : client.getPostcodes())
        {
            if (p.getName().equals(postcode.getName()))
            {
                clientPostcode = p;
            }
        }

        client.getPostcodes().remove(clientPostcode);
        return postcode;
    }

    // addUser(): Adds the passed User object to the client.
    private void addUser() throws IOException, ClassNotFoundException
    {
        client.getUsers().add((User)input.readObject());
    }

    // removeUser(): Removes the passed User object based on it's name.
    private void removeUser() throws IOException, ClassNotFoundException
    {
        User user = (User)input.readObject();
        User clientUser = null;

        for (User u : client.getUsers())
        {
            if (u.getName().equals(user.getName()))
            {
                clientUser = u;
            }
        }

        client.getUsers().remove(clientUser);
    }

    // updateOrder(): Removes the passed Order object and then adds it back to the list of orders of the logged in user.
    private void updateOrder() throws IOException, ClassNotFoundException
    {
        Order order = removeOrder();
        client.getOrders(client.getLoggedInUser()).add(order);
    }

    // removeOrder(): Removes the passed Order object, based on it's name, from the list of orders of the logged in user.
    private Order removeOrder() throws IOException, ClassNotFoundException
    {
        Order order = (Order)input.readObject();
        Order clientOrder = null;

        for (Order o : client.getOrders(client.getLoggedInUser()))
        {
            if (o.getName().equals(order.getName()))
            {
                clientOrder = o;
                break;
            }
        }

        client.getOrders(client.getLoggedInUser()).remove(clientOrder);

        return order;
    }

    // clearData(): Clears all of the data loaded from the server - used when loading a new configuration.
    private void clearData()
    {
        client.setRestaurant(null);
        client.getPostcodes().clear();
        client.getDishes().clear();
        client.getUsers().clear();
    }
}