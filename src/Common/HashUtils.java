package Common;
import java.security.MessageDigest;

/**
 * Βοηθητική κλάση για την παραγωγή SHA-256 hashes.
 * Χρησιμοποιείται για την επαλήθευση της επικοινωνίας Worker - SRG.
 */
public class HashUtils {
    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Μετατροπή του κειμένου σε bytes και υπολογισμός του hash
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            
            // Μετατροπή των bytes σε δεκαεξαδική μορφή (Hex String)
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Hashing error", ex);
        }
    }
}