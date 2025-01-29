package l2ft.gameserver.tables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import l2ft.commons.dbutils.DbUtils;
import l2ft.gameserver.database.DatabaseFactory;
import l2ft.gameserver.model.base.ClassId;
import l2ft.gameserver.templates.PlayerTemplate;
import l2ft.gameserver.templates.StatsSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings( { "nls", "unqualified-field-access", "boxing" })
public class CharTemplateTable
{
	private static final Logger _log = LoggerFactory.getLogger(CharTemplateTable.class);
	
	private static CharTemplateTable _instance;

	private Map<Integer, double[]> _sizes;

	public static CharTemplateTable getInstance()
	{
		if(_instance == null)
			_instance = new CharTemplateTable();
		return _instance;
	}

	private CharTemplateTable()
	{
		_sizes = new HashMap<>();

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM char_templates");
			rset = statement.executeQuery();
			while(rset.next())
			{
				double[] sizes = new double[5];//Race, Mradius, Mheight, Fradius, Fheight
				sizes[0] = (double) rset.getInt("RaceId");
				sizes[1] = rset.getDouble("m_col_r");
				sizes[2] = rset.getDouble("m_col_h");
				sizes[3] = rset.getDouble("f_col_r");
				sizes[4] = rset.getDouble("f_col_h");
				int classId = rset.getInt("ClassId");
				_sizes.put(classId, sizes);
			}
			DbUtils.close(statement);
		}
		catch(Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}
	}

	public PlayerTemplate getTemplate(int baseClassId, int classId, boolean secondaryMage, boolean female)
	{
		return getTemplate(baseClassId, ClassId.values()[classId], secondaryMage, female);
	}

	public PlayerTemplate getTemplate(int baseClassId, ClassId realClassId, boolean secondaryMage, boolean female)
	{
		PlayerTemplate template = null;
		double[] sizes = _sizes.get(baseClassId);
		int realId = realClassId.getId();

		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rset = null;
		try
		{
			con = DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT * FROM class_list, char_templates, lvlupgain WHERE class_list.id="+realId+" AND char_templates.classId="+realId+" AND lvlupgain.classId="+realId);
			rset = statement.executeQuery();
			if(rset.next())
			{
				StatsSet set = new StatsSet();
				set.set("classId", baseClassId);
				set.set("raceId", sizes[0]);////Race, Mradius, Mheight, Fradius, Fheight
				set.set("className", realClassId.name());
				set.set("baseSTR", rset.getInt("char_templates.STR"));
				set.set("baseCON", rset.getInt("char_templates.CON"));
				set.set("baseDEX", rset.getInt("char_templates.DEX"));
				set.set("baseINT", rset.getInt("char_templates._INT"));
				set.set("baseWIT", rset.getInt("char_templates.WIT"));
				set.set("baseMEN", rset.getInt("char_templates.MEN"));
				set.set("baseHpMax", rset.getDouble("lvlupgain.defaultHpBase"));
				set.set("lvlHpAdd", rset.getDouble("lvlupgain.defaultHpAdd"));
				set.set("lvlHpMod", rset.getDouble("lvlupgain.defaultHpMod"));
				set.set("baseMpMax", rset.getDouble("lvlupgain.defaultMpBase"));
				set.set("baseCpMax", rset.getDouble("lvlupgain.defaultCpBase"));
				set.set("lvlCpAdd", rset.getDouble("lvlupgain.defaultCpAdd"));
				set.set("lvlCpMod", rset.getDouble("lvlupgain.defaultCpMod"));
				set.set("lvlMpAdd", rset.getDouble("lvlupgain.defaultMpAdd"));
				set.set("lvlMpMod", rset.getDouble("lvlupgain.defaultMpMod"));
				set.set("baseHpReg", 0.01);
				set.set("baseCpReg", 0.01);
				set.set("baseMpReg", 0.01);
				set.set("basePAtk", rset.getInt("char_templates.p_atk"));
				set.set("basePDef", /* classId.isMage()? 77 : 129 */rset.getInt("char_templates.p_def"));
				set.set("baseMAtk", rset.getInt("char_templates.m_atk"));
				set.set("baseMDef", 41 /* rset.getInt("char_templates.m_def") */);
				set.set("classBaseLevel", rset.getInt("lvlupgain.class_lvl"));
				set.set("basePAtkSpd", rset.getInt("char_templates.p_spd"));
				set.set("baseMAtkSpd", !realClassId.isMage() && secondaryMage ? 249 : ( !realClassId.isMage() ? 333 : 166) /* rset.getInt("char_templates.m_spd") */);
				set.set("baseCritRate", rset.getInt("char_templates.critical"));
				set.set("baseWalkSpd", rset.getInt("char_templates.walk_spd"));
				set.set("baseRunSpd", rset.getInt("char_templates.run_spd"));
				set.set("baseShldDef", 0);
				set.set("baseShldRate", 0);
				set.set("baseAtkRange", 40);
				set.set("saveSet", "true");
				
				if(!female)//Male
				{
					set.set("isMale", true);
					set.set("collision_radius", sizes[1]);
					set.set("collision_height", sizes[2]);
					template = new PlayerTemplate(set);
				}
				else//Female
				{
					set.set("isMale", false);
					set.set("collision_radius", sizes[3]);
					set.set("collision_height", sizes[4]);
					template = new PlayerTemplate(set);
				}
			}
		}
		catch(Exception e)
		{
			_log.error("", e);
		}
		finally
		{
			DbUtils.closeQuietly(con, statement, rset);
		}

		return template;
	}
}
