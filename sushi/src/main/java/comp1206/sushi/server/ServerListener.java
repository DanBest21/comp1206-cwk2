package comp1206.sushi.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;

// ServerListener class: Class that monitors the socket of an individual Client connection to the comms.
public class ServerListener extends Thread
{
    private final ServerComms comms;
    private final ObjectInputStream input;
    private final ObjectOutputStream output;
    private boolean receivingData = false;

    public ServerListener(ServerComms comms, ObjectInputStream input, ObjectOutputStream output)
    {
        super();

        this.comms = comms;
        this.input = input;
        this.output = output;
    }

    public void run()
    {
        try
        {
            while (true)
            {
                // If data is already being received, don't call receiveMessage again yet.
                if (!receivingData)
                {
                    receivingData = true;
                    comms.receiveMessage((String) input.readObject(), this);
                    receivingData = false;
                }
            }
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
        catch (SocketException ex)
        {
            // This likely means the thread has been cancelled, so do nothing and let the finally handle removing the listener.
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            comms.removeServerListener(this);
        }
    }

    public ObjectInputStream getInputStream()
    {
        return input;
    }

    public ObjectOutputStream getOutputStream()
    {
        return output;
    }
}
