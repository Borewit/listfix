/*
 * listFix() - Fix Broken Playlists!
 * Copyright (C) 2001-2008 Jeremy Caron
 * 
 * This file is part of listFix().
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, please see http://www.gnu.org/licenses/
 */

package listfix.model;

import java.util.Hashtable;
import javax.swing.UIManager;

/**
 *
 * @author jcaron
 */
public class AppOptions 
{
    private boolean savePlaylistsWithRelativePaths = false;
    private boolean autoLocateEntriesOnPlaylistLoad = false;
	private boolean autoRefreshMediaLibraryOnStartup = false;
	private boolean alwaysUseUNCPaths = false;
    private int maxPlaylistHistoryEntries = 5;
	private String lookAndFeel = UIManager.getInstalledLookAndFeels()[0].getClassName();
	
    public static final Hashtable<String,Integer> optionEnumTable = new Hashtable<String,Integer>();    
    static
    {
        optionEnumTable.put("SAVE_RELATIVE_REFERENCES", AppOptionsEnum.SAVE_RELATIVE_REFERENCES);
        optionEnumTable.put("AUTO_FIND_ENTRIES_ON_PLAYLIST_LOAD", AppOptionsEnum.AUTO_FIND_ENTRIES_ON_PLAYLIST_LOAD);
        optionEnumTable.put("MAX_PLAYLIST_HISTORY_SIZE", AppOptionsEnum.MAX_PLAYLIST_HISTORY_SIZE);
		optionEnumTable.put("AUTO_REFRESH_MEDIA_LIBRARY_ON_LOAD", AppOptionsEnum.AUTO_REFRESH_MEDIA_LIBRARY_ON_LOAD);
		optionEnumTable.put("LOOK_AND_FEEL", AppOptionsEnum.LOOK_AND_FEEL);
		optionEnumTable.put("ALWAYS_USE_UNC_PATHS", AppOptionsEnum.ALWAYS_USE_UNC_PATHS);
    }
    
    public AppOptions()
    {
        // creates an AppOptions instance with the default settings.
    }
    
    public AppOptions(int maxPlaylistHistoryEntries, boolean autoLocateEntriesOnPlaylistLoad, 
			boolean savePlaylistsWithRelativePaths, boolean autoRefreshMediaLibraryOnStartup, String lookAndFeel, boolean alwaysUseUNCPaths)
    {
        this.autoLocateEntriesOnPlaylistLoad = autoLocateEntriesOnPlaylistLoad;
        this.maxPlaylistHistoryEntries = maxPlaylistHistoryEntries;
        this.savePlaylistsWithRelativePaths = savePlaylistsWithRelativePaths;
		this.autoRefreshMediaLibraryOnStartup = autoRefreshMediaLibraryOnStartup;
		this.lookAndFeel = lookAndFeel;
		this.alwaysUseUNCPaths = alwaysUseUNCPaths;
    }
    
    public boolean getAutoLocateEntriesOnPlaylistLoad()
    {
        return autoLocateEntriesOnPlaylistLoad;
    }

    public void setAutoLocateEntriesOnPlaylistLoad(boolean autoLocateEntriesOnPlaylistLoad)
    {
        this.autoLocateEntriesOnPlaylistLoad = autoLocateEntriesOnPlaylistLoad;
    }

    public int getMaxPlaylistHistoryEntries()
    {
        return maxPlaylistHistoryEntries;
    }

	public void setLookAndFeel(String lookAndFeel)
	{
		this.lookAndFeel = lookAndFeel;
	}
	
	public String getLookAndFeel()
	{
		return lookAndFeel;
	}
	
    public void setMaxPlaylistHistoryEntries(int maxPlaylistHistoryEntries)
    {
        this.maxPlaylistHistoryEntries = maxPlaylistHistoryEntries;
    }

	public void setAutoRefreshMediaLibraryOnStartup(boolean autoRefreshMediaLibraryOnStartup)
	{
		this.autoRefreshMediaLibraryOnStartup = autoRefreshMediaLibraryOnStartup;
	}
	
	public boolean getAutoRefreshMediaLibraryOnStartup()
    {
        return autoRefreshMediaLibraryOnStartup;
    }

    public boolean getSavePlaylistsWithRelativePaths()
    {
        return savePlaylistsWithRelativePaths;
    }

    public void setSavePlaylistsWithRelativePaths(boolean savePlaylistsWithRelativePaths)
    {
        this.savePlaylistsWithRelativePaths = savePlaylistsWithRelativePaths;
    }

	public boolean getAlwaysUseUNCPaths()
	{
		return alwaysUseUNCPaths;
	}

	public void setAlwaysUseUNCPaths(boolean alwaysUseUNCPaths)
	{
		this.alwaysUseUNCPaths = alwaysUseUNCPaths;
	}
}