package Common;
import java.io.Serializable;

/**
 * Η κεντρική οντότητα του παιχνιδιού. 
 * Περιέχει πληροφορίες, στατιστικά και τη λογική υπολογισμού αυτόματων πεδίων.
 */
public class Game implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String gameName, providerName, gameLogoPath, riskLevel, betCategory, hashKey;
    public double stars; // Χρήση double για ακρίβεια στον μέσο όρο βαθμολογίας
    public int noOfVotes;
    public double minBet, maxBet, jackpot, totalProfitLoss = 0.0;
    public boolean isActive = true; // Flag για το "Remove Game" (soft delete)
    public int totalStarsSum = 0; 

    public Game(String name, String provider, int stars, int votes, String logo, double min, double max, String risk, String key) {
        this.gameName = name; 
        this.providerName = provider; 
        this.noOfVotes = votes; 
        this.gameLogoPath = logo; 
        this.minBet = min;
        this.maxBet = max; 
        this.riskLevel = risk.toLowerCase(); 
        this.hashKey = key;
        
        this.totalStarsSum = stars * votes;
        // Υπολογισμός αρχικού μέσου όρου
        this.stars = (votes > 0) ? (double) totalStarsSum / votes : stars;
        
        calculateAutomaticFields();
    }

    /**
     * Υπολογίζει αυτόματα την κατηγορία πονταρίσματος και το Jackpot 
     * βάσει των κανόνων της εκφώνησης.
     */
    public void calculateAutomaticFields() {
        // Κατηγορία πονταρίσματος βάσει ελάχιστου στοιχήματος
        if (minBet >= 5.0) betCategory = "$$$";
        else if (minBet >= 1.0) betCategory = "$$";
        else betCategory = "$";
        
        // Jackpot βάσει επιπέδου ρίσκου
        if (riskLevel.equals("low")) jackpot = 10.0;
        else if (riskLevel.equals("medium")) jackpot = 20.0;
        else jackpot = 40.0;
    }

    public void updateJackpot() { 
        calculateAutomaticFields(); 
    }
    
    /**
     * Προσθέτει μια νέα βαθμολογία και ενημερώνει τον μέσο όρο.
     */
    public void addRating(int r) {
        if (r < 1 || r > 5) return;
        this.totalStarsSum += r;
        this.noOfVotes++;
        this.stars = (double) totalStarsSum / noOfVotes;
        System.out.println("[GAME] " + gameName + " rated with " + r + " stars. New avg: " + String.format("%.2f", stars));
    }

    @Override
    public String toString() {
        return String.format("Game: %-15s [%s] | Limits: %.2f - %.2f | Rating: %.1f/5 | Risk: %-6s | Jackpot: x%.0f", 
                gameName, betCategory, minBet, maxBet, stars, riskLevel, jackpot);
    }
}