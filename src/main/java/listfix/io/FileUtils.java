package listfix.io;

import listfix.model.playlists.Playlist;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileUtils
{
  public static File findDeepestPathToExist(File file)
  {
    if (file == null || file.exists())
    {
      return file;
    }
    return findDeepestPathToExist(file.getParentFile());
  }

  public static boolean isMediaFile(File file)
  {
    return isMediaFile(file.getName());
  }

  public static boolean isMediaFile(Path file)
  {
    return isMediaFile(file.getFileName().toString());
  }

  public static boolean isMediaFile(String filename)
  {
    String extension = getFileExtension(filename);
    if (extension != null)
    {
      return Playlist.mediaExtensions.contains(extension.toLowerCase());
    }
    return false;
  }

  public static boolean isURL(String trackText)
  {
    try
    {
      if (trackText.startsWith("file:"))
      {
        return false;
      }
      else
      {
        new URL(trackText).toURI();
        return true;
      }
    }
    catch (MalformedURLException | URISyntaxException e)
    {
      return false;
    }
  }

  public static String getRelativePath(File trackPath, File playlistPath)
  {
    return getRelativePath(trackPath.toPath(), playlistPath.toPath()).toString();
  }

  public static Path getRelativePath(Path trackPath, Path playlistPath)
  {
    Path offset = Files.isDirectory(playlistPath) ? playlistPath : playlistPath.getParent();
    String uncPath = UNCFile.from(offset.normalize().toFile()).getUNCPath();
    try
    {
      return Path.of(uncPath).relativize(trackPath);
    }
    catch (IllegalArgumentException exception)
    {
      return trackPath;
    }
  }

  public static String replaceInvalidWindowsFileSystemCharsWithChar(String input, char replacement)
  {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < input.length(); i++)
    {
      char x = input.charAt(i);
      if (Constants.INVALID_WINDOWS_FILENAME_CHARACTERS.indexOf(x) > -1)
      {
        result.append(replacement);
      }
      else
      {
        result.append(x);
      }
    }
    return result.toString();
  }

  /**
   * Delete recursive
   *
   * @param directory Folder to delete
   */
  public static void deleteDirectory(Path directory) throws IOException
  {
    try (Stream<Path> walk = Files.walk(directory))
    {
      walk
        .map(Path::toFile)
        .forEach(File::delete);
    }
  }

  /**
   * Extract the extension from the provided filename
   *
   * @param filename Filename
   * @return extension without leading dot, e.g.: "mp3"
   */
  public static String getFileExtension(String filename)
  {
    int index = filename.lastIndexOf(".");
    return index == -1 ? null : filename.substring(filename.lastIndexOf(".") + 1);
  }

}
