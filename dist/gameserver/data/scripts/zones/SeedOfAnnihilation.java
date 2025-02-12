package zones;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import l2ft.commons.threading.RunnableImpl;
import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.ThreadPoolManager;
import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.instancemanager.ServerVariables;
import l2ft.gameserver.listener.zone.OnZoneEnterLeaveListener;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.model.Zone;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.Inventory;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.item.ArmorTemplate.ArmorType;
import l2ft.gameserver.templates.item.WeaponTemplate.WeaponType;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.Location;
import l2ft.gameserver.utils.ReflectionUtils;


public class SeedOfAnnihilation implements ScriptFile
{
	private static final int ANNIHILATION_FURNACE = 18928;
	private static final int[][] ZONE_BUFFS_LIST = { { 1, 2, 3 }, { 1, 3, 2 }, { 2, 1, 3 }, { 2, 3, 1 }, { 3, 2, 1 }, { 3, 1, 2 } };
	private static final Map<String, Location> _teleportZones = new HashMap<String, Location>();
	static
	{
		_teleportZones.put("[14_23_telzone_to_cocracon]", new Location(-213175, 182648, -10992)); // In Kokracon location teleport zone.
		_teleportZones.put("[14_23_telzone_to_raptilicon]", new Location(-180211, 182984, -15152)); // In Reptilikon location teleport zone.
		_teleportZones.put("[13_23_telzone_from_cocracon]", new Location(-181217, 186711, -10528)); // Out Kokracon location teleport zone.
		_teleportZones.put("[14_23_telzone_from_raptilicon]", new Location(-179275, 186802, -10720)); // Out Reptilikon location teleport zone.
	}
	private static final List<String> _buffZones = new ArrayList<String>();
	static
	{
		_buffZones.add("[13_23_cocracon_for_melee_for_pc]");
		_buffZones.add("[13_23_cocracon_for_archer_for_pc]");
		_buffZones.add("[14_23_raptilicon_for_melee_for_pc]");
		_buffZones.add("[14_23_raptilicon_for_melee_for_pc]");
		_buffZones.add("[14_23_raptilicon_for_archer_for_pc]");
		_buffZones.add("[14_23_raptilicon_for_mage_for_pc]");
		_buffZones.add("[14_23_beastacon_for_melee_for_pc]");
		_buffZones.add("[14_23_beastacon_for_archer_for_pc]");
		_buffZones.add("[14_23_beastacon_for_mage_for_pc]");
	}

	private static ZoneListener _zoneListener;
	private static BuffZoneListener _buffZoneListener;

	// 0: Bistakon, 1: Reptilikon, 2: Cokrakon
	private SeedRegion[] _regionsData = new SeedRegion[3];
	private Long _seedsNextStatusChange;

	private class SeedRegion
	{
		public String[] buff_zone_pc;
		public String[] buff_zone_npc;
		public int[][] af_spawns;
		public NpcInstance[] af_npcs = new NpcInstance[2];
		public int activeBuff = 0;

		public SeedRegion(String[] bz_pc, String[] bz_npc, int[][] as)
		{
			buff_zone_pc = bz_pc;
			buff_zone_npc = bz_npc;
			af_spawns = as;
		}
	}

	public void loadSeedRegionData()
	{
		_zoneListener = new ZoneListener();
		if(_teleportZones != null && !_teleportZones.isEmpty())
			for(String s : _teleportZones.keySet())
			{
				Zone zone = ReflectionUtils.getZone(s);
				zone.addListener(_zoneListener);
			}

		_regionsData[0] = new SeedRegion(new String[] { "[14_23_beastacon_for_melee_for_pc]", "[14_23_beastacon_for_archer_for_pc]", "[14_23_beastacon_for_mage_for_pc]" }, new String[] { "[14_23_beastacon_for_melee]", "[14_23_beastacon_for_archer]", "[14_23_beastacon_for_mage]" }, new int[][] {
				{ -180450, 185507, -10574, 11632 },
				{ -180005, 185489, -10577, 11632 } }); // Bistakon data
		_regionsData[1] = new SeedRegion(new String[] { "[14_23_raptilicon_for_melee_for_pc]", "[14_23_raptilicon_for_archer_for_pc]", "[14_23_raptilicon_for_mage_for_pc]" }, new String[] { "[14_23_raptilicon_for_melee]", "[14_23_raptilicon_for_archer]", "[14_23_raptilicon_for_mage]" }, new int[][] {
				{ -179600, 186998, -10737, 11632 },
				{ -179295, 186444, -10733, 11632 } }); // Reptilikon data
		_regionsData[2] = new SeedRegion(new String[] { "[13_23_cocracon_for_melee_for_pc]", "[13_23_cocracon_for_archer_for_pc]", "[13_23_cocracon_for_mage_for_pc]" }, new String[] { "[13_23_cocracon_for_melee]", "[13_23_cocracon_for_archer]", "[13_23_cocracon_for_mage]" }, new int[][] {
				{ -180971, 186361, -10557, 11632 },
				{ -180758, 186739, -10556, 11632 } }); // Cokrakon data
		
		_buffZoneListener = new BuffZoneListener();
		for(String zoneName : _buffZones)
		{
			Zone zone = ReflectionUtils.getZone(zoneName);
			if(zone != null)
				zone.addListener(_buffZoneListener);
		}

		int buffsNow = 0;
		long nextStatusChange = ServerVariables.getLong("SeedNextStatusChange", 0);
		if(nextStatusChange < System.currentTimeMillis())
		{
			buffsNow = Rnd.get(ZONE_BUFFS_LIST.length);
			_seedsNextStatusChange = getNextSeedsStatusChangeTime();
			ServerVariables.set("SeedBuffsList", buffsNow);
			ServerVariables.set("SeedNextStatusChange", _seedsNextStatusChange);
		}
		else
		{
			_seedsNextStatusChange = nextStatusChange;
			buffsNow = ServerVariables.getInt("SeedBuffsList", 0);
		}
		for(int i = 0; i < _regionsData.length; i++)
			_regionsData[i].activeBuff = ZONE_BUFFS_LIST[buffsNow][i];
		
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new BuffThread(), 20000, 10000);
	}

	private Long getNextSeedsStatusChangeTime()
	{
		Calendar reenter = Calendar.getInstance();
		reenter.set(Calendar.SECOND, 0);
		reenter.set(Calendar.MINUTE, 0);
		reenter.set(Calendar.HOUR_OF_DAY, 13);
		reenter.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		if(reenter.getTimeInMillis() <= System.currentTimeMillis())
			reenter.add(Calendar.DAY_OF_MONTH, 7);
		return reenter.getTimeInMillis();
	}

	@Override
	public void onLoad()
	{
		loadSeedRegionData();
		startEffectZonesControl();
	}

	@Override
	public void onReload()
	{}

	@Override
	public void onShutdown()
	{}

	private void startEffectZonesControl()
	{
		for(SeedRegion sr : _regionsData)
		{
			NpcTemplate template = NpcHolder.getInstance().getTemplate(ANNIHILATION_FURNACE);
			for(int i = 0; i < sr.af_spawns.length; i++)
			{
				NpcInstance npc = template.getNewInstance();
				npc.setCurrentHpMp(npc.getMaxHp(), npc.getMaxMp());
				npc.spawnMe(new Location(sr.af_spawns[i][0], sr.af_spawns[i][1], sr.af_spawns[i][2], sr.af_spawns[i][3]));
				npc.setNpcState(sr.activeBuff);
				sr.af_npcs[i] = npc;
			}
			chanceZoneActive(sr.buff_zone_pc[sr.activeBuff - 1], true);
			chanceZoneActive(sr.buff_zone_npc[sr.activeBuff - 1], true);
		}
		ThreadPoolManager.getInstance().schedule(new ChangeSeedsStatus(), _seedsNextStatusChange - System.currentTimeMillis());
	}

	private class ChangeSeedsStatus extends RunnableImpl
	{
		public ChangeSeedsStatus()
		{}

		public void runImpl() throws Exception
		{
			int buffsNow = Rnd.get(ZONE_BUFFS_LIST.length);
			_seedsNextStatusChange = getNextSeedsStatusChangeTime();
			ServerVariables.set("SeedBuffsList", buffsNow);
			ServerVariables.set("SeedNextStatusChange", _seedsNextStatusChange);
			for(int i = 0; i < _regionsData.length; i++)
			{
				int oldBuff = _regionsData[i].activeBuff;
				_regionsData[i].activeBuff = ZONE_BUFFS_LIST[buffsNow][i];

				for(NpcInstance af : _regionsData[i].af_npcs)
					af.setNpcState(_regionsData[i].activeBuff);

				chanceZoneActive(_regionsData[i].buff_zone_pc[oldBuff - 1], false);
				chanceZoneActive(_regionsData[i].buff_zone_npc[oldBuff - 1], false);
				chanceZoneActive(_regionsData[i].buff_zone_pc[_regionsData[i].activeBuff - 1], true);
				chanceZoneActive(_regionsData[i].buff_zone_npc[_regionsData[i].activeBuff - 1], true);
			}
			ThreadPoolManager.getInstance().schedule(new ChangeSeedsStatus(), _seedsNextStatusChange - System.currentTimeMillis());
		}
	}

	private void chanceZoneActive(String zoneName, boolean val)
	{
		Zone zone = ReflectionUtils.getZone(zoneName);
		zone.setActive(val);
	}
	
	private static List<Player> _playersInZone = new CopyOnWriteArrayList<Player>();
	
	public class BuffZoneListener implements OnZoneEnterLeaveListener
	{

		@Override
		public void onZoneEnter(Zone zone, Creature actor)
		{
			if(!actor.isPlayer())
				return;
			Player player = actor.getPlayer();
			if(!_playersInZone.contains(player))
				_playersInZone.add(player);
		}

		@Override
		public void onZoneLeave(Zone zone, Creature actor)
		{
			if(!actor.isPlayer())
				return;
			Player player = actor.getPlayer();
			if(_playersInZone.contains(player))
				_playersInZone.remove(player);
		}
		
	}
	
	private static Skill MAGE_SKILL;
	private static Skill ARCHER_SKILL;
	private static Skill OTHER_SKILL;
	
	private class BuffThread extends RunnableImpl
	{
		public BuffThread()
		{
			if(MAGE_SKILL == null)
			{
				MAGE_SKILL = SkillTable.getInstance().getInfo(6442, 1);
				ARCHER_SKILL = SkillTable.getInstance().getInfo(6444, 1);
				OTHER_SKILL = SkillTable.getInstance().getInfo(6443, 1);
			}
		}
		@Override
		public void runImpl()
		{
			for(Player player : _playersInZone)
			{
				Skill skill = getSkill(player);
				skill.getEffects(player, player, false, false);
			}
		}

		private Skill getSkill(Player player) 
		{
			ItemInstance chest = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
			ItemInstance weapon = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
			if(chest != null && ((ArmorType)chest.getItemType()) == ArmorType.MAGIC)
				return MAGE_SKILL;
			else if(weapon != null && weapon.getItemType() != null && ((WeaponType)weapon.getItemType()) == WeaponType.BOW || ((WeaponType)weapon.getItemType()) == WeaponType.CROSSBOW)
				return ARCHER_SKILL;
			else
				return OTHER_SKILL;
		}
	}

	public class ZoneListener implements OnZoneEnterLeaveListener
	{
		@Override
		public void onZoneEnter(Zone zone, Creature cha)
		{
			if(_teleportZones.containsKey(zone.getName()))
			{
				//Заглушка для 454 квеста.
				List<NpcInstance> around = cha.getAroundNpc(500, 300);
				if(around != null && !around.isEmpty())
					for(NpcInstance npc : around)
						if(npc.getNpcId() == 32738 && npc.getFollowTarget() != null)
						{
							if(npc.getFollowTarget().getObjectId() == cha.getObjectId())
							{
								npc.teleToLocation(_teleportZones.get(zone.getName()));
								npc.getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, cha, Config.FOLLOW_RANGE);
							}
						}

				cha.teleToLocation(_teleportZones.get(zone.getName()));
			}
		}

		@Override
		public void onZoneLeave(Zone zone, Creature cha)
		{}
	}
}