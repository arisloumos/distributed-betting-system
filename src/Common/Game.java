package Common;

import java.io.Serializable;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;

    // Πεδία από το JSON
    public String gameName;
    public String providerName;
    public int stars;
    public int noOfVotes;
    public String gameLogoPath;
    public double minBet;
    public double maxBet;
    public String riskLevel; // low, medium, high
    public String hashKey;   // Το secret S για τον Random Generator

    // Πεδία που υπολογίζονται αυτόματα
    public String betCategory; // $, $$, $$$
    public double jackpot;
    public double totalProfitLoss = 0.0;

    public Game(String gameName, String providerName, int stars, int noOfVotes, 
                String gameLogoPath, double minBet, double maxBet, String riskLevel, String hashKey) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.gameLogoPath = gameLogoPath;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.riskLevel = riskLevel.toLowerCase();
        this.hashKey = hashKey;

        // Αυτόματος υπολογισμός Κατηγορίας Πονταρίσματος
        if (minBet >= 5.0) {
            this.betCategory = "$$$";
        } else if (minBet >= 1.0) {
            this.betCategory = "$$";
        } else {
            this.betCategory = "$";
        }

        // Αυτόματος υπολογισμός Jackpot βάσει Risk Level
        if (this.riskLevel.equals("low")) {
            this.jackpot = 10.0;
        } else if (this.riskLevel.equals("medium")) {
            this.jackpot = 20.0;
        } else if (this.riskLevel.equals("high")) {
            this.jackpot = 40.0;
        }
    }

    @Override
    public String toString() {
        return "Game: " + gameName + " [" + betCategory + "] | Risk: " + riskLevel + 
               " | Stars: " + stars + " | Jackpot: x" + jackpot;
    }
}