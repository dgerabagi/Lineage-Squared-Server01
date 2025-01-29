// File: C:\l2sq\Pac Project\Java\l2ft\gameserver\GameServer.java

package l2ft.gameserver;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;

// Added imports for database connection
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// ... [other imports]
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.GameServer.GameServerListenerList;
import l2ft.commons.lang.StatsUtils;
import l2ft.commons.listener.Listener;
import l2ft.commons.listener.ListenerList;
import l2ft.commons.net.nio.impl.SelectorThread;
import l2ft.commons.versioning.Version;
import l2ft.gameserver.cache.CrestCache;
import l2ft.gameserver.dao.CharacterDAO;
import l2ft.gameserver.dao.ItemsDAO;
import l2ft.gameserver.data.BoatHolder;
import l2ft.gameserver.data.xml.Parsers;
import l2ft.gameserver.data.xml.holder.EventHolder;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.data.xml.holder.StaticObjectHolder;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.geodata.GeoEngine;
import l2ft.gameserver.handler.admincommands.AdminCommandHandler;
import l2ft.gameserver.handler.items.ItemHandler;
import l2ft.gameserver.handler.usercommands.UserCommandHandler;
import l2ft.gameserver.handler.voicecommands.VoicedCommandHandler;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.instancemanager.AutoSpawnManager;
import l2ft.gameserver.instancemanager.BloodAltarManager;
import l2ft.gameserver.instancemanager.CastleManorManager;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.instancemanager.DimensionalRiftManager;
import l2ft.gameserver.instancemanager.FameZoneManager;
import l2ft.gameserver.instancemanager.HellboundManager;
import l2ft.gameserver.instancemanager.PetitionManager;
import l2ft.gameserver.instancemanager.PlayerMessageStack;
import l2ft.gameserver.instancemanager.RaidBossSpawnManager;
import l2ft.gameserver.instancemanager.SoDManager;
import l2ft.gameserver.instancemanager.SoIManager;
import l2ft.gameserver.instancemanager.SpawnManager;
import l2ft.gameserver.instancemanager.games.FishingChampionShipManager;
import l2ft.gameserver.instancemanager.games.LotteryManager;
import l2ft.gameserver.instancemanager.games.MiniGameScoreManager;
import l2ft.gameserver.instancemanager.itemauction.ItemAuctionManager;
import l2ft.gameserver.instancemanager.naia.NaiaCoreManager;
import l2ft.gameserver.instancemanager.naia.NaiaTowerManager;
import l2ft.gameserver.listener.GameListener;
import l2ft.gameserver.listener.game.OnShutdownListener;
import l2ft.gameserver.listener.game.OnStartListener;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.MonsterRace;
import l2ft.gameserver.model.entity.SevenSigns;
import l2ft.gameserver.model.entity.SevenSignsFestival.SevenSignsFestival;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.network.authcomm.AuthServerCommunication;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.network.l2.GamePacketHandler;
import l2ft.gameserver.network.telnet.TelnetServer;
import l2ft.gameserver.scripts.Scripts;
import l2ft.gameserver.tables.AugmentationData;
import l2ft.gameserver.tables.CharTemplateTable;
import l2ft.gameserver.tables.ClanTable;
import l2ft.gameserver.tables.EnchantHPBonusTable;
import l2ft.gameserver.tables.LevelUpTable;
import l2ft.gameserver.tables.PetSkillsTable;
import l2ft.gameserver.tables.SkillTreeTable;
import l2ft.gameserver.taskmanager.ItemsAutoDestroy;
import l2ft.gameserver.taskmanager.TaskManager;
import l2ft.gameserver.taskmanager.tasks.RestoreOfflineTraders;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.utils.FirstTeam;
import l2ft.gameserver.utils.Strings;
import net.sf.ehcache.CacheManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameServer {
    public static final int AUTH_SERVER_PROTOCOL = 2;
    private static final Logger _log = LoggerFactory.getLogger(GameServer.class);

    public class GameServerListenerList extends ListenerList<GameServer> {
        public void onStart() {
            for (Listener<GameServer> listener : getListeners())
                if (OnStartListener.class.isInstance(listener))
                    ((OnStartListener) listener).onStart();
        }

        public void onShutdown() {
            for (Listener<GameServer> listener : getListeners())
                if (OnShutdownListener.class.isInstance(listener))
                    ((OnShutdownListener) listener).onShutdown();
        }
    }

    public static GameServer _instance;

    private final SelectorThread<GameClient> _selectorThreads[];
    private Version version;
    private TelnetServer statusServer;
    private final GameServerListenerList _listeners;

    private int _serverStarted;

    public SelectorThread<GameClient>[] getSelectorThreads() {
        return _selectorThreads;
    }

    public int time() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public int uptime() {
        return time() - _serverStarted;
    }

    @SuppressWarnings("unchecked")
    public GameServer() throws Exception {
        _instance = this;
        _serverStarted = time();
        _listeners = new GameServerListenerList();

        new File("./log/").mkdir();

        version = new Version(GameServer.class);

        _log.info("=================================================");
        _log.info("Revision: ................ " + version.getRevisionNumber());
        _log.info("Build date: .............. " + version.getBuildDate());
        _log.info("Compiler version: ........ " + version.getBuildJdk());
        _log.info("=================================================");

        // Initialize config
        Config.load();
        // Check binding address
        checkFreePorts();
        // Initialize database
        Class.forName(Config.DATABASE_DRIVER).newInstance();
        DatabaseFactory.getInstance().getConnection().close();

        IdFactory _idFactory = IdFactory.getInstance();
        if (!_idFactory.isInitialized()) {
            _log.error("Could not read object IDs from DB. Please Check Your Data.");
            throw new Exception("Could not initialize the ID factory");
        }

        CacheManager.getInstance();

        ThreadPoolManager.getInstance();

        Scripts.getInstance();

        GeoEngine.load();

        Strings.reload();

        GameTimeController.getInstance();

        World.init();

        Parsers.parseAll();

        ItemsDAO.getInstance();

        CrestCache.getInstance();

        CharacterDAO.getInstance();

        ClanTable.getInstance();

        SkillTreeTable.getInstance();

        AugmentationData.getInstance();

        CharTemplateTable.getInstance();

        EnchantHPBonusTable.getInstance();

        LevelUpTable.getInstance();

        PetSkillsTable.getInstance();

        ItemAuctionManager.getInstance();

        SpawnManager.getInstance().spawnAll();
        BoatHolder.getInstance().spawnAll();

        StaticObjectHolder.getInstance().spawnAll();

        RaidBossSpawnManager.getInstance();

        Scripts.getInstance().init();

        DimensionalRiftManager.getInstance();

        Announcements.getInstance();

        LotteryManager.getInstance();

        PlayerMessageStack.getInstance();

        if (Config.AUTODESTROY_ITEM_AFTER > 0)
            ItemsAutoDestroy.getInstance();

        MonsterRace.getInstance();

        SevenSigns.getInstance();
        SevenSignsFestival.getInstance();
        SevenSigns.getInstance().updateFestivalScore();

        AutoSpawnManager.getInstance();

        SevenSigns.getInstance().spawnSevenSignsNPC();

        if (Config.ENABLE_OLYMPIAD) {
            Olympiad.load();
            Hero.getInstance();
        }

        PetitionManager.getInstance();

        CursedWeaponsManager.getInstance();

        ItemHandler.getInstance();

        AdminCommandHandler.getInstance().log();
        UserCommandHandler.getInstance().log();
        VoicedCommandHandler.getInstance().log();

        TaskManager.getInstance();

        _log.info("=[Events]=========================================");
        ResidenceHolder.getInstance().callInit();
        EventHolder.getInstance().callInit();
        _log.info("==================================================");

        CastleManorManager.getInstance();

        Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());

        _log.info("IdFactory: Free ObjectID's remaining: " + IdFactory.getInstance().size());

        if (Config.ALT_FISH_CHAMPIONSHIP_ENABLED)
            FishingChampionShipManager.getInstance();

        HellboundManager.getInstance();

        NaiaTowerManager.getInstance();
        NaiaCoreManager.getInstance();

        SoDManager.getInstance();
        SoIManager.getInstance();
        BloodAltarManager.getInstance();

        MiniGameScoreManager.getInstance();

        FameZoneManager.getInstance();

        // MessageSender.send();
        _log.info("GameServer Started");
        _log.info("Maximum Numbers of Connected Players: " + Config.MAXIMUM_ONLINE_USERS);

        GamePacketHandler gph = new GamePacketHandler();

        InetAddress serverAddr = Config.GAMESERVER_HOSTNAME.equalsIgnoreCase("*") ? null
                : InetAddress.getByName(Config.GAMESERVER_HOSTNAME);

        _selectorThreads = new SelectorThread[Config.PORTS_GAME.length];
        for (int i = 0; i < Config.PORTS_GAME.length; i++) {
            _selectorThreads[i] = new SelectorThread<GameClient>(Config.SELECTOR_CONFIG, gph, gph, gph, null);
            _selectorThreads[i].openServerSocket(serverAddr, Config.PORTS_GAME[i]);
            _selectorThreads[i].start();
        }

        AuthServerCommunication.getInstance().start();

        if (Config.SERVICES_OFFLINE_TRADE_RESTORE_AFTER_RESTART)
            ThreadPoolManager.getInstance().schedule(new RestoreOfflineTraders(), 30000L);

        // Start the RecHaveUpdater
        startRecHaveUpdater(); // <-- Added this line to start the updater

        getListeners().onStart();

        if (Config.IS_TELNET_ENABLED)
            statusServer = new TelnetServer();
        else
            _log.info("Telnet server is currently disabled.");

        _log.info("=================================================");
        String memUsage = new StringBuilder().append(StatsUtils.getMemUsage()).toString();
        for (String line : memUsage.split("\n"))
            _log.info(line);
        _log.info("=================================================");
        FirstTeam.info();
        l2ft.gameserver.ccpGuard.Protection.Init();
    }

    // Added the startRecHaveUpdater method
    public void startRecHaveUpdater() {
        // Schedule the updater to run every 60 seconds
        ThreadPoolManager.getInstance().scheduleAtFixedRate(new RecHaveUpdater(), 60000, 60000);
    }

    private class RecHaveUpdater implements Runnable {
        @Override
        public void run() {
            for (Player player : GameObjectsStorage.getAllPlayersForIterate()) {
                if (player != null && player.isOnline()) {
                    updatePlayerRecHave(player);
                }
            }
        }

        private void updatePlayerRecHave(Player player) {
            // We fetch from the DAO
            int desiredRec = CharacterDAO.getInstance().getDesiredRecHave(player);
            if (desiredRec >= 0 && desiredRec != player.getRecomHave()) {
                // Update player's rec have in-memory
                player.refreshRecHave(desiredRec);

                // (Optionally) store to 'characters' table if you want
                // CharacterDAO.getInstance().updateRecHaveInCharacters(player, desiredRec);

                _log.info("RecHaveUpdater: Player {} rec_have updated to {}", player.getName(), desiredRec);
            }
        }
    }

    public GameServerListenerList getListeners() {
        return _listeners;
    }

    public static GameServer getInstance() {
        return _instance;
    }

    public <T extends GameListener> boolean addListener(T listener) {
        return _listeners.add(listener);
    }

    public <T extends GameListener> boolean removeListener(T listener) {
        return _listeners.remove(listener);
    }

    public static void checkFreePorts() {
        boolean binded = false;
        while (!binded)
            for (int PORT_GAME : Config.PORTS_GAME)
                try {
                    ServerSocket ss;
                    if (Config.GAMESERVER_HOSTNAME.equalsIgnoreCase("*"))
                        ss = new ServerSocket(PORT_GAME);
                    else
                        ss = new ServerSocket(PORT_GAME, 50, InetAddress.getByName(Config.GAMESERVER_HOSTNAME));
                    ss.close();
                    binded = true;
                } catch (Exception e) {
                    _log.warn("Port " + PORT_GAME + " is already binded. Please free it and restart server.");
                    binded = false;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e2) {
                    }
                }
    }

    public static void main(String[] args) throws Exception {
        new GameServer();
    }

    public Version getVersion() {
        return version;
    }

    public TelnetServer getStatusServer() {
        return statusServer;
    }
}
// EOF Java/l2ft/gameserver/GameServer.java
