package Common;
import java.io.*;
import java.util.*;

public class Config {
    private static Properties props = new Properties();

    static {
        try (InputStream is = new FileInputStream("system.conf")) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("Could not load system.conf, using defaults.");
        }
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
    }
}