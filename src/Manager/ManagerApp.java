package Manager;

import java.io.*;
import java.net.*;
import Common.Game;

public class ManagerApp {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 1234;

    public static void main(String[] args) {
        // Ελέγχουμε αν έδωσες όνομα στο terminal
        String nameToRegister = (args.length > 0) ? args[0] : "DefaultGame";

        System.out.println("--- Manager Console App ---");

        // Χρησιμοποιούμε το nameToRegister αντί για το hardcoded "MegaFortune"
        Game testGame = new Game(
            nameToRegister, 
            "NetEnt", 
            4, 
            100, 
            "/images/logo.png", 
            0.1, 
            10.0, 
            "medium", 
            "mySecret123"
        );

        // 2. Σύνδεση στον Master
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            System.out.println("Connected to Master. Sending game...");

            // 3. Στέλνουμε την εντολή και το αντικείμενο
            out.writeObject("ADD_GAME");
            out.writeObject(testGame);
            out.flush();

            // 4. Λήψη απάντησης
            String response = in.readUTF();
            System.out.println("Master says: " + response);

        } catch (Exception e) {
            System.err.println("Error: Could not connect to Master.");
            e.printStackTrace();
        }
    }
}