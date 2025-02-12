package l2ft.gameserver.model.instances;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.Announcements;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.cache.Msg;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.idfactory.IdFactory;
import l2ft.gameserver.instancemanager.QuestManager;
import l2ft.gameserver.instancemanager.RaidBossSpawnManager;
import l2ft.gameserver.model.AggroList.HateInfo;
import l2ft.gameserver.model.CommandChannel;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.GameObjectTasks;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.HeroDiary;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class RaidBossInstance extends MonsterInstance {
	private ScheduledFuture<?> minionMaintainTask;

	private static final int MINION_UNSPAWN_INTERVAL = 5000; // time to unspawn minions when boss is dead, msec

	public RaidBossInstance(int objectId, NpcTemplate template) {
		super(objectId, template);
	}

	@Override
	public boolean isRaid() {
		return true;
	}

	protected int getMinionUnspawnInterval() {
		return MINION_UNSPAWN_INTERVAL;
	}

	protected int getKilledInterval(MinionInstance minion) {
		return 120000; // 2 minutes to respawn
	}

	@Override
	public void notifyMinionDied(MinionInstance minion) {
		minionMaintainTask = ThreadPoolManager.getInstance().schedule(new MaintainKilledMinion(minion),
				getKilledInterval(minion));
		super.notifyMinionDied(minion);
	}

	private class MaintainKilledMinion extends RunnableImpl {
		private final MinionInstance minion;

		public MaintainKilledMinion(MinionInstance minion) {
			this.minion = minion;
		}

		@Override
		public void runImpl() throws Exception {
			if (!isDead()) {
				minion.refreshID();
				spawnMinion(minion);
			}
		}
	}

	@Override
	protected void onDeath(Creature killer) {
		if (minionMaintainTask != null) {
			minionMaintainTask.cancel(false);
			minionMaintainTask = null;
		}

		final int points = getTemplate().rewardRp;
		if (points > 0)
			calcRaidPointsReward(points);

		if (this instanceof ReflectionBossInstance) {
			super.onDeath(killer);
			return;
		}

		if (killer.isPlayable() && getAggroList().getTopDamager() != null) {
			Player player = killer.getPlayer();
			if (player.isInParty()) {
				for (Player member : player.getParty().getPartyMembers())
					if (member.isNoble())
						Hero.getInstance().addHeroDiary(member.getObjectId(), HeroDiary.ACTION_RAID_KILLED, getNpcId());
				player.getParty().broadCast(Msg.CONGRATULATIONS_YOUR_RAID_WAS_SUCCESSFUL);
			} else {
				if (player.isNoble())
					Hero.getInstance().addHeroDiary(player.getObjectId(), HeroDiary.ACTION_RAID_KILLED, getNpcId());
				player.sendPacket(Msg.CONGRATULATIONS_YOUR_RAID_WAS_SUCCESSFUL);
			}

			Quest q = QuestManager.getQuest(508);
			if (q != null) {
				String qn = q.getName();
				if (player.getClan() != null && player.getClan().getLeader().isOnline()
						&& player.getClan().getLeader().getPlayer().getQuestState(qn) != null) {
					QuestState st = player.getClan().getLeader().getPlayer().getQuestState(qn);
					st.getQuest().onKill(this, st);
				}
			}

			int clanReputationToAdd = 0;
			if (getNpcId() == 29001)// Queen Ant
				clanReputationToAdd = 1500;
			else if (getNpcId() == 29006)// Core
				clanReputationToAdd = 1500;
			else if (getNpcId() == 29014)// Orfen
				clanReputationToAdd = 1500;
			else if (getNpcId() == 29022 || getNpcId() == 29176 || getNpcId() == 29181)// Zaken
				clanReputationToAdd = 2500;
			else if (getNpcId() == 29103 || getNpcId() == 29099)// Baylor
				clanReputationToAdd = 2500;
			else if (getNpcId() == 29179 || getNpcId() == 29180)// Freya
				clanReputationToAdd = 5000;
			else if (getNpcId() == 29047)// Halisha
				clanReputationToAdd = 5000;
			else if (getNpcId() == 29020)// Baium
				clanReputationToAdd = 5000;
			else if (getNpcId() == 29118)// Beleth
				clanReputationToAdd = 5000;
			else if (getNpcId() == 29028)// Valakas
				clanReputationToAdd = 10000;
			else if (getNpcId() == 29019 || getNpcId() == 29066 || getNpcId() == 29067 || getNpcId() == 29068)// Antharas
				clanReputationToAdd = 10000;
			else if (getLevel() >= 40 && getLevel() <= 49)
				clanReputationToAdd = 75;
			else if (getLevel() >= 50 && getLevel() <= 59)
				clanReputationToAdd = 100;
			else if (getLevel() >= 60 && getLevel() <= 75)
				clanReputationToAdd = 125;
			else if (getLevel() >= 76 && getLevel() <= 79)
				clanReputationToAdd = 200;
			else if (getLevel() > 80)
				clanReputationToAdd = 300;

			Player mostDamagePlayer = getAggroList().getTopDamager().getPlayer();

			if (mostDamagePlayer.isInParty()) {
				int reputationPerMember = Math
						.round(clanReputationToAdd / mostDamagePlayer.getParty().getMemberCount());
				Map<Clan, Integer> repPerClan = new HashMap<>();
				for (Player member : mostDamagePlayer.getParty().getPartyMembers()) {
					if (member.getClan() != null)
						if (repPerClan.containsKey(member.getClan()))
							repPerClan.put(member.getClan(), repPerClan.get(member.getClan()) + reputationPerMember);
						else
							repPerClan.put(member.getClan(), reputationPerMember);
				}
				for (Entry<Clan, Integer> clan : repPerClan.entrySet())
					clan.getKey().incReputation(clan.getValue(), true, "raid");
			} else if (mostDamagePlayer.getClan() != null)
				mostDamagePlayer.getClan().incReputation(clanReputationToAdd, true, "raid");

		}

		if (getMinionList().hasAliveMinions())
			ThreadPoolManager.getInstance().schedule(new RunnableImpl() {
				@Override
				public void runImpl() throws Exception {
					if (isDead())
						getMinionList().unspawnMinions();
				}
			}, getMinionUnspawnInterval());

		int boxId = 0;
		switch (getNpcId()) {
			case 25035: // Shilens Messenger Cabrio
				boxId = 31027;
				break;
			case 25054: // Demon Kernon
				boxId = 31028;
				break;
			case 25126: // Golkonda, the Longhorn General
				boxId = 31029;
				break;
			case 25220: // Death Lord Hallate
				boxId = 31030;
				break;
		}

		if (boxId != 0) {
			NpcTemplate boxTemplate = NpcHolder.getInstance().getTemplate(boxId);
			if (boxTemplate != null) {
				final NpcInstance box = new NpcInstance(IdFactory.getInstance().getNextId(), boxTemplate);
				box.spawnMe(getLoc());
				box.setSpawnedLoc(getLoc());

				ThreadPoolManager.getInstance().schedule(new GameObjectTasks.DeleteTask(box), 60000);
			}
		}
		RaidBossSpawnManager.getInstance().onBossStatusChange(getNpcId());

		super.onDeath(killer);
	}

	// FIXME [G1ta0] разобрать этот хлам
	@SuppressWarnings("unchecked")
	private void calcRaidPointsReward(int totalPoints) {
		// Object groupkey (L2Party/L2CommandChannel/L2Player) | [List<L2Player> group,
		// Long GroupDdamage]
		Map<Object, Object[]> participants = new HashMap<Object, Object[]>();
		double totalHp = getMaxHp();

		// Разбиваем игроков по группам. По возможности используем наибольшую из
		// доступных групп: Command Channel → Party → StandAlone (сам плюс пет :)
		for (HateInfo ai : getAggroList().getPlayableMap().values()) {
			Player player = ai.attacker.getPlayer();
			Object key = player.getParty() != null
					? player.getParty().getCommandChannel() != null ? player.getParty().getCommandChannel()
							: player.getParty()
					: player.getPlayer();
			Object[] info = participants.get(key);
			if (info == null) {
				info = new Object[] { new HashSet<Player>(), new Long(0) };
				participants.put(key, info);
			}

			// если это пати или командный канал то берем оттуда весь список участвующих,
			// даже тех кто не в аггролисте
			// дубликаты не страшны - это хашсет
			if (key instanceof CommandChannel) {
				for (Player p : ((CommandChannel) key))
					if (p.isInRangeZ(this, Config.ALT_PARTY_DISTRIBUTION_RANGE))
						((Set<Player>) info[0]).add(p);
			} else if (key instanceof Party) {
				for (Player p : ((Party) key).getPartyMembers())
					if (p.isInRangeZ(this, Config.ALT_PARTY_DISTRIBUTION_RANGE))
						((Set<Player>) info[0]).add(p);
			} else
				((Set<Player>) info[0]).add(player);

			info[1] = ((Long) info[1]).longValue() + ai.damage;
		}

		for (Object[] groupInfo : participants.values()) {
			Set<Player> players = (HashSet<Player>) groupInfo[0];
			// это та часть, которую игрок заслужил дамагом группы, но на нее может быть
			// наложен штраф от уровня игрока
			int perPlayer = (int) Math
					.round(totalPoints * ((Long) groupInfo[1]).longValue() / (totalHp * players.size()));
			for (Player player : players) {
				int playerReward = perPlayer;
				// применяем штраф если нужен
				playerReward = (int) Math.round(
						playerReward * Experience.penaltyModifier(calculateLevelDiffForDrop(player.getLevel()), 9));
				if (playerReward == 0)
					continue;
				player.sendPacket(
						new SystemMessage(SystemMessage.YOU_HAVE_EARNED_S1_RAID_POINTS).addNumber(playerReward));
				RaidBossSpawnManager.getInstance().addPoints(player.getObjectId(), getNpcId(), playerReward);
			}
		}

		RaidBossSpawnManager.getInstance().updatePointsDb();
		RaidBossSpawnManager.getInstance().calculateRanking();
	}

	@Override
	protected void onDecay() {
		super.onDecay();
		RaidBossSpawnManager.getInstance().onBossStatusChange(getNpcId());
	}

	@Override
	protected void onSpawn() {
		super.onSpawn();
		addSkill(SkillTable.getInstance().getInfo(4045, 1)); // Resist Full Magic Attack

		if (!isBoss() && getLevel() >= 76)
			Announcements.getInstance().announceToAll(getName() + " Raid Boss just spawned!");

		RaidBossSpawnManager.getInstance().onBossSpawned(this);
	}

	@Override
	public boolean isFearImmune() {
		return true;
	}

	@Override
	public boolean isParalyzeImmune() {
		return true;
	}

	@Override
	public boolean isLethalImmune() {
		return true;
	}

	@Override
	public boolean hasRandomWalk() {
		return false;
	}

	@Override
	public boolean canChampion() {
		return false;
	}
}