package listfix.view;

import listfix.config.IApplicationConfiguration;
import listfix.model.playlists.Playlist;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public interface IListFixGui
{
  void openPlaylist(Path playlistFile);

  void openNewTabForPlaylist(Playlist playlist);

  void savePlaylist(Playlist playlist) throws InterruptedException, IOException, ExecutionException;

  void savePlaylistAs(Playlist playlist, File saveAsPath) throws InterruptedException, IOException, ExecutionException;

  boolean showPlaylistSaveAsDialog(Playlist list);

  IApplicationConfiguration getApplicationConfiguration();
}