package l2ft.gameserver.model.entity.tournament;

import java.util.ArrayList;
import java.util.List;

public class TournamentTeam
{
	private String _name;
	private int _leaderId;
	private List<Integer> _membersIds = new ArrayList<Integer>();
	
	public TournamentTeam(String name)
	{
		_name = name;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public int getLeaderId()
	{
		return _leaderId;
	}
	
	/**
	 * @return all members including leader
	 */
	public List<Integer> getMembers()
	{
		return _membersIds;
	}
	
	/**
	 * Setting @objId Leader and adding it to members
	 */
	public void setLeader(Integer objId)
	{
		_leaderId = objId;
		addMember(objId);
	}
	
	public void addMember(Integer objId)
	{
		if(!_membersIds.contains(objId))
			_membersIds.add(objId);
	}
	
	public void removeMember(Integer objId)
	{
		_membersIds.remove(objId);
	}
}