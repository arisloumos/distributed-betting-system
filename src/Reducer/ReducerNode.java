package Reducer;
import java.io.*;
import java.net.*;
import java.util.*;

public class ReducerNode {
    private static Map<String, Map<String, Double>> providerData = new HashMap<>();
    private static Map<String, Double> playerData = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(4444);
        System.out.println("Reducer Node started on port 4444");
        while (true) {
            Socket s = ss.accept();
            new Thread(() -> {
                try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    String cmd = (String) in.readObject();

                    if (cmd.equals("RESET_STATS")) {
                        synchronized(providerData) { providerData.clear(); playerData.clear(); }
                        System.out.println("[REDUCER] Stats reset for new MapReduce job.");
                    } 
                    else if (cmd.equals("MAP_DATA")) {
                        Map<String, Double> wGames = (Map<String, Double>) in.readObject();
                        Map<String, String> wGameProviders = (Map<String, String>) in.readObject();
                        Map<String, Double> wPlayers = (Map<String, Double>) in.readObject();

                        synchronized(providerData) {
                            for (String gName : wGames.keySet()) {
                                String prov = wGameProviders.get(gName);
                                providerData.putIfAbsent(prov, new HashMap<>());
                                providerData.get(prov).put(gName, wGames.get(gName));
                            }
                        }
                        synchronized(playerData) {
                            wPlayers.forEach((k, v) -> playerData.merge(k, v, Double::sum));
                        }
                        System.out.println("[REDUCER] MAP Phase: Received intermediate data from a Worker.");
                        out.writeUTF("OK");
                        out.flush();
                    } 
                    else if (cmd.equals("GET_REDUCED_RESULTS")) {
                        out.writeObject(new HashMap<>(providerData));
                        out.writeObject(new HashMap<>(playerData));
                        out.flush();
                        System.out.println("[REDUCER] REDUCE Phase: Sent final aggregated results to Master.");
                    }
                } catch (Exception e) {}
            }).start();
        }
    }
}