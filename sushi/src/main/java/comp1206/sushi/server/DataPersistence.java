package comp1206.sushi.server;

import java.io.*;

// DataPersistence class: Auxiliary class that frequently makes a backup of the Server object to a file.
public class DataPersistence
{
    private final Server server;
    private final File file;

    public DataPersistence(String filePath, Server server)
    {
        this.server = server;

        file = new File(filePath);
    }

    // backupServer(): Method that backs up the Server object by writing to it a file.
    public synchronized void backupServer()
    {
        try
        {
            ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(file, false));
            output.writeObject(server);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    // recoverServer(): Method that recovers the Server object by reading the file and then returning it.
    public Server recoverServer()
    {
        if (!file.exists())
            return null;

        try
        {
            ObjectInputStream input = new ObjectInputStream(new FileInputStream(file));
            return (Server)input.readObject();
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