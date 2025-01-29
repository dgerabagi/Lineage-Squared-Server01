package l2ft.gameserver.taskmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import l2ft.commons.threading.RunnableImpl;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.network.l2.s2c.ExAutoSoulShot;
import l2ft.gameserver.network.l2.s2c.ExUseSharedGroupItem;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.skills.TimeStamp;
import l2ft.gameserver.tables.SkillTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPotionsManager {
	public static final Logger _log = LoggerFactory.getLogger(AutoPotionsManager.class);
	private static AutoPotionsManager _instance;
	private static final int[][] HP_POTS = { { 1060, 2031, 1, 75 }, { 1061, 2032, 1, 450 }, { 1539, 2037, 1, 750 },
			{ 1539, 2037, 1, 750 } };
	private static final int[][] CP_POTS = { { 5591, 2166, 1, 50 } };
	private static final int[][] CP2_POTS = { { 5592, 2166, 2, 200 } };
	private static final int[][] MP_POTS = { { 726, 2003, 1, 300 }, { 728, 90001, 1, 300 } };
	private static final int[][][] ALL_POTS = { HP_POTS, CP_POTS, CP2_POTS, MP_POTS };
	private static final double[] POTION_DELAY = { 15, 0.5, 0.5, 10 };
	private Map<Player, long[]> _tasks = new ConcurrentHashMap<>();

	public AutoPotionsManager() {
		ThreadPoolManager.getInstance().schedule(new PotionsThread(), 0);
	}

	public class PotionsThread extends RunnableImpl {
		@Override
		public void runImpl() {
			for (Entry<Player, long[]> task : _tasks.entrySet()) {
				for (int i = 0; i < ALL_POTS.length; i++) {
					Player player = task.getKey();
					if (player == null || player.isDead() || player.isInOlympiadMode() || player.isActionsDisabled()
							|| player.isConfused() || player.isFrozen() || player.isParalyzed() || player.isSleeping()
							|| player.isStunned())
						break;
					if (i == 3)// Mana potions place inside All_pots
					{
						// if(player.isInCombat())
						// continue;//mana potions can not be used during combat
					}
					if (task.getValue()[i] <= System.currentTimeMillis()) {
						if (handlePotion(player, ALL_POTS[i], getStatToFill(player, i))) {
							task.getValue()[i] = (long) (System.currentTimeMillis() + POTION_DELAY[i] * 1000);
						}
					}
				}
			}
			ThreadPoolManager.getInstance().schedule(new PotionsThread(), 500);
		}
	}

	public boolean playerUseAutoPotion(Player player) {
		return _tasks.containsKey(player);
	}

	public void addNewPlayer(Player player) {
		long time = System.currentTimeMillis();
		long hpDelay = (long) (time + POTION_DELAY[0] * 1000);
		long cpDelay = (long) (time + POTION_DELAY[1] * 1000);
		long cp2Delay = (long) (time + POTION_DELAY[2] * 1000);
		long mpDelay = (long) (time + POTION_DELAY[3] * 1000);
		_tasks.put(player, new long[] { hpDelay, cpDelay, cp2Delay, mpDelay });
		for (int[][] potGroup : ALL_POTS)
			for (int[] pot : potGroup)
				player.sendPacket(new ExAutoSoulShot(pot[0], true));
		player.sendMessage("Your potions are now automatic");
	}

	public void removePlayer(Player player) {
		_tasks.remove(player);
		for (int[][] potGroup : ALL_POTS)
			for (int[] pot : potGroup)
				player.sendPacket(new ExAutoSoulShot(pot[0], false));
		player.sendMessage("Your potions are NO LONGER automatic");
	}

	private static boolean handlePotion(Player player, int[][] potionGroup, double playerStatToFill) {
		for (int i = potionGroup.length - 1; i >= 0; i--) {
			int[] pot = potionGroup[i];
			if (playerStatToFill >= pot[3]) {
				ItemInstance potion = player.getInventory().getItemByItemId(pot[0]);
				if (player.getInventory().getItemByItemId(pot[0]) != null) {
					long nextTimeUse = potion.getTemplate().getReuseType().next(potion);
					int reuse = potion.getTemplate().getReuseDelay();
					int sharedGroup = potion.getTemplate().getDisplayReuseGroup();

					if (player.getInventory().destroyItemByItemId(pot[0], 1)) {
						Skill potionSkill = SkillTable.getInstance().getInfo(pot[1], pot[2]);
						List<Creature> targets = new ArrayList<>();
						targets.add(player);
						potionSkill.useSkill(player, targets);
						player.broadcastPacket(new MagicSkillUse(player, player, pot[1], 1, 1, 0));

						if (nextTimeUse > System.currentTimeMillis()) {
							TimeStamp timeStamp = new TimeStamp(pot[0], nextTimeUse, reuse);
							player.addSharedGroupReuse(reuse, timeStamp);
							if (reuse > 0)
								player.sendPacket(new ExUseSharedGroupItem(sharedGroup, timeStamp));
						}
						return true;
					}
				}
			}
		}
		return false;
	}

	private double getStatToFill(Player player, int potionType) {
		switch (potionType) {
			case 0:
				return player.getMaxHp() - player.getCurrentHp();
			case 1:
			case 2:
				return player.getMaxCp() - player.getCurrentCp();
			case 3:
				return player.getMaxMp() - player.getCurrentMp();
		}
		return 100;
	}

	public static AutoPotionsManager getInstance() {
		if (_instance == null)
			_instance = new AutoPotionsManager();
		return _instance;
	}
}
