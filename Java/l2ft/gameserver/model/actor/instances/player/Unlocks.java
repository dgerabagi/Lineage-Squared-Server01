package l2ft.gameserver.model.actor.instances.player;

import gnu.trove.TIntObjectHashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.database.mysql;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.model.base.Experience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Unlocks
{
	private static final Logger _log = LoggerFactory.getLogger(Unlocks.class);
	private TIntObjectHashMap<UnlockedClass> _unlockedClasses = new TIntObjectHashMap<>();
	private Player _activeChar;
	
	public Unlocks(Player player)
	{
		_activeChar = player;
		loadClasses();
	}
	
	public TIntObjectHashMap<UnlockedClass> getAllUnlocks()
	{
		return _unlockedClasses;
	}
	
	public UnlockedClass getUnlockedClass(int classId)
	{
		return _unlockedClasses.get(classId);
	}
	/**
	 * Needed Change just to change Community Board and make correct save to database
	 */
	public void changeClassVars(int classId, int newLevel, long newExp)
	{
		UnlockedClass classToChange = _unlockedClasses.get(classId);
		if(classToChange == null)
			addNewClass(classId, newLevel, newExp);
		else
		{
			classToChange.setLevel(newLevel);
			classToChange.setExp(newExp);
		}
	}
	
	public void addNewClass(int classId, int level, long exp)
	{
		UnlockedClass newClass = new UnlockedClass(classId, level, exp);
		_unlockedClasses.put(classId, newClass);
		
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("INSERT INTO character_unlocks (Char_id, Class_id, Level, Exp, academy) VALUES (?,?,?,?,?)");
			statement.setInt(1, _activeChar.getObjectId());
			statement.setInt(2, classId);
			statement.setInt(3, level);
			statement.setLong(4, exp);
			statement.setInt(5, 0);
			statement.execute();
		}
		catch(Exception e)
		{
			_log.warn("" + e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}
	
	private void loadClasses()
	{
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM character_unlocks WHERE Char_id=?");
			statement.setInt(1, _activeChar.getObjectId());
			rs = statement.executeQuery();
			while(rs.next())
			{
				int class_id = rs.getInt("Class_id");
				int level = rs.getInt("Level");
				long exp = rs.getLong("Exp");
				boolean academy = rs.getInt("academy") == 1 ? true : false;
				UnlockedClass unlockedClass = new UnlockedClass(class_id, level, exp);
				if(academy)
				{
					unlockedClass.setGrantedAcademy();
				}
				_unlockedClasses.put(class_id, unlockedClass);
			}
		}
		catch(Exception e)
		{
			_log.error("load classes:", e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rs);
		}
	}
	
	public void saveClasses()
	{
		Connection con = null;
		PreparedStatement statement = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			for(Object obj : _unlockedClasses.getValues())
			{
				UnlockedClass unlockedClass = (UnlockedClass)obj;
				statement = con.prepareStatement("UPDATE character_unlocks SET Level=?, Exp=?, academy=? WHERE Char_id=? AND Class_id=?");
				statement.setInt(1, unlockedClass.getLevel());
				statement.setLong(2, unlockedClass.getExp());
				statement.setInt(3, unlockedClass.isGrantedAcademy() ? 1 : 0);
				statement.setInt(4, _activeChar.getObjectId());
				statement.setInt(5, unlockedClass.getId());
				statement.execute();
				DbUtils.close(statement);
			}
		}
		catch(Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement);
		}
	}
	
	public void removeClass(int classId)
	{
		_unlockedClasses.remove(classId);
		mysql.set("DELETE FROM `character_unlocks` WHERE `Char_id`=? AND `Class_id`=? LIMIT 1", _activeChar.getObjectId(), classId);
	}
	
	public class UnlockedClass
	{
		private int _id;
		private int _level;
		private long _exp;
		private boolean _grantedAcademy = false;
		
		private UnlockedClass(int classId, int level, long exp)
		{
			_id = classId;
			_level = level;
			_exp = exp;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public int getLevel()
		{
			return _level;
		}
		
		public long getExp()
		{
			return _exp;
		}
		
		public void setLevel(int level)
		{
			_level = level;
		}
		
		public void setExp(long exp)
		{
			_exp = exp;
			_level = Experience.getLevel(_exp);
		}
		
		public void setGrantedAcademy()
		{
			_grantedAcademy = true;
		}
		
		public boolean isGrantedAcademy()
		{
			return _grantedAcademy;
		}
	}
}
