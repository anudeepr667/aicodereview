import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

public class WebhookSecurityDemo {

    // Issue 1: Hardcoded credential
    private static final String DB_PASSWORD = "admin123";

    // Issue 2: SQL injection risk; also fails to use a parameterized query
    public static String findUser(Connection connection, String username)
            throws SQLException {
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql);

        if (result.next()) {
            return result.getString("username");
        }
        return null;
    }

    // Issue 3: Path traversal risk if filename is attacker-controlled
    public static byte[] readUpload(String filename) throws IOException {
        Path path = Paths.get("uploads").resolve(filename);
        return Files.readAllBytes(path);
    }

    // Issue 4: Insecure hash algorithm for security-sensitive use
    public static byte[] hashPassword(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return digest.digest(password.getBytes());
    }

    // Issue 5: Predictable random value if used as a security token
    public static int generateToken() {
        return new Random().nextInt(1_000_000);
    }

    // Issue 6: Command injection risk if userInput is untrusted
    public static void runCommand(String userInput) throws IOException {
        new ProcessBuilder("sh", "-c", userInput).start();
    }

    // Issue 7: Silently ignores errors, hiding failures
    public static String readConfig(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            return null;
        }
    }
}
