package npc.model.residences.dominion;

import l2ft.gameserver.data.xml.holder.MultiSellHolder;
import l2ft.gameserver.instancemanager.QuestManager;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.Race;
import l2ft.gameserver.model.entity.events.impl.DominionSiegeEvent;
import l2ft.gameserver.model.entity.olympiad.Olympiad;
import l2ft.gameserver.model.entity.residence.Dominion;
import l2ft.gameserver.model.instances.NpcInstance;
import l2ft.gameserver.model.quest.Quest;
import l2ft.gameserver.model.quest.QuestState;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.scripts.Functions;
import l2ft.gameserver.templates.item.ItemTemplate;
import l2ft.gameserver.templates.npc.NpcTemplate;
import l2ft.gameserver.utils.HtmlUtils;
import l2ft.gameserver.utils.ItemFunctions;
import quests._234_FatesWhisper;
import quests._235_MimirsElixir;
import quests._236_SeedsOfChaos;

public class TerritoryManagerInstance extends NpcInstance
{
	public TerritoryManagerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}

	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if(!canBypassCheck(player, this))
			return;

		Dominion dominion = getDominion();
		DominionSiegeEvent siegeEvent = dominion.getSiegeEvent();
		int npcId = getNpcId();
		int badgeId = 13676 + dominion.getId();

		if(command.equalsIgnoreCase("buyspecial"))
		{
			if(Functions.getItemCount(player, badgeId) < 1)
				showChatWindow(player, 1);
			else
				MultiSellHolder.getInstance().SeparateAndSend(npcId, player, 0);
		}
		else if(command.equalsIgnoreCase("buyNobless"))
		{
			if(player.isNoble())
			{
				//
				return; //TODO [VISTALL] Đ˝ĐµĐ¸Đ·Đ˛ĐµŃ�Ń‚Đ˝Đľ
			}
			if(player.consumeItem(badgeId, 100L))
			{
				Quest q = QuestManager.getQuest(_234_FatesWhisper.class);
				QuestState qs = player.getQuestState(q.getClass());
				if(qs != null)
					qs.exitCurrentQuest(true);
				q.newQuestState(player, Quest.COMPLETED);

				if(player.getRace() == Race.kamael)
				{
					qs = player.getQuestState(_236_SeedsOfChaos.class);
					if(qs != null)
						qs.exitCurrentQuest(true);
					q.newQuestState(player, Quest.COMPLETED);
				}
				else
				{
					q = QuestManager.getQuest(_235_MimirsElixir.class);
					qs = player.getQuestState(q.getClass());
					if(qs != null)
						qs.exitCurrentQuest(true);
					q.newQuestState(player, Quest.COMPLETED);
				}

				Olympiad.addNoble(player);
				player.setNoble(true);
				player.updatePledgeClass();
				player.updateNobleSkills();
				player.sendPacket(new SkillList(player));
				player.broadcastUserInfo(true);
			}
			else
				player.sendPacket(SystemMsg.INCORRECT_ITEM_COUNT);
		}
		else if(command.equalsIgnoreCase("calculate"))
		{
			if(!player.isQuestContinuationPossible(true))
				return;
			int[] rewards = siegeEvent.calculateReward(player);
			if(rewards == null || rewards[0] == 0)
			{
				showChatWindow(player, 4);
				return;
			}

			NpcHtmlMessage html = new NpcHtmlMessage(player, this, getHtmlPath(npcId, 5, player), 5);
			html.replace("%territory%", HtmlUtils.htmlResidenceName(dominion.getId()));
			html.replace("%badges%", String.valueOf(rewards[0]));
			html.replace("%adena%", String.valueOf(rewards[1]));
			html.replace("%fame%", String.valueOf(rewards[2]));
			player.sendPacket(html);
		}
		else if(command.equalsIgnoreCase("recivelater"))
			showChatWindow(player, getHtmlPath(npcId, 6, player));
		else if(command.equalsIgnoreCase("recive"))
		{
			int[] rewards = siegeEvent.calculateReward(player);
			if(rewards == null || rewards[0] == 0)
			{
				showChatWindow(player, 4);
				return;
			}

			ItemFunctions.addItem(player, badgeId, rewards[0], true);
			ItemFunctions.addItem(player, ItemTemplate.ITEM_ID_ADENA, rewards[1], true);
			if(rewards[2] > 0)
				player.setFame(player.getFame() + rewards[2], "CalcBadges:" + dominion.getId());

			siegeEvent.clearReward(player.getObjectId());
			showChatWindow(player, 7);
		}
		else
			super.onBypassFeedback(player, command);
	}

	@Override
	public String getHtmlPath(int npcId, int val, Player player)
	{
		if(player.getLevel() < 40 || player.getActiveClassClassId().getLevel() <= 2)
			val = 8;
		return val == 0 ? "residence2/dominion/TerritoryManager.htm" : "residence2/dominion/TerritoryManager-" + val + ".htm";
	}
}