package l2ft.loginserver.network.clientpackets;

import javax.crypto.Cipher;

import l2ft.loginserver.Config;
import l2ft.loginserver.GameServerManager;
import l2ft.loginserver.IpBanManager;
import l2ft.loginserver.accounts.Account;
import l2ft.loginserver.accounts.SessionManager;
import l2ft.loginserver.accounts.SessionManager.Session;
import l2ft.loginserver.network.L2LoginClient;
import l2ft.loginserver.network.L2LoginClient.LoginClientState;
import l2ft.loginserver.network.gameservercon.GameServer;
import l2ft.loginserver.network.gameservercon.lspackets.GetAccountInfo;
import l2ft.loginserver.network.serverpackets.LoginFail.LoginFailReason;
import l2ft.loginserver.network.serverpackets.LoginOk;
import l2ft.loginserver.utils.Log;

public class RequestAuthLogin extends L2LoginClientPacket {
    private byte[] _raw = new byte[128];

    @Override
    protected void readImpl() {
        readB(_raw);
        readD();
        readD();
        readD();
        readD();
        readD();
        readD();
        readH();
        readC();
    }

    @Override
    protected void runImpl() throws Exception {
        L2LoginClient client = getClient();

        byte[] decrypted;
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
            rsaCipher.init(Cipher.DECRYPT_MODE, client.getRSAPrivateKey());
            decrypted = rsaCipher.doFinal(_raw, 0x00, 0x80);
        } catch (Exception e) {
            client.closeNow(true);
            return;
        }

        String user = new String(decrypted, 0x5E, 14).trim().toLowerCase();
        String password = new String(decrypted, 0x6C, 16).trim();

        int currentTime = (int) (System.currentTimeMillis() / 1000L);

        Account account = new Account(user);
        account.restore();

        if (account.getPasswordHash() == null) {
            if (Config.AUTO_CREATE_ACCOUNTS && user.matches(Config.ANAME_TEMPLATE) && password.matches(Config.APASSWD_TEMPLATE)) {
                // Create new account
                String passwordHash = Config.DEFAULT_CRYPT.encrypt(password);
                account.setPasswordHash(passwordHash);
                account.save();
            } else {
                client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
                return;
            }
        }

        // Validate password
        boolean passwordCorrect = Config.DEFAULT_CRYPT.compare(password, account.getPasswordHash());

        if (!IpBanManager.getInstance().tryLogin(client.getIpAddress(), passwordCorrect)) {
            client.closeNow(false);
            return;
        }

        if (!passwordCorrect) {
            client.close(LoginFailReason.REASON_USER_OR_PASS_WRONG);
            return;
        }

        if (account.getAccessLevel() < 0) {
            client.close(LoginFailReason.REASON_ACCESS_FAILED);
            return;
        }

        if (account.getBanExpire() > currentTime) {
            client.close(LoginFailReason.REASON_ACCESS_FAILED);
            return;
        }

        for (GameServer gs : GameServerManager.getInstance().getGameServers()) {
            if (gs.getProtocol() >= 2 && gs.isAuthed()) {
                gs.sendPacket(new GetAccountInfo(user));
            }
        }

        account.setLastIP(client.getIpAddress());
        // account.setLastAccess(currentTime); // Uncomment if needed

        Log.LogAccount(account);

        Session session = SessionManager.getInstance().openSession(account);

        client.setAuthed(true);
        client.setLogin(user);
        client.setAccount(account);
        client.setSessionKey(session.getSessionKey());
        client.setState(LoginClientState.AUTHED);

        client.sendPacket(new LoginOk(client.getSessionKey()));
    }
}
