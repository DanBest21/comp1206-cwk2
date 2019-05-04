package comp1206.sushi.server;

import java.io.*;

public class DataPersistence
{
    private final Server server;

    private final File file;

    public DataPersistence(String filePath, Server server)
    {
        this.server = server;

        file = new File(filePath);
    }

    public synchronized void backupServer()
    {
        file.delete();

        try
        {
            file.createNewFile();
            ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(file));
            output.writeObject(server);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    public Server recoverServer()
    {
        try
        {
            ObjectInputStream input = new ObjectInputStream(new FileInputStream(file));
            return (Server) input.readObject();
        }
        catch (EOFException ex)
        {
            // Do nothing
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        return null;
    }
}