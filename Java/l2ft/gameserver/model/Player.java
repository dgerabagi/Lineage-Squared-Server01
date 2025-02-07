// Java/l2ft/gameserver/model/Player.java
package l2ft.gameserver.model;

import static l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode.ZONE_ALTERED_FLAG;
import static l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode.ZONE_PEACE_FLAG;
import static l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode.ZONE_PVP_FLAG;
import static l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode.ZONE_SIEGE_FLAG;
import static l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode.ZONE_SSQ_FLAG;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import l2ft.gameserver.autofarm.AutoFarmEngine;
import l2ft.gameserver.autofarm.AutoFarmState;
import l2ft.gameserver.autofarm.DropTrackerData;
import l2ft.commons.collections.LazyArrayList;
import l2ft.commons.dao.JdbcEntityState;
import l2ft.commons.dbutils.DbUtils;
import l2ft.commons.lang.reference.HardReference;
import l2ft.commons.lang.reference.HardReferences;
import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.GameTimeController;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.ai.CtrlEvent;
import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.ai.PlayableAI.nextAction;
import l2ft.gameserver.ai.PlayerAI;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.dao.AccountBonusDAO;
import l2ft.gameserver.dao.AutoFarmSkillDAO;
import l2ft.gameserver.dao.CharacterDAO;
import l2ft.gameserver.dao.CharacterGroupReuseDAO;
import l2ft.gameserver.dao.CharacterPostFriendDAO;
import l2ft.gameserver.dao.EffectsDAO;
import l2ft.gameserver.data.xml.holder.EventHolder;
import l2ft.gameserver.data.xml.holder.HennaHolder;
import l2ft.gameserver.data.xml.holder.InstantZoneHolder;
import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.data.xml.holder.MultiSellHolder.MultiSellListContainer;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.data.xml.holder.RecipeHolder;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.data.xml.holder.SkillAcquireHolder;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.database.mysql;
import l2ft.gameserver.handler.bbs.CommunityBoardManager;
import l2ft.gameserver.handler.bbs.ICommunityBoardHandler;
import l2ft.gameserver.handler.items.IItemHandler;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.instancemanager.BypassManager;
import l2ft.gameserver.instancemanager.BypassManager.BypassType;
import l2ft.gameserver.instancemanager.BypassManager.DecodedBypass;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.instancemanager.DimensionalRiftManager;
import l2ft.gameserver.instancemanager.FameZoneManager;
import l2ft.gameserver.instancemanager.MatchingRoomManager;
import l2ft.gameserver.instancemanager.QuestManager;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.instancemanager.games.HandysBlockCheckerManager;
import l2ft.gameserver.instancemanager.games.HandysBlockCheckerManager.ArenaParticipantsHolder;
import l2ft.gameserver.listener.actor.player.OnAnswerListener;
import l2ft.gameserver.listener.actor.player.impl.ReviveAnswerListener;
import l2ft.gameserver.listener.actor.player.impl.ScriptAnswerListener;
import l2ft.gameserver.listener.actor.player.impl.SummonAnswerListener;
import l2ft.gameserver.model.GameObjectTasks.EndSitDownTask;
import l2ft.gameserver.model.GameObjectTasks.EndStandUpTask;
import l2ft.gameserver.model.GameObjectTasks.HourlyTask;
import l2ft.gameserver.model.GameObjectTasks.KickTask;
import l2ft.gameserver.model.GameObjectTasks.PvPFlagTask;
import l2ft.gameserver.model.GameObjectTasks.RecomBonusTask;
import l2ft.gameserver.model.GameObjectTasks.UnJailTask;
import l2ft.gameserver.model.GameObjectTasks.WaterTask;
import l2ft.gameserver.model.Player.SkillLearnType;
import l2ft.gameserver.model.Player.TeleportPoints;
import l2ft.gameserver.model.Request.L2RequestType;
import l2ft.gameserver.model.Skill.AddedSkill;
import l2ft.gameserver.model.Zone.ZoneType;
import l2ft.gameserver.model.actor.instances.player.Bonus;
import l2ft.gameserver.model.actor.instances.player.BookMarkList;
import l2ft.gameserver.model.actor.instances.player.FriendList;
import l2ft.gameserver.model.actor.instances.player.Macro;
import l2ft.gameserver.model.actor.instances.player.MacroList;
import l2ft.gameserver.model.actor.instances.player.NevitSystem;
import l2ft.gameserver.model.actor.instances.player.RecomBonus;
import l2ft.gameserver.model.actor.instances.player.ShortCut;
import l2ft.gameserver.model.actor.instances.player.ShortCutList;
import l2ft.gameserver.model.actor.instances.player.StackClass;
import l2ft.gameserver.model.actor.instances.player.Unlocks;
import l2ft.gameserver.model.actor.instances.player.Unlocks.UnlockedClass;
import l2ft.gameserver.model.actor.listener.PlayerListenerList;
import l2ft.gameserver.model.actor.recorder.PlayerStatsChangeRecorder;
import l2ft.gameserver.model.base.AcquireType;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.base.Element;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.base.InvisibleType;
import l2ft.gameserver.model.base.PlayerAccess;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.base.RestartType;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.DimensionalRift;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.SevenSignsFestival.DarknessFestival;
import l2ft.gameserver.model.entity.boat.Boat;
import l2ft.gameserver.model.entity.boat.ClanAirShip;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.model.entity.events.impl.DominionSiegeEvent;
import l2ft.gameserver.model.entity.events.impl.DuelEvent;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsEvent;
import l2ft.gameserver.model.entity.events.impl.ForgottenBattlegroundsManager;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultEvent;
import l2ft.gameserver.model.entity.events.impl.IslandAssaultManager;
import l2ft.gameserver.model.entity.events.impl.SiegeEvent;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.model.entity.olympiad.OlympiadGame;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.entity.residence.ClanHall;
import l2ft.gameserver.model.entity.residence.Fortress;
import l2ft.gameserver.model.entity.residence.Residence;
import l2ft.gameserver.model.entity.tournament.TournamentMatch;
import l2ft.gameserver.model.instances.DecoyInstance;
import l2ft.gameserver.model.instances.FestivalMonsterInstance;
import l2ft.gameserver.model.instances.GuardInstance;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.instances.PetBabyInstance;
import l2ft.gameserver.model.instances.PetInstance;
import l2ft.gameserver.model.instances.ReflectionBossInstance;
import l2ft.gameserver.model.instances.StaticObjectInstance;
import l2ft.gameserver.model.instances.TamedBeastInstance;
import l2ft.gameserver.model.instances.TrapInstance;
import l2ft.gameserver.model.items.Inventory;
import l2ft.gameserver.model.items.ItemContainer;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.items.LockType;
import l2ft.gameserver.model.items.ManufactureItem;
import l2ft.gameserver.model.items.PcFreight;
import l2ft.gameserver.model.items.PcInventory;
import l2ft.gameserver.model.items.PcRefund;
import l2ft.gameserver.model.items.PcWarehouse;
import l2ft.gameserver.model.items.TradeItem;
import l2ft.gameserver.model.items.Warehouse;
import l2ft.gameserver.model.items.Warehouse.WarehouseType;
import l2ft.gameserver.model.items.attachment.FlagItemAttachment;
import l2ft.gameserver.model.items.attachment.PickableAttachment;
import l2ft.gameserver.model.matching.MatchingRoom;
import l2ft.gameserver.model.petition.PetitionMainGroup;
import l2ft.gameserver.model.pledge.Alliance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.pledge.Privilege;
import l2ft.gameserver.model.pledge.RankPrivs;
import l2ft.gameserver.model.pledge.SubUnit;
import l2ft.gameserver.model.pledge.UnitMember;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestEventType;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.authcomm.AuthServerCommunication;
import l2ft.gameserver.network.authcomm.gspackets.ChangeAccessLevel;
import l2ft.gameserver.network.l2.GameClient;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.IStaticPacket;
import l2ft.gameserver.network.l2.components.SceneMovie;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.AbnormalStatusUpdate;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.AutoAttackStart;
import l2ft.gameserver.network.l2.s2c.CameraMode;
import l2ft.gameserver.network.l2.s2c.ChairSit;
import l2ft.gameserver.network.l2.s2c.ChangeWaitType;
import l2ft.gameserver.network.l2.s2c.CharInfo;
import l2ft.gameserver.network.l2.s2c.ConfirmDlg;
import l2ft.gameserver.network.l2.s2c.EtcStatusUpdate;
import l2ft.gameserver.network.l2.s2c.ExAutoSoulShot;
import l2ft.gameserver.network.l2.s2c.ExBR_AgathionEnergyInfo;
import l2ft.gameserver.network.l2.s2c.ExBR_ExtraUserInfo;
import l2ft.gameserver.network.l2.s2c.ExBasicActionList;
import l2ft.gameserver.network.l2.s2c.ExDominionWarStart;
import l2ft.gameserver.network.l2.s2c.ExDuelUpdateUserInfo;
import l2ft.gameserver.network.l2.s2c.ExOlympiadMatchEnd;
import l2ft.gameserver.network.l2.s2c.ExOlympiadMode;
import l2ft.gameserver.network.l2.s2c.ExOlympiadSpelledInfo;
import l2ft.gameserver.network.l2.s2c.ExPCCafePointInfo;
import l2ft.gameserver.network.l2.s2c.ExQuestItemList;
import l2ft.gameserver.network.l2.s2c.ExSetCompassZoneCode;
import l2ft.gameserver.network.l2.s2c.ExStartScenePlayer;
import l2ft.gameserver.network.l2.s2c.ExStorageMaxCount;
import l2ft.gameserver.network.l2.s2c.ExVitalityPointInfo;
import l2ft.gameserver.network.l2.s2c.ExVoteSystemInfo;
import l2ft.gameserver.network.l2.s2c.GetItem;
import l2ft.gameserver.network.l2.s2c.HennaInfo;
import l2ft.gameserver.network.l2.s2c.InventoryUpdate;
import l2ft.gameserver.network.l2.s2c.ItemList;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.LeaveWorld;
import l2ft.gameserver.network.l2.s2c.MagicSkillLaunched;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.network.l2.s2c.NpcInfoPoly;
import l2ft.gameserver.network.l2.s2c.ObserverEnd;
import l2ft.gameserver.network.l2.s2c.ObserverStart;
import l2ft.gameserver.network.l2.s2c.PartySmallWindowUpdate;
import l2ft.gameserver.network.l2.s2c.PartySpelled;
import l2ft.gameserver.network.l2.s2c.PlaySound;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListDelete;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListDeleteAll;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListUpdate;
import l2ft.gameserver.network.l2.s2c.PrivateStoreListBuy;
import l2ft.gameserver.network.l2.s2c.PrivateStoreListSell;
import l2ft.gameserver.network.l2.s2c.PrivateStoreMsgBuy;
import l2ft.gameserver.network.l2.s2c.PrivateStoreMsgSell;
import l2ft.gameserver.network.l2.s2c.QuestList;
import l2ft.gameserver.network.l2.s2c.RadarControl;
import l2ft.gameserver.network.l2.s2c.RecipeShopMsg;
import l2ft.gameserver.network.l2.s2c.RecipeShopSellList;
import l2ft.gameserver.network.l2.s2c.RelationChanged;
import l2ft.gameserver.network.l2.s2c.Ride;
import l2ft.gameserver.network.l2.s2c.SendTradeDone;
import l2ft.gameserver.network.l2.s2c.ServerClose;
import l2ft.gameserver.network.l2.s2c.SetupGauge;
import l2ft.gameserver.network.l2.s2c.ShortBuffStatusUpdate;
import l2ft.gameserver.network.l2.s2c.ShortCutInit;
import l2ft.gameserver.network.l2.s2c.SkillCoolTime;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.network.l2.s2c.SpawnEmitter;
import l2ft.gameserver.network.l2.s2c.SpecialCamera;
import l2ft.gameserver.network.l2.s2c.StatusUpdate;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.network.l2.s2c.TargetSelected;
import l2ft.gameserver.network.l2.s2c.TargetUnselected;
import l2ft.gameserver.network.l2.s2c.TeleportToLocation;
import l2ft.gameserver.network.l2.s2c.UserInfo;
import l2ft.gameserver.network.l2.s2c.ValidateLocation;
import l2ft.gameserver.scripts.Events;
import l2ft.gameserver.skills.EffectType;
import l2ft.gameserver.skills.TimeStamp;
import l2ft.gameserver.skills.effects.EffectCubic;
import l2ft.gameserver.skills.effects.EffectTemplate;
import l2ft.gameserver.skills.skillclasses.Charge;
import l2ft.gameserver.skills.skillclasses.Transformation;
import l2ft.gameserver.stats.Formulas;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.stats.funcs.FuncTemplate;
import l2ft.gameserver.tables.CharTemplateTable;
import l2ft.gameserver.tables.ClanTable;
import l2ft.gameserver.tables.PetDataTable;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.tables.SkillTreeTable;
import l2ft.gameserver.taskmanager.AutoSaveManager;
import l2ft.gameserver.taskmanager.LazyPrecisionTaskManager;
import l2ft.gameserver.taskmanager.PlayerTaskManager;
import l2ft.gameserver.taskmanager.tasks.TaskVariable.TaskType;
import l2ft.gameserver.templates.FishTemplate;
import l2ft.gameserver.templates.Henna;
import l2ft.gameserver.templates.InstantZone;
import l2ft.gameserver.templates.PlayerTemplate;
import l2ft.gameserver.templates.item.ArmorTemplate;
import l2ft.gameserver.templates.item.ArmorTemplate.ArmorType;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.templates.item.WeaponTemplate;
import l2ft.gameserver.templates.item.WeaponTemplate.WeaponType;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.AntiFlood;
import l2ft.gameserver.utils.EffectsComparator;
import l2ft.gameserver.utils.GameStats;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.utils.Log;
import l2ft.gameserver.utils.SiegeUtils;
import l2ft.gameserver.utils.SqlBatch;
import l2ft.gameserver.utils.Strings;
import l2ft.gameserver.utils.TeleportUtils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.napile.primitive.Containers;
import org.napile.primitive.maps.IntObjectMap;
import org.napile.primitive.maps.impl.CHashIntObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Player extends Playable implements PlayerGroup {
	private static final Logger _log = LoggerFactory.getLogger(Player.class);

	public static final int DEFAULT_TITLE_COLOR = 0xFFFF77;
	public static final int MAX_POST_FRIEND_SIZE = 100;
	public static final int MAX_FRIEND_SIZE = 128;
	private AutoFarmState _autoFarmState;
	public static final String NO_TRADERS_VAR = "notraders";
	public static final String NO_ANIMATION_OF_CAST_VAR = "notShowBuffAnim";
	public static final String MY_BIRTHDAY_RECEIVE_YEAR = "MyBirthdayReceiveYear";
	private static final String NOT_CONNECTED = "<not connected>";

	public final static int OBSERVER_NONE = 0;
	public final static int OBSERVER_STARTING = 1;
	public final static int OBSERVER_STARTED = 3;
	public final static int OBSERVER_LEAVING = 2;

	public static final int STORE_PRIVATE_NONE = 0;
	public static final int STORE_PRIVATE_SELL = 1;
	public static final int STORE_PRIVATE_BUY = 3;
	public static final int STORE_PRIVATE_MANUFACTURE = 5;
	public static final int STORE_OBSERVING_GAMES = 7;
	public static final int STORE_PRIVATE_SELL_PACKAGE = 8;

	public static final int RANK_VAGABOND = 0;
	public static final int RANK_VASSAL = 1;
	public static final int RANK_HEIR = 2;
	public static final int RANK_KNIGHT = 3;
	public static final int RANK_WISEMAN = 4;
	public static final int RANK_BARON = 5;
	public static final int RANK_VISCOUNT = 6;
	public static final int RANK_COUNT = 7;
	public static final int RANK_MARQUIS = 8;
	public static final int RANK_DUKE = 9;
	public static final int RANK_GRAND_DUKE = 10;
	public static final int RANK_DISTINGUISHED_KING = 11;
	public static final int RANK_EMPEROR = 12; // unused

	public static final int LANG_ENG = 0;
	public static final int LANG_RUS = 1;
	public static final int LANG_UNK = -1;

	public static final int[] EXPERTISE_LEVELS = {
			0,
			20,
			40,
			52,
			61,
			76,
			80,
			84,
			Integer.MAX_VALUE
	};

	private GameClient _connection;
	private String _login;

	private int _karma, _pkKills, _pvpKills;
	private int _face, _hairStyle, _hairColor;
	private int _recomHave, _recomLeftToday, _fame;
	private int _recomLeft = 20;
	private int _recomBonusTime = 3600;
	private boolean _isHourglassEffected, _isRecomTimerActive;

	// For Kamael Sub
	protected int _sex;

	// Custom Vote Recommendation System
	private ScheduledFuture<?> _recHaveUpdateTask;

	private ScheduledFuture<?> _recomBonusTask;
	private boolean _isUndying = false;
	private int _deleteTimer;

	private long _createTime, _onlineTime, _onlineBeginTime, _leaveClanTime, _deleteClanTime;
	private long _uptime;
	private long _accumulatedOnlineTime;
	/**
	 * Time on login in game
	 */
	private long _lastAccess;

	/**
	 * The Color of players name / title (white is 0xFFFFFF)
	 */
	private int _nameColor, _titlecolor;

	private int _vitalityLevel = -1;
	private double _vitality = Config.VITALITY_LEVELS[4];
	private boolean _overloaded;

	boolean sittingTaskLaunched;

	public long getOnlineTime() {
		// If the player is currently online, add the difference from when
		// they logged in:
		long total = _accumulatedOnlineTime;
		if (isOnline()) {
			long now = System.currentTimeMillis();
			long session = (now - _onlineBeginTime) / 1000L;
			if (session > 0)
				total += session;
		}
		return total;
	}

	/**
	 * Time counter when L2Player is sitting
	 */
	private int _waitTimeWhenSit;

	private boolean _autoLoot = Config.AUTO_LOOT, AutoLootHerbs = Config.AUTO_LOOT_HERBS;

	private final PcInventory _inventory = new PcInventory(this);
	private final Warehouse _warehouse = new PcWarehouse(this);
	private final ItemContainer _refund = new PcRefund(this);
	private final PcFreight _freight = new PcFreight(this);

	public final BookMarkList bookmarks = new BookMarkList(this, 0);

	public final AntiFlood antiFlood = new AntiFlood();

	/**
	 * The table containing all L2RecipeList of the L2Player
	 */
	private final Map<Integer, Recipe> _recipebook = new TreeMap<Integer, Recipe>();
	private final Map<Integer, Recipe> _commonrecipebook = new TreeMap<Integer, Recipe>();

	/**
	 * Premium Items
	 */
	private Map<Integer, PremiumItem> _premiumItems = new TreeMap<Integer, PremiumItem>();

	/**
	 * The table containing all Quests began by the L2Player
	 */
	private final Map<String, QuestState> _quests = new HashMap<String, QuestState>();

	/**
	 * The list containing all shortCuts of this L2Player
	 */
	private final ShortCutList _shortCuts = new ShortCutList(this);

	/**
	 * The list containing all macroses of this L2Player
	 */
	private final MacroList _macroses = new MacroList(this);

	/**
	 * The Private Store type of the L2Player (STORE_PRIVATE_NONE=0,
	 * STORE_PRIVATE_SELL=1, sellmanage=2, STORE_PRIVATE_BUY=3, buymanage=4,
	 * STORE_PRIVATE_MANUFACTURE=5)
	 */
	private int _privatestore;
	/**
	 * Đ”Đ°Đ˝Đ˝Ń‹Đµ Đ´Đ»ŃŹ ĐĽĐ°ĐłĐ°Đ·Đ¸Đ˝Đ° Ń€ĐµŃ†ĐµĐżŃ‚ĐľĐ˛
	 */
	private String _manufactureName;
	private List<ManufactureItem> _createList = Collections.emptyList();
	/**
	 * Đ”Đ°Đ˝Đ˝Ń‹Đµ Đ´Đ»ŃŹ ĐĽĐ°ĐłĐ°Đ·Đ¸Đ˝Đ° ĐżŃ€ĐľĐ´Đ°Đ¶Đ¸
	 */
	private String _sellStoreName;
	private List<TradeItem> _sellList = Collections.emptyList();
	private List<TradeItem> _packageSellList = Collections.emptyList();
	/**
	 * Đ”Đ°Đ˝Đ˝Ń‹Đµ Đ´Đ»ŃŹ ĐĽĐ°ĐłĐ°Đ·Đ¸Đ˝Đ° ĐżĐľĐşŃ�ĐżĐşĐ¸
	 */
	private String _buyStoreName;
	private List<TradeItem> _buyList = Collections.emptyList();
	/**
	 * Đ”Đ°Đ˝Đ˝Ń‹Đµ Đ´Đ»ŃŹ ĐľĐ±ĐĽĐµĐ˝Đ°
	 */
	private List<TradeItem> _tradeList = Collections.emptyList();

	/**
	 * hennas
	 */
	private final Henna[] _henna = new Henna[3];
	private int _hennaSTR, _hennaINT, _hennaDEX, _hennaMEN, _hennaWIT, _hennaCON;

	private Party _party;
	private Location _lastPartyPosition;

	private Clan _clan;
	private int _pledgeClass = 0, _pledgeType = Clan.SUBUNIT_NONE, _powerGrade = 0, _lvlJoinedAcademy = 0,
			_apprentice = 0;

	/**
	 * GM Stuff
	 */
	private int _accessLevel;
	private PlayerAccess _playerAccess = new PlayerAccess();

	private boolean _messageRefusal = false, _tradeRefusal = false, _blockAll = false;

	/**
	 * The L2Summon of the L2Player
	 */
	private Summon _summon = null;
	private boolean _riding;

	private DecoyInstance _decoy = null;

	private Map<Integer, EffectCubic> _cubics = null;
	private int _agathionId = 0;

	private Request _request;

	private ItemInstance _arrowItem;

	/**
	 * The fists L2Weapon of the L2Player (used when no weapon is equipped)
	 */
	private WeaponTemplate _fistsWeaponItem;

	private Map<Integer, String> _chars = new HashMap<Integer, String>(8);

	/**
	 * The current higher Expertise of the L2Player (None=0, D=1, C=2, B=3, A=4,
	 * S=5, S80=6, S84=7)
	 */
	public int expertiseIndex = 0;

	private ItemInstance _enchantScroll = null;

	private WarehouseType _usingWHType;

	private boolean _isOnline = false;

	private AtomicBoolean _isLogout = new AtomicBoolean();

	/**
	 * The L2NpcInstance corresponding to the last Folk which one the player talked.
	 */
	private HardReference<NpcInstance> _lastNpc = HardReferences.emptyRef();
	/**
	 * Ń‚Ń�Ń‚ Ń…Ń€Đ°Đ˝Đ¸ĐĽ ĐĽŃ�Đ»ŃŚŃ‚Đ¸Ń�ĐµĐ»Đ» Ń� ĐşĐľŃ‚ĐľŃ€Ń‹ĐĽ Ń€Đ°Đ±ĐľŃ‚Đ°ĐµĐĽ
	 */
	private MultiSellListContainer _multisell = null;

	private Set<Integer> _activeSoulShots = new CopyOnWriteArraySet<Integer>();

	private WorldRegion _observerRegion;
	private AtomicInteger _observerMode = new AtomicInteger(0);

	public int _telemode = 0;

	private int _handysBlockCheckerEventArena = -1;

	public boolean entering = true;

	/**
	 * Đ­Ń‚Đ° Ń‚ĐľŃ‡ĐşĐ° ĐżŃ€ĐľĐ˛ĐµŃ€ŃŹĐµŃ‚Ń�ŃŹ ĐżŃ€Đ¸ Đ˝ĐµŃ�Ń‚Đ°Ń‚Đ˝ĐľĐĽ
	 * Đ˛Ń‹Ń…ĐľĐ´Đµ Ń‡Đ°Ń€Đ°, Đ¸ ĐµŃ�Đ»Đ¸ Đ˝Đµ Ń€Đ°Đ˛Đ˝Đ° null Ń‡Đ°Ń€
	 * Đ˛ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚Ń�ŃŹ Đ˛ Đ˝ĐµĐµ
	 * Đ�Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ˝Đ°ĐżŃ€Đ¸ĐĽĐµŃ€ Đ´Đ»ŃŹ Đ˛ĐľĐ·Đ˛Ń€Đ°Ń‰ĐµĐ˝Đ¸ŃŹ
	 * ĐżŃ€Đ¸ ĐżĐ°Đ´ĐµĐ˝Đ¸Đ¸ Ń� Đ˛Đ¸Đ˛ĐµŃ€Đ˝Ń‹
	 * ĐźĐľĐ»Đµ heading Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ´Đ»ŃŹ Ń…Ń€Đ°Đ˝ĐµĐ˝Đ¸ŃŹ Đ´ĐµĐ˝ĐµĐł
	 * Đ˛ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµĐĽŃ‹Ń… ĐżŃ€Đ¸ Ń�Đ±ĐľĐµ
	 */
	public Location _stablePoint = null;

	/**
	 * new loto ticket *
	 */
	public int _loto[] = new int[5];
	/**
	 * new race ticket *
	 */
	public int _race[] = new int[2];

	private final Map<Integer, String> _blockList = new ConcurrentSkipListMap<Integer, String>(); // characters blocked
																									// with '/block
																									// <charname>' cmd
	private final FriendList _friendList = new FriendList(this);

	private boolean _hero = false;

	/**
	 * True if the L2Player is in a boat
	 */
	private Boat _boat;
	private Location _inBoatPosition;

	protected int _primaryClassTemplate = 1;
	protected StackClass _activeClass = new StackClass(this);
	private Unlocks _unlocks;
	private long _sp;

	private boolean _autoAttackOnNonFlagged = false;

	private Bonus _bonus = new Bonus();
	private Future<?> _bonusExpiration;

	private boolean _isSitting;
	private StaticObjectInstance _sittingObject;

	private boolean _noble = false;

	private boolean _inOlympiadMode;
	private OlympiadGame _olympiadGame;
	private OlympiadGame _olympiadObserveGame;

	private int _olympiadSide = -1;

	private TournamentMatch _tournamentMatch = null;
	private int _tournamentPoints = -1000;

	/**
	 * ally with ketra or varka related wars
	 */
	private int _varka = 0;
	private int _ketra = 0;
	private int _ram = 0;

	private byte[] _keyBindings = ArrayUtils.EMPTY_BYTE_ARRAY;

	private int _cursedWeaponEquippedId = 0;

	private final Fishing _fishing = new Fishing(this);
	private boolean _isFishing;

	private Future<?> _taskWater;
	private Future<?> _autoSaveTask;
	private Future<?> _kickTask;

	private Future<?> _vitalityTask;
	private Future<?> _pcCafePointsTask;
	private Future<?> _unjailTask;

	private final Lock _storeLock = new ReentrantLock();

	private int _zoneMask;

	private boolean _offline = false;
	private boolean _partyMatchingVisible = true;

	private int _transformationId;
	private int _transformationTemplate;
	private String _transformationName;

	private int _pcBangPoints;

	Map<Integer, Skill> _transformationSkills = new HashMap<Integer, Skill>();

	private SkillLearnType _skillLearnType = SkillLearnType.One_By_One;

	private int _expandInventory = 0;
	private int _expandWarehouse = 0;
	private int _battlefieldChatId;
	private int _lectureMark;
	private InvisibleType _invisibleType = InvisibleType.NONE;

	private List<String> bypasses = null, bypasses_bbs = null;
	private IntObjectMap<String> _postFriends = Containers.emptyIntObjectMap();

	private List<String> _blockedActions = new ArrayList<String>();

	private boolean _notShowBuffAnim = false;
	private boolean _notShowTraders = false;
	private boolean _debug = false;

	private long _dropDisabled;
	private long _lastItemAuctionInfoRequest;

	private IntObjectMap<TimeStamp> _sharedGroupReuses = new CHashIntObjectMap<TimeStamp>();
	private Pair<Integer, OnAnswerListener> _askDialog = null;

	// High Five: Navit's Bonus System
	private NevitSystem _nevitSystem = new NevitSystem(this);

	private MatchingRoom _matchingRoom;
	private PetitionMainGroup _petitionGroup;
	private final Map<Integer, Long> _instancesReuses = new ConcurrentHashMap<Integer, Long>();

	public List<TeleportPoints> _teleportPoints = new ArrayList<TeleportPoints>();

	/**
	 * ĐšĐľĐ˝Ń�Ń‚Ń€Ń�ĐşŃ‚ĐľŃ€ Đ´Đ»ŃŹ L2Player. ĐťĐ°ĐżŃ€ŃŹĐĽŃ�ŃŽ Đ˝Đµ
	 * Đ˛Ń‹Đ·Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, Đ´Đ»ŃŹ Ń�ĐľĐ·Đ´Đ°Đ˝Đ¸ŃŹ Đ¸ĐłŃ€ĐľĐşĐ°
	 * Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ PlayerManager.create
	 */
	public Player(final int objectId, final PlayerTemplate template, final String accountName) {
		super(objectId, template);

		_login = accountName;
		_nameColor = 0xFFFFFF;
		_titlecolor = 0xFFFF77;
	}

	/**
	 * Constructor<?> of L2Player (use L2Character constructor).<BR>
	 * <BR>
	 * <p/>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Call the L2Character constructor to create an empty _skills slot and copy
	 * basic Calculator set to this L2Player</li>
	 * <li>Create a L2Radar object</li>
	 * <li>Retrieve from the database all items of this L2Player and add them to
	 * _inventory</li>
	 * <p/>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SET the account
	 * name of the L2Player</B></FONT><BR>
	 * <BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2PlayerTemplate to apply to the L2Player
	 */
	private Player(final int objectId, final PlayerTemplate template) {
		this(objectId, template, null);

		_ai = new PlayerAI(this);

		if (!Config.EVERYBODY_HAS_ADMIN_RIGHTS)
			setPlayerAccess(Config.gmlist.get(objectId));
		else
			setPlayerAccess(Config.gmlist.get(0));
	}

	@SuppressWarnings("unchecked")
	@Override
	public HardReference<Player> getRef() {
		return (HardReference<Player>) super.getRef();
	}

	public String getAccountName() {
		if (_connection == null)
			return _login;
		return _connection.getLogin();
	}

	public String getIP() {
		if (_connection == null)
			return NOT_CONNECTED;
		return _connection.getIpAddr();
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Ń�ĐżĐ¸Ń�ĐľĐş ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ĐµĐą Đ˝Đ° Đ°ĐşĐşĐ°Ń�Đ˝Ń‚Đµ,
	 * Đ·Đ° Đ¸Ń�ĐşĐ»ŃŽŃ‡ĐµĐ˝Đ¸ĐµĐĽ Ń‚ĐµĐşŃ�Ń‰ĐµĐłĐľ
	 *
	 * @return ĐˇĐżĐ¸Ń�ĐľĐş ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ĐµĐą
	 */
	public Map<Integer, String> getAccountChars() {
		return _chars;
	}

	@Override
	public final PlayerTemplate getTemplate() {
		return (PlayerTemplate) _template;
	}

	@Override
	public PlayerTemplate getBaseTemplate() {
		return (PlayerTemplate) _baseTemplate;
	}

	public void changeSex() {
		boolean male = true;
		if (getSex() == 1)
			male = false;
		boolean secondaryClassMage = ClassId.values()[getSecondaryClassId()].isMage();
		_template = CharTemplateTable.getInstance().getTemplate(getPrimaryClass(),
				getTemplateClassId(getActiveClass().getFirstClassId(), getSex(), getLevel()), secondaryClassMage,
				!male);
	}

	@Override
	public PlayerAI getAI() {
		return (PlayerAI) _ai;
	}

	@Override
	public void doCast(final Skill skill, final Creature target, boolean forceUse) {
		if (skill == null)
			return;

		super.doCast(skill, target, forceUse);

		// if(getUseSeed() != 0 && skill.getSkillType() == SkillType.SOWING)
		// sendPacket(new ExUseSharedGroupItem(getUseSeed(), getUseSeed(), 5000, 5000));
	}

	@Override
	public void sendReuseMessage(Skill skill) {
		if (isCastingNow())
			return;
		TimeStamp sts = getSkillReuse(skill);
		if (sts == null || !sts.hasNotPassed())
			return;
		long timeleft = sts.getReuseCurrent();
		if (!Config.ALT_SHOW_REUSE_MSG && timeleft < 10000 || timeleft < 500)
			return;
		long hours = timeleft / 3600000;
		long minutes = (timeleft - hours * 3600000) / 60000;
		long seconds = (long) Math.ceil((timeleft - hours * 3600000 - minutes * 60000) / 1000.);
		if (hours > 0)
			sendPacket(new SystemMessage(
					SystemMessage.THERE_ARE_S2_HOURS_S3_MINUTES_AND_S4_SECONDS_REMAINING_IN_S1S_REUSE_TIME)
					.addSkillName(skill.getId(), skill.getDisplayLevel()).addNumber(hours).addNumber(minutes)
					.addNumber(seconds));
		else if (minutes > 0)
			sendPacket(new SystemMessage(SystemMessage.THERE_ARE_S2_MINUTES_S3_SECONDS_REMAINING_IN_S1S_REUSE_TIME)
					.addSkillName(skill.getId(), skill.getDisplayLevel()).addNumber(minutes).addNumber(seconds));
		else
			sendPacket(new SystemMessage(SystemMessage.THERE_ARE_S2_SECONDS_REMAINING_IN_S1S_REUSE_TIME)
					.addSkillName(skill.getId(), skill.getDisplayLevel()).addNumber(seconds));
	}

	@Override
	public final int getLevel() {
		return getActiveClass().getLevel();
	}

	public int getSex() {
		return getTemplate().isMale ? 0 : 1;
	}

	public int getFace() {
		return _face;
	}

	public void setFace(int face) {
		_face = face;
	}

	public int getHairColor() {
		return _hairColor;
	}

	public void setHairColor(int hairColor) {
		_hairColor = hairColor;
	}

	public int getHairStyle() {
		return _hairStyle;
	}

	public void setHairStyle(int hairStyle) {
		_hairStyle = hairStyle;
	}

	public void offline() {
		if (_connection != null) {
			_connection.setActiveChar(null);
			_connection.close(ServerClose.STATIC);
			setNetConnection(null);
		}

		setNameColor(Config.SERVICES_OFFLINE_TRADE_NAME_COLOR);
		setOfflineMode(true);

		setVar("offline", String.valueOf(System.currentTimeMillis() / 1000L), -1);

		if (Config.SERVICES_OFFLINE_TRADE_SECONDS_TO_KICK > 0)
			startKickTask(Config.SERVICES_OFFLINE_TRADE_SECONDS_TO_KICK * 1000L);

		Party party = getParty();
		if (party != null) {
			if (isFestivalParticipant())
				party.broadcastMessageToPartyMembers(getName() + " has been removed from the upcoming festival.");
			leaveParty();
		}

		if (getPet() != null)
			getPet().unSummon();

		CursedWeaponsManager.getInstance().doLogout(this);

		Olympiad.logoutPlayer(this);

		broadcastCharInfo();
		stopWaterTask();
		stopBonusTask();
		stopHourlyTask();
		stopVitalityTask();
		stopPcBangPointsTask();
		stopAutoSaveTask();
		stopRecomBonusTask(true);
		stopQuestTimers();
		getNevitSystem().stopTasksOnLogout();

		try {
			getInventory().store();
		} catch (Throwable t) {
			_log.error("", t);
		}

		try {
			store(false);
		} catch (Throwable t) {
			_log.error("", t);
		}
	}

	/**
	 * ĐˇĐľĐµĐ´Đ¸Đ˝ĐµĐ˝Đ¸Đµ Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐşĐ»Đ¸ĐµĐ˝Ń‚
	 * Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Ń�ĐľŃ…Ń€Đ°Đ˝ŃŹĐµŃ‚Ń�ŃŹ Đ¸
	 * Ń�Đ´Đ°Đ»ŃŹĐµŃ‚Ń�ŃŹ Đ¸Đ· Đ¸ĐłŃ€Ń‹
	 */
	public void kick() {
		if (_connection != null) {
			_connection.close(LeaveWorld.STATIC);
			setNetConnection(null);
		}
		prepareToLogout();
		deleteMe();
	}

	/**
	 * ĐˇĐľĐµĐ´Đ¸Đ˝ĐµĐ˝Đ¸Đµ Đ˝Đµ Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐşĐ»Đ¸ĐµĐ˝Ń‚ Đ˝Đµ
	 * Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Ń�ĐľŃ…Ń€Đ°Đ˝ŃŹĐµŃ‚Ń�ŃŹ Đ¸
	 * Ń�Đ´Đ°Đ»ŃŹĐµŃ‚Ń�ŃŹ Đ¸Đ· Đ¸ĐłŃ€Ń‹
	 */
	public void restart() {
		if (_connection != null) {
			_connection.setActiveChar(null);
			setNetConnection(null);
		}
		prepareToLogout();
		deleteMe();
	}

	/**
	 * ĐˇĐľĐµĐ´Đ¸Đ˝ĐµĐ˝Đ¸Đµ Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐşĐ»Đ¸ĐµĐ˝Ń‚ Đ˝Đµ
	 * Đ·Đ°ĐşŃ€Ń‹Đ˛Đ°ĐµŃ‚Ń�ŃŹ, ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Ń�ĐľŃ…Ń€Đ°Đ˝ŃŹĐµŃ‚Ń�ŃŹ Đ¸
	 * Ń�Đ´Đ°Đ»ŃŹĐµŃ‚Ń�ŃŹ Đ¸Đ· Đ¸ĐłŃ€Ń‹
	 */
	public void logout() {
		if (_connection != null) {
			_connection.close(ServerClose.STATIC);
			setNetConnection(null);
		}
		prepareToLogout();
		deleteMe();
	}

	private void prepareToLogout() {
		if (_isLogout.getAndSet(true))
			return;

		setNetConnection(null);
		setIsOnline(false);

		storeMainClasses(true);

		getListeners().onExit();

		// Add this line to deregister player from Forgotten Battlegrounds event or
		// queue
		ForgottenBattlegroundsManager.getInstance().onPlayerLogout(this);
		AutoFarmEngine.getInstance().stopAutoFarm(this);

		AutoFarmState oldSt = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		if (oldSt != null) {
			oldSt.cancelTask(); // kill any leftover scheduled tasks
			AutoFarmEngine.getInstance().removeState(getObjectId());
		}

		if (isFlying() && !checkLandingState())
			_stablePoint = TeleportUtils.getRestartLocation(this, RestartType.TO_VILLAGE);

		if (isCastingNow())
			abortCast(true, true);

		Party party = getParty();
		if (party != null) {
			if (isFestivalParticipant())
				party.broadcastMessageToPartyMembers(getName() + " has been removed from the upcoming festival.");
			leaveParty();
		}

		CursedWeaponsManager.getInstance().doLogout(this);

		if (_olympiadObserveGame != null)
			_olympiadObserveGame.removeSpectator(this);

		Olympiad.logoutPlayer(this);

		stopFishing();

		if (_stablePoint != null)
			teleToLocation(_stablePoint);
		saveUnlocks();

		Summon pet = getPet();
		if (pet != null) {
			pet.saveEffects();
			pet.unSummon();
		}

		_friendList.notifyFriends(false);

		if (isProcessingRequest())
			getRequest().cancel();

		stopAllTimers();

		if (isInBoat())
			getBoat().removePlayer(this);

		SubUnit unit = getSubUnit();
		UnitMember member = unit == null ? null : unit.getUnitMember(getObjectId());
		if (member != null) {
			int sponsor = member.getSponsor();
			int apprentice = getApprentice();
			PledgeShowMemberListUpdate memberUpdate = new PledgeShowMemberListUpdate(this);
			for (Player clanMember : _clan.getOnlineMembers(getObjectId())) {
				clanMember.sendPacket(memberUpdate);
				if (clanMember.getObjectId() == sponsor)
					clanMember.sendPacket(
							new SystemMessage(SystemMessage.S1_YOUR_CLAN_ACADEMYS_APPRENTICE_HAS_LOGGED_OUT)
									.addString(_name));
				else if (clanMember.getObjectId() == apprentice)
					clanMember.sendPacket(
							new SystemMessage(SystemMessage.S1_YOUR_CLAN_ACADEMYS_SPONSOR_HAS_LOGGED_OUT)
									.addString(_name));
			}
			member.setPlayerInstance(this, true);
		}

		FlagItemAttachment attachment = getActiveWeaponFlagAttachment();
		if (attachment != null)
			attachment.onLogout(this);

		if (CursedWeaponsManager.getInstance().getCursedWeapon(getCursedWeaponEquippedId()) != null)
			CursedWeaponsManager.getInstance().getCursedWeapon(getCursedWeaponEquippedId()).setPlayer(null);

		MatchingRoom room = getMatchingRoom();
		if (room != null) {
			if (room.getLeader() == this)
				room.disband();
			else
				room.removeMember(this, false);
		}
		setMatchingRoom(null);

		MatchingRoomManager.getInstance().removeFromWaitingList(this);

		destroyAllTraps();

		if (_decoy != null) {
			_decoy.unSummon();
			_decoy = null;
		}

		stopPvPFlag();

		Reflection ref = getReflection();

		if (isInZone(ZoneType.no_restart))
			setVar("loggedOut", System.currentTimeMillis() / 1000, -1);

		if (ref != ReflectionManager.DEFAULT) {
			if (ref.getReturnLoc() != null)
				_stablePoint = ref.getReturnLoc();
			ref.removeObject(this);
		}

		try {
			getInventory().store();
			getRefund().clear();
		} catch (Throwable t) {
			_log.error("", t);
		}

		try {
			store(false);
		} catch (Throwable t) {
			_log.error("", t);
		}
	}

	/**
	 * @return a table containing all L2RecipeList of the L2Player.<BR>
	 *         <BR>
	 */
	public Collection<Recipe> getDwarvenRecipeBook() {
		return _recipebook.values();
	}

	public Collection<Recipe> getCommonRecipeBook() {
		return _commonrecipebook.values();
	}

	public int recipesCount() {
		return _commonrecipebook.size() + _recipebook.size();
	}

	public boolean hasRecipe(final Recipe id) {
		return _recipebook.containsValue(id) || _commonrecipebook.containsValue(id);
	}

	public boolean findRecipe(final int id) {
		return _recipebook.containsKey(id) || _commonrecipebook.containsKey(id);
	}

	/**
	 * Add a new L2RecipList to the table _recipebook containing all L2RecipeList of
	 * the L2Player
	 */
	public void registerRecipe(final Recipe recipe, boolean saveDB) {
		if (recipe == null)
			return;
		if (recipe.isDwarvenRecipe())
			_recipebook.put(recipe.getId(), recipe);
		else
			_commonrecipebook.put(recipe.getId(), recipe);
		if (saveDB)
			mysql.set("REPLACE INTO character_recipebook (char_id, id) VALUES(?,?)", getObjectId(), recipe.getId());
	}

	/**
	 * Remove a L2RecipList from the table _recipebook containing all L2RecipeList
	 * of the L2Player
	 */
	public void unregisterRecipe(final int RecipeID) {
		if (_recipebook.containsKey(RecipeID)) {
			mysql.set("DELETE FROM `character_recipebook` WHERE `char_id`=? AND `id`=? LIMIT 1", getObjectId(),
					RecipeID);
			_recipebook.remove(RecipeID);
		} else if (_commonrecipebook.containsKey(RecipeID)) {
			mysql.set("DELETE FROM `character_recipebook` WHERE `char_id`=? AND `id`=? LIMIT 1", getObjectId(),
					RecipeID);
			_commonrecipebook.remove(RecipeID);
		} else
			_log.warn("Attempted to remove unknown RecipeList" + RecipeID);
	}

	// ------------------- Quest Engine ----------------------

	public QuestState getQuestState(String quest) {
		questRead.lock();
		try {
			return _quests.get(quest);
		} finally {
			questRead.unlock();
		}
	}

	public QuestState getQuestState(Class<?> quest) {
		return getQuestState(quest.getSimpleName());
	}

	public boolean isQuestCompleted(String quest) {
		QuestState q = getQuestState(quest);
		return q != null && q.isCompleted();
	}

	public boolean isQuestCompleted(Class<?> quest) {
		QuestState q = getQuestState(quest);
		return q != null && q.isCompleted();
	}

	public void setQuestState(QuestState qs) {
		questWrite.lock();
		try {
			_quests.put(qs.getQuest().getName(), qs);
		} finally {
			questWrite.unlock();
		}
	}

	public void removeQuestState(String quest) {
		questWrite.lock();
		try {
			_quests.remove(quest);
		} finally {
			questWrite.unlock();
		}
	}

	public Quest[] getAllActiveQuests() {
		List<Quest> quests = new ArrayList<Quest>(_quests.size());
		questRead.lock();
		try {
			for (final QuestState qs : _quests.values())
				if (qs.isStarted())
					quests.add(qs.getQuest());
		} finally {
			questRead.unlock();
		}
		return quests.toArray(new Quest[quests.size()]);
	}

	public QuestState[] getAllQuestsStates() {
		questRead.lock();
		try {
			return _quests.values().toArray(new QuestState[_quests.size()]);
		} finally {
			questRead.unlock();
		}
	}

	public List<QuestState> getQuestsForEvent(NpcInstance npc, QuestEventType event) {
		List<QuestState> states = new ArrayList<QuestState>();
		Quest[] quests = npc.getTemplate().getEventQuests(event);
		QuestState qs;
		if (quests != null)
			for (Quest quest : quests) {
				qs = getQuestState(quest.getName());
				if (qs != null && !qs.isCompleted())
					states.add(getQuestState(quest.getName()));
			}
		return states;
	}

	public void processQuestEvent(String quest, String event, NpcInstance npc) {
		if (event == null)
			event = "";
		QuestState qs = getQuestState(quest);
		if (qs == null) {
			Quest q = QuestManager.getQuest(quest);
			if (q == null) {
				_log.warn("Quest " + quest + " not found!");
				return;
			}
			qs = q.newQuestState(this, Quest.CREATED);
		}
		if (qs == null || qs.isCompleted())
			return;
		qs.getQuest().notifyEvent(event, qs, npc);
		sendPacket(new QuestList(this));
	}

	/**
	 * ĐźŃ€ĐľĐ˛ĐµŃ€ĐşĐ° Đ˝Đ° ĐżĐµŃ€ĐµĐżĐľĐ»Đ˝ĐµĐ˝Đ¸Đµ Đ¸Đ˝Đ˛ĐµĐ˝Ń‚Đ°Ń€ŃŹ Đ¸
	 * ĐżĐµŃ€ĐµĐ±ĐľŃ€ Đ˛ Đ˛ĐµŃ�Đµ Đ´Đ»ŃŹ ĐşĐ˛ĐµŃ�Ń‚ĐľĐ˛ Đ¸ ŃŤĐ˛ĐµĐ˝Ń‚ĐľĐ˛
	 *
	 * @return true ĐµŃ�Đ»Đ¸ Đ˛Đµ ĐżŃ€ĐľĐ˛ĐµŃ€ĐşĐ¸ ĐżŃ€ĐľŃ�Đ»Đ¸ Ń�Ń�ĐżĐµŃ�Đ˝Đľ
	 */
	public boolean isQuestContinuationPossible(boolean msg) {
		if (getWeightPenalty() >= 3 || getInventoryLimit() * 0.9 < getInventory().getSize()
				|| Config.QUEST_INVENTORY_MAXIMUM * 0.9 < getInventory().getQuestSize()) {
			if (msg)
				sendPacket(
						Msg.PROGRESS_IN_A_QUEST_IS_POSSIBLE_ONLY_WHEN_YOUR_INVENTORYS_WEIGHT_AND_VOLUME_ARE_LESS_THAN_80_PERCENT_OF_CAPACITY);
			return false;
		}
		return true;
	}

	/**
	 * ĐžŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµĐĽ Đ¸ Đ·Đ°ĐżĐľĐĽĐ¸Đ˝Đ°ĐµĐĽ Đ˛Ń�Đµ ĐşĐ˛ĐµŃ�Ń‚ĐľĐ˛Ń‹Đµ
	 * Ń‚Đ°ĐąĐĽĐµŃ€Ń‹
	 */
	public void stopQuestTimers() {
		for (QuestState qs : getAllQuestsStates())
			if (qs.isStarted())
				qs.pauseQuestTimers();
			else
				qs.stopQuestTimers();
	}

	/**
	 * Đ’ĐľŃ�Ń�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµĐĽ Đ˛Ń�Đµ ĐşĐ˛ĐµŃ�Ń‚ĐľĐ˛Ń‹Đµ Ń‚Đ°ĐąĐĽĐµŃ€Ń‹
	 */
	public void resumeQuestTimers() {
		for (QuestState qs : getAllQuestsStates())
			qs.resumeQuestTimers();
	}

	// ----------------- End of Quest Engine -------------------

	public Collection<ShortCut> getAllShortCuts() {
		return _shortCuts.getAllShortCuts();
	}

	public ShortCut getShortCut(int slot, int page) {
		return _shortCuts.getShortCut(slot, page);
	}

	public void registerShortCut(ShortCut shortcut) {
		_shortCuts.registerShortCut(shortcut);
	}

	public void deleteShortCut(int slot, int page) {
		_shortCuts.deleteShortCut(slot, page);
	}

	public void registerMacro(Macro macro) {
		_macroses.registerMacro(macro);
	}

	public void deleteMacro(int id) {
		_macroses.deleteMacro(id);
	}

	public MacroList getMacroses() {
		return _macroses;
	}

	public boolean isCastleLord(int castleId) {
		return _clan != null && isClanLeader() && _clan.getCastle() == castleId;
	}

	/**
	 * ĐźŃ€ĐľĐ˛ĐµŃ€ŃŹĐµŃ‚ ŃŹĐ˛Đ»ŃŹĐµŃ‚Ń�ŃŹ Đ»Đ¸ ŃŤŃ‚ĐľŃ‚ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶
	 * Đ˛Đ»Đ°Đ´ĐµĐ»ŃŚŃ†ĐµĐĽ ĐşŃ€ĐµĐżĐľŃ�Ń‚Đ¸
	 *
	 * @param fortressId
	 * @return true ĐµŃ�Đ»Đ¸ Đ˛Đ»Đ°Đ´ĐµĐ»ĐµŃ†
	 */
	public boolean isFortressLord(int fortressId) {
		return _clan != null && isClanLeader() && _clan.getHasFortress() == fortressId;
	}

	public int getPkKills() {
		return _pkKills;
	}

	public void setPkKills(final int pkKills) {
		_pkKills = pkKills;
	}

	public long getCreateTime() {
		return _createTime;
	}

	public void setCreateTime(final long createTime) {
		_createTime = createTime;
	}

	public int getDeleteTimer() {
		return _deleteTimer;
	}

	public void setDeleteTimer(final int deleteTimer) {
		_deleteTimer = deleteTimer;
	}

	public int getCurrentLoad() {
		return getInventory().getTotalWeight();
	}

	public long getLastAccess() {
		return _lastAccess;
	}

	public void setLastAccess(long value) {
		_lastAccess = value;
	}

	// Vote System
	public void refreshRecHave(int newRecHave) {
		setRecomHave(newRecHave);
		sendPacket(new UserInfo(this));
		sendPacket(new ExBR_ExtraUserInfo(this));
	}

	public int getRecomHave() {
		return _recomHave;
	}

	public void setRecomHave(int value) {
		if (value > 255)
			_recomHave = 255;
		else if (value < 0)
			_recomHave = 0;
		else
			_recomHave = value;
	}

	public int getRecomBonusTime() {
		if (_recomBonusTask != null)
			return (int) Math.max(0, _recomBonusTask.getDelay(TimeUnit.SECONDS));
		return _recomBonusTime;
	}

	public void setRecomBonusTime(int val) {
		_recomBonusTime = val;
	}

	public int getRecomLeft() {
		return _recomLeft;
	}

	public void setRecomLeft(final int value) {
		_recomLeft = value;
	}

	public boolean isHourglassEffected() {
		return _isHourglassEffected;
	}

	public void setHourlassEffected(boolean val) {
		_isHourglassEffected = val;
	}

	public void startHourglassEffect() {
		setHourlassEffected(true);
		stopRecomBonusTask(true);
		sendVoteSystemInfo();
	}

	public void stopHourglassEffect() {
		setHourlassEffected(false);
		startRecomBonusTask();
		sendVoteSystemInfo();
	}

	public int addRecomLeft() {
		int recoms = 0;
		if (getRecomLeftToday() < 20)
			recoms = 10;
		else
			recoms = 1;
		setRecomLeft(getRecomLeft() + recoms);
		setRecomLeftToday(getRecomLeftToday() + recoms);
		sendUserInfo(true);
		return recoms;
	}

	public int getRecomLeftToday() {
		return _recomLeftToday;
	}

	public void setRecomLeftToday(final int value) {
		_recomLeftToday = value;
		setVar("recLeftToday", String.valueOf(_recomLeftToday), -1);
	}

	public void giveRecom(final Player target) {
		int targetRecom = target.getRecomHave();
		if (targetRecom < 255)
			target.addRecomHave(1);
		if (getRecomLeft() > 0)
			setRecomLeft(getRecomLeft() - 1);

		sendUserInfo(true);
	}

	public void addRecomHave(final int val) {
		setRecomHave(getRecomHave() + val);
		broadcastUserInfo(true);
		sendVoteSystemInfo();
	}

	public int getRecomBonus() {
		if (getRecomBonusTime() > 0 || isHourglassEffected())
			return RecomBonus.getRecoBonus(this);
		return 0;
	}

	public double getRecomBonusMul() {
		if (getRecomBonusTime() > 0 || isHourglassEffected())
			return RecomBonus.getRecoMultiplier(this);
		return 1;
	}

	public void sendVoteSystemInfo() {
		sendPacket(new ExVoteSystemInfo(this));
	}

	public boolean isRecomTimerActive() {
		return _isRecomTimerActive;
	}

	public void setRecomTimerActive(boolean val) {
		if (_isRecomTimerActive == val)
			return;

		_isRecomTimerActive = val;

		if (val)
			startRecomBonusTask();
		else
			stopRecomBonusTask(true);

		sendVoteSystemInfo();
	}

	public void startRecomBonusTask() {
		if (_recomBonusTask == null && getRecomBonusTime() > 0 && isRecomTimerActive() && !isHourglassEffected())
			_recomBonusTask = ThreadPoolManager.getInstance().schedule(new RecomBonusTask(this),
					getRecomBonusTime() * 1000);
	}

	public void stopRecomBonusTask(boolean saveTime) {
		if (_recomBonusTask != null) {
			if (saveTime)
				setRecomBonusTime((int) Math.max(0, _recomBonusTask.getDelay(TimeUnit.SECONDS)));
			_recomBonusTask.cancel(false);
			_recomBonusTask = null;
		}
	}

	@Override
	public int getKarma() {
		return _karma;
	}

	public void setKarma(int karma) {
		if (karma < 0)
			karma = 0;

		if (_karma == karma)
			return;

		_karma = karma;

		sendChanges();

		if (getPet() != null)
			getPet().broadcastCharInfo();
	}

	@Override
	public int getMaxLoad() {
		// Weight Limit = (CON Modifier*69000)*Skills
		// Source http://l2ft.bravehost.com/weightlimit.html (May 2007)
		// Fitted exponential curve to the data
		int con = getCON();
		if (con < 1)
			return (int) (31000 * Config.MAXLOAD_MODIFIER);
		else if (con > 59)
			return (int) (176000 * Config.MAXLOAD_MODIFIER);
		else
			return (int) calcStat(Stats.MAX_LOAD, Math.pow(1.029993928, con) * 30495.627366 * Config.MAXLOAD_MODIFIER,
					this, null);
	}

	private Future<?> _updateEffectIconsTask;

	private class UpdateEffectIcons extends RunnableImpl {
		@Override
		public void runImpl() throws Exception {
			updateEffectIconsImpl();
			_updateEffectIconsTask = null;
		}
	}

	@Override
	public void updateEffectIcons() {
		if (entering || isLogoutStarted())
			return;

		if (Config.USER_INFO_INTERVAL == 0) {
			if (_updateEffectIconsTask != null) {
				_updateEffectIconsTask.cancel(false);
				_updateEffectIconsTask = null;
			}
			updateEffectIconsImpl();
			return;
		}

		if (_updateEffectIconsTask != null)
			return;

		_updateEffectIconsTask = ThreadPoolManager.getInstance().schedule(new UpdateEffectIcons(),
				Config.USER_INFO_INTERVAL);
	}

	public void updateEffectIconsImpl() {
		Effect[] effects = getEffectList().getAllFirstEffects();
		Arrays.sort(effects, EffectsComparator.getInstance());

		PartySpelled ps = new PartySpelled(this, false);
		AbnormalStatusUpdate mi = new AbnormalStatusUpdate();

		for (Effect effect : effects)
			if (effect.isInUse()) {
				if (effect.getStackType().equals(EffectTemplate.HP_RECOVER_CAST))
					sendPacket(new ShortBuffStatusUpdate(effect));
				else
					effect.addIcon(mi);
				if (_party != null)
					effect.addPartySpelledIcon(ps);
			}

		sendPacket(mi);
		if (_party != null)
			_party.broadCast(ps);

		if (isInOlympiadMode() && isOlympiadCompStart()) {
			OlympiadGame olymp_game = _olympiadGame;
			if (olymp_game != null) {
				ExOlympiadSpelledInfo olympiadSpelledInfo = new ExOlympiadSpelledInfo();

				for (Effect effect : effects)
					if (effect != null && effect.isInUse())
						effect.addOlympiadSpelledIcon(this, olympiadSpelledInfo);

				for (Player member : olymp_game.getSpectators())
					member.sendPacket(olympiadSpelledInfo);
			}
		}
	}

	public int getWeightPenalty() {
		return getSkillLevel(4270, 0);
	}

	public void refreshOverloaded() {
		if (isLogoutStarted() || getMaxLoad() <= 0)
			return;

		setOverloaded(getCurrentLoad() > getMaxLoad());
		double weightproc = 100. * (getCurrentLoad() - calcStat(Stats.MAX_NO_PENALTY_LOAD, 0, this, null))
				/ getMaxLoad();
		int newWeightPenalty = 0;

		if (weightproc < 50)
			newWeightPenalty = 0;
		else if (weightproc < 66.6)
			newWeightPenalty = 1;
		else if (weightproc < 80)
			newWeightPenalty = 2;
		else if (weightproc < 100)
			newWeightPenalty = 3;
		else
			newWeightPenalty = 4;

		int current = getWeightPenalty();
		if (current == newWeightPenalty)
			return;

		if (newWeightPenalty > 0)
			super.addSkill(SkillTable.getInstance().getInfo(4270, newWeightPenalty));
		else
			super.removeSkill(getKnownSkill(4270));

		sendPacket(new SkillList(this));
		sendEtcStatusUpdate();
		updateStats();
	}

	public int getArmorsExpertisePenalty() {
		return getSkillLevel(6213, 0);
	}

	public int getWeaponsExpertisePenalty() {
		return getSkillLevel(6209, 0);
	}

	public int getExpertisePenalty(ItemInstance item) {
		if (item.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON)
			return getWeaponsExpertisePenalty();
		else if (item.getTemplate().getType2() == ItemTemplate.TYPE2_SHIELD_ARMOR
				|| item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY)
			return getArmorsExpertisePenalty();
		return 0;
	}

	public void refreshExpertisePenalty() {
		if (isLogoutStarted())
			return;

		boolean skillUpdate = false; // Đ”Đ»ŃŹ Ń‚ĐľĐłĐľ, Ń‡Ń‚ĐľĐ±Ń‹ Đ»Đ¸Ń�Đ˝Đ¸Đą Ń€Đ°Đ· Đ˝Đµ ĐżĐľŃ�Ń‹Đ»Đ°Ń‚ŃŚ
										// ĐżĐ°ĐşĐµŃ‚Ń‹

		int level = (int) calcStat(Stats.GRADE_EXPERTISE_LEVEL, getLevel(), null, null);
		int i = 0;
		for (i = 0; (i < EXPERTISE_LEVELS.length) && (level >= EXPERTISE_LEVELS[(i + 1)]); i++)
			;
		if (expertiseIndex != i) {
			expertiseIndex = i;
			if (expertiseIndex > 0 && Config.EXPERTISE_PENALTY) // TODO ĐşŃ‚Đľ Đ´ĐµĐ»Đ°Đ»??? Đ˝Đµ Ń‚Ń�Ń‚ Đ˝Ń�Đ¶Đ˝Đľ!!!
																// ĐżĐµŃ€ĐµĐ´ĐµĐ»Đ°ĐąŃ‚Đµ Ń� ĐżŃ€ĐľĐ˛ĐµŃ€ĐşĐľĐą Đ˝Đ°
																// ŃŤĐżĐ¸Đş Đ¸Ń‚ĐµĐĽ!!! Đ´ĐľĐ±Đ°Đ˛Đ»ĐµĐ˝Đľ
																// Config.EPIC_EXPERTISE_PENALTY
			{
				addSkill(SkillTable.getInstance().getInfo(239, expertiseIndex), false);
				skillUpdate = true;
			}
		}

		int newWeaponPenalty = 0;
		int newArmorPenalty = 0;
		ItemInstance[] items = getInventory().getPaperdollItems();
		for (ItemInstance item : items)
			if (item != null) {
				int crystaltype = item.getTemplate().getCrystalType().ordinal();
				if (item.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON) {
					if (crystaltype > newWeaponPenalty)
						newWeaponPenalty = crystaltype;
				} else if (item.getTemplate().getType2() == ItemTemplate.TYPE2_SHIELD_ARMOR
						|| item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY)
					if (crystaltype > newArmorPenalty)
						newArmorPenalty = crystaltype;
			}

		newWeaponPenalty = newWeaponPenalty - expertiseIndex;
		if (newWeaponPenalty <= 0)
			newWeaponPenalty = 0;
		else if (newWeaponPenalty >= 4)
			newWeaponPenalty = 4;

		newArmorPenalty = newArmorPenalty - expertiseIndex;
		if (newArmorPenalty <= 0)
			newArmorPenalty = 0;
		else if (newArmorPenalty >= 4)
			newArmorPenalty = 4;

		int weaponExpertise = getWeaponsExpertisePenalty();
		int armorExpertise = getArmorsExpertisePenalty();

		if (weaponExpertise != newWeaponPenalty) {
			weaponExpertise = newWeaponPenalty;
			if (newWeaponPenalty > 0 && Config.EXPERTISE_PENALTY)
				super.addSkill(SkillTable.getInstance().getInfo(6209, weaponExpertise));
			else
				super.removeSkill(getKnownSkill(6209));
			skillUpdate = true;
		}
		if (armorExpertise != newArmorPenalty) {
			armorExpertise = newArmorPenalty;
			if (newArmorPenalty > 0 && Config.EXPERTISE_PENALTY)
				super.addSkill(SkillTable.getInstance().getInfo(6213, armorExpertise));
			else
				super.removeSkill(getKnownSkill(6213));
			skillUpdate = true;
		}

		if (skillUpdate) {
			getInventory().validateItemsSkills();

			sendPacket(new SkillList(this));
			sendEtcStatusUpdate();
			updateStats();
		}
	}

	public int getPvpKills() {
		return _pvpKills;
	}

	public void setPvpKills(int pvpKills) {
		_pvpKills = pvpKills;
	}

	public void addClanPointsOnProfession(int newLevel) {
		if (newLevel >= 60 && getClan() != null && getClan().getLevel() >= 5 && !getActiveClass().isKamael()
				&& getPledgeType() == Clan.SUBUNIT_ACADEMY && getLvlJoinedAcademy() < 100) {
			int earnedPoints = 0;
			if (getLvlJoinedAcademy() <= 36)
				earnedPoints = Config.MAX_ACADEM_POINT;
			else if (getLvlJoinedAcademy() >= 59)
				earnedPoints = Config.MIN_ACADEM_POINT;
			else
				earnedPoints = Config.MAX_ACADEM_POINT - (getLvlJoinedAcademy() - 16) * 20;

			_clan.incReputation(earnedPoints, false, "academy");
			_clan.removeClanMember(getObjectId());

			for (UnitMember member : _clan.getAllMembers()) {
				if (member.getPlayer() != null && !member.getPlayer().equals(this))
					member.getPlayer().sendMessage(getName() + " has gained level 60 and obtained " + earnedPoints
							+ " Clan Reputation Points!");

			}
			_clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListDelete(getName()), this);

			setClan(null);
			setTitle("");
			sendPacket(
					Msg.CONGRATULATIONS_YOU_WILL_NOW_GRADUATE_FROM_THE_CLAN_ACADEMY_AND_LEAVE_YOUR_CURRENT_CLAN_AS_A_GRADUATE_OF_THE_ACADEMY_YOU_CAN_IMMEDIATELY_JOIN_A_CLAN_AS_A_REGULAR_MEMBER_WITHOUT_BEING_SUBJECT_TO_ANY_PENALTIES);
			setLeaveClanTime(0);

			broadcastCharInfo();

			sendPacket(PledgeShowMemberListDeleteAll.STATIC);

			ItemFunctions.addItem(this, 8181, 1, true);
			setLvlJoinedAcademy(100);
		}
	}

	public void setClassId(int id, boolean noban, boolean fromQuest) {
		setClassId(id, noban, fromQuest, true);
	}

	/**
	 * Set the template of the L2Player.
	 *
	 * @param id The Identifier of the L2PlayerTemplate to set to the L2Player
	 */
	public synchronized void setClassId(final int id, boolean noban, boolean fromQuest, boolean changeTemplate) {
		if (getUnlocks().getUnlockedClass(id) != null) {
			final StackClass activeClass = getActiveClass();

			if (activeClass.getFirstClassId() == id)
				activeClass.setFirstClassId(id);
			else
				activeClass.setSecondaryClass(id);

			ItemInstance coupons = null;
			if (ClassId.VALUES[id].getLevel() == 2) {
				if (fromQuest)
					coupons = ItemFunctions.createItem(8869);
				unsetVar("newbieweapon");
				unsetVar("p1q2");
				unsetVar("p1q3");
				unsetVar("p1q4");
				unsetVar("prof1");
				unsetVar("ng1");
				unsetVar("ng2");
				unsetVar("ng3");
				unsetVar("ng4");
			} else if (ClassId.VALUES[id].getLevel() == 3) {
				if (fromQuest)
					coupons = ItemFunctions.createItem(8870);
				unsetVar("newbiearmor");
				unsetVar("dd1"); // удаляем отметки о выдаче дименшен даймондов
				unsetVar("dd2");
				unsetVar("dd3");
				unsetVar("prof2.1");
				unsetVar("prof2.2");
				unsetVar("prof2.3");
			}
			if (coupons != null) {
				coupons.setCount(15);
				sendPacket(SystemMessage2.obtainItems(coupons));
				getInventory().addItem(coupons);
			}

			if (fromQuest) {
				// Социалка при получении профы
				broadcastPacket(new MagicSkillUse(this, this, 5103, 1, 1000, 0));
				sendPacket(new PlaySound("ItemSound.quest_fanfare_2"));
			}
		}

		if (changeTemplate) {
			boolean secondaryClassMage = ClassId.values()[getSecondaryClassId()].isMage();
			PlayerTemplate t = CharTemplateTable.getInstance().getTemplate(getPrimaryClass(),
					getTemplateClassId(getActiveClassClassId().getId(), getSex(), getLevel()), secondaryClassMage,
					getSex() == 1);

			if (t == null) {
				_log.error("Missing template for classId: " + id);
				// do not throw error - only print error
				return;
			}

			// Set the template of the L2Player
			_template = t;
		}

		broadcastCharInfo();
		// Update class icon in party and clan
		if (isInParty())
			getParty().broadCast(new PartySmallWindowUpdate(this));
		if (getClan() != null)
			getClan().broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));
		if (_matchingRoom != null)
			_matchingRoom.broadcastPlayerUpdate(this);
	}

	public static int getTemplateClassId(int currentClassId, int sex, int level) {
		ClassId currentClass = ClassId.values()[currentClassId];
		if (level >= 76 || currentClass.getLevel() <= 1)
			return currentClassId;
		ClassId secondaryclass = currentClass.getParent(sex);
		if (level >= 40)
			return secondaryclass.getId();
		ClassId firstClass = secondaryclass.getParent(sex);
		if (level >= 20)
			return firstClass.getId();

		return firstClass.getParent(sex).getId();

	}

	public void removeClassSkill(int classToDelete) {
		List<Skill> notAllowedSkills = SkillAcquireHolder.getInstance().getNotAllowedSkills(this, classToDelete,
				getAllSkills());
		for (Skill skill : notAllowedSkills)
			removeSkill(skill);
	}

	public void switchStackClass(int newClassId, boolean primary) {
		AutoFarmState st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		saveUnlocks(); // same as before

		StackClass currentClasses = getActiveClass();
		UnlockedClass unlockedClass = getUnlocks().getUnlockedClass(newClassId);
		if (unlockedClass == null) {
			System.out.println("[switchStackClass] Could not find an unlockedClass for " + newClassId);
			return;
		}

		// 1) If picking a new primary:
		if (primary) {
			// If we are mixing races in a disallowed way, forcibly shuffle the secondary
			// out:
			if (ClassId.values()[newClassId].getRace() != getActiveClassClassId().getRace()
					&& ClassId.values()[currentClasses.getSecondaryClass()].getRace() != Race.dwarf) {
				removeClassSkill(currentClasses.getSecondaryClass());
				currentClasses.setSecondaryClass(getPrimaryClass()); // basically your old main
				currentClasses.setExpWithoutSave(0);
			}
			// Remove skills of the old main:
			removeClassSkill(currentClasses.getFirstClassId());

			// The old main class to rename in DB:
			int oldMainClassId = currentClasses.getFirstClassId();

			// 2) Actually switch the main in memory:
			currentClasses.setFirstClassId(newClassId);
			currentClasses.setFirstExp(unlockedClass.getExp());

			// 3) Adjust setClassId(...) for visuals, etc.
			setClassId(newClassId, false, false, true);

			// 4) Then rename that row in character_subclasses:
			storeMainClasses(true, oldMainClassId);
		}
		// 5) If picking a new secondary:
		else {
			// We are only changing the secondClassId in memory:
			removeClassSkill(currentClasses.getSecondaryClass());
			currentClasses.setSecondaryClass(newClassId);
			currentClasses.setExpWithoutSave(unlockedClass.getExp());

			// We do NOT rename the row in `character_subclasses`; instead,
			// we do a direct UPDATE of secondClassId, secondExp, secondLevel, etc.:
			Connection con = null;
			PreparedStatement ps = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				ps = con.prepareStatement(
						"UPDATE character_subclasses "
								+ "SET secondClassId=?, secondExp=?, secondLevel=? "
								+ "WHERE char_obj_id=? AND class_id=? AND isBase=1");
				ps.setInt(1, newClassId);
				ps.setLong(2, unlockedClass.getExp());
				ps.setInt(3, unlockedClass.getLevel());
				ps.setInt(4, getObjectId());
				ps.setInt(5, currentClasses.getFirstClassId()); // the main's class_id
				int rowCount = ps.executeUpdate();
				System.out.println("[switchStackClass-secondary] updated " + rowCount + " row(s) for secondClassId.");
			} catch (Exception e) {
				_log.error("Error updating secondClassId in DB:", e);
			} finally {
				DbUtils.closeQuietly(con, ps);
			}
		}

		// 6) Next, remove any skills that are not valid for the new arrangement:
		for (Skill sk : getAllSkills())
			if (!SkillAcquireHolder.getInstance().isSkillPossible(this, sk))
				removeSkill(sk);

		// 7) Re-apply the data for skill sets, etc. (the rest is unchanged)
		restoreSkills(getSecondaryClassId());
		restoreSkills(getActiveClassClassId().getId());

		boolean secondaryClassMage = ClassId.values()[getSecondaryClassId()].isMage();
		PlayerTemplate t = CharTemplateTable.getInstance().getTemplate(
				getPrimaryClass(),
				getTemplateClassId(getActiveClassClassId().getId(), getSex(), getLevel()),
				secondaryClassMage,
				(getSex() == 1));
		_template = t;

		sendPacket(new ExStorageMaxCount(this));

		refreshExpertisePenalty();
		restoreHenna();
		if (isNoble())
			updateNobleSkills();
		sendPacket(new HennaInfo(this));

		_shortCuts.restore();
		sendPacket(new ShortCutInit(this));

		sendPacket(new SkillList(this));

		getInventory().refreshEquip();
		getInventory().validateItems();

		setIncreasedForce(0);

		broadcastCharInfo();
		updateEffectIcons();
		updateStats();

		st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		if (st != null)
			AutoFarmSkillDAO.getInstance().loadSkillsForStackClass(this, st);
	}

	public void swapStackClasses() {
		AutoFarmState st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		System.out.println("[Player] swapStackClasses() called for " + getName() +
				". Current primary=" + getActiveClassClassId().getId() +
				", secondary=" + getSecondaryClassId());

		// 1) Save old data so we don't lose anything
		System.out.println("[Player] saveUnlocks() called before the swap");
		saveUnlocks();

		// 2) The old main class ID (which is about to become secondary)
		int oldMainClassId = getActiveClassClassId().getId();

		// Some references to the old classes:
		UnlockedClass first = getUnlocks().getUnlockedClass(oldMainClassId);
		UnlockedClass second = getUnlocks().getUnlockedClass(getSecondaryClassId());
		if (first == null || second == null) {
			System.out.println(
					"[Player] Could not find 'UnlockedClass' references for primary or secondary. Aborting swap.");
			return;
		}

		System.out.println("[Player] BEFORE SWAP -> Primary class ID=" + oldMainClassId +
				" (exp=" + first.getExp() + "), Secondary class ID=" + getSecondaryClassId() +
				" (exp=" + second.getExp() + ")");

		// 3) Actually swap them in memory
		StackClass stack = getActiveClass();
		stack.setFirstClassId(second.getId());
		stack.setFirstExp(second.getExp());
		stack.setSecondaryClass(first.getId());
		stack.setExpWithoutSave(first.getExp());

		// Now update template. We'll guess if new main is a mage
		boolean secondaryClassMage = ClassId.values()[first.getId()].isMage();
		PlayerTemplate t = CharTemplateTable.getInstance().getTemplate(
				getPrimaryClass(),
				getTemplateClassId(second.getId(), getSex(), getLevel()),
				secondaryClassMage,
				(getSex() == 1));
		_template = t;

		System.out.println("[Player] After memory-swap -> newPrimary=" + stack.getFirstClassId() +
				", newSecondary=" + stack.getSecondaryClass());

		// 4) Remove all skills, re-learn from new data
		removeAllSkills();
		restoreSkills(getActiveClassClassId().getId());
		restoreSkills(getSecondaryClassId());
		rewardSkills(true);

		if (isNoble())
			updateNobleSkills();

		sendPacket(new ExStorageMaxCount(this));

		refreshExpertisePenalty();
		restoreHenna();
		sendPacket(new HennaInfo(this));

		_shortCuts.restore();
		sendPacket(new ShortCutInit(this));

		sendPacket(new SkillList(this));

		getInventory().refreshEquip();
		getInventory().validateItems();

		setIncreasedForce(0);

		broadcastCharInfo();
		updateEffectIcons();
		updateStats();

		// 5) Important: store them in DB so it persists,
		// passing the old main class ID so we rename from "oldMainClassId" to new.
		System.out.println(
				"[Player] storeMainClasses(true, oldMainClassId=" + oldMainClassId + ") after swap for " + getName());
		storeMainClasses(true, oldMainClassId);

		st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		if (st != null) {
			System.out.println("[Player] Reloading auto-farm skills for new main class: " + stack.getFirstClassId());
			AutoFarmSkillDAO.getInstance().loadSkillsForStackClass(this, st);
		}

		System.out.println("[Player] swapStackClasses() finished successfully for " + getName());
	}

	public void addNewStackClass(int classId, boolean primary) {
		StackClass currentClasses = getActiveClass();
		if (primary)
			currentClasses.setFirstClassId(classId);
		else
			currentClasses.setSecondaryClass(classId);
		if (getActiveClass() != null) {
			EffectsDAO.getInstance().insert(this);
			storeDisableSkills();
		}

		if (primary) {
			setClassId(classId, false, false, true);
			saveMainClass(classId);
		} else
			rewardSkills(true);

		getUnlocks().addNewClass(classId, 1, 0);

		sendPacket(new ExStorageMaxCount(this));

		refreshExpertisePenalty();

		sendPacket(new SkillList(this));

		getInventory().refreshEquip();
		getInventory().validateItems();

		setIncreasedForce(0);

		broadcastCharInfo();
		updateEffectIcons();
		updateStats();
	}

	private void saveMainClass(int newClassId) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con
					.prepareStatement("UPDATE character_subclasses SET class_id=? WHERE char_obj_id=? AND isBase=1");
			statement.setInt(1, newClassId);
			statement.setInt(2, getObjectId());
			statement.executeUpdate();
			DbUtils.close(statement);

		} catch (final SQLException e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public long getExp() {
		return _activeClass.getExp();
	}

	public void setEnchantScroll(final ItemInstance scroll) {
		_enchantScroll = scroll;
	}

	public ItemInstance getEnchantScroll() {
		return _enchantScroll;
	}

	public void setFistsWeaponItem(final WeaponTemplate weaponItem) {
		_fistsWeaponItem = weaponItem;
	}

	public WeaponTemplate getFistsWeaponItem() {
		return _fistsWeaponItem;
	}

	public WeaponTemplate findFistsWeaponItem(final int classId) {
		// human fighter fists
		if (classId >= 0x00 && classId <= 0x09)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(246);

		// human mage fists
		if (classId >= 0x0a && classId <= 0x11)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(251);

		// elven fighter fists
		if (classId >= 0x12 && classId <= 0x18)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(244);

		// elven mage fists
		if (classId >= 0x19 && classId <= 0x1e)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(249);

		// dark elven fighter fists
		if (classId >= 0x1f && classId <= 0x25)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(245);

		// dark elven mage fists
		if (classId >= 0x26 && classId <= 0x2b)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(250);

		// orc fighter fists
		if (classId >= 0x2c && classId <= 0x30)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(248);

		// orc mage fists
		if (classId >= 0x31 && classId <= 0x34)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(252);

		// dwarven fists
		if (classId >= 0x35 && classId <= 0x39)
			return (WeaponTemplate) ItemHolder.getInstance().getTemplate(247);

		return null;
	}

	public void addExpAndCheckBonus(MonsterInstance mob, final double noRateExp, double noRateSp,
			double partyVitalityMod) {
		if (_activeClass == null)
			return;

		// ĐťĐ°Ń‡Đ¸Ń�Đ»ĐµĐ˝Đ¸Đµ Đ´Ń�Ń� ĐşĐ°ĐĽĐ°ŃŤĐ»ŃŹĐĽ
		double neededExp = calcStat(Stats.SOULS_CONSUME_EXP, 0, mob, null);
		if (neededExp > 0 && noRateExp > neededExp) {
			mob.broadcastPacket(new SpawnEmitter(mob, this));
			ThreadPoolManager.getInstance().schedule(new GameObjectTasks.SoulConsumeTask(this), 1000);
		}

		double vitalityBonus = 0.;
		int npcLevel = mob.getLevel();
		if (Config.ALT_VITALITY_ENABLED && getEffectList().getEffectByType(EffectType.VitalityMaintain) == null) {
			boolean blessActive = getNevitSystem().isBlessingActive();
			vitalityBonus = mob.isRaid() ? 0. : getVitalityLevel(blessActive) / 2.;
			vitalityBonus *= Config.ALT_VITALITY_RATE;
			if (noRateExp > 0) {
				if (!mob.isRaid()) {
					// TODO: Đ Đ°Đ·ĐľĐ±Ń€Đ°Ń‚Ń�ŃŹ, Đ˝ĐµĐ»ŃŚĐ·ŃŹ ĐżŃ€ĐµĐ´ĐĽĐµŃ‚Ń‹
					// Đ¸Ń�ĐżĐľĐ»ŃŚĐ·ĐľĐ˛Đ°Ń‚ŃŚ, Đ¸Đ»Đ¸ ĐżŃ€ĐµĐ´ĐĽĐµŃ‚Ń‹ Đ˝Đµ Đ±Ń�Đ´Ń�Ń‚
					// Đ´Đ°Đ˛Đ°Ń‚ŃŚ ŃŤŃ„Ń„ĐµĐşŃ‚Đ°?
					// (Đ’Ń�Đµ ĐżŃ€ĐµĐ´ĐĽĐµŃ‚Ń‹ Đ´Đ»ŃŹ Đ˛ĐľŃ�ĐżĐľĐ»Đ˝ĐµĐ˝Đ¸ŃŹ Đ¸Đ»Đ¸
					// ĐżĐľĐ´Đ´ĐµŃ€Đ¶Đ°Đ˝Đ¸ŃŹ ŃŤĐ˝ĐµŃ€ĐłĐ¸Đ¸ Đ˝Đµ Đ´ĐµĐąŃ�Ń‚Đ˛Ń�ŃŽŃ‚ Đ˛Đľ Đ˛Ń€ĐµĐĽŃŹ
					// Đ´ĐµĐąŃ�Ń‚Đ˛Đ¸ŃŹ ĐťĐ¸Ń�Ń…ĐľĐ¶Đ´ĐµĐ˝Đ¸ŃŹ ĐťĐµĐ˛Đ¸Ń‚Ń‚Đ°)
					if (!blessActive && !getVarB("NoExp") && !(getExp() == Experience.LEVEL[getLevel() + 1] - 1)) {
						double points = ((noRateExp / (npcLevel * npcLevel)) * 100) / 9;
						points *= Config.ALT_VITALITY_CONSUME_RATE;

						if (getEffectList().getEffectByType(EffectType.Vitality) != null)
							points *= -1;

						setVitality(getVitality() - points * partyVitalityMod);
					}
				} else
					setVitality(getVitality() + Config.ALT_VITALITY_RAID_BONUS);
			}
		}

		// ĐźŃ€Đ¸ ĐżĐµŃ€Đ˛ĐľĐĽ Đ˛Ń‹Đ·ĐľĐ˛Đµ, Đ°ĐşŃ‚Đ¸Đ˛Đ¸Ń€Ń�ĐµĐĽ Ń‚Đ°ĐąĐĽĐµŃ€Ń‹
		// Đ±ĐľĐ˝Ń�Ń�ĐľĐ˛.
		if (!isInPeaceZone()) {
			setRecomTimerActive(true);
			getNevitSystem().startAdventTask();
			if ((getLevel() - npcLevel) <= 9) {
				int nevitPoints = (int) Math.round(((noRateExp / (npcLevel * npcLevel)) * 100) / 20); // TODO:
																										// Đ¤ĐľŃ€ĐĽŃ�Đ»Đ°
																										// ĐľŃ‚
																										// Đ±Đ°Đ»Đ´Ń‹.
				getNevitSystem().addPoints(nevitPoints);
			}
		}

		long normalExp = (long) (noRateExp
				* (Config.RATE_XP * (vitalityBonus + (getRateExp() - 1.) + getRecomBonusMul())));
		long normalSp = (long) (noRateSp * (Config.RATE_SP * (getRateSp() + vitalityBonus)));

		long expWithoutBonus = (long) (noRateExp * Config.RATE_XP * getRateExp());// make movie, yes
		long spWithoutBonus = (long) (noRateSp * Config.RATE_SP * getRateSp());

		addExpAndSp(normalExp, normalSp, normalExp - expWithoutBonus, normalSp - spWithoutBonus, false, true);
	}

	@Override
	public void addExpAndSp(long exp, long sp) {
		addExpAndSp(exp, sp, 0, 0, false, false);
	}

	public void addExpAndSp(long addToExp, long addToSp, long bonusAddExp, long bonusAddSp, boolean applyRate,
			boolean applyToPet) {
		if (_activeClass == null)
			return;

		if (applyRate) {
			addToExp *= Config.RATE_XP * getRateExp();
			addToSp *= Config.RATE_SP * getRateSp();
		}

		Summon pet = getPet();
		if (addToExp > 0) {
			if (applyToPet) {
				if (pet != null && !pet.isDead() && !PetDataTable.isVitaminPet(pet.getNpcId()))
					// Sin Eater Đ·Đ°Đ±Đ¸Ń€Đ°ĐµŃ‚ Đ˛Ń�ŃŽ ŃŤĐşŃ�ĐżŃ� Ń� ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶Đ°
					if (pet.getNpcId() == PetDataTable.SIN_EATER_ID) {
						pet.addExpAndSp(addToExp, 0);
						addToExp = 0;
					} else if (pet.isPet() && pet.getExpPenalty() > 0f)
						if (pet.getLevel() > getLevel() - 20 && pet.getLevel() < getLevel() + 5) {
							pet.addExpAndSp((long) (addToExp * pet.getExpPenalty()), 0);
							addToExp *= 1. - pet.getExpPenalty();
						} else {
							pet.addExpAndSp((long) (addToExp * pet.getExpPenalty() / 5.), 0);
							addToExp *= 1. - pet.getExpPenalty() / 5.;
						}
					else if (pet.isSummon())
						addToExp *= 1. - pet.getExpPenalty();
			}

			// Remove Karma when the player kills L2MonsterInstance
			// TODO [G1ta0] Đ´Đ˛Đ¸Đ˝Ń�Ń‚ŃŚ Đ˛ ĐĽĐµŃ‚ĐľĐ´ Đ˝Đ°Ń‡Đ¸Ń�Đ»ĐµĐ˝Đ¸ŃŹ Đ˝Đ°ĐłŃ€Đ°Đ´
			// ĐżŃ€Đ¸ Ń�Đ±ĐąĐ¸Ń�Ń‚Đ˛Đµ ĐĽĐľĐ±Đ°
			if (!isCursedWeaponEquipped() && addToSp > 0 && _karma > 0)
				_karma -= addToSp / (Config.KARMA_SP_DIVIDER * Config.RATE_SP);

			if (_karma < 0)
				_karma = 0;

			if (getVarB("NoExp"))
				addToExp = 0;
		}

		int oldLvl = _activeClass.getLevel();
		int oldSecondaryLevel = _activeClass.getSecondaryLevel();

		_activeClass.addExp(addToExp);
		addSp(addToSp);

		if (addToExp > 0 && addToSp > 0 && (bonusAddExp > 0 || bonusAddSp > 0))
			sendPacket(new SystemMessage2(SystemMsg.YOU_HAVE_ACQUIRED_S1_EXP_BONUS_S2_AND_S3_SP_BONUS_S4)
					.addLong(addToExp).addLong(bonusAddExp).addInteger(addToSp).addInteger((int) bonusAddSp));
		else if (addToSp > 0 && addToExp == 0)
			sendPacket(new SystemMessage(SystemMessage.YOU_HAVE_ACQUIRED_S1_SP).addNumber(addToSp));
		else if (addToSp > 0 && addToExp > 0)
			sendPacket(new SystemMessage(SystemMessage.YOU_HAVE_EARNED_S1_EXPERIENCE_AND_S2_SP).addNumber(addToExp)
					.addNumber(addToSp));
		else if (addToSp == 0 && addToExp > 0)
			sendPacket(new SystemMessage(SystemMessage.YOU_HAVE_EARNED_S1_EXPERIENCE).addNumber(addToExp));

		int level = _activeClass.getLevel();
		int secondaryLevel = _activeClass.getSecondaryLevel();
		if (level != oldLvl || oldSecondaryLevel != secondaryLevel) {
			int levels = level - oldLvl;
			if (levels > 0)
				getNevitSystem().addPoints(1950);
			levelSet(levels);
		}

		if (pet != null && pet.isPet() && PetDataTable.isVitaminPet(pet.getNpcId())) {
			PetInstance _pet = (PetInstance) pet;
			_pet.setLevel(getLevel());
			_pet.setExp(_pet.getExpForNextLevel());
			_pet.broadcastStatusUpdate();
		}

		if (getNevitSystem().isBlessingActive()) {
			addVitality(Config.ALT_VITALITY_NEVIT_POINT);
		}

		updateStats();
	}

	/**
	 * Give Expertise skill of this level.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Get the Level of the L2Player</li>
	 * <li>Add the Expertise skill corresponding to its Expertise level</li>
	 * <li>Update the overloaded status of the L2Player</li><BR>
	 * <BR>
	 * <p/>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T give other free
	 * skills (SP needed = 0)</B></FONT><BR>
	 * <BR>
	 * 
	 * @param send
	 */
	public void rewardSkills(boolean send) {
		boolean update = false;
		if (Config.AUTO_LEARN_SKILLS) {
			int unLearnable = 0;
			Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(this,
					AcquireType.NORMAL);
			while (skills.size() > unLearnable) {
				unLearnable = 0;
				for (SkillLearn s : skills) {
					Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
					if (sk == null || !canLearn(sk) || (!Config.AUTO_LEARN_FORGOTTEN_SKILLS && s.isClicked())) {
						unLearnable++;
						continue;
					}
					addSkill(sk, false);
				}
				skills = SkillAcquireHolder.getInstance().getAvailableSkills(this, AcquireType.NORMAL);
			}
			update = true;
		} else {
			int unLearnable = 0;
			Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(this,
					AcquireType.NORMAL);
			for (SkillLearn skill : skills) {
				if (skill.getCost() == 0 && skill.getItemId() == 0) {
					boolean shouldNotAdd = false;
					for (Skill existingSkill : getAllSkillsArray())
						if (existingSkill.getId() == skill.getId() && existingSkill.getLevel() >= skill.getLevel()) {
							shouldNotAdd = true;
							break;
						}

					if (shouldNotAdd)
						continue;
					addSkill(SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel()), false);
				}
			}
			if (getSkillLearnType() == SkillLearnType.Insta_Learn) {
				boolean needsToBreak = false;
				while (skills.size() > unLearnable) {
					unLearnable = 0;
					for (SkillLearn s : skills) {
						Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
						if (sk == null || !canLearn(sk) || s.getItemId() != 0 || s.getCost() == 0) {
							unLearnable++;
							continue;
						}
						if (getSp() < s.getCost()) {
							needsToBreak = true;
							break;
						}
						setSp(getSp() - s.getCost());
						addSkill(sk, true);
						update = true;
					}
					if (needsToBreak)
						break;
					skills = SkillAcquireHolder.getInstance().getAvailableSkills(this, AcquireType.NORMAL);
				}
			}
		}
		if (send && update)
			sendPacket(new SkillList(this));

		updateStats();
	}

	public boolean canLearn(Skill skill) {
		if (!skill.getCanLearn(getActiveClassClassId()))
			if (!skill.getCanLearn(getSecondaryClassId()))
				return false;
		return true;
	}

	public Race getRace() {
		return getBaseTemplate().race;
	}

	public int getIntSp() {
		return (int) getSp();
	}

	public long getSp() {
		return _sp;
	}

	public void addSp(long newSp) {
		_sp = Math.min(_sp + newSp, Integer.MAX_VALUE);
	}

	public void setSp(long sp) {
		_sp = Math.min(sp, Integer.MAX_VALUE);
	}

	public int getClanId() {
		return _clan == null ? 0 : _clan.getClanId();
	}

	public long getLeaveClanTime() {
		return _leaveClanTime;
	}

	public long getDeleteClanTime() {
		return _deleteClanTime;
	}

	public void setLeaveClanTime(final long time) {
		_leaveClanTime = time;
	}

	public void setDeleteClanTime(final long time) {
		_deleteClanTime = time;
	}

	public void setOnlineTime(final long time) {
		_onlineTime = time;
		_onlineBeginTime = System.currentTimeMillis();
	}

	public void setLeaveClanCurTime() {
		_leaveClanTime = System.currentTimeMillis();
	}

	public void setDeleteClanCurTime() {
		_deleteClanTime = System.currentTimeMillis();
	}

	public boolean canJoinClan() {
		return true;
	}

	public boolean canCreateClan() {
		if (_deleteClanTime == 0)
			return true;
		if (System.currentTimeMillis() - _deleteClanTime >= 2 * 24 * 60 * 60 * 1000L) {
			_deleteClanTime = 0;
			return true;
		}
		return false;
	}

	public IStaticPacket canJoinParty(Player inviter) {
		Request request = getRequest();
		if (request != null && request.isInProgress() && request.getOtherPlayer(this) != inviter)
			return SystemMsg.WAITING_FOR_ANOTHER_REPLY.packet(inviter); // Đ·Đ°Đ˝ŃŹŃ‚
		if (isBlockAll() || getMessageRefusal()) // Đ˛Ń�ĐµŃ… Đ˝Đ°Ń„Đ¸Đł
			return SystemMsg.THAT_PERSON_IS_IN_MESSAGE_REFUSAL_MODE.packet(inviter);
		if (isInParty()) // Ń�Đ¶Đµ
			return new SystemMessage2(SystemMsg.C1_IS_A_MEMBER_OF_ANOTHER_PARTY_AND_CANNOT_BE_INVITED).addName(this);
		if (inviter.getReflection() != getReflection()) // Đ˛ Ń€Đ°Đ·Đ˝Ń‹Ń… Đ¸Đ˝Ń�Ń‚Đ°Đ˝Ń‚Đ°Ń…
			if (inviter.getReflection() != ReflectionManager.DEFAULT && getReflection() != ReflectionManager.DEFAULT)
				return SystemMsg.INVALID_TARGET.packet(inviter);
		if (isCursedWeaponEquipped() || inviter.isCursedWeaponEquipped()) // Đ·Đ°Ń€Đ¸Ń‡
			return SystemMsg.INVALID_TARGET.packet(inviter);
		if (inviter.isInOlympiadMode() || isInOlympiadMode()) // ĐľĐ»Đ¸ĐĽĐżĐ¸Đ°Đ´Đ°
			return SystemMsg.A_USER_CURRENTLY_PARTICIPATING_IN_THE_OLYMPIAD_CANNOT_SEND_PARTY_AND_FRIEND_INVITATIONS
					.packet(inviter);
		if (!inviter.getPlayerAccess().CanJoinParty || !getPlayerAccess().CanJoinParty) // Đ˝Đ¸Đ·ŃŹ
			return SystemMsg.INVALID_TARGET.packet(inviter);
		if (getTeam() != TeamType.NONE) // Ń�Ń‡Đ°Ń�Ń‚Đ˝Đ¸Đş ĐżĐ˛Đż ŃŤĐ˛ĐµĐ˝Ń‚Đ° Đ¸Đ»Đ¸ Đ´Ń�ŃŤĐ»Đ¸
			return SystemMsg.INVALID_TARGET.packet(inviter);
		return null;
	}

	@Override
	public PcInventory getInventory() {
		return _inventory;
	}

	@Override
	public long getWearedMask() {
		return _inventory.getWearedMask();
	}

	public PcFreight getFreight() {
		return _freight;
	}

	public void removeItemFromShortCut(final int objectId) {
		_shortCuts.deleteShortCutByObjectId(objectId);
	}

	public void removeSkillFromShortCut(final int skillId) {
		_shortCuts.deleteShortCutBySkillId(skillId);
	}

	public boolean isSitting() {
		return _isSitting;
	}

	public void setSitting(boolean val) {
		_isSitting = val;
	}

	public boolean getSittingTask() {
		return sittingTaskLaunched;
	}

	@Override
	public void sitDown(StaticObjectInstance throne) {
		if (isSitting() || sittingTaskLaunched || isAlikeDead())
			return;

		if (isStunned() || isSleeping() || isParalyzed() || isAttackingNow() || isCastingNow() || isMoving) {
			getAI().setNextAction(nextAction.REST, null, null, false, false);
			return;
		}

		resetWaitSitTime();
		getAI().setIntention(CtrlIntention.AI_INTENTION_REST, null, null);

		if (throne == null)
			broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_SITTING));
		else
			broadcastPacket(new ChairSit(this, throne));

		_sittingObject = throne;
		setSitting(true);
		sittingTaskLaunched = true;
		ThreadPoolManager.getInstance().schedule(new EndSitDownTask(this), 2500);
	}

	@Override
	public void standUp() {
		if (!isSitting() || sittingTaskLaunched || isInStoreMode() || isAlikeDead())
			return;

		// FIXME [G1ta0] ŃŤŃ„Ń„ĐµĐşŃ‚ Ń�Đ°ĐĽ ĐľŃ‚ĐşĐ»ŃŽŃ‡Đ°ĐµŃ‚Ń�ŃŹ Đ˛Đľ Đ˛Ń€ĐµĐĽŃŹ
		// Đ´ĐµĐąŃ�Ń‚Đ˛Đ¸ŃŹ, ĐµŃ�Đ»Đ¸ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Đ˝Đµ Ń�Đ¸Đ´Đ¸Ń‚, Đ˛ĐľĐ·ĐĽĐľĐ¶Đ˝Đľ
		// Ń�Ń‚ĐľĐ¸Ń‚ Ń�Đ±Ń€Đ°Ń‚ŃŚ
		getEffectList().stopAllSkillEffects(EffectType.Relax);

		getAI().clearNextAction();
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));

		_sittingObject = null;
		setSitting(false);
		sittingTaskLaunched = true;
		ThreadPoolManager.getInstance().schedule(new EndStandUpTask(this), 2500);
	}

	public void updateWaitSitTime() {
		if (_waitTimeWhenSit < 200)
			_waitTimeWhenSit += 2;
	}

	public int getWaitSitTime() {
		return _waitTimeWhenSit;
	}

	public void resetWaitSitTime() {
		_waitTimeWhenSit = 0;
	}

	public Warehouse getWarehouse() {
		return _warehouse;
	}

	public ItemContainer getRefund() {
		return _refund;
	}

	public long getAdena() {
		return getInventory().getAdena();
	}

	public boolean reduceAdena(long adena) {
		return reduceAdena(adena, false);
	}

	/**
	 * Đ—Đ°Đ±Đ¸Ń€Đ°ĐµŃ‚ Đ°Đ´ĐµĐ˝Ń� Ń� Đ¸ĐłŃ€ĐľĐşĐ°.<BR>
	 * <BR>
	 *
	 * @param adena  - Ń�ĐşĐľĐ»ŃŚĐşĐľ Đ°Đ´ĐµĐ˝Ń‹ Đ·Đ°Đ±Ń€Đ°Ń‚ŃŚ
	 * @param notify - ĐľŃ‚ĐľĐ±Ń€Đ°Đ¶Đ°Ń‚ŃŚ Ń�Đ¸Ń�Ń‚ĐµĐĽĐ˝ĐľĐµ Ń�ĐľĐľĐ±Ń‰ĐµĐ˝Đ¸Đµ
	 * @return true ĐµŃ�Đ»Đ¸ Ń�Đ˝ŃŹĐ»Đ¸
	 */
	public boolean reduceAdena(long adena, boolean notify) {
		if (adena < 0)
			return false;
		if (adena == 0)
			return true;
		boolean result = getInventory().reduceAdena(adena);
		if (notify && result)
			sendPacket(SystemMessage2.removeItems(ItemTemplate.ITEM_ID_ADENA, adena));
		return result;
	}

	public ItemInstance addAdena(long adena) {
		return addAdena(adena, false);
	}

	/**
	 * Đ”ĐľĐ±Đ°Đ˛Đ»ŃŹĐµŃ‚ Đ°Đ´ĐµĐ˝Ń� Đ¸ĐłŃ€ĐľĐşŃ�.<BR>
	 * <BR>
	 *
	 * @param adena  - Ń�ĐşĐľĐ»ŃŚĐşĐľ Đ°Đ´ĐµĐ˝Ń‹ Đ´Đ°Ń‚ŃŚ
	 * @param notify - ĐľŃ‚ĐľĐ±Ń€Đ°Đ¶Đ°Ń‚ŃŚ Ń�Đ¸Ń�Ń‚ĐµĐĽĐ˝ĐľĐµ Ń�ĐľĐľĐ±Ń‰ĐµĐ˝Đ¸Đµ
	 * @return L2ItemInstance - Đ˝ĐľĐ˛ĐľĐµ ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ Đ°Đ´ĐµĐ˝Ń‹
	 */
	public ItemInstance addAdena(long adena, boolean notify) {
		if (adena < 1)
			return null;
		ItemInstance item = getInventory().addAdena(adena);
		if (item != null && notify)
			sendPacket(SystemMessage2.obtainItems(ItemTemplate.ITEM_ID_ADENA, adena, 0));
		return item;
	}

	public GameClient getNetConnection() {
		return _connection;
	}

	public int getRevision() {
		return _connection == null ? 0 : _connection.getRevision();
	}

	public void setNetConnection(final GameClient connection) {
		_connection = connection;
	}

	// ============= AutoFarm Accessors =============
	/**
	 * Returns the associated AutoFarmState reference, or null if none.
	 */
	public AutoFarmState getAutoFarmState() // <--- Add this method
	{
		return _autoFarmState;
	}

	/**
	 * Assigns a new AutoFarmState reference, which you might store in your
	 * global map as well. Typically used by your AutoFarmEngine.
	 */
	public void setAutoFarmState(AutoFarmState state) // <--- Add this method
	{
		_autoFarmState = state;
	}

	public boolean isConnected() {
		return _connection != null && _connection.isConnected();
	}

	@Override
	public void onAction(final Player player, boolean shift) {
		if (isFrozen()) {
			player.sendPacket(ActionFail.STATIC);
			return;
		}

		if (Events.onAction(player, this, shift)) {
			player.sendPacket(ActionFail.STATIC);
			return;
		}
		// Check if the other player already target this L2Player
		if (player.getTarget() != this) {
			player.setTarget(this);
			if (player.getTarget() == this)
				player.sendPacket(new MyTargetSelected(getObjectId(), 0)); // The color to display in the select window
																			// is White
			else
				player.sendPacket(ActionFail.STATIC);
		} else if (getPrivateStoreType() != Player.STORE_PRIVATE_NONE) {
			if (getDistance(player) > INTERACTION_DISTANCE
					&& player.getAI().getIntention() != CtrlIntention.AI_INTENTION_INTERACT) {
				if (!shift)
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this, null);
				else
					player.sendPacket(ActionFail.STATIC);
			} else
				player.doInteract(this);
		} else if (isAutoAttackable(player))
			player.getAI().Attack(this, false, shift);
		else if (player != this) {
			if (player.getAI().getIntention() != CtrlIntention.AI_INTENTION_FOLLOW) {
				if (!shift)
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, this, Config.FOLLOW_RANGE);
				else
					player.sendPacket(ActionFail.STATIC);
			} else
				player.sendPacket(ActionFail.STATIC);
		} else
			player.sendPacket(ActionFail.STATIC);
	}

	@Override
	public void broadcastStatusUpdate() {
		if (!needStatusUpdate())
			return;

		StatusUpdate su = makeStatusUpdate(StatusUpdate.MAX_HP, StatusUpdate.MAX_MP, StatusUpdate.MAX_CP,
				StatusUpdate.CUR_HP, StatusUpdate.CUR_MP, StatusUpdate.CUR_CP);
		sendPacket(su);

		// Check if a party is in progress
		if (isInParty())
			// Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and
			// Level to all other L2Player of the Party
			getParty().broadcastToPartyMembers(this, new PartySmallWindowUpdate(this));

		DuelEvent duelEvent = getEvent(DuelEvent.class);
		if (duelEvent != null)
			duelEvent.sendPacket(new ExDuelUpdateUserInfo(this), getTeam().revert().name());

		if (isInOlympiadMode() && isOlympiadCompStart()) {
			if (_olympiadGame != null)
				_olympiadGame.broadcastInfo(this, null, false);
		}
	}

	private ScheduledFuture<?> _broadcastCharInfoTask;

	public class BroadcastCharInfoTask extends RunnableImpl {
		@Override
		public void runImpl() throws Exception {
			broadcastCharInfoImpl();
			_broadcastCharInfoTask = null;
		}
	}

	@Override
	public void broadcastCharInfo() {
		broadcastUserInfo(false);
	}

	/**
	 * ĐžŃ‚ĐżŃ€Đ°Đ˛Đ»ŃŹĐµŃ‚ UserInfo Đ´Đ°Đ˝ĐľĐĽŃ� Đ¸ĐłŃ€ĐľĐşŃ� Đ¸ CharInfo Đ˛Ń�ĐµĐĽ
	 * ĐľĐşŃ€Ń�Đ¶Đ°ŃŽŃ‰Đ¸ĐĽ.<BR>
	 * <BR>
	 * <p/>
	 * <B><U> ĐšĐľĐ˝Ń†ĐµĐżŃ‚</U> :</B><BR>
	 * <BR>
	 * ĐˇĐµŃ€Đ˛ĐµŃ€ Ń�Đ»ĐµŃ‚ Đ¸ĐłŃ€ĐľĐşŃ� UserInfo.
	 * ĐˇĐµŃ€Đ˛ĐµŃ€ Đ˛Ń‹Đ·Ń‹Đ˛Đ°ĐµŃ‚ ĐĽĐµŃ‚ĐľĐ´
	 * {@link Creature#broadcastPacketToOthers(l2ft.gameserver.network.l2.s2c.L2GameServerPacket...)}
	 * Đ´Đ»ŃŹ Ń€Đ°Ń�Ń�Ń‹Đ»ĐşĐ¸ CharInfo<BR>
	 * <BR>
	 * <p/>
	 * <B><U> Đ”ĐµĐąŃ�Ń‚Đ˛Đ¸ŃŹ</U> :</B><BR>
	 * <BR>
	 * <li>ĐžŃ‚Ń�Ń‹Đ»ĐşĐ° Đ¸ĐłŃ€ĐľĐşŃ� UserInfo(Đ»Đ¸Ń‡Đ˝Ń‹Đµ Đ¸ ĐľĐ±Ń‰Đ¸Đµ
	 * Đ´Đ°Đ˝Đ˝Ń‹Đµ)</li>
	 * <li>ĐžŃ‚Ń�Ń‹Đ»ĐşĐ° Đ´Ń€Ń�ĐłĐ¸ĐĽ Đ¸ĐłŃ€ĐľĐşĐ°ĐĽ CharInfo(Public data
	 * only)</li><BR>
	 * <BR>
	 * <p/>
	 * <FONT COLOR=#FF0000><B> <U>Đ’Đ˝Đ¸ĐĽĐ°Đ˝Đ¸Đµ</U> : ĐťĐ• ĐźĐžĐˇĐ«Đ›Đ�Đ™Đ˘Đ•
	 * UserInfo Đ´Ń€Ń�ĐłĐ¸ĐĽ Đ¸ĐłŃ€ĐľĐşĐ°ĐĽ Đ»Đ¸Đ±Đľ CharInfo Đ´Đ°Đ˝ĐľĐĽŃ�
	 * Đ¸ĐłŃ€ĐľĐşŃ�.<BR>
	 * ĐťĐ• Đ’Đ«Đ—Đ«Đ’Đ�Đ•Đ™Đ˘Đ• Đ­Đ˘ĐžĐ˘ ĐśĐ•Đ˘ĐžĐ” ĐšĐ ĐžĐśĐ• ĐžĐˇĐžĐ‘Đ«ĐĄ
	 * ĐžĐ‘ĐˇĐ˘ĐžĐŻĐ˘Đ•Đ›Đ¬ĐˇĐ˘Đ’(Ń�ĐĽĐµĐ˝Đ° Ń�Đ°Đ±ĐşĐ»Đ°Ń�Ń�Đ° Đş
	 * ĐżŃ€Đ¸ĐĽĐµŃ€Ń�)!!! Đ˘Ń€Đ°Ń„Ń„Đ¸Đş Đ´Đ¸ĐşĐľ ĐşŃ�Ń�Đ°ĐµŃ‚Ń�ŃŹ Ń� Đ¸ĐłŃ€ĐľĐşĐľĐ˛
	 * Đ¸ Đ˝Đ°Ń‡Đ¸Đ˝Đ°ŃŽŃ‚Ń�ŃŹ Đ»Đ°ĐłĐ¸.<br>
	 * Đ�Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐąŃ‚Đµ ĐĽĐµŃ‚ĐľĐ´ {@link Player#sendChanges()}</B></FONT><BR>
	 * <BR>
	 */
	public void broadcastUserInfo(boolean force) {
		sendUserInfo(force);

		if (!isVisible())
			return;

		if (Config.BROADCAST_CHAR_INFO_INTERVAL == 0)
			force = true;

		if (force) {
			if (_broadcastCharInfoTask != null) {
				_broadcastCharInfoTask.cancel(false);
				_broadcastCharInfoTask = null;
			}
			broadcastCharInfoImpl();
			return;
		}

		if (_broadcastCharInfoTask != null)
			return;

		_broadcastCharInfoTask = ThreadPoolManager.getInstance().schedule(new BroadcastCharInfoTask(),
				Config.BROADCAST_CHAR_INFO_INTERVAL);
	}

	private int _polyNpcId;

	public void setPolyId(int polyid) {
		_polyNpcId = polyid;

		teleToLocation(getLoc());
		broadcastUserInfo(true);
	}

	public boolean isPolymorphed() {
		return _polyNpcId != 0;
	}

	public int getPolyId() {
		return _polyNpcId;
	}

	private void broadcastCharInfoImpl() {
		if (!isVisible())
			return;

		L2GameServerPacket ci = isPolymorphed() ? new NpcInfoPoly(this) : new CharInfo(this);
		L2GameServerPacket exCi = new ExBR_ExtraUserInfo(this);
		L2GameServerPacket dominion = getEvent(DominionSiegeEvent.class) != null ? new ExDominionWarStart(this) : null;
		for (Player player : World.getAroundPlayers(this)) {
			if (isInvisible() && !player.isGM())
				continue;

			player.sendPacket(ci, exCi);
			player.sendPacket(RelationChanged.update(player, this, player));
			if (dominion != null)
				player.sendPacket(dominion);
		}
	}

	public void broadcastRelationChanged() {
		if (!isVisible() || isInvisible())
			return;

		for (Player player : World.getAroundPlayers(this))
			player.sendPacket(RelationChanged.update(player, this, player));
	}

	public void sendEtcStatusUpdate() {
		if (!isVisible())
			return;

		sendPacket(new EtcStatusUpdate(this));
	}

	private Future<?> _userInfoTask;

	private class UserInfoTask extends RunnableImpl {
		@Override
		public void runImpl() throws Exception {
			sendUserInfoImpl();
			_userInfoTask = null;
		}
	}

	private void sendUserInfoImpl() {
		sendPacket(new UserInfo(this), new ExBR_ExtraUserInfo(this));
		DominionSiegeEvent siegeEvent = getEvent(DominionSiegeEvent.class);
		if (siegeEvent != null)
			sendPacket(new ExDominionWarStart(this));
	}

	public void sendUserInfo() {
		sendUserInfo(false);
	}

	public void sendUserInfo(boolean force) {
		if (!isVisible() || entering || isLogoutStarted())
			return;

		if (Config.USER_INFO_INTERVAL == 0 || force) {
			if (_userInfoTask != null) {
				_userInfoTask.cancel(false);
				_userInfoTask = null;
			}
			sendUserInfoImpl();
			return;
		}

		if (_userInfoTask != null)
			return;

		_userInfoTask = ThreadPoolManager.getInstance().schedule(new UserInfoTask(), Config.USER_INFO_INTERVAL);
	}

	@Override
	public StatusUpdate makeStatusUpdate(int... fields) {
		StatusUpdate su = new StatusUpdate(getObjectId());
		for (int field : fields)
			switch (field) {
				case StatusUpdate.CUR_HP:
					su.addAttribute(field, (int) getCurrentHp());
					break;
				case StatusUpdate.MAX_HP:
					su.addAttribute(field, getMaxHp());
					break;
				case StatusUpdate.CUR_MP:
					su.addAttribute(field, (int) getCurrentMp());
					break;
				case StatusUpdate.MAX_MP:
					su.addAttribute(field, getMaxMp());
					break;
				case StatusUpdate.CUR_LOAD:
					su.addAttribute(field, getCurrentLoad());
					break;
				case StatusUpdate.MAX_LOAD:
					su.addAttribute(field, getMaxLoad());
					break;
				case StatusUpdate.PVP_FLAG:
					su.addAttribute(field, _pvpFlag);
					break;
				case StatusUpdate.KARMA:
					su.addAttribute(field, getKarma());
					break;
				case StatusUpdate.CUR_CP:
					su.addAttribute(field, (int) getCurrentCp());
					break;
				case StatusUpdate.MAX_CP:
					su.addAttribute(field, getMaxCp());
					break;
			}
		return su;
	}

	public void sendStatusUpdate(boolean broadCast, boolean withPet, int... fields) {
		if (fields.length == 0 || entering && !broadCast)
			return;

		StatusUpdate su = makeStatusUpdate(fields);
		if (!su.hasAttributes())
			return;

		List<L2GameServerPacket> packets = new ArrayList<L2GameServerPacket>(withPet ? 2 : 1);
		if (withPet && getPet() != null)
			packets.add(getPet().makeStatusUpdate(fields));

		packets.add(su);

		if (!broadCast)
			sendPacket(packets);
		else if (entering)
			broadcastPacketToOthers(packets);
		else
			broadcastPacket(packets);
	}

	/**
	 * @return the Alliance Identifier of the L2Player.<BR>
	 *         <BR>
	 */
	public int getAllyId() {
		return _clan == null ? 0 : _clan.getAllyId();
	}

	@Override
	public void sendPacket(IStaticPacket p) {
		if (!isConnected())
			return;

		if (isPacketIgnored(p.packet(this)))
			return;

		_connection.sendPacket(p.packet(this));
	}

	@Override
	public void sendPacket(IStaticPacket... packets) {
		if (!isConnected())
			return;

		for (IStaticPacket p : packets) {
			if (isPacketIgnored(p))
				continue;

			_connection.sendPacket(p.packet(this));
		}
	}

	private boolean isPacketIgnored(IStaticPacket p) {
		if (p == null)
			return true;
		if (_notShowBuffAnim && (p.getClass() == MagicSkillUse.class || p.getClass() == MagicSkillLaunched.class))
			return true;

		// if(_notShowTraders && (p.getClass() == PrivateStoreMsgBuy.class ||
		// p.getClass() == PrivateStoreMsgSell.class || p.getClass() ==
		// RecipeShopMsg.class))
		// return true;

		return false;
	}

	@Override
	public void sendPacket(List<? extends IStaticPacket> packets) {
		if (!isConnected())
			return;

		for (IStaticPacket p : packets)
			_connection.sendPacket(p.packet(this));
	}

	public void doInteract(GameObject target) {
		if (target == null || isActionsDisabled()) {
			sendActionFailed();
			return;
		}
		if (target.isPlayer()) {
			if (target.getDistance(this) <= INTERACTION_DISTANCE) {
				Player temp = (Player) target;

				if (temp.getPrivateStoreType() == STORE_PRIVATE_SELL
						|| temp.getPrivateStoreType() == STORE_PRIVATE_SELL_PACKAGE) {
					sendPacket(new PrivateStoreListSell(this, temp));
					sendActionFailed();
				} else if (temp.getPrivateStoreType() == STORE_PRIVATE_BUY) {
					sendPacket(new PrivateStoreListBuy(this, temp));
					sendActionFailed();
				} else if (temp.getPrivateStoreType() == STORE_PRIVATE_MANUFACTURE) {
					sendPacket(new RecipeShopSellList(this, temp));
					sendActionFailed();
				}
				sendActionFailed();
			} else if (getAI().getIntention() != CtrlIntention.AI_INTENTION_INTERACT)
				getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this, null);
		} else
			target.onAction(this, false);
	}

	public void doAutoLootOrDrop(ItemInstance item, NpcInstance fromNpc) {
		boolean forceAutoloot = fromNpc.isFlying() || getReflection().isAutolootForced();

		if ((fromNpc.isRaid() || fromNpc instanceof ReflectionBossInstance) && !Config.AUTO_LOOT_FROM_RAIDS
				&& !item.isHerb() && !forceAutoloot) {
			item.dropToTheGround(this, fromNpc);
			return;
		}

		// Herbs
		if (item.isHerb()) {
			if (!AutoLootHerbs && !forceAutoloot) {
				item.dropToTheGround(this, fromNpc);
				return;
			}
			Skill[] skills = item.getTemplate().getAttachedSkills();
			if (skills.length > 0)
				for (Skill skill : skills) {
					altUseSkill(skill, this);
					if (getPet() != null && getPet().isSummon() && !getPet().isDead())
						getPet().altUseSkill(skill, getPet());
				}
			item.deleteMe();
			return;
		}

		if (!_autoLoot && !forceAutoloot) {
			item.dropToTheGround(this, fromNpc);
			return;
		}

		// Đ ŃźĐˇĐ‚Đ Ń•Đ Đ†Đ ÂµĐˇĐ‚Đ Ń”Đ Â° Đ Đ…Đ Â°Đ Â»Đ Ń‘Đˇâ€ˇĐ Ń‘ĐˇĐŹ Đ ŃźĐ Ń’
		// Đ Ň‘Đ Â»ĐˇĐŹ Đ Â°Đ Đ†Đˇâ€šĐ Ń•Đ Â»ĐˇŃ“Đˇâ€šĐ Â°
		if (Config.AUTO_LOOT_PA) {
			if (!(_bonusExpiration != null)) // Đ Đ…Đ Âµ Đ Ń—ĐˇĐ‚Đ Â°Đ Đ†Đ Ń‘Đ Â»ĐˇĐŠĐ Đ…Đ Â°ĐˇĐŹ
												// Đ Ń—ĐˇĐ‚Đ Ń•Đ Đ†Đ ÂµĐˇĐ‚Đ Ń”Đ Â° Đ Ň‘Đ Â»ĐˇĐŹ Đ ŃźĐ Ń’ TODO
			{
				item.dropToTheGround(this, fromNpc);
				sendMessage("Need bay Premium Account");
				return;
			}
		}

		// Check if the L2Player is in a Party
		if (!isInParty()) {
			if (!pickupItem(item, Log.Pickup)) {
				item.dropToTheGround(this, fromNpc);
				return;
			}
		} else
			getParty().distributeItem(this, item, fromNpc);

		broadcastPickUpMsg(item);
	}

	@Override
	public void doPickupItem(final GameObject object) {
		// Check if the L2Object to pick up is a L2ItemInstance
		if (!object.isItem()) {
			_log.warn("trying to pickup wrong target." + getTarget());
			return;
		}

		sendActionFailed();
		stopMove();

		ItemInstance item = (ItemInstance) object;

		synchronized (item) {
			if (!item.isVisible())
				return;

			// Check if me not owner of item and, if in party, not in owner party and
			// nonowner pickup delay still active
			if (!ItemFunctions.checkIfCanPickup(this, item)) {
				SystemMessage sm;
				if (item.getItemId() == 57) {
					sm = new SystemMessage(SystemMessage.YOU_HAVE_FAILED_TO_PICK_UP_S1_ADENA);
					sm.addNumber(item.getCount());
				} else {
					sm = new SystemMessage(SystemMessage.YOU_HAVE_FAILED_TO_PICK_UP_S1);
					sm.addItemName(item.getItemId());
				}
				sendPacket(sm);
				return;
			}

			// Herbs
			if (item.isHerb()) {
				Skill[] skills = item.getTemplate().getAttachedSkills();
				if (skills.length > 0)
					for (Skill skill : skills)
						altUseSkill(skill, this);

				broadcastPacket(new GetItem(item, getObjectId()));
				item.deleteMe();
				return;
			}

			FlagItemAttachment attachment = item.getAttachment() instanceof FlagItemAttachment
					? (FlagItemAttachment) item.getAttachment()
					: null;

			if (!isInParty() || attachment != null) {
				if (pickupItem(item, Log.Pickup)) {
					broadcastPacket(new GetItem(item, getObjectId()));
					broadcastPickUpMsg(item);
					item.pickupMe();
				}
			} else
				getParty().distributeItem(this, item, null);
		}
	}

	/**
	 * Attempt to pick up the specified item from the world or a drop.
	 *
	 * We fix the "count=0" problem by capturing the item count in a local variable
	 * *before* calling getInventory().addItem(...). This ensures we always
	 * log the real quantity that was picked up.
	 *
	 * @param item the ItemInstance being picked up
	 * @param log  a log label used for debugging/logging (e.g., Log.Pickup)
	 * @return true if successful
	 */
	public boolean pickupItem(ItemInstance item, String log) {
		// Safety checks
		if (item == null)
			return false;

		// Make sure we can add the item (inventory capacity, weight, etc.)
		if (!ItemFunctions.canAddItem(this, item))
			return false;

		// CAPTURE THE REAL QUANTITY *NOW*:
		long realCount = item.getCount();

		// Optional quest logic: e.g. quest #255 if item is Adena or item #6353
		if (item.getItemId() == ItemTemplate.ITEM_ID_ADENA || item.getItemId() == 6353) {
			Quest q = QuestManager.getQuest(255);
			if (q != null) {
				// e.g. "CE57" meaning "quest event 'CE57' for quest #255"
				processQuestEvent(q.getName(), "CE" + item.getItemId(), null);
			}
		}

		// Show the standard "obtained item" system message
		sendPacket(SystemMessage2.obtainItems(item));

		// Actually add the item to inventory
		// WARNING: after this call, item.getCount() may become 0 if merged
		getInventory().addItem(item);

		// Now record it in the AutoFarm DropTracker with the *realCount*
		AutoFarmState state = getAutoFarmState();
		if (state != null) {
			DropTrackerData tracker = DropTrackerData.get(state);
			tracker.addDrop(item.getItemId(), realCount);
		}

		// If there's a special item-attachment logic
		if (item.getAttachment() instanceof PickableAttachment) {
			PickableAttachment attachment = (PickableAttachment) item.getAttachment();
			attachment.pickUp(this);
		}

		// Let the server know stats changed
		sendChanges();
		return true;
	}

	public void setObjectTarget(GameObject target) {
		setTarget(target);
		if (target == null)
			return;

		if (target == getTarget()) {
			if (target.isNpc()) {
				NpcInstance npc = (NpcInstance) target;
				sendPacket(new MyTargetSelected(npc.getObjectId(), getLevel() - npc.getLevel()));
				sendPacket(npc.makeStatusUpdate(StatusUpdate.CUR_HP, StatusUpdate.MAX_HP));
				sendPacket(new ValidateLocation(npc), ActionFail.STATIC);
			} else
				sendPacket(new MyTargetSelected(target.getObjectId(), 0));
		}
	}

	@Override
	public void setTarget(GameObject newTarget) {
		// Check if the new target is visible
		if (newTarget != null && !newTarget.isVisible())
			newTarget = null;

		// Can't target and attack festival monsters if not participant
		if (newTarget instanceof FestivalMonsterInstance && !isFestivalParticipant())
			newTarget = null;

		Party party = getParty();

		// Can't target and attack rift invaders if not in the same room
		if (party != null && party.isInDimensionalRift()) {
			int riftType = party.getDimensionalRift().getType();
			int riftRoom = party.getDimensionalRift().getCurrentRoom();
			if (newTarget != null && !DimensionalRiftManager.getInstance().getRoom(riftType, riftRoom)
					.checkIfInZone(newTarget.getX(), newTarget.getY(), newTarget.getZ()))
				newTarget = null;
		}

		GameObject oldTarget = getTarget();

		if (oldTarget != null) {
			if (oldTarget.equals(newTarget))
				return;

			// Remove the L2Player from the _statusListener of the old target if it was a
			// L2Character
			if (oldTarget.isCreature())
				((Creature) oldTarget).removeStatusListener(this);

			broadcastPacket(new TargetUnselected(this));
		}

		if (newTarget != null) {
			// Add the L2Player to the _statusListener of the new target if it's a
			// L2Character
			if (newTarget.isCreature())
				((Creature) newTarget).addStatusListener(this);

			broadcastPacket(new TargetSelected(getObjectId(), newTarget.getObjectId(), getLoc()));
		}

		super.setTarget(newTarget);
	}

	/**
	 * @return the active weapon instance (always equipped in the right hand).<BR>
	 *         <BR>
	 */
	@Override
	public ItemInstance getActiveWeaponInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
	}

	/**
	 * @return the active weapon item (always equipped in the right hand).<BR>
	 *         <BR>
	 */
	@Override
	public WeaponTemplate getActiveWeaponItem() {
		final ItemInstance weapon = getActiveWeaponInstance();

		if (weapon == null)
			return getFistsWeaponItem();

		return (WeaponTemplate) weapon.getTemplate();
	}

	/**
	 * @return the secondary weapon instance (always equipped in the left hand).<BR>
	 *         <BR>
	 */
	@Override
	public ItemInstance getSecondaryWeaponInstance() {
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
	}

	/**
	 * @return the secondary weapon item (always equipped in the left hand) or the
	 *         fists weapon.<BR>
	 *         <BR>
	 */
	@Override
	public WeaponTemplate getSecondaryWeaponItem() {
		final ItemInstance weapon = getSecondaryWeaponInstance();

		if (weapon == null)
			return getFistsWeaponItem();

		final ItemTemplate item = weapon.getTemplate();

		if (item instanceof WeaponTemplate)
			return (WeaponTemplate) item;

		return null;
	}

	public boolean isWearingArmor(final ArmorType armorType) {
		final ItemInstance chest = getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);

		if (chest == null)
			return armorType == ArmorType.NONE;

		if (chest.getItemType() != armorType)
			return false;

		if (chest.getBodyPart() == ItemTemplate.SLOT_FULL_ARMOR)
			return true;

		final ItemInstance legs = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);

		return legs == null ? armorType == ArmorType.NONE : legs.getItemType() == armorType;
	}

	@Override
	public void reduceCurrentHp(double damage, Creature attacker, Skill skill, boolean awake, boolean standUp,
			boolean directHp, boolean canReflect, boolean transferDamage, boolean isDot, boolean sendMessage) {
		if (attacker == null || isDead() || (attacker.isDead() && !isDot))
			return;

		// 5182 = Blessing of protection, Ń€Đ°Đ±ĐľŃ‚Đ°ĐµŃ‚ ĐµŃ�Đ»Đ¸ Ń€Đ°Đ·Đ˝Đ¸Ń†Đ°
		// Ń�Ń€ĐľĐ˛Đ˝ĐµĐą Đ±ĐľĐ»ŃŚŃ�Đµ 10 Đ¸ Đ˝Đµ Đ˛ Đ·ĐľĐ˝Đµ ĐľŃ�Đ°Đ´Ń‹
		if (attacker.isPlayer() && Math.abs(attacker.getLevel() - getLevel()) > 10) {
			// ĐźĐš Đ˝Đµ ĐĽĐľĐ¶ĐµŃ‚ Đ˝Đ°Đ˝ĐµŃ�Ń‚Đ¸ Ń�Ń€ĐľĐ˝ Ń‡Đ°Ń€Ń� Ń� Đ±Đ»ĐµŃ�Ń�Đ¸Đ˝ĐłĐľĐĽ
			if (attacker.getKarma() > 0 && getEffectList().getEffectsBySkillId(5182) != null
					&& !isInZone(ZoneType.SIEGE))
				return;
			// Ń‡Đ°Ń€ Ń� Đ±Đ»ĐµŃ�Ń�Đ¸Đ˝ĐłĐľĐĽ Đ˝Đµ ĐĽĐľĐ¶ĐµŃ‚ Đ˝Đ°Đ˝ĐµŃ�Ń‚Đ¸ Ń�Ń€ĐľĐ˝ ĐźĐš
			if (getKarma() > 0 && attacker.getEffectList().getEffectsBySkillId(5182) != null
					&& !attacker.isInZone(ZoneType.SIEGE))
				return;
		}

		// Reduce the current HP of the L2Player
		super.reduceCurrentHp(damage, attacker, skill, awake, standUp, directHp, canReflect, transferDamage, isDot,
				sendMessage);
	}

	@Override
	protected void onReduceCurrentHp(double damage, Creature attacker, Skill skill, boolean awake, boolean standUp,
			boolean directHp) {
		if (standUp) {
			standUp();
			if (isFakeDeath())
				breakFakeDeath();
		}

		if (attacker.isPlayable()) {
			if (!directHp && getCurrentCp() > 0) {
				double cp = getCurrentCp();
				if (cp >= damage) {
					cp -= damage;
					damage = 0;
				} else {
					damage -= cp;
					cp = 0;
				}

				setCurrentCp(cp);
			}
		}

		double hp = getCurrentHp();

		DuelEvent duelEvent = getEvent(DuelEvent.class);
		if (duelEvent != null)
			if (hp - damage <= 1) // ĐµŃ�Đ»Đ¸ Ń…Đż <= 1 - Ń�Đ±Đ¸Ń‚
			{
				setCurrentHp(1, false);
				duelEvent.onDie(this);
				return;
			}

		if (isInOlympiadMode()) {
			OlympiadGame game = _olympiadGame;
			if (this != attacker && (skill == null || skill.isOffensive())) // Ń�Ń‡Đ¸Ń‚Đ°ĐµĐĽ Đ´Đ°ĐĽĐ°Đł ĐľŃ‚
																			// ĐżŃ€ĐľŃ�Ń‚Ń‹Ń… Ń�Đ´Đ°Ń€ĐľĐ˛ Đ¸
																			// Đ°Ń‚Đ°ĐşŃ�ŃŽŃ‰Đ¸Ń… Ń�ĐşĐ¸Đ»Đ»ĐľĐ˛
				game.addDamage(this, Math.min(hp, damage));

			if (hp - damage <= 1) // ĐµŃ�Đ»Đ¸ Ń…Đż <= 1 - Ń�Đ±Đ¸Ń‚
				if (game.doDie(this)) // Đ’Ń�Đµ Ń�ĐĽĐµŃ€Đ»Đ¸
				{
					game.setWinner(getOlympiadSide() == 1 ? 2 : 1);
					game.endGame(20000, false);
				}
		}

		super.onReduceCurrentHp(damage, attacker, skill, awake, standUp, directHp);
	}

	private void altDeathPenalty(final Creature killer) {
		// Reduce the Experience of the L2Player in function of the calculated Death
		// Penalty
		if (!Config.ALT_GAME_DELEVEL)
			return;
		if (isInZoneBattle())
			return;
		if (getNevitSystem().isBlessingActive())
			return;
		deathPenalty(killer);
	}

	public final boolean atWarWith(final Player player) {
		return _clan != null && player.getClan() != null && getPledgeType() != -1 && player.getPledgeType() != -1
				&& _clan.isAtWarWith(player.getClan().getClanId());
	}

	public boolean atMutualWarWith(Player player) {
		return _clan != null && player.getClan() != null && getPledgeType() != -1 && player.getPledgeType() != -1
				&& _clan.isAtWarWith(player.getClan().getClanId()) && player.getClan().isAtWarWith(_clan.getClanId());
	}

	public final void doPurePk(final Player killer) {
		// Check if the attacker has a PK counter greater than 0
		final int pkCountMulti = Math.max(killer.getPkKills() / 2, 1);

		// Calculate the level difference Multiplier between attacker and killed
		// L2Player
		// final int lvlDiffMulti = Math.max(killer.getLevel() / _level, 1);

		// Calculate the new Karma of the attacker : newKarma =
		// baseKarma*pkCountMulti*lvlDiffMulti
		// Add karma to attacker and increase its PK counter
		killer.increaseKarma(Config.KARMA_MIN_KARMA * pkCountMulti); // * lvlDiffMulti);
		killer.setPkKills(killer.getPkKills() + 1);
	}

	public final void doKillInPeace(final Player killer) // Check if the L2Player killed haven't Karma
	{
		if (_karma <= 0)
			doPurePk(killer);
		else
			killer.setPvpKills(killer.getPvpKills() + 1);
	}

	public void checkAddItemToDrop(List<ItemInstance> array, List<ItemInstance> items, int maxCount) {
		for (int i = 0; i < maxCount && !items.isEmpty(); i++)
			array.add(items.remove(Rnd.get(items.size())));
	}

	public FlagItemAttachment getActiveWeaponFlagAttachment() {
		ItemInstance item = getActiveWeaponInstance();
		if (item == null || !(item.getAttachment() instanceof FlagItemAttachment))
			return null;
		return (FlagItemAttachment) item.getAttachment();
	}

	protected void doPKPVPManage(Creature killer) {
		FlagItemAttachment attachment = getActiveWeaponFlagAttachment();
		if (attachment != null)
			attachment.onDeath(this, killer);

		if (killer == null || killer == _summon || killer == this)
			return;

		if ((isInZoneBattle() || killer.isInZoneBattle()) && !Config.ZONE_PVP_COUNT)
			return;

		if (killer instanceof Summon && (killer = killer.getPlayer()) == null)
			return;

		if (getTournamentMatch() != null)
			return;

		// Processing Karma/PKCount/PvPCount for killer
		if (killer.isPlayer()) {
			final Player pk = (Player) killer;
			final int repValue = Math.abs(getLevel() - pk.getLevel()) < 20 && getClan() != null && pk.getClan() != null
					&& pk.getClan().getReputationScore() > 0 ? 5 : 1;
			boolean war = atMutualWarWith(pk);

			if (war && killer.isInZone(ZoneType.fame))
				FameZoneManager.getInstance().addPvP(killer.getPlayer(), this);
			// TODO [VISTALL] fix it
			if (war /*
					 * || _clan.getSiege() != null && _clan.getSiege() == pk.getClan().getSiege() &&
					 * (_clan.isDefender() && pk.getClan().isAttacker() || _clan.isAttacker() &&
					 * pk.getClan().isDefender())
					 */)
				if (pk.getClan().getReputationScore() > 0 && _clan.getLevel() >= 5 && _clan.getReputationScore() > 0
						&& pk.getClan().getLevel() >= 5) {
					_clan.broadcastToOtherOnlineMembers(new SystemMessage(
							SystemMessage.YOUR_CLAN_MEMBER_S1_WAS_KILLED_S2_POINTS_HAVE_BEEN_DEDUCTED_FROM_YOUR_CLAN_REPUTATION_SCORE_AND_ADDED_TO_YOUR_OPPONENT_CLAN_REPUTATION_SCORE)
							.addString(getName()).addNumber(-_clan.incReputation(-repValue, true, "ClanWar")), this);
					pk.getClan().broadcastToOtherOnlineMembers(new SystemMessage(
							SystemMessage.FOR_KILLING_AN_OPPOSING_CLAN_MEMBER_S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_YOUR_OPPONENTS_CLAN_REPUTATION_SCORE)
							.addNumber(pk.getClan().incReputation(repValue, true, "ClanWar")), pk);
				}

			if (isOnSiegeField() && !Config.SIEGE_PVP_COUNT)
				return;

			if (_pvpFlag > 0 || war || Config.SIEGE_PVP_COUNT && getZone(ZoneType.SIEGE) != null
					|| Config.ZONE_PVP_COUNT && getZone(ZoneType.battle_zone) != null)
				pk.setPvpKills(pk.getPvpKills() + 1);
			else
				doKillInPeace(pk);

			pk.sendChanges();
		}

		int karma = _karma;
		decreaseKarma(Config.KARMA_LOST_BASE);

		// Đ˛ Đ˝ĐľŃ€ĐĽĐ°Đ»ŃŚĐ˝Ń‹Ń… Ń�Ń�Đ»ĐľĐ˛Đ¸ŃŹŃ… Đ˛ĐµŃ‰Đ¸ Ń‚ĐµŃ€ŃŹŃŽŃ‚Ń�ŃŹ
		// Ń‚ĐľĐ»ŃŚĐşĐľ ĐżŃ€Đ¸ Ń�ĐĽĐµŃ€Ń‚Đ¸ ĐľŃ‚ ĐłĐ˛Đ°Ń€Đ´Đ° Đ¸Đ»Đ¸ Đ¸ĐłŃ€ĐľĐşĐ°
		// ĐşŃ€ĐľĐĽĐµ Ń‚ĐľĐłĐľ, Đ°Đ»ŃŚŃ‚ Đ˝Đ° ĐżĐľŃ‚ĐµŃ€ŃŽ Đ˛ĐµŃ‰ĐµĐą ĐżŃ€Đ¸
		// Ń�ĐĽĐµŃ‚Ń€Đ¸ ĐżĐľĐ·Đ˛ĐľĐ»ŃŹĐµŃ‚ Ń‚ĐµŃ€ŃŹŃ‚ŃŚ Đ˛ĐµŃ‰Đ¸ ĐżŃ€Đ¸ Ń�ĐĽŃ‚ĐµŃ€Đ¸
		// ĐľŃ‚ ĐĽĐľĐ˝Ń�Ń‚Ń€Đ°
		boolean isPvP = killer.isPlayable() || killer instanceof GuardInstance;

		if (killer.isMonster() && !Config.DROP_ITEMS_ON_DIE // ĐµŃ�Đ»Đ¸ Ń�Đ±Đ¸Đ» ĐĽĐľĐ˝Ń�Ń‚Ń€ Đ¸ Đ°Đ»ŃŚŃ‚
															// Đ˛Ń‹ĐşĐ»ŃŽŃ‡ĐµĐ˝
				|| isPvP // ĐµŃ�Đ»Đ¸ Ń�Đ±Đ¸Đ» Đ¸ĐłŃ€ĐľĐş Đ¸Đ»Đ¸ ĐłĐ˛Đ°Ń€Đ´ Đ¸
						&& (_pkKills < Config.MIN_PK_TO_ITEMS_DROP // ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ ĐżĐş Ń�Đ»Đ¸Ń�ĐşĐľĐĽ ĐĽĐ°Đ»Đľ
								|| karma == 0 && Config.KARMA_NEEDED_TO_DROP) // ĐşĐ°Ń€ĐĽŃ‹ Đ˝ĐµŃ‚
				|| isFestivalParticipant() // Đ˛ Ń„ĐµŃ�Ń‚Đ¸Đ˛Đ°Đ»Đµ Đ˛ĐµŃ‰Đ¸ Đ˝Đµ Ń‚ĐµŃ€ŃŹŃŽŃ‚Ń�ŃŹ
				|| !killer.isMonster() && !isPvP) // Đ˛ ĐżŃ€ĐľŃ‡Đ¸Ń… Ń�Đ»Ń�Ń‡Đ°ŃŹŃ… Ń‚ĐľĐ¶Đµ
			return;

		// No drop from GM's
		if (!Config.KARMA_DROP_GM && isGM())
			return;

		final int max_drop_count = isPvP ? Config.KARMA_DROP_ITEM_LIMIT : 1;

		double dropRate; // Đ±Đ°Đ·ĐľĐ˛Ń‹Đą Ń�Đ°Đ˝Ń� Đ˛ ĐżŃ€ĐľŃ†ĐµĐ˝Ń‚Đ°Ń…
		if (isPvP)
			dropRate = _pkKills * Config.KARMA_DROPCHANCE_MOD + Config.KARMA_DROPCHANCE_BASE;
		else
			dropRate = Config.NORMAL_DROPCHANCE_BASE;

		int dropEquipCount = 0, dropWeaponCount = 0, dropItemCount = 0;

		for (int i = 0; i < Math.ceil(dropRate / 100) && i < max_drop_count; i++)
			if (Rnd.chance(dropRate)) {
				int rand = Rnd.get(
						Config.DROPCHANCE_EQUIPPED_WEAPON + Config.DROPCHANCE_EQUIPMENT + Config.DROPCHANCE_ITEM) + 1;
				if (rand > Config.DROPCHANCE_EQUIPPED_WEAPON + Config.DROPCHANCE_EQUIPMENT)
					dropItemCount++;
				else if (rand > Config.DROPCHANCE_EQUIPPED_WEAPON)
					dropEquipCount++;
				else
					dropWeaponCount++;
			}

		List<ItemInstance> drop = new LazyArrayList<ItemInstance>(), // ĐľĐ±Ń‰Đ¸Đą ĐĽĐ°Ń�Ń�Đ¸Đ˛ Ń�
																		// Ń€ĐµĐ·Ń�Đ»ŃŚŃ‚Đ°Ń‚Đ°ĐĽĐ¸ Đ˛Ń‹Đ±ĐľŃ€Đ°
				dropItem = new LazyArrayList<ItemInstance>(), dropEquip = new LazyArrayList<ItemInstance>(),
				dropWeapon = new LazyArrayList<ItemInstance>(); // Đ˛Ń€ĐµĐĽĐµĐ˝Đ˝Ń‹Đµ

		getInventory().writeLock();
		try {
			for (ItemInstance item : getInventory().getItems()) {
				if (!item.canBeDropped(this, true) || Config.KARMA_LIST_NONDROPPABLE_ITEMS.contains(item.getItemId()))
					continue;

				if (item.getTemplate().getType2() == ItemTemplate.TYPE2_WEAPON)
					dropWeapon.add(item);
				else if (item.getTemplate().getType2() == ItemTemplate.TYPE2_SHIELD_ARMOR
						|| item.getTemplate().getType2() == ItemTemplate.TYPE2_ACCESSORY)
					dropEquip.add(item);
				else if (item.getTemplate().getType2() == ItemTemplate.TYPE2_OTHER)
					dropItem.add(item);
			}

			checkAddItemToDrop(drop, dropWeapon, dropWeaponCount);
			checkAddItemToDrop(drop, dropEquip, dropEquipCount);
			checkAddItemToDrop(drop, dropItem, dropItemCount);

			// Dropping items, if present
			if (drop.isEmpty())
				return;

			for (ItemInstance item : drop) {
				if (item.isAugmented() && !Config.ALT_ALLOW_DROP_AUGMENTED)
					item.setAugmentationId(0);

				item = getInventory().removeItem(item);
				Log.LogItem(this, Log.PvPDrop, item);

				if (item.getEnchantLevel() > 0)
					sendPacket(new SystemMessage(SystemMessage.DROPPED__S1_S2).addNumber(item.getEnchantLevel())
							.addItemName(item.getItemId()));
				else
					sendPacket(new SystemMessage(SystemMessage.YOU_HAVE_DROPPED_S1).addItemName(item.getItemId()));

				if (killer.isPlayable() && ((Config.AUTO_LOOT && Config.AUTO_LOOT_PK) || this.isInFlyingTransform())) {
					killer.getPlayer().getInventory().addItem(item);
					Log.LogItem(this, Log.Pickup, item);

					killer.getPlayer().sendPacket(SystemMessage2.obtainItems(item));
				} else
					item.dropToTheGround(this,
							Location.findAroundPosition(this, Config.KARMA_RANDOM_DROP_LOCATION_LIMIT));
			}
		} finally {
			getInventory().writeUnlock();
		}
	}

	@Override
	protected void onDeath(Creature killer) {
		getDeathPenalty().checkCharmOfLuck();

		if (isInStoreMode())
			setPrivateStoreType(Player.STORE_PRIVATE_NONE);
		if (isProcessingRequest()) {
			Request request = getRequest();
			if (isInTrade()) {
				Player parthner = request.getOtherPlayer(this);
				sendPacket(SendTradeDone.FAIL);
				parthner.sendPacket(SendTradeDone.FAIL);
			}
			request.cancel();
		}

		setAgathion(0);

		boolean checkPvp = true;
		if (Config.ALLOW_CURSED_WEAPONS) {
			if (isCursedWeaponEquipped()) {
				CursedWeaponsManager.getInstance().dropPlayer(this);
				checkPvp = false;
			} else if (killer != null && killer.isPlayer() && killer.isCursedWeaponEquipped()) {
				CursedWeaponsManager.getInstance().increaseKills(((Player) killer).getCursedWeaponEquippedId());
				checkPvp = false;
			}
		}
		if (killer.isPlayable() && killer.isInOlympiadMode())
			checkPvp = false;

		ForgottenBattlegroundsEvent fgbEvent = getEvent(ForgottenBattlegroundsEvent.class);
		if (checkPvp && fgbEvent != null && killer != null && killer.isPlayable()) {
			Player deadPlayer = getPlayer();
			Player killerPlayer = killer.getPlayer();
			ForgottenBattlegroundsEvent fgbEventKiller = (killerPlayer != null)
					? killerPlayer.getEvent(ForgottenBattlegroundsEvent.class)
					: null;

			if (deadPlayer != null && killerPlayer != null && fgbEventKiller == fgbEvent) {
				TeamType deadTeam = fgbEvent.getTeamOfPlayer(deadPlayer);
				TeamType killerTeam = fgbEvent.getTeamOfPlayer(killerPlayer);
				if (deadTeam != TeamType.NONE && killerTeam != TeamType.NONE && deadTeam != killerTeam) {
					// Both in battleground on opposite teams, skip normal PK/PvP logic
					checkPvp = false;

					_log.info(
							"onDeath: Calling onPlayerKill for ForgottenBattlegroundsEvent - Killer: "
									+ killerPlayer.getName()
									+ ", Victim: "
									+ getName());

					fgbEvent.onPlayerKill(killerPlayer, this);
				}
			}
		}

		// === Add the same check for IslandAssaultEvent ===
		IslandAssaultEvent iaEvent = getEvent(IslandAssaultEvent.class);
		if (checkPvp && iaEvent != null && killer != null && killer.isPlayable()) {
			Player killerPlayer = killer.getPlayer();
			if (killerPlayer != null && killerPlayer.getEvent(IslandAssaultEvent.class) == iaEvent) {
				// Don’t even check teams, skip PK logic
				checkPvp = false;
				iaEvent.onPlayerKill(killerPlayer, this);
			}
		}

		if (checkPvp) {
			doPKPVPManage(killer);
			altDeathPenalty(killer);
		}

		getDeathPenalty().notifyDead(killer);

		setIncreasedForce(0);

		if (isInParty() && getParty().isInReflection() && getParty().getReflection() instanceof DimensionalRift)
			((DimensionalRift) getParty().getReflection()).memberDead(this);

		stopWaterTask();

		if (!isSalvation() && isOnSiegeField() && isCharmOfCourage()) {
			ask(new ConfirmDlg(SystemMsg.YOUR_CHARM_OF_COURAGE_IS_TRYING_TO_RESURRECT_YOU, 60000),
					new ReviveAnswerListener(this, 100, false));
			setCharmOfCourage(false);
		}

		if (getLevel() < 6) {
			Quest q = QuestManager.getQuest(255);
			if (q != null)
				processQuestEvent(q.getName(), "CE30", null);
		}

		super.onDeath(killer);
	}

	public void restoreExp() {
		restoreExp(100.);
	}

	public void restoreExp(double percent) {
		if (percent == 0)
			return;

		int lostexp = 0;

		String lostexps = getVar("lostexp");
		if (lostexps != null) {
			lostexp = Integer.parseInt(lostexps);
			unsetVar("lostexp");
		}

		if (lostexp != 0)
			addExpAndSp((long) (lostexp * percent / 100), 0);
	}

	public void deathPenalty(Creature killer) {
		if (killer == null)
			return;

		// If the player is in Forgotten Battlegrounds and we have that special flag
		if (getVarB("NoExpLossInFGB")) {
			// Skip XP loss entirely
			return;
		}

		final boolean atwar = killer.getPlayer() != null && atWarWith(killer.getPlayer());

		double deathPenaltyBonus = getDeathPenalty().getLevel() * Config.ALT_DEATH_PENALTY_C5_EXPERIENCE_PENALTY;
		if (deathPenaltyBonus < 2)
			deathPenaltyBonus = 1;
		else
			deathPenaltyBonus = deathPenaltyBonus / 2;

		double percentLost = 8.0;

		int level = getLevel();
		if (level >= 79)
			percentLost = 1.0;
		else if (level >= 78)
			percentLost = 1.5;
		else if (level >= 76)
			percentLost = 2.0;
		else if (level >= 40)
			percentLost = 4.0;

		if (Config.ALT_DEATH_PENALTY)
			percentLost = percentLost * Config.RATE_XP + _pkKills * Config.ALT_PK_DEATH_RATE;

		if (isFestivalParticipant() || atwar)
			percentLost = percentLost / 4.0;

		int lostexp = (int) Math.round((Experience.LEVEL[level + 1] - Experience.LEVEL[level]) * percentLost / 100);
		lostexp *= deathPenaltyBonus;

		lostexp = (int) calcStat(Stats.EXP_LOST, lostexp, killer, null);

		if (isOnSiegeField()) {
			SiegeEvent<?, ?> siegeEvent = getEvent(SiegeEvent.class);
			if (siegeEvent != null)
				lostexp = 0;

			if (siegeEvent != null) {
				List<Effect> effect = getEffectList().getEffectsBySkillId(Skill.SKILL_BATTLEFIELD_DEATH_SYNDROME);
				if (effect != null) {
					int syndromeLvl = effect.get(0).getSkill().getLevel();
					getEffectList().stopEffect(Skill.SKILL_BATTLEFIELD_DEATH_SYNDROME);
					int nextLvl = Math.min(syndromeLvl + 1, 5);
					Skill skill = SkillTable.getInstance().getInfo(Skill.SKILL_BATTLEFIELD_DEATH_SYNDROME, nextLvl);
					if (skill != null)
						skill.getEffects(this, this, false, false);
				} else {
					Skill skill = SkillTable.getInstance().getInfo(Skill.SKILL_BATTLEFIELD_DEATH_SYNDROME, 1);
					if (skill != null)
						skill.getEffects(this, this, false, false);
				}
			}
		}

		long before = getExp();
		addExpAndSp(-lostexp, 0);
		long lost = before - getExp();

		if (lost > 0)
			setVar("lostexp", String.valueOf(lost), -1);
	}

	public void setRequest(Request transaction) {
		_request = transaction;
	}

	public Request getRequest() {
		return _request;
	}

	/**
	 * ĐźŃ€ĐľĐ˛ĐµŃ€ĐşĐ°, Đ·Đ°Đ˝ŃŹŃ‚ Đ»Đ¸ Đ¸ĐłŃ€ĐľĐş Đ´Đ»ŃŹ ĐľŃ‚Đ˛ĐµŃ‚Đ° Đ˝Đ°
	 * Đ·Đ°Ń€ĐľŃ�
	 *
	 * @return true, ĐµŃ�Đ»Đ¸ Đ¸ĐłŃ€ĐľĐş Đ˝Đµ ĐĽĐľĐ¶ĐµŃ‚ ĐľŃ‚Đ˛ĐµŃ‚Đ¸Ń‚ŃŚ Đ˝Đ°
	 *         Đ·Đ°ĐżŃ€ĐľŃ�
	 */
	public boolean isBusy() {
		return isProcessingRequest() || isOutOfControl() || isInOlympiadMode() || getTeam() != TeamType.NONE
				|| isInStoreMode() || isInDuel() || getMessageRefusal() || isBlockAll() || isInvisible();
	}

	public boolean isProcessingRequest() {
		if (_request == null)
			return false;
		if (!_request.isInProgress())
			return false;
		return true;
	}

	public boolean isInTrade() {
		return isProcessingRequest() && getRequest().isTypeOf(L2RequestType.TRADE);
	}

	public List<L2GameServerPacket> addVisibleObject(GameObject object, Creature dropper) {
		if (isLogoutStarted() || object == null || object.getObjectId() == getObjectId() || !object.isVisible())
			return Collections.emptyList();

		return object.addPacketList(this, dropper);
	}

	@Override
	public List<L2GameServerPacket> addPacketList(Player forPlayer, Creature dropper) {
		if (isInvisible() && forPlayer.getObjectId() != getObjectId())
			return Collections.emptyList();

		if (getPrivateStoreType() != STORE_PRIVATE_NONE && forPlayer.getVarB("notraders"))
			return Collections.emptyList();

		// Đ•Ń�Đ»Đ¸ ŃŤŃ‚Đľ Ń„ŃŤĐąĐş ĐľĐ±Ń�ĐµŃ€Đ˛ĐµŃ€Đ° - Đ˝Đµ ĐżĐľĐşĐ°Đ·Ń‹Đ˛Đ°Ń‚ŃŚ.
		if (isInObserverMode() && getCurrentRegion() != getObserverRegion()
				&& getObserverRegion() == forPlayer.getCurrentRegion())
			return Collections.emptyList();

		List<L2GameServerPacket> list = new ArrayList<L2GameServerPacket>();
		if (forPlayer.getObjectId() != getObjectId())
			list.add(isPolymorphed() ? new NpcInfoPoly(this) : new CharInfo(this));

		list.add(new ExBR_ExtraUserInfo(this));

		if (isSitting() && _sittingObject != null)
			list.add(new ChairSit(this, _sittingObject));

		if (getPrivateStoreType() != STORE_PRIVATE_NONE) {
			if (getPrivateStoreType() == STORE_PRIVATE_BUY)
				list.add(new PrivateStoreMsgBuy(this));
			else if (getPrivateStoreType() == STORE_PRIVATE_SELL || getPrivateStoreType() == STORE_PRIVATE_SELL_PACKAGE)
				list.add(new PrivateStoreMsgSell(this));
			else if (getPrivateStoreType() == STORE_PRIVATE_MANUFACTURE)
				list.add(new RecipeShopMsg(this));
			if (forPlayer.isInZonePeace()) // ĐśĐ¸Ń€Đ˝Ń‹ĐĽ Ń‚ĐľŃ€ĐłĐľĐ˛Ń†Đ°ĐĽ Đ˝Đµ Đ˝Ń�Đ¶Đ˝Đľ ĐżĐľŃ�Ń‹Đ»Đ°Ń‚ŃŚ
											// Đ±ĐľĐ»ŃŚŃ�Đµ ĐżĐ°ĐşĐµŃ‚ĐľĐ˛, Đ´Đ»ŃŹ ŃŤĐşĐľĐ˝ĐľĐĽĐ¸Đ¸ Ń‚Ń€Đ°Ń„Ń„Đ¸ĐşĐ°
				return list;
		}

		if (isCastingNow()) {
			Creature castingTarget = getCastingTarget();
			Skill castingSkill = getCastingSkill();
			long animationEndTime = getAnimationEndTime();
			if (castingSkill != null && castingTarget != null && castingTarget.isCreature()
					&& getAnimationEndTime() > 0)
				list.add(new MagicSkillUse(this, castingTarget, castingSkill.getId(), castingSkill.getLevel(),
						(int) (animationEndTime - System.currentTimeMillis()), 0));
		}

		if (isInCombat())
			list.add(new AutoAttackStart(getObjectId()));

		list.add(RelationChanged.update(forPlayer, this, forPlayer));
		DominionSiegeEvent dominionSiegeEvent = getEvent(DominionSiegeEvent.class);
		if (dominionSiegeEvent != null)
			list.add(new ExDominionWarStart(this));

		if (isInBoat())
			list.add(getBoat().getOnPacket(this, getInBoatPosition()));
		else {
			if (isMoving || isFollow)
				list.add(movePacket());
		}
		return list;
	}

	public List<L2GameServerPacket> removeVisibleObject(GameObject object, List<L2GameServerPacket> list) {
		if (isLogoutStarted() || object == null || object.getObjectId() == getObjectId()) // FIXME || isTeleporting()
			return null;

		List<L2GameServerPacket> result = list == null ? object.deletePacketList() : list;

		getAI().notifyEvent(CtrlEvent.EVT_FORGET_OBJECT, object);
		return result;
	}

	private void levelSet(int levels) {
		if (levels > 0) {
			sendPacket(Msg.YOU_HAVE_INCREASED_YOUR_LEVEL);
			broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));

			setCurrentHpMp(getMaxHp(), getMaxMp());
			setCurrentCp(getMaxCp());

			Quest q = QuestManager.getQuest(255);
			if (q != null)
				processQuestEvent(q.getName(), "CE40", null);
			if ((getLevel() == 20 || getLevel() == 40 || getLevel() == 76)) {
				boolean secondaryClassMage = ClassId.values()[getSecondaryClassId()].isMage();
				PlayerTemplate t = CharTemplateTable.getInstance().getTemplate(getPrimaryClass(),
						getTemplateClassId(getActiveClass().getFirstClassId(), getSex(), getLevel()),
						secondaryClassMage, getSex() == 1);

				_template = t;

				broadcastCharInfo();

				if (getLevel() == 20 && getVarInt("couponsD", 0) == 0) {
					getInventory().addItem(8869, 5);
					setVar("couponsD", 1, -1);
				} else if (getLevel() == 40 && getVarInt("couponsC", 0) == 0) {
					getInventory().addItem(8870, 5);
					setVar("couponsC", 1, -1);
				}
			}

			addClanPointsOnProfession(getLevel());

			if (getClan() != null && !getActiveClass().isKamael()) {
				int[] classesToAddCRP = { 0, 0 };
				if (getLevel() >= 60)
					classesToAddCRP[0] = getActiveClass().getFirstClassId();
				if (getActiveClass().getSecondaryLevel() >= 60)
					classesToAddCRP[1] = getSecondaryClassId();

				for (int classId : classesToAddCRP)
					if (classId != 0) {
						UnlockedClass clazz = getUnlocks().getUnlockedClass(classId);
						if (!clazz.isGrantedAcademy()) {
							getClan().setReputationScore(getClan().getReputationScore() + 100);
							clazz.setGrantedAcademy();
						}
					}
			}
		} else if (levels < 0)
			if (Config.ALT_REMOVE_SKILLS_ON_DELEVEL)
				checkSkills();

		// Recalculate the party level
		if (isInParty())
			getParty().recalculatePartyData();

		getUnlocks().changeClassVars(getActiveClass().getFirstClassId(), getActiveClass().getLevel(),
				getActiveClass().getExp());

		if (_clan != null)
			_clan.broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));

		if (_matchingRoom != null)
			_matchingRoom.broadcastPlayerUpdate(this);

		// Pomanders
		int[] classes = { getActiveClassClassId().getId(), getSecondaryClassId() };
		int[] classLevels = { getLevel(), getActiveClass().getSecondaryLevel() };
		for (int i = 0; i < classes.length; i++) {
			if (classLevels[i] >= 76 && getVarInt("Pomander" + classes[i], 0) == 0) {
				switch (ClassId.values()[classes[i]]) {
					case cardinal:
						ItemFunctions.addItem(this, 15307, 1, true);
						setVar("Pomander" + classes[i], 1, -1);
						break;
					case evaSaint:
						ItemFunctions.addItem(this, 15308, 1, true);
						setVar("Pomander" + classes[i], 1, -1);
						break;
					case shillienSaint:
						ItemFunctions.addItem(this, 15309, 4, true);
						setVar("Pomander" + classes[i], 1, -1);
						break;
					default:
						break;
				}
			}
		}

		// Give Expertise skill of this level
		restoreSkills(getFirstClassId());
		restoreSkills(getSecondaryClassId());
		rewardSkills(true);
	}

	/**
	 * If forcibly-lowered levels, we remove any skills that exceed that new level,
	 * to match the official 'delevel' logic (if ALT_REMOVE_SKILLS_ON_DELEVEL =
	 * true).
	 */
	public void checkSkills() {
		if (!Config.ALT_REMOVE_SKILLS_ON_DELEVEL)
			return;

		int forcedLevel = getLevel(); // new forcibly-lowered main level
		int forcedSecLvl = getActiveClass().getSecondaryLevel();
		if (forcedSecLvl > forcedLevel)
			forcedSecLvl = forcedLevel;

		Skill[] allSkills = getAllSkillsArray();
		for (Skill sk : allSkills) {
			boolean inPrimary = SkillAcquireHolder.getInstance().isSkillPossibleAtLevel(forcedLevel, getFirstClassId(),
					sk);
			boolean inSecondary = false;
			if (getSecondaryClassId() > 0)
				inSecondary = SkillAcquireHolder.getInstance().isSkillPossibleAtLevel(forcedSecLvl,
						getSecondaryClassId(), sk);

			if (!inPrimary && !inSecondary)
				removeSkill(sk, false);
		}
		updateStats();
	}

	public void startTimers() {
		startAutoSaveTask();
		startPcBangPointsTask();
		startBonusTask();
		getInventory().startTimers();
		resumeQuestTimers();
	}

	public void stopAllTimers() {
		setAgathion(0);
		stopWaterTask();
		stopBonusTask();
		stopHourlyTask();
		stopKickTask();
		stopVitalityTask();
		stopPcBangPointsTask();
		stopAutoSaveTask();
		stopRecomBonusTask(true);
		getInventory().stopAllTimers();
		stopQuestTimers();
		getNevitSystem().stopTasksOnLogout();
	}

	@Override
	public Summon getPet() {
		return _summon;
	}

	public void setPet(Summon summon) {
		boolean isPet = false;
		if (_summon != null && _summon.isPet())
			isPet = true;
		unsetVar("pet");
		_summon = summon;
		autoShot();
		if (summon == null) {
			if (isPet) {
				if (isLogoutStarted())
					if (getPetControlItem() != null)
						setVar("pet", String.valueOf(getPetControlItem().getObjectId()), -1);
				setPetControlItem(null);
			}
			getEffectList().stopEffect(4140);
		}
	}

	public void scheduleDelete() {
		long time = 0L;

		if (Config.SERVICES_ENABLE_NO_CARRIER)
			time = NumberUtils.toInt(getVar("noCarrier"), Config.SERVICES_NO_CARRIER_DEFAULT_TIME);

		scheduleDelete(time * 1000L);
	}

	/**
	 * ĐŁĐ´Đ°Đ»Đ¸Ń‚ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶Đ° Đ¸Đ· ĐĽĐ¸Ń€Đ° Ń‡ĐµŃ€ĐµĐ· Ń�ĐşĐ°Đ·Đ°Đ˝Đ˝ĐľĐµ
	 * Đ˛Ń€ĐµĐĽŃŹ, ĐµŃ�Đ»Đ¸ Đ˝Đ° ĐĽĐľĐĽĐµĐ˝Ń‚ Đ¸Ń�Ń‚ĐµŃ‡ĐµĐ˝Đ¸ŃŹ Đ˛Ń€ĐµĐĽĐµĐ˝Đ¸ ĐľĐ˝
	 * Đ˝Đµ Đ±Ń�Đ´ĐµŃ‚ ĐżŃ€Đ¸Ń�ĐľĐµĐ´Đ¸Đ˝ĐµĐ˝.
	 * <br>
	 * <br>
	 * TODO: Ń‡ĐµŃ€ĐµĐ· ĐĽĐ¸Đ˝Ń�Ń‚Ń� Đ´ĐµĐ»Đ°Ń‚ŃŚ ĐµĐłĐľ Đ˝ĐµŃ�ŃŹĐ·Đ˛Đ¸ĐĽŃ‹ĐĽ.<br>
	 * TODO: Ń�Đ´ĐµĐ»Đ°Ń‚ŃŚ ĐżŃ€Đ¸Đ˛ŃŹĐ·ĐşŃ� Đ˛Ń€ĐµĐĽĐµĐ˝Đ¸ Đş ĐşĐľĐ˝Ń‚ĐµĐşŃ�Ń‚Ń�,
	 * Đ´Đ»ŃŹ Đ·ĐľĐ˝ Ń� Đ»Đ¸ĐĽĐ¸Ń‚ĐľĐĽ Đ˛Ń€ĐµĐĽĐµĐ˝Đ¸ ĐľŃ�Ń‚Đ°Đ˛Đ»ŃŹŃ‚ŃŚ Đ˛ Đ¸ĐłŃ€Đµ
	 * Đ˝Đ° Đ˛Ń�Đµ Đ˛Ń€ĐµĐĽŃŹ Đ˛ Đ·ĐľĐ˝Đµ.<br>
	 * <br>
	 *
	 * @param time Đ˛Ń€ĐµĐĽŃŹ Đ˛ ĐĽĐ¸Đ»Đ»Đ¸Ń�ĐµĐşŃ�Đ˝Đ´Đ°Ń…
	 */
	public void scheduleDelete(long time) {
		if (isLogoutStarted() || isInOfflineMode())
			return;

		broadcastCharInfo();

		ThreadPoolManager.getInstance().schedule(new RunnableImpl() {
			@Override
			public void runImpl() throws Exception {
				if (!isConnected()) {
					prepareToLogout();
					deleteMe();
				}
			}
		}, time);
	}

	@Override
	protected void onDelete() {
		super.onDelete();

		// ĐŁĐ±Đ¸Ń€Đ°ĐµĐĽ Ń„ŃŤĐąĐş Đ˛ Ń‚ĐľŃ‡ĐşĐµ Đ˝Đ°Đ±Đ»ŃŽĐ´ĐµĐ˝Đ¸ŃŹ
		WorldRegion observerRegion = getObserverRegion();
		if (observerRegion != null)
			observerRegion.removeObject(this);

		// Send friendlists to friends that this player has logged off
		_friendList.notifyFriends(false);

		bookmarks.clear();

		_inventory.clear();
		_warehouse.clear();
		_summon = null;
		_arrowItem = null;
		_fistsWeaponItem = null;
		_chars = null;
		_enchantScroll = null;
		_lastNpc = HardReferences.emptyRef();
		_observerRegion = null;
	}

	public void setTradeList(List<TradeItem> list) {
		_tradeList = list;
	}

	public List<TradeItem> getTradeList() {
		return _tradeList;
	}

	public String getSellStoreName() {
		return _sellStoreName;
	}

	public void setSellStoreName(String name) {
		_sellStoreName = Strings.stripToSingleLine(name);
	}

	public void setSellList(boolean packageSell, List<TradeItem> list) {
		if (packageSell)
			_packageSellList = list;
		else
			_sellList = list;
	}

	public List<TradeItem> getSellList() {
		return getSellList(_privatestore == STORE_PRIVATE_SELL_PACKAGE);
	}

	public List<TradeItem> getSellList(boolean packageSell) {
		return packageSell ? _packageSellList : _sellList;
	}

	public String getBuyStoreName() {
		return _buyStoreName;
	}

	public void setBuyStoreName(String name) {
		_buyStoreName = Strings.stripToSingleLine(name);
	}

	public void setBuyList(List<TradeItem> list) {
		_buyList = list;
	}

	public List<TradeItem> getBuyList() {
		return _buyList;
	}

	public void setManufactureName(String name) {
		_manufactureName = Strings.stripToSingleLine(name);
	}

	public String getManufactureName() {
		return _manufactureName;
	}

	public List<ManufactureItem> getCreateList() {
		return _createList;
	}

	public void setCreateList(List<ManufactureItem> list) {
		_createList = list;
	}

	public void setPrivateStoreType(final int type) {
		_privatestore = type;
		if (type != STORE_PRIVATE_NONE)
			setVar("storemode", String.valueOf(type), -1);
		else
			unsetVar("storemode");
	}

	public boolean isInStoreMode() {
		return _privatestore != STORE_PRIVATE_NONE;
	}

	public int getPrivateStoreType() {
		return _privatestore;
	}

	/**
	 * Set the _clan object, _clanId, _clanLeader Flag and title of the
	 * L2Player.<BR>
	 * <BR>
	 *
	 * @param clan the clat to set
	 */
	public void setClan(Clan clan) {
		if (_clan != clan && _clan != null)
			unsetVar("canWhWithdraw");

		Clan oldClan = _clan;
		if (oldClan != null && clan == null)
			for (Skill skill : oldClan.getAllSkills())
				removeSkill(skill, false);

		_clan = clan;

		if (clan == null) {
			_pledgeType = Clan.SUBUNIT_NONE;
			_pledgeClass = 0;
			_powerGrade = 0;
			_apprentice = 0;
			getInventory().validateItems();
			return;
		}

		if (!clan.isAnyMember(getObjectId())) {
			setClan(null);
			if (!isNoble())
				setTitle("");
		}
	}

	@Override
	public Clan getClan() {
		return _clan;
	}

	public SubUnit getSubUnit() {
		return _clan == null ? null : _clan.getSubUnit(_pledgeType);
	}

	public ClanHall getClanHall() {
		int id = _clan != null ? _clan.getHasHideout() : 0;
		return ResidenceHolder.getInstance().getResidence(ClanHall.class, id);
	}

	public Castle getCastle() {
		int id = _clan != null ? _clan.getCastle() : 0;
		return ResidenceHolder.getInstance().getResidence(Castle.class, id);
	}

	public Fortress getFortress() {
		int id = _clan != null ? _clan.getHasFortress() : 0;
		return ResidenceHolder.getInstance().getResidence(Fortress.class, id);
	}

	public Alliance getAlliance() {
		return _clan == null ? null : _clan.getAlliance();
	}

	public boolean isClanLeader() {
		return _clan != null && getObjectId() == _clan.getLeaderId();
	}

	public boolean isAllyLeader() {
		return getAlliance() != null && getAlliance().getLeader().getLeaderId() == getObjectId();
	}

	@Override
	public void reduceArrowCount() {
		sendPacket(SystemMsg.YOU_CAREFULLY_NOCK_AN_ARROW);
		if (!getInventory().destroyItemByObjectId(getInventory().getPaperdollObjectId(Inventory.PAPERDOLL_LHAND), 1L)) {
			getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, null);
			_arrowItem = null;
		}
	}

	/**
	 * Equip arrows needed in left hand and send a Server->Client packet ItemList to
	 * the L2Player then return True.
	 */
	protected boolean checkAndEquipArrows() {
		// Check if nothing is equipped in left hand
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null) {
			ItemInstance activeWeapon = getActiveWeaponInstance();
			if (activeWeapon != null) {
				if (activeWeapon.getItemType() == WeaponType.BOW)
					_arrowItem = getInventory().findArrowForBow(activeWeapon.getTemplate());
				else if (activeWeapon.getItemType() == WeaponType.CROSSBOW)
					getInventory().findArrowForCrossbow(activeWeapon.getTemplate());
			}

			// Equip arrows needed in left hand
			if (_arrowItem != null)
				getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, _arrowItem);
		} else
			// Get the L2ItemInstance of arrows equipped in left hand
			_arrowItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);

		return _arrowItem != null;
	}

	public void setUptime(final long time) {
		_uptime = time;
	}

	public long getUptime() {
		return System.currentTimeMillis() - _uptime;
	}

	public boolean isInParty() {
		return _party != null;
	}

	public void setParty(final Party party) {
		_party = party;
	}

	public void joinParty(final Party party) {
		if (party != null)
			party.addPartyMember(this);
	}

	public void leaveParty() {
		if (isInParty())
			_party.removePartyMember(this, false);
	}

	public Party getParty() {
		return _party;
	}

	public void setLastPartyPosition(Location loc) {
		_lastPartyPosition = loc;
	}

	public Location getLastPartyPosition() {
		return _lastPartyPosition;
	}

	public boolean isGM() {
		return _playerAccess == null ? false : _playerAccess.IsGM;
	}

	/**
	 * ĐťĐ¸ĐłĐ´Đµ Đ˝Đµ Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ, Đ˝Đľ ĐĽĐľĐ¶ĐµŃ‚
	 * ĐżŃ€Đ¸ĐłĐľĐ´Đ¸Ń‚ŃŚŃ�ŃŹ Đ´Đ»ŃŹ Đ‘Đ”
	 */
	public void setAccessLevel(final int level) {
		_accessLevel = level;
	}

	/**
	 * ĐťĐ¸ĐłĐ´Đµ Đ˝Đµ Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ, Đ˝Đľ ĐĽĐľĐ¶ĐµŃ‚
	 * ĐżŃ€Đ¸ĐłĐľĐ´Đ¸Ń‚ŃŚŃ�ŃŹ Đ´Đ»ŃŹ Đ‘Đ”
	 */
	@Override
	public int getAccessLevel() {
		return _accessLevel;
	}

	public void setPlayerAccess(final PlayerAccess pa) {
		if (pa != null)
			_playerAccess = pa;
		else
			_playerAccess = new PlayerAccess();

		setAccessLevel(isGM() || _playerAccess.Menu ? 100 : 0);
	}

	public PlayerAccess getPlayerAccess() {
		return _playerAccess;
	}

	@Override
	public double getLevelMod() {
		return (89. + getLevel()) / 100.0;
	}

	/**
	 * Update Stats of the Player client side by sending Server->Client packet
	 * UserInfo/StatusUpdate to this L2Player and CharInfo/StatusUpdate to all
	 * players around (broadcast).<BR>
	 * <BR>
	 */
	@Override
	public void updateStats() {
		if (entering || isLogoutStarted())
			return;

		refreshOverloaded();
		if (Config.EXPERTISE_PENALTY) {
			refreshExpertisePenalty();
		}
		super.updateStats();
	}

	@Override
	public void sendChanges() {
		if (entering || isLogoutStarted())
			return;
		super.sendChanges();
	}

	/**
	 * Send a Server->Client StatusUpdate packet with Karma to the L2Player and all
	 * L2Player to inform (broadcast).
	 */
	public void updateKarma(boolean flagChanged) {
		sendStatusUpdate(true, true, StatusUpdate.KARMA);
		if (flagChanged)
			broadcastRelationChanged();
	}

	public boolean isOnline() {
		return _isOnline;
	}

	public void setIsOnline(boolean isOnline) {
		_isOnline = isOnline;
	}

	public void setOnlineStatus(boolean isOnline) {
		_isOnline = isOnline;
		updateOnlineStatus();
	}

	private void updateOnlineStatus() {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("UPDATE characters SET online=?, lastAccess=? WHERE obj_id=?");
			statement.setInt(1, isOnline() && !isInOfflineMode() ? 1 : 0);
			statement.setLong(2, System.currentTimeMillis() / 1000L);
			statement.setInt(3, getObjectId());
			statement.execute();
		} catch (final Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	/**
	 * Decrease Karma of the L2Player and Send it StatusUpdate packet with Karma and
	 * PvP Flag (broadcast).
	 */
	public void increaseKarma(final long add_karma) {
		boolean flagChanged = _karma == 0;
		long new_karma = _karma + add_karma;

		if (new_karma > Integer.MAX_VALUE)
			new_karma = Integer.MAX_VALUE;

		if (_karma == 0 && new_karma > 0) {
			if (_pvpFlag > 0) {
				_pvpFlag = 0;
				if (_PvPRegTask != null) {
					_PvPRegTask.cancel(true);
					_PvPRegTask = null;
				}
				sendStatusUpdate(true, true, StatusUpdate.PVP_FLAG);
			}

			_karma = (int) new_karma;
		} else
			_karma = (int) new_karma;

		updateKarma(flagChanged);
	}

	/**
	 * Decrease Karma of the L2Player and Send it StatusUpdate packet with Karma and
	 * PvP Flag (broadcast).
	 */
	public void decreaseKarma(final int i) {
		boolean flagChanged = _karma > 0;
		_karma -= i;
		if (_karma <= 0) {
			_karma = 0;
			updateKarma(flagChanged);
		} else
			updateKarma(false);
	}

	/**
	 * Create a new L2Player and add it in the characters table of the database.<BR>
	 * <BR>
	 * <p/>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Create a new L2Player with an account name</li>
	 * <li>Set the name, the Hair Style, the Hair Color and the Face type of the
	 * L2Player</li>
	 * <li>Add the player in the characters table of the database</li><BR>
	 * <BR>
	 *
	 * @param accountName The name of the L2Player
	 * @param name        The name of the L2Player
	 * @param hairStyle   The hair style Identifier of the L2Player
	 * @param hairColor   The hair color Identifier of the L2Player
	 * @param face        The face type Identifier of the L2Player
	 * @return The L2Player added to the database or null
	 */
	public static Player create(int classId, int sex, String accountName, final String name, final int hairStyle,
			final int hairColor, final int face) {
		PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId, classId, false, sex != 0);

		// Create a new L2Player with an account name
		Player player = new Player(IdFactory.getInstance().getNextId(), template, accountName);

		player.setPrimaryClass(classId);
		player.setName(name);
		player.setTitle("");
		player.setHairStyle(hairStyle);
		player.setHairColor(hairColor);
		player.setFace(face);
		player.setCreateTime(System.currentTimeMillis());

		// Add the player in the characters table of the database
		if (!CharacterDAO.getInstance().insert(player))
			return null;

		return player;
	}

	/**
	 * Retrieve a L2Player from the characters table of the database and add it in
	 * _allObjects of the L2World
	 *
	 * @return The L2Player loaded from the database
	 */
	public static Player restore(final int objectId) {
		Player player = null;
		Connection con = null;
		Statement statement = null;
		Statement statement2 = null;
		PreparedStatement statement3 = null;
		ResultSet rset = null;
		ResultSet rset2 = null;
		ResultSet rset3 = null;
		try {
			// Retrieve the L2Player from the characters table of the database
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.createStatement();
			statement2 = con.createStatement();
			rset = statement.executeQuery("SELECT * FROM `characters` WHERE `obj_Id`=" + objectId + " LIMIT 1");
			rset2 = statement2.executeQuery(
					"SELECT * FROM `character_subclasses` WHERE `char_obj_id`=" + objectId + " AND `active`=1 LIMIT 1");

			if (rset.next() && rset2.next()) {
				int primaryClassTemplate = rset.getInt("primary_class_template");
				final int baseClassId = rset2.getInt("class_id");
				final int level = Experience.getLevel(rset2.getLong("exp"));
				final boolean female = rset.getInt("sex") == 1;
				int templateClassId = 0;
				if (rset2.getInt("isBase") == 0) {
					primaryClassTemplate = baseClassId;
					templateClassId = baseClassId;
				} else
					templateClassId = getTemplateClassId(baseClassId, female ? 1 : 0, level);
				boolean secondaryClassMage = ClassId.values()[rset2.getInt("secondClassId")].isMage();
				final PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(primaryClassTemplate,
						templateClassId, secondaryClassMage, female);

				player = new Player(objectId, template);
				player.setPrimaryClass(rset.getInt("primary_class_template"));
				player.loadVariables();
				player.loadInstanceReuses();
				player.loadPremiumItemList();
				player.bookmarks.setCapacity(rset.getInt("bookmarks"));
				player.bookmarks.restore();
				player._friendList.restore();
				player._postFriends = CharacterPostFriendDAO.getInstance().select(player);
				CharacterGroupReuseDAO.getInstance().select(player);
				player.setName(rset.getString("char_name"));

				player.setStackClass(rset2);
				player.setName(rset.getString("char_name"));

				player._login = rset.getString("account_name");

				player.setFace(rset.getInt("face"));
				player.setHairStyle(rset.getInt("hairStyle"));
				player.setHairColor(rset.getInt("hairColor"));
				player.setHeading(0);

				player.setKarma(rset.getInt("karma"));
				player.setPvpKills(rset.getInt("pvpkills"));
				player.setPkKills(rset.getInt("pkkills"));
				player.setLeaveClanTime(rset.getLong("leaveclan") * 1000L);
				if (player.getLeaveClanTime() > 0 && player.canJoinClan())
					player.setLeaveClanTime(0);
				player.setDeleteClanTime(rset.getLong("deleteclan") * 1000L);
				if (player.getDeleteClanTime() > 0 && player.canCreateClan())
					player.setDeleteClanTime(0);

				player.setOnlineTime(rset.getLong("onlinetime") * 1000L);

				player.setSp(rset.getLong("sp"));

				final int clanId = rset.getInt("clanid");
				if (clanId > 0) {
					player.setClan(ClanTable.getInstance().getClan(clanId));
					player.setPledgeType(rset.getInt("pledge_type"));
					player.setPowerGrade(rset.getInt("pledge_rank"));
					player.setApprentice(rset.getInt("apprentice"));
				}

				player.setLvlJoinedAcademy(rset.getInt("lvl_joined_academy"));

				player.setCreateTime(rset.getLong("createtime") * 1000L);
				player.setDeleteTimer(rset.getInt("deletetime"));

				player.setTitle(rset.getString("title"));

				if (player.getVar("titlecolor") != null)
					player.setTitleColor(Integer.decode("0x" + player.getVar("titlecolor")));

				if (player.getVar("namecolor") == null)
					if (player.isGM())
						player.setNameColor(Config.GM_NAME_COLOUR);
					else if (player.getClan() != null && player.getClan().getLeaderId() == player.getObjectId())
						player.setNameColor(Config.CLANLEADER_NAME_COLOUR);
					else
						player.setNameColor(Config.NORMAL_NAME_COLOUR);
				else
					player.setNameColor(Integer.decode("0x" + player.getVar("namecolor")));

				if (Config.AUTO_LOOT_INDIVIDUAL) {
					player._autoLoot = player.getVarB("AutoLoot", Config.AUTO_LOOT);
					player.AutoLootHerbs = player.getVarB("AutoLootHerbs", Config.AUTO_LOOT_HERBS);
				}

				player.findFistsWeaponItem(primaryClassTemplate);
				player.setUptime(System.currentTimeMillis());
				player.setLastAccess(rset.getLong("lastAccess"));

				player.setRecomHave(rset.getInt("rec_have"));
				player.setRecomLeft(rset.getInt("rec_left"));
				player.setRecomBonusTime(rset.getInt("rec_bonus_time"));

				if (player.getVar("recLeftToday") != null)
					player.setRecomLeftToday(Integer.parseInt(player.getVar("recLeftToday")));
				else
					player.setRecomLeftToday(0);

				player.getNevitSystem().setPoints(rset.getInt("hunt_points"), rset.getInt("hunt_time"));

				player.setKeyBindings(rset.getBytes("key_bindings"));
				player.setPcBangPoints(rset.getInt("pcBangPoints"));

				player.setFame(rset.getInt("fame"), null);

				player.restoreRecipeBook();

				if (Config.ENABLE_OLYMPIAD) {
					player.setHero(Hero.getInstance().isHero(player.getObjectId()));
					player.setNoble(Olympiad.isNoble(player.getObjectId()));
					if (player.isNoble())
						player.updateNobleSkills();
				}

				player.updatePledgeClass();

				int reflection = 0;

				if (player.taskExists(TaskType.Jail)) {
					player.setXYZ(-114648, -249384, -2984);
					player.sitDown(null);
					player.block();
				} else {
					player.setXYZ(rset.getInt("x"), rset.getInt("y"), rset.getInt("z"));
					String ref = player.getVar("reflection");
					if (ref != null) {
						reflection = Integer.parseInt(ref);
						if (reflection > 0) // Đ˝Đµ ĐżĐľŃ€Ń‚Đ°ĐµĐĽ Đ˝Đ°Đ·Đ°Đ´ Đ¸Đ· Đ“ĐĄ, ĐżĐ°Ń€Đ˝Đ°Ń�Đ°, Đ´Đ¶Đ°ĐąĐ»Đ°
						{
							String back = player.getVar("backCoords");
							if (back != null) {
								player.setLoc(Location.parseLoc(back));
								player.unsetVar("backCoords");
							}
							reflection = 0;
						}
					}
				}

				player.setReflection(reflection);

				EventHolder.getInstance().findEvent(player);

				// TODO [G1ta0] Đ·Đ°ĐżŃ�Ń�ĐşĐ°Ń‚ŃŚ Đ˝Đ° Đ˛Ń…ĐľĐ´Đµ
				Quest.restoreQuestStates(player);

				player.getInventory().restore();

				// 4 ĐľŃ‡ĐşĐ° Đ˛ ĐĽĐ¸Đ˝Ń�Ń‚Ń� ĐľŃ„Ń„Đ»Đ°ĐąĐ˝Đ°
				player.setVitality(rset.getInt("vitality")
						+ (int) ((System.currentTimeMillis() / 1000L - rset.getLong("lastAccess")) / 15.));

				// Restore Hero skills at main class only
				if (player.isHero())
					Hero.addSkills(player);

				// Restore clan skills
				if (player.getClan() != null) {
					player.getClan().addSkillsQuietly(player);

					// Restore clan leader siege skills
					if (player.getClan().getLeaderId() == player.getObjectId() && player.getClan().getLevel() >= 5)
						SiegeUtils.addSiegeSkills(player);
				}

				// Give dwarven craft skill
				player.addSkill(SkillTable.getInstance().getInfo(1321, 1));

				player.addSkill(SkillTable.getInstance().getInfo(1322, 1));

				if (Config.UNSTUCK_SKILL && player.getSkillLevel(1050) < 0)
					player.addSkill(SkillTable.getInstance().getInfo(2099, 1));

				try {
					String var = player.getVar("ExpandInventory");
					if (var != null)
						player.setExpandInventory(Integer.parseInt(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar("ExpandWarehouse");
					if (var != null)
						player.setExpandWarehouse(Integer.parseInt(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar(NO_ANIMATION_OF_CAST_VAR);
					if (var != null)
						player.setNotShowBuffAnim(Boolean.parseBoolean(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar(NO_TRADERS_VAR);
					if (var != null)
						player.setNotShowTraders(Boolean.parseBoolean(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar("pet");
					if (var != null)
						player.setPetControlItem(Integer.parseInt(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar("skillLearn");
					if (var != null)
						player.setSkillLearnType(Integer.parseInt(var));
				} catch (Exception e) {
					_log.error("", e);
				}

				try {
					String var = player.getVar("autoOnNonFlagged");
					if (var != null)
						player.setAutoAttackOnNonFlagged(Integer.parseInt(var) == 1 ? true : false);
				} catch (Exception e) {
					_log.error("", e);
				}

				statement3 = con.prepareStatement(
						"SELECT obj_Id, char_name FROM characters WHERE account_name=? AND obj_Id!=?");
				statement3.setString(1, player._login);
				statement3.setInt(2, objectId);
				rset3 = statement3.executeQuery();
				while (rset3.next()) {
					final Integer charId = rset3.getInt("obj_Id");
					final String charName = rset3.getString("char_name");
					player._chars.put(charId, charName);
				}

				DbUtils.close(statement3, rset3);

				// if(!player.isGM())
				{
					LazyArrayList<Zone> zones = LazyArrayList.newInstance();

					World.getZones(zones, player.getLoc(), player.getReflection());

					if (!zones.isEmpty())
						for (Zone zone : zones)
							if (zone.getType() == ZoneType.no_restart) {
								String logOutTime = player.getVar("loggedOut");
								if (logOutTime != null) {
									if (System.currentTimeMillis() / 1000L - Long.parseLong(logOutTime) > zone
											.getRestartTime()) {
										player.sendMessage(new CustomMessage(
												"l2ft.gameserver.clientpackets.EnterWorld.TeleportedReasonNoRestart",
												player));
										player.setLoc(TeleportUtils.getRestartLocation(player, RestartType.TO_VILLAGE));
									}
									player.unsetVar("loggedOut");
								}
							} else if (zone.getType() == ZoneType.SIEGE) {
								SiegeEvent<?, ?> siegeEvent = player.getEvent(SiegeEvent.class);
								if (siegeEvent != null)
									player.setLoc(siegeEvent.getEnterLoc(player));
								else {
									Residence r = ResidenceHolder.getInstance()
											.getResidence(zone.getParams().getInteger("residence"));
									player.setLoc(r.getNotOwnerRestartPoint(player));
								}
							}

					LazyArrayList.recycle(zones);

					if (DimensionalRiftManager.getInstance().checkIfInRiftZone(player.getLoc(), false))
						player.setLoc(DimensionalRiftManager.getInstance().getRoom(0, 0).getTeleportCoords());
				}

				player.restoreBlockList();
				player._macroses.restore();

				// FIXME [VISTALL] Đ˝Ń�Đ¶Đ˝Đľ Đ»Đ¸?
				player.refreshExpertisePenalty();
				player.refreshOverloaded();

				player.getWarehouse().restore();
				player.getFreight().restore();

				player.restoreTradeList();
				if (player.getVar("storemode") != null) {
					player.setPrivateStoreType(Integer.parseInt(player.getVar("storemode")));
					player.setSitting(true);
				}

				player.updateKetraVarka();
				player.updateRam();
				player.checkRecom();
			}
		} catch (Exception e) {
			_log.error("Could not restore char data!", e);
		} finally {
			DbUtils.closeQuietly(statement2, rset2);
			DbUtils.closeQuietly(statement3, rset3);
			DbUtils.closeQuietly(con, statement, rset);
		}
		return player;
	}

	private void loadPremiumItemList() {
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"SELECT itemNum, itemId, itemCount, itemSender FROM character_premium_items WHERE charId=?");
			statement.setInt(1, getObjectId());
			rs = statement.executeQuery();
			while (rs.next()) {
				int itemNum = rs.getInt("itemNum");
				int itemId = rs.getInt("itemId");
				long itemCount = rs.getLong("itemCount");
				String itemSender = rs.getString("itemSender");
				PremiumItem item = new PremiumItem(itemId, itemCount, itemSender);
				_premiumItems.put(itemNum, item);
			}
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rs);
		}
	}

	public void updatePremiumItem(int itemNum, long newcount) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con
					.prepareStatement("UPDATE character_premium_items SET itemCount=? WHERE charId=? AND itemNum=?");
			statement.setLong(1, newcount);
			statement.setInt(2, getObjectId());
			statement.setInt(3, itemNum);
			statement.execute();
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public void deletePremiumItem(int itemNum) {
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("DELETE FROM character_premium_items WHERE charId=? AND itemNum=?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, itemNum);
			statement.execute();
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public Map<Integer, PremiumItem> getPremiumItemList() {
		return _premiumItems;
	}

	/**
	 * Persists this player's data to DB (subclass, variables, location, etc.).
	 * If "IgnoreDelevelStore" is set to "1", we skip writing forcibly-lowered
	 * level/exp to DB, and also skip storeMainClasses.
	 */
	public void store(boolean fast) {
		if (!_storeLock.tryLock())
			return;

		try {
			Connection con = null;
			PreparedStatement statement = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();

				// If forcibly-lowered => skip storing that lowered level/exp
				boolean skipLevelExp = "1".equals(getVar("IgnoreDelevelStore"));

				String sql = "UPDATE characters SET face=?,hairStyle=?,hairColor=?,"
						+ "x=?,y=?,z=?,"
						+ "karma=?,pvpkills=?,pkkills=?,"
						+ "rec_have=?,rec_left=?,rec_bonus_time=?,"
						+ "clanid=?,deletetime=?,title=?,"
						+ "accesslevel=?,online=?,leaveclan=?,deleteclan=?,onlinetime=?,"
						+ "char_name=?,sp=?";

				if (!skipLevelExp)
					sql += ",level=?,exp=?";

				sql += " WHERE obj_Id=? LIMIT 1";

				statement = con.prepareStatement(sql);

				int paramIndex = 1;

				// Fill basic appearance, position, etc. (omitted for brevity)
				statement.setInt(paramIndex++, getFace());
				statement.setInt(paramIndex++, getHairStyle());
				statement.setInt(paramIndex++, getHairColor());
				statement.setInt(paramIndex++, getX());
				statement.setInt(paramIndex++, getY());
				statement.setInt(paramIndex++, getZ());
				statement.setInt(paramIndex++, getKarma());
				statement.setInt(paramIndex++, getPvpKills());
				statement.setInt(paramIndex++, getPkKills());
				statement.setInt(paramIndex++, getRecomHave());
				statement.setInt(paramIndex++, getRecomLeft());
				statement.setInt(paramIndex++, getRecomBonusTime());
				statement.setInt(paramIndex++, getClanId());
				statement.setLong(paramIndex++, getDeleteTimer());
				statement.setString(paramIndex++, getTitle());
				statement.setInt(paramIndex++, getAccessLevel());
				statement.setInt(paramIndex++, isOnline() && !isInOfflineMode() ? 1 : 0);
				statement.setLong(paramIndex++, getLeaveClanTime());
				statement.setLong(paramIndex++, getDeleteClanTime());
				statement.setLong(paramIndex++, getOnlineTime());
				statement.setString(paramIndex++, getName());
				statement.setLong(paramIndex++, getSp());

				if (!skipLevelExp) {
					statement.setInt(paramIndex++, getLevel());
					statement.setLong(paramIndex++, getExp());
				}

				statement.setInt(paramIndex++, getObjectId());
				statement.executeUpdate();

				if (!fast) {
					// store additional data: effect slots, reuses, etc.
					EffectsDAO.getInstance().insert(this);
					CharacterGroupReuseDAO.getInstance().insert(this);
				}

				// if ignoring forcibly-lowered changes, skip storeMainClasses
				if (!skipLevelExp) {
					storeMainClasses(true);
				}
			} catch (Exception e) {
				_log.warn("Could not store char data for " + this + "!", e);
			} finally {
				DbUtils.closeQuietly(con, statement);
			}
		} finally {
			_storeLock.unlock();
		}
	}

	public Skill addSkill(final Skill newSkill, final boolean store) {
		return addSkill(newSkill, store, getClassOfTheSkill(newSkill));
	}

	/**
	 * Add a skill to the Player's skill list, optionally storing it in the DB.
	 *
	 * @param newSkill The Skill object to add in memory
	 * @param store    if true, also store in character_skills
	 * @param classId  which class_id to store in DB
	 * @return the Skill replaced, or null if it was a new skill
	 */
	public Skill addSkill(final Skill newSkill, final boolean store, int classId) {
		if (newSkill == null) {
			return null;
		}

		// Add skill in memory
		Skill oldSkill = super.addSkill(newSkill);

		// Only store if we are NOT ignoring forcibly-lowered skill changes
		if (store && !"1".equals(getVar("IgnoreDelevelStore"))) {
			storeSkill(newSkill, oldSkill, classId);
		}

		return oldSkill;
	}

	/**
	 * Remove a skill from the Player's skill list (in memory),
	 * optionally removing it from the DB as well.
	 *
	 * @param skill  the Skill object to remove
	 * @param fromDB if true, remove/update from character_skills
	 * @return the Skill that was removed, or null if not found
	 */
	public Skill removeSkill(Skill skill, boolean fromDB) {
		if (skill == null) {
			return null;
		}

		// remove from memory
		Skill oldSkill = super.removeSkillById(skill.getId());
		if (oldSkill == null) {
			return null;
		}

		// If ignoring forcibly-lowered changes => skip removing from DB
		if (fromDB && !"1".equals(getVar("IgnoreDelevelStore"))) {
			removeSkillFromDB(oldSkill, getClassOfTheSkill(skill));
		}

		return oldSkill;
	}

	public int getClassOfTheSkill(Skill skill) {
		int firstClassId = getActiveClass().getFirstClassId();
		if (SkillAcquireHolder.getInstance().isItClassSkill(getLevel(), firstClassId, skill))
			return firstClassId;
		else if (SkillAcquireHolder.getInstance().isItClassSkill(getSecondaryClassId(), getSecondaryClassId(), skill))
			return getSecondaryClassId();
		else if (SkillAcquireHolder.getInstance().isItPomanderClassSkill(getLevel(), firstClassId, skill))
			return firstClassId;
		else if (SkillAcquireHolder.getInstance().isItPomanderClassSkill(getActiveClass().getSecondaryLevel(),
				getSecondaryClassId(), skill))
			return getSecondaryClassId();
		else
			return 0;
	}

	public void removeSkillFromDB(Skill skill, int classId) {
		if (skill == null) {
			return;
		}
		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"DELETE FROM character_skills WHERE skill_id=? AND char_obj_id=? AND class_index=?");
			statement.setInt(1, skill.getId());
			statement.setInt(2, getObjectId());
			statement.setInt(3, classId);
			statement.execute();
		} catch (final Exception e) {
			_log.error("Could not delete skill from DB!", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	/**
	 * Add or update a L2Player skill in the character_skills table of the database.
	 */
	private void storeSkill(final Skill newSkill, final Skill oldSkill, int classId) {
		if (newSkill == null) {
			_log.warn("Could not store skill: newSkill is null");
			return;
		}

		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"REPLACE INTO character_skills (char_obj_id, skill_id, skill_level, class_index) VALUES (?,?,?,?)");
			statement.setInt(1, getObjectId());
			statement.setInt(2, newSkill.getId());
			statement.setInt(3, newSkill.getLevel());
			statement.setInt(4, classId);
			statement.execute();
		} catch (final Exception e) {
			_log.error("Error while storing skill!", e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	/**
	 * Retrieve from the database all skills of this L2Player and add them to
	 * _skills.
	 */
	private void restoreSkills(int classId) {
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;

		try {
			// Retrieve all skills of this L2Player from the database
			// Send the SQL query : SELECT skill_id,skill_level FROM character_skills WHERE
			// char_obj_id=? to the database
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"SELECT class_index, skill_id,skill_level FROM character_skills WHERE char_obj_id=?");
			statement.setInt(1, getObjectId());
			rset = statement.executeQuery();

			// Go though the recordset of this SQL query
			while (rset.next()) {

				int skillClassIndex = rset.getInt("class_index");
				if (skillClassIndex != 0 && skillClassIndex != classId)
					continue;

				final int id = rset.getInt("skill_id");
				int level = rset.getInt("skill_level");

				// Create a L2Skill object for each record
				Skill skill = SkillTable.getInstance().getInfo(id, level);

				if (skill == null)
					continue;
				// Remove skill if not possible
				if (!isGM()) {
					boolean triedEnded = false;
					while (!SkillAcquireHolder.getInstance().isSkillPossible(this, skill) && !triedEnded) {
						if (level == 1)
							triedEnded = true;
						else
							skill = SkillTable.getInstance().getInfo(id, --level);
					}
					if (triedEnded)
						continue;
				}

				boolean shouldSkip = false;
				for (Skill existingSkill : getAllSkillsArray())
					if (existingSkill.getId() == id && existingSkill.getLevel() > level) {
						shouldSkip = true;
						break;
					}
				if (shouldSkip)
					continue;

				super.addSkill(skill);
			}
			// Restore Hero skills at main class only
			if (_hero)
				Hero.addSkills(this);

			// Restore clan skills
			if (_clan != null) {
				_clan.addSkillsQuietly(this);

				// Restore clan leader siege skills
				if (_clan.getLeaderId() == getObjectId() && _clan.getLevel() >= 5)
					SiegeUtils.addSiegeSkills(this);
			}

			// Give dwarven craft skill
			super.addSkill(SkillTable.getInstance().getInfo(1321, 1));

			super.addSkill(SkillTable.getInstance().getInfo(1322, 1));

			if (Config.UNSTUCK_SKILL && getSkillLevel(1050) < 0)
				super.addSkill(SkillTable.getInstance().getInfo(2099, 1));
		} catch (final Exception e) {
			_log.warn("Could not restore skills for player objId: " + getObjectId());
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	public void storeDisableSkills() {
		Connection con = null;
		Statement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.createStatement();
			statement.executeUpdate("DELETE FROM character_skills_save WHERE char_obj_id = " + getObjectId()
					+ " AND class_index=" + getFirstClassId() + " AND `end_time` < " + System.currentTimeMillis());

			if (_skillReuses.isEmpty())
				return;

			SqlBatch b = new SqlBatch(
					"REPLACE INTO `character_skills_save` (`char_obj_id`,`skill_id`,`skill_level`,`class_index`,`end_time`,`reuse_delay_org`) VALUES");
			synchronized (_skillReuses) {
				StringBuilder sb;
				for (TimeStamp timeStamp : _skillReuses.values()) {
					if (timeStamp.hasNotPassed()) {
						sb = new StringBuilder("(");
						sb.append(getObjectId()).append(",");
						sb.append(timeStamp.getId()).append(",");
						sb.append(timeStamp.getLevel()).append(",");
						sb.append(getFirstClassId()).append(",");
						sb.append(timeStamp.getEndTime()).append(",");
						sb.append(timeStamp.getReuseBasic()).append(")");
						b.write(sb.toString());
					}
				}
			}
			if (!b.isEmpty())
				statement.executeUpdate(b.close());
		} catch (final Exception e) {
			_log.warn("Could not store disable skills data: " + e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public void restoreDisableSkills() {
		_skillReuses.clear();

		Connection con = null;
		Statement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.createStatement();
			rset = statement.executeQuery(
					"SELECT skill_id,skill_level,end_time,reuse_delay_org FROM character_skills_save WHERE char_obj_id="
							+ getObjectId() + " AND class_index=" + getFirstClassId());
			while (rset.next()) {
				int skillId = rset.getInt("skill_id");
				int skillLevel = rset.getInt("skill_level");
				long endTime = rset.getLong("end_time");
				long rDelayOrg = rset.getLong("reuse_delay_org");
				long curTime = System.currentTimeMillis();

				Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);

				if (skill != null && endTime - curTime > 500)
					_skillReuses.put(skill.hashCode(), new TimeStamp(skill, endTime, rDelayOrg));
			}
			DbUtils.close(statement);

			statement = con.createStatement();
			statement.executeUpdate("DELETE FROM character_skills_save WHERE char_obj_id = " + getObjectId()
					+ " AND class_index=" + getFirstClassId() + " AND `end_time` < " + System.currentTimeMillis());
		} catch (Exception e) {
			_log.error("Could not restore active skills data!", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	public int getPomanderClassOfTheSkill(Skill skill) {
		int firstClassId = getActiveClass().getFirstClassId();
		if (SkillAcquireHolder.getInstance().isItPomanderClassSkill(getLevel(), firstClassId, skill))
			return firstClassId;
		else if (SkillAcquireHolder.getInstance().isItPomanderClassSkill(getSecondaryClassId(), getSecondaryClassId(),
				skill))
			return getSecondaryClassId();
		else
			return 0;
	}

	/**
	 * Retrieve from the database all Henna of this L2Player, add them to _henna and
	 * calculate stats of the L2Player.<BR>
	 * <BR>
	 */
	private void restoreHenna() {
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"select slot, symbol_id from character_hennas where char_obj_id=? AND class_index=?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, getActiveClass().getFirstClassId());
			rset = statement.executeQuery();

			for (int i = 0; i < 3; i++)
				_henna[i] = null;

			while (rset.next()) {
				final int slot = rset.getInt("slot");
				if (slot < 1 || slot > 3)
					continue;

				final int symbol_id = rset.getInt("symbol_id");

				if (symbol_id != 0) {
					final Henna tpl = HennaHolder.getInstance().getHenna(symbol_id);
					if (tpl != null) {
						_henna[slot - 1] = tpl;
					}
				}
			}
		} catch (final Exception e) {
			_log.warn("could not restore henna: " + e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}

		// Calculate Henna modifiers of this L2Player
		recalcHennaStats();

	}

	public int getHennaEmptySlots() {
		ClassId classId = ClassId.values()[getActiveClass().getFirstClassId()];
		int totalSlots = 1 + classId.level();
		for (int i = 0; i < 3; i++)
			if (_henna[i] != null)
				totalSlots--;

		if (totalSlots <= 0)
			return 0;

		return totalSlots;
	}

	/**
	 * Remove a Henna of the L2Player, save update in the character_hennas table of
	 * the database and send Server->Client HennaInfo/UserInfo packet to this
	 * L2Player.<BR>
	 * <BR>
	 */
	public boolean removeHenna(int slot) {
		if (slot < 1 || slot > 3)
			return false;

		slot--;

		if (_henna[slot] == null)
			return false;

		final Henna henna = _henna[slot];
		final int dyeID = henna.getDyeId();

		_henna[slot] = null;

		Connection con = null;
		PreparedStatement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con
					.prepareStatement("DELETE FROM character_hennas where char_obj_id=? and slot=? and class_index=?");
			statement.setInt(1, getObjectId());
			statement.setInt(2, slot + 1);
			statement.setInt(3, getActiveClass().getFirstClassId());
			statement.execute();
		} catch (final Exception e) {
			_log.warn("could not remove char henna: " + e, e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}

		// Calculate Henna modifiers of this L2Player
		recalcHennaStats();

		// Send Server->Client HennaInfo packet to this L2Player
		sendPacket(new HennaInfo(this));
		// Send Server->Client UserInfo packet to this L2Player
		sendUserInfo(true);

		// Add the recovered dyes to the player's inventory and notify them.
		ItemFunctions.addItem(this, dyeID, henna.getDrawCount() / 2, true);

		return true;
	}

	/**
	 * Add a Henna to the L2Player, save update in the character_hennas table of the
	 * database and send Server->Client HennaInfo/UserInfo packet to this
	 * L2Player.<BR>
	 * <BR>
	 *
	 * @param henna L2Henna Đ Ň‘Đ Â»ĐˇĐŹ Đ Ň‘Đ Ń•Đ Â±Đ Â°Đ Đ†Đ Â»Đ ÂµĐ Đ…Đ Ń‘ĐˇĐŹ
	 */
	public boolean addHenna(Henna henna) {
		if (getHennaEmptySlots() == 0) {
			sendPacket(SystemMsg.NO_SLOT_EXISTS_TO_DRAW_THE_SYMBOL);
			return false;
		}

		// int slot = 0;
		for (int i = 0; i < 3; i++)
			if (_henna[i] == null) {
				_henna[i] = henna;

				// Calculate Henna modifiers of this L2Player
				recalcHennaStats();

				Connection con = null;
				PreparedStatement statement = null;
				try {
					con = DatabaseFactory.getInstance().getConnection();
					statement = con.prepareStatement(
							"INSERT INTO `character_hennas` (char_obj_id, symbol_id, slot, class_index) VALUES (?,?,?,?)");
					statement.setInt(1, getObjectId());
					statement.setInt(2, henna.getSymbolId());
					statement.setInt(3, i + 1);
					statement.setInt(4, getActiveClass().getFirstClassId());
					statement.execute();
				} catch (Exception e) {
					_log.warn("could not save char henna: " + e);
				} finally {
					DbUtils.closeQuietly(con, statement);
				}

				sendPacket(new HennaInfo(this));
				sendUserInfo(true);

				return true;
			}

		return false;
	}

	/**
	 * Calculate Henna modifiers of this L2Player.
	 */
	private void recalcHennaStats() {
		_hennaINT = 0;
		_hennaSTR = 0;
		_hennaCON = 0;
		_hennaMEN = 0;
		_hennaWIT = 0;
		_hennaDEX = 0;

		for (int i = 0; i < 3; i++) {
			Henna henna = _henna[i];
			if (henna == null)
				continue;
			if (!henna.isForThisClass(this))
				continue;

			_hennaINT += henna.getStatINT();
			_hennaSTR += henna.getStatSTR();
			_hennaMEN += henna.getStatMEN();
			_hennaCON += henna.getStatCON();
			_hennaWIT += henna.getStatWIT();
			_hennaDEX += henna.getStatDEX();
		}

		if (_hennaINT > 5)
			_hennaINT = 5;
		if (_hennaSTR > 5)
			_hennaSTR = 5;
		if (_hennaMEN > 5)
			_hennaMEN = 5;
		if (_hennaCON > 5)
			_hennaCON = 5;
		if (_hennaWIT > 5)
			_hennaWIT = 5;
		if (_hennaDEX > 5)
			_hennaDEX = 5;
	}

	/**
	 * @param slot id Ń�Đ»ĐľŃ‚Đ° Ń� ĐżĐµŃ€Ń�Đ°
	 * @return the Henna of this L2Player corresponding to the selected slot.<BR>
	 *         <BR>
	 */
	public Henna getHenna(final int slot) {
		if (slot < 1 || slot > 3)
			return null;
		return _henna[slot - 1];
	}

	public int getHennaStatINT() {
		return _hennaINT;
	}

	public int getHennaStatSTR() {
		return _hennaSTR;
	}

	public int getHennaStatCON() {
		return _hennaCON;
	}

	public int getHennaStatMEN() {
		return _hennaMEN;
	}

	public int getHennaStatWIT() {
		return _hennaWIT;
	}

	public int getHennaStatDEX() {
		return _hennaDEX;
	}

	@Override
	public boolean consumeItem(int itemConsumeId, long itemCount) {
		if (getInventory().destroyItemByItemId(itemConsumeId, itemCount)) {
			sendPacket(SystemMessage2.removeItems(itemConsumeId, itemCount));
			return true;
		}
		return false;
	}

	@Override
	public boolean consumeItemMp(int itemId, int mp) {
		for (ItemInstance item : getInventory().getPaperdollItems())
			if (item != null && item.getItemId() == itemId) {
				final int newMp = item.getLifeTime() - mp;
				if (newMp >= 0) {
					item.setLifeTime(newMp);
					sendPacket(new InventoryUpdate().addModifiedItem(item));
					return true;
				}
				break;
			}
		return false;
	}

	/**
	 * @return True if the L2Player is a Mage.<BR>
	 *         <BR>
	 */
	@Override
	public boolean isMageClass() {
		return _template.baseMAtk > 3;
	}

	public boolean isMounted() {
		return _mountNpcId > 0;
	}

	public final boolean isRiding() {
		return _riding;
	}

	public final void setRiding(boolean mode) {
		_riding = mode;
	}

	/**
	 * ĐźŃ€ĐľĐ˛ĐµŃ€ŃŹĐµŃ‚, ĐĽĐľĐ¶Đ˝Đľ Đ»Đ¸ ĐżŃ€Đ¸Đ·ĐµĐĽĐ»Đ¸Ń‚ŃŚŃ�ŃŹ Đ˛ ŃŤŃ‚ĐľĐą
	 * Đ·ĐľĐ˝Đµ.
	 *
	 * @return ĐĽĐľĐ¶Đ˝Đľ Đ»Đ¸ ĐżŃ€Đ¸Đ·ĐµĐĽĐ»Đ¸Ń‚Ń�ŃŹ
	 */
	public boolean checkLandingState() {
		if (isInZone(ZoneType.no_landing))
			return false;

		SiegeEvent<?, ?> siege = getEvent(SiegeEvent.class);
		if (siege != null) {
			Residence unit = siege.getResidence();
			if (unit != null && getClan() != null && isClanLeader()
					&& (getClan().getCastle() == unit.getId() || getClan().getHasFortress() == unit.getId()))
				return true;
			return false;
		}

		return true;
	}

	public void setMount(int npcId, int obj_id, int level) {
		if (isCursedWeaponEquipped())
			return;

		switch (npcId) {
			case 0: // Dismount
				setFlying(false);
				setRiding(false);
				if (getTransformation() > 0)
					setTransformation(0);
				removeSkillById(Skill.SKILL_STRIDER_ASSAULT);
				removeSkillById(Skill.SKILL_WYVERN_BREATH);
				getEffectList().stopEffect(Skill.SKILL_HINDER_STRIDER);
				break;
			case PetDataTable.STRIDER_WIND_ID:
			case PetDataTable.STRIDER_STAR_ID:
			case PetDataTable.STRIDER_TWILIGHT_ID:
			case PetDataTable.RED_STRIDER_WIND_ID:
			case PetDataTable.RED_STRIDER_STAR_ID:
			case PetDataTable.RED_STRIDER_TWILIGHT_ID:
			case PetDataTable.GUARDIANS_STRIDER_ID:
				setRiding(true);
				if (isNoble())
					addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_STRIDER_ASSAULT, 1), false);
				break;
			case PetDataTable.WYVERN_ID:
				setFlying(true);
				setLoc(getLoc().changeZ(32));
				addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_WYVERN_BREATH, 1), false);
				break;
			case PetDataTable.WGREAT_WOLF_ID:
			case PetDataTable.FENRIR_WOLF_ID:
			case PetDataTable.WFENRIR_WOLF_ID:
				setRiding(true);
				break;
		}

		if (npcId > 0)
			unEquipWeapon();

		_mountNpcId = npcId;
		_mountObjId = obj_id;
		_mountLevel = level;

		broadcastUserInfo(true); // Đ˝Ń�Đ¶Đ˝Đľ ĐżĐľŃ�Đ»Đ°Ń‚ŃŚ ĐżĐ°ĐşĐµŃ‚ ĐżĐµŃ€ĐµĐ´ Ride Đ´Đ»ŃŹ
									// ĐşĐľŃ€Ń€ĐµĐşŃ‚Đ˝ĐľĐłĐľ Ń�Đ˝ŃŹŃ‚Đ¸ŃŹ ĐľŃ€Ń�Đ¶Đ¸ŃŹ Ń� Đ·Đ°Ń‚ĐľŃ‡ĐşĐľĐą
		broadcastPacket(new Ride(this));
		broadcastUserInfo(true); // Đ˝Ń�Đ¶Đ˝Đľ ĐżĐľŃ�Đ»Đ°Ń‚ŃŚ ĐżĐ°ĐşĐµŃ‚ ĐżĐľŃ�Đ»Đµ Ride Đ´Đ»ŃŹ
									// ĐşĐľŃ€Ń€ĐµĐşŃ‚Đ˝ĐľĐłĐľ ĐľŃ‚ĐľĐ±Ń€Đ°Đ¶ĐµĐ˝Đ¸ŃŹ Ń�ĐşĐľŃ€ĐľŃ�Ń‚Đ¸

		sendPacket(new SkillList(this));
	}

	public void unEquipWeapon() {
		ItemInstance wpn = getSecondaryWeaponInstance();
		if (wpn != null) {
			sendDisarmMessage(wpn);
			getInventory().unEquipItem(wpn);
		}

		wpn = getActiveWeaponInstance();
		if (wpn != null) {
			sendDisarmMessage(wpn);
			getInventory().unEquipItem(wpn);
		}

		abortAttack(true, true);
		abortCast(true, true);
	}

	/*
	 * @Override
	 * public double getMovementSpeedMultiplier()
	 * {
	 * int template_speed = _template.baseRunSpd;
	 * if(isMounted())
	 * {
	 * L2PetData petData = PetDataTable.getInstance().getInfo(_mountNpcId,
	 * _mountLevel);
	 * if(petData != null)
	 * template_speed = petData.getSpeed();
	 * }
	 * return getRunSpeed() * 1f / template_speed;
	 * }
	 */

	@Override
	public int getSpeed(int baseSpeed) {
		if (isMounted()) {
			PetData petData = PetDataTable.getInstance().getInfo(_mountNpcId, _mountLevel);
			int speed = 187;
			if (petData != null)
				speed = petData.getSpeed();
			double mod = 1.;
			int level = getLevel();
			if (_mountLevel > level && level - _mountLevel > 10)
				mod = 0.5; // Đ¨Ń‚Ń€Đ°Ń„ Đ˝Đ° Ń€Đ°Đ·Đ˝Đ¸Ń†Ń� Ń�Ń€ĐľĐ˛Đ˝ĐµĐą ĐĽĐµĐ¶Đ´Ń� Đ¸ĐłŃ€ĐľĐşĐľĐĽ Đ¸
							// ĐżĐµŃ‚ĐľĐĽ
			baseSpeed = (int) (mod * speed);
		}
		return super.getSpeed(baseSpeed);
	}

	private int _mountNpcId;
	private int _mountObjId;
	private int _mountLevel;

	public int getMountNpcId() {
		return _mountNpcId;
	}

	public int getMountObjId() {
		return _mountObjId;
	}

	public int getMountLevel() {
		return _mountLevel;
	}

	public void sendDisarmMessage(ItemInstance wpn) {
		if (wpn.getEnchantLevel() > 0) {
			SystemMessage sm = new SystemMessage(SystemMessage.EQUIPMENT_OF__S1_S2_HAS_BEEN_REMOVED);
			sm.addNumber(wpn.getEnchantLevel());
			sm.addItemName(wpn.getItemId());
			sendPacket(sm);
		} else {
			SystemMessage sm = new SystemMessage(SystemMessage.S1__HAS_BEEN_DISARMED);
			sm.addItemName(wpn.getItemId());
			sendPacket(sm);
		}
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ Ń‚Đ¸Đż Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµĐĽĐľĐłĐľ Ń�ĐşĐ»Đ°Đ´Đ°.
	 *
	 * @param type Ń‚Đ¸Đż Ń�ĐşĐ»Đ°Đ´Đ°:<BR>
	 *             <ul>
	 *             <li>WarehouseType.PRIVATE
	 *             <li>WarehouseType.CLAN
	 *             <li>WarehouseType.CASTLE
	 *             </ul>
	 */
	public void setUsingWarehouseType(final WarehouseType type) {
		_usingWHType = type;
	}

	/**
	 * Đ â€™Đ Ń•Đ Â·Đ Đ†ĐˇĐ‚Đ Â°Đˇâ€°Đ Â°Đ ÂµĐˇâ€š Đˇâ€šĐ Ń‘Đ Ń—
	 * Đ Ń‘ĐˇĐ�Đ Ń—Đ Ń•Đ Â»ĐˇĐŠĐ Â·ĐˇŃ“Đ ÂµĐ Ń�Đ Ń•Đ Ń–Đ Ń•
	 * ĐˇĐ�Đ Ń”Đ Â»Đ Â°Đ Ň‘Đ Â°.
	 *
	 * @return null Đ Ń‘Đ Â»Đ Ń‘ Đˇâ€šĐ Ń‘Đ Ń— ĐˇĐ�Đ Ń”Đ Â»Đ Â°Đ Ň‘Đ Â°:<br>
	 *         <ul>
	 *         <li>WarehouseType.PRIVATE
	 *         <li>WarehouseType.CLAN
	 *         <li>WarehouseType.CASTLE
	 *         </ul>
	 */
	public WarehouseType getUsingWarehouseType() {
		return _usingWHType;
	}

	public Collection<EffectCubic> getCubics() {
		return _cubics == null ? Collections.<EffectCubic>emptyList() : _cubics.values();
	}

	public void addCubic(EffectCubic cubic) {
		if (_cubics == null)
			_cubics = new ConcurrentHashMap<Integer, EffectCubic>(3);
		_cubics.put(cubic.getId(), cubic);
	}

	public void removeCubic(int id) {
		if (_cubics != null)
			_cubics.remove(id);
	}

	public EffectCubic getCubic(int id) {
		return _cubics == null ? null : _cubics.get(id);
	}

	@Override
	public String toString() {
		return getName() + "[" + getObjectId() + "]";
	}

	/**
	 * @return the modifier corresponding to the Enchant Effect of the Active Weapon
	 *         (Min : 127).<BR>
	 *         <BR>
	 */
	public int getEnchantEffect() {
		final ItemInstance wpn = getActiveWeaponInstance();

		if (wpn == null)
			return 0;

		return Math.min(127, wpn.getEnchantLevel());
	}

	/**
	 * Set the _lastFolkNpc of the L2Player corresponding to the last Folk witch one
	 * the player talked.<BR>
	 * <BR>
	 */
	public void setLastNpc(final NpcInstance npc) {
		if (npc == null)
			_lastNpc = HardReferences.emptyRef();
		else
			_lastNpc = npc.getRef();
	}

	/**
	 * @return the _lastFolkNpc of the L2Player corresponding to the last Folk witch
	 *         one the player talked.<BR>
	 *         <BR>
	 */
	public NpcInstance getLastNpc() {
		return _lastNpc.get();
	}

	public void setMultisell(MultiSellListContainer multisell) {
		_multisell = multisell;
	}

	public MultiSellListContainer getMultisell() {
		return _multisell;
	}

	/**
	 * @return True if L2Player is a participant in the Festival of Darkness.<BR>
	 *         <BR>
	 */
	public boolean isFestivalParticipant() {
		return getReflection() instanceof DarknessFestival;
	}

	@Override
	public boolean unChargeShots(boolean spirit) {
		ItemInstance weapon = getActiveWeaponInstance();
		if (weapon == null)
			return false;

		if (spirit)
			weapon.setChargedSpiritshot(ItemInstance.CHARGED_NONE);
		else
			weapon.setChargedSoulshot(ItemInstance.CHARGED_NONE);

		autoShot();
		return true;
	}

	public boolean unChargeFishShot() {
		ItemInstance weapon = getActiveWeaponInstance();
		if (weapon == null)
			return false;
		weapon.setChargedFishshot(false);
		autoShot();
		return true;
	}

	public void autoShot() {
		for (Integer shotId : _activeSoulShots) {
			ItemInstance item = getInventory().getItemByItemId(shotId);
			if (item == null) {
				removeAutoSoulShot(shotId);
				continue;
			}
			IItemHandler handler = item.getTemplate().getHandler();
			if (handler == null)
				continue;
			handler.useItem(this, item, false);
		}
	}

	public boolean getChargedFishShot() {
		ItemInstance weapon = getActiveWeaponInstance();
		return weapon != null && weapon.getChargedFishshot();
	}

	@Override
	public boolean getChargedSoulShot() {
		ItemInstance weapon = getActiveWeaponInstance();
		return weapon != null && weapon.getChargedSoulshot() == ItemInstance.CHARGED_SOULSHOT;
	}

	@Override
	public int getChargedSpiritShot() {
		ItemInstance weapon = getActiveWeaponInstance();
		if (weapon == null)
			return 0;
		return weapon.getChargedSpiritshot();
	}

	public void addAutoSoulShot(Integer itemId) {
		_activeSoulShots.add(itemId);
	}

	public void removeAutoSoulShot(Integer itemId) {
		_activeSoulShots.remove(itemId);
	}

	public Set<Integer> getAutoSoulShot() {
		return _activeSoulShots;
	}

	public void setInvisibleType(InvisibleType vis) {
		_invisibleType = vis;
	}

	@Override
	public InvisibleType getInvisibleType() {
		return _invisibleType;
	}

	public int getClanPrivileges() {
		if (_clan == null)
			return 0;
		if (isClanLeader())
			return Clan.CP_ALL;
		if (_powerGrade < 1 || _powerGrade > 9)
			return 0;
		RankPrivs privs = _clan.getRankPrivs(_powerGrade);
		if (privs != null)
			return privs.getPrivs();
		return 0;
	}

	public void teleToClosestTown() {
		teleToLocation(
				TeleportUtils.getRestartLocation(this, RestartType.TO_VILLAGE),
				ReflectionManager.DEFAULT);
	}

	public void teleToCastle() {
		teleToLocation(TeleportUtils.getRestartLocation(this, RestartType.TO_CASTLE), ReflectionManager.DEFAULT);
	}

	public void teleToFortress() {
		teleToLocation(TeleportUtils.getRestartLocation(this, RestartType.TO_FORTRESS), ReflectionManager.DEFAULT);
	}

	public void teleToClanhall() {
		teleToLocation(TeleportUtils.getRestartLocation(this, RestartType.TO_CLANHALL), ReflectionManager.DEFAULT);
	}

	@Override
	public void sendMessage(CustomMessage message) {
		sendMessage(message.toString());
	}

	@Override
	public void teleToLocation(int x, int y, int z, int refId) {
		if (isDeleted())
			return;

		super.teleToLocation(x, y, z, refId);
	}

	@Override
	public boolean onTeleported() {
		if (!super.onTeleported())
			return false;

		if (isFakeDeath())
			breakFakeDeath();

		if (isInBoat())
			setLoc(getBoat().getLoc());

		// 15 Ń�ĐµĐşŃ�Đ˝Đ´ ĐżĐľŃ�Đ»Đµ Ń‚ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ° Đ˝Đ° ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶Đ° Đ˝Đµ
		// Đ°ĐłŃ€ŃŹŃ‚Ń�ŃŹ ĐĽĐľĐ±Ń‹
		setNonAggroTime(System.currentTimeMillis() + Config.NONAGGRO_TIME_ONTELEPORT);

		spawnMe();

		setLastClientPosition(getLoc());
		setLastServerPosition(getLoc());

		if (isPendingRevive())
			doRevive();

		sendActionFailed();

		getAI().notifyEvent(CtrlEvent.EVT_TELEPORTED);

		if (isLockedTarget() && getTarget() != null)
			sendPacket(new MyTargetSelected(getTarget().getObjectId(), 0));

		sendUserInfo(true);
		if (getPet() != null)
			getPet().teleportToOwner();

		return true;
	}

	public boolean enterObserverMode(Location loc) {
		WorldRegion observerRegion = World.getRegion(loc);
		if (observerRegion == null)
			return false;
		if (!_observerMode.compareAndSet(OBSERVER_NONE, OBSERVER_STARTING))
			return false;

		setTarget(null);
		stopMove();
		sitDown(null);
		setFlying(true);

		// ĐžŃ‡Đ¸Ń‰Đ°ĐµĐĽ Đ˛Ń�Đµ Đ˛Đ¸Đ´Đ¸ĐĽŃ‹Đµ ĐľĐ±ŃŚĐµĐşŃ‚Ń‹
		World.removeObjectsFromPlayer(this);

		setObserverRegion(observerRegion);

		// ĐžŃ‚ĐľĐ±Ń€Đ°Đ¶Đ°ĐµĐĽ Đ˝Đ°Đ´ĐżĐ¸Ń�ŃŚ Đ˝Đ°Đ´ ĐłĐľĐ»ĐľĐ˛ĐľĐą
		broadcastCharInfo();

		// ĐźĐµŃ€ĐµŃ…ĐľĐ´Đ¸ĐĽ Đ˛ Ń€ĐµĐ¶Đ¸ĐĽ ĐľĐ±Ń�ĐµŃ€Đ˛Đ¸Đ˝ĐłĐ°
		sendPacket(new ObserverStart(loc));

		return true;
	}

	public void appearObserverMode() {
		if (!_observerMode.compareAndSet(OBSERVER_STARTING, OBSERVER_STARTED))
			return;

		WorldRegion currentRegion = getCurrentRegion();
		WorldRegion observerRegion = getObserverRegion();

		// Đ”ĐľĐ±Đ°Đ˛Đ»ŃŹĐµĐĽ Ń„ŃŤĐąĐş Đ˛ Ń‚ĐľŃ‡ĐşŃ� Đ˝Đ°Đ±Đ»ŃŽĐ´ĐµĐ˝Đ¸ŃŹ
		if (!observerRegion.equals(currentRegion))
			observerRegion.addObject(this);

		World.showObjectsToPlayer(this);

		OlympiadGame game = getOlympiadObserveGame();
		if (game != null) {
			game.addSpectator(this);
			game.broadcastInfo(null, this, true);
		}
	}

	public void leaveObserverMode() {
		if (!_observerMode.compareAndSet(OBSERVER_STARTED, OBSERVER_LEAVING))
			return;

		WorldRegion currentRegion = getCurrentRegion();
		WorldRegion observerRegion = getObserverRegion();

		// ĐŁĐ±Đ¸Ń€Đ°ĐµĐĽ Ń„ŃŤĐąĐş Đ˛ Ń‚ĐľŃ‡ĐşĐµ Đ˝Đ°Đ±Đ»ŃŽĐ´ĐµĐ˝Đ¸ŃŹ
		if (!observerRegion.equals(currentRegion))
			observerRegion.removeObject(this);

		// ĐžŃ‡Đ¸Ń‰Đ°ĐµĐĽ Đ˛Ń�Đµ Đ˛Đ¸Đ´Đ¸ĐĽŃ‹Đµ ĐľĐ±ŃŚĐµĐşŃ‚Ń‹
		World.removeObjectsFromPlayer(this);

		setObserverRegion(null);

		setTarget(null);
		stopMove();

		// Đ’Ń‹Ń…ĐľĐ´Đ¸ĐĽ Đ¸Đ· Ń€ĐµĐ¶Đ¸ĐĽĐ° ĐľĐ±Ń�ĐµŃ€Đ˛Đ¸Đ˝ĐłĐ°
		sendPacket(new ObserverEnd(getLoc()));
	}

	public void returnFromObserverMode() {
		if (!_observerMode.compareAndSet(OBSERVER_LEAVING, OBSERVER_NONE))
			return;

		// ĐťŃ�Đ¶Đ˝Đľ ĐżŃ€Đ¸ Ń‚ĐµĐ»ĐµĐżĐľŃ€Ń‚Đµ Ń� Đ±ĐľĐ»ĐµĐµ Đ˛Ń‹Ń�ĐľĐşĐľĐą Ń‚ĐľŃ‡ĐşĐ¸
		// Đ˝Đ° Đ±ĐľĐ»ĐµĐµ Đ˝Đ¸Đ·ĐşŃ�ŃŽ, Đ¸Đ˝Đ°Ń‡Đµ Đ˝Đ°Đ˝ĐľŃ�Đ¸Ń‚Ń�ŃŹ Đ˛Ń€ĐµĐ´ ĐľŃ‚
		// "ĐżĐ°Đ´ĐµĐ˝Đ¸ŃŹ"
		setLastClientPosition(null);
		setLastServerPosition(null);

		unblock();
		standUp();
		setFlying(false);

		broadcastCharInfo();

		World.showObjectsToPlayer(this);
	}

	public void enterOlympiadObserverMode(Location loc, OlympiadGame game, Reflection reflect) {
		WorldRegion observerRegion = World.getRegion(loc);
		if (observerRegion == null)
			return;

		OlympiadGame oldGame = getOlympiadObserveGame();
		if (!_observerMode.compareAndSet(oldGame != null ? OBSERVER_STARTED : OBSERVER_NONE, OBSERVER_STARTING))
			return;

		setTarget(null);
		stopMove();

		// ĐžŃ‡Đ¸Ń‰Đ°ĐµĐĽ Đ˛Ń�Đµ Đ˛Đ¸Đ´Đ¸ĐĽŃ‹Đµ ĐľĐ±ŃŚĐµĐşŃ‚Ń‹
		World.removeObjectsFromPlayer(this);
		setObserverRegion(observerRegion);

		if (oldGame != null) {
			oldGame.removeSpectator(this);
			sendPacket(ExOlympiadMatchEnd.STATIC);
		} else {
			block();

			// ĐžŃ‚ĐľĐ±Ń€Đ°Đ¶Đ°ĐµĐĽ Đ˝Đ°Đ´ĐżĐ¸Ń�ŃŚ Đ˝Đ°Đ´ ĐłĐľĐ»ĐľĐ˛ĐľĐą
			broadcastCharInfo();

			// ĐśĐµĐ˝ŃŹĐµĐĽ Đ¸Đ˝Ń‚ĐµŃ€Ń„ĐµĐąŃ�
			sendPacket(new ExOlympiadMode(3));
		}

		setOlympiadObserveGame(game);

		// "Đ˘ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ¸Ń€Ń�ĐµĐĽŃ�ŃŹ"
		setReflection(reflect);
		sendPacket(new TeleportToLocation(this, loc));
	}

	public void leaveOlympiadObserverMode(boolean removeFromGame) {
		OlympiadGame game = getOlympiadObserveGame();
		if (game == null)
			return;
		if (!_observerMode.compareAndSet(OBSERVER_STARTED, OBSERVER_LEAVING))
			return;

		if (removeFromGame)
			game.removeSpectator(this);
		setOlympiadObserveGame(null);

		WorldRegion currentRegion = getCurrentRegion();
		WorldRegion observerRegion = getObserverRegion();

		// ĐŁĐ±Đ¸Ń€Đ°ĐµĐĽ Ń„ŃŤĐąĐş Đ˛ Ń‚ĐľŃ‡ĐşĐµ Đ˝Đ°Đ±Đ»ŃŽĐ´ĐµĐ˝Đ¸ŃŹ
		if (observerRegion != null && currentRegion != null && !observerRegion.equals(currentRegion))
			observerRegion.removeObject(this);

		// ĐžŃ‡Đ¸Ń‰Đ°ĐµĐĽ Đ˛Ń�Đµ Đ˛Đ¸Đ´Đ¸ĐĽŃ‹Đµ ĐľĐ±ŃŚĐµĐşŃ‚Ń‹
		World.removeObjectsFromPlayer(this);

		setObserverRegion(null);

		setTarget(null);
		stopMove();

		// ĐśĐµĐ˝ŃŹĐµĐĽ Đ¸Đ˝Ń‚ĐµŃ€Ń„ĐµĐąŃ�
		sendPacket(new ExOlympiadMode(0));
		sendPacket(ExOlympiadMatchEnd.STATIC);

		setReflection(ReflectionManager.DEFAULT);
		// "Đ˘ĐµĐ»ĐµĐżĐľŃ€Ń‚Đ¸Ń€Ń�ĐµĐĽŃ�ŃŹ"
		sendPacket(new TeleportToLocation(this, getLoc()));
	}

	public void setOlympiadSide(final int i) {
		_olympiadSide = i;
	}

	public int getOlympiadSide() {
		return _olympiadSide;
	}

	@Override
	public boolean isInObserverMode() {
		return _observerMode.get() > 0;
	}

	public int getObserverMode() {
		return _observerMode.get();
	}

	public WorldRegion getObserverRegion() {
		return _observerRegion;
	}

	public void setObserverRegion(WorldRegion region) {
		_observerRegion = region;
	}

	public int getTeleMode() {
		return _telemode;
	}

	public void setTeleMode(final int mode) {
		_telemode = mode;
	}

	public void setLoto(final int i, final int val) {
		_loto[i] = val;
	}

	public int getLoto(final int i) {
		return _loto[i];
	}

	public void setRace(final int i, final int val) {
		_race[i] = val;
	}

	public int getRace(final int i) {
		return _race[i];
	}

	public boolean getMessageRefusal() {
		return _messageRefusal;
	}

	public void setMessageRefusal(final boolean mode) {
		_messageRefusal = mode;
	}

	public void setTradeRefusal(final boolean mode) {
		_tradeRefusal = mode;
	}

	public boolean getTradeRefusal() {
		return _tradeRefusal;
	}

	public void addToBlockList(final String charName) {
		if (charName == null || charName.equalsIgnoreCase(getName()) || isInBlockList(charName)) {
			// Ń�Đ¶Đµ Đ˛ Ń�ĐżĐ¸Ń�ĐşĐµ
			sendPacket(Msg.YOU_HAVE_FAILED_TO_REGISTER_THE_USER_TO_YOUR_IGNORE_LIST);
			return;
		}

		Player block_target = World.getPlayer(charName);

		if (block_target != null) {
			if (block_target.isGM()) {
				sendPacket(Msg.YOU_MAY_NOT_IMPOSE_A_BLOCK_ON_A_GM);
				return;
			}
			_blockList.put(block_target.getObjectId(), block_target.getName());
			sendPacket(new SystemMessage(SystemMessage.S1_HAS_BEEN_ADDED_TO_YOUR_IGNORE_LIST)
					.addString(block_target.getName()));
			block_target.sendPacket(
					new SystemMessage(SystemMessage.S1__HAS_PLACED_YOU_ON_HIS_HER_IGNORE_LIST).addString(getName()));
			return;
		}

		int charId = CharacterDAO.getInstance().getObjectIdByName(charName);

		if (charId == 0) {
			// Ń‡Đ°Ń€ Đ˝Đµ Ń�Ń�Ń‰ĐµŃ�Ń‚Đ˛Ń�ĐµŃ‚
			sendPacket(Msg.YOU_HAVE_FAILED_TO_REGISTER_THE_USER_TO_YOUR_IGNORE_LIST);
			return;
		}

		if (Config.gmlist.containsKey(charId) && Config.gmlist.get(charId).IsGM) {
			sendPacket(Msg.YOU_MAY_NOT_IMPOSE_A_BLOCK_ON_A_GM);
			return;
		}
		_blockList.put(charId, charName);
		sendPacket(new SystemMessage(SystemMessage.S1_HAS_BEEN_ADDED_TO_YOUR_IGNORE_LIST).addString(charName));
	}

	public void removeFromBlockList(final String charName) {
		int charId = 0;
		for (int blockId : _blockList.keySet())
			if (charName.equalsIgnoreCase(_blockList.get(blockId))) {
				charId = blockId;
				break;
			}
		if (charId == 0) {
			sendPacket(Msg.YOU_HAVE_FAILED_TO_DELETE_THE_CHARACTER_FROM_IGNORE_LIST);
			return;
		}
		sendPacket(new SystemMessage(SystemMessage.S1_HAS_BEEN_REMOVED_FROM_YOUR_IGNORE_LIST)
				.addString(_blockList.remove(charId)));
		Player block_target = GameObjectsStorage.getPlayer(charId);
		if (block_target != null)
			block_target.sendMessage(getName() + " has removed you from his/her Ignore List."); // Đ’
																								// Ń�Đ¸Ń�Ń‚ĐµĐĽĐ˝Ń‹Ń…(619
																								// == 620)
																								// ĐĽĐµŃ�Ń�Đ°ĐłĐ°Ń…
																								// ĐľŃ�Đ¸Đ±ĐşĐ° ;)
	}

	public boolean isInBlockList(final Player player) {
		return isInBlockList(player.getObjectId());
	}

	public boolean isInBlockList(final int charId) {
		return _blockList != null && _blockList.containsKey(charId);
	}

	public boolean isInBlockList(final String charName) {
		for (int blockId : _blockList.keySet())
			if (charName.equalsIgnoreCase(_blockList.get(blockId)))
				return true;
		return false;
	}

	private void restoreBlockList() {
		_blockList.clear();

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"SELECT target_Id, char_name FROM character_blocklist LEFT JOIN characters ON ( character_blocklist.target_Id = characters.obj_Id ) WHERE character_blocklist.obj_Id = ?");
			statement.setInt(1, getObjectId());
			rs = statement.executeQuery();
			while (rs.next()) {
				int targetId = rs.getInt("target_Id");
				String name = rs.getString("char_name");
				if (name == null)
					continue;
				_blockList.put(targetId, name);
			}
		} catch (SQLException e) {
			_log.warn("Can't restore player blocklist " + e, e);
		} finally {
			DbUtils.closeQuietly(con, statement, rs);
		}
	}

	private void storeBlockList() {
		Connection con = null;
		Statement statement = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.createStatement();
			statement.executeUpdate("DELETE FROM character_blocklist WHERE obj_Id=" + getObjectId());

			if (_blockList.isEmpty())
				return;

			SqlBatch b = new SqlBatch("INSERT IGNORE INTO `character_blocklist` (`obj_Id`,`target_Id`) VALUES");

			synchronized (_blockList) {
				StringBuilder sb;
				for (Entry<Integer, String> e : _blockList.entrySet()) {
					sb = new StringBuilder("(");
					sb.append(getObjectId()).append(",");
					sb.append(e.getKey()).append(")");
					b.write(sb.toString());
				}
			}
			if (!b.isEmpty())
				statement.executeUpdate(b.close());
		} catch (Exception e) {
			_log.warn("Can't store player blocklist " + e);
		} finally {
			DbUtils.closeQuietly(con, statement);
		}
	}

	public boolean isBlockAll() {
		return _blockAll;
	}

	public void setBlockAll(final boolean state) {
		_blockAll = state;
	}

	public Collection<String> getBlockList() {
		return _blockList.values();
	}

	public Map<Integer, String> getBlockListMap() {
		return _blockList;
	}

	public void setTournamentMatch(TournamentMatch match) {
		_tournamentMatch = match;
	}

	public TournamentMatch getTournamentMatch() {
		return _tournamentMatch;
	}

	public void setTournamentPoints(int points) {
		_tournamentPoints = points;
	}

	public int getTournamentPoints() {
		return _tournamentPoints;
	}

	public void setHero(final boolean hero) {
		_hero = hero;
	}

	@Override
	public boolean isHero() {
		return _hero;
	}

	public void setIsInOlympiadMode(final boolean b) {
		_inOlympiadMode = b;
	}

	@Override
	public boolean isInOlympiadMode() {
		return _inOlympiadMode;
	}

	public boolean isOlympiadGameStart() {
		return _olympiadGame != null && _olympiadGame.getState() == 1;
	}

	public boolean isOlympiadCompStart() {
		return _olympiadGame != null && _olympiadGame.getState() == 2;
	}

	public void updateNobleSkills() {
		if (isNoble()) {
			if (isClanLeader() && getClan().getCastle() > 0)
				super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_WYVERN_AEGIS, 1));
			super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_NOBLESSE_BLESSING, 1));
			super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_SUMMON_CP_POTION, 1));
			super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_FORTUNE_OF_NOBLESSE, 1));
			super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_HARMONY_OF_NOBLESSE, 1));
			super.addSkill(SkillTable.getInstance().getInfo(Skill.SKILL_SYMPHONY_OF_NOBLESSE, 1));
		} else {
			super.removeSkillById(Skill.SKILL_WYVERN_AEGIS);
			super.removeSkillById(Skill.SKILL_NOBLESSE_BLESSING);
			super.removeSkillById(Skill.SKILL_SUMMON_CP_POTION);
			super.removeSkillById(Skill.SKILL_FORTUNE_OF_NOBLESSE);
			super.removeSkillById(Skill.SKILL_HARMONY_OF_NOBLESSE);
			super.removeSkillById(Skill.SKILL_SYMPHONY_OF_NOBLESSE);
		}
	}

	public void setNoble(boolean noble) {
		if (noble) // broadcast skill animation: Presentation - Attain Noblesse
			broadcastPacket(new MagicSkillUse(this, this, 6673, 1, 1000, 0));
		_noble = noble;
	}

	public boolean isNoble() {
		return _noble;
	}

	/* varka silenos and ketra orc quests related functions */
	public void updateKetraVarka() {
		if (ItemFunctions.getItemCount(this, 7215) > 0)
			_ketra = 5;
		else if (ItemFunctions.getItemCount(this, 7214) > 0)
			_ketra = 4;
		else if (ItemFunctions.getItemCount(this, 7213) > 0)
			_ketra = 3;
		else if (ItemFunctions.getItemCount(this, 7212) > 0)
			_ketra = 2;
		else if (ItemFunctions.getItemCount(this, 7211) > 0)
			_ketra = 1;
		else if (ItemFunctions.getItemCount(this, 7225) > 0)
			_varka = 5;
		else if (ItemFunctions.getItemCount(this, 7224) > 0)
			_varka = 4;
		else if (ItemFunctions.getItemCount(this, 7223) > 0)
			_varka = 3;
		else if (ItemFunctions.getItemCount(this, 7222) > 0)
			_varka = 2;
		else if (ItemFunctions.getItemCount(this, 7221) > 0)
			_varka = 1;
		else {
			_varka = 0;
			_ketra = 0;
		}
	}

	public int getVarka() {
		return _varka;
	}

	public int getKetra() {
		return _ketra;
	}

	public void updateRam() {
		if (ItemFunctions.getItemCount(this, 7247) > 0)
			_ram = 2;
		else if (ItemFunctions.getItemCount(this, 7246) > 0)
			_ram = 1;
		else
			_ram = 0;
	}

	public int getRam() {
		return _ram;
	}

	public void setPledgeType(final int typeId) {
		_pledgeType = typeId;
	}

	public int getPledgeType() {
		return _pledgeType;
	}

	public void setLvlJoinedAcademy(int lvl) {
		_lvlJoinedAcademy = lvl;
	}

	public int getLvlJoinedAcademy() {
		return _lvlJoinedAcademy;
	}

	public int getPledgeClass() {
		return _pledgeClass;
	}

	public void updatePledgeClass() {
		int CLAN_LEVEL = _clan == null ? -1 : _clan.getLevel();
		boolean IN_ACADEMY = _clan != null && Clan.isAcademy(_pledgeType);
		boolean IS_GUARD = _clan != null && Clan.isRoyalGuard(_pledgeType);
		boolean IS_KNIGHT = _clan != null && Clan.isOrderOfKnights(_pledgeType);

		boolean IS_GUARD_CAPTAIN = false, IS_KNIGHT_COMMANDER = false, IS_LEADER = false;

		SubUnit unit = getSubUnit();
		if (unit != null) {
			UnitMember unitMember = unit.getUnitMember(getObjectId());
			if (unitMember == null) {
				_log.warn("Player: unitMember null, clan: " + _clan.getClanId() + "; pledgeType: " + unit.getType());
				return;
			}
			IS_GUARD_CAPTAIN = Clan.isRoyalGuard(unitMember.getLeaderOf());
			IS_KNIGHT_COMMANDER = Clan.isOrderOfKnights(unitMember.getLeaderOf());
			IS_LEADER = unitMember.getLeaderOf() == Clan.SUBUNIT_MAIN_CLAN;
		}

		switch (CLAN_LEVEL) {
			case -1:
				_pledgeClass = RANK_VAGABOND;
				break;
			case 0:
			case 1:
			case 2:
			case 3:
				if (IS_LEADER)
					_pledgeClass = RANK_HEIR;
				else
					_pledgeClass = RANK_VASSAL;
				break;
			case 4:
				if (IS_LEADER)
					_pledgeClass = RANK_KNIGHT;
				else
					_pledgeClass = RANK_HEIR;
				break;
			case 5:
				if (IS_LEADER)
					_pledgeClass = RANK_WISEMAN;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else
					_pledgeClass = RANK_HEIR;
				break;
			case 6:
				if (IS_LEADER)
					_pledgeClass = RANK_BARON;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_WISEMAN;
				else if (IS_GUARD)
					_pledgeClass = RANK_HEIR;
				else
					_pledgeClass = RANK_KNIGHT;
				break;
			case 7:
				if (IS_LEADER)
					_pledgeClass = RANK_COUNT;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_VISCOUNT;
				else if (IS_GUARD)
					_pledgeClass = RANK_KNIGHT;
				else if (IS_KNIGHT_COMMANDER)
					_pledgeClass = RANK_BARON;
				else if (IS_KNIGHT)
					_pledgeClass = RANK_HEIR;
				else
					_pledgeClass = RANK_WISEMAN;
				break;
			case 8:
				if (IS_LEADER)
					_pledgeClass = RANK_MARQUIS;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_COUNT;
				else if (IS_GUARD)
					_pledgeClass = RANK_WISEMAN;
				else if (IS_KNIGHT_COMMANDER)
					_pledgeClass = RANK_VISCOUNT;
				else if (IS_KNIGHT)
					_pledgeClass = RANK_KNIGHT;
				else
					_pledgeClass = RANK_BARON;
				break;
			case 9:
				if (IS_LEADER)
					_pledgeClass = RANK_DUKE;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_MARQUIS;
				else if (IS_GUARD)
					_pledgeClass = RANK_BARON;
				else if (IS_KNIGHT_COMMANDER)
					_pledgeClass = RANK_COUNT;
				else if (IS_KNIGHT)
					_pledgeClass = RANK_WISEMAN;
				else
					_pledgeClass = RANK_VISCOUNT;
				break;
			case 10:
				if (IS_LEADER)
					_pledgeClass = RANK_GRAND_DUKE;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD)
					_pledgeClass = RANK_VISCOUNT;
				else if (IS_KNIGHT)
					_pledgeClass = RANK_BARON;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_DUKE;
				else if (IS_KNIGHT_COMMANDER)
					_pledgeClass = RANK_MARQUIS;
				else
					_pledgeClass = RANK_COUNT;
				break;
			case 11:
				if (IS_LEADER)
					_pledgeClass = RANK_DISTINGUISHED_KING;
				else if (IN_ACADEMY)
					_pledgeClass = RANK_VASSAL;
				else if (IS_GUARD)
					_pledgeClass = RANK_COUNT;
				else if (IS_KNIGHT)
					_pledgeClass = RANK_VISCOUNT;
				else if (IS_GUARD_CAPTAIN)
					_pledgeClass = RANK_GRAND_DUKE;
				else if (IS_KNIGHT_COMMANDER)
					_pledgeClass = RANK_DUKE;
				else
					_pledgeClass = RANK_MARQUIS;
				break;
		}

		if (_hero && _pledgeClass < RANK_MARQUIS)
			_pledgeClass = RANK_MARQUIS;
		else if (_noble && _pledgeClass < RANK_BARON)
			_pledgeClass = RANK_BARON;
	}

	public void setPowerGrade(final int grade) {
		_powerGrade = grade;
	}

	public int getPowerGrade() {
		return _powerGrade;
	}

	public void setApprentice(final int apprentice) {
		_apprentice = apprentice;
	}

	public int getApprentice() {
		return _apprentice;
	}

	public int getSponsor() {
		return _clan == null ? 0 : _clan.getAnyMember(getObjectId()).getSponsor();
	}

	public int getNameColor() {
		if (isInObserverMode())
			return Color.black.getRGB();

		return _nameColor;
	}

	public void setNameColor(final int nameColor) {
		if (nameColor != Config.NORMAL_NAME_COLOUR && nameColor != Config.CLANLEADER_NAME_COLOUR
				&& nameColor != Config.GM_NAME_COLOUR && nameColor != Config.SERVICES_OFFLINE_TRADE_NAME_COLOR)
			setVar("namecolor", Integer.toHexString(nameColor), -1);
		else if (nameColor == Config.NORMAL_NAME_COLOUR)
			unsetVar("namecolor");
		_nameColor = nameColor;
	}

	public void setNameColor(final int red, final int green, final int blue) {
		_nameColor = (red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16);
		if (_nameColor != Config.NORMAL_NAME_COLOUR && _nameColor != Config.CLANLEADER_NAME_COLOUR
				&& _nameColor != Config.GM_NAME_COLOUR && _nameColor != Config.SERVICES_OFFLINE_TRADE_NAME_COLOR)
			setVar("namecolor", Integer.toHexString(_nameColor), -1);
		else
			unsetVar("namecolor");
	}

	private final Map<String, String> user_variables = new ConcurrentHashMap<String, String>();
	private List<TaskType> _activeTasks = new Vector<>();

	public void setVar(String name, String value, long expirationTime) {
		if (value == null) {
			// If the value is null, we should remove the variable instead of adding it.
			unsetVar(name);
			return;
		}
		user_variables.put(name, value);
		mysql.set(
				"REPLACE INTO character_variables (obj_id, type, name, value, expire_time) VALUES (?,?,?, ?,?)",
				getObjectId(), "user-var", name, value, expirationTime);
	}

	public void setVar(String name, int value, long expirationTime) {
		setVar(name, String.valueOf(value), expirationTime);
	}

	public void setVar(String name, long value, long expirationTime) {
		setVar(name, String.valueOf(value), expirationTime);
	}

	// ----------------------------------------------------------------------
	// ADD THIS NEW OVERLOADED METHOD IN Player.java
	// so that setVar("IgnoreDelevelStore", "1") will compile
	// ----------------------------------------------------------------------
	public void setVar(String name, String value) {
		// Default to "-1" for expirationTime, meaning "no expiration"
		setVar(name, value, -1L);
	}

	public void unsetVar(String name) {
		if (name == null)
			return;

		if (user_variables.remove(name) != null)
			mysql.set("DELETE FROM `character_variables` WHERE `obj_id`=? AND `type`='user-var' AND `name`=? LIMIT 1",
					getObjectId(), name);
	}

	public String getVar(String name) {
		return user_variables.get(name);
	}

	public boolean getVarB(String name, boolean defaultVal) {
		String var = user_variables.get(name);
		if (var == null)
			return defaultVal;
		return !(var.equals("0") || var.equalsIgnoreCase("false"));
	}

	public boolean getVarB(String name) {
		String var = user_variables.get(name);
		return !(var == null || var.equals("0") || var.equalsIgnoreCase("false"));
	}

	public long getVarLong(String name) {
		return getVarLong(name, 0L);
	}

	public long getVarLong(String name, long defaultVal) {
		long result = defaultVal;
		String var = getVar(name);
		if (var != null)
			result = Long.parseLong(var);
		return result;
	}

	public int getVarInt(String name) {
		return getVarInt(name, 0);
	}

	public int getVarInt(String name, int defaultVal) {
		int result = defaultVal;
		String var = getVar(name);
		if (var != null)
			result = Integer.parseInt(var);
		return result;
	}

	public Map<String, String> getVars() {
		return user_variables;
	}

	private void loadVariables() {
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT * FROM character_variables WHERE obj_id = ?");
			offline.setInt(1, getObjectId());
			rs = offline.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				String value = Strings.stripSlashes(rs.getString("value"));
				user_variables.put(name, value);
				long expireTime = rs.getLong("expire_time");
				if (expireTime > 0) {
					addNewTask(TaskType.valueOf(name), expireTime);
					unsetVar(name);
				}
			}

			if (getVar("lang@") != null)
				unsetVar("lang@");
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, offline, rs);
		}
	}

	public static String getVarFromPlayer(int objId, String var) {
		String value = null;
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT value FROM character_variables WHERE obj_id = ? AND name = ?");
			offline.setInt(1, objId);
			offline.setString(2, var);
			rs = offline.executeQuery();
			if (rs.next())
				value = Strings.stripSlashes(rs.getString("value"));
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, offline, rs);
		}
		return value;
	}

	public boolean taskExists(TaskType type) {
		return _activeTasks.contains(type);
	}

	/**
	 * Adding new Task, starting it and adding to user_variables
	 * 
	 * @param type
	 * @param expireTime - time in milis between current and finish
	 */
	public void addNewTask(TaskType type, long expireTime) {
		if (type == null)
			return;
		PlayerTaskManager.getInstance().addNewTask(this, type, System.currentTimeMillis() + expireTime);
		_activeTasks.add(type);
	}

	public long getTaskExpireTime(TaskType type) {
		return PlayerTaskManager.getInstance().getExpireTime(this, type);
	}

	/**
	 * Removing effect and unsetting from list
	 * 
	 * @param type
	 */
	public void removeWholeTask(TaskType type) {
		PlayerTaskManager.getInstance().finishTask(this, type);
		_activeTasks.remove(type);
	}

	/**
	 * Usetting task from list
	 * 
	 * @param type
	 */
	public void removeTaskFromList(TaskType type) {
		_activeTasks.remove(type);
	}

	public int isAtWarWith(final Integer id) {
		return _clan == null || !_clan.isAtWarWith(id) ? 0 : 1;
	}

	public int isAtWar() {
		return _clan == null || _clan.isAtWarOrUnderAttack() <= 0 ? 0 : 1;
	}

	public void stopWaterTask() {
		if (_taskWater != null) {
			_taskWater.cancel(false);
			_taskWater = null;
			sendPacket(new SetupGauge(this, SetupGauge.CYAN, 0));
			sendChanges();
		}
	}

	public void startWaterTask() {
		if (isDead())
			stopWaterTask();
		else if (Config.ALLOW_WATER && _taskWater == null) {
			int timeinwater = (int) (calcStat(Stats.BREATH, 86, null, null) * 1000L);
			sendPacket(new SetupGauge(this, SetupGauge.CYAN, timeinwater));
			if (getTransformation() > 0 && getTransformationTemplate() > 0 && !isCursedWeaponEquipped())
				setTransformation(0);
			_taskWater = ThreadPoolManager.getInstance().scheduleAtFixedRate(new WaterTask(this), timeinwater, 1000L);
			sendChanges();
		}
	}

	public void doRevive(double percent) {
		restoreExp(percent);
		doRevive();
	}

	@Override
	public void doRevive() {
		super.doRevive();
		setAgathionRes(false);
		unsetVar("lostexp");
		updateEffectIcons();
		autoShot();
	}

	public void reviveRequest(Player reviver, double percent, boolean pet) {
		ReviveAnswerListener reviveAsk = _askDialog != null && _askDialog.getValue() instanceof ReviveAnswerListener
				? (ReviveAnswerListener) _askDialog.getValue()
				: null;
		if (reviveAsk != null) {
			if (reviveAsk.isForPet() == pet && reviveAsk.getPower() >= percent) {
				reviver.sendPacket(Msg.BETTER_RESURRECTION_HAS_BEEN_ALREADY_PROPOSED);
				return;
			}
			if (pet && !reviveAsk.isForPet()) {
				reviver.sendPacket(
						Msg.SINCE_THE_MASTER_WAS_IN_THE_PROCESS_OF_BEING_RESURRECTED_THE_ATTEMPT_TO_RESURRECT_THE_PET_HAS_BEEN_CANCELLED);
				return;
			}
			if (pet && isDead()) {
				reviver.sendPacket(
						Msg.WHILE_A_PET_IS_ATTEMPTING_TO_RESURRECT_IT_CANNOT_HELP_IN_RESURRECTING_ITS_MASTER);
				return;
			}
		}

		if (pet && getPet() != null && getPet().isDead() || !pet && isDead()) {
			ConfirmDlg pkt = new ConfirmDlg(
					SystemMsg.C1_IS_MAKING_AN_ATTEMPT_TO_RESURRECT_YOU_IF_YOU_CHOOSE_THIS_PATH_S2_EXPERIENCE_WILL_BE_RETURNED_FOR_YOU,
					0);
			pkt.addName(reviver).addString(Math.round(percent) + " percent");

			ask(pkt, new ReviveAnswerListener(this, percent, pet));
		}
	}

	public void summonCharacterRequest(final Creature summoner, final Location loc, final int summonConsumeCrystal) {
		ConfirmDlg cd = new ConfirmDlg(SystemMsg.C1_WISHES_TO_SUMMON_YOU_FROM_S2, 60000);
		cd.addName(summoner).addZoneName(loc);

		ask(cd, new SummonAnswerListener(this, loc, summonConsumeCrystal));
	}

	public void scriptRequest(String text, String scriptName, Object[] args) {
		ask(new ConfirmDlg(SystemMsg.S1, 30000).addString(text), new ScriptAnswerListener(this, scriptName, args));
	}

	private void checkRecom() {
		Calendar temp = Calendar.getInstance();
		temp.set(Calendar.HOUR_OF_DAY, 6);
		temp.set(Calendar.MINUTE, 30);
		temp.set(Calendar.SECOND, 0);
		temp.set(Calendar.MILLISECOND, 0);
		long count = Math.round((System.currentTimeMillis() / 1000 - _lastAccess) / 86400);
		if (count == 0 && _lastAccess < temp.getTimeInMillis() / 1000
				&& System.currentTimeMillis() > temp.getTimeInMillis())
			count++;

		for (int i = 1; i < count; i++)
			setRecomHave(getRecomHave() - 20);

		if (count > 0)
			restartRecom();
	}

	public void restartRecom() {
		setRecomBonusTime(3600);
		setRecomLeftToday(0);
		setRecomLeft(20);
		setRecomHave(getRecomHave() - 20);
		stopRecomBonusTask(false);
		startRecomBonusTask();
		sendUserInfo(true);
		sendVoteSystemInfo();
	}

	@Override
	public boolean isInBoat() {
		return _boat != null;
	}

	public Boat getBoat() {
		return _boat;
	}

	public void setBoat(Boat boat) {
		_boat = boat;
	}

	public Location getInBoatPosition() {
		return _inBoatPosition;
	}

	public void setInBoatPosition(Location loc) {
		_inBoatPosition = loc;
	}

	public void setPrimaryClass(int classId) {
		_primaryClassTemplate = classId;
	}

	public int getPrimaryClass() {
		return !getActiveClass().isKamael() ? _primaryClassTemplate : getFirstClassId();
	}

	public void setActiveClass(StackClass clazz) {
		_activeClass = clazz;
	}

	public StackClass getActiveClass() {
		return _activeClass;
	}

	/*
	 * Old code
	 * public boolean isOnMainClass() {
	 * return getActiveClass().isMain();
	 * }
	 */
	public int getSecondaryClassId() {
		return getActiveClass().getSecondaryClass();
	}

	public ClassId getActiveClassClassId() {
		return ClassId.values()[getActiveClass().getFirstClassId()];
	}

	/**
	 * @return if main return 0, else class id
	 */
	public int getFirstClassId() {
		return getActiveClass().getFirstClassId();
	}

	public void setUnlocks(Unlocks newUnlocks) {
		_unlocks = newUnlocks;
	}

	public Unlocks getUnlocks() {
		return _unlocks;
	}

	public void saveUnlocks() {
		if (getActiveClass().isKamael())
			return;
		getUnlocks().changeClassVars(getActiveClass().getFirstClassId(), getActiveClass().getLevel(),
				getActiveClass().getExp());
		getUnlocks().changeClassVars(getActiveClass().getSecondaryClass(), getActiveClass().getSecondaryLevel(),
				getActiveClass().getSecondaryExp());
		getUnlocks().saveClasses();
	}

	/**
	 * @param Base Subclass results set
	 */
	private void setStackClass(ResultSet rset) {
		try {
			setUnlocks(new Unlocks(this));
			StackClass stackClass = _activeClass;

			stackClass.setFirstClassId(rset.getInt("class_id"));
			restoreSkills(stackClass.getFirstClassId());
			stackClass.setFirstExp(rset.getLong("exp"));
			setCurrentHp(rset.getDouble("curHp"), false);
			setCurrentMp(rset.getDouble("curMp"));
			setCurrentCp(rset.getDouble("curCp"));
			stackClass.setDeathPenalty(new DeathPenalty(this, rset.getInt("death_penalty")));
			stackClass.setSecondaryClass(rset.getInt("secondClassId"));
			restoreSkills(getSecondaryClassId());
			stackClass.setExpWithoutSave(rset.getLong("secondExp"));

			setActiveClass(stackClass);
			switchActiveClass(stackClass.getFirstClassId());
			restoreSkills(getActiveClassClassId().getId());
			restoreSkills(getSecondaryClassId());
		} catch (Exception e) {
			_log.error("setstackclass:", e);
		}
	}

	// The 1-parameter version (restored):
	public void storeMainClasses(boolean setActive) {
		// For normal updates, oldMainClassId = current main:
		int oldMainClassId = getActiveClassClassId().getId();
		storeMainClasses(setActive, oldMainClassId);
	}

	/**
	 * The new 2-parameter version: actually rename the row from oldMainClassId to
	 * the newMainClassId.
	 * Called by swapStackClasses(...) or if dev wants a custom oldMainClassId for
	 * some reason.
	 */
	public void storeMainClasses(boolean ignoredSetActive, int oldMainClassId) {
		if (_activeClass == null) {
			_log.warn("[storeMainClasses] Could not store sub data: active class is null for " + getName());
			return;
		}

		long storedExp = _activeClass.getExp();
		int storedLevel = _activeClass.getLevel();

		int newMainClassId = _activeClass.getFirstClassId();
		int secondClassId = _activeClass.getSecondaryClass();
		long secondExp = _activeClass.getSecondaryExp();
		int secondLevel = _activeClass.getSecondaryLevel();

		System.out.println("[storeMainClasses] Called for " + getName()
				+ ". oldMainClassId=" + oldMainClassId
				+ ", newMainClassId=" + newMainClassId
				+ ", secondClassId=" + secondClassId
				+ ", storedLevel=" + storedLevel
				+ ", storedExp=" + storedExp
				+ ", secondLevel=" + secondLevel
				+ ", secondExp=" + secondExp);

		Connection con = null;
		PreparedStatement ps = null;
		PreparedStatement ps2 = null;

		try {
			con = DatabaseFactory.getInstance().getConnection();

			// 1) rename old main class row to newMainClassId in character_subclasses
			String sql1 = "UPDATE character_subclasses "
					+ "SET class_id=?, exp=?, level=?, secondClassId=?, secondExp=?, secondLevel=? "
					+ "WHERE char_obj_id=? AND class_id=?";
			ps = con.prepareStatement(sql1);
			ps.setInt(1, newMainClassId);
			ps.setLong(2, storedExp);
			ps.setInt(3, storedLevel);
			ps.setInt(4, secondClassId);
			ps.setLong(5, secondExp);
			ps.setInt(6, secondLevel);
			ps.setInt(7, getObjectId());
			ps.setInt(8, oldMainClassId);

			int updatedRows = ps.executeUpdate();
			DbUtils.close(ps);
			ps = null;

			System.out.println("[storeMainClasses] Subclass update query: " + sql1);
			System.out.println("[storeMainClasses] Rows updated in character_subclasses=" + updatedRows);

			// 2) update the characters table so char select screen sees the right level
			String sql2 = "UPDATE characters SET level=?, exp=?, sp=? WHERE obj_Id=?";
			ps2 = con.prepareStatement(sql2);
			ps2.setInt(1, storedLevel);
			ps2.setLong(2, storedExp);
			ps2.setLong(3, getSp());
			ps2.setInt(4, getObjectId());

			int updatedChars = ps2.executeUpdate();
			DbUtils.close(ps2);
			ps2 = null;

			System.out.println("[storeMainClasses] " + updatedChars
					+ " row(s) updated in `characters` for level/exp/sp. (sql=" + sql2 + ")");
		} catch (Exception e) {
			_log.warn("[storeMainClasses] Could not store main/secondary class data for " + getName() + ":", e);
		} finally {
			DbUtils.closeQuietly(ps);
			DbUtils.closeQuietly(ps2);
			DbUtils.closeQuietly(con);
		}
	}

	/**
	 * Returning StackClass, if not found then null
	 */
	public static StackClass restoreCharSubClass(final Player player, int classId) {
		StackClass classToReturn = null;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM character_subclasses WHERE char_obj_id=? "
					+ (classId == 0 ? "AND isBase=1" : "AND class_id=" + classId));
			statement.setInt(1, player.getObjectId());
			rset = statement.executeQuery();

			if (rset.next()) {
				classToReturn = new StackClass(player);
				classToReturn.setFirstClassId(rset.getInt("class_id"));
				classToReturn.setFirstExp(rset.getLong("exp"));
				classToReturn.setHp(rset.getDouble("curHp"));
				classToReturn.setMp(rset.getDouble("curMp"));
				classToReturn.setCp(rset.getDouble("curCp"));
				classToReturn.setDeathPenalty(new DeathPenalty(player, rset.getInt("death_penalty")));
				classToReturn.setSecondaryClass(rset.getInt("secondClassId"));
				classToReturn.setExpWithoutSave(rset.getLong("secondExp"));
			}
		} catch (final Exception e) {
			_log.warn("Could not restore char sub-classes: " + e);
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}

		return classToReturn;
	}

	/**
	 * Adding new Subclass and setting it active
	 *
	 * @param storeOld
	 * @param certification
	 */
	public boolean addSubClass(int classId) {
		final ClassId newId = ClassId.VALUES[classId];

		final StackClass newClass = new StackClass(this);

		newClass.setFirstClassId(classId);
		if (classId == 134)
			newClass.setSecondaryClass(133);
		else if (classId == 131)
			newClass.setSecondaryClass(132);
		else if (classId == 133)
			newClass.setSecondaryClass(136);
		newClass.addExp(Experience.getExpForLevel(40));

		Connection con = null;
		PreparedStatement statement = null;
		try {
			// Store the basic info about this new sub-class.
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement(
					"INSERT INTO character_subclasses (char_obj_id, class_id, exp, sp, curHp, curMp, curCp, maxHp, maxMp, maxCp, level, active, isBase, death_penalty, secondClassId, secondLevel, secondExp) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			statement.setInt(1, getObjectId());
			statement.setInt(2, newClass.getFirstClassId());
			statement.setLong(3, Experience.getExpForLevel(40));
			statement.setInt(4, 0);
			statement.setDouble(5, getCurrentHp());
			statement.setDouble(6, getCurrentMp());
			statement.setDouble(7, getCurrentCp());
			statement.setDouble(8, getCurrentHp());
			statement.setDouble(9, getCurrentMp());
			statement.setDouble(10, getCurrentCp());
			statement.setInt(11, 40);
			statement.setInt(12, 0);
			statement.setInt(13, 0);
			statement.setInt(14, 0);
			statement.setInt(15, newClass.getSecondaryClass());
			statement.setInt(16, Experience.getLevel(newClass.getSecondaryExp()));
			statement.setLong(17, newClass.getSecondaryExp());
			statement.execute();
		} catch (final Exception e) {
			_log.warn("Could not add character sub-class: " + e, e);
			return false;
		} finally {
			DbUtils.closeQuietly(con, statement);
		}

		switchActiveClass(classId);

		boolean countUnlearnable = true;
		int unLearnable = 0;

		Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(this, AcquireType.NORMAL);
		while (skills.size() > unLearnable) {
			for (final SkillLearn s : skills) {
				final Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
				if (sk == null || !sk.getCanLearn(newId)) {
					if (countUnlearnable)
						unLearnable++;
					continue;
				}
				addSkill(sk, true);
			}
			countUnlearnable = false;
			skills = SkillAcquireHolder.getInstance().getAvailableSkills(this, AcquireType.NORMAL);
		}

		sendPacket(new SkillList(this));
		setCurrentHpMp(getMaxHp(), getMaxMp(), true);
		setCurrentCp(getMaxCp());
		return true;
	}

	/**
	 * restoring/switching subclass, set classId=0 if to main
	 */
	public void switchActiveClass(int classId) {
		AutoFarmState st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		StackClass sub = restoreCharSubClass(this, classId);
		if (sub == null)
			return;

		boolean subclassChange = false;
		if (getActiveClass() != null && getFirstClassId() != classId) {
			// Save old data
			EffectsDAO.getInstance().insert(this);
			storeDisableSkills();

			// Any custom steps if needed:
			if (QuestManager.getQuest(422) != null) {
				String qn = QuestManager.getQuest(422).getName();
				if (qn != null) {
					QuestState qs = getQuestState(qn);
					if (qs != null)
						qs.exitCurrentQuest(true);
				}
			}

			// Possibly store
			// If "IgnoreDelevelStore=1", we skip storing forcibly-lowered level
			storeMainClasses(true);

			// Remove all old class skills from memory
			removeAllSkills();

			// Optional: impose a brief immobilize or paralyze if you want
			startParalyzed();
			startImmobilized();
		}

		// If the new class is different from old:
		if (getFirstClassId() != classId) {
			setActiveClass(sub);
			setCurrentHpMp(sub.getHp(), sub.getMp());
			setCurrentCp(sub.getCp());
			subclassChange = true;
		}

		// Re-learn skills from both main & secondary
		restoreSkills(getSecondaryClassId());
		restoreSkills(getFirstClassId());

		setClassId(getFirstClassId(), false, false, !subclassChange);

		getEffectList().stopAllEffects();
		if (getPet() != null && (getPet().isSummon() || (Config.ALT_IMPROVED_PETS_LIMITED_USE &&
				((getPet().getNpcId() == PetDataTable.IMPROVED_BABY_KOOKABURRA_ID && !isMageClass()) ||
						(getPet().getNpcId() == PetDataTable.IMPROVED_BABY_BUFFALO_ID && isMageClass())))))
			getPet().unSummon();

		setAgathion(0);

		if (isNoble())
			updateNobleSkills();

		rewardSkills(false);
		checkSkills(); // ensure skill levels match forced-lowered level
		sendPacket(new ExStorageMaxCount(this));

		refreshExpertisePenalty();
		sendPacket(new SkillList(this));

		getInventory().refreshEquip();
		getInventory().validateItems();

		for (int i = 0; i < 3; i++)
			_henna[i] = null;
		restoreHenna();
		sendPacket(new HennaInfo(this));

		// Re-apply effect data to the new class
		EffectsDAO.getInstance().restoreEffects(this);
		restoreDisableSkills();

		// Re-load shortcuts
		_shortCuts.restore();
		sendPacket(new ShortCutInit(this));
		for (int shotId : getAutoSoulShot())
			sendPacket(new ExAutoSoulShot(shotId, true));
		sendPacket(new SkillCoolTime(this));

		broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));
		getDeathPenalty().restore(this);

		setIncreasedForce(0);

		startHourlyTask();
		broadcastCharInfo();
		updateEffectIcons();
		updateStats();

		st = AutoFarmEngine.getInstance().getAutoFarmState(getObjectId());
		if (st != null)
			AutoFarmSkillDAO.getInstance().loadSkillsForStackClass(this, st);
	}

	public void forceChangeLevel(int newLevel, boolean broadcast) {
		if (getActiveClass() != null) {
			getActiveClass().setLevel(newLevel);

			// Set exp to the minimal for that level, so there's no mismatch:
			long neededExp = Experience.LEVEL[Math.min(newLevel, Experience.getMaxLevel())];
			getActiveClass().setFirstExp(neededExp);
		}

		// Re-check skill levels, etc.
		checkSkills();
		updateStats();

		if (broadcast) {
			// Update everyone about our new stats:
			broadcastCharInfo();

			// Show the level-up animation to self and watchers:
			broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));
		}
	}

	/**
	 * Đ§ĐµŃ€ĐµĐ· delay ĐĽĐ¸Đ»Đ»Đ¸Ń�ĐµĐşŃ�Đ˝Đ´ Đ˛Ń‹Đ±Ń€ĐľŃ�Đ¸Ń‚ Đ¸ĐłŃ€ĐľĐşĐ° Đ¸Đ·
	 * Đ¸ĐłŃ€Ń‹
	 */
	public void startKickTask(long delayMillis) {
		stopKickTask();
		_kickTask = ThreadPoolManager.getInstance().schedule(new KickTask(this), delayMillis);
	}

	public void stopKickTask() {
		if (_kickTask != null) {
			_kickTask.cancel(false);
			_kickTask = null;
		}
	}

	public void startBonusTask() {
		if (Config.SERVICES_RATE_TYPE != Bonus.NO_BONUS) {
			int bonusExpire = getNetConnection().getBonusExpire();
			double bonus = getNetConnection().getBonus();
			if (bonusExpire > System.currentTimeMillis() / 1000L) {
				getBonus().setRateXp(bonus);
				getBonus().setRateSp(bonus);
				getBonus().setDropAdena(bonus);
				getBonus().setDropItems(bonus);
				getBonus().setDropSpoil(bonus);

				getBonus().setBonusExpire(bonusExpire);

				if (_bonusExpiration == null)
					_bonusExpiration = LazyPrecisionTaskManager.getInstance().startBonusExpirationTask(this);
			} else if (bonus > 0 && Config.SERVICES_RATE_TYPE == Bonus.BONUS_GLOBAL_ON_GAMESERVER)
				AccountBonusDAO.getInstance().delete(getAccountName());
		}
	}

	public void stopBonusTask() {
		if (_bonusExpiration != null) {
			_bonusExpiration.cancel(false);
			_bonusExpiration = null;
		}
	}

	@Override
	public int getInventoryLimit() {
		return (int) calcStat(Stats.INVENTORY_LIMIT, 0, null, null);
	}

	public int getWarehouseLimit() {
		return (int) calcStat(Stats.STORAGE_LIMIT, 0, null, null);
	}

	public int getTradeLimit() {
		return (int) calcStat(Stats.TRADE_LIMIT, 0, null, null);
	}

	public int getDwarvenRecipeLimit() {
		return (int) calcStat(Stats.DWARVEN_RECIPE_LIMIT, 100, null, null) + Config.ALT_ADD_RECIPES;
	}

	public int getCommonRecipeLimit() {
		return (int) calcStat(Stats.COMMON_RECIPE_LIMIT, 50, null, null) + Config.ALT_ADD_RECIPES;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Ń‚Đ¸Đż Đ°Ń‚Đ°ĐşŃ�ŃŽŃ‰ĐµĐłĐľ ŃŤĐ»ĐµĐĽĐµĐ˝Ń‚Đ°
	 */
	public Element getAttackElement() {
		return Formulas.getAttackElement(this, null);
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Ń�Đ¸Đ»Ń� Đ°Ń‚Đ°ĐşĐ¸ ŃŤĐ»ĐµĐĽĐµĐ˝Ń‚Đ°
	 *
	 * @return Đ·Đ˝Đ°Ń‡ĐµĐ˝Đ¸Đµ Đ°Ń‚Đ°ĐşĐ¸
	 */
	public int getAttack(Element element) {
		if (element == Element.NONE)
			return 0;
		return (int) calcStat(element.getAttack(), 0., null, null);
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Đ·Đ°Ń‰Đ¸Ń‚Ń� ĐľŃ‚ ŃŤĐ»ĐµĐĽĐµĐ˝Ń‚Đ°
	 *
	 * @return Đ·Đ˝Đ°Ń‡ĐµĐ˝Đ¸Đµ Đ·Đ°Ń‰Đ¸Ń‚Ń‹
	 */
	public int getDefence(Element element) {
		if (element == Element.NONE)
			return 0;
		return (int) calcStat(element.getDefence(), 0., null, null);
	}

	public boolean getAndSetLastItemAuctionRequest() {
		if (_lastItemAuctionInfoRequest + 2000L < System.currentTimeMillis()) {
			_lastItemAuctionInfoRequest = System.currentTimeMillis();
			return true;
		} else {
			_lastItemAuctionInfoRequest = System.currentTimeMillis();
			return false;
		}
	}

	@Override
	public int getNpcId() {
		return -2;
	}

	public GameObject getVisibleObject(int id) {
		if (getObjectId() == id)
			return this;

		GameObject target = null;

		if (getTargetId() == id)
			target = getTarget();

		if (target == null && _party != null)
			for (Player p : _party.getPartyMembers())
				if (p != null && p.getObjectId() == id) {
					target = p;
					break;
				}

		if (target == null)
			target = World.getAroundObjectById(this, id);

		return target == null || target.isInvisible() ? null : target;
	}

	@Override
	public int getPAtk(final Creature target) {
		double init = getActiveWeaponInstance() == null ? (isMageClass() ? 3 : 4) : 0;
		return (int) calcStat(Stats.POWER_ATTACK, init, target, null);
	}

	@Override
	public int getPDef(final Creature target) {
		double init = 4.; // empty cloak and underwear slots

		final ItemInstance chest = getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		if (chest == null)
			init += isMageClass() ? ArmorTemplate.EMPTY_BODY_MYSTIC : ArmorTemplate.EMPTY_BODY_FIGHTER;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS) == null
				&& (chest == null || chest.getBodyPart() != ItemTemplate.SLOT_FULL_ARMOR))
			init += isMageClass() ? ArmorTemplate.EMPTY_LEGS_MYSTIC : ArmorTemplate.EMPTY_LEGS_FIGHTER;

		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD) == null)
			init += ArmorTemplate.EMPTY_HELMET;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_GLOVES) == null)
			init += ArmorTemplate.EMPTY_GLOVES;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET) == null)
			init += ArmorTemplate.EMPTY_BOOTS;

		return (int) calcStat(Stats.POWER_DEFENCE, init, target, null);
	}

	@Override
	public int getMDef(final Creature target, final Skill skill) {
		double init = 0.;

		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEAR) == null)
			init += ArmorTemplate.EMPTY_EARRING;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_REAR) == null)
			init += ArmorTemplate.EMPTY_EARRING;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_NECK) == null)
			init += ArmorTemplate.EMPTY_NECKLACE;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LFINGER) == null)
			init += ArmorTemplate.EMPTY_RING;
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_RFINGER) == null)
			init += ArmorTemplate.EMPTY_RING;

		return (int) calcStat(Stats.MAGIC_DEFENCE, init, target, skill);
	}

	@Override
	public String getTitle() {
		return super.getTitle();
	}

	public int getTitleColor() {
		return _titlecolor;
	}

	public void setTitleColor(final int titlecolor) {
		if (titlecolor != DEFAULT_TITLE_COLOR)
			setVar("titlecolor", Integer.toHexString(titlecolor), -1);
		else
			unsetVar("titlecolor");
		_titlecolor = titlecolor;
	}

	@Override
	public boolean isCursedWeaponEquipped() {
		return _cursedWeaponEquippedId != 0;
	}

	public void setCursedWeaponEquippedId(int value) {
		_cursedWeaponEquippedId = value;
	}

	public int getCursedWeaponEquippedId() {
		return _cursedWeaponEquippedId;
	}

	@Override
	public boolean isImmobilized() {
		return super.isImmobilized() || isOverloaded() || isSitting() || isFishing();
	}

	@Override
	public boolean isBlocked() {
		return super.isBlocked() || isInMovie() || isInObserverMode() || isTeleporting() || isLogoutStarted();
	}

	@Override
	public boolean isInvul() {
		return super.isInvul() || isInMovie();
	}

	/**
	 * if True, the L2Player can't take more item
	 */
	public void setOverloaded(boolean overloaded) {
		_overloaded = overloaded;
	}

	public boolean isOverloaded() {
		return _overloaded;
	}

	public boolean isFishing() {
		return _isFishing;
	}

	public Fishing getFishing() {
		return _fishing;
	}

	public void setFishing(boolean value) {
		_isFishing = value;
	}

	public void startFishing(FishTemplate fish, int lureId) {
		_fishing.setFish(fish);
		_fishing.setLureId(lureId);
		_fishing.startFishing();
	}

	public void stopFishing() {
		_fishing.stopFishing();
	}

	public Location getFishLoc() {
		return _fishing.getFishLoc();
	}

	public Bonus getBonus() {
		return _bonus;
	}

	public boolean hasBonus() {
		return _bonus.getBonusExpire() > System.currentTimeMillis() / 1000L;
	}

	@Override
	public double getRateAdena() {
		return _party == null ? _bonus.getDropAdena() : _party._rateAdena;
	}

	@Override
	public double getRateItems() {
		return _party == null ? _bonus.getDropItems() : _party._rateDrop;
	}

	@Override
	public double getRateExp() {
		return calcStat(Stats.EXP, 1., null, null);
	}

	@Override
	public double getRateSp() {
		return calcStat(Stats.SP, (_party == null ? _bonus.getRateSp() : _party._rateSp), null, null);
	}

	@Override
	public double getRateSpoil() {
		return _party == null ? _bonus.getDropSpoil() : _party._rateSpoil;
	}

	private boolean _maried = false;
	private int _partnerId = 0;
	private int _coupleId = 0;
	private boolean _maryrequest = false;
	private boolean _maryaccepted = false;

	public boolean isMaried() {
		return _maried;
	}

	public void setMaried(boolean state) {
		_maried = state;
	}

	public void setMaryRequest(boolean state) {
		_maryrequest = state;
	}

	public boolean isMaryRequest() {
		return _maryrequest;
	}

	public void setMaryAccepted(boolean state) {
		_maryaccepted = state;
	}

	public boolean isMaryAccepted() {
		return _maryaccepted;
	}

	public int getPartnerId() {
		return _partnerId;
	}

	public void setPartnerId(int partnerid) {
		_partnerId = partnerid;
	}

	public int getCoupleId() {
		return _coupleId;
	}

	public void setCoupleId(int coupleId) {
		_coupleId = coupleId;
	}

	public void setUndying(boolean val) {
		if (!isGM())
			return;
		_isUndying = val;
	}

	public boolean isUndying() {
		return _isUndying;
	}

	/**
	 * private List<L2Player> _snoopListener = new ArrayList<L2Player>();
	 * private List<L2Player> _snoopedPlayer = new ArrayList<L2Player>();
	 * <p/>
	 * public void broadcastSnoop(int type, String name, int fStringId, String...
	 * params)
	 * {
	 * if(_snoopListener.size() > 0)
	 * {
	 * Snoop sn = new Snoop(getObjectId(), getName(), type, name, fStringId,
	 * params);
	 * for(L2Player pci : _snoopListener)
	 * if(pci != null)
	 * pci.sendPacket(sn);
	 * }
	 * }
	 * <p/>
	 * public void addSnooper(L2Player pci)
	 * {
	 * if(!_snoopListener.contains(pci))
	 * _snoopListener.add(pci);
	 * }
	 * <p/>
	 * public void removeSnooper(L2Player pci)
	 * {
	 * _snoopListener.remove(pci);
	 * }
	 * <p/>
	 * public void addSnooped(L2Player pci)
	 * {
	 * if(!_snoopedPlayer.contains(pci))
	 * _snoopedPlayer.add(pci);
	 * }
	 * <p/>
	 * public void removeSnooped(L2Player pci)
	 * {
	 * _snoopedPlayer.remove(pci);
	 * }
	 */

	/**
	 * ĐˇĐ±Ń€ĐľŃ� Ń€ĐµŃŽĐ·Đ° Đ˛Ń�ĐµŃ… Ń�ĐşĐ¸Đ»ĐľĐ˛ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶Đ°.
	 */
	public void resetReuse() {
		_skillReuses.clear();
		_sharedGroupReuses.clear();
	}

	public DeathPenalty getDeathPenalty() {
		return _activeClass == null ? null : _activeClass.getDeathPenalty(this);
	}

	private boolean _charmOfCourage = false;

	public boolean isCharmOfCourage() {
		return _charmOfCourage;
	}

	public void setCharmOfCourage(boolean val) {
		_charmOfCourage = val;

		if (!val)
			getEffectList().stopEffect(Skill.SKILL_CHARM_OF_COURAGE);

		sendEtcStatusUpdate();
	}

	private int _increasedForce = 0;
	private int _consumedSouls = 0;

	@Override
	public int getIncreasedForce() {
		return _increasedForce;
	}

	@Override
	public int getConsumedSouls() {
		return _consumedSouls;
	}

	@Override
	public void setConsumedSouls(int i, NpcInstance monster) {
		if (i == _consumedSouls)
			return;

		int max = (int) calcStat(Stats.SOULS_LIMIT, 0, monster, null);

		if (i > max)
			i = max;

		if (i <= 0) {
			_consumedSouls = 0;
			sendEtcStatusUpdate();
			return;
		}

		if (_consumedSouls != i) {
			int diff = i - _consumedSouls;
			if (diff > 0) {
				SystemMessage sm = new SystemMessage(SystemMessage.YOUR_SOUL_HAS_INCREASED_BY_S1_SO_IT_IS_NOW_AT_S2);
				sm.addNumber(diff);
				sm.addNumber(i);
				sendPacket(sm);
			}
		} else if (max == i) {
			sendPacket(Msg.SOUL_CANNOT_BE_ABSORBED_ANY_MORE);
			return;
		}

		_consumedSouls = i;
		sendPacket(new EtcStatusUpdate(this));
	}

	@Override
	public void setIncreasedForce(int i) {
		i = Math.min(i, Charge.MAX_CHARGE);
		i = Math.max(i, 0);

		if (i != 0 && i > _increasedForce)
			sendPacket(new SystemMessage(SystemMessage.YOUR_FORCE_HAS_INCREASED_TO_S1_LEVEL).addNumber(i));

		_increasedForce = i;
		sendEtcStatusUpdate();
	}

	private long _lastFalling;

	public boolean isFalling() {
		return System.currentTimeMillis() - _lastFalling < 5000;
	}

	public void falling(int height) {
		if (!Config.DAMAGE_FROM_FALLING || isDead() || isFlying() || isInWater() || isInBoat())
			return;
		_lastFalling = System.currentTimeMillis();
		int damage = (int) calcStat(Stats.FALL, getMaxHp() / 2000 * height, null, null);
		if (damage > 0) {
			int curHp = (int) getCurrentHp();
			if (curHp - damage < 1)
				setCurrentHp(1, false);
			else
				setCurrentHp(curHp - damage, false);
			sendPacket(
					new SystemMessage(SystemMessage.YOU_RECEIVED_S1_DAMAGE_FROM_TAKING_A_HIGH_FALL).addNumber(damage));
		}
	}

	/**
	 * ĐˇĐ¸Ń�Ń‚ĐµĐĽĐ˝Ń‹Đµ Ń�ĐľĐľĐ±Ń‰ĐµĐ˝Đ¸ŃŹ Đľ Ń‚ĐµĐşŃ�Ń‰ĐµĐĽ Ń�ĐľŃ�Ń‚ĐľŃŹĐ˝Đ¸Đ¸
	 * Ń…Đż
	 */
	@Override
	public void checkHpMessages(double curHp, double newHp) {
		// Ń�ŃŽĐ´Đ° ĐżĐ°Ń�Đ¸Đ˛Đ˝Ń‹Đµ Ń�ĐşĐ¸Đ»Đ»Ń‹
		int[] _hp = {
				30,
				30
		};
		int[] skills = {
				290,
				291
		};

		// Ń�ŃŽĐ´Đ° Đ°ĐşŃ‚Đ¸Đ˛Đ˝Ń‹Đµ ŃŤŃ„Ń„ĐµĐşŃ‚Ń‹
		int[] _effects_skills_id = {
				139,
				176,
				292,
				292,
				420
		};
		int[] _effects_hp = {
				30,
				30,
				30,
				60,
				30
		};

		double percent = getMaxHp() / 100;
		double _curHpPercent = curHp / percent;
		double _newHpPercent = newHp / percent;
		boolean needsUpdate = false;

		// check for passive skills
		for (int i = 0; i < skills.length; i++) {
			int level = getSkillLevel(skills[i]);
			if (level > 0)
				if (_curHpPercent > _hp[i] && _newHpPercent <= _hp[i]) {
					sendPacket(new SystemMessage(SystemMessage.SINCE_HP_HAS_DECREASED_THE_EFFECT_OF_S1_CAN_BE_FELT)
							.addSkillName(skills[i], level));
					needsUpdate = true;
				} else if (_curHpPercent <= _hp[i] && _newHpPercent > _hp[i]) {
					sendPacket(new SystemMessage(SystemMessage.SINCE_HP_HAS_INCREASED_THE_EFFECT_OF_S1_WILL_DISAPPEAR)
							.addSkillName(skills[i], level));
					needsUpdate = true;
				}
		}

		// check for active effects
		for (Integer i = 0; i < _effects_skills_id.length; i++)
			if (getEffectList().getEffectsBySkillId(_effects_skills_id[i]) != null)
				if (_curHpPercent > _effects_hp[i] && _newHpPercent <= _effects_hp[i]) {
					sendPacket(new SystemMessage(SystemMessage.SINCE_HP_HAS_DECREASED_THE_EFFECT_OF_S1_CAN_BE_FELT)
							.addSkillName(_effects_skills_id[i], 1));
					needsUpdate = true;
				} else if (_curHpPercent <= _effects_hp[i] && _newHpPercent > _effects_hp[i]) {
					sendPacket(new SystemMessage(SystemMessage.SINCE_HP_HAS_INCREASED_THE_EFFECT_OF_S1_WILL_DISAPPEAR)
							.addSkillName(_effects_skills_id[i], 1));
					needsUpdate = true;
				}

		if (needsUpdate)
			sendChanges();
	}

	/**
	 * ĐˇĐ¸Ń�Ń‚ĐµĐĽĐ˝Ń‹Đµ Ń�ĐľĐľĐ±Ń‰ĐµĐ˝Đ¸ŃŹ Đ´Đ»ŃŹ Ń‚ĐµĐĽĐ˝Ń‹Ń… ŃŤĐ»ŃŚŃ„ĐľĐ˛ Đľ
	 * Đ˛ĐşĐ»/Đ˛Ń‹ĐşĐ» ShadowSence (skill id = 294)
	 */
	public void checkDayNightMessages() {
		int level = getSkillLevel(294);
		if (level > 0)
			if (GameTimeController.getInstance().isNowNight())
				sendPacket(new SystemMessage(SystemMessage.IT_IS_NOW_MIDNIGHT_AND_THE_EFFECT_OF_S1_CAN_BE_FELT)
						.addSkillName(294, level));
			else
				sendPacket(new SystemMessage(SystemMessage.IT_IS_DAWN_AND_THE_EFFECT_OF_S1_WILL_NOW_DISAPPEAR)
						.addSkillName(294, level));
		sendChanges();
	}

	public int getZoneMask() {
		return _zoneMask;
	}

	public Zone[] getCurrentZones() {
		WorldRegion reg = getCurrentRegion();
		if (reg == null)
			return Zone.EMPTY_L2ZONE_ARRAY;
		// getZones() is package-private, but here we can call it because Player.java is
		// in the same package as WorldRegion
		return reg.getZones();
	}

	// TODO [G1ta0] ĐżĐµŃ€ĐµŃ€Đ°Đ±ĐľŃ‚Đ°Ń‚ŃŚ Đ˛ Đ»Đ¸Ń�ĐµĐ˝ĐµŃ€?
	@Override
	protected void onUpdateZones(List<Zone> leaving, List<Zone> entering) {
		super.onUpdateZones(leaving, entering);

		if ((leaving == null || leaving.isEmpty()) && (entering == null || entering.isEmpty()))
			return;

		boolean lastInCombatZone = (_zoneMask & ZONE_PVP_FLAG) == ZONE_PVP_FLAG;
		boolean lastInDangerArea = (_zoneMask & ZONE_ALTERED_FLAG) == ZONE_ALTERED_FLAG;
		boolean lastOnSiegeField = (_zoneMask & ZONE_SIEGE_FLAG) == ZONE_SIEGE_FLAG;
		boolean lastInPeaceZone = (_zoneMask & ZONE_PEACE_FLAG) == ZONE_PEACE_FLAG;
		// FIXME G1ta0 boolean lastInSSQZone = (_zoneMask & ZONE_SSQ_FLAG) ==
		// ZONE_SSQ_FLAG;

		boolean isInCombatZone = isInCombatZone();
		boolean isInDangerArea = isInDangerArea();
		boolean isOnSiegeField = isOnSiegeField();
		boolean isInPeaceZone = isInPeaceZone();
		boolean isInSSQZone = isInSSQZone();

		// ĐľĐ±Đ˝ĐľĐ˛Đ»ŃŹĐµĐĽ ĐşĐľĐĽĐżĐ°Ń�, Ń‚ĐľĐ»ŃŚĐşĐľ ĐµŃ�Đ»Đ¸ ĐżĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Đ˛
		// ĐĽĐ¸Ń€Đµ
		int lastZoneMask = _zoneMask;
		_zoneMask = 0;

		if (isInCombatZone)
			_zoneMask |= ZONE_PVP_FLAG;
		if (isInDangerArea)
			_zoneMask |= ZONE_ALTERED_FLAG;
		if (isOnSiegeField)
			_zoneMask |= ZONE_SIEGE_FLAG;
		if (isInPeaceZone)
			_zoneMask |= ZONE_PEACE_FLAG;
		if (isInSSQZone)
			_zoneMask |= ZONE_SSQ_FLAG;

		if (lastZoneMask != _zoneMask)
			sendPacket(new ExSetCompassZoneCode(this));

		if (lastInCombatZone != isInCombatZone)
			broadcastRelationChanged();

		if (lastInDangerArea != isInDangerArea)
			sendPacket(new EtcStatusUpdate(this));

		if (lastOnSiegeField != isOnSiegeField) {
			broadcastRelationChanged();
			if (isOnSiegeField)
				sendPacket(Msg.YOU_HAVE_ENTERED_A_COMBAT_ZONE);
			else {
				sendPacket(Msg.YOU_HAVE_LEFT_A_COMBAT_ZONE);
				if (!isTeleporting() && getPvpFlag() == 0)
					startPvPFlag(null);
			}
		}

		if (lastInPeaceZone != isInPeaceZone)
			if (isInPeaceZone) {
				setRecomTimerActive(false);
				if (getNevitSystem().isActive())
					getNevitSystem().stopAdventTask(true);
				startVitalityTask();
			} else
				stopVitalityTask();

		if (isInWater())
			startWaterTask();
		else
			stopWaterTask();
	}

	public void startAutoSaveTask() {
		if (!Config.AUTOSAVE)
			return;
		if (_autoSaveTask == null)
			_autoSaveTask = AutoSaveManager.getInstance().addAutoSaveTask(this);
	}

	public void stopAutoSaveTask() {
		if (_autoSaveTask != null)
			_autoSaveTask.cancel(false);
		_autoSaveTask = null;
	}

	public void startVitalityTask() {
		if (!Config.ALT_VITALITY_ENABLED)
			return;
		if (_vitalityTask == null)
			_vitalityTask = LazyPrecisionTaskManager.getInstance().addVitalityRegenTask(this);
	}

	public void stopVitalityTask() {
		if (_vitalityTask != null)
			_vitalityTask.cancel(false);
		_vitalityTask = null;
	}

	public void startPcBangPointsTask() {
		if (!Config.ALT_PCBANG_POINTS_ENABLED || Config.ALT_PCBANG_POINTS_DELAY <= 0)
			return;
		if (_pcCafePointsTask == null)
			_pcCafePointsTask = LazyPrecisionTaskManager.getInstance().addPCCafePointsTask(this);
	}

	public void stopPcBangPointsTask() {
		if (_pcCafePointsTask != null)
			_pcCafePointsTask.cancel(false);
		_pcCafePointsTask = null;
	}

	public void startUnjailTask(Player player, int time) {
		if (_unjailTask != null)
			_unjailTask.cancel(false);
		_unjailTask = ThreadPoolManager.getInstance().schedule(new UnJailTask(player), time * 60000);
	}

	public void stopUnjailTask() {
		if (_unjailTask != null)
			_unjailTask.cancel(false);
		_unjailTask = null;
	}

	@Override
	public void sendMessage(String message) {
		sendPacket(new SystemMessage(message));
	}

	private Location _lastClientPosition;
	private Location _lastServerPosition;

	public void setLastClientPosition(Location position) {
		_lastClientPosition = position;
	}

	public Location getLastClientPosition() {
		return _lastClientPosition;
	}

	public void setLastServerPosition(Location position) {
		_lastServerPosition = position;
	}

	public Location getLastServerPosition() {
		return _lastServerPosition;
	}

	private int _useSeed = 0;

	public void setUseSeed(int id) {
		_useSeed = id;
	}

	public int getUseSeed() {
		return _useSeed;
	}

	public int getRelation(Player target) {
		int result = 0;

		if (getClan() != null) {
			result |= RelationChanged.RELATION_CLAN_MEMBER;
			if (getClan() == target.getClan())
				result |= RelationChanged.RELATION_CLAN_MATE;
			if (getClan().getAllyId() != 0)
				result |= RelationChanged.RELATION_ALLY_MEMBER;
		}

		if (isClanLeader())
			result |= RelationChanged.RELATION_LEADER;

		Party party = getParty();
		if (party != null && party == target.getParty()) {
			result |= RelationChanged.RELATION_HAS_PARTY;

			switch (party.getPartyMembers().indexOf(this)) {
				case 0:
					result |= RelationChanged.RELATION_PARTYLEADER; // 0x10
					break;
				case 1:
					result |= RelationChanged.RELATION_PARTY4; // 0x8
					break;
				case 2:
					result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2
							+ RelationChanged.RELATION_PARTY1; // 0x7
					break;
				case 3:
					result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2; // 0x6
					break;
				case 4:
					result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY1; // 0x5
					break;
				case 5:
					result |= RelationChanged.RELATION_PARTY3; // 0x4
					break;
				case 6:
					result |= RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1; // 0x3
					break;
				case 7:
					result |= RelationChanged.RELATION_PARTY2; // 0x2
					break;
				case 8:
					result |= RelationChanged.RELATION_PARTY1; // 0x1
					break;
			}
		}

		Clan clan1 = getClan();
		Clan clan2 = target.getClan();
		if (clan1 != null && clan2 != null) {
			if (target.getPledgeType() != Clan.SUBUNIT_ACADEMY && getPledgeType() != Clan.SUBUNIT_ACADEMY)
				if (clan2.isAtWarWith(clan1.getClanId())) {
					result |= RelationChanged.RELATION_1SIDED_WAR;
					if (clan1.isAtWarWith(clan2.getClanId()))
						result |= RelationChanged.RELATION_MUTUAL_WAR;
				}
			if (getBlockCheckerArena() != -1) {
				result |= RelationChanged.RELATION_INSIEGE;
				ArenaParticipantsHolder holder = HandysBlockCheckerManager.getInstance()
						.getHolder(getBlockCheckerArena());
				if (holder.getPlayerTeam(this) == 0)
					result |= RelationChanged.RELATION_ENEMY;
				else
					result |= RelationChanged.RELATION_ALLY;
				result |= RelationChanged.RELATION_ATTACKER;
			}
		}

		for (GlobalEvent e : getEvents())
			result = e.getRelation(this, target, result);

		return result;
	}

	public void setAutoAttackOnNonFlagged(boolean b) {
		_autoAttackOnNonFlagged = b;
	}

	public boolean getAutoAttackOnNonFlagged() {
		return _autoAttackOnNonFlagged;
	}

	/**
	 * 0=White, 1=Purple, 2=PurpleBlink
	 */
	protected int _pvpFlag;

	private Future<?> _PvPRegTask;
	private long _lastPvpAttack;

	public long getlastPvpAttack() {
		return _lastPvpAttack;
	}

	@Override
	public void startPvPFlag(Creature target) {
		if (_karma > 0)
			return;

		ForgottenBattlegroundsEvent fbEvent = getEvent(ForgottenBattlegroundsEvent.class);
		if (fbEvent != null && fbEvent.isParticipant(this)) {
			// In battleground, do not start pvp flag
			return;
		}

		long startTime = System.currentTimeMillis();
		if (target != null && target.getPvpFlag() != 0)
			startTime -= Config.PVP_TIME / 2;
		if (_pvpFlag != 0 && _lastPvpAttack > startTime)
			return;

		_lastPvpAttack = startTime;

		updatePvPFlag(1);

		if (_PvPRegTask == null)
			_PvPRegTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new PvPFlagTask(this), 1000, 1000);
	}

	public void stopPvPFlag() {
		if (_PvPRegTask != null) {
			_PvPRegTask.cancel(false);
			_PvPRegTask = null;
		}
		updatePvPFlag(0);
	}

	public void updatePvPFlag(int value) {
		if (_handysBlockCheckerEventArena != -1)
			return;
		if (_pvpFlag == value)
			return;

		setPvpFlag(value);

		sendStatusUpdate(true, true, StatusUpdate.PVP_FLAG);

		broadcastRelationChanged();
	}

	public void setPvpFlag(int pvpFlag) {
		_pvpFlag = pvpFlag;
	}

	@Override
	public int getPvpFlag() {
		return _pvpFlag;
	}

	public boolean isInDuel() {
		return getEvent(DuelEvent.class) != null;
	}

	private Map<Integer, TamedBeastInstance> _tamedBeasts = new ConcurrentHashMap<Integer, TamedBeastInstance>();

	public Map<Integer, TamedBeastInstance> getTrainedBeasts() {
		return _tamedBeasts;
	}

	public void addTrainedBeast(TamedBeastInstance tamedBeast) {
		_tamedBeasts.put(tamedBeast.getObjectId(), tamedBeast);
	}

	public void removeTrainedBeast(int npcId) {
		_tamedBeasts.remove(npcId);
	}

	private long _lastAttackPacket = 0;

	public long getLastAttackPacket() {
		return _lastAttackPacket;
	}

	public void setLastAttackPacket() {
		_lastAttackPacket = System.currentTimeMillis();
	}

	private long _lastMovePacket = 0;

	public long getLastMovePacket() {
		return _lastMovePacket;
	}

	public void setLastMovePacket() {
		_lastMovePacket = System.currentTimeMillis();
	}

	public byte[] getKeyBindings() {
		return _keyBindings;
	}

	public void setKeyBindings(byte[] keyBindings) {
		if (keyBindings == null)
			keyBindings = ArrayUtils.EMPTY_BYTE_ARRAY;
		_keyBindings = keyBindings;
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ Ń€ĐµĐ¶Đ¸ĐĽ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Đ¸Đ¸<BR>
	 *
	 * @param transformationId Đ¸Đ´ĐµĐ˝Ń‚Đ¸Ń„Đ¸ĐşĐ°Ń‚ĐľŃ€ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 *                         Đ�Đ·Đ˛ĐµŃ�Ń‚Đ˝Ń‹Đµ Ń€ĐµĐ¶Đ¸ĐĽŃ‹:<BR>
	 *                         <li>0 - Ń�Ń‚Đ°Đ˝Đ´Đ°Ń€Ń‚Đ˝Ń‹Đą Đ˛Đ¸Đ´ Ń‡Đ°Ń€Đ°
	 *                         <li>1 - Onyx Beast
	 *                         <li>2 - Death Blader
	 *                         <li>etc.
	 */
	public void setTransformation(int transformationId) {
		if (transformationId == _transformationId || _transformationId != 0 && transformationId != 0)
			return;

		// Đ”Đ»ŃŹ ĐşĐ°Đ¶Đ´ĐľĐą Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸ Ń�Đ˛ĐľĐą Đ˝Đ°Đ±ĐľŃ€
		// Ń�ĐşĐ¸Đ»ĐľĐ˛
		if (transformationId == 0) // ĐžĐ±Ń‹Ń‡Đ˝Đ°ŃŹ Ń„ĐľŃ€ĐĽĐ°
		{
			// ĐžŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµĐĽ Ń‚ĐµĐşŃ�Ń‰Đ¸Đą ŃŤŃ„Ń„ĐµĐşŃ‚
			// Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
			for (Effect effect : getEffectList().getAllEffects())
				if (effect != null && effect.getEffectType() == EffectType.Transformation) {
					if (effect.calc() == 0) // ĐťĐµ ĐľĐ±Ń€Ń‹Đ˛Đ°ĐµĐĽ Dispel
						continue;
					effect.exit();
					preparateToTransform(effect.getSkill());
					break;
				}

			// ĐŁĐ´Đ°Đ»ŃŹĐµĐĽ Ń�ĐşĐ¸Đ»Ń‹ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
			if (!_transformationSkills.isEmpty()) {
				for (Skill s : _transformationSkills.values()) {
					if (!s.isCommon() && !SkillAcquireHolder.getInstance().isSkillPossible(this, s) && !s.isHeroic()) {
						super.removeSkill(s);
					}
				}
				_transformationSkills.clear();
			}
			restoreSkills(getActiveClassClassId().getId());
			restoreSkills(getSecondaryClassId());
			rewardSkills(true);
		} else {
			if (!isCursedWeaponEquipped()) {
				// Đ”ĐľĐ±Đ°Đ˛Đ»ŃŹĐµĐĽ Ń�ĐşĐ¸Đ»Ń‹ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
				for (Effect effect : getEffectList().getAllEffects())
					if (effect != null && effect.getEffectType() == EffectType.Transformation) {
						if (effect.getSkill() instanceof Transformation
								&& ((Transformation) effect.getSkill()).isDisguise) {
							for (Skill s : getAllSkills())
								if (s != null && (s.isActive() || s.isToggle()))
									_transformationSkills.put(s.getId(), s);
						} else
							for (AddedSkill s : effect.getSkill().getAddedSkills())
								if (s.level == 0) // Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸ŃŹ ĐżĐľĐ·Đ˛ĐľĐ»ŃŹĐµŃ‚
													// ĐżĐľĐ»ŃŚĐ·ĐľĐ˛Đ°Ń‚ŃŚŃ�ŃŹ ĐľĐ±Ń‹Ń‡Đ˝Ń‹ĐĽ Ń�ĐşĐ¸Đ»Đ»ĐľĐĽ
								{
									int s2 = getSkillLevel(s.id);
									if (s2 > 0)
										_transformationSkills.put(s.id, SkillTable.getInstance().getInfo(s.id, s2));
								} else if (s.level == -2) // XXX: Đ´Đ¸ĐşĐ¸Đą Đ¸Đ·Đ¶ĐľĐż Đ´Đ»ŃŹ Ń�ĐşĐ¸Đ»Đ»ĐľĐ˛
															// Đ·Đ°Đ˛Đ¸Ń�ŃŹŃ‰Đ¸Ń… ĐľŃ‚ Ń�Ń€ĐľĐ˛Đ˝ŃŹ Đ¸ĐłŃ€ĐľĐşĐ°
								{
									int learnLevel = Math.max(effect.getSkill().getMagicLevel(), 40);
									int maxLevel = SkillTable.getInstance().getBaseLevel(s.id);
									int curSkillLevel = 1;
									if (maxLevel > 3)
										curSkillLevel += getLevel() - learnLevel;
									else
										curSkillLevel += (getLevel() - learnLevel) / ((76 - learnLevel) / maxLevel); // Đ˝Đµ
																														// Ń�ĐżŃ€Đ°Ń�Đ¸Đ˛Đ°ĐąŃ‚Đµ
																														// ĐĽĐµĐ˝ŃŹ
																														// Ń‡Ń‚Đľ
																														// ŃŤŃ‚Đľ
																														// Ń‚Đ°ĐşĐľĐµ
									curSkillLevel = Math.min(Math.max(curSkillLevel, 1), maxLevel);
									_transformationSkills.put(s.id,
											SkillTable.getInstance().getInfo(s.id, curSkillLevel));
								} else
									_transformationSkills.put(s.id, s.getSkill());
						preparateToTransform(effect.getSkill());
						break;
					}
			} else
				preparateToTransform(null);

			if (!isInOlympiadMode() && !isCursedWeaponEquipped() && _hero) {
				// Đ”ĐľĐ±Đ°Đ˛Đ»ŃŹĐµĐĽ Ń…Đ¸Ń€Đľ Ń�ĐşĐ¸Đ»Đ»Ń‹ ĐżŃ€ĐľĐşĐ»ŃŹŃ‚ĐľĐĽŃ�
				// Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽŃ�
				_transformationSkills.put(395, SkillTable.getInstance().getInfo(395, 1));
				_transformationSkills.put(396, SkillTable.getInstance().getInfo(396, 1));
				_transformationSkills.put(1374, SkillTable.getInstance().getInfo(1374, 1));
				_transformationSkills.put(1375, SkillTable.getInstance().getInfo(1375, 1));
				_transformationSkills.put(1376, SkillTable.getInstance().getInfo(1376, 1));
			}

			for (Skill s : _transformationSkills.values())
				addSkill(s, false);
		}

		_transformationId = transformationId;

		sendPacket(new ExBasicActionList(this));
		sendPacket(new SkillList(this));
		sendPacket(new ShortCutInit(this));
		for (int shotId : getAutoSoulShot())
			sendPacket(new ExAutoSoulShot(shotId, true));
		broadcastUserInfo(true);
	}

	private void preparateToTransform(Skill transSkill) {
		if (transSkill == null || !transSkill.isBaseTransformation()) {
			// ĐžŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµĐĽ Ń‚Ń�ĐłĐ» Ń�ĐşĐ¸Đ»Đ»Ń‹
			for (Effect effect : getEffectList().getAllEffects())
				if (effect != null && effect.getSkill().isToggle())
					effect.exit();
		}
	}

	public boolean isInFlyingTransform() {
		return _transformationId == 8 || _transformationId == 9 || _transformationId == 260;
	}

	public boolean isInMountTransform() {
		return _transformationId == 106 || _transformationId == 109 || _transformationId == 110
				|| _transformationId == 20001;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Ń€ĐµĐ¶Đ¸ĐĽ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 *
	 * @return ID Ń€ĐµĐ¶Đ¸ĐĽĐ° Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 */
	public int getTransformation() {
		return _transformationId;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Đ¸ĐĽŃŹ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 *
	 * @return String
	 */
	public String getTransformationName() {
		return _transformationName;
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ Đ¸ĐĽŃŹ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Đ¸Đ¸
	 *
	 * @param name Đ¸ĐĽŃŹ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 */
	public void setTransformationName(String name) {
		_transformationName = name;
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ Ń�Đ°Đ±Đ»ĐľĐ˝ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸,
	 * Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ´Đ»ŃŹ ĐľĐżŃ€ĐµĐ´ĐµĐ»ĐµĐ˝Đ¸ŃŹ ĐşĐľĐ»Đ»Đ¸Đ·Đ¸Đą
	 *
	 * @param template ID Ń�Đ°Đ±Đ»ĐľĐ˝Đ°
	 */
	public void setTransformationTemplate(int template) {
		_transformationTemplate = template;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ Ń�Đ°Đ±Đ»ĐľĐ˝ Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸,
	 * Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ´Đ»ŃŹ ĐľĐżŃ€ĐµĐ´ĐµĐ»ĐµĐ˝Đ¸ŃŹ ĐşĐľĐ»Đ»Đ¸Đ·Đ¸Đą
	 *
	 * @return NPC ID
	 */
	public int getTransformationTemplate() {
		return _transformationTemplate;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ ĐşĐľĐ»Đ»ĐµĐşŃ†Đ¸ŃŽ Ń�ĐşĐ¸Đ»Đ»ĐľĐ˛, Ń� Ń�Ń‡ĐµŃ‚ĐľĐĽ
	 * Ń‚ĐµĐşŃ�Ń‰ĐµĐą Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
	 */
	@Override
	public final Collection<Skill> getAllSkills() {
		// Đ˘Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸ŃŹ Đ˝ĐµĐ°ĐşŃ‚Đ¸Đ˛Đ˝Đ°
		if (_transformationId == 0)
			return super.getAllSkills();

		// Đ˘Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸ŃŹ Đ°ĐşŃ‚Đ¸Đ˛Đ˝Đ°
		Map<Integer, Skill> tempSkills = new HashMap<Integer, Skill>();
		for (Skill s : super.getAllSkills())
			if (s != null && !s.isActive() && !s.isToggle())
				tempSkills.put(s.getId(), s);
		tempSkills.putAll(_transformationSkills); // Đ”ĐľĐ±Đ°Đ˛Đ»ŃŹĐµĐĽ Đş ĐżĐ°Ń�Ń�Đ¸Đ˛ĐşĐ°ĐĽ Ń�ĐşĐ¸Đ»Ń‹ Ń‚ĐµĐşŃ�Ń‰ĐµĐą
													// Ń‚Ń€Đ°Đ˝Ń�Ń„ĐľŃ€ĐĽĐ°Ń†Đ¸Đ¸
		return tempSkills.values();
	}

	public void setAgathion(int id) {
		if (_agathionId == id)
			return;

		_agathionId = id;
		broadcastCharInfo();
	}

	public int getAgathionId() {
		return _agathionId;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ PcBangPoint'ĐľĐ˛ Đ´Đ°Đ˝ĐľĐłĐľ
	 * Đ¸ĐłŃ€ĐľĐşĐ°
	 *
	 * @return ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ PcCafe Bang Points
	 */
	public int getPcBangPoints() {
		return _pcBangPoints;
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ Pc Cafe Bang Points Đ´Đ»ŃŹ
	 * Đ´Đ°Đ˝ĐľĐłĐľ Đ¸ĐłŃ€ĐľĐşĐ°
	 *
	 * @param val Đ˝ĐľĐ˛ĐľĐµ ĐşĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ PcCafeBangPoints
	 */
	public void setPcBangPoints(int val) {
		_pcBangPoints = val;
	}

	public void addPcBangPoints(int count, boolean doublePoints) {
		if (doublePoints)
			count *= 2;

		_pcBangPoints += count;

		sendPacket(new SystemMessage(doublePoints ? SystemMessage.DOUBLE_POINTS_YOU_AQUIRED_S1_PC_BANG_POINT
				: SystemMessage.YOU_ACQUIRED_S1_PC_BANG_POINT).addNumber(count));
		sendPacket(new ExPCCafePointInfo(this, count, 1, 2, 12));
	}

	public boolean reducePcBangPoints(int count) {
		if (_pcBangPoints < count)
			return false;

		_pcBangPoints -= count;
		sendPacket(new SystemMessage(SystemMessage.YOU_ARE_USING_S1_POINT).addNumber(count));
		sendPacket(new ExPCCafePointInfo(this, 0, 1, 2, 12));
		return true;
	}

	private Location _groundSkillLoc;

	public void setGroundSkillLoc(Location location) {
		_groundSkillLoc = location;
	}

	public Location getGroundSkillLoc() {
		return _groundSkillLoc;
	}

	/**
	 * ĐźĐµŃ€Ń�ĐľĐ˝Đ°Đ¶ Đ˛ ĐżŃ€ĐľŃ†ĐµŃ�Ń�Đµ Đ˛Ń‹Ń…ĐľĐ´Đ° Đ¸Đ· Đ¸ĐłŃ€Ń‹
	 *
	 * @return Đ˛ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ true ĐµŃ�Đ»Đ¸ ĐżŃ€ĐľŃ†ĐµŃ�Ń� Đ˛Ń‹Ń…ĐľĐ´Đ° Ń�Đ¶Đµ
	 *         Đ˝Đ°Ń‡Đ°Đ»Ń�ŃŹ
	 */
	public boolean isLogoutStarted() {
		return _isLogout.get();
	}

	public void setOfflineMode(boolean val) {
		if (!val)
			unsetVar("offline");
		_offline = val;
	}

	public boolean isInOfflineMode() {
		return _offline;
	}

	public void saveTradeList() {
		String val = "";

		if (_sellList == null || _sellList.isEmpty())
			unsetVar("selllist");
		else {
			for (TradeItem i : _sellList)
				val += i.getObjectId() + ";" + i.getCount() + ";" + i.getOwnersPrice() + ":";
			setVar("selllist", val, -1);
			val = "";
			if (_tradeList != null && getSellStoreName() != null)
				setVar("sellstorename", getSellStoreName(), -1);
		}

		if (_packageSellList == null || _packageSellList.isEmpty())
			unsetVar("packageselllist");
		else {
			for (TradeItem i : _packageSellList)
				val += i.getObjectId() + ";" + i.getCount() + ";" + i.getOwnersPrice() + ":";
			setVar("packageselllist", val, -1);
			val = "";
			if (_tradeList != null && getSellStoreName() != null)
				setVar("sellstorename", getSellStoreName(), -1);
		}

		if (_buyList == null || _buyList.isEmpty())
			unsetVar("buylist");
		else {
			for (TradeItem i : _buyList)
				val += i.getItemId() + ";" + i.getCount() + ";" + i.getOwnersPrice() + ":";
			setVar("buylist", val, -1);
			val = "";
			if (_tradeList != null && getBuyStoreName() != null)
				setVar("buystorename", getBuyStoreName(), -1);
		}

		if (_createList == null || _createList.isEmpty())
			unsetVar("createlist");
		else {
			for (ManufactureItem i : _createList)
				val += i.getRecipeId() + ";" + i.getCost() + ":";
			setVar("createlist", val, -1);
			if (getManufactureName() != null)
				setVar("manufacturename", getManufactureName(), -1);
		}
	}

	public void restoreTradeList() {
		String var;
		var = getVar("selllist");
		if (var != null) {
			_sellList = new CopyOnWriteArrayList<TradeItem>();
			String[] items = var.split(":");
			for (String item : items) {
				if (item.equals(""))
					continue;
				String[] values = item.split(";");
				if (values.length < 3)
					continue;

				int oId = Integer.parseInt(values[0]);
				long count = Long.parseLong(values[1]);
				long price = Long.parseLong(values[2]);

				ItemInstance itemToSell = getInventory().getItemByObjectId(oId);

				if (count < 1 || itemToSell == null)
					continue;

				if (count > itemToSell.getCount())
					count = itemToSell.getCount();

				TradeItem i = new TradeItem(itemToSell);
				i.setCount(count);
				i.setOwnersPrice(price);

				_sellList.add(i);
			}
			var = getVar("sellstorename");
			if (var != null)
				setSellStoreName(var);
		}
		var = getVar("packageselllist");
		if (var != null) {
			_packageSellList = new CopyOnWriteArrayList<TradeItem>();
			String[] items = var.split(":");
			for (String item : items) {
				if (item.equals(""))
					continue;
				String[] values = item.split(";");
				if (values.length < 3)
					continue;

				int oId = Integer.parseInt(values[0]);
				long count = Long.parseLong(values[1]);
				long price = Long.parseLong(values[2]);

				ItemInstance itemToSell = getInventory().getItemByObjectId(oId);

				if (count < 1 || itemToSell == null)
					continue;

				if (count > itemToSell.getCount())
					count = itemToSell.getCount();

				TradeItem i = new TradeItem(itemToSell);
				i.setCount(count);
				i.setOwnersPrice(price);

				_packageSellList.add(i);
			}
			var = getVar("sellstorename");
			if (var != null)
				setSellStoreName(var);
		}
		var = getVar("buylist");
		if (var != null) {
			_buyList = new CopyOnWriteArrayList<TradeItem>();
			String[] items = var.split(":");
			for (String item : items) {
				if (item.equals(""))
					continue;
				String[] values = item.split(";");
				if (values.length < 3)
					continue;
				TradeItem i = new TradeItem();
				i.setItemId(Integer.parseInt(values[0]));
				i.setCount(Long.parseLong(values[1]));
				i.setOwnersPrice(Long.parseLong(values[2]));
				_buyList.add(i);
			}
			var = getVar("buystorename");
			if (var != null)
				setBuyStoreName(var);
		}
		var = getVar("createlist");
		if (var != null) {
			_createList = new CopyOnWriteArrayList<ManufactureItem>();
			String[] items = var.split(":");
			for (String item : items) {
				if (item.equals(""))
					continue;
				String[] values = item.split(";");
				if (values.length < 2)
					continue;
				int recId = Integer.parseInt(values[0]);
				long price = Long.parseLong(values[1]);
				if (findRecipe(recId))
					_createList.add(new ManufactureItem(recId, price));
			}
			var = getVar("manufacturename");
			if (var != null)
				setManufactureName(var);
		}
	}

	public void restoreRecipeBook() {
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT id FROM character_recipebook WHERE char_id=?");
			statement.setInt(1, getObjectId());
			rset = statement.executeQuery();

			while (rset.next()) {
				int id = rset.getInt("id");
				Recipe recipe = RecipeHolder.getInstance().getRecipeByRecipeId(id);
				registerRecipe(recipe, false);
			}
		} catch (Exception e) {
			_log.warn("count not recipe skills:" + e);
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	/**
	 * Switching PartyMatchingVisible state
	 */
	public void setPartyMatchingVisible() {
		_partyMatchingVisible = _partyMatchingVisible ? false : true;
	}

	/**
	 * @return should player be visible on party matching list?
	 */
	public boolean isPartyMatchingVisible() {
		return _partyMatchingVisible;
	}

	public DecoyInstance getDecoy() {
		return _decoy;
	}

	public void setDecoy(DecoyInstance decoy) {
		_decoy = decoy;
	}

	public int getMountType() {
		switch (getMountNpcId()) {
			case PetDataTable.STRIDER_WIND_ID:
			case PetDataTable.STRIDER_STAR_ID:
			case PetDataTable.STRIDER_TWILIGHT_ID:
			case PetDataTable.RED_STRIDER_WIND_ID:
			case PetDataTable.RED_STRIDER_STAR_ID:
			case PetDataTable.RED_STRIDER_TWILIGHT_ID:
			case PetDataTable.GUARDIANS_STRIDER_ID:
				return 1;
			case PetDataTable.WYVERN_ID:
				return 2;
			case PetDataTable.WGREAT_WOLF_ID:
			case PetDataTable.FENRIR_WOLF_ID:
			case PetDataTable.WFENRIR_WOLF_ID:
				return 3;
		}
		return 0;
	}

	@Override
	public double getColRadius() {
		if (getTransformation() != 0) {
			final int template = getTransformationTemplate();
			if (template != 0) {
				final NpcTemplate npcTemplate = NpcHolder.getInstance().getTemplate(template);
				if (npcTemplate != null)
					return npcTemplate.collisionRadius;
			}
		} else if (isMounted()) {
			final int mountTemplate = getMountNpcId();
			if (mountTemplate != 0) {
				final NpcTemplate mountNpcTemplate = NpcHolder.getInstance().getTemplate(mountTemplate);
				if (mountNpcTemplate != null)
					return mountNpcTemplate.collisionRadius;
			}
		}
		return getBaseTemplate().collisionRadius;
	}

	@Override
	public double getColHeight() {
		if (getTransformation() != 0) {
			final int template = getTransformationTemplate();
			if (template != 0) {
				final NpcTemplate npcTemplate = NpcHolder.getInstance().getTemplate(template);
				if (npcTemplate != null)
					return npcTemplate.collisionHeight;
			}
		} else if (isMounted()) {
			final int mountTemplate = getMountNpcId();
			if (mountTemplate != 0) {
				final NpcTemplate mountNpcTemplate = NpcHolder.getInstance().getTemplate(mountTemplate);
				if (mountNpcTemplate != null)
					return mountNpcTemplate.collisionHeight;
			}
		}
		return getBaseTemplate().collisionHeight;
	}

	@Override
	public void setReflection(Reflection reflection) {
		if (getReflection() == reflection)
			return;

		super.setReflection(reflection);

		if (_summon != null && !_summon.isDead())
			_summon.setReflection(reflection);

		if (reflection != ReflectionManager.DEFAULT) {
			String var = getVar("reflection");
			if (var == null || !var.equals(String.valueOf(reflection.getId())))
				setVar("reflection", String.valueOf(reflection.getId()), -1);
		} else
			unsetVar("reflection");

		if (getActiveClass() != null) {
			getInventory().validateItems();
			// Đ”Đ»ŃŹ ĐşĐ˛ĐµŃ�Ń‚Đ° _129_PailakaDevilsLegacy
			if (getPet() != null && (getPet().getNpcId() == 14916 || getPet().getNpcId() == 14917))
				getPet().unSummon();
		}
	}

	public boolean isTerritoryFlagEquipped() {
		ItemInstance weapon = getActiveWeaponInstance();
		return weapon != null && weapon.getTemplate().isTerritoryFlag();
	}

	private int _buyListId;

	public void setBuyListId(int listId) {
		_buyListId = listId;
	}

	public int getBuyListId() {
		return _buyListId;
	}

	public int getFame() {
		return _fame;
	}

	public void setFame(int fame, String log) {
		fame = Math.min(Config.LIM_FAME, fame);
		if (log != null && !log.isEmpty())
			Log.add(_name + "|" + (fame - _fame) + "|" + fame + "|" + log, "fame");
		if (fame > _fame)
			sendPacket(new SystemMessage(SystemMessage.YOU_HAVE_ACQUIRED_S1_REPUTATION_SCORE).addNumber(fame - _fame));
		_fame = fame;
		sendChanges();
	}

	public int getVitalityLevel(boolean blessActive) {
		return Config.ALT_VITALITY_ENABLED ? (blessActive ? 4 : _vitalityLevel) : 0;
	}

	public double getVitality() {
		return Config.ALT_VITALITY_ENABLED ? _vitality : 0;
	}

	public void addVitality(double val) {
		setVitality(getVitality() + val);
	}

	public void setVitality(double newVitality) {
		if (!Config.ALT_VITALITY_ENABLED)
			return;

		newVitality = Math.max(Math.min(newVitality, Config.VITALITY_LEVELS[4]), 0);

		if (newVitality >= _vitality || getLevel() >= 10) {
			if (newVitality != _vitality)
				if (newVitality == 0)
					sendPacket(Msg.VITALITY_IS_FULLY_EXHAUSTED);
				else if (newVitality == Config.VITALITY_LEVELS[4])
					sendPacket(Msg.YOUR_VITALITY_IS_AT_MAXIMUM);

			_vitality = newVitality;
		}

		int newLevel = 0;
		if (_vitality >= Config.VITALITY_LEVELS[3])
			newLevel = 4;
		else if (_vitality >= Config.VITALITY_LEVELS[2])
			newLevel = 3;
		else if (_vitality >= Config.VITALITY_LEVELS[1])
			newLevel = 2;
		else if (_vitality >= Config.VITALITY_LEVELS[0])
			newLevel = 1;

		if (_vitalityLevel > newLevel)
			getNevitSystem().addPoints(1500); // TODO: ĐšĐľĐ»Đ¸Ń‡ĐµŃ�Ń‚Đ˛Đľ ĐľŃ‚ Đ±Đ°Đ»Đ´Ń‹.

		if (_vitalityLevel != newLevel) {
			if (_vitalityLevel != -1) // ĐżŃ€Đ¸ Đ¸Đ˝Đ¸Ń‚Đµ Ń‡Đ°Ń€Đ° Ń�ĐľĐľĐ±Ń‰ĐµĐ˝Đ¸ŃŹ Đ˝Đµ Ń�Đ»Ń‘ĐĽ
				sendPacket(newLevel < _vitalityLevel ? Msg.VITALITY_HAS_DECREASED : Msg.VITALITY_HAS_INCREASED);
			_vitalityLevel = newLevel;
		}

		sendPacket(new ExVitalityPointInfo((int) _vitality));
	}

	private final int _incorrectValidateCount = 0;

	public int getIncorrectValidateCount() {
		return _incorrectValidateCount;
	}

	public int setIncorrectValidateCount(int count) {
		return _incorrectValidateCount;
	}

	public int getExpandInventory() {
		return _expandInventory;
	}

	public void setExpandInventory(int inventory) {
		_expandInventory = inventory;
	}

	public int getExpandWarehouse() {
		return _expandWarehouse;
	}

	public void setExpandWarehouse(int warehouse) {
		_expandWarehouse = warehouse;
	}

	public boolean isNotShowBuffAnim() {
		return _notShowBuffAnim;
	}

	public void setNotShowBuffAnim(boolean value) {
		_notShowBuffAnim = value;
	}

	public void enterMovieMode() {
		if (isInMovie()) // already in movie
			return;

		setTarget(null);
		stopMove();
		setIsInMovie(true);
		sendPacket(new CameraMode(1));
	}

	public void leaveMovieMode() {
		setIsInMovie(false);
		sendPacket(new CameraMode(0));
		broadcastCharInfo();
	}

	public void specialCamera(GameObject target, int dist, int yaw, int pitch, int time, int duration) {
		sendPacket(new SpecialCamera(target.getObjectId(), dist, yaw, pitch, time, duration));
	}

	public void specialCamera(GameObject target, int dist, int yaw, int pitch, int time, int duration, int turn,
			int rise, int widescreen, int unk) {
		sendPacket(
				new SpecialCamera(target.getObjectId(), dist, yaw, pitch, time, duration, turn, rise, widescreen, unk));
	}

	private int _movieId = 0;
	private boolean _isInMovie;

	public void setMovieId(int id) {
		_movieId = id;
	}

	public int getMovieId() {
		return _movieId;
	}

	public boolean isInMovie() {
		return _isInMovie;
	}

	public void setIsInMovie(boolean state) {
		_isInMovie = state;
	}

	public void showQuestMovie(SceneMovie movie) {
		if (isInMovie()) // already in movie
			return;

		sendActionFailed();
		setTarget(null);
		stopMove();
		setMovieId(movie.getId());
		setIsInMovie(true);
		sendPacket(movie.packet(this));
	}

	public void showQuestMovie(int movieId) {
		if (isInMovie()) // already in movie
			return;

		sendActionFailed();
		setTarget(null);
		stopMove();
		setMovieId(movieId);
		setIsInMovie(true);
		sendPacket(new ExStartScenePlayer(movieId));
	}

	public void setAutoLoot(boolean enable) {
		if (Config.AUTO_LOOT_INDIVIDUAL) {
			_autoLoot = enable;
			setVar("AutoLoot", String.valueOf(enable), -1);
		}
	}

	public void setAutoLootHerbs(boolean enable) {
		if (Config.AUTO_LOOT_INDIVIDUAL) {
			AutoLootHerbs = enable;
			setVar("AutoLootHerbs", String.valueOf(enable), -1);
		}
	}

	public boolean isAutoLootEnabled() {
		return _autoLoot;
	}

	public boolean isAutoLootHerbsEnabled() {
		return AutoLootHerbs;
	}

	public SkillLearnType getSkillLearnType() {
		return _skillLearnType;
	}

	public void setSkillLearnType(int type) {
		_skillLearnType = SkillLearnType.values()[type];
		setVar("skillLearn", type, -1);
	}

	public void setSkillLearnType(SkillLearnType type) {
		_skillLearnType = type;
	}

	public enum SkillLearnType {
		One_By_One,
		All_At_Once,
		Insta_Learn
	}

	public final void reName(String name, boolean saveToDB) {
		setName(name);
		if (saveToDB)
			saveNameToDB();
		broadcastCharInfo();
	}

	public final void reName(String name) {
		reName(name, false);
	}

	public final void saveNameToDB() {
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			st = con.prepareStatement("UPDATE characters SET char_name = ? WHERE obj_Id = ?");
			st.setString(1, getName());
			st.setInt(2, getObjectId());
			st.executeUpdate();
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, st);
		}
	}

	@Override
	public Player getPlayer() {
		return this;
	}

	private List<String> getStoredBypasses(boolean bbs) {
		if (bbs) {
			if (bypasses_bbs == null)
				bypasses_bbs = new LazyArrayList<String>();
			return bypasses_bbs;
		}
		if (bypasses == null)
			bypasses = new LazyArrayList<String>();
		return bypasses;
	}

	public void cleanBypasses(boolean bbs) {
		List<String> bypassStorage = getStoredBypasses(bbs);
		synchronized (bypassStorage) {
			bypassStorage.clear();
		}
	}

	public String encodeBypasses(String htmlCode, boolean bbs) {
		List<String> bypassStorage = getStoredBypasses(bbs);
		synchronized (bypassStorage) {
			return BypassManager.encode(htmlCode, bypassStorage, bbs);
		}
	}

	public DecodedBypass decodeBypass(String bypass) {
		BypassType bpType = BypassManager.getBypassType(bypass);
		boolean bbs = bpType == BypassType.ENCODED_BBS || bpType == BypassType.SIMPLE_BBS;
		List<String> bypassStorage = getStoredBypasses(bbs);
		if (bpType == BypassType.ENCODED || bpType == BypassType.ENCODED_BBS)
			return BypassManager.decode(bypass, bypassStorage, bbs, this);
		if (bpType == BypassType.SIMPLE)
			return new DecodedBypass(bypass, false).trim();
		if (bpType == BypassType.SIMPLE_BBS && !bypass.startsWith("_bbsscripts"))
			return new DecodedBypass(bypass, true).trim();
		ICommunityBoardHandler handler = CommunityBoardManager.getInstance().getCommunityHandler(bypass);
		if (handler != null)
			return new DecodedBypass(bypass, handler).trim();
		_log.warn("Direct access to bypass: " + bypass + " / Player: " + getName());
		return null;
	}

	public int getTalismanCount() {
		return (int) calcStat(Stats.TALISMANS_LIMIT, 0, null, null);
	}

	public boolean getOpenCloak() {
		if (Config.ALT_OPEN_CLOAK_SLOT)
			return true;
		return (int) calcStat(Stats.CLOAK_SLOT, 0, null, null) > 0;
	}

	public final void disableDrop(int time) {
		_dropDisabled = System.currentTimeMillis() + time;
	}

	public final boolean isDropDisabled() {
		return _dropDisabled > System.currentTimeMillis();
	}

	private ItemInstance _petControlItem = null;

	public void setPetControlItem(int itemObjId) {
		setPetControlItem(getInventory().getItemByObjectId(itemObjId));
	}

	public void setPetControlItem(ItemInstance item) {
		_petControlItem = item;
	}

	public ItemInstance getPetControlItem() {
		return _petControlItem;
	}

	// Add the updateRecHave method
	public void updateRecHave() {
		// We simply call our DAO method to fetch desired_rec_have
		// and then apply it here, rather than direct DB code in the Player class.
		int desiredRecHave = CharacterDAO.getInstance().getDesiredRecHave(this);
		if (desiredRecHave < 0) {
			// Means the DAO returned -1 or we didn't find a record
			// We can log it or do something else:
			_log.warn("No desired_rec_have found for account {}.", getAccountName());
			return;
		}

		// If we found a valid desired_rec_have, set it.
		setRecomHave(desiredRecHave);
		sendUserInfo(true);

		_log.info("Updated rec_have for player {} to {}", getName(), desiredRecHave);
	}

	public void setActive() {
		// if(isTeleportProtected() && !isInPeaceZone())
		// sendMessage("You are no longer protected from Aggresive monsters");
		setNonAggroTime(0);
	}

	public void summonPet() {
		if (getPet() != null)
			return;

		ItemInstance controlItem = getPetControlItem();
		if (controlItem == null)
			return;

		int npcId = PetDataTable.getSummonId(controlItem);
		if (npcId == 0)
			return;

		NpcTemplate petTemplate = NpcHolder.getInstance().getTemplate(npcId);
		if (petTemplate == null)
			return;

		PetInstance pet = PetInstance.restore(controlItem, petTemplate, this);
		if (pet == null)
			return;

		setPet(pet);
		pet.setTitle(getName());

		if (!pet.isRespawned()) {
			pet.setCurrentHp(pet.getMaxHp(), false);
			pet.setCurrentMp(pet.getMaxMp());
			pet.setCurrentFed(pet.getMaxFed());
			pet.updateControlItem();
			pet.store();
		}

		pet.getInventory().restore();

		pet.setNonAggroTime(System.currentTimeMillis() + Config.NONAGGRO_TIME_ONTELEPORT);
		pet.setReflection(getReflection());
		pet.spawnMe(Location.findPointToStay(this, 50, 70));
		pet.setRunning();
		pet.setFollowMode(true);
		pet.getInventory().validateItems();

		if (pet instanceof PetBabyInstance)
			((PetBabyInstance) pet).startBuffTask();
	}

	private Map<Integer, Long> _traps;

	public Collection<TrapInstance> getTraps() {
		if (_traps == null)
			return null;
		Collection<TrapInstance> result = new ArrayList<TrapInstance>(getTrapsCount());
		TrapInstance trap;
		for (Integer trapId : _traps.keySet())
			if ((trap = (TrapInstance) GameObjectsStorage.get(_traps.get(trapId))) != null)
				result.add(trap);
			else
				_traps.remove(trapId);
		return result;
	}

	public int getTrapsCount() {
		return _traps == null ? 0 : _traps.size();
	}

	public void addTrap(TrapInstance trap) {
		if (_traps == null)
			_traps = new HashMap<Integer, Long>();
		_traps.put(trap.getObjectId(), trap.getStoredId());
	}

	public void removeTrap(TrapInstance trap) {
		Map<Integer, Long> traps = _traps;
		if (traps == null || traps.isEmpty())
			return;
		traps.remove(trap.getObjectId());
	}

	public void destroyFirstTrap() {
		Map<Integer, Long> traps = _traps;
		if (traps == null || traps.isEmpty())
			return;
		TrapInstance trap;
		for (Integer trapId : traps.keySet()) {
			if ((trap = (TrapInstance) GameObjectsStorage.get(traps.get(trapId))) != null) {
				trap.deleteMe();
				return;
			}
			return;
		}
	}

	public void destroyAllTraps() {
		Map<Integer, Long> traps = _traps;
		if (traps == null || traps.isEmpty())
			return;
		List<TrapInstance> toRemove = new ArrayList<TrapInstance>();
		for (Integer trapId : traps.keySet())
			toRemove.add((TrapInstance) GameObjectsStorage.get(traps.get(trapId)));
		for (TrapInstance t : toRemove)
			if (t != null)
				t.deleteMe();
	}

	public void setBlockCheckerArena(byte arena) {
		_handysBlockCheckerEventArena = arena;
	}

	public int getBlockCheckerArena() {
		return _handysBlockCheckerEventArena;
	}

	@Override
	public PlayerListenerList getListeners() {
		if (listeners == null)
			synchronized (this) {
				if (listeners == null)
					listeners = new PlayerListenerList(this);
			}
		return (PlayerListenerList) listeners;
	}

	@Override
	public PlayerStatsChangeRecorder getStatsRecorder() {
		if (_statsRecorder == null)
			synchronized (this) {
				if (_statsRecorder == null)
					_statsRecorder = new PlayerStatsChangeRecorder(this);
			}
		return (PlayerStatsChangeRecorder) _statsRecorder;
	}

	private Future<?> _hourlyTask;
	private int _hoursInGame = 0;

	public int getHoursInGame() {
		_hoursInGame++;
		return _hoursInGame;
	}

	public void startHourlyTask() {
		_hourlyTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new HourlyTask(this), 3600000L, 3600000L);
	}

	public void stopHourlyTask() {
		if (_hourlyTask != null) {
			_hourlyTask.cancel(false);
			_hourlyTask = null;
		}
	}

	public long getPremiumPoints() {
		if (Config.GAME_POINT_ITEM_ID != -1)
			return ItemFunctions.getItemCount(this, Config.GAME_POINT_ITEM_ID);
		else
			return getNetConnection().getPointG();
	}

	public void reducePremiumPoints(final int val) {
		int reduce = (getNetConnection().getPointG() - (val));
		if (Config.GAME_POINT_ITEM_ID != -1)
			ItemFunctions.removeItem(this, Config.GAME_POINT_ITEM_ID, val, true);
		else
			getNetConnection().setPointG(reduce);
	}

	private boolean _agathionResAvailable = false;

	public boolean isAgathionResAvailable() {
		return _agathionResAvailable;
	}

	public void setAgathionRes(boolean val) {
		_agathionResAvailable = val;
	}

	public boolean isClanAirShipDriver() {
		return isInBoat() && getBoat().isClanAirShip() && ((ClanAirShip) getBoat()).getDriver() == this;
	}

	/**
	 * _userSession - Đ¸Ń�ĐżĐľĐ»ŃŚŃŽĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ´Đ»ŃŹ Ń…Ń€Đ°Đ˝ĐµĐ˝Đ¸ŃŹ
	 * Đ˛Ń€ĐµĐĽĐµĐ˝Đ˝Ń‹Ń… ĐżĐµŃ€ĐµĐĽĐµĐ˝Đ˝Ń‹Ń….
	 */
	private Map<String, String> _userSession;

	public String getSessionVar(String key) {
		if (_userSession == null)
			return null;
		return _userSession.get(key);
	}

	public void setSessionVar(String key, String val) {
		if (_userSession == null)
			_userSession = new ConcurrentHashMap<String, String>();

		if (val == null || val.isEmpty())
			_userSession.remove(key);
		else
			_userSession.put(key, val);
	}

	public FriendList getFriendList() {
		return _friendList;
	}

	public boolean isNotShowTraders() {
		return _notShowTraders;
	}

	public void setNotShowTraders(boolean notShowTraders) {
		_notShowTraders = notShowTraders;
	}

	public boolean isDebug() {
		return _debug;
	}

	public void setDebug(boolean b) {
		_debug = b;
	}

	public void sendItemList(boolean show) {
		ItemInstance[] items = getInventory().getItems();
		LockType lockType = getInventory().getLockType();
		int[] lockItems = getInventory().getLockItems();

		int allSize = items.length;
		int questItemsSize = 0;
		int agathionItemsSize = 0;
		for (ItemInstance item : items) {
			if (item.getTemplate().isQuest())
				questItemsSize++;
			if (item.getTemplate().getAgathionEnergy() > 0)
				agathionItemsSize++;
		}

		sendPacket(new ItemList(allSize - questItemsSize, items, show, lockType, lockItems));
		if (questItemsSize > 0)
			sendPacket(new ExQuestItemList(questItemsSize, items, lockType, lockItems));
		if (agathionItemsSize > 0)
			sendPacket(new ExBR_AgathionEnergyInfo(agathionItemsSize, items));
	}

	public int getBeltInventoryIncrease() {
		ItemInstance item = getInventory().getPaperdollItem(Inventory.PAPERDOLL_BELT);
		if (item != null && item.getTemplate().getAttachedSkills() != null)
			for (Skill skill : item.getTemplate().getAttachedSkills())
				for (FuncTemplate func : skill.getAttachedFuncs())
					if (func._stat == Stats.INVENTORY_LIMIT)
						return (int) func._value;
		return 0;
	}

	@Override
	public boolean isPlayer() {
		return true;
	}

	public boolean checkCoupleAction(Player target) {
		if (target.getPrivateStoreType() != Player.STORE_PRIVATE_NONE) {
			sendPacket(
					new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IN_PRIVATE_STORE).addName(target));
			return false;
		}
		if (target.isFishing()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_FISHING).addName(target));
			return false;
		}
		if (target.isInCombat()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_IN_COMBAT).addName(target));
			return false;
		}
		if (target.isCursedWeaponEquipped()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_CURSED_WEAPON_EQUIPED)
					.addName(target));
			return false;
		}
		if (target.isInOlympiadMode()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_IN_OLYMPIAD).addName(target));
			return false;
		}
		if (target.isOnSiegeField()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_IN_SIEGE).addName(target));
			return false;
		}
		if (target.isInBoat() || target.getMountNpcId() != 0) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_IN_VEHICLE_MOUNT_OTHER)
					.addName(target));
			return false;
		}
		if (target.isTeleporting()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_TELEPORTING).addName(target));
			return false;
		}
		if (target.getTransformation() != 0) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_IN_TRANSFORM).addName(target));
			return false;
		}
		if (target.isDead()) {
			sendPacket(new SystemMessage(SystemMessage.COUPLE_ACTION_CANNOT_C1_TARGET_IS_DEAD).addName(target));
			return false;
		}
		return true;
	}

	@Override
	public void startAttackStanceTask() {
		startAttackStanceTask0();
		Summon summon = getPet();
		if (summon != null)
			summon.startAttackStanceTask0();
	}

	@Override
	public void displayGiveDamageMessage(Creature target, int damage, boolean crit, boolean miss, boolean shld,
			boolean magic) {
		super.displayGiveDamageMessage(target, damage, crit, miss, shld, magic);
		if (crit)
			if (magic)
				sendPacket(new SystemMessage(SystemMessage.MAGIC_CRITICAL_HIT).addName(this));
			else
				sendPacket(new SystemMessage(SystemMessage.C1_HAD_A_CRITICAL_HIT).addName(this));

		if (miss)
			sendPacket(new SystemMessage(SystemMessage.C1S_ATTACK_WENT_ASTRAY).addName(this));
		else if (!target.isDamageBlocked())
			sendPacket(new SystemMessage(SystemMessage.C1_HAS_GIVEN_C2_DAMAGE_OF_S3).addName(this).addName(target)
					.addNumber(damage));

		if (target.isPlayer()) {
			if (shld && damage > 1)
				target.sendPacket(SystemMsg.YOUR_SHIELD_DEFENSE_HAS_SUCCEEDED);
			else if (shld && damage == 1)
				target.sendPacket(SystemMsg.YOUR_EXCELLENT_SHIELD_DEFENSE_WAS_A_SUCCESS);
		}
	}

	@Override
	public void displayReceiveDamageMessage(Creature attacker, int damage) {
		if (attacker != this)
			sendPacket(new SystemMessage(SystemMessage.C1_HAS_RECEIVED_DAMAGE_OF_S3_FROM_C2).addName(this)
					.addName(attacker).addNumber((long) damage));
	}

	public IntObjectMap<String> getPostFriends() {
		return _postFriends;
	}

	public boolean isSharedGroupDisabled(int groupId) {
		TimeStamp sts = _sharedGroupReuses.get(groupId);
		if (sts == null)
			return false;
		if (sts.hasNotPassed())
			return true;
		_sharedGroupReuses.remove(groupId);
		return false;
	}

	public TimeStamp getSharedGroupReuse(int groupId) {
		return _sharedGroupReuses.get(groupId);
	}

	public void addSharedGroupReuse(int group, TimeStamp stamp) {
		_sharedGroupReuses.put(group, stamp);
	}

	public Collection<IntObjectMap.Entry<TimeStamp>> getSharedGroupReuses() {
		return _sharedGroupReuses.entrySet();
	}

	public void sendReuseMessage(ItemInstance item) {
		TimeStamp sts = getSharedGroupReuse(item.getTemplate().getReuseGroup());
		if (sts == null || !sts.hasNotPassed())
			return;

		long timeleft = sts.getReuseCurrent();
		long hours = timeleft / 3600000;
		long minutes = (timeleft - hours * 3600000) / 60000;
		long seconds = (long) Math.ceil((timeleft - hours * 3600000 - minutes * 60000) / 1000.);

		if (hours > 0)
			sendPacket(new SystemMessage2(item.getTemplate().getReuseType().getMessages()[2])
					.addItemName(item.getTemplate().getItemId()).addInteger(hours).addInteger(minutes)
					.addInteger(seconds));
		else if (minutes > 0)
			sendPacket(new SystemMessage2(item.getTemplate().getReuseType().getMessages()[1])
					.addItemName(item.getTemplate().getItemId()).addInteger(minutes).addInteger(seconds));
		else
			sendPacket(new SystemMessage2(item.getTemplate().getReuseType().getMessages()[0])
					.addItemName(item.getTemplate().getItemId()).addInteger(seconds));
	}

	public NevitSystem getNevitSystem() {
		return _nevitSystem;
	}

	public void ask(ConfirmDlg dlg, OnAnswerListener listener) {
		if (_askDialog != null)
			return;
		int rnd = Rnd.nextInt();
		_askDialog = new ImmutablePair<Integer, OnAnswerListener>(rnd, listener);
		dlg.setRequestId(rnd);
		sendPacket(dlg);
	}

	public Pair<Integer, OnAnswerListener> getAskListener(boolean clear) {
		if (!clear)
			return _askDialog;
		else {
			Pair<Integer, OnAnswerListener> ask = _askDialog;
			_askDialog = null;
			return ask;
		}
	}

	@Override
	public boolean isDead() {
		return (isInOlympiadMode() || isInDuel()) ? getCurrentHp() <= 1. : super.isDead();
	}

	@Override
	public int getAgathionEnergy() {
		ItemInstance item = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LBRACELET);
		return item == null ? 0 : item.getAgathionEnergy();
	}

	@Override
	public void setAgathionEnergy(int val) {
		ItemInstance item = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LBRACELET);
		if (item == null)
			return;
		item.setAgathionEnergy(val);
		item.setJdbcState(JdbcEntityState.UPDATED);

		sendPacket(new ExBR_AgathionEnergyInfo(1, item));
	}

	public boolean hasPrivilege(Privilege privilege) {
		return _clan != null && (getClanPrivileges() & privilege.mask()) == privilege.mask();
	}

	public MatchingRoom getMatchingRoom() {
		return _matchingRoom;
	}

	public void setMatchingRoom(MatchingRoom matchingRoom) {
		_matchingRoom = matchingRoom;
	}

	public void dispelBuffs() {
		for (Effect e : getEffectList().getAllEffects())
			if (!e.getSkill().isOffensive() && !e.getSkill().isNewbie() && e.isCancelable()
					&& !e.getSkill().isPreservedOnDeath()) {
				sendPacket(new SystemMessage(SystemMessage.THE_EFFECT_OF_S1_HAS_BEEN_REMOVED)
						.addSkillName(e.getSkill().getId(), e.getSkill().getLevel()));
				e.exit();
			}
		if (getPet() != null)
			for (Effect e : getPet().getEffectList().getAllEffects())
				if (!e.getSkill().isOffensive() && !e.getSkill().isNewbie() && e.isCancelable()
						&& !e.getSkill().isPreservedOnDeath())
					e.exit();
	}

	public void setInstanceReuse(int id, long time) {
		final SystemMessage msg = new SystemMessage(
				SystemMessage.INSTANT_ZONE_FROM_HERE__S1_S_ENTRY_HAS_BEEN_RESTRICTED_YOU_CAN_CHECK_THE_NEXT_ENTRY_POSSIBLE)
				.addString(getName());
		sendPacket(msg);
		_instancesReuses.put(id, time);
		mysql.set("REPLACE INTO character_instances (obj_id, id, reuse) VALUES (?,?,?)", getObjectId(), id, time);
	}

	public void removeInstanceReuse(int id) {
		if (_instancesReuses.remove(id) != null)
			mysql.set("DELETE FROM `character_instances` WHERE `obj_id`=? AND `id`=? LIMIT 1", getObjectId(), id);
	}

	public void removeAllInstanceReuses() {
		_instancesReuses.clear();
		mysql.set("DELETE FROM `character_instances` WHERE `obj_id`=?", getObjectId());
	}

	public void removeInstanceReusesByGroupId(int groupId) {
		for (int i : InstantZoneHolder.getInstance().getSharedReuseInstanceIdsByGroup(groupId))
			if (getInstanceReuse(i) != null)
				removeInstanceReuse(i);
	}

	public Long getInstanceReuse(int id) {
		return _instancesReuses.get(id);
	}

	public Map<Integer, Long> getInstanceReuses() {
		return _instancesReuses;
	}

	private void loadInstanceReuses() {
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT * FROM character_instances WHERE obj_id = ?");
			offline.setInt(1, getObjectId());
			rs = offline.executeQuery();
			while (rs.next()) {
				int id = rs.getInt("id");
				long reuse = rs.getLong("reuse");
				_instancesReuses.put(id, reuse);
			}
		} catch (Exception e) {
			_log.error("", e);
		} finally {
			DbUtils.closeQuietly(con, offline, rs);
		}
	}

	public Reflection getActiveReflection() {
		for (Reflection r : ReflectionManager.getInstance().getAll())
			if (r != null && ArrayUtils.contains(r.getVisitors(), getObjectId()))
				return r;
		return null;
	}

	public boolean canEnterInstance(int instancedZoneId) {
		InstantZone iz = InstantZoneHolder.getInstance().getInstantZone(instancedZoneId);

		if (isDead())
			return false;

		if (ReflectionManager.getInstance().size() > Config.MAX_REFLECTIONS_COUNT) {
			sendPacket(SystemMsg.THE_MAXIMUM_NUMBER_OF_INSTANCE_ZONES_HAS_BEEN_EXCEEDED);
			return false;
		}

		if (iz == null) {
			sendPacket(SystemMsg.SYSTEM_ERROR);
			return false;
		}

		if (ReflectionManager.getInstance().getCountByIzId(instancedZoneId) >= iz.getMaxChannels()) {
			sendPacket(SystemMsg.THE_MAXIMUM_NUMBER_OF_INSTANCE_ZONES_HAS_BEEN_EXCEEDED);
			return false;
		}

		return iz.getEntryType().canEnter(this, iz);
	}

	public boolean canReenterInstance(int instancedZoneId) {
		InstantZone iz = InstantZoneHolder.getInstance().getInstantZone(instancedZoneId);
		if (getActiveReflection() != null && getActiveReflection().getInstancedZoneId() != instancedZoneId) {
			sendPacket(
					SystemMsg.YOU_HAVE_ENTERED_ANOTHER_INSTANCE_ZONE_THEREFORE_YOU_CANNOT_ENTER_CORRESPONDING_DUNGEON);
			return false;
		}
		if (iz.isDispelBuffs())
			dispelBuffs();
		return iz.getEntryType().canReEnter(this, iz);
	}

	public int getBattlefieldChatId() {
		return _battlefieldChatId;
	}

	public void setBattlefieldChatId(int battlefieldChatId) {
		_battlefieldChatId = battlefieldChatId;
	}

	@Override
	public void broadCast(IStaticPacket... packet) {
		sendPacket(packet);
	}

	@Override
	public Iterator<Player> iterator() {
		return Collections.singleton(this).iterator();
	}

	public PlayerGroup getPlayerGroup() {
		if (getParty() != null) {
			if (getParty().getCommandChannel() != null)
				return getParty().getCommandChannel();
			else
				return getParty();
		} else
			return this;
	}

	public boolean isActionBlocked(String action) {
		return _blockedActions.contains(action);
	}

	public void blockActions(String... actions) {
		Collections.addAll(_blockedActions, actions);
	}

	public void unblockActions(String... actions) {
		for (String action : actions)
			_blockedActions.remove(action);
	}

	public OlympiadGame getOlympiadGame() {
		return _olympiadGame;
	}

	public void setOlympiadGame(OlympiadGame olympiadGame) {
		_olympiadGame = olympiadGame;
	}

	public OlympiadGame getOlympiadObserveGame() {
		return _olympiadObserveGame;
	}

	public void setOlympiadObserveGame(OlympiadGame olympiadObserveGame) {
		_olympiadObserveGame = olympiadObserveGame;
	}

	public void addRadar(int x, int y, int z) {
		sendPacket(new RadarControl(0, 1, x, y, z));
	}

	public void addRadarWithMap(int x, int y, int z) {
		sendPacket(new RadarControl(0, 2, x, y, z));
	}

	public PetitionMainGroup getPetitionGroup() {
		return _petitionGroup;
	}

	public void setPetitionGroup(PetitionMainGroup petitionGroup) {
		_petitionGroup = petitionGroup;
	}

	public int getLectureMark() {
		return _lectureMark;
	}

	public void setLectureMark(int lectureMark) {
		_lectureMark = lectureMark;
	}

	class TeleportPoints {
		private String _name;
		private Location _xyz;
		private long _prace;
		private int _itemId;
		private int _id;

		public TeleportPoints(String name, Location xyz, int id, int itemId, long price) {
			_id = id;
			_name = name;
			_xyz = xyz;
			_itemId = itemId;
			_prace = price;
		}

		public int getId() {
			return _id;
		}

		public String getName() {
			return _name;
		}

		public long getPrice() {
			return _prace;
		}

		public int getItemId() {
			return _itemId;
		}

		public Location getXYZ() {
			return _xyz;
		}
	}

	public TeleportPoints getTeleportPoint(String name) {
		for (TeleportPoints point : _teleportPoints) {
			if (point.getName().equalsIgnoreCase(name)) {
				return point;
			}
		}
		return null;
	}

	public void addTeleportPoint(String name, int id, int itemId, long price) {
		_teleportPoints.add(new TeleportPoints(name, new Location(getX(), getY(), getZ()), id, itemId, price));
	}

	public void delTeleportPoint(String name) {
		for (TeleportPoints point : _teleportPoints) {
			if (point.getName().equals(name)) {
				_teleportPoints.remove(point);
			}
		}
	}

	private boolean is_bbs_use = false;

	public void setIsBBSUse(boolean value) {
		is_bbs_use = value;
	}

	public boolean isBBSUse() {
		return is_bbs_use;
	}

	public void setAccountAccesslevel(final int level, final String comments, int banTime) {
		AuthServerCommunication.getInstance().sendPacket(new ChangeAccessLevel(getAccountName(), level, banTime));
	}

	@Deprecated
	public boolean isLangRus() {
		return false;
	}
}
// EOF java/l2ft/gameserver/model/Player.java