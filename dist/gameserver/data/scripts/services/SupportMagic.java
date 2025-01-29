package services;

import java.util.ArrayList;
import java.util.List;

import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.network.l2.s2c.RadarControl;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.utils.Location;

public class SupportMagic extends Functions {
	// Buff format: { minLevel, maxLevel, skillID, skillLevel }

	private final static int[][] _mageBuff = new int[][] {
			// Songs
			{ 1, 86, 264, 1 }, // Song of Earth
			{ 1, 86, 267, 1 }, // Song of Warding
			{ 1, 86, 268, 1 }, // Song of Wind
			{ 1, 86, 304, 1 }, // Song of Vitality
			{ 1, 86, 349, 1 }, // Song of Renewal
			{ 1, 86, 363, 1 }, // Song of Meditation
			{ 1, 86, 368, 1 }, // Song of Vengeance
			{ 1, 86, 914, 1 }, // Song of Purification
			{ 1, 86, 529, 1 }, // Song of Elemental

			// Dances
			{ 1, 86, 273, 1 }, // Dance of the Mystic
			{ 1, 86, 276, 1 }, // Dance of Concentration
			{ 1, 86, 365, 1 }, // Siren's Dance
			{ 1, 86, 915, 1 }, // Dance of Berserker
			{ 1, 86, 530, 1 }, // Dance of Alignment

			// Buffs
			{ 1, 86, 1232, 3 }, // Blazing Skin
			{ 1, 86, 1257, 3 }, // Decrease Weight
			{ 1, 86, 1392, 3 }, // Holy Resistance
			{ 1, 86, 1062, 2 }, // Berserker Spirit
			{ 1, 86, 1499, 1 }, // Improved Combat
			{ 1, 86, 1501, 1 }, // Improved Condition
			{ 1, 86, 1500, 1 }, // Improved Magic
			{ 1, 86, 1504, 1 }, // Improved Movement
			{ 1, 86, 1503, 1 }, // Improved Shield Defense
			{ 1, 86, 1085, 3 }, // Acumen
			{ 1, 86, 1078, 6 }, // Concentration
			{ 1, 86, 1035, 4 }, // Mental Shield
			{ 1, 86, 1259, 4 }, // Resist Shock
			{ 1, 86, 1393, 3 }, // Unholy Resistance
			{ 1, 86, 1352, 1 }, // Elemental Protection
			{ 1, 86, 1461, 1 }, // Chant of Protection
			{ 1, 86, 4703, 13 }, // Gift of Seraphim
			{ 1, 86, 1397, 3 }, // Clarity
			{ 1, 86, 1303, 2 }, // Wild Magic
			{ 1, 86, 1460, 1 }, // Mana Gain
			{ 1, 86, 1047, 4 }, // Mana Regeneration
			{ 1, 86, 1364, 1 }, // Eye of Pa'agrio
			{ 1, 86, 830, 1 }, // Embroider
			{ 1, 86, 1389, 3 }, // Greater Shield
			{ 1, 86, 1413, 1 }, // Magnus' Chant
			{ 1, 86, 1542, 1 }, // Counter Critical
	};

	private final static int[][] _warrBuff = new int[][] {
			// Songs
			{ 1, 86, 264, 1 }, // Song of Earth
			{ 1, 86, 267, 1 }, // Song of Warding
			{ 1, 86, 268, 1 }, // Song of Wind
			{ 1, 86, 304, 1 }, // Song of Vitality
			{ 1, 86, 269, 1 }, // Song of Hunter
			{ 1, 86, 349, 1 }, // Song of Renewal
			{ 1, 86, 364, 1 }, // Song of Champion
			{ 1, 86, 368, 1 }, // Song of Vengeance
			{ 1, 86, 914, 1 }, // Song of Purification
			{ 1, 86, 529, 1 }, // Song of Elemental

			// Dances
			{ 1, 86, 271, 1 }, // Dance of Warrior
			{ 1, 86, 275, 1 }, // Dance of Fury
			{ 1, 86, 274, 1 }, // Dance of Fire
			{ 1, 86, 272, 1 }, // Dance of Inspiration
			{ 1, 86, 310, 1 }, // Dance of Vampire
			{ 1, 86, 915, 1 }, // Dance of Berserker
			{ 1, 86, 530, 1 }, // Dance of Alignment

			// Buffs
			{ 1, 86, 1257, 3 }, // Decrease Weight
			{ 1, 86, 1392, 3 }, // Holy Resistance
			{ 1, 86, 1503, 1 }, // Improved Shield Defense
			{ 1, 86, 1393, 3 }, // Unholy Resistance
			{ 1, 86, 1062, 2 }, // Berserker Spirit
			{ 1, 86, 1499, 1 }, // Improved Combat
			{ 1, 86, 1501, 1 }, // Improved Condition
			{ 1, 86, 1500, 1 }, // Improved Magic
			{ 1, 86, 1504, 1 }, // Improved Movement
			{ 1, 86, 1502, 1 }, // Improved Critical Attack
			{ 1, 86, 1519, 1 }, // Chant of Blood Awakening
			{ 1, 86, 1390, 3 }, // War Chant
			{ 1, 86, 982, 3 }, // Combat Aura
			{ 1, 86, 1364, 1 }, // Eye of Pa'agrio
			{ 1, 86, 1035, 4 }, // Mental Shield
			{ 1, 86, 1259, 4 }, // Resist Shock
			{ 1, 86, 1352, 1 }, // Elemental Protection
			{ 1, 86, 1461, 1 }, // Chant of Protection
			{ 1, 86, 4699, 13 }, // Blessing of Queen
			{ 1, 86, 829, 1 }, // Hard Tanning
			{ 1, 86, 825, 1 }, // Sharp Edge
			{ 1, 86, 1363, 1 }, // Chant of Victory
			{ 1, 86, 1542, 1 }, // Counter Critical
	};

	private final static int[][] _summonBuff = new int[][] {
			// minlevel maxlevel skill
			{ 1, 86, 4342, 4 }, // windwalk
			{ 1, 86, 4344, 6 }, // shield
			{ 1, 86, 4349, 4 }, // Magic Barrier 1
			{ 1, 86, 4347, 12 }, // btb
			{ 1, 86, 4354, 8 }, // vampirerage
			{ 1, 86, 4346, 5 }, // mental shield
			{ 1, 86, 4350, 5 }, // resist stun
			{ 1, 86, 4326, 1 }, // regeneration
			{ 1, 86, 4348, 12 }, // blessthesoul
			{ 1, 86, 4355, 6 }, // acumen
			{ 1, 86, 4351, 12 }, // concentration
			{ 1, 86, 4356, 6 }, // empower
			{ 1, 86, 4357, 4 }, // haste 2
			{ 1, 86, 4345, 6 }, // might
			{ 1, 86, 4352, 3 }, // berserker spirit
			{ 1, 86, 4360, 4 }, // death whisper
			{ 1, 86, 4359, 4 }, // focus
			{ 1, 86, 269, 1 }, // song of hunter
			{ 1, 86, 268, 1 }, // song of wind
			{ 1, 86, 267, 1 }, // song of ward
			{ 1, 86, 264, 1 }, // song of earth
			{ 1, 86, 276, 1 }, // dance of concentration
			{ 1, 86, 273, 1 }, // dance of mystic
			{ 1, 86, 275, 1 }, // dance of fury
			{ 1, 86, 274, 1 }, // dance of fire
			{ 1, 86, 272, 1 }, // dance of insp
			{ 1, 86, 271, 1 }, // dance of warr
			{ 1, 86, 1363, 1 }, // cov
	};

	private final static int minSupLvl = 1;
	private final static int maxSupLvl = 87;

	public void getSupportMagic() {
		Player player = getSelf();
		NpcInstance npc = getNpc();

		doSupportMagic(npc, player, false);
	}

	public void getSupportServitorMagic() {
		Player player = getSelf();
		NpcInstance npc = getNpc();

		doSupportMagic(npc, player, true);
	}

	public void getProtectionBlessing() {
		Player player = getSelf();
		NpcInstance npc = getNpc();

		// Не выдаём блессиг протекшена ПКшникам.
		if (player.getKarma() > 0)
			return;
		if (player.getLevel() > 39 || player.getActiveClassClassId().getLevel() >= 3) {
			show("default/newbie_blessing_no.htm", player, npc);
			return;
		}
		npc.doCast(SkillTable.getInstance().getInfo(5182, 1), player, true);
	}

	public static void doSupportMagic(final NpcInstance npc, final Player player, boolean servitor) {
		// Prevent a cursed weapon weilder of being buffed
		if (player.isCursedWeaponEquipped())
			return;

		if (player.getActiveClassClassId().getLevel() <= 1) {
			Location firstNpc = new Location(-119528, 87176, -12618);
			player.sendPacket(new RadarControl(2, 2, firstNpc.getX(), firstNpc.getY(), firstNpc.getZ()));
			player.sendPacket(new RadarControl(0, 1, firstNpc.getX(), firstNpc.getY(), firstNpc.getZ()));
		}

		final int lvl = player.getLevel();

		if (servitor && (player.getPet() == null || !player.getPet().isSummon())) {
			show("default/newbie_nosupport_servitor.htm", player, npc);
			return;
		} else {
			if (lvl < minSupLvl) {
				show("default/newbie_nosupport_min.htm", player, npc);
				return;
			}
			if (lvl > maxSupLvl) {
				show("default/newbie_nosupport_max.htm", player, npc);
				return;
			}
		}

		final List<Creature> target = new ArrayList<Creature>();

		if (servitor) {
			target.add(player.getPet());

			for (int[] buff : _summonBuff)
				if (lvl >= buff[0] && lvl <= buff[1]) {
					npc.broadcastPacket(new MagicSkillUse(npc, player.getPet(), buff[2], buff[3], 0, 0));
					npc.callSkill(SkillTable.getInstance().getInfo(buff[2], buff[3]), target, true);
				}
		} else {
			target.add(player);

			if (!player.isMageClass()) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						for (int[] buff : _warrBuff)
							if (lvl >= buff[0] && lvl <= buff[1]) {
								npc.broadcastPacket(new MagicSkillUse(npc, player, buff[2], buff[3], 0, 0));
								npc.callSkill(SkillTable.getInstance().getInfo(buff[2], buff[3]), target, true);
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
					}
				}).start();
			} else {
				new Thread(new Runnable() {
					@Override
					public void run() {
						for (int[] buff : _mageBuff)
							if (lvl >= buff[0] && lvl <= buff[1]) {
								npc.broadcastPacket(new MagicSkillUse(npc, player, buff[2], buff[3], 0, 0));
								npc.callSkill(SkillTable.getInstance().getInfo(buff[2], buff[3]), target, true);
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
					}
				}).start();
			}
		}
	}

}
