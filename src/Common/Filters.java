package Common;
import java.io.Serializable;

public class Filters implements Serializable {
    public int minStars = 0;
    public String betCategory = null; // $, $$, $$$
    public String riskLevel = null;   // low, medium, high

    public Filters(int stars, String bet, String risk) {
        this.minStars = stars;
        this.betCategory = bet;
        this.riskLevel = risk;
    }
}