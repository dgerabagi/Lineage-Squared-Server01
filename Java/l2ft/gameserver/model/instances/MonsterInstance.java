package l2ft.gameserver.model.instances;

import gnu.trove.TIntHashSet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import l2ft.commons.dbutils.DbUtils;
import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.instancemanager.CursedWeaponsManager;
import l2ft.gameserver.instancemanager.RaidBossSpawnManager;
import l2ft.gameserver.model.AggroList.HateInfo;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Effect;
import l2ft.gameserver.model.Manor;
import l2ft.gameserver.model.MinionList;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Playable;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.Spawner;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.base.TeamType;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.instances.MinionInstance;
import l2ft.gameserver.model.instances.RaidBossInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestEventType;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.model.reward.RewardItem;
import l2ft.gameserver.model.reward.RewardList;
import l2ft.gameserver.model.reward.RewardType;
import l2ft.gameserver.network.l2.s2c.SocialAction;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.stats.Stats;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.Faction;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.Location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages normal monsters and normal (non-epic) raid bosses.
 * Logs kills into `raidboss_history` but no longer attempts to store
 * respawn_date.
 */
public class MonsterInstance extends NpcInstance {
	private static final Logger _log = LoggerFactory.getLogger(MonsterInstance.class);

	/**
	 * For distributing kills/drops among parties, we use an internal RewardInfo.
	 */
	protected static final class RewardInfo {
		protected Creature _attacker;
		protected int _dmg;

		public RewardInfo(Creature attacker, int dmg) {
			_attacker = attacker;
			_dmg = dmg;
		}

		public void addDamage(int dmg) {
			if (dmg < 0)
				dmg = 0;
			_dmg += dmg;
		}

		@Override
		public int hashCode() {
			return _attacker.getObjectId();
		}
	}

	/**
	 * Scheduled task for minion maintenance
	 */
	private ScheduledFuture<?> minionMaintainTask;
	private MinionList minionList;

	/** Crop / seed data */
	private boolean _isSeeded;
	private int _seederId;
	private boolean _altSeed;
	private RewardItem _harvestItem;
	private final Lock harvestLock = new ReentrantLock();

	/** Overhit data */
	private int overhitAttackerId;
	private double _overhitDamage;

	/** Soul absorber data */
	private TIntHashSet _absorbersIds;
	private final Lock absorbLock = new ReentrantLock();

	/** Spoil data */
	private boolean _isSpoiled;
	private int spoilerId;
	private List<RewardItem> _sweepItems;
	private final Lock sweepLock = new ReentrantLock();

	/** Champion data */
	private int _isChampion;
	private long _lastSocialAction = 0L;

	/**
	 * Some custom blacklisted RaidBoss IDs
	 */
	private static final int[] MONSTERS_THAT_PAC_DONT_WANT_TO_DROP_ITEMS = {
			25718, 25719, 25720, 25721, 25722, 25723,
			25724, 25725, 25726, 25727, 25732
	};

	public MonsterInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
		minionList = new MinionList(this);
	}

	@Override
	public boolean isMovementDisabled() {
		// Some special mobs (e.g. 18344, 18345) do not move
		if (getNpcId() == 18344 || getNpcId() == 18345) {
			return true;
		}
		return super.isMovementDisabled();
	}

	@Override
	public boolean isLethalImmune() {
		// Some champion or special NPC IDs are lethal-immune
		if (_isChampion > 0
				|| getNpcId() == 22215
				|| getNpcId() == 22216
				|| getNpcId() == 22217) {
			return true;
		}
		return super.isLethalImmune();
	}

	@Override
	public boolean isFearImmune() {
		return (_isChampion > 0) || super.isFearImmune();
	}

	@Override
	public boolean isParalyzeImmune() {
		return (_isChampion > 0) || super.isParalyzeImmune();
	}

	@Override
	public boolean isAutoAttackable(Creature attacker) {
		// Mobs are always auto-attackable by players
		return !attacker.isMonster();
	}

	/** Champion getters & setters **/
	public int getChampion() {
		return _isChampion;
	}

	public void setChampion() {
		if (getReflection().canChampions() && canChampion()) {
			double random = Rnd.nextDouble();
			double chance2 = Config.ALT_CHAMPION_CHANCE2 / 100.0; // highest champion
			double chance1 = (Config.ALT_CHAMPION_CHANCE1 + Config.ALT_CHAMPION_CHANCE2) / 100.0; // total for lvl1 or
																									// lvl2

			if (chance2 >= random)
				setChampion(2);
			else if (chance1 >= random)
				setChampion(1);
			else
				setChampion(0);
		} else {
			setChampion(0);
		}
	}

	public void setChampion(int level) {
		if (level == 0) {
			removeSkillById(4407);
			_isChampion = 0;
		} else {
			addSkill(SkillTable.getInstance().getInfo(4407, level));
			_isChampion = level;
		}
	}

	/**
	 * Returns true if this can be champion based on config and level
	 */
	public boolean canChampion() {
		return (getTemplate().rewardExp > 0
				&& getTemplate().level <= Config.ALT_CHAMPION_TOP_LEVEL
				&& getTemplate().level >= Config.ALT_CHAMPION_MIN_LEVEL);
	}

	@Override
	public TeamType getTeam() {
		// e.g. champion color-coded
		if (_isChampion == 2)
			return TeamType.RED;
		else if (_isChampion == 1)
			return TeamType.BLUE;
		return TeamType.NONE;
	}

	/**
	 * On spawn tasks
	 */
	@Override
	protected void onSpawn() {
		super.onSpawn();
		setCurrentHpMp(getMaxHp(), getMaxMp(), true);

		// If minion-based, schedule the minion spawn task
		if (getMinionList().hasMinions()) {
			if (minionMaintainTask != null) {
				minionMaintainTask.cancel(false);
				minionMaintainTask = null;
			}
			minionMaintainTask = ThreadPoolManager.getInstance().schedule(new MinionMaintainTask(), 1000L);
		}
	}

	@Override
	protected void onDespawn() {
		setOverhitDamage(0);
		setOverhitAttacker(null);
		clearSweep();
		clearHarvest();
		clearAbsorbers();
		super.onDespawn();
	}

	@Override
	public MinionList getMinionList() {
		return minionList;
	}

	public class MinionMaintainTask extends RunnableImpl {
		@Override
		public void runImpl() throws Exception {
			if (isDead())
				return;
			getMinionList().spawnMinions();
		}
	}

	public Location getMinionPosition() {
		return Location.findPointToStay(this, 100, 150);
	}

	public void notifyMinionDied(MinionInstance minion) {
		// no default logic
	}

	/**
	 * Summon a minion of me
	 */
	public void spawnMinion(MonsterInstance minion) {
		minion.setReflection(getReflection());
		if (getChampion() == 2)
			minion.setChampion(1);
		else
			minion.setChampion(0);
		minion.setHeading(getHeading());
		minion.setCurrentHpMp(minion.getMaxHp(), minion.getMaxMp(), true);
		minion.spawnMe(getMinionPosition());
	}

	@Override
	public boolean hasMinions() {
		return getMinionList().hasMinions();
	}

	@Override
	public void setReflection(Reflection reflection) {
		super.setReflection(reflection);
		if (hasMinions()) {
			for (MinionInstance m : getMinionList().getAliveMinions()) {
				m.setReflection(reflection);
			}
		}
	}

	@Override
	protected void onDelete() {
		// cancel any scheduled minion tasks
		if (minionMaintainTask != null) {
			minionMaintainTask.cancel(false);
			minionMaintainTask = null;
		}

		// schedule delayed minion cleanup
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				getMinionList().deleteMinions();
			}
		}, 30000);

		super.onDelete();
	}

	/**
	 * Called when monster is killed
	 */
	@Override
	protected void onDeath(Creature killer) {
		if (minionMaintainTask != null) {
			minionMaintainTask.cancel(false);
			minionMaintainTask = null;
		}

		// If it's a normal raid boss (not an epic), log to raidboss_history
		if (isRaid() && !isBoss()) {
			Player lastHitPlayer = (killer != null) ? killer.getPlayer() : null;
			if (lastHitPlayer != null) {
				insertUnifiedRaidBossHistory(lastHitPlayer);
			}
			// Let the manager store new DB data for next respawn, but not in
			// raidboss_history
			RaidBossSpawnManager.getInstance().onBossStatusChange(getNpcId());
		}

		calculateRewards(killer);
		super.onDeath(killer);
	}

	/**
	 * Insert into `raidboss_history` for non-epic raids, omitting respawn_date
	 */
	private void insertUnifiedRaidBossHistory(Player lastHitPlayer) {
		Connection con = null;
		PreparedStatement ps = null;

		long nowSec = System.currentTimeMillis() / 1000L;
		_log.info("[MonsterInstance] insertUnifiedRaidBossHistory: bossId=" + getNpcId()
				+ ", killer=" + lastHitPlayer.getName() + ", timeNowSec=" + nowSec);

		try {
			con = DatabaseFactory.getInstance().getConnection();

			int charId = lastHitPlayer.getObjectId();
			String charName = lastHitPlayer.getName();
			String clanName = "";
			String allyName = "";
			if (lastHitPlayer.getClan() != null) {
				clanName = lastHitPlayer.getClan().getName();
				if (lastHitPlayer.getClan().getAlliance() != null) {
					allyName = lastHitPlayer.getClan().getAlliance().getAllyName();
				}
			}

			// Insert row without respawn_date
			ps = con.prepareStatement(
					"INSERT INTO raidboss_history ("
							+ "boss_id, boss_name, kill_timestamp, "
							+ "last_hit_char_id, last_hit_char_name, "
							+ "last_hit_char_clan_name, last_hit_char_ally_name"
							+ ") VALUES (?,?,?,?,?,?,?)");
			ps.setInt(1, getNpcId());
			ps.setString(2, getName());
			ps.setLong(3, nowSec);
			ps.setInt(4, charId);
			ps.setString(5, charName);
			ps.setString(6, clanName);
			ps.setString(7, allyName);

			ps.executeUpdate();

			_log.info("[MonsterInstance] Inserted normal raid kill for bossId=" + getNpcId()
					+ " (no respawn_date logged).");
		} catch (SQLException e) {
			_log.warn("[MonsterInstance] Failed to insert into raidboss_history for bossId="
					+ getNpcId() + ", charId=" + lastHitPlayer.getObjectId(), e);
		} finally {
			DbUtils.closeQuietly(con, ps);
		}
	}

	/**
	 * Over-hit logic
	 */
	@Override
	protected void onReduceCurrentHp(
			double damage, Creature attacker, Skill skill,
			boolean awake, boolean standUp, boolean directHp) {
		if (skill != null && skill.isOverhit()) {
			double overhitDmg = (getCurrentHp() - damage) * -1;
			if (overhitDmg <= 0) {
				setOverhitDamage(0);
				setOverhitAttacker(null);
			} else {
				setOverhitDamage(overhitDmg);
				setOverhitAttacker(attacker);
			}
		}
		super.onReduceCurrentHp(damage, attacker, skill, awake, standUp, directHp);
	}

	/**
	 * Distribute XP, SP, items
	 */
	public void calculateRewards(Creature lastAttacker) {
		Creature topDamager = getAggroList().getTopDamager();
		if (lastAttacker == null || !lastAttacker.isPlayable()) {
			lastAttacker = topDamager;
		}
		if (lastAttacker == null || !lastAttacker.isPlayable()) {
			return;
		}

		Player killer = lastAttacker.getPlayer();
		if (killer == null) {
			return;
		}

		Map<Playable, HateInfo> aggroMap = getAggroList().getPlayableMap();
		Quest[] quests = getTemplate().getEventQuests(QuestEventType.MOB_KILLED_WITH_QUEST);

		// Quest logic
		if (quests != null && quests.length > 0) {
			List<Player> players = null;
			if (isRaid() && Config.ALT_NO_LASTHIT) {
				players = new ArrayList<Player>();
				for (Playable pl : aggroMap.keySet()) {
					if (!pl.isDead()
							&& (isInRangeZ(pl, Config.ALT_PARTY_DISTRIBUTION_RANGE)
									|| killer.isInRangeZ(pl, Config.ALT_PARTY_DISTRIBUTION_RANGE))) {
						Player p2 = pl.getPlayer();
						if (!players.contains(p2)) {
							players.add(p2);
						}
					}
				}
			} else if (killer.getParty() != null) {
				players = new ArrayList<Player>(killer.getParty().getMemberCount());
				for (Player pl : killer.getParty().getPartyMembers()) {
					if (!pl.isDead()
							&& (isInRangeZ(pl, Config.ALT_PARTY_DISTRIBUTION_RANGE)
									|| killer.isInRangeZ(pl, Config.ALT_PARTY_DISTRIBUTION_RANGE))) {
						players.add(pl);
					}
				}
			}

			for (Quest quest : quests) {
				Player toReward = killer;
				if (quest.getParty() != Quest.PARTY_NONE && players != null) {
					if (isRaid() || quest.getParty() == Quest.PARTY_ALL) {
						for (Player pl : players) {
							QuestState qs = pl.getQuestState(quest.getName());
							if (qs != null && !qs.isCompleted()) {
								quest.notifyKill(this, qs);
							}
						}
						toReward = null;
					} else {
						// choose one random among players who have the quest
						List<Player> interested = new ArrayList<Player>(players.size());
						for (Player pl : players) {
							QuestState qs = pl.getQuestState(quest.getName());
							if (qs != null && !qs.isCompleted()) {
								interested.add(pl);
							}
						}
						if (!interested.isEmpty()) {
							toReward = interested.get(Rnd.get(interested.size()));
						}
						if (toReward == null) {
							toReward = killer;
						}
					}
				}
				if (toReward != null) {
					QuestState qs = toReward.getQuestState(quest.getName());
					if (qs != null && !qs.isCompleted()) {
						quest.notifyKill(this, qs);
					}
				}
			}
		}

		// Build table of attacker -> damage
		Map<Player, RewardInfo> rewards = new HashMap<Player, RewardInfo>();
		for (HateInfo info : aggroMap.values()) {
			if (info.damage <= 1)
				continue;
			Playable attacker = (Playable) info.attacker;
			Player player = attacker.getPlayer();
			RewardInfo reward = rewards.get(player);
			if (reward == null) {
				reward = new RewardInfo(player, info.damage);
				rewards.put(player, reward);
			} else {
				reward.addDamage(info.damage);
			}
		}

		// Example: Halloween event drops
		if (Config.EVENT_HALLOWEEN_ENABLED) {
			boolean earnedHalloweenItem = false;
			if (Rnd.chance(0.1)) {
				killer.getInventory().addItem(20709, 1);
				earnedHalloweenItem = true;
			}
			if (Rnd.chance(0.1)) {
				killer.getInventory().addItem(20707, 1);
				earnedHalloweenItem = true;
			}
			if (Rnd.chance(3)) {
				killer.getInventory().addItem(15430, 1);
				earnedHalloweenItem = true;
			}
			if (Rnd.chance(1)) {
				killer.getInventory().addItem(20706, 1);
				earnedHalloweenItem = true;
			}
			if (earnedHalloweenItem) {
				killer.sendMessage("You have earned an item from the Halloween Event!");
			}
		}

		Player[] attackers = rewards.keySet().toArray(new Player[rewards.size()]);
		double[] xpsp = new double[2];
		int maxHp = getMaxHp();

		// Distribute XP & SP
		for (Player attacker : attackers) {
			if (attacker.isDead()) {
				continue;
			}
			RewardInfo reward = rewards.get(attacker);
			if (reward == null) {
				continue;
			}

			Party party = attacker.getParty();
			xpsp[0] = 0.;
			xpsp[1] = 0.;

			if (party == null) {
				int damage = Math.min(reward._dmg, maxHp);
				if (damage > 0) {
					if (isInRangeZ(attacker, Config.ALT_PARTY_DISTRIBUTION_RANGE)) {
						xpsp = calculateExpAndSp(attacker.getLevel(), damage);
					}
					xpsp[0] = applyOverhit(killer, xpsp[0]);
					attacker.addExpAndCheckBonus(this, (long) xpsp[0], (long) xpsp[1], 1.);
				}
				rewards.remove(attacker);
			} else {
				int partyDmg = 0;
				int partylevel = 1;
				List<Player> rewardedMembers = new ArrayList<Player>();
				for (Player partyMember : party.getPartyMembers()) {
					RewardInfo ai = rewards.remove(partyMember);
					if (partyMember.isDead()
							|| !isInRangeZ(partyMember, Config.ALT_PARTY_DISTRIBUTION_RANGE)) {
						continue;
					}
					if (ai != null) {
						partyDmg += ai._dmg;
					}
					rewardedMembers.add(partyMember);
					if (partyMember.getLevel() > partylevel) {
						partylevel = partyMember.getLevel();
					}
				}
				partyDmg = Math.min(partyDmg, maxHp);
				if (partyDmg > 0) {
					xpsp = calculateExpAndSp(partylevel, partyDmg);
					double partyMul = (double) partyDmg / maxHp;
					xpsp[0] *= partyMul;
					xpsp[1] *= partyMul;
					xpsp[0] = applyOverhit(killer, xpsp[0]);
					party.distributeXpAndSp(xpsp[0], xpsp[1], rewardedMembers, lastAttacker, this);
				}
			}
		}

		// Cursed weapons
		CursedWeaponsManager.getInstance().dropAttackable(this, killer);

		if (topDamager == null || !topDamager.isPlayable()) {
			return;
		}

		// Normal drop logic
		for (Map.Entry<RewardType, RewardList> entry : getTemplate().getRewards().entrySet()) {
			rollRewards(entry, lastAttacker, topDamager);
		}

		// If it's a normal raid (not epic), skip certain item drops if it's
		// blacklisted.
		if (isRaid() && !isBoss()) {
			for (int id : MONSTERS_THAT_PAC_DONT_WANT_TO_DROP_ITEMS) {
				if (getNpcId() == id) {
					return;
				}
			}
			// Example: chance-based reward
			if (getLevel() > 20 && Rnd.chance(getLevel())) {
				topDamager.getPlayer().getInventory().addItem(15632, 1);
				topDamager.getPlayer().sendMessage("Sub Class Reward has been added to your inventory!");
			}
		}
	}

	/**
	 * Calculate portion of XP/SP
	 */
	private double[] calculateExpAndSp(int level, long damage) {
		int diff = level - getLevel();
		// Kamael penalty if difference is 4 or 5 for lv > 77
		if (level > 77 && diff > 3 && diff <= 5) {
			diff += 3;
		}

		double xp = getExpReward() * damage / getMaxHp();
		double sp = getSpReward() * damage / getMaxHp();

		if (diff > 5) {
			double mod = Math.pow(0.83, diff - 5);
			xp *= mod;
			sp *= mod;
		}
		xp = Math.max(0., xp);
		sp = Math.max(0., sp);

		return new double[] { xp, sp };
	}

	/**
	 * Over-hit XP bonus
	 */
	private double applyOverhit(Player killer, double xpValue) {
		if (xpValue > 0 && killer.getObjectId() == overhitAttackerId) {
			int overHitExp = calculateOverhitExp(xpValue);
			killer.sendPacket(
					Msg.OVER_HIT,
					new SystemMessage(SystemMessage.ACQUIRED_S1_BONUS_EXPERIENCE_THROUGH_OVER_HIT)
							.addNumber(overHitExp));
			xpValue += overHitExp;
		}
		return xpValue;
	}

	@Override
	public void setOverhitAttacker(Creature attacker) {
		overhitAttackerId = (attacker == null) ? 0 : attacker.getObjectId();
	}

	public double getOverhitDamage() {
		return _overhitDamage;
	}

	@Override
	public void setOverhitDamage(double damage) {
		_overhitDamage = damage;
	}

	public int calculateOverhitExp(double normalExp) {
		double overhitPercentage = (getOverhitDamage() * 100.0) / getMaxHp();
		if (overhitPercentage > 25.0) {
			overhitPercentage = 25.0;
		}
		double overhitExp = (overhitPercentage / 100.0) * normalExp;
		setOverhitAttacker(null);
		setOverhitDamage(0);
		return (int) Math.round(overhitExp);
	}

	@Override
	public boolean isAggressive() {
		// If it's a champion, let config decide if it can be aggro
		return (Config.ALT_CHAMPION_CAN_BE_AGGRO || getChampion() == 0) && super.isAggressive();
	}

	@Override
	public Faction getFaction() {
		// If champion > 0 and config says no social
		if (!Config.ALT_CHAMPION_CAN_BE_SOCIAL && getChampion() > 0) {
			return Faction.NONE;
		}
		return super.getFaction();
	}

	/**
	 * reduceCurrentHp override to possibly cast Ultimate Defense if attacked from
	 * range
	 */
	@Override
	public void reduceCurrentHp(
			double damage,
			Creature attacker,
			Skill skill,
			boolean awake,
			boolean standUp,
			boolean directHp,
			boolean canReflect,
			boolean transferDamage,
			boolean isDot,
			boolean sendMessage) {
		checkUD(attacker, damage);
		super.reduceCurrentHp(
				damage, attacker, skill, awake, standUp, directHp,
				canReflect, transferDamage, isDot, sendMessage);
	}

	private final double MIN_DISTANCE_FOR_USE_UD = 200.0;
	private final double MIN_DISTANCE_FOR_CANCEL_UD = 50.0;
	private final double UD_USE_CHANCE = 30.0;

	/**
	 * If attacked from range, might cast UD skill #5044
	 */
	private void checkUD(Creature attacker, double damage) {
		if (getTemplate().baseAtkRange > MIN_DISTANCE_FOR_USE_UD
				|| getLevel() < 20
				|| getLevel() > 78
				|| (attacker.getLevel() - getLevel()) > 9
				|| (getLevel() - attacker.getLevel()) > 9) {
			return;
		}
		if (isMinion()
				|| getMinionList() != null
				|| isRaid()
				|| this instanceof ReflectionBossInstance
				|| this instanceof ChestInstance
				|| getChampion() > 0) {
			return;
		}

		int skillId = 5044; // "Ultimate Defense"
		int skillLvl = 1;
		if (getLevel() >= 41 && getLevel() <= 60) {
			skillLvl = 2;
		} else if (getLevel() > 60) {
			skillLvl = 3;
		}

		double distance = getDistance(attacker);

		// If it's too close, remove UD
		if (distance <= MIN_DISTANCE_FOR_CANCEL_UD) {
			if (getEffectList() != null && getEffectList().getEffectsBySkillId(skillId) != null) {
				for (Effect e : getEffectList().getEffectsBySkillId(skillId)) {
					e.exit();
				}
			}
		}
		// If it's far enough away, maybe cast UD
		else if (distance >= MIN_DISTANCE_FOR_USE_UD) {
			double chance = UD_USE_CHANCE / (getMaxHp() / damage);
			if (Rnd.chance(chance)) {
				Skill udSkill = SkillTable.getInstance().getInfo(skillId, skillLvl);
				if (udSkill != null) {
					udSkill.getEffects(this, this, false, false);
				}
			}
		}
	}

	@Override
	public boolean isMonster() {
		return true;
	}

	@Override
	public boolean isInvul() {
		return _isInvul;
	}

	// Random social action roughly every 10s
	@Override
	public void onRandomAnimation() {
		if (System.currentTimeMillis() - _lastSocialAction > 10000L) {
			broadcastPacket(new SocialAction(getObjectId(), 1));
			_lastSocialAction = System.currentTimeMillis();
		}
	}

	@Override
	public void startRandomAnimation() {
		// handled in AI
	}

	/**
	 * We do not add Karma
	 */
	@Override
	public int getKarma() {
		return 0;
	}

	/** Soul absorber logic */
	public void addAbsorber(Player attacker) {
		if (attacker == null)
			return;
		if (getCurrentHpPercents() > 50)
			return;

		absorbLock.lock();
		try {
			if (_absorbersIds == null)
				_absorbersIds = new TIntHashSet();
			_absorbersIds.add(attacker.getObjectId());
		} finally {
			absorbLock.unlock();
		}
	}

	public boolean isAbsorbed(Player player) {
		absorbLock.lock();
		try {
			if (_absorbersIds == null)
				return false;
			return _absorbersIds.contains(player.getObjectId());
		} finally {
			absorbLock.unlock();
		}
	}

	public void clearAbsorbers() {
		absorbLock.lock();
		try {
			if (_absorbersIds != null) {
				_absorbersIds.clear();
			}
		} finally {
			absorbLock.unlock();
		}
	}

	/** Harvest logic */
	public RewardItem takeHarvest() {
		harvestLock.lock();
		try {
			RewardItem harvest = _harvestItem;
			clearHarvest();
			return harvest;
		} finally {
			harvestLock.unlock();
		}
	}

	public void clearHarvest() {
		harvestLock.lock();
		try {
			_harvestItem = null;
			_altSeed = false;
			_seederId = 0;
			_isSeeded = false;
		} finally {
			harvestLock.unlock();
		}
	}

	public boolean setSeeded(Player player, int seedId, boolean altSeed) {
		harvestLock.lock();
		try {
			if (isSeeded())
				return false;
			_isSeeded = true;
			_altSeed = altSeed;
			_seederId = player.getObjectId();
			_harvestItem = new RewardItem(Manor.getInstance().getCropType(seedId));
			if (getTemplate().rateHp > 1) {
				_harvestItem.count = Rnd.get(
						(long) Math.round(getTemplate().rateHp),
						(long) Math.round(1.5 * getTemplate().rateHp));
			}
		} finally {
			harvestLock.unlock();
		}
		return true;
	}

	public boolean isSeeded(Player player) {
		// seed must match the player ID
		return (isSeeded()
				&& _seederId == player.getObjectId()
				&& getDeadTime() < 20000L);
	}

	public boolean isSeeded() {
		return _isSeeded;
	}

	/** Spoil / sweep logic */
	public boolean isSpoiled() {
		return _isSpoiled;
	}

	public boolean isSpoiled(Player player) {
		if (!isSpoiled())
			return false;
		if (player.getObjectId() == spoilerId && getDeadTime() < 20000L)
			return true;
		if (player.isInParty()) {
			for (Player pm : player.getParty().getPartyMembers()) {
				if (pm.getObjectId() == spoilerId
						&& getDistance(pm) < Config.ALT_PARTY_DISTRIBUTION_RANGE) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean setSpoiled(Player player) {
		sweepLock.lock();
		try {
			if (_isSpoiled)
				return false;
			_isSpoiled = true;
			spoilerId = player.getObjectId();
		} finally {
			sweepLock.unlock();
		}
		return true;
	}

	public boolean isSweepActive() {
		sweepLock.lock();
		try {
			return _sweepItems != null && !_sweepItems.isEmpty();
		} finally {
			sweepLock.unlock();
		}
	}

	public List<RewardItem> takeSweep() {
		sweepLock.lock();
		try {
			List<RewardItem> sweep = _sweepItems;
			clearSweep();
			return sweep;
		} finally {
			sweepLock.unlock();
		}
	}

	public void clearSweep() {
		sweepLock.lock();
		try {
			_isSpoiled = false;
			spoilerId = 0;
			_sweepItems = null;
		} finally {
			sweepLock.unlock();
		}
	}

	public void rollRewards(Map.Entry<RewardType, RewardList> entry, Creature lastAttacker, Creature topDamager) {
		RewardType type = entry.getKey();
		RewardList list = entry.getValue();

		// If it's SWEEP but not spoiled, skip
		if (type == RewardType.SWEEP && !isSpoiled())
			return;

		Creature activeChar = (type == RewardType.SWEEP) ? lastAttacker : topDamager;
		if (activeChar == null)
			return;
		Player activePlayer = activeChar.getPlayer();
		if (activePlayer == null)
			return;

		int diff = calculateLevelDiffForDrop(topDamager.getLevel());
		double mod = calcStat(Stats.REWARD_MULTIPLIER, 1.0, activeChar, null);
		mod *= Experience.penaltyModifier(diff, 9);

		// Roll the items
		List<RewardItem> rewardItems = list.roll(activePlayer, mod, this instanceof RaidBossInstance);

		switch (type) {
			case SWEEP:
				sweepLock.lock();
				try {
					_sweepItems = rewardItems;
				} finally {
					sweepLock.unlock();
				}
				break;
			default:
				for (RewardItem drop : rewardItems) {
					// If is seeded by normal seed => only Adena allowed
					if (isSeeded() && !_altSeed && !drop.isAdena) {
						continue;
					}
					dropItem(activePlayer, drop.itemId, drop.count);
				}
				break;
		}
	}
}
