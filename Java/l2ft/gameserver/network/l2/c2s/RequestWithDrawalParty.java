package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.DimensionalRift;
import l2ft.gameserver.model.entity.tournament.TournamentManager;
import l2ft.gameserver.network.l2.components.CustomMessage;

public class RequestWithDrawalParty extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;

		Party party = activeChar.getParty();
		if(party == null)
		{
			activeChar.sendActionFailed();
			return;
		}

		if(activeChar.isInOlympiadMode())
		{
			activeChar.sendMessage("Вы не можете сейчас выйти из группы."); //TODO [G1ta0] custom message
			return;
		}

		Reflection r = activeChar.getParty().getReflection();
		if(r != null && r instanceof DimensionalRift && activeChar.getReflection().equals(r))
			activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestWithDrawalParty.Rift", activeChar));
		else if(r != null && activeChar.isInCombat())
			activeChar.sendMessage("Вы не можете сейчас выйти из группы.");
		else if(TournamentManager.getInstance().isPlayerAtTournamentStart(activeChar))
			activeChar.sendMessage("You cannot leave your party at tournament!");
		else
			activeChar.leaveParty();
	}
}