package quests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.data.xml.holder.NpcHolder;
import l2ft.gameserver.data.xml.holder.SoulCrystalHolder;
import l2ft.gameserver.templates.npc.AbsorbInfo;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.templates.SoulCrystal;
import l2ft.gameserver.model.instances.MonsterInstance;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.items.ItemInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;
import l2ft.gameserver.scripts.ScriptFile;
import l2ft.gameserver.templates.npc.NpcTemplate;


public class _350_EnhanceYourWeapon extends Quest implements ScriptFile
{
	private static class PlayerResult
	{
		private final Player _player;
		private SystemMsg _message;

		public PlayerResult(Player player)
		{
			_player = player;
		}

		public Player getPlayer()
		{
			return _player;
		}

		public SystemMsg getMessage()
		{
			return _message;
		}

		public void setMessage(SystemMsg message)
		{
			_message = message;
		}

		public void send()
		{
			if(_message != null)
				_player.sendPacket(_message);
		}
	}

	private static final int RED_SOUL_CRYSTAL0_ID = 4629;
	private static final int GREEN_SOUL_CRYSTAL0_ID = 4640;
	private static final int BLUE_SOUL_CRYSTAL0_ID = 4651;

	private static final int Jurek = 30115;
	private static final int Gideon = 30194;
	private static final int Winonin = 30856;

	@Override
	public void onLoad()
	{
	}

	@Override
	public void onReload()
	{
	}

	@Override
	public void onShutdown()
	{
	}

	public _350_EnhanceYourWeapon()
	{
		super(false);
		addStartNpc(Jurek);
		addStartNpc(Gideon);
		addStartNpc(Winonin);

		for(NpcTemplate template : NpcHolder.getInstance().getAll())
		{
			if(template != null && !template.getAbsorbInfo().isEmpty())
				addKillId(template.npcId);
		}
	}

	@Override
	public String onEvent(String event, QuestState st, NpcInstance npc)
	{
		if(event.equalsIgnoreCase(Jurek + "-04.htm") || event.equalsIgnoreCase(Gideon + "-04.htm") || event.equalsIgnoreCase(Winonin + "-04.htm"))
		{
			st.setCond(1);
			st.setState(STARTED);
			st.playSound(SOUND_ACCEPT);
		}
		if(event.equalsIgnoreCase(Jurek + "-09.htm") || event.equalsIgnoreCase(Gideon + "-09.htm") || event.equalsIgnoreCase(Winonin + "-09.htm"))
			st.giveItems(RED_SOUL_CRYSTAL0_ID, 1);
		if(event.equalsIgnoreCase(Jurek + "-10.htm") || event.equalsIgnoreCase(Gideon + "-10.htm") || event.equalsIgnoreCase(Winonin + "-10.htm"))
			st.giveItems(GREEN_SOUL_CRYSTAL0_ID, 1);
		if(event.equalsIgnoreCase(Jurek + "-11.htm") || event.equalsIgnoreCase(Gideon + "-11.htm") || event.equalsIgnoreCase(Winonin + "-11.htm"))
			st.giveItems(BLUE_SOUL_CRYSTAL0_ID, 1);
		if(event.equalsIgnoreCase("exit.htm"))
			st.exitCurrentQuest(true);
		return event;
	}

	@Override
	public String onTalk(NpcInstance npc, QuestState st)
	{
		String npcId = str(npc.getNpcId());
		String htmltext = "noquest";
		int id = st.getState();
		if(st.getQuestItemsCount(RED_SOUL_CRYSTAL0_ID) == 0 && st.getQuestItemsCount(GREEN_SOUL_CRYSTAL0_ID) == 0 && st.getQuestItemsCount(BLUE_SOUL_CRYSTAL0_ID) == 0)
			if(id == CREATED)
				htmltext = npcId + "-01.htm";
			else
				htmltext = npcId + "-21.htm";
		else
		{
			if(id == CREATED)
			{
				st.setCond(1);
				st.setState(STARTED);
			}
			htmltext = npcId + "-03.htm";
		}
		return htmltext;
	}

	@Override
	public String onKill(NpcInstance npc, QuestState qs)
	{
		Player player = qs.getPlayer();
		if(player == null || !npc.isMonster())
			return null;

		List<PlayerResult> list;
		if(player.getParty() == null)
		{
			list = new ArrayList<PlayerResult>(1);
			list.add(new PlayerResult(player));
		}
		else
		{
			list = new ArrayList<PlayerResult>(player.getParty().getMemberCount());
			list.add(new PlayerResult(player)); // index 0
			for(Player m : player.getParty().getPartyMembers())
				if(m != player && m.isInRange(npc.getLoc(), Config.ALT_PARTY_DISTRIBUTION_RANGE))
					list.add(new PlayerResult(m));
		}

		for(AbsorbInfo info : npc.getTemplate().getAbsorbInfo())
			calcAbsorb(list, (MonsterInstance) npc, info);

		for(PlayerResult r : list)
			r.send();

		return null;
	}

	private void calcAbsorb(List<PlayerResult> players, MonsterInstance npc, AbsorbInfo info)
	{
		int memberSize = 0;
		List<PlayerResult> targets;
		switch(info.getAbsorbType())
		{
			case LAST_HIT:
				targets = Collections.singletonList(players.get(0));
				break;
			case PARTY_ALL:
				targets = players;
				break;
			case PARTY_RANDOM:
				memberSize = players.size();
				if(memberSize == 1)
					targets = Collections.singletonList(players.get(0));
				else
				{
					int size = Rnd.get(memberSize);
					targets = new ArrayList<PlayerResult>(size);
					List<PlayerResult> temp = new ArrayList<PlayerResult>(players);
					Collections.shuffle(temp);
					for(int i = 0; i < size; i++)
						targets.add(temp.get(i));
				}
				break;
			case PARTY_ONE:
				memberSize = players.size();
				if(memberSize == 1)
					targets = Collections.singletonList(players.get(0));
				else
				{
					int rnd = Rnd.get(memberSize);
					targets = Collections.singletonList(players.get(rnd));
				}
				break;
			default:
				return;
		}

		for(PlayerResult target : targets)
		{
			if(target == null || target.getMessage() == SystemMsg.THE_SOUL_CRYSTAL_SUCCEEDED_IN_ABSORBING_A_SOUL)
				continue;
			Player targetPlayer = target.getPlayer();
			if(info.isSkill() && !npc.isAbsorbed(targetPlayer))
				continue;
			if(targetPlayer.getQuestState(_350_EnhanceYourWeapon.class) == null)
				continue;
			SoulCrystal soulCrystal = null;
			ItemInstance[] items = targetPlayer.getInventory().getItems();
			for(ItemInstance item : items)
			{
				SoulCrystal crystal = SoulCrystalHolder.getInstance().getCrystal(item.getItemId());
				if(crystal == null || !info.canAbsorb(crystal.getLevel()))
					continue;
				soulCrystal = crystal;
				break;
			}
			
			if(soulCrystal == null)
				continue;
			if(!info.canAbsorb(soulCrystal.getLevel()))
			{
				target.setMessage(SystemMsg.THE_SOUL_CRYSTAL_IS_REFUSING_TO_ABSORB_THE_SOUL);
				continue;
			}
			int nextItemId = 0;
			if(info.getCursedChance() > 0 && soulCrystal.getCursedNextItemId() > 0)
				nextItemId = Rnd.chance(info.getCursedChance()) ? soulCrystal.getCursedNextItemId() : 0;

			if(nextItemId == 0)
				nextItemId = Rnd.chance(info.getChance()) ? soulCrystal.getNextItemId() : 0;
			if(nextItemId == 0)
			{
				target.setMessage(SystemMsg.THE_SOUL_CRYSTAL_WAS_NOT_ABLE_TO_ABSORB_THE_SOUL);
				continue;
			}

			if(targetPlayer.consumeItem(soulCrystal.getItemId(), 1))
			{
				targetPlayer.getInventory().addItem(nextItemId, 1);
				targetPlayer.sendPacket(SystemMessage2.obtainItems(nextItemId, 1, 0));

				target.setMessage(SystemMsg.THE_SOUL_CRYSTAL_SUCCEEDED_IN_ABSORBING_A_SOUL);
			}
			else
				target.setMessage(SystemMsg.THE_SOUL_CRYSTAL_WAS_NOT_ABLE_TO_ABSORB_THE_SOUL);
		}
	}
}