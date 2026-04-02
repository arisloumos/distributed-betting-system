package Common;
import java.io.Serializable;

/**
 * Αντικείμενο που μεταφέρει τα κριτήρια αναζήτησης από τον Player στον Master
 * και στη συνέχεια στους Workers.
 */
public class Filters implements Serializable {
    private static final long serialVersionUID = 1L; // Για συμβατότητα κατά το Serialization
    
    public int minStars = 0;
    public String betCategory = null; // $, $$, $$$
    public String riskLevel = null;   // low, medium, high

    public Filters(int stars, String bet, String risk) {
        this.minStars = stars;
        this.betCategory = bet;
        this.riskLevel = risk;
    }
}