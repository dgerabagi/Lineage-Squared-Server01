package l2ft.gameserver.model.instances;

import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import l2ft.gameserver.ai.CtrlIntention;
import l2ft.gameserver.model.Creature;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Skill;
import l2ft.gameserver.network.l2.s2c.MagicSkillUse;
import l2ft.gameserver.network.l2.s2c.MyTargetSelected;
import l2ft.gameserver.network.l2.s2c.ValidateLocation;
import l2ft.gameserver.scripts.Events;
import l2ft.gameserver.tables.SkillTable;
import l2ft.gameserver.templates.npc.NpcTemplate;


public class OlympiadBufferInstance extends NpcInstance
{
	private TIntObjectHashMap<Integer> buffs = new TIntObjectHashMap<>();

	public OlympiadBufferInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void onAction(Player player, boolean shift)
	{
		if(Events.onAction(player, this, shift))
		{
			player.sendActionFailed();
			return;
		}
		
		if(buffs.get(player.getObjectId()) == null)
			buffs.put(player.getObjectId(), 0);
		
		if(this != player.getTarget())
		{
			player.setTarget(this);
			MyTargetSelected my = new MyTargetSelected(getObjectId(), player.getLevel() - getLevel());
			player.sendPacket(my);
			player.sendPacket(new ValidateLocation(this));
		}
		else
		{
			MyTargetSelected my = new MyTargetSelected(getObjectId(), player.getLevel() - getLevel());
			player.sendPacket(my);
			if(!isInRange(player, INTERACTION_DISTANCE))
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			else if(buffs.get(player.getObjectId()) > 4)
				showChatWindow(player, 1);
			else
				showChatWindow(player, 0);
			player.sendActionFailed();
		}
	}

	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;
		
		if(buffs.get(player.getObjectId()) > 4)
		{
			showChatWindow(player, 1);
			return;
		}

		if(command.startsWith("Buff"))
		{
			int id = 0;
			int lvl = 0;
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			id = Integer.parseInt(st.nextToken());
			lvl = Integer.parseInt(st.nextToken());
			Skill skill = SkillTable.getInstance().getInfo(id, lvl);
			List<Creature> target = new ArrayList<Creature>();
			target.add(player);
			broadcastPacket(new MagicSkillUse(this, player, id, lvl, 0, 0));
			callSkill(skill, target, true);
			buffs.put(player.getObjectId(), buffs.get(player.getObjectId())+1);
			if(buffs.size() > 4)
				showChatWindow(player, 1);
			else
				showChatWindow(player, 0);
		}
		else
			showChatWindow(player, 0);
	}

	@Override
	public String getHtmlPath(int npcId, int val, Player player)
	{
		String pom;
		if(val == 0)
			pom = "buffer";
		else
			pom = "buffer-" + val;

		// If the file is not found, the standard message "I have nothing to say to you" is returned
		return "olympiad/" + pom + ".htm";
	}
}