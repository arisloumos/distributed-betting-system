package Common;
import java.io.Serializable;

public class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    public String gameName, providerName, gameLogoPath, riskLevel, betCategory, hashKey;
    public int stars, noOfVotes;
    public double minBet, maxBet, jackpot, totalProfitLoss = 0.0;
    public boolean isActive = true;
    
    // Πρέπει να είναι public για να γίνονται Serialize σωστά
    public int totalStarsSum = 0; 

    public Game(String name, String provider, int stars, int votes, String logo, double min, double max, String risk, String key) {
        this.gameName = name; this.providerName = provider; this.stars = stars;
        this.noOfVotes = votes; this.gameLogoPath = logo; this.minBet = min;
        this.maxBet = max; this.riskLevel = risk.toLowerCase(); this.hashKey = key;
        this.totalStarsSum = stars * votes;
        calculateAutomaticFields();
    }

    public void calculateAutomaticFields() {
        if (minBet >= 5.0) betCategory = "$$$";
        else if (minBet >= 1.0) betCategory = "$$";
        else betCategory = "$";
        if (riskLevel.equals("low")) jackpot = 10.0;
        else if (riskLevel.equals("medium")) jackpot = 20.0;
        else jackpot = 40.0;
    }

    public void updateJackpot() { calculateAutomaticFields(); }
    
    public void addRating(int r) {
        this.totalStarsSum += r;
        this.noOfVotes++;
        // Υπολογισμός μέσου όρου
        this.stars = (int) Math.round((double) totalStarsSum / noOfVotes);
    }

    @Override
    public String toString() {
        return String.format("Game: %-12s [%s] | Stars: %d (%d votes) | Risk: %-6s | Jackpot: x%.0f", 
                gameName, betCategory, stars, noOfVotes, riskLevel, jackpot);
    }
}