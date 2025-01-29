/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package quests;

import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.components.ChatType;
import l2ft.gameserver.network.l2.components.NpcString;
import l2ft.gameserver.network.l2.s2c.NpcSay;
import l2ft.gameserver.tables.SkillTable;

/**
 * @author corbin12
 */

public class NevitsHerald extends Quest
{

    //NPCs
    private static final int NevitsHerald   = 4326;
    //GrandBosses
    private static final int AntharasOld    = 29019;
    private static final int AntharasWeak   = 29067;
    private static final int AntharasNormal = 29067;
    private static final int AntharasStrong = 29068;
    private static final int Valakas        = 29028;
    //Fall of the Dragon Buff
    private static int _buff = 23312;
    //Spawnlist
    public int[][] _spawns =
    {
            {  82766,  149438, -3464, 33865 },
            {  82286,   53291, -1488, 15250 },
            { 147060,   25943, -2008, 18774 },
            { 148096,  -55466, -2728, 40541 },
            {  87116, -141332, -1336, 52193 },
            {  43521,  -47542,  -792, 31655 },
            {  17203,  144949, -3024, 18166 },
            { 111164,  221062, -3544,  2714 },
            { -13869,  122063, -2984, 18270 },
            { -83161,  150915, -3120, 17311 },
            {  45402,   48355, -3056, 49153 },
            { 115616, -177941,  -896, 30708 },
            { -44928, -113608,  -192, 30212 },
            { -84037,  243194, -3728,  8992 },
            {-119690,   44583,   360, 29289 },
            {  12084,   16576, -4584, 57345 }
    };
 
	@Override
	public String onEvent(String event, QuestState qs, NpcInstance npc)
	{
		Player player = qs.getPlayer();
		String htmltext = event;
		QuestState st = player.getQuestState(getName());
		if (st == null)
			return null;
		
		if (event.equalsIgnoreCase("giveBuff"))
		{
			if (player.getEffectList().containEffectFromSkills(new int[] {_buff}))
			{
				return "no-buff.htm";
			}
			else
				npc.setTarget(player);
			npc.doCast(SkillTable.getInstance().getInfo(_buff,1), player, true);	// Fall of the Dragon
			st.setState(Quest.STARTED);
		}
		return htmltext;
	}
    
	@Override
	public String onFirstTalk(NpcInstance npc, Player player)
	{
		return npc.getNpcId() + ".htm";
	}
	
	@Override
	public String onKill (NpcInstance npc, QuestState qs)
	{
		if (npc.getNpcId() == Valakas);
		{
			for (int[] spawn : _spawns)
			{
				addSpawn(NevitsHerald, spawn[0], spawn[1], spawn[2], spawn[3], 0, 10800000);
				npc.broadcastPacket(new NpcSay(npc, ChatType.SHOUT, NpcString.THE_EVIL_FIRE_DRAGON_VALAKAS_HAS_BEEN_DEFEATED));
			}
		}
    	if (npc.getNpcId() == AntharasOld || npc.getNpcId() == AntharasWeak || npc.getNpcId() == AntharasNormal || npc.getNpcId() == AntharasStrong );
    	{
    		for (int[] spawn : _spawns)
    		{
    			npc.broadcastPacket(new NpcSay(npc, ChatType.SHOUT, NpcString.THE_EVIL_LAND_DRAGON_ANTHARAS_HAS_BEEN_DEFEATED));	
    			addSpawn(NevitsHerald, spawn[0], spawn[1], spawn[2], spawn[3], 0, 10800000);
    	     }
    	}

		return super.onKill(npc, qs);
	}

	public NevitsHerald(int questId, String name, String descr)
	{
		super(false);
		
		addStartNpc(NevitsHerald);
		addTalkId(NevitsHerald);
		addKillId(AntharasOld);
		addKillId(AntharasWeak);
		addKillId(AntharasNormal);
		addKillId(AntharasStrong);
		addKillId(Valakas);
	}
	
	public static void main(String[] args)
	{
		new NevitsHerald(-1, NevitsHerald.class.getSimpleName(), "retail");
	}
}