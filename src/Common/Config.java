package Common;
import java.io.*;
import java.util.*;

/**
 * Κλάση υπεύθυνη για τη φόρτωση των καθολικών ρυθμίσεων του συστήματος
 * από το αρχείο system.conf.
 */
public class Config {
    private static Properties props = new Properties();

    // Στατικό block που εκτελείται μία φορά κατά τη φόρτωση της κλάσης
    static {
        try (InputStream is = new FileInputStream("system.conf")) {
            props.load(is);
            System.out.println("[CONFIG] System configuration loaded successfully.");
        } catch (IOException e) {
            // Αν δεν βρεθεί το αρχείο, το σύστημα θα συνεχίσει με τις default τιμές
            System.err.println("[CONFIG] Warning: Could not load system.conf, using defaults.");
        }
    }

    // Επιστρέφει μια ρύθμιση ως String
    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    // Επιστρέφει μια ρύθμιση ως Integer
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}