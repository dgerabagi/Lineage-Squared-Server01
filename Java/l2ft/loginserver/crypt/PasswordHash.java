package l2ft.loginserver.crypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mindrot.jbcrypt.BCrypt;

public class PasswordHash {
    private final static Logger _log = LoggerFactory.getLogger(PasswordHash.class);

    // Optional: Store the algorithm name, even if unused
    private String algorithm;

    public PasswordHash(String algorithm) {
        // Since we're only using bcrypt now, the algorithm parameter is not used
        // Keeping it for compatibility with existing code
        this.algorithm = algorithm;
    }

    /**
     * Hashes the password using bcrypt.
     *
     * @param password The plaintext password to hash.
     * @return The bcrypt hash of the password.
     */
    public String encrypt(String password) {
        // Adjust the salt rounds if needed (currently set to 10)
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    /**
     * Compares the provided plaintext password with the expected hashed password.
     *
     * @param password The plaintext password entered by the user.
     * @param expected The stored bcrypt hash from the database.
     * @return true if the password matches the expected hash; false otherwise.
     */
    public boolean compare(String password, String expected) {
        try {
            // Normalize the hash prefix to $2a$ for compatibility with jBCrypt 0.4
            String modifiedExpected = expected;
            if (expected.startsWith("$2b$") || expected.startsWith("$2y$")) {
                modifiedExpected = "$2a$" + expected.substring(4);
            }

            return BCrypt.checkpw(password, modifiedExpected);
        } catch (IllegalArgumentException e) {
            _log.error("PasswordHash: encryption error!", e);
            return false;
        }
    }
}