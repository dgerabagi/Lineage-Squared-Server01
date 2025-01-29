package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.data.xml.holder.EventHolder;
import l2ft.gameserver.model.pledge.Clan;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.Request;
import l2ft.gameserver.model.Request.L2RequestType;
import l2ft.gameserver.model.entity.events.impl.DominionSiegeEvent;
import l2ft.gameserver.model.pledge.SubUnit;
import l2ft.gameserver.model.pledge.UnitMember;
import l2ft.gameserver.network.l2.components.SystemMsg;
import l2ft.gameserver.network.l2.s2c.JoinPledge;
import l2ft.gameserver.network.l2.s2c.PledgeShowInfoUpdate;
import l2ft.gameserver.network.l2.s2c.PledgeShowMemberListAdd;
import l2ft.gameserver.network.l2.s2c.PledgeSkillList;
import l2ft.gameserver.network.l2.s2c.SkillList;
import l2ft.gameserver.network.l2.s2c.SystemMessage2;

public class RequestAnswerJoinPledge extends L2GameClientPacket
{
	private int _response;

	@Override
	protected void readImpl()
	{
		_response = _buf.hasRemaining() ? readD() : 0;
	}

	@Override
	protected void runImpl()
	{
		Player player = getClient().getActiveChar();
		if(player == null)
			return;

		Request request = player.getRequest();
		if(request == null || !request.isTypeOf(L2RequestType.CLAN))
			return;

		if(!request.isInProgress())
		{
			request.cancel();
			player.sendActionFailed();
			return;
		}

		if(player.isOutOfControl())
		{
			request.cancel();
			player.sendActionFailed();
			return;
		}

		Player requestor = request.getRequestor();
		if(requestor == null)
		{
			request.cancel();
			player.sendPacket(SystemMsg.THAT_PLAYER_IS_NOT_ONLINE);
			player.sendActionFailed();
			return;
		}

		if(requestor.getRequest() != request)
		{
			request.cancel();
			player.sendActionFailed();
			return;
		}

		Clan clan = requestor.getClan();
		if(clan == null)
		{
			request.cancel();
			player.sendActionFailed();
			return;
		}

		if(_response == 0)
		{
			request.cancel();
			requestor.sendPacket(new SystemMessage2(SystemMsg.S1_DECLINED_YOUR_CLAN_INVITATION).addName(player));
			return;
		}

		if(!player.canJoinClan())
		{
			request.cancel();
			player.sendPacket(SystemMsg.AFTER_LEAVING_OR_HAVING_BEEN_DISMISSED_FROM_A_CLAN_YOU_MUST_WAIT_AT_LEAST_A_DAY_BEFORE_JOINING_ANOTHER_CLAN);
			return;
		}

		try
		{
			player.sendPacket(new JoinPledge(requestor.getClanId()));

			int pledgeType = request.getInteger("pledgeType");
			SubUnit subUnit = clan.getSubUnit(pledgeType);
			if(subUnit == null)
				return;

			UnitMember member = new UnitMember(clan, player.getName(), player.getTitle(), player.getLevel(), player.getActiveClass().getFirstClassId(), player.getObjectId(), pledgeType, player.getPowerGrade(), player.getApprentice(), player.getSex(), Clan.SUBUNIT_NONE);
			subUnit.addUnitMember(member);

			player.setPledgeType(pledgeType);
			player.setClan(clan);

			member.setPlayerInstance(player, false);

			if(pledgeType == Clan.SUBUNIT_ACADEMY)
				player.setLvlJoinedAcademy(player.getLevel());

			member.setPowerGrade(clan.getAffiliationRank(player.getPledgeType()));

			clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListAdd(member), player);
			clan.broadcastToOnlineMembers(new SystemMessage2(SystemMsg.S1_HAS_JOINED_THE_CLAN).addString(player.getName()), new PledgeShowInfoUpdate(clan));

			// this activates the clan tab on the new member
			player.sendPacket(SystemMsg.ENTERED_THE_CLAN);
			player.sendPacket(player.getClan().listAll());
			player.setLeaveClanTime(0);
			player.updatePledgeClass();

			// Đ´ĐľĐ±Đ°Đ˛Đ»ŃŹĐµĐĽ Ń�ĐşĐ¸Đ»Ń‹ Đ¸ĐłŃ€ĐľĐşŃ�, Ń‚ĐľĐş Ń‚Đ¸Ń…Đľ
			clan.addSkillsQuietly(player);
			// ĐľŃ‚ĐľĐ±Ń€Đ°Đ¶ĐµĐĽ
			player.sendPacket(new PledgeSkillList(clan));
			player.sendPacket(new SkillList(player));

			EventHolder.getInstance().findEvent(player);
			if(clan.getWarDominion() > 0) // Đ±Đ°Đł ĐľŃ„Ń„Đ°, ĐżĐľŃ�Đ»Đµ Đ˛Ń�Ń‚Ń�ĐżĐ° Đ˛ ĐşĐ»Đ°Đ˝ Đ˝Ń�Đ¶ĐµĐ˝ Ń€ĐµĐ»ĐľĐł Đ´Đ»ŃŹ ĐşĐ˛ĐµŃ�Ń‚ĐľĐ˛
			{
				DominionSiegeEvent siegeEvent = player.getEvent(DominionSiegeEvent.class);

				siegeEvent.updatePlayer(player, true);
			}
			else
				player.broadcastCharInfo();

			player.store(false);
		}
		finally
		{
			request.done();
		}
	}
}