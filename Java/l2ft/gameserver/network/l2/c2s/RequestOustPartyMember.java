package l2ft.gameserver.network.l2.c2s;

import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.entity.Reflection;
import l2ft.gameserver.model.entity.DimensionalRift;
import l2ft.gameserver.model.entity.tournament.TournamentManager;
import l2ft.gameserver.network.l2.components.CustomMessage;

public class RequestOustPartyMember extends L2GameClientPacket
{
	//Format: cS
	private String _name;

	@Override
	protected void readImpl()
	{
		_name = readS(16);
	}

	@Override
	protected void runImpl()
	{
		Player activeChar = getClient().getActiveChar();
		if(activeChar == null)
			return;
		
		Party party = activeChar.getParty();
		if(party == null || !activeChar.getParty().isLeader(activeChar))
		{
			activeChar.sendActionFailed();
			return;
		}
				
			if(activeChar.isInOlympiadMode())
			{
				activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestOustPartyMember.CantOutOfGroup", activeChar));
				return;
			}
			
			if(TournamentManager.getInstance().isPlayerAtTournamentStart(activeChar))
			{
				activeChar.sendMessage("You cannot dismiss any member while being on tournament!");
				return;
			}
			
			Player member = party.getPlayerByName(_name);
			
			if(member == activeChar)
			{
				activeChar.sendActionFailed();
				return;
			}
			
			if(member == null)
			{
				activeChar.sendActionFailed();
				return;
			}
			
			Reflection r = party.getReflection();

			if(r != null && r instanceof DimensionalRift && member.getReflection().equals(r))
				activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestOustPartyMember.CantOustInRift", activeChar));
			else if(r != null && !(r instanceof DimensionalRift))
				activeChar.sendMessage(new CustomMessage("l2ft.gameserver.clientpackets.RequestOustPartyMember.CantOustInDungeon", activeChar));
			else
				party.removePartyMember(member, true);
	}
}