package l2ft.loginserver;

import java.io.File;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import l2ft.loginserver.crypt.PasswordHash;
import l2ft.loginserver.crypt.ScrambledKeyPair;
import l2ft.commons.configuration.ExProperties;
import l2ft.commons.util.Rnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
    private final static Logger _log = LoggerFactory.getLogger(Config.class);

    public static final String LOGIN_CONFIGURATION_FILE = "config/loginserver.ini";
    public static final String SERVER_NAMES_FILE = "config/xml/servername.xml";

    public static String LOGIN_HOST;
    public static int PORT_LOGIN;

    public static String GAME_SERVER_LOGIN_HOST;
    public static int GAME_SERVER_LOGIN_PORT;
    public static long GAME_SERVER_PING_DELAY;
    public static int GAME_SERVER_PING_RETRY;

    public static String DATABASE_DRIVER;
    public static int DATABASE_MAX_CONNECTIONS;
    public static int DATABASE_MAX_IDLE_TIMEOUT;
    public static int DATABASE_IDLE_TEST_PERIOD;
    public static String DATABASE_URL;
    public static String DATABASE_LOGIN;
    public static String DATABASE_PASSWORD;

    public static String PASSWORD_HASH_ALGORITHM;

    public static int LOGIN_BLOWFISH_KEYS;
    public static int LOGIN_RSA_KEYPAIRS;

    public static boolean ACCEPT_NEW_GAMESERVER;
    public static boolean AUTO_CREATE_ACCOUNTS;

    public static String ANAME_TEMPLATE;
    public static String APASSWD_TEMPLATE;

    public static final Map<Integer, String> SERVER_NAMES = new HashMap<>();

    public final static long LOGIN_TIMEOUT = 60 * 1000L;

    public static int LOGIN_TRY_BEFORE_BAN;
    public static long LOGIN_TRY_TIMEOUT;
    public static long IP_BAN_TIME;

    private static ScrambledKeyPair[] _keyPairs;
    private static byte[][] _blowfishKeys;

    public static PasswordHash DEFAULT_CRYPT;

    public static boolean LOGIN_LOG;

    private Config() {
    }

    public final static void load() {
        loadConfiguration();
        loadServerNames();
    }

    public final static void initCrypt() throws Throwable {
        DEFAULT_CRYPT = new PasswordHash(PASSWORD_HASH_ALGORITHM);

        _log.info("Loaded {} as default crypt.", PASSWORD_HASH_ALGORITHM);

        _keyPairs = new ScrambledKeyPair[LOGIN_RSA_KEYPAIRS];

        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
        keygen.initialize(spec);

        for (int i = 0; i < _keyPairs.length; i++) {
            _keyPairs[i] = new ScrambledKeyPair(keygen.generateKeyPair());
        }

        _log.info("Cached {} KeyPairs for RSA communication", _keyPairs.length);

        _blowfishKeys = new byte[LOGIN_BLOWFISH_KEYS][16];

        for (int i = 0; i < _blowfishKeys.length; i++) {
            for (int j = 0; j < _blowfishKeys[i].length; j++) {
                _blowfishKeys[i][j] = (byte) (Rnd.get(255) + 1);
            }
        }

        _log.info("Stored {} keys for Blowfish communication", _blowfishKeys.length);
    }

    public final static void loadServerNames() {
        SERVER_NAMES.clear();

        try {
            SAXReader reader = new SAXReader(true);
            Document document = reader.read(new File(SERVER_NAMES_FILE));

            Element root = document.getRootElement();

            for (Iterator<Element> itr = root.elementIterator(); itr.hasNext();) {
                Element node = itr.next();
                if (node.getName().equalsIgnoreCase("server")) {
                    Integer id = Integer.valueOf(node.attributeValue("id"));
                    String name = node.attributeValue("name");
                    SERVER_NAMES.put(id, name);
                }
            }

            _log.info("Loaded {} server names", SERVER_NAMES.size());
        } catch (Exception e) {
            _log.error("Error loading server names", e);
        }
    }

    public final static void loadConfiguration() {
        ExProperties serverSettings = load(LOGIN_CONFIGURATION_FILE);

        LOGIN_HOST = serverSettings.getProperty("LoginserverHostname", "127.0.0.1");
        PORT_LOGIN = serverSettings.getProperty("LoginserverPort", 2106);

        GAME_SERVER_LOGIN_HOST = serverSettings.getProperty("LoginHost", "127.0.0.1");
        GAME_SERVER_LOGIN_PORT = serverSettings.getProperty("LoginPort", 9014);

        DATABASE_DRIVER = serverSettings.getProperty("Driver", "com.mysql.jdbc.Driver");
        DATABASE_MAX_CONNECTIONS = serverSettings.getProperty("MaximumDbConnections", 3);
        DATABASE_MAX_IDLE_TIMEOUT = serverSettings.getProperty("MaxIdleConnectionTimeout", 600);
        DATABASE_IDLE_TEST_PERIOD = serverSettings.getProperty("IdleConnectionTestPeriod", 60);
        DATABASE_URL = serverSettings.getProperty("URL", "jdbc:mysql://localhost/l2jdb");
        DATABASE_LOGIN = serverSettings.getProperty("Login", "root");
        DATABASE_PASSWORD = serverSettings.getProperty("Password", "");

        LOGIN_BLOWFISH_KEYS = serverSettings.getProperty("BlowFishKeys", 20);
        LOGIN_RSA_KEYPAIRS = serverSettings.getProperty("RSAKeyPairs", 10);

        ACCEPT_NEW_GAMESERVER = serverSettings.getProperty("AcceptNewGameServer", true);

        PASSWORD_HASH_ALGORITHM = serverSettings.getProperty("PasswordHash", "bcrypt");

        AUTO_CREATE_ACCOUNTS = serverSettings.getProperty("AutoCreateAccounts", true);
        ANAME_TEMPLATE = serverSettings.getProperty("AccountTemplate", "[A-Za-z0-9]{4,14}");
        APASSWD_TEMPLATE = serverSettings.getProperty("PasswordTemplate", "[A-Za-z0-9]{4,16}");

        LOGIN_TRY_BEFORE_BAN = serverSettings.getProperty("LoginTryBeforeBan", 10);
        LOGIN_TRY_TIMEOUT = serverSettings.getProperty("LoginTryTimeout", 5) * 1000L;
        IP_BAN_TIME = serverSettings.getProperty("IpBanTime", 300) * 1000L;
        GAME_SERVER_PING_DELAY = serverSettings.getProperty("GameServerPingDelay", 30) * 1000L;
        GAME_SERVER_PING_RETRY = serverSettings.getProperty("GameServerPingRetry", 4);

        LOGIN_LOG = serverSettings.getProperty("LoginLog", true);
    }

    public static ExProperties load(String filename) {
        return load(new File(filename));
    }

    public static ExProperties load(File file) {
        ExProperties result = new ExProperties();

        try {
            result.load(file);
        } catch (IOException e) {
            _log.error("Error loading configuration file: {}", file.getName(), e);
        }

        return result;
    }

    public static ScrambledKeyPair getScrambledRSAKeyPair() {
        return _keyPairs[Rnd.get(_keyPairs.length)];
    }

    public static byte[] getBlowfishKey() {
        return _blowfishKeys[Rnd.get(_blowfishKeys.length)];
    }
}