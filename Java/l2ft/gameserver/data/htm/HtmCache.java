package l2ft.gameserver.data.htm;

import java.io.File;
import java.io.IOException;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import l2ft.gameserver.Config;
import l2ft.gameserver.model.Player;
import l2ft.gameserver.utils.Language;
import l2ft.gameserver.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Кэширование html диалогов.
 * 
 * @author G1ta0
 * @reworked VISTALL
 * В кеше список вот так
 * admin/admhelp.htm
 * admin/admin.htm
 * admin/admserver.htm
 * admin/banmenu.htm
 * admin/charmanage.htm
 */
public class HtmCache
{
	public static final int DISABLED = 0; // все диалоги кешируются при загрузке сервера
	public static final int LAZY = 1; // диалоги кешируются по мере обращения
	public static final int ENABLED = 2;  // кеширование отключено (только для тестирования)

	private static final Logger _log = LoggerFactory.getLogger(HtmCache.class);

	private final static HtmCache _instance = new HtmCache();

	public static HtmCache getInstance()
	{
		return _instance;
	}

	private Cache _cache = null;

	private HtmCache()
	{
		_cache = CacheManager.getInstance().getCache(getClass().getName() + "." + Language.ENGLISH.name());
	}

	public void reload()
	{
		clear();

		switch (Config.HTM_CACHE_MODE)
		{
			case ENABLED:
				File root = new File(Config.DATAPACK_ROOT, "data/html");
				if(!root.exists())
				{
					_log.info("HtmCache: Not find html dir");
					return;
				}
				load(root, root.getAbsolutePath() + "/");
				_log.info(String.format("HtmCache: parsing %d documents; lang: %s.", _cache.getSize(), Language.ENGLISH));
				break;
			case LAZY:
				_log.info("HtmCache: lazy cache mode.");
				break;
			case DISABLED:
				_log.info("HtmCache: disabled.");
				break;
		}
	}

	private void load(File f, final String rootPath)
	{
		if (!f.exists())
		{
			_log.info("HtmCache: dir not exists: " + f);
			return;
		}
		File[] files = f.listFiles();

		//FIXME [VISTALL] может лучше использовать Apache FileUtils?
		for (File file : files)
		{
			if (file.isDirectory())
				load(file, rootPath);
			else
			{
				if (file.getName().endsWith(".htm"))
					try
					{
						putContent(file, rootPath);
					}
					catch (IOException e)
					{
						_log.info("HtmCache: file error" + e, e);
					}
			}
		}
	}

	public void putContent(File f, final String rootPath)  throws IOException
	{
		String content = FileUtils.readFileToString(f, "UTF-8");

		String path = f.getAbsolutePath().substring(rootPath.length()).replace("\\", "/");

		_cache.put(new Element(path.toLowerCase(), Strings.bbParse(content)));
	}

	public String getNotNull(String fileName, Player player)
	{
		String cache = getCache(fileName, Language.ENGLISH);

		if (StringUtils.isEmpty(cache))
			cache = "Dialog not found: " + fileName;

		return cache;
	}

	public String getNullable(String fileName, Player player)
	{
		String cache = getCache(fileName, Language.ENGLISH);

		if (StringUtils.isEmpty(cache))
			return null;

		return cache;
	}

	private String getCache(String file, Language lang)
	{
		if(file == null)
			return null;

		final String fileLower = file.toLowerCase();
		String cache = get(lang, fileLower);

		if (cache == null)
		{
			switch (Config.HTM_CACHE_MODE)
			{
				case ENABLED:
					break;
				case LAZY:
					cache = loadLazy(lang, file);
					if(cache == null && lang != Language.ENGLISH)
						cache = loadLazy(Language.ENGLISH, file);
					break;
				case DISABLED:
					cache = loadDisabled(lang, file);
					if(cache == null && lang != Language.ENGLISH)
						cache = loadDisabled(Language.ENGLISH, file);
					break;
			}
		}

		return cache;
	}

	private String loadDisabled(Language lang, String file)
	{
		String cache = null;
		File f = new File(Config.DATAPACK_ROOT, "data/html-" + lang.getShortName() + "/" + file);
		if(f.exists())
			try
			{
				cache = FileUtils.readFileToString(f, "UTF-8");
				cache = Strings.bbParse(cache);
			}
			catch (IOException e)
			{
				_log.info("HtmCache: File error: " + file + " lang: " + lang);
			}
		return cache;
	}

	private String loadLazy(Language lang, String file)
	{
		String cache = null;
		File f = new File(Config.DATAPACK_ROOT, "data/html/" + file);
		if(f.exists())
			try
			{
				cache = FileUtils.readFileToString(f, "UTF-8");
				cache = Strings.bbParse(cache);

				_cache.put(new Element(file, cache));
			}
			catch (IOException e)
			{
				_log.info("HtmCache: File error: " + file);
			}
		return cache;
	}

 	private String get(Language lang, String f)
	{
		Element element = _cache.get(f);

		if(element == null)
			element = _cache.get(f);

		return element == null ? null : (String)element.getObjectValue();
	}

	public void clear()
	{
		_cache.removeAll();
	}
}
