package comp1206.sushi.common;

public interface Comms
{
    void sendMessage(String message, Model model);
    void sendMessage(String message, Model model, User user);
}
