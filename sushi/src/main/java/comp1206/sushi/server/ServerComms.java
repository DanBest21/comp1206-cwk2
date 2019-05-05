package comp1206.sushi.server;

import comp1206.sushi.common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

// ServerComms class: Handles the server-side communications between the clients and the server.
public class ServerComms extends Thread
{
    private static final int PORT_NUMBER = 2066;
    private final Map<ServerListener, User> serverListeners = new HashMap<>();
    private ServerSocket serverSocket;
    private final Server server;

    public ServerComms(Server server)
    {
        super();

        this.server = server;

        try
        {
            serverSocket = new ServerSocket(PORT_NUMBER);
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
                // Wait for a new connection.
                Socket socket = serverSocket.accept();

                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

                // Create a new ServerListener for each connection to handle any messages received from that client.
                ServerListener serverListener = new ServerListener(this, input, output);
                serverListeners.put(serverListener, null);
                serverListener.start();
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            // Always close the ServerSocket when the thread is stopped.
            try
            {
                serverSocket.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    // sendMessage(String, Model): Sends the passed message and object to all currently connected clients.
    public void sendMessage(String message, Model model)
    {
        // Synchronize the server so a conflict cannot occur.
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                for (Map.Entry entry : serverListeners.entrySet()) {
                    ServerListener serverListener = (ServerListener) entry.getKey();
                    ObjectOutputStream output = serverListener.getOutputStream();

                    // Write the message to tell the client what data to expect and what to do with it.
                    output.writeObject(message);

                    // Select the correct command, and send the data to the client.
                    switch (message)
                    {
                        case "ADD DISH":
                        case "EDIT DISH":
                        case "REMOVE DISH":
                            output.writeObject((Dish)model);
                            break;

                        case "ADD POSTCODE":
                        case "EDIT POSTCODE":
                        case "REMOVE POSTCODE":
                            output.writeObject((Postcode)model);
                            break;

                        case "ADD USER":
                        case "REMOVE USER":
                            output.writeObject((User)model);
                            break;

                        // The message alone will suffice.
                        case "CLEAR DATA":
                            break;

                        default:
                            throw new IOException("Attempting to send unrecognised command - " + message);
                    }

                    // Reset the ObjectOutputStream in order to ensure changes to objects update correctly.
                    output.reset();
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    // sendMessage(String, Model, User): Sends the message and the model to the client of the user specified.
    public void sendMessage(String message, Model model, User user)
    {
        // Synchronize the server so a conflict cannot occur.
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                for (Map.Entry entry : serverListeners.entrySet()) {
                    User loggedInUser = (User) entry.getValue();

                    // If the loggedInUser of the client is equal to the user we want to send the message and data to, continue.
                    if (loggedInUser.getName().equals(user.getName())) {
                        ServerListener serverListener = (ServerListener) entry.getKey();
                        ObjectOutputStream output = serverListener.getOutputStream();

                        // Write the message to tell the client what data to expect and what to do with it.
                        output.writeObject(message);

                        // Select the correct command, and send the data to the client.
                        switch (message)
                        {
                            case "CHANGE ORDER STATUS":
                            case "COMPLETE ORDER":
                                output.writeObject((Order)model);
                                break;

                            default:
                                throw new IOException("Attempting to send unrecognised command - " + message);
                        }

                        // Reset the ObjectOutputStream in order to ensure changes to objects update correctly.
                        output.reset();
                    }
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    // receiveMessage(String, ServerListener): Handles the retrieval of a message from the specified ServerListener.
    public void receiveMessage(String message, ServerListener serverListener)
    {
        // Synchronize the server so a conflict cannot occur.
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                ObjectInputStream input = serverListener.getInputStream();
                ObjectOutputStream output = serverListener.getOutputStream();

                // Based on the message, decide what to do with the passed data.
                switch (message)
                {
                    case "LOAD DATA":
                        loadData(output);
                        break;

                    case "LOGIN":
                        login(serverListener);
                        break;

                    case "REGISTER USER":
                        register(input, serverListener);
                        break;

                    case "NEW ORDER":
                        newOrder(input);
                        break;

                    case "CANCEL ORDER":
                        cancelOrder(input);
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

            server.notifyUpdate();
        }
    }

    // loadData(ObjectOutputStream): Send across the Restaurant, Postcode, Dish and User data to the client.
    private void loadData(ObjectOutputStream output) throws IOException
    {
        output.writeObject(server.getRestaurant());
        output.writeObject(server.getPostcodes());
        output.writeObject(server.getDishes());
        output.writeObject(server.getUsers());
    }

    // login(ServerListener): Associate the passed ServerListener to the read User object in the serverListeners map, so that data can be sent to a specific client at a later point.
    private void login(ServerListener serverListener) throws ClassNotFoundException, IOException
    {
        ObjectInputStream input = serverListener.getInputStream();
        serverListeners.put(serverListener, (User)input.readObject());
    }

    // register(ObjectInputStream): Add the passed User object to the list of users on the server.
    private void register(ObjectInputStream input, ServerListener serverListener) throws ClassNotFoundException, IOException
    {
        User user = (User)input.readObject();
        server.getUsers().add(user);
        serverListeners.put(serverListener, user);
    }

    // newOrder(ObjectInputStream): Add the passed Order object to the list of orders on the server, and associate the order to the logged in user in the list of users.
    private void newOrder(ObjectInputStream input) throws ClassNotFoundException, IOException
    {
        Order order = (Order)input.readObject();
        server.getOrders().add(order);
        User user = (User)input.readObject();

        for (User u : server.getUsers())
        {
            if (u.getName().equals(user.getName()))
            {
                u.getOrders().add(order);
                break;
            }
        }
    }

    // cancelOrder(ObjectInputStream): Removes and re-adds the passed order so that it's status is updated to "Cancelled", then does the same to the order in the User object.
    private void cancelOrder(ObjectInputStream input) throws ClassNotFoundException, IOException
    {
        Order order = (Order)input.readObject();
        Order serverOrder = null;

        for (Order o : server.getOrders())
        {
            if (o.getName().equals(order.getName()))
            {
                serverOrder = o;
            }
        }

        server.getOrders().remove(serverOrder);
        server.getOrders().add(order);

        User user = (User)input.readObject();

        for (User u : server.getUsers())
        {
            if (u.getName().equals(user.getName()))
            {
                u.getOrders().remove(serverOrder);
                u.getOrders().add(order);
                break;
            }
        }
    }

    // removeServerListener(ServerListener): Removes the specified ServerListener object from the serverListeners map.
    public void removeServerListener(ServerListener serverListener)
    {
        serverListeners.remove(serverListener);
    }
}