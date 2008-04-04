/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package listfix.model;

import java.util.Hashtable;

/**
 *
 * @author jcaron
 */
public class AppOptions 
{
    private boolean savePlaylistsWithRelativePaths = false;
    private boolean autoLocateEntriesOnPlaylistLoad = false;
	private boolean autoRefreshMediaLibraryOnStartup = false;
    private int maxPlaylistHistoryEntries = 5;
	private String lookAndFeel = "";
	
    public static final Hashtable<String,Integer> optionEnumTable = new Hashtable<String,Integer>();    
    static
    {
        optionEnumTable.put("SAVE_RELATIVE_REFERENCES", AppOptionsEnum.SAVE_RELATIVE_REFERENCES);
        optionEnumTable.put("AUTO_FIND_ENTRIES_ON_PLAYLIST_LOAD", AppOptionsEnum.AUTO_FIND_ENTRIES_ON_PLAYLIST_LOAD);
        optionEnumTable.put("MAX_PLAYLIST_HISTORY_SIZE", AppOptionsEnum.MAX_PLAYLIST_HISTORY_SIZE);
		optionEnumTable.put("AUTO_REFRESH_MEDIA_LIBRARY_ON_LOAD", AppOptionsEnum.AUTO_REFRESH_MEDIA_LIBRARY_ON_LOAD);
		optionEnumTable.put("LOOK_AND_FEEL", AppOptionsEnum.LOOK_AND_FEEL);
    }
    
    public AppOptions()
    {
        // creates an AppOptions instance with the default settings.
    }
    
    public AppOptions(int maxPlaylistHistoryEntries, boolean autoLocateEntriesOnPlaylistLoad, 
			boolean savePlaylistsWithRelativePaths, boolean autoRefreshMediaLibraryOnStartup, String lookAndFeel)
    {
        this.autoLocateEntriesOnPlaylistLoad = autoLocateEntriesOnPlaylistLoad;
        this.maxPlaylistHistoryEntries = maxPlaylistHistoryEntries;
        this.savePlaylistsWithRelativePaths = savePlaylistsWithRelativePaths;
		this.autoRefreshMediaLibraryOnStartup = autoRefreshMediaLibraryOnStartup;
		this.lookAndFeel = lookAndFeel;
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
}
