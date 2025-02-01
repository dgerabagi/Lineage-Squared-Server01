//
// C:\l2sq\Pac Project\Java\l2ft\gameserver\model\instances\RaidBossInstance.java
//
package l2ft.gameserver.model.instances;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import l2ft.gameserver.model.*;
import l2ft.gameserver.model.base.Experience;
import l2ft.gameserver.model.entity.Hero;
import l2ft.gameserver.model.entity.HeroDiary;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.s2c.SystemMessage;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.NpcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic RaidBoss logic: spawns the boss, includes dynamic zone creation, etc.
 */
public class RaidBossInstance extends MonsterInstance {
	private static final Logger _log = LoggerFactory.getLogger(RaidBossInstance.class);
	private ScheduledFuture<?> minionMaintainTask;
	private static final int MINION_UNSPAWN_INTERVAL = 5000; // 5s

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
		return 120000; // 2 minutes
	}

	@Override
	public void notifyMinionDied(MinionInstance minion) {
		minionMaintainTask = ThreadPoolManager.getInstance().schedule(new MaintainKilledMinion(minion),
				getKilledInterval(minion));
		super.notifyMinionDied(minion);
	}

	private class MaintainKilledMinion extends RunnableImpl {
		private final MinionInstance _minion;

		public MaintainKilledMinion(MinionInstance m) {
			_minion = m;
		}

		@Override
		public void runImpl() {
			if (!isDead()) {
				_minion.refreshID();
				spawnMinion(_minion);
			}
		}
	}

	@Override
	protected void onDeath(Creature killer) {
		if (minionMaintainTask != null) {
			minionMaintainTask.cancel(false);
			minionMaintainTask = null;
		}
		int points = getTemplate().rewardRp;
		if (points > 0) {
			calcRaidPointsReward(points);
		}

		// ReflectionBosses handle re-spawn differently
		if (this instanceof ReflectionBossInstance) {
			super.onDeath(killer);
			return;
		}

		// Give diary entries, hero, clan rep, etc.
		if (killer.isPlayable() && getAggroList().getTopDamager() != null) {
			Player player = killer.getPlayer();
			if (player.isInParty()) {
				for (Player member : player.getParty().getPartyMembers()) {
					if (member.isNoble()) {
						Hero.getInstance().addHeroDiary(member.getObjectId(),
								HeroDiary.ACTION_RAID_KILLED, getNpcId());
					}
				}
				player.getParty().broadCast(Msg.CONGRATULATIONS_YOUR_RAID_WAS_SUCCESSFUL);
			} else {
				if (player.isNoble()) {
					Hero.getInstance().addHeroDiary(player.getObjectId(),
							HeroDiary.ACTION_RAID_KILLED, getNpcId());
				}
				player.sendPacket(Msg.CONGRATULATIONS_YOUR_RAID_WAS_SUCCESSFUL);
			}

			Quest q = QuestManager.getQuest(508);
			if (q != null) {
				String qn = q.getName();
				if (player.getClan() != null
						&& player.getClan().getLeader().isOnline()
						&& player.getClan().getLeader().getPlayer().getQuestState(qn) != null) {
					QuestState st = player.getClan().getLeader().getPlayer().getQuestState(qn);
					st.getQuest().onKill(this, st);
				}
			}

			// clan rep example
			int clanReputationToAdd = 0;
			switch (getNpcId()) {
				case 29001: // QA
				case 29006: // Core
				case 29014: // Orfen
					clanReputationToAdd = 1500;
					break;
				case 29022: // Zaken
				case 29176:
				case 29181:
				case 29103:
				case 29099:
					clanReputationToAdd = 2500;
					break;
				case 29179:
				case 29180:
				case 29047:
				case 29020:
				case 29118:
					clanReputationToAdd = 5000;
					break;
				case 29028:
				case 29019:
				case 29066:
				case 29067:
				case 29068:
					clanReputationToAdd = 10000;
					break;
				default:
					if (getLevel() >= 40 && getLevel() <= 49) {
						clanReputationToAdd = 75;
					} else if (getLevel() >= 50 && getLevel() <= 59) {
						clanReputationToAdd = 100;
					} else if (getLevel() >= 60 && getLevel() <= 75) {
						clanReputationToAdd = 125;
					} else if (getLevel() >= 76 && getLevel() <= 79) {
						clanReputationToAdd = 200;
					} else if (getLevel() > 80) {
						clanReputationToAdd = 300;
					}
					break;
			}
			Player topDmg = getAggroList().getTopDamager().getPlayer();
			if (topDmg != null) {
				if (topDmg.isInParty()) {
					int repPerMember = clanReputationToAdd / topDmg.getParty().getMemberCount();
					Map<Clan, Integer> repMap = new HashMap<Clan, Integer>();
					for (Player mem : topDmg.getParty().getPartyMembers()) {
						if (mem.getClan() != null) {
							if (!repMap.containsKey(mem.getClan())) {
								repMap.put(mem.getClan(), repPerMember);
							} else {
								int oldVal = repMap.get(mem.getClan());
								repMap.put(mem.getClan(), oldVal + repPerMember);
							}
						}
					}
					for (Entry<Clan, Integer> e : repMap.entrySet()) {
						e.getKey().incReputation(e.getValue(), true, "raid");
					}
				} else if (topDmg.getClan() != null) {
					topDmg.getClan().incReputation(clanReputationToAdd, true, "raid");
				}
			}
		}

		// Schedule minions for unspawn
		if (getMinionList().hasAliveMinions()) {
			ThreadPoolManager.getInstance().schedule(new RunnableImpl() {
				@Override
				public void runImpl() {
					if (isDead()) {
						getMinionList().unspawnMinions();
					}
				}
			}, getMinionUnspawnInterval());
		}

		// Example for Cabrio, Kernon, Golkonda, Hallate:
		int boxId = 0;
		switch (getNpcId()) {
			case 25035:
				boxId = 31027;
				break;
			case 25054:
				boxId = 31028;
				break;
			case 25126:
				boxId = 31029;
				break;
			case 25220:
				boxId = 31030;
				break;
		}
		if (boxId != 0) {
			NpcTemplate boxT = NpcHolder.getInstance().getTemplate(boxId);
			if (boxT != null) {
				NpcInstance box = new NpcInstance(IdFactory.getInstance().getNextId(), boxT);
				box.spawnMe(getLoc());
				box.setSpawnedLoc(getLoc());
				ThreadPoolManager.getInstance().schedule(new GameObjectTasks.DeleteTask(box), 60000);
			}
		}

		/**
		 * ------------------------------------------------------------------------
		 * FIX: Set a non-zero respawn time in the spawner BEFORE
		 * notifying the RaidBossSpawnManager.
		 * ------------------------------------------------------------------------
		 */
		if (getSpawn() != null) {
			// If the spawn's getRespawnDelay() is in seconds, then do:
			int nextRespawn = (int) (System.currentTimeMillis() / 1000L) + getSpawn().getRespawnDelay();
			getSpawn().setRespawnTime(nextRespawn);
		}

		// This updates the DB with spawner.getRespawnTime()
		RaidBossSpawnManager.getInstance().onBossStatusChange(getNpcId());

		super.onDeath(killer);
	}

	@Override
	protected void onSpawn() {
		super.onSpawn();
		// Resist Full Magic Attack
		addSkill(SkillTable.getInstance().getInfo(4045, 1));

		if (!isBoss() && getLevel() >= 76) {
			Announcements.getInstance().announceToAll(getName() + " Raid Boss just spawned!");
		}

		RaidBossSpawnManager.getInstance().onBossSpawned(this);

		_log.info("[RaidBossInstance] onSpawn => npcId=" + getNpcId()
				+ ", name=" + getName()
				+ ", loc=(" + getX() + "," + getY() + "," + getZ() + "), lvl=" + getLevel());

		// Create the dynamic zones, etc.
		RaidBossZoneCreator.createZonesForBoss(this);
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

	// Stub method
	private void calcRaidPointsReward(int points) {
		// do nothing or handle your logic for awarding raid points
	}
}
