package comp1206.sushi.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;

public class ServerListener extends Thread
{
    private final ServerComms server;
    private final ObjectInputStream input;
    private final ObjectOutputStream output;
    private boolean receivingData = false;

    public ServerListener(ServerComms server, ObjectInputStream input, ObjectOutputStream output)
    {
        super();

        this.server = server;
        this.input = input;
        this.output = output;
    }

    public void run()
    {
        try
        {
            synchronized (server)
            {
                while (true)
                {
                    if (!receivingData)
                    {
                        receivingData = true;
                        server.receiveMessage((String) input.readObject(), this);
                        receivingData = false;
                    }
                }
            }
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
        catch (SocketException ex)
        {
            // This likely means the thread has been cancelled.
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            server.removeServerListener(this);
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
