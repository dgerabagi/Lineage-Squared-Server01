package quests;

import l2ft.commons.util.Rnd;
import l2ft.gameserver.Config;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.scripts.ScriptFile;

/**
 * User: Keiichi
 * Date: 08.10.2008
 * Time: 16:00:35
 * Hellbound Isle Quest 690
 * 22399	Greater Evil
 */
public class _690_JudesRequest extends Quest implements ScriptFile
{
	// NPC's
	private static int JUDE = 32356;
	// ITEM's
	private static int EVIL_WEAPON = 10327;
	// MOB's
	private static int Evil = 22399;
	// Chance
	private static int EVIL_WEAPON_CHANCE = 100;
	// Reward Recipe's
	private static int ISawsword = 10373;
	private static int IDisperser = 10374;
	private static int ISpirit = 10375;
	private static int IHeavyArms = 10376;
	private static int ITrident = 10377;
	private static int IHammer = 10378;
	private static int IHand = 10379;
	private static int IHall = 10380;
	private static int ISpitter = 10381;
	// Reward Piece's
	private static int ISawswordP = 10397;
	private static int IDisperserP = 10398;
	private static int ISpiritP = 10399;
	private static int IHeavyArmsP = 10400;
	private static int ITridentP = 10401;
	private static int IHammerP = 10402;
	private static int IHandP = 10403;
	private static int IHallP = 10404;
	private static int ISpitterP = 10405;
	//DROPLIST (MOB_ID, CHANCE)
	private final static int[][] DROPLIST = {
			{22320,55},{22321,55},{22324,55},{22325,55},{22327,66},{22328,66},
			{22329,66},{22334,55},{22335,55},{22336,55},{22337,55},{22340,55},
			{22349,77},{22350,77},{22351,77},{22352,77},{22363,88},{22364,88},
			{22365,88},{22366,88},{22367,88},{22368,88},{22369,88},{22370,88},
			{22371,88},{22372,88},{22373,88},{22374,88},{22375,88},{22376,88},{22330,77}};
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

	public _690_JudesRequest()
	{
		super(true);

		addStartNpc(JUDE);
		addTalkId(JUDE);
		addKillId(Evil);
		addQuestItem(EVIL_WEAPON);
		
		for(int[] element : DROPLIST)
			addKillId(element[0]);
	}

	@Override
	public String onEvent(String event, QuestState st, NpcInstance npc)
	{
		String htmltext = event;
		if(event.equalsIgnoreCase("jude_q0690_03.htm"))
		{
			st.setCond(1);
			st.setState(STARTED);
			st.playSound(SOUND_ACCEPT);
		}
		return htmltext;
	}

	private void giveReward(QuestState st, int item_id, long count)
	{
		st.giveItems(item_id, count);
	}

	@Override
	public String onTalk(NpcInstance npc, QuestState st)
	{
		String htmltext = "noquest";
		int cond = st.getCond();
		if(cond == 0)
		{
			if(st.getPlayer().getLevel() >= 78)
				htmltext = "jude_q0690_01.htm";
			else
				htmltext = "jude_q0690_02.htm";
			st.exitCurrentQuest(true);
		}
		else if(cond == 1 && st.getQuestItemsCount(EVIL_WEAPON) >= 5)
		{
			int reward = Rnd.get(8);
			if(st.getQuestItemsCount(EVIL_WEAPON) >= 1)
			{
				htmltext = "jude_q0690_07.htm";
			}
		}
		else
			htmltext = "jude_q0690_10.htm";
		return htmltext;
	}

	@Override
	public String onKill(NpcInstance npc, QuestState st)
	{
		for(int[] element : DROPLIST)
			if(npc.getNpcId() == element[0])
			{
				st.giveItems(EVIL_WEAPON, 1);
				st.playSound(SOUND_ITEMGET);
			}
		return null;
	}
}