package comp1206.sushi.server;

import comp1206.sushi.common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

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
                Socket socket = serverSocket.accept();

                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

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

    public void sendMessage(String message, Model model)
    {
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                for (Map.Entry entry : serverListeners.entrySet()) {
                    ServerListener serverListener = (ServerListener) entry.getKey();
                    ObjectOutputStream output = serverListener.getOutputStream();

                    output.writeObject(message);

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

                        default:
                            throw new IOException("Attempting to send unrecognised command - " + message);
                    }
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    private void sendMessage(String message, Model model, User user)
    {
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                for (Map.Entry entry : serverListeners.entrySet()) {
                    User loggedInUser = (User) entry.getValue();

                    if (loggedInUser.equals(user)) {
                        ServerListener serverListener = (ServerListener) entry.getKey();
                        ObjectOutputStream output = serverListener.getOutputStream();

                        output.writeObject(message);

                        switch (message) {
                            case "CHANGE ORDER STATUS":
                            case "COMPLETE ORDER":
                                output.writeObject((Order)model);
                                break;

                            default:
                                throw new IOException("Attempting to send unrecognised command - " + message);
                        }
                    }
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public void receiveMessage(String message, ServerListener serverListener)
    {
        synchronized (server)
        {
            message = message.toUpperCase().trim();

            try
            {
                ObjectInputStream input = serverListener.getInputStream();
                ObjectOutputStream output = serverListener.getOutputStream();

                switch (message)
                {
                    case "LOAD DATA":
                        loadData(input, output);
                        break;

                    case "LOGIN":
                        login(serverListener);
                        break;

                    case "REGISTER USER":
                        register(input, output);
                        break;

                    case "NEW ORDER":
                        newOrder(input, output);
                        break;

                    case "CANCEL ORDER":
                        cancelOrder(input, output);
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

    private void loadData(ObjectInputStream input, ObjectOutputStream output) throws IOException, ClassNotFoundException
    {
        output.writeObject(server.getRestaurant());
        output.writeObject(server.getPostcodes());
        output.writeObject(server.getDishes());
        output.writeObject(server.getUsers());
    }

    private void login(ServerListener serverListener) throws ClassNotFoundException, IOException
    {
        ObjectInputStream input = serverListener.getInputStream();
        serverListeners.put(serverListener, (User)input.readObject());
    }

    private void register(ObjectInputStream input, ObjectOutputStream output) throws ClassNotFoundException, IOException
    {
        server.getUsers().add((User)input.readObject());
    }

    private void newOrder(ObjectInputStream input, ObjectOutputStream output) throws ClassNotFoundException, IOException
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

    private void cancelOrder(ObjectInputStream input, ObjectOutputStream output) throws ClassNotFoundException, IOException
    {
        Order order = (Order)input.readObject();
        server.getOrders().get(server.getOrders().indexOf(order)).cancelOrder();
        User user = (User)input.readObject();

        for (User u : server.getUsers())
        {
            if (u.getName().equals(user.getName()))
            {
                u.getOrders().forEach(o -> {
                    if (o.getName().equals(order.getName()))
                        o.cancelOrder();
                });
            }
        }
    }

    public void removeServerListener(ServerListener serverListener)
    {
        serverListeners.remove(serverListener);
    }
}