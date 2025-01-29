package l2ft.gameserver.model.instances;

import gnu.trove.TIntObjectIterator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.collections.MultiValueSet;
import l2ft.commons.lang.reference.HardReference;
import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.ai.CharacterAI;
import l2ft.gameserver.ai.CtrlEvent;
import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.htm.HtmCache;
import l2ft.gameserver.data.xml.holder.ItemHolder;
import l2ft.gameserver.data.xml.holder.MultiSellHolder;
import l2ft.gameserver.data.xml.holder.ResidenceHolder;
import l2ft.gameserver.data.xml.holder.SkillAcquireHolder;
import l2ft.gameserver.geodata.GeoEngine;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.instancemanager.DimensionalRiftManager;
import l2ft.gameserver.instancemanager.QuestManager;
import l2ft.gameserver.instancemanager.ReflectionManager;
import l2ft.gameserver.listener.NpcListener;
import l2ft.gameserver.model.AggroList;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.GameObjectTasks.NotifyAITask;
import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.MinionList;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.SkillLearn;
import l2ft.gameserver.model.Spawner;
import l2ft.gameserver.model.TeleportLocation;
import l2ft.gameserver.model.Territory;
import l2ft.gameserver.model.World;
import l2ft.gameserver.model.Zone.ZoneType;
import l2ft.gameserver.model.actor.listener.NpcListenerList;
import l2ft.gameserver.model.actor.recorder.NpcStatsChangeRecorder;
import l2ft.gameserver.model.base.AcquireType;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.model.entity.DimensionalRift;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.SevenSigns;
import l2ft.gameserver.model.entity.events.GlobalEvent;
import l2ft.gameserver.model.entity.events.objects.TerritoryWardObject;
import l2ft.gameserver.model.entity.residence.Castle;
import l2ft.gameserver.model.entity.residence.ClanHall;
import l2ft.gameserver.model.entity.residence.Dominion;
import l2ft.gameserver.model.entity.residence.Fortress;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.pledge.SubUnit;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestEventType;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.components.CustomMessage;
import l2ft.gameserver.network.l2.components.NpcString;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.AcquireSkillDone;
import l2ft.gameserver.network.l2.s2c.AcquireSkillList;
import l2ft.gameserver.network.l2.s2c.ActionFail;
import l2ft.gameserver.network.l2.s2c.AutoAttackStart;
import l2ft.gameserver.network.l2.s2c.ExChangeNpcState;
import l2ft.gameserver.network.l2.s2c.ExShowBaseAttributeCancelWindow;
import l2ft.gameserver.network.l2.s2c.ExShowVariationCancelWindow;
import l2ft.gameserver.network.l2.s2c.ExShowVariationMakeWindow;
import l2ft.gameserver.network.l2.s2c.L2GameServerPacket;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.NpcInfo;
import l2ft.gameserver.network.l2.s2c.RadarControl;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.network.l2.s2c.StatusUpdate;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.network.l2.s2c.ValidateLocation;
import l2ft.gameserver.scripts.Events;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.tables.ClanTable;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.taskmanager.DecayTaskManager;
import l2ft.gameserver.taskmanager.LazyPrecisionTaskManager;
import l2ft.gameserver.templates.StatsSet;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.templates.item.WeaponTemplate;
import l2ft.gameserver.templates.npc.Faction;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.templates.spawn.SpawnRange;
import l2ft.gameserver.utils.CertificationFunctions;
import l2ft.gameserver.utils.HtmlUtils;
import l2ft.gameserver.utils.ItemFunctions;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.utils.ReflectionUtils;
import l2ft.gameserver.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpcInstance extends Creature
{
	public static final String NO_CHAT_WINDOW = "noChatWindow";
	public static final String NO_RANDOM_WALK = "noRandomWalk";
	public static final String NO_RANDOM_ANIMATION = "noRandomAnimation";
	public static final String TARGETABLE = "TargetEnabled";
	public static final String QUEST_CHAT_FIRST = "QuestChatFirst";
	public static final String SHOW_NAME = "showName";

	private static final Logger _log = LoggerFactory.getLogger(NpcInstance.class);

	private int _personalAggroRange = -1;
	private int _level = 0;

	private long _dieTime = 0L;

	protected int _spawnAnimation = 2;

	private int _currentLHandId;
	private int _currentRHandId;

	private double _currentCollisionRadius;
	private double _currentCollisionHeight;

	private int npcState = 0;

	protected boolean _hasRandomAnimation;
	protected boolean _hasRandomWalk;
	protected boolean _hasChatWindow;

	private Future<?> _decayTask;
	private Future<?> _animationTask;

	private AggroList _aggroList;

	private boolean _isTargetable;
	private boolean _isQuestChatFirst;

	private boolean _showName;

	private Castle _nearestCastle;
	private Fortress _nearestFortress;
	private ClanHall _nearestClanHall;
	private Dominion _nearestDominion;

	private NpcString _nameNpcString = NpcString.NONE;
	private NpcString _titleNpcString = NpcString.NONE;

	private Spawner _spawn;
	private Location _spawnedLoc = new Location();
	private SpawnRange _spawnRange;

	private MultiValueSet<String> _parameters = StatsSet.EMPTY;

	public NpcInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);

		if(template == null)
			throw new NullPointerException("No template for Npc. Please check your datapack is setup correctly.");

		setParameters(template.getAIParams());

		_hasRandomAnimation = !getParameter(NO_RANDOM_ANIMATION, false) && Config.MAX_NPC_ANIMATION > 0;
		_hasRandomWalk = !getParameter(NO_RANDOM_WALK, false);
		setHasChatWindow(!getParameter(NO_CHAT_WINDOW, false));
		setTargetable(getParameter(TARGETABLE, true));
		setQuestChatFirst(getParameter(QUEST_CHAT_FIRST, false));
		setShowName(getParameter(SHOW_NAME, true));

		if(template.getSkills().size() > 0)
			for(TIntObjectIterator<Skill> iterator = template.getSkills().iterator(); iterator.hasNext();)
			{
				iterator.advance();
				addSkill(iterator.value());
			}

		setName(template.name);
		setTitle(template.title);

		// Đ¸Đ˝Đ¸Ń†Đ¸Đ°Đ»Đ¸Đ·Đ°Ń†Đ¸ŃŹ ĐżĐ°Ń€Đ°ĐĽĐµŃ‚Ń€ĐľĐ˛ ĐľŃ€Ń�Đ¶Đ¸ŃŹ
		setLHandId(getTemplate().lhand);
		setRHandId(getTemplate().rhand);

		// Đ¸Đ˝Đ¸Ń†Đ¸Đ°Đ»Đ¸Đ·Đ°Ń†Đ¸ŃŹ ĐşĐľĐ»Đ»Đ¸Đ·Đ¸Đą
		setCollisionHeight(getTemplate().collisionHeight);
		setCollisionRadius(getTemplate().collisionRadius);

		_aggroList = new AggroList(this);

		setFlying(getParameter("isFlying", false));
	}

	@SuppressWarnings("unchecked")
	@Override
	public HardReference<NpcInstance> getRef()
	{
		return (HardReference<NpcInstance>) super.getRef();
	}

	@Override
	public CharacterAI getAI()
	{
		if(_ai == null)
			synchronized (this)
			{
				if(_ai == null)
					_ai = getTemplate().getNewAI(this);
			}

		return _ai;
	}

	/**
	 * Return the position of the spawned point.<BR><BR>
	 * ĐśĐľĐ¶ĐµŃ‚ Đ˛ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°Ń‚ŃŚ Ń�Đ»Ń�Ń‡Đ°ĐąĐ˝Ń�ŃŽ Ń‚ĐľŃ‡ĐşŃ�, ĐżĐľŃŤŃ‚ĐľĐĽŃ� Đ˛Ń�ĐµĐłĐ´Đ° Ń�Đ»ĐµĐ´Ń�ĐµŃ‚ ĐşĐµŃ�Đ¸Ń€ĐľĐ˛Đ°Ń‚ŃŚ Ń€ĐµĐ·Ń�Đ»ŃŚŃ‚Đ°Ń‚ Đ˛Ń‹Đ·ĐľĐ˛Đ°!
	 */
	public Location getSpawnedLoc()
	{
		return _spawnedLoc;
	}

	public void setSpawnedLoc(Location loc)
	{
		_spawnedLoc = loc;
	}

	public int getRightHandItem()
	{
		return _currentRHandId;
	}

	public int getLeftHandItem()
	{
		return _currentLHandId;
	}

	public void setLHandId(int newWeaponId)
	{
		_currentLHandId = newWeaponId;
	}

	public void setRHandId(int newWeaponId)
	{
		_currentRHandId = newWeaponId;
	}

	public double getCollisionHeight()
	{
		return _currentCollisionHeight;
	}

	public void setCollisionHeight(double offset)
	{
		_currentCollisionHeight = offset;
	}

	public double getCollisionRadius()
	{
		return _currentCollisionRadius;
	}

	public void setCollisionRadius(double collisionRadius)
	{
		_currentCollisionRadius = collisionRadius;
	}

	@Override
	protected void onReduceCurrentHp(double damage, Creature attacker, Skill skill, boolean awake, boolean standUp, boolean directHp)
	{
		if(attacker.isPlayable())
			getAggroList().addDamageHate(attacker, (int) damage, 0);

		super.onReduceCurrentHp(damage, attacker, skill, awake, standUp, directHp);
	}

	@Override
	protected void onDeath(Creature killer)
	{
		_dieTime = System.currentTimeMillis();

		if(isMonster() && (((MonsterInstance) this).isSeeded() || ((MonsterInstance) this).isSpoiled()))
			startDecay(20000L);
		else if(isBoss())
			startDecay(20000L);
		else if(isFlying())
			startDecay(4500L);
		else
			startDecay(8500L);

		// Ń�Ń�Ń‚Đ°Đ˝ĐľĐ˛ĐşĐ° ĐżĐ°Ń€Đ°ĐĽĐµŃ‚Ń€ĐľĐ˛ ĐľŃ€Ń�Đ¶Đ¸ŃŹ Đ¸ ĐşĐľĐ»Đ»Đ¸Đ·Đ¸Đą ĐżĐľ Ń�ĐĽĐľĐ»Ń‡Đ°Đ˝Đ¸ŃŽ
		setLHandId(getTemplate().lhand);
		setRHandId(getTemplate().rhand);
		setCollisionHeight(getTemplate().collisionHeight);
		setCollisionRadius(getTemplate().collisionRadius);

		getAI().stopAITask();
		stopRandomAnimation();

		super.onDeath(killer);
	}

	public long getDeadTime()
	{
		if(_dieTime <= 0L)
			return 0L;
		return System.currentTimeMillis() - _dieTime;
	}

	public AggroList getAggroList()
	{
		return _aggroList;
	}

	public MinionList getMinionList()
	{
		return null;
	}

	public boolean hasMinions()
	{
		return false;
	}

	public void dropItem(Player lastAttacker, int itemId, long itemCount)
	{
		if(itemCount == 0 || lastAttacker == null)
			return;

		ItemInstance item;

		for(long i = 0; i < itemCount; i++)
		{
			item = ItemFunctions.createItem(itemId);
			for(GlobalEvent e : getEvents())
				item.addEvent(e);

			// Set the Item quantity dropped if L2ItemInstance is stackable
			if(item.isStackable())
			{
				i = itemCount; // Set so loop won't happent again
				item.setCount(itemCount); // Set item count
			}

			if(isRaid() || this instanceof ReflectionBossInstance)
			{
				SystemMessage2 sm;
				if(itemId == 57)
				{
					sm = new SystemMessage2(SystemMsg.C1_HAS_DIED_AND_DROPPED_S2_ADENA);
					sm.addName(this);
					sm.addLong(item.getCount());
				}
				else
				{
					sm = new SystemMessage2(SystemMsg.C1_DIED_AND_DROPPED_S3_S2);
					sm.addName(this);
					sm.addItemName(itemId);
					sm.addLong(item.getCount());
				}
				broadcastPacket(sm);
			}

			lastAttacker.doAutoLootOrDrop(item, this);
		}
	}

	public void dropItem(Player lastAttacker, ItemInstance item)
	{
		if(item.getCount() == 0)
			return;

		if(isRaid() || this instanceof ReflectionBossInstance)
		{
			SystemMessage2 sm;
			if(item.getItemId() == 57)
			{
				sm = new SystemMessage2(SystemMsg.C1_HAS_DIED_AND_DROPPED_S2_ADENA);
				sm.addName(this);
				sm.addLong(item.getCount());
			}
			else
			{
				sm = new SystemMessage2(SystemMsg.C1_DIED_AND_DROPPED_S3_S2);
				sm.addName(this);
				sm.addItemName(item.getItemId());
				sm.addLong(item.getCount());
			}
			broadcastPacket(sm);
		}

		lastAttacker.doAutoLootOrDrop(item, this);
	}

	@Override
	public boolean isAttackable(Creature attacker)
	{
		return true;
	}

	@Override
	public boolean isAutoAttackable(Creature attacker)
	{
		return false;
	}

	@Override
	protected void onSpawn()
	{
		super.onSpawn();

		_dieTime = 0L;
		_spawnAnimation = 0;

		if(getAI().isGlobalAI() || getCurrentRegion() != null && getCurrentRegion().isActive())
		{
			getAI().startAITask();
			startRandomAnimation();
		}

		ThreadPoolManager.getInstance().execute(new NotifyAITask(this, CtrlEvent.EVT_SPAWN));

		getListeners().onSpawn();
	}

	@Override
	protected void onDespawn()
	{
		getAggroList().clear();

		getAI().onEvtDeSpawn();
		getAI().stopAITask();
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		stopRandomAnimation();

		super.onDespawn();
	}

	@Override
	public NpcTemplate getTemplate()
	{
		return (NpcTemplate) _template;
	}

	@Override
	public int getNpcId()
	{
		return getTemplate().npcId;
	}

	protected boolean _unAggred = false;

	public void setUnAggred(boolean state)
	{
		_unAggred = state;
	}

	/**
	 * Return True if the L2NpcInstance is aggressive (ex : L2MonsterInstance in function of aggroRange).<BR><BR>
	 */
	public boolean isAggressive()
	{
		return getAggroRange() > 0;
	}

	public int getAggroRange()
	{
		if(_unAggred)
			return 0;

		if(_personalAggroRange >= 0)
			return _personalAggroRange;

		return getTemplate().aggroRange;
	}

	/**
	 * ĐŁŃ�Ń‚Đ°Đ˝Đ°Đ˛Đ»Đ¸Đ˛Đ°ĐµŃ‚ Đ´Đ°Đ˝Đ˝ĐľĐĽŃ� npc Đ˝ĐľĐ˛Ń‹Đą aggroRange.
	 * Đ•Ń�Đ»Đ¸ Ń�Ń�Ń‚Đ°Đ˝ĐľĐ˛Đ»ĐµĐ˝Ń‹Đą aggroRange < 0, Ń‚Đľ Đ±Ń�Đ´ĐµŃ‚ Đ±Ń€Đ°Ń‚Ń�ŃŹ Đ°ĐłĐłŃ€ĐľŃ€ĐµĐąĐ˝Đ´Đ¶ Ń� Ń‚ĐµĐĽĐżĐ»ĐµĐąŃ‚Đ°.
	 * @param aggroRange Đ˝ĐľĐ˛Ń‹Đą agrroRange
	 */
	public void setAggroRange(int aggroRange)
	{
		_personalAggroRange = aggroRange;
	}

	/**
	 * Đ’ĐľĐ·Đ˛Ń€Đ°Ń‰Đ°ĐµŃ‚ ĐłŃ€Ń�ĐżĐżŃ� Ń�ĐľŃ†Đ¸Đ°Đ»ŃŚĐ˝ĐľŃ�Ń‚Đ¸
	 */
	public Faction getFaction()
	{
		return getTemplate().getFaction();
	}

	public boolean isInFaction(NpcInstance npc)
	{
		return getFaction().equals(npc.getFaction()) && !getFaction().isIgnoreNpcId(npc.getNpcId());
	}

	@Override
	public int getMAtk(Creature target, Skill skill)
	{
		return (int) (super.getMAtk(target, skill) * Config.ALT_NPC_MATK_MODIFIER);
	}

	@Override
	public int getPAtk(Creature target)
	{
		return (int) (super.getPAtk(target) * Config.ALT_NPC_PATK_MODIFIER);
	}

	@Override
	public int getMaxHp()
	{
		return (int) (super.getMaxHp() * Config.ALT_NPC_MAXHP_MODIFIER);
	}

	@Override
	public int getMaxMp()
	{
		return (int) (super.getMaxMp() * Config.ALT_NPC_MAXMP_MODIFIER);
	}

	public long getExpReward()
	{
		return (long) calcStat(Stats.EXP, getTemplate().rewardExp, null, null);
	}

	public long getSpReward()
	{
		return (long) calcStat(Stats.SP, getTemplate().rewardSp, null, null);
	}

	@Override
	protected void onDelete()
	{
		stopDecay();
		if(_spawn != null)
			_spawn.stopRespawn();
		setSpawn(null);

		super.onDelete();
	}

	public Spawner getSpawn()
	{
		return _spawn;
	}

	public void setSpawn(Spawner spawn)
	{
		_spawn = spawn;
	}

	@Override
	protected void onDecay()
	{
		super.onDecay();

		_spawnAnimation = 2;

		if(_spawn != null)
			_spawn.decreaseCount(this);
		else
			deleteMe(); // Đ•Ń�Đ»Đ¸ ŃŤŃ‚ĐľŃ‚ ĐĽĐľĐ± Đ·Đ°Ń�ĐżĐ°Đ˛Đ˝ĐµĐ˝ Đ˝Đµ Ń‡ĐµŃ€ĐµĐ· Ń�Ń‚Đ°Đ˝Đ´Đ°Ń€Ń‚Đ˝Ń‹Đą ĐĽĐµŃ…Đ°Đ˝Đ¸Đ·ĐĽ Ń�ĐżĐ°Đ˛Đ˝Đ° Đ·Đ˝Đ°Ń‡Đ¸Ń‚ ĐżĐľŃ�ĐĽĐµŃ€Ń‚Đ¸Đµ ĐµĐĽŃ� Đ˝Đµ ĐżĐľĐ»ĐľĐ¶ĐµĐ˝Đľ Đ¸ ĐľĐ˝ Ń�ĐĽĐ¸Ń€Đ°ĐµŃ‚ Đ˝Đ°Ń�ĐľĐ˛Ń�ĐµĐĽ
	}

	/**
	 * Đ—Đ°ĐżŃ�Ń�Ń‚Đ¸Ń‚ŃŚ Đ·Đ°Đ´Đ°Ń‡Ń� "Đ¸Ń�Ń‡ĐµĐ·Đ˝ĐľĐ˛ĐµĐ˝Đ¸ŃŹ" ĐżĐľŃ�Đ»Đµ Ń�ĐĽĐµŃ€Ń‚Đ¸
	 */
	protected void startDecay(long delay)
	{
		stopDecay();
		_decayTask = DecayTaskManager.getInstance().addDecayTask(this, delay);
	}

	/**
	 * ĐžŃ‚ĐĽĐµĐ˝Đ¸Ń‚ŃŚ Đ·Đ°Đ´Đ°Ń‡Ń� "Đ¸Ń�Ń‡ĐµĐ·Đ˝ĐľĐ˛ĐµĐ˝Đ¸ŃŹ" ĐżĐľŃ�Đ»Đµ Ń�ĐĽĐµŃ€Ń‚Đ¸
	 */
	public void stopDecay()
	{
		if(_decayTask != null)
		{
			_decayTask.cancel(false);
			_decayTask = null;
		}
	}

	/**
	 * ĐžŃ‚ĐĽĐµĐ˝Đ¸Ń‚ŃŚ Đ¸ Đ·Đ°Đ˛ĐµŃ€Ń�Đ¸Ń‚ŃŚ Đ·Đ°Đ´Đ°Ń‡Ń� "Đ¸Ń�Ń‡ĐµĐ·Đ˝ĐľĐ˛ĐµĐ˝Đ¸ŃŹ" ĐżĐľŃ�Đ»Đµ Ń�ĐĽĐµŃ€Ń‚Đ¸
	 */
	public void endDecayTask()
	{
		if(_decayTask != null)
		{
			_decayTask.cancel(false);
			_decayTask = null;
		}
		doDecay();
	}

	@Override
	public boolean isUndead()
	{
		return getTemplate().isUndead();
	}

	public void setLevel(int level)
	{
		_level = level;
	}

	@Override
	public int getLevel()
	{
		return _level == 0 ? getTemplate().level : _level;
	}

	private int _displayId = 0;

	public void setDisplayId(int displayId)
	{
		_displayId = displayId;
	}

	public int getDisplayId()
	{
		return _displayId > 0 ? _displayId : getTemplate().displayId;
	}

	@Override
	public ItemInstance getActiveWeaponInstance()
	{
		// regular NPCs dont have weapons instancies
		return null;
	}

	@Override
	public WeaponTemplate getActiveWeaponItem()
	{
		// Get the weapon identifier equipped in the right hand of the L2NpcInstance
		int weaponId = getTemplate().rhand;

		if(weaponId < 1)
			return null;

		// Get the weapon item equipped in the right hand of the L2NpcInstance
		ItemTemplate item = ItemHolder.getInstance().getTemplate(getTemplate().rhand);

		if(!(item instanceof WeaponTemplate))
			return null;

		return (WeaponTemplate) item;
	}

	@Override
	public ItemInstance getSecondaryWeaponInstance()
	{
		// regular NPCs dont have weapons instances
		return null;
	}

	@Override
	public WeaponTemplate getSecondaryWeaponItem()
	{
		// Get the weapon identifier equipped in the right hand of the L2NpcInstance
		int weaponId = getTemplate().lhand;

		if(weaponId < 1)
			return null;

		// Get the weapon item equipped in the right hand of the L2NpcInstance
		ItemTemplate item = ItemHolder.getInstance().getTemplate(getTemplate().lhand);

		if(!(item instanceof WeaponTemplate))
			return null;

		return (WeaponTemplate) item;
	}

	@Override
	public void sendChanges()
	{
		if(isFlying()) // FIXME
			return;
		super.sendChanges();
	}

	private ScheduledFuture<?> _broadcastCharInfoTask;

	public class BroadcastCharInfoTask extends RunnableImpl
	{
		@Override
		public void runImpl() throws Exception
		{
			broadcastCharInfoImpl();
			_broadcastCharInfoTask = null;
		}
	}

	@Override
	public void broadcastCharInfo()
	{
		if(!isVisible())
			return;

		if(_broadcastCharInfoTask != null)
			return;

		_broadcastCharInfoTask = ThreadPoolManager.getInstance().schedule(new BroadcastCharInfoTask(), Config.BROADCAST_CHAR_INFO_INTERVAL);
	}

	public void broadcastCharInfoImpl()
	{
		for(Player player : World.getAroundPlayers(this))
			player.sendPacket(new NpcInfo(this, player).update());
	}

	// ĐŁ NPC Đ˛Ń�ĐµĐłĐ´Đ° 2
	public void onRandomAnimation()
	{
		if(System.currentTimeMillis() - _lastSocialAction > 10000L)
		{
			broadcastPacket(new SocialAction(getObjectId(), 2));
			_lastSocialAction = System.currentTimeMillis();
		}
	}

	public void startRandomAnimation()
	{
		if(!hasRandomAnimation())
			return;
		_animationTask = LazyPrecisionTaskManager.getInstance().addNpcAnimationTask(this);
	}

	public void stopRandomAnimation()
	{
		if(_animationTask != null)
		{
			_animationTask.cancel(false);
			_animationTask = null;
		}
	}

	public boolean hasRandomAnimation()
	{
		return _hasRandomAnimation;
	}

	public boolean hasRandomWalk()
	{
		return _hasRandomWalk;
	}

	public Castle getCastle()
	{
		if(getReflection() == ReflectionManager.PARNASSUS && Config.SERVICES_PARNASSUS_NOTAX)
			return null;
		if(Config.SERVICES_OFFSHORE_NO_CASTLE_TAX && getReflection() == ReflectionManager.GIRAN_HARBOR)
			return null;
		if(Config.SERVICES_OFFSHORE_NO_CASTLE_TAX && getReflection() == ReflectionManager.PARNASSUS)
			return null;
		if(Config.SERVICES_OFFSHORE_NO_CASTLE_TAX && isInZone(ZoneType.offshore))
			return null;
		if(_nearestCastle == null)
			_nearestCastle = ResidenceHolder.getInstance().getResidence(getTemplate().getCastleId());
		return _nearestCastle;
	}

	public Castle getCastle(Player player)
	{
		return getCastle();
	}

	public Fortress getFortress()
	{
		if(_nearestFortress == null)
			_nearestFortress = ResidenceHolder.getInstance().findNearestResidence(Fortress.class, getX(), getY(), getZ(), getReflection(), 32768);

		return _nearestFortress;
	}

	public ClanHall getClanHall()
	{
		if(_nearestClanHall == null)
			_nearestClanHall = ResidenceHolder.getInstance().findNearestResidence(ClanHall.class, getX(), getY(), getZ(), getReflection(), 32768);

		return _nearestClanHall;
	}

	public Dominion getDominion()
	{
		if(getReflection() != ReflectionManager.DEFAULT)
			return null;

		if(_nearestDominion == null)
		{
			if(getTemplate().getCastleId() == 0)
				return null;

			Castle castle = ResidenceHolder.getInstance().getResidence(getTemplate().getCastleId());
			_nearestDominion = castle.getDominion();
		}

		return _nearestDominion;
	}

	protected long _lastSocialAction;

	@Override
	public void onAction(Player player, boolean shift)
	{
		if(!isTargetable())
		{
			player.sendActionFailed();
			return;
		}

		if(player.getTarget() != this)
		{
			player.setTarget(this);
			if(player.getTarget() == this)
				player.sendPacket(new MyTargetSelected(getObjectId(), player.getLevel() - getLevel()), makeStatusUpdate(StatusUpdate.CUR_HP, StatusUpdate.MAX_HP));

			player.sendPacket(new ValidateLocation(this), ActionFail.STATIC);
			return;
		}

		if(Events.onAction(player, this, shift))
		{
			player.sendActionFailed();
			return;
		}

		if(isAutoAttackable(player))
		{
			player.getAI().Attack(this, false, shift);
			return;
		}

		if(!isInRange(player, INTERACTION_DISTANCE))
		{
			if(player.getAI().getIntention() != CtrlIntention.AI_INTENTION_INTERACT)
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this, null);
			return;
		}

		if(!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && player.getKarma() > 0 && !player.isGM() && !(this instanceof WarehouseInstance))
		{
			player.sendActionFailed();
			return;
		}

		// Đˇ NPC Đ˝ĐµĐ»ŃŚĐ·ŃŹ Ń€Đ°Đ·ĐłĐľĐ˛Đ°Ń€Đ¸Đ˛Đ°Ń‚ŃŚ ĐĽĐµŃ€Ń‚Đ˛Ń‹ĐĽ Đ¸ Ń�Đ¸Đ´ŃŹ
		if(!Config.ALLOW_TALK_WHILE_SITTING && player.isSitting() || player.isAlikeDead())
			return;

		if(hasRandomAnimation())
			onRandomAnimation();

		player.sendActionFailed();
		player.stopMove(false);

		if(_isBusy)
			showBusyWindow(player);
		else if(isHasChatWindow())
		{
			boolean flag = false;
			Quest[] qlst = getTemplate().getEventQuests(QuestEventType.NPC_FIRST_TALK);
			if(qlst != null && qlst.length > 0)
				for(Quest element : qlst)
				{
					QuestState qs = player.getQuestState(element.getName());
					if((qs == null || !qs.isCompleted()) && element.notifyFirstTalk(this, player))
						flag = true;
				}
			if(isQuestChatFirst() && player.getQuestsForEvent(this, QuestEventType.QUEST_TALK).size() > 0)
			{
				for(QuestState qs :player.getQuestsForEvent(this, QuestEventType.QUEST_TALK))
					if(!qs.getQuest().onTalk(this, qs).equals("noquest"))
						if(qs.getQuest().notifyTalk(this, qs))
							return;
			}
			else if(isQuestChatFirst()) 
			{
					Quest[] questStarts = getTemplate().getEventQuests(QuestEventType.QUEST_START);
					if(questStarts != null && questStarts.length > 0)
						for(Quest element : questStarts){
								QuestState qs = element.newQuestState(player, Quest.CREATED);
								if(qs.getQuest().notifyTalk(this, qs))
									return;
						}
			}
			if(!flag)
				showChatWindow(player, 0);
		}
	}

	public void showQuestWindow(Player player, String questId)
	{
		if(!player.isQuestContinuationPossible(true))
			return;

		int count = 0;
		for(QuestState quest : player.getAllQuestsStates())
			if(quest != null && quest.getQuest().isVisible() && quest.isStarted() && quest.getCond() > 0)
				count++;

		if(count > 40)
		{
			showChatWindow(player, "quest-limit.htm");
			return;
		}

		try
		{
			// Get the state of the selected quest
			QuestState qs = player.getQuestState(questId);
			if(qs != null)
			{
				if(qs.isCompleted())
				{
					showChatWindow(player, "completed-quest.htm");
					return;
				}
				if(qs.getQuest().notifyTalk(this, qs))
					return;
			}
			else
			{
				Quest q = QuestManager.getQuest(questId);
				if(q != null)
				{
					// check for start point
					Quest[] qlst = getTemplate().getEventQuests(QuestEventType.QUEST_START);
					if(qlst != null && qlst.length > 0)
						for(Quest element : qlst)
							if(element == q)
							{
								qs = q.newQuestState(player, Quest.CREATED);
								if(qs.getQuest().notifyTalk(this, qs))
									return;
								break;
							}
				}
			}

			showChatWindow(player, "no-quest.htm");
		}
		catch(Exception e)
		{
			_log.warn("problem with npc text(questId: " + questId + ") " + e);
			_log.error("", e);
		}

		player.sendActionFailed();
	}

	public static boolean canBypassCheck(Player player, NpcInstance npc)
	{
		if(npc == null || player.isActionsDisabled() || !Config.ALLOW_TALK_WHILE_SITTING && player.isSitting() || !npc.isInRange(player, INTERACTION_DISTANCE))
		{
			player.sendActionFailed();
			return false;
		}
		return true;
	}

	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;

		if(getTemplate().getTeleportList().size() > 0 && checkForDominionWard(player))
			return;

		try
		{
			if(command.equalsIgnoreCase("TerritoryStatus"))
			{
				NpcHtmlMessage html = new NpcHtmlMessage(player, this);
				html.setFile("merchant/territorystatus.htm");
				html.replace("%npcname%", getName());

				Castle castle = getCastle(player);
				if(castle != null && castle.getId() > 0)
				{
					html.replace("%castlename%",  HtmlUtils.htmlResidenceName(castle.getId()));
					html.replace("%taxpercent%", String.valueOf(castle.getTaxPercent()));

					if(castle.getOwnerId() > 0)
					{
						Clan clan = ClanTable.getInstance().getClan(castle.getOwnerId());
						if(clan != null)
						{
							html.replace("%clanname%", clan.getName());
							html.replace("%clanleadername%", clan.getLeaderName());
						}
						else
						{
							html.replace("%clanname%", "unexistant clan");
							html.replace("%clanleadername%", "None");
						}
					}
					else
					{
						html.replace("%clanname%", "NPC");
						html.replace("%clanleadername%", "None");
					}
				}
				else
				{
					html.replace("%castlename%", "Open");
					html.replace("%taxpercent%", "0");

					html.replace("%clanname%", "No");
					html.replace("%clanleadername%", getName());
				}

				player.sendPacket(html);
			}
			else if(command.startsWith("Quest"))
			{
				String quest = command.substring(5).trim();
				if(quest.length() == 0)
					showQuestWindow(player);
				else
					showQuestWindow(player, quest);
			}
			else if(command.startsWith("Chat"))
				try
				{
					int val = Integer.parseInt(command.substring(5));
					showChatWindow(player, val);
				}
				catch(NumberFormatException nfe)
				{
					String filename = command.substring(5).trim();
					if(filename.length() == 0)
						showChatWindow(player, "npcdefault.htm");
					else
						showChatWindow(player, filename);
				}
			else if(command.startsWith("AttributeCancel"))
				player.sendPacket(new ExShowBaseAttributeCancelWindow(player));
			else if(command.startsWith("NpcLocationInfo"))
			{
				int val = Integer.parseInt(command.substring(16));
				NpcInstance npc = GameObjectsStorage.getByNpcId(val);
				if(npc != null)
				{
					// ĐŁĐ±Đ¸Ń€Đ°ĐµĐĽ Ń„Đ»Đ°Đ¶ĐľĐş Đ˝Đ° ĐşĐ°Ń€Ń‚Đµ Đ¸ Ń�Ń‚Ń€ĐµĐ»ĐşŃ� Đ˝Đ° ĐşĐľĐĽĐżĐ°Ń�Đµ
					player.sendPacket(new RadarControl(2, 2, npc.getLoc()));
					// ĐˇŃ‚Đ°Đ˛Đ¸ĐĽ Ń„Đ»Đ°Đ¶ĐľĐş Đ˝Đ° ĐşĐ°Ń€Ń‚Đµ Đ¸ Ń�Ń‚Ń€ĐµĐ»ĐşŃ� Đ˝Đ° ĐşĐľĐĽĐżĐ°Ń�Đµ
					player.sendPacket(new RadarControl(0, 1, npc.getLoc()));
				}
			}
			else if(command.startsWith("Multisell") || command.startsWith("multisell"))
			{
				String listId = command.substring(9).trim();
				Castle castle = getCastle(player);
				MultiSellHolder.getInstance().SeparateAndSend(Integer.parseInt(listId), player, castle != null ? castle.getTaxRate() : 0);
			}
			else if(command.startsWith("EnterRift"))
			{
				if(checkForDominionWard(player))
					return;

				StringTokenizer st = new StringTokenizer(command);
				st.nextToken(); //no need for "enterRift"

				Integer b1 = Integer.parseInt(st.nextToken()); //type

				DimensionalRiftManager.getInstance().start(player, b1, this);
			}
			else if(command.startsWith("ChangeRiftRoom"))
			{
				if(player.isInParty() && player.getParty().isInReflection() && player.getParty().getReflection() instanceof DimensionalRift)
					((DimensionalRift) player.getParty().getReflection()).manualTeleport(player, this);
				else
					DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
			}
			else if(command.startsWith("ExitRift"))
			{
				if(player.isInParty() && player.getParty().isInReflection() && player.getParty().getReflection() instanceof DimensionalRift)
					((DimensionalRift) player.getParty().getReflection()).manualExitRift(player, this);
				else
					DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
			}
			else if(command.equalsIgnoreCase("SkillList"))
				showSkillList(player);
			else if(command.equalsIgnoreCase("ClanSkillList"))
				showClanSkillList(player);
			else if(command.startsWith("SubUnitSkillList"))
				showSubUnitSkillList(player);
			else if(command.equalsIgnoreCase("TransformationSkillList"))
				showTransformationSkillList(player, AcquireType.TRANSFORMATION);
			else if(command.equalsIgnoreCase("CertificationSkillList"))
				showTransformationSkillList(player, AcquireType.CERTIFICATION);
			else if(command.equalsIgnoreCase("CollectionSkillList"))
				showCollectionSkillList(player);
			else if(command.equalsIgnoreCase("BuyTransformation"))
				showTransformationMultisell(player);
			else if(command.startsWith("Augment"))
			{
				int cmdChoice = Integer.parseInt(command.substring(8, 9).trim());
				if(cmdChoice == 1)
					player.sendPacket(Msg.SELECT_THE_ITEM_TO_BE_AUGMENTED, ExShowVariationMakeWindow.STATIC);
				else if(cmdChoice == 2)
					player.sendPacket(Msg.SELECT_THE_ITEM_FROM_WHICH_YOU_WISH_TO_REMOVE_AUGMENTATION, ExShowVariationCancelWindow.STATIC);
			}
			else if(command.startsWith("Link"))
				showChatWindow(player, command.substring(5));
			else if(command.startsWith("Teleport"))
			{
				int cmdChoice = Integer.parseInt(command.substring(9, 10).trim());
				TeleportLocation[] list = getTemplate().getTeleportList(cmdChoice);
				if(list != null)
					showTeleportList(player, list);
				else
					player.sendMessage("ĐˇŃ�Ń‹Đ»ĐşĐ° Đ˝ĐµĐ¸Ń�ĐżŃ€Đ°Đ˛Đ˝Đ°, Ń�ĐľĐľĐ±Ń‰Đ¸Ń‚Đµ Đ°Đ´ĐĽĐ¸Đ˝Đ¸Ń�Ń‚Ń€Đ°Ń‚ĐľŃ€Ń�.");
			}
			else if(command.startsWith("Tele20Lvl"))
			{
				int cmdChoice = Integer.parseInt(command.substring(10, 11).trim());
				TeleportLocation[] list = getTemplate().getTeleportList(cmdChoice);
				if(player.getLevel() > 20)
					showChatWindow(player, "teleporter/" + getNpcId() + "-no.htm");
				else if(list != null)
					showTeleportList(player, list);
				else
					player.sendMessage("ĐˇŃ�Ń‹Đ»ĐşĐ° Đ˝ĐµĐ¸Ń�ĐżŃ€Đ°Đ˛Đ˝Đ°, Ń�ĐľĐľĐ±Ń‰Đ¸Ń‚Đµ Đ°Đ´ĐĽĐ¸Đ˝Đ¸Ń�Ń‚Ń€Đ°Ń‚ĐľŃ€Ń�.");
			}
			else if(command.startsWith("open_gate"))
			{
				int val = Integer.parseInt(command.substring(10));
				ReflectionUtils.getDoor(val).openMe();
				player.sendActionFailed();
			}
			else if(command.equalsIgnoreCase("TransferSkillList"))
				showTransferSkillList(player);
			else if(command.equalsIgnoreCase("CertificationCancel"))
				CertificationFunctions.cancelCertification(this, player);
			else if(command.startsWith("RemoveTransferSkill"))
			{
				AcquireType type = AcquireType.transferType(player.getActiveClassClassId().getId());
				if(type == null)
					return;

				Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(null, type);
				if(skills.isEmpty())
				{
					player.sendActionFailed();
					return;
				}
				
				if(player.getFirstClassId() != ClassId.cardinal.getId() && player.getFirstClassId() != ClassId.evaSaint.getId() && player.getFirstClassId() != ClassId.shillienSaint.getId())
				{
					player.sendMessage("Class that gained Pomander skill needs to be your primary!");
					return;
				}

				boolean reset = false;
				for(SkillLearn skill : skills)
					if(player.getKnownSkill(skill.getId()) != null)
					{
						reset = true;
						break;
					}

				if(!reset)
				{
					player.sendActionFailed();
					return;
				}

				if(!player.reduceAdena(10000000L, true))
				{
					showChatWindow(player, "common/skill_share_healer_no_adena.htm");
					return;
				}

				for(SkillLearn skill : skills)
					if(player.removeSkill(SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel()), false) != null)
					{
						ItemFunctions.addItem(player, skill.getItemId(), skill.getItemCount(), true);
						player.removeSkillFromDB(SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel()), player.getFirstClassId());
					}
			}
			else if(command.startsWith("ExitFromQuestInstance"))
			{
				Reflection r = player.getReflection();
				r.startCollapseTimer(60000);
				player.teleToLocation(r.getReturnLoc(), 0);
				if(command.length() > 22)
					try
					{
						int val = Integer.parseInt(command.substring(22));
						showChatWindow(player, val);
					}
					catch(NumberFormatException nfe)
					{
						String filename = command.substring(22).trim();
						if(filename.length() > 0)
							showChatWindow(player, filename);
					}
			}
		}
		catch(StringIndexOutOfBoundsException sioobe)
		{
			_log.info("Incorrect htm bypass! npcId=" + getTemplate().npcId + " command=[" + command + "]");
		}
		catch(NumberFormatException nfe)
		{
			_log.info("Invalid bypass to Server command parameter! npcId=" + getTemplate().npcId + " command=[" + command + "]");
		}
	}

	public void showTeleportList(Player player, TeleportLocation[] list)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("&$556;").append("<br><br>");

		if(list != null && player.getPlayerAccess().UseTeleport)
		{
			for(TeleportLocation tl : list)
				if(tl.getItem().getItemId() == ItemTemplate.ITEM_ID_ADENA)
				{
					double pricemod = player.getLevel() <= Config.GATEKEEPER_FREE ? 0. : Config.GATEKEEPER_MODIFIER;
					if(tl.getPrice() > 0 && pricemod > 0)
					{
						//On Saturdays and Sundays from 8 PM to 12 AM, gatekeeper teleport fees decrease by 50%.
						Calendar calendar = Calendar.getInstance();
						int day = calendar.get(Calendar.DAY_OF_WEEK);
						int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
						if((day == Calendar.SUNDAY || day == Calendar.SATURDAY) && (hour >= 20 && hour <= 12))
							pricemod /= 2;
					}
					sb.append("[scripts_Util:Gatekeeper ").append(tl.getX()).append(" ").append(tl.getY()).append(" ").append(tl.getZ());
					if(tl.getCastleId() != 0)
						sb.append(" ").append(tl.getCastleId());
					sb.append(" ").append((long) (tl.getPrice() * pricemod)).append(" @811;F;").append(tl.getName()).append("|").append(HtmlUtils.htmlNpcString(tl.getName()));
					if(tl.getPrice() * pricemod > 0)
						sb.append(" - ").append((long) (tl.getPrice() * pricemod)).append(" ").append(HtmlUtils.htmlItemName(ItemTemplate.ITEM_ID_ADENA));
					sb.append("]<br1>\n");
				}
				else
					sb.append("[scripts_Util:QuestGatekeeper ").append(tl.getX()).append(" ").append(tl.getY()).append(" ").append(tl.getZ()).append(" ").append(tl.getPrice()).append(" ").append(tl.getItem().getItemId()).append(" @811;F;").append("|").append(HtmlUtils.htmlNpcString(tl.getName())).append(" - ").append(tl.getPrice()).append(" ").append(HtmlUtils.htmlItemName(tl.getItem().getItemId())).append("]<br1>\n");
		}
		else
			sb.append("No teleports available for you.");

		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setHtml(Strings.bbParse(sb.toString()));
		player.sendPacket(html);
	}

	public void showQuestWindow(Player player)
	{
		// collect awaiting quests and start points
		List<Quest> options = new ArrayList<Quest>();

		List<QuestState> awaits = player.getQuestsForEvent(this, QuestEventType.QUEST_TALK);
		Quest[] starts = getTemplate().getEventQuests(QuestEventType.QUEST_START);

		if(awaits != null)
			for(QuestState x : awaits)
				if(!options.contains(x.getQuest()))
					if(x.getQuest().getQuestIntId() > 0)
						options.add(x.getQuest());

		if(starts != null)
			for(Quest x : starts)
				if(!options.contains(x))
					if(x.getQuestIntId() > 0)
						options.add(x);

		// Display a QuestChooseWindow (if several quests are available) or QuestWindow
		if(options.size() > 1)
			showQuestChooseWindow(player, options.toArray(new Quest[options.size()]));
		else if(options.size() == 1)
			showQuestWindow(player, options.get(0).getName());
		else
			showQuestWindow(player, "");
	}

	public void showQuestChooseWindow(Player player, Quest[] quests)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("<html><body><title>Talk about:</title><br>");

		for(Quest q : quests)
		{
			if(!q.isVisible())
				continue;

			sb.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Quest ").append(q.getName()).append("\">[").append(q.getDescr(player)).append("]</a><br>");
		}

		sb.append("</body></html>");

		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}

	public void showChatWindow(Player player, int val, Object... replace)
	{
		if(getTemplate().getTeleportList().size() > 0 && checkForDominionWard(player))
			return;

		String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH;
		int npcId = getNpcId();
		switch(npcId)
		{
			case 31111: // Gatekeeper Spirit (Disciples)
				int sealAvariceOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE);
				int playerCabal = SevenSigns.getInstance().getPlayerCabal(player);
				int compWinner = SevenSigns.getInstance().getCabalHighestScore();
				if(playerCabal == sealAvariceOwner && playerCabal == compWinner)
					switch(sealAvariceOwner)
					{
						case SevenSigns.CABAL_DAWN:
							filename += "spirit_dawn.htm";
							break;
						case SevenSigns.CABAL_DUSK:
							filename += "spirit_dusk.htm";
							break;
						case SevenSigns.CABAL_NULL:
							filename += "spirit_null.htm";
							break;
					}
				else
					filename += "spirit_null.htm";
				break;
			case 31112: // Gatekeeper Spirit (Disciples)
				filename += "spirit_exit.htm";
				break;
			case 30298:
				if(player.getPledgeType() == Clan.SUBUNIT_ACADEMY)
					filename = getHtmlPath(npcId, 1, player);
				else
					filename = getHtmlPath(npcId, 0, player);
				break;
			default:
				if(npcId >= 31093 && npcId <= 31094 || npcId >= 31172 && npcId <= 31201 || npcId >= 31239 && npcId <= 31254)
					return;
				// Get the text of the selected HTML file in function of the npcId and of the page number
				filename = getHtmlPath(npcId, val, player);
				break;
		}

		NpcHtmlMessage packet = new NpcHtmlMessage(player, this, filename, val);
		if(replace.length % 2 == 0)
			for(int i = 0; i < replace.length; i+= 2)
				packet.replace(String.valueOf(replace[i]), String.valueOf(replace[i + 1]));
		player.sendPacket(packet);
	}

	public void showChatWindow(Player player, String filename, Object... replace)
	{
		NpcHtmlMessage packet = new NpcHtmlMessage(player, this, filename, 0);
		if(replace.length % 2 == 0)
			for(int i = 0; i < replace.length; i+= 2)
				packet.replace(String.valueOf(replace[i]), String.valueOf(replace[i + 1]));
		player.sendPacket(packet);
	}

	public String getHtmlPath(int npcId, int val, Player player)
	{
		String pom;
		if(val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;

 		if(getTemplate().getHtmRoot() != null)
			return getTemplate().getHtmRoot() + pom + ".htm";

		String temp = "default/" + pom + ".htm";
		if(HtmCache.getInstance().getNullable(temp, player) != null)
			return temp;

		temp = "trainer/" + pom + ".htm";
		if(HtmCache.getInstance().getNullable(temp, player) != null)
			return temp;

		// If the file is not found, the standard message "I have nothing to say to you" is returned
		return "npcdefault.htm";
	}

	private boolean _isBusy;
	private String _busyMessage = "";

	public final boolean isBusy()
	{
		return _isBusy;
	}

	public void setBusy(boolean isBusy)
	{
		_isBusy = isBusy;
	}

	public final String getBusyMessage()
	{
		return _busyMessage;
	}

	public void setBusyMessage(String message)
	{
		_busyMessage = message;
	}

	public void showBusyWindow(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(player, this);
		html.setFile("npcbusy.htm");
		html.replace("%npcname%", getName());
		html.replace("%playername%", player.getName());
		html.replace("%busymessage%", _busyMessage);
		player.sendPacket(html);
	}

	public void showSkillList(Player player)
	{
		ClassId classId = player.getActiveClassClassId();

		if(classId == null)
			return;

		int npcId = getTemplate().npcId;

		if(getTemplate().getTeachInfo().isEmpty())
		{
			NpcHtmlMessage html = new NpcHtmlMessage(player, this);
			StringBuilder sb = new StringBuilder();
			sb.append("<html><head><body>");
			sb.append("I cannot teach you. My class list is empty.<br> Ask admin to fix it. <br>NpcId:" + npcId + ", Your classId:" + classId.getId() + "<br>");
			sb.append("</body></html>");
			html.setHtml(sb.toString());
			player.sendPacket(html);

			return;
		}

		if(!(getTemplate().canTeach(classId) || getTemplate().canTeach(classId.getParent(player.getSex()))))
		{
			if(this instanceof WarehouseInstance)
				showChatWindow(player, "warehouse/" + getNpcId() + "-noteach.htm");
			else if(this instanceof TrainerInstance)
				showChatWindow(player, "trainer/" + getNpcId() + "-noteach.htm");
			else
			{
				NpcHtmlMessage html = new NpcHtmlMessage(player, this);
				StringBuilder sb = new StringBuilder();
				sb.append("<html><head><body>");
				sb.append(new CustomMessage("l2ft.gameserver.model.instances.L2NpcInstance.WrongTeacherClass", player));
				sb.append("</body></html>");
				html.setHtml(sb.toString());
				player.sendPacket(html);
			}
			return;
		}

		final Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(player, AcquireType.NORMAL);

		final AcquireSkillList asl = new AcquireSkillList(AcquireType.NORMAL, skills.size());
		int counts = 0;

		for(SkillLearn s : skills)
		{
			if(s.isClicked())
				continue;

			Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
			if(sk == null || !player.canLearn(sk) || !sk.canTeachBy(npcId))
					continue;

			counts++;

			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), s.getCost(), 0);
		}

		if(counts == 0)
		{
			int minlevel = SkillAcquireHolder.getInstance().getMinLevelForNewSkill(player, AcquireType.NORMAL);

			if(minlevel > 0)
			{
				SystemMessage2 sm = new SystemMessage2(SystemMsg.YOU_DO_NOT_HAVE_ANY_FURTHER_SKILLS_TO_LEARN__COME_BACK_WHEN_YOU_HAVE_REACHED_LEVEL_S1);
				sm.addInteger(minlevel);
				player.sendPacket(sm);
			}
			else
				player.sendPacket(SystemMsg.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
			player.sendPacket(AcquireSkillDone.STATIC);
		}
		else
			player.sendPacket(asl);

		player.sendActionFailed();
	}

	public void showTransferSkillList(Player player)
	{
		ClassId classId = player.getActiveClassClassId();
		if(classId == null)
			return;

		if(player.getLevel() < 76 || classId.getLevel() < 4)
		{
			NpcHtmlMessage html = new NpcHtmlMessage(player, this);
			StringBuilder sb = new StringBuilder();
			sb.append("<html><head><body>");
			sb.append("You must have 3rd class change quest completed.");
			sb.append("</body></html>");
			html.setHtml(sb.toString());
			player.sendPacket(html);
			return;
		}

		AcquireType type = AcquireType.transferType(player.getActiveClassClassId().getId());
		if(type == null)
		{
			player.sendMessage("Your Primary class needs to be Cardinal, Eva's Saint or Shillien Saint!");
			return;
		}

		showAcquireList(type, player);
	}

	public static void showCollectionSkillList(Player player)
	{
		showAcquireList(AcquireType.COLLECTION, player);
	}

	public void showTransformationMultisell(Player player)
	{
		if(!Config.ALLOW_LEARN_TRANS_SKILLS_WO_QUEST)
			if(!player.isQuestCompleted("_136_MoreThanMeetsTheEye"))
			{
				showChatWindow(player, "trainer/" + getNpcId() + "-nobuy.htm");
				return;
			}

		Castle castle = getCastle(player);
		MultiSellHolder.getInstance().SeparateAndSend(32323, player, castle != null ? castle.getTaxRate() : 0);
		player.sendActionFailed();
	}

	public void showTransformationSkillList(Player player, AcquireType type)
	{
		showAcquireList(type, player);
	}

	public static void showFishingSkillList(Player player)
	{
		showAcquireList(AcquireType.FISHING, player);
	}

	public static void showClanSkillList(Player player)
	{
		if(player.getClan() == null || !player.isClanLeader())
		{
			player.sendPacket(SystemMsg.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			player.sendActionFailed();
			return;
		}

		showAcquireList(AcquireType.CLAN, player);
	}

	public static void showAcquireList(AcquireType t, Player player)
	{
		final Collection<SkillLearn> skills = SkillAcquireHolder.getInstance().getAvailableSkills(player, t);

		final AcquireSkillList asl = new AcquireSkillList(t, skills.size());

		for(SkillLearn s : skills)
			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), s.getCost(), 0);

		if(skills.size() == 0)
		{
			player.sendPacket(AcquireSkillDone.STATIC);
			player.sendPacket(SystemMsg.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
		}
		else
			player.sendPacket(asl);

		player.sendActionFailed();
	}

	public static void showSubUnitSkillList(Player player)
	{
		Clan clan = player.getClan();
		if(clan == null)
			return;

		if((player.getClanPrivileges() & Clan.CP_CL_TROOPS_FAME) != Clan.CP_CL_TROOPS_FAME)
		{
			player.sendPacket(SystemMsg.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}

		Set<SkillLearn> learns = new TreeSet<SkillLearn>();
		for(SubUnit sub : player.getClan().getAllSubUnits())
			learns.addAll(SkillAcquireHolder.getInstance().getAvailableSkills(player, AcquireType.SUB_UNIT, sub));

		final AcquireSkillList asl = new AcquireSkillList(AcquireType.SUB_UNIT, learns.size());

		for(SkillLearn s : learns)
			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), s.getCost(), 1, Clan.SUBUNIT_KNIGHT4);

		if(learns.size() == 0)
		{
			player.sendPacket(AcquireSkillDone.STATIC);
			player.sendPacket(SystemMsg.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
		}
		else
			player.sendPacket(asl);

		player.sendActionFailed();
	}
	/**
	 * ĐťŃ�Đ¶Đ˝Đľ Đ´Đ»ŃŹ ĐľŃ‚ĐľĐ±Ń€Đ°Đ¶ĐµĐ˝Đ¸ŃŹ Đ°Đ˝Đ¸ĐĽĐ°Ń†Đ¸Đ¸ Ń�ĐżĐ°Ń�Đ˝Đ°, Đ¸Ń�ĐżĐľĐ»ŃŚĐ·Ń�ĐµŃ‚Ń�ŃŹ Đ˛ ĐżĐ°ĐşĐµŃ‚Đµ NpcInfo:
	 * 0=false, 1=true, 2=summoned (only works if model has a summon animation)
	 **/
	public int getSpawnAnimation()
	{
		return _spawnAnimation;
	}

	@Override
	public double getColRadius()
	{
		return getCollisionRadius();
	}

	@Override
	public double getColHeight()
	{
		return getCollisionHeight();
	}

	public int calculateLevelDiffForDrop(int charLevel)
	{
		if(!Config.DEEPBLUE_DROP_RULES)
			return 0;

		int mobLevel = getLevel();
		// According to official data (Prima), deep blue mobs are 9 or more levels below players
		int deepblue_maxdiff = this instanceof RaidBossInstance ? Config.DEEPBLUE_DROP_RAID_MAXDIFF : Config.DEEPBLUE_DROP_MAXDIFF;

		return Math.max(charLevel - mobLevel - deepblue_maxdiff, 0);
	}

	public boolean isSevenSignsMonster()
	{
		return getFaction().getName().equalsIgnoreCase("c_dungeon_clan");
	}

	@Override
	public String toString()
	{
		return getNpcId() + " " + getName();
	}

	public void refreshID()
	{
		objectId = IdFactory.getInstance().getNextId();
		_storedId = GameObjectsStorage.refreshId(this);
	}

	private boolean _isUnderground = false;

	public void setUnderground(boolean b)
	{
		_isUnderground = b;
	}

	public boolean isUnderground()
	{
		return _isUnderground;
	}

	public boolean isTargetable()
	{
		return _isTargetable;
	}

	public void setTargetable(boolean value)
	{
		_isTargetable = value;
	}
	
	public boolean isQuestChatFirst()
	{
		return _isQuestChatFirst;
	}

	public void setQuestChatFirst(boolean value)
	{
		_isQuestChatFirst = value;
	}

	public boolean isShowName()
	{
		return _showName;
	}

	public void setShowName(boolean value)
	{
		_showName = value;
	}

	@Override
	public NpcListenerList getListeners()
	{
		if(listeners == null)
			synchronized (this)
			{
				if(listeners == null)
					listeners = new NpcListenerList(this);
			}

		return (NpcListenerList) listeners;
	}

	public <T extends NpcListener> boolean addListener(T listener)
	{
		return getListeners().add(listener);
	}

	public <T extends NpcListener> boolean removeListener(T listener)
	{
		return getListeners().remove(listener);
	}

	@Override
	public NpcStatsChangeRecorder getStatsRecorder()
	{
		if(_statsRecorder == null)
			synchronized (this)
			{
				if(_statsRecorder == null)
					_statsRecorder = new NpcStatsChangeRecorder(this);
			}

		return (NpcStatsChangeRecorder) _statsRecorder;
	}

	public void setNpcState(int stateId)
	{
		broadcastPacket(new ExChangeNpcState(getObjectId(), stateId));
		npcState = stateId;
	}

	public int getNpcState()
	{
		return npcState;
	}

	@Override
	public List<L2GameServerPacket> addPacketList(Player forPlayer, Creature dropper)
	{
		List<L2GameServerPacket> list = new ArrayList<L2GameServerPacket>(3);
		list.add(new NpcInfo(this, forPlayer));

		if(isInCombat())
			list.add(new AutoAttackStart(getObjectId()));

		if(isMoving || isFollow)
			list.add(movePacket());

		return list;
	}

	@Override
	public boolean isNpc()
	{
		return true;
	}

	@Override
	public int getGeoZ(Location loc)
	{
		if(isFlying() || isInWater() || isInBoat() || isBoat() || isDoor())
			return loc.z;
		if(isNpc())
		{
			if(_spawnRange instanceof Territory)
				return GeoEngine.getHeight(loc, getGeoIndex());
			return loc.z;
		}

		return super.getGeoZ(loc);
	}

	@Override
	public Clan getClan()
	{
		Dominion dominion = getDominion();
		if(dominion == null)
			return null;
		int lordObjectId = dominion.getLordObjectId();
		return lordObjectId == 0 ? null : dominion.getOwner();
	}

	public NpcString getNameNpcString()
	{
		return _nameNpcString;
	}

	public NpcString getTitleNpcString()
	{
		return _titleNpcString;
	}

	public void setNameNpcString(NpcString nameNpcString)
	{
		_nameNpcString = nameNpcString;
	}

	public void setTitleNpcString(NpcString titleNpcString)
	{
		_titleNpcString = titleNpcString;
	}

	public boolean isMerchantNpc()
	{
		return false;
	}

	public SpawnRange getSpawnRange()
	{
		return _spawnRange;
	}

	public void setSpawnRange(SpawnRange spawnRange)
	{
		_spawnRange = spawnRange;
	}

	public boolean checkForDominionWard(Player player)
	{
		ItemInstance item = getActiveWeaponInstance();
		if(item != null && item.getAttachment() instanceof TerritoryWardObject)
		{
			showChatWindow(player, "flagman.htm");
			return true;
		}
		return false;
	}

	public void setParameter(String str, Object val)
	{
		if(_parameters == StatsSet.EMPTY)
			_parameters = new StatsSet();

		_parameters.set(str, val);
	}

	public void setParameters(MultiValueSet<String> set)
	{
		if(set.isEmpty())
			return;

		if(_parameters == StatsSet.EMPTY)
			_parameters = new MultiValueSet<String>(set.size());

		_parameters.putAll(set);
	}

	public int getParameter(String str, int val)
	{
		return _parameters.getInteger(str, val);
	}

	public long getParameter(String str, long val)
	{
		return _parameters.getLong(str, val);
	}

	public boolean getParameter(String str, boolean val)
	{
		return _parameters.getBool(str, val);
	}

	public String getParameter(String str, String val)
	{
		return _parameters.getString(str, val);
	}

	public MultiValueSet<String> getParameters()
	{
		return _parameters;
	}

	@Override
	public boolean isInvul()
	{
		return true;
	}

	public boolean isHasChatWindow()
	{
		return _hasChatWindow;
	}

	public void setHasChatWindow(boolean hasChatWindow)
	{
		_hasChatWindow = hasChatWindow;
	}
}