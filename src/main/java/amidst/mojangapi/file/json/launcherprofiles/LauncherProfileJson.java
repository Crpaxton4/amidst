package amidst.mojangapi.file.json.launcherprofiles;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

import amidst.documentation.GsonConstructor;
import amidst.documentation.Immutable;
import amidst.documentation.NotNull;
import amidst.logging.Log;
import amidst.mojangapi.MojangApi;
import amidst.mojangapi.file.directory.ProfileDirectory;
import amidst.mojangapi.file.directory.VersionDirectory;
import amidst.mojangapi.file.json.ReleaseType;
import amidst.mojangapi.file.json.versionlist.VersionListJson;

@Immutable
public class LauncherProfileJson {
	/**
	 * Some Minecraft installations have a profile with the key "(Default)" and
	 * no properties in the actual profile object. The JSON looks like this:
	 * 
	 * "(Default)": {},
	 * 
	 * This profile has the name null. Also, it cannot be deleted from the
	 * launcher. I guess this is a bug in the minecraft launcher. However, the
	 * minecraft launcher displays it with an empty string as name and it uses
	 * the latest stable release for it, so we do the same.
	 */
	private volatile String name = "";
	private volatile String lastVersionId;
	private volatile String gameDir;
	private volatile List<ReleaseType> allowedReleaseTypes = Arrays
			.asList(ReleaseType.RELEASE);

	/**
	 * @return true if the path is a UNC path
	 * (Uniform Naming Convention, sometimes called a "network path")
	 */
	private boolean isUncPath(String path) {
		// UNC path:                \\server\share\foo
		// Absolute path:           C:\foo
		// Relative path:           foo
		// Directory_relative path: \foo
		// Drive_relative path:     C:foo		
		return path.startsWith("\\\\");
	}
	
	@GsonConstructor
	public LauncherProfileJson() {
	}

	public String getName() {
		return name;
	}

	public String getLastVersionId() {
		return lastVersionId;
	}

	public String getGameDir() {
		return gameDir;
	}

	@NotNull
	public ProfileDirectory createValidProfileDirectory(MojangApi mojangApi)
			throws FileNotFoundException {
		if (gameDir != null) {
			
			ProfileDirectory result = new ProfileDirectory(new File(gameDir));
			
			if (!result.isValid()) {
				String gameRoot = mojangApi.getDotMinecraftDirectory().getRoot().getPath();
				if (isUncPath(gameRoot)) {
					// Amidst has been passed a 'network path' to the DotMinecraftDirectory.
					if (gameDir.endsWith(".minecraft_snapshots") && gameRoot.endsWith(".minecraft") && !isUncPath(gameDir)) {
						// The 'network path' passed to the DotMinecraftDirectory allows for
						// the possibility that the DotMinecraftDirectory is located on a
						// different machine.
						// Meanwhile, the gameDir from launcher_profile.json is a local path for
						// the computer the DotMinecraftDirectory is located on.
						//
						// When we tried to access the gameDir we found it didn't exist - which 
						// we expect to happen if the gameDir holds a local path for a different 
						// computer.
						//
						// As far as I can tell, there's no nice way in cross-platform Java to 
						// resolve a network path into a server's local path, which would be the
						// first step in re-building a network path to access a remote gameDir, so 
						// instead I'm only handling a common and known case...
						//
						// By default, across all platforms, normal profiles are kept in the .minecraft 
						// directory, while snapshot profiles are now kept separate in the 
						// .minecraft_snapshots directory. So if gameRoot is a network path to
						// the .minecraft directory, and launcher_profile.json specifies a local path
						// that ends in .minecraft_snapshots, then there's a damn good chance it
						// can be accessed via the same network path as the .minecraft directory.
						Log.i("Profile directory for " + name + " wasn't found, attempting a UNC path: " + gameRoot + "_snapshots");
						result = new ProfileDirectory(new File(gameRoot + "_snapshots"));
					}
				}	
			}
			
			if (result.isValid()) {
				return result;
			} else {
				throw new FileNotFoundException(
						"cannot find valid profile directory for launcher profile '"
								+ name + "': " + gameDir);
			}
		} else {
			return new ProfileDirectory(mojangApi.getDotMinecraftDirectory()
					.getRoot());
		}
	}

	@NotNull
	public VersionDirectory createValidVersionDirectory(MojangApi mojangApi)
			throws FileNotFoundException {
		VersionListJson versionList = mojangApi.getVersionList();
		if (lastVersionId != null) {
			VersionDirectory result = mojangApi
					.createVersionDirectory(lastVersionId);
			if (result.isValid()) {
				return result;
			}
		} else {
			VersionDirectory result = versionList
					.tryFindFirstValidVersionDirectory(allowedReleaseTypes,
							mojangApi);
			if (result != null) {
				return result;
			}
		}
		throw new FileNotFoundException(
				"cannot find valid version directory for launcher profile '"
						+ name + "'");
	}
}