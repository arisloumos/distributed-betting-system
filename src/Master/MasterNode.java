package Master;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import Common.*;

public class MasterNode {
    private static final int MASTER_PORT = 1234;
    private static final String REDUCER_IP = Config.get("REDUCER_IP", "localhost");
    private static final int REDUCER_PORT = Config.getInt("REDUCER_PORT", 4444);
    private static List<WorkerInfo> workers = new ArrayList<>();
    private static Map<String, Double> playerBalances = new HashMap<>();

    public static void main(String[] args) {
        // 1. Δυναμική Φόρτωση Workers από το αρχείο workers.conf
        try {
            // Χρησιμοποιούμε τη Files.readAllLines για να διαβάσουμε το config
            java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("workers.conf"));
            for (String line : lines) {
                if (line.trim().isEmpty()) continue; // Αγνοούμε κενές γραμμές
                String[] parts = line.split(":");
                workers.add(new WorkerInfo(parts[0].trim(), Integer.parseInt(parts[1].trim())));
            }
            System.out.println("Loaded " + workers.size() + " workers from config.");
        } catch (Exception e) {
            System.err.println("Could not load workers.conf. Using default localhost:8001");
            workers.add(new WorkerInfo("localhost", 8001));
        }

        // 2. Εκκίνηση του Server - ΑΥΤΟ ΤΟ ΚΟΜΜΑΤΙ ΚΡΑΤΑΕΙ ΤΟΝ SERVER ΖΩΝΤΑΝΟ
        System.out.println("Master started on port " + MASTER_PORT);

        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            while (true) {
                // Ο server σταματάει εδώ και περιμένει σύνδεση (accept)
                Socket clientSocket = serverSocket.accept();
                // Μόλις συνδεθεί κάποιος, ξεκινάει νέο Thread
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Master Server Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket s;
        public ClientHandler(Socket s) { this.s = s; }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                String type = (String) in.readObject();
                System.out.println("[MASTER] Processing: " + type);

                if (type.equals("ADD_GAME")) {
                    Game g = (Game) in.readObject();
                    int idx = Math.abs(g.gameName.hashCode()) % workers.size();
                    
                    // Fix Γ3: Έλεγχος πριν την προσθήκη
                    if (checkIfGameExists(workers.get(idx), g.gameName)) {
                        out.writeUTF("REJECTED: A game with name '" + g.gameName + "' already exists.");
                    } else {
                        out.writeUTF(forwardAdd(workers.get(idx), g));
                    }
                }
                // ΕΔΩ ΕΙΝΑΙ Η ΜΕΓΑΛΗ ΑΛΛΑΓΗ: Μία μέθοδος για όλα τα Updates
                else if (type.equals("REMOVE_GAME") || type.equals("EDIT_GAME") || type.equals("RATE_GAME")) {
                    String name = in.readUTF();
                    int idx = Math.abs(name.hashCode()) % workers.size();
                    out.writeUTF(forwardUpdateToWorker(workers.get(idx), type, name, in));
                }
                else if (type.equals("SEARCH")) {
                    Filters f = (Filters) in.readObject();
                    List<Game> allResults = new ArrayList<>();
                    for (WorkerInfo w : workers) {
                        allResults.addAll(forwardSearch(w, f));
                    }
                    out.writeObject(allResults);
                }
                else if (type.equals("STATS")) {
                    try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
                        ObjectOutputStream outR = new ObjectOutputStream(s.getOutputStream())) {
                        outR.writeObject("SET_WORKER_COUNT");
                        outR.writeInt(workers.size()); // Δυναμικός αριθμός από το workers.conf
                        outR.flush();
                    } catch (Exception e) { e.printStackTrace(); }

                    callReducer("RESET_STATS");
                    for (WorkerInfo w : workers) {
                        forwardSimpleCmd(w, "PUSH_TO_REDUCER"); 
                    }

                    Map<String, Object> results = getResultsFromReducer();
                    out.writeObject(results.get("providers"));
                    out.writeObject(results.get("players"));
                }
                else if (type.equals("PLAY")) {
                    String pId = in.readUTF();
                    String gName = in.readUTF();
                    double amt = in.readDouble();

                    // Fix Γ2: Έλεγχος εγκυρότητας ποσού
                    if (amt <= 0) {
                        out.writeUTF("REJECTED: Bet amount must be positive.");
                    } else {
                        synchronized(playerBalances) {
                            double currentBal = playerBalances.getOrDefault(pId, 0.0);
                            
                            if (currentBal < amt) {
                                out.writeUTF("REJECTED: Insufficient Balance. Current: " + currentBal + " tokens.");
                            } else {
                                // 1. Προσωρινή αφαίρεση του πονταρίσματος
                                playerBalances.put(pId, currentBal - amt);
                                
                                int idx = Math.abs(gName.hashCode()) % workers.size();
                                
                                // 2. Κλήση του Worker
                                double winAmount = forwardPlayToWorkerNumeric(workers.get(idx), pId, gName, amt);
                                
                                // 3. Διαχείριση αποτελέσματος
                                if (winAmount >= 0) {
                                    // Επιτυχία: Προσθήκη κέρδους (αν έχασε, το winAmount είναι 0)
                                    playerBalances.put(pId, playerBalances.get(pId) + winAmount);
                                    
                                    String resultMsg;
                                    if (winAmount > amt) resultMsg = "WIN " + String.format("%.2f", winAmount);
                                    else if (winAmount == amt) resultMsg = "DRAW";
                                    else resultMsg = "LOSS";
                                    
                                    out.writeUTF(resultMsg + " | New Balance: " + String.format("%.2f", playerBalances.get(pId)));
                                } else {
                                    // Fix Γ1: Σφάλμα στον Worker ή SRG (winAmount < 0)
                                    // Επιστροφή χρημάτων στον παίκτη (Refund)
                                    playerBalances.put(pId, playerBalances.get(pId) + amt);
                                    
                                    String errorType = (winAmount == -1.0) ? "Game not found" : 
                                                      (winAmount == -2.0) ? "Security/Hash Error" : "Server Error";
                                    out.writeUTF("ERROR: " + errorType + ". Your balance was restored.");
                                }
                            }
                        }
                    }
                }
                else if (type.equals("ADD_BALANCE")) {
                    String pId = in.readUTF();
                    double amount = in.readDouble();
                    synchronized(playerBalances) {
                        playerBalances.put(pId, playerBalances.getOrDefault(pId, 0.0) + amount);
                    }
                    out.writeUTF("New Balance for " + pId + ": " + playerBalances.get(pId) + " tokens.");
                }
                else if (type.equals("GET_BALANCE")) {
                    String pId = in.readUTF();
                    out.writeUTF("Your current balance: " + playerBalances.getOrDefault(pId, 0.0) + " tokens.");
                }

                out.flush();
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Η "έξυπνη" μέθοδος που προωθεί τα πάντα σωστά
        private String forwardUpdateToWorker(WorkerInfo w, String type, String name, ObjectInputStream clientIn) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                
                outW.writeObject(type);
                outW.writeUTF(name);
                
                if (type.equals("EDIT_GAME")) {
                    String newRisk = clientIn.readUTF();
                    outW.writeUTF(newRisk);
                } else if (type.equals("RATE_GAME")) {
                    int stars = clientIn.readInt();
                    outW.writeInt(stars);
                }
                
                outW.flush();
                return inW.readUTF();
            } catch (Exception e) { return "Worker Error"; }
        }

        private String forwardAdd(WorkerInfo w, Game g) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("ADD_GAME");
                outW.writeObject(g);
                outW.flush();
                return inW.readUTF();
            } catch (Exception e) { return "Worker Error"; }
        }

        private boolean checkIfGameExists(WorkerInfo w, String gameName) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                
                // Στέλνουμε ένα SEARCH χωρίς φίλτρα για να πάρουμε όλα τα παιχνίδια του Worker
                outW.writeObject("SEARCH");
                outW.writeObject(new Filters(0, null, null));
                outW.flush();
                
                List<Game> games = (List<Game>) inW.readObject();
                for (Game g : games) {
                    if (g.gameName.equalsIgnoreCase(gameName)) {
                        return true; // Το παιχνίδι βρέθηκε
                    }
                }
            } catch (Exception e) {
                return false; // Σε περίπτωση σφάλματος επικοινωνίας, υποθέτουμε ότι δεν υπάρχει
            }
            return false;
        }

        private List<Game> forwardSearch(WorkerInfo w, Filters f) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("SEARCH");
                outW.writeObject(f);
                outW.flush();
                return (List<Game>) inW.readObject();
            } catch (Exception e) { return new ArrayList<>(); }
        }

        private void forwardSimpleCmd(WorkerInfo w, String cmd) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject(cmd);
                outW.flush();
                inW.readUTF(); // Περιμένει το "DATA_SENT" για συγχρονισμό
            } catch (Exception e) {}
        }

        private double forwardPlayToWorkerNumeric(WorkerInfo w, String pId, String gName, double amt) {
            try (Socket sock = new Socket(w.host, w.port);
                ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                
                outW.writeObject("PLAY");
                outW.writeUTF(pId); 
                outW.writeUTF(gName); 
                outW.writeDouble(amt);
                outW.flush();
                
                // Διαβάζουμε double από τον Worker
                return inW.readDouble(); 
            } catch (Exception e) { 
                return -3.0; // Error code ως double
            }
        }
    }

    private static void callReducer(String cmd) {
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(cmd);
            out.flush();
        } catch (Exception e) {}
    }

    private static Map<String, Object> getResultsFromReducer() {
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            out.writeObject("GET_REDUCED_RESULTS");
            out.flush();
            Map<String, Object> res = new HashMap<>();
            res.put("providers", in.readObject());
            res.put("players", in.readObject());
            return res;
        } catch (Exception e) { return new HashMap<>(); }
    }
}