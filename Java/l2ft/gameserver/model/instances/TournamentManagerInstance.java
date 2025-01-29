package l2ft.gameserver.model.instances;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.TreeMap;

import l2ft.gameserver.model.GameObjectsStorage;
import l2ft.gameserver.model.Party;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.InvisibleType;
import l2ft.gameserver.model.entity.tournament.TournamentManager;
import l2ft.gameserver.model.entity.tournament.TournamentMatch;
import l2ft.gameserver.model.entity.tournament.TournamentTeam;
import l2ft.gameserver.model.entity.tournament.TournamentType;
import l2ft.gameserver.network.l2.s2c.NpcHtmlMessage;
import l2ft.gameserver.templates.npc.NpcTemplate;

public class TournamentManagerInstance extends MerchantInstance
{
	public TournamentManagerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		TournamentManager.getInstance().startTournament();
	}
	
	@Override
	public void onBypassFeedback(Player player, String command) 
	{
		TournamentManager manager = TournamentManager.getInstance();
		StringTokenizer st = new StringTokenizer(command);
		String mainToken = st.nextToken();
		if(mainToken.equals("create"))
		{
			String teamName = st.nextToken();
			
			if(teamName.length()<2)
			{
				player.sendMessage("That team name is too short!");
				return;
			}
			if(teamName.length()>15)
			{
				player.sendMessage("That team name is too long!");
				return;
			}
			
			for(TournamentTeam team : manager.getTeams())
			{
				if(team.getName().equalsIgnoreCase(teamName))
				{
					player.sendMessage("That team name already exists!");
					return;
				}
			}
			if(manager.findMyTeam(player) != null)
			{
				player.sendMessage("You are already a member of another team!");
				return;
			}
			
			TournamentTeam newTeam = new TournamentTeam(teamName);
			newTeam.setLeader(player.getObjectId());
			
			manager.getTeams().add(newTeam);
			
			showEditTeam(player);
			player.sendMessage("Your team has been created!");
		}
		else if(mainToken.equals("edit"))
		{
			if(st.hasMoreTokens())
			{
				String next = st.nextToken();
				if(!st.hasMoreTokens())
				{
					player.sendMessage("You didn't add a name of a player!");
					showEditTeam(player);
					return;
				}
				if(next.equals("add"))
				{
					String playerName = st.nextToken();
					Player newPlayer = GameObjectsStorage.getPlayer(playerName);
					if(newPlayer == null)
					{
						player.sendMessage("That player is not online!");
						showEditTeam(player);
						return;
					}
					if(manager.findMyTeam(newPlayer) != null)
					{
						player.sendMessage("This player already has a team!");
						showEditTeam(player);
						return;
					}
					TournamentTeam team = manager.findMyTeam(player);
					if(team == null)
						return;
					team.addMember(newPlayer.getObjectId());
					player.sendMessage(player.getName()+" has been added to the team!");
					newPlayer.sendMessage("You have been added to "+team.getName()+" team by "+player.getName());
				}
				else if(next.equals("remove"))
				{
					int playerId = Integer.parseInt(st.nextToken());
					Player oldPlayer = GameObjectsStorage.getPlayer(playerId);
					if(oldPlayer == null || manager.findMyTeam(oldPlayer) != manager.findMyTeam(player))
						return;
					TournamentTeam team = manager.findMyTeam(player);
					team.removeMember(oldPlayer.getObjectId());
					player.sendMessage(oldPlayer.getName()+" was removed from the team!");
					oldPlayer.sendMessage("You have been removed from "+team.getName()+" by "+player.getName());
				}
			}
			showEditTeam(player);
		}
		else if(mainToken.equals("leave"))
		{
			TournamentTeam team = manager.findMyTeam(player);
			if(team == null)
			{
				
			}
			else if(team.getLeaderId() == player.getObjectId())
			{
				for(TournamentType type : TournamentType.values())
					for(Party waitingParty : TournamentManager.getInstance().getWaitingList(type))
						if(waitingParty.getTournamentTeam() == team)
						{
							player.sendMessage("Your team is currently on waiting list!");
							return;
						}
				for(TournamentType type : TournamentType.values())
					for(TournamentMatch match : TournamentManager.getInstance().getMatches(type))
						if(match.getFirstTeam().getTournamentTeam() == team || match.getSecondTeam().getTournamentTeam() == team)
						{
							player.sendMessage("Your team is fighting currently!");
							return;
						}
				TournamentManager.getInstance().removeTeam(team);
				manager.getTeams().remove(team);
				player.sendMessage("Team has been dissolved!");
			}
			else
			{
				team.removeMember(player.getObjectId());
				player.sendMessage("You have left your team!");
			}
			showChatWindow(player, "events/36601.htm");
		}
		else if(mainToken.equals("watch"))
		{
			int firstTeamLeaderId = Integer.parseInt(st.nextToken());
			Player leader = GameObjectsStorage.getPlayer(firstTeamLeaderId);
			player.setInvisibleType(InvisibleType.NORMAL);
			if(player.getPet() != null)
				player.getPet().unSummon();
			player.teleToLocation(leader.getX(), leader.getY(), leader.getZ(), leader.getReflection());
		}
		else if(mainToken.equals("sign"))
		{
			TournamentTeam team = TournamentManager.getInstance().findMyTeam(player);
			if(team == null)
			{
				player.sendMessage("You need a team to participate!");
				showChatWindow(player, "events/36601.htm");
				return;
			}
			if(player.getParty() == null)
			{
				player.sendMessage("You need a party to participate!");
				showChatWindow(player, "events/36601.htm");
				return;
			}
			if(player.getParty().getPartyLeader() != player)
			{
				player.sendMessage("Only party leaders may participate!");
				showChatWindow(player, "events/36601.htm");
				return;
			}
			for(TournamentType type : TournamentType.values())
				for(Party signedParty : TournamentManager.getInstance().getWaitingList(type))
					if(signedParty == player.getParty())
					{
						player.sendMessage("Your party is already signed!");
						showChatWindow(player, "events/36601.htm");
						return;
					}
			String stringType = st.nextToken();
			TournamentType type = TournamentType.valueOf(stringType);
			
			TournamentManager.getInstance().signTeam(player.getParty(), type);
		}
		else if(mainToken.equals("Chat"))
		{
			String chatPage = st.nextToken();
			switch(chatPage)
			{
			case "events/36601-04.htm":
				TournamentTeam team = manager.findMyTeam(player);
				String dissoveLeave = team == null ? "Leave" : team.getLeaderId()==player.getObjectId() ? "Dissolve" : "Leave";
				showChatWindow(player, chatPage, "%leave%", dissoveLeave);
				return;
			case "events/36601-06.htm":
				String teams2 = getWaitingTeamsString(manager.getWaitingList(TournamentType.match2));
				String teams3 = getWaitingTeamsString(manager.getWaitingList(TournamentType.match3));
				String teams6 = getWaitingTeamsString(manager.getWaitingList(TournamentType.match6));
				String teams9 = getWaitingTeamsString(manager.getWaitingList(TournamentType.match9));
				
				showChatWindow(player, chatPage, "%registed2%", teams2, "%registed3%", teams3, "%registed6%", teams6, "%registed9%", teams9);
				return;
			case "events/36601-07.htm":
				boolean isGm = player.isGM();
				String matches2 = getMatchListString(isGm, manager.getMatches(TournamentType.match2));
				String matches3 = getMatchListString(isGm, manager.getMatches(TournamentType.match3));
				String matches6 = getMatchListString(isGm, manager.getMatches(TournamentType.match6));
				String matches9 = getMatchListString(isGm, manager.getMatches(TournamentType.match9));
				
				showChatWindow(player, chatPage, "%playing2%", matches2, "%playing3%", matches3, "%playing6%", matches6, "%playing9%", matches9);
				return;
			case "events/36601-08.htm":
				showChatWindow(player, chatPage, "%ranking%", getTeamsRanking());
				return;
			}
			super.onBypassFeedback(player, command);
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void showChatWindow(Player player, String filename, Object... replace) {
		if(filename.equals("events/36601.htm"))
			super.showChatWindow(player, filename, "%points%", TournamentManager.getInstance().getPlayerPoints(player.getObjectId()));
		else
			super.showChatWindow(player, filename, replace);
	}
	
	@Override
	public void showChatWindow(Player player, int val, Object... replace) {
		if(val == 0)
			super.showChatWindow(player, val, "%points%", TournamentManager.getInstance().getPlayerPoints(player.getObjectId()));
		else
			super.showChatWindow(player, val, replace);
	}
	
	private String getWaitingTeamsString(List<Party> teams)
	{
		String html = "<table>";
		for(Party team : teams)
		{
			if(team.getTournamentTeam() == null)
			{
				teams.remove(team);
				continue;
			}
			html += "<tr><td>"+team.getTournamentTeam().getName()+"</td></tr>";
		}
		html += "</table>";
		return html;
	}
	
	public String getMatchListString(boolean isGm, List<TournamentMatch> matchList)
	{
		String html = "<table>";
		for(TournamentMatch match : matchList)
		{
			html += "<tr><td>"+match.getFirstTeam().getTournamentTeam().getName()+"</td><td> VS </td><td>"+match.getSecondTeam().getTournamentTeam().getName()+"</td>";
			if(isGm)
				html += "<td><button value=\"Watch\" action=\"bypass -h npc_%objectId%_watch "+match.getFirstTeam().getPartyLeader().getObjectId()+"\" width=102 height=32 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\"></td>";
			html += "</tr>";
		}
		html += "</table>";
		return html;
	}
	
	private String getTeamsRanking()
	{
		Map<TournamentTeam, Integer> map = new HashMap<>();
        ValueComparator bvc =  new ValueComparator(map);
        TreeMap<TournamentTeam, Integer> sorted_map = new TreeMap<>(bvc);

        for(TournamentTeam team : TournamentManager.getInstance().getTeams())
        	map.put(team, getTeamPoints(team));

        sorted_map.putAll(map);
		String html = "<table><tr><td>Nr. </td><td>Team Name </td><td>Points</td></tr>";
		int place = 1;
        for(Entry<TournamentTeam, Integer> teamPoints : sorted_map.entrySet())
        {
        	html += "<tr><td>"+place+"</td><td>"+teamPoints.getKey().getName()+"</td><td>"+teamPoints.getValue()+"</td></tr>";
        	place ++;
        }
        html += "</table>";
        return html;
	}
	
	private int getTeamPoints(TournamentTeam team)
	{
		int totalPoints = 0;
		for(int member : team.getMembers())
			totalPoints += TournamentManager.getInstance().getPlayerPoints(member);
		return totalPoints;
	}

	private class ValueComparator implements Comparator<TournamentTeam> 
	{
	    Map<TournamentTeam, Integer> _base;
	    public ValueComparator(Map<TournamentTeam, Integer> base) {
	        _base = base;
	    }
	    
	    public int compare(TournamentTeam a, TournamentTeam b) 
	    {
	        return _base.get(b).compareTo(_base.get(a));
	    }
	}
	
	private void showEditTeam(Player player)
	{
		TournamentManager manager = TournamentManager.getInstance();
		TournamentTeam team = manager.findMyTeam(player);
		if(team == null)
		{
			player.sendMessage("You dont have any team!");
			return;
		}
		NpcHtmlMessage msg = new NpcHtmlMessage(player, this);
		msg.setFile("events/"+getNpcId()+"-02.htm");
		
		Player leader = GameObjectsStorage.getPlayer(team.getLeaderId());
		msg.replace("%leader%", (leader == null ? "Offline" : leader.getName()));
		
		String members = "<table>";
		for(int memberId : team.getMembers())
		{
			members += "<tr><td>";
			Player member = GameObjectsStorage.getPlayer(memberId);
			members += member == null ? "Offline" : member.getName();
			members += "</td><td>";
			if(member != null && memberId != team.getLeaderId())
				members += "<button value=\"Remove\" action=\"bypass -h npc_"+getNpcId()+"_edit remove "+memberId+"\" width=160 height=32 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_ct1.button_df\">";
			members += "</td></tr>";
		}
		members += "</table>";
		msg.replace("%members%", members);
		
		player.sendPacket(msg);
	}

}
