package Master;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;
import Common.Filters;

public class MasterNode {
    private static final int MASTER_PORT = 1234;
    private static List<WorkerInfo> workers = new ArrayList<>();

    public static void main(String[] args) {
        // Ορισμός Workers
        workers.add(new WorkerInfo("localhost", 8001));
        workers.add(new WorkerInfo("localhost", 8002)); 

        System.out.println("Master Node started on port " + MASTER_PORT);

        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
            ) {
                String requestType = (String) in.readObject();

                if (requestType.equals("ADD_GAME")) {
                    System.out.println("[MASTER] Routing ADD_GAME request to appropriate Worker...");
                    
                    Game game = (Game) in.readObject();
                    int workerIdx = Math.abs(game.gameName.hashCode()) % workers.size();
                    WorkerInfo target = workers.get(workerIdx);
                    
                    String response = forwardToWorker(target, "ADD_GAME", game);
                    out.writeUTF(response);
                } 
                else if (requestType.equals("SEARCH")) {
                    System.out.println("[MASTER] Executing MapReduce Search with filters...");

                    // Λήψη φίλτρων από τον παίκτη
                    Filters filters = (Filters) in.readObject();
                    
                    // MAP PHASE: Αποστολή φίλτρων σε όλους τους Workers
                    List<Game> allGames = new ArrayList<>();
                    for (WorkerInfo w : workers) {
                        List<Game> workerGames = forwardToWorkerSearch(w, filters);
                        if (workerGames != null) allGames.addAll(workerGames);
                    }
                    // REDUCE PHASE: Επιστροφή συγκεντρωτικών αποτελεσμάτων
                    out.writeObject(allGames);
                }
                else if (requestType.equals("PLAY")) {
                    System.out.println("[MASTER] Routing PLAY request to appropriate Worker...");

                    String playerId = in.readUTF(); // Λήψη ID παίκτη
                    String gameName = in.readUTF();
                    double amount = in.readDouble();
                    
                    int workerIdx = Math.abs(gameName.hashCode()) % workers.size();
                    WorkerInfo target = workers.get(workerIdx);
                    
                    String result = forwardToWorkerPlay(target, playerId, gameName, amount);
                    out.writeUTF(result);
                }
                else if (requestType.equals("STATS")) {
                    System.out.println("[MASTER] Executing MapReduce Aggregation for Stats...");

                    // MAPREDUCE ΓΙΑ ΣΤΑΤΙΣΤΙΚΑ
                    Map<String, Double> providerStats = new HashMap<>();
                    Map<String, Double> playerStats = new HashMap<>();

                    for (WorkerInfo w : workers) {
                        // Ζητάμε stats από κάθε Worker
                        Map<String, Object> wData = forwardToWorkerStats(w);
                        if (wData != null) {
                            Map<String, Game> wGames = (Map<String, Game>) wData.get("games");
                            Map<String, Double> wPlayers = (Map<String, Double>) wData.get("players");

                            // Reduce για Providers (από τα Games)
                            for (Game g : wGames.values()) {
                                providerStats.put(g.providerName, providerStats.getOrDefault(g.providerName, 0.0) + g.totalProfitLoss);
                            }
                            // Reduce για Players
                            for (Map.Entry<String, Double> entry : wPlayers.entrySet()) {
                                playerStats.put(entry.getKey(), playerStats.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                            }
                        }
                    }
                    out.writeObject(providerStats);
                    out.writeObject(playerStats);
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private String forwardToWorker(WorkerInfo w, String cmd, Game game) {
            try (Socket s = new Socket(w.host, w.port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject(cmd);
                out.writeObject(game);
                return in.readUTF();
            } catch (Exception e) { return "Worker Error"; }
        }

        @SuppressWarnings("unchecked")
        private List<Game> forwardToWorkerSearch(WorkerInfo w, Filters f) {
            try (Socket s = new Socket(w.host, w.port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject("SEARCH");
                out.writeObject(f);
                return (List<Game>) in.readObject();
            } catch (Exception e) { return null; }
        }

        private String forwardToWorkerPlay(WorkerInfo w, String pId, String gName, double amt) {
            try (Socket s = new Socket(w.host, w.port);
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject("PLAY");
                out.writeUTF(pId);
                out.writeUTF(gName);
                out.writeDouble(amt);
                out.flush();
                return in.readUTF();
            } catch (Exception e) { return "Worker Error"; }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> forwardToWorkerStats(WorkerInfo w) {
            try (Socket s = new Socket(w.host, w.port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject("GET_STATS");
                Map<String, Game> games = (Map<String, Game>) in.readObject();
                Map<String, Double> players = (Map<String, Double>) in.readObject();
                
                Map<String, Object> result = new HashMap<>();
                result.put("games", games);
                result.put("players", players);
                return result;
            } catch (Exception e) { return null; }
        }
    }

    static class WorkerInfo {
        String host;
        int port;
        WorkerInfo(String h, int p) { this.host = h; this.port = p; }
    }
}