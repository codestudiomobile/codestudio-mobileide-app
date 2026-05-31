package com.csmide.app.environment;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.OutputStream;

/**
 * EnvironmentManager handles the initialization of the workspace directory structure
 * using the Storage Access Framework (SAF). It ensures that necessary directories
 * and scripts are present.
 */
public class EnvironmentManager {
	private static final String TAG = "EnvironmentManager";
	private static final String SCRIPT_NAME = "install_package.codex";

	// The content of the bash script used for package installation.
	private static final String SCRIPT_CONTENT = "#!/data/data/com.csmide/jniLibs/usr/bin/bash\n\n" +
			"install_package() {\n" +
			"  pkg_label=\"$1\"\n" +
			"  pkg_search=\"$2\"\n" +
			"  pkg_check=\"$3\"\n" +
			"  graphics=\"$4\"\n\n" +
			"  pkg update -y && pkg upgrade -y\n\n" +
			"  if command -v \"$pkg_check\" >/dev/null 2>&1; then\n" +
			"    echo \"✅ $pkg_label is already installed.\"\n" +
			"    return\n" +
			"  fi\n\n" +
			"  latest_pkg=$(pkg search \"$pkg_search\" | awk '{print $1}' | sort -V | tail -n 1)\n" +
			"  size_info=$(pkg show \"$latest_pkg\" | grep -E 'Size|Installed-Size')\n" +
			"  download_size=$(echo \"$size_info\" | grep 'Size' | awk '{print $2}')\n" +
			"  install_size=$(echo \"$size_info\" | grep 'Installed-Size' | awk '{print $2}')\n\n" +
			"  echo \"📦 $pkg_label installation:\"\n" +
			"  echo \"Archives to be downloaded: $download_size\"\n" +
			"  echo \"Disk space needed after installation: $install_size\"\n" +
			"  echo \"Proceed with installation? [y/n]\"\n" +
			"  read confirm\n\n" +
			"  if [ \"$confirm\" = \"y\" ]; then\n" +
			"    pkg install -y \"$latest_pkg\" | while read -r line; do\n" +
			"      if echo \"$line\" | grep -q 'MB'; then\n" +
			"        echo \"$line\"\n" +
			"      fi\n" +
			"    done\n" +
			"    echo \"✅ $pkg_label installed successfully.\"\n" +
			"  else\n" +
			"    echo \"❌ Installation cancelled.\"\n" +
			"    return\n" +
			"  fi\n\n" +
			"  if [ \"$graphics\" = \"true\" ]; then\n" +
			"    if ! command -v vncserver >/dev/null 2>&1; then\n" +
			"      echo \"🖥️ $pkg_label supports graphical programs.\"\n" +
			"      echo \"Install Graphics Pack (TigerVNC)? [y/n]\"\n" +
			"      read gconfirm\n" +
			"      if [ \"$gconfirm\" = \"y\" ]; then\n" +
			"        pkg install -y tigervnc\n" +
			"        echo \"✅ Graphics Pack installed.\"\n" +
			"      fi\n" +
			"    fi\n" +
			"  fi\n" +
			"}";

	/**
	 * Sets up the workspace environment by creating required folders and scripts
	 * based on the SAF URI stored in preferences.
	 *
	 * @param context The application context.
	 */
	public static void setupEnvironment(Context context) {
		SharedPreferences prefs = context.getSharedPreferences("codestudio", Context.MODE_PRIVATE);
		String uriString = prefs.getString("saf_uri", null);
		if (uriString == null) {
			return;
		}

		Uri safUri = Uri.parse(uriString);
		DocumentFile baseDir = DocumentFile.fromTreeUri(context, safUri);
		if (baseDir == null || !baseDir.exists()) {
			return;
		}

		// Ensure sub-directories exist
		DocumentFile scripts = ensureDir(baseDir, "scripts");
		DocumentFile logs = ensureDir(baseDir, "logs");
		DocumentFile terminals = ensureDir(baseDir, "terminals");

		// Ensure the installation script is present
		if (scripts != null) {
			ensureScript(context, scripts);
		}
	}

	// --- Private Helper Methods ---

	/**
	 * Ensures that a directory with the given name exists under the parent directory.
	 *
	 * @param parent The parent DocumentFile.
	 * @param name   The name of the directory.
	 * @return The DocumentFile representing the directory.
	 */
	private static DocumentFile ensureDir(DocumentFile parent, String name) {
		DocumentFile dir = parent.findFile(name);
		if (dir != null && dir.isDirectory()) {
			return dir;
		}
		return parent.createDirectory(name);
	}

	/**
	 * Ensures that the installation script exists in the scripts directory.
	 *
	 * @param context    The application context.
	 * @param scriptsDir The DocumentFile for the scripts directory.
	 */
	private static void ensureScript(Context context, DocumentFile scriptsDir) {
		DocumentFile script = scriptsDir.findFile(SCRIPT_NAME);
		if (script != null && script.isFile()) {
			return;
		}

		DocumentFile newScript = scriptsDir.createFile("text/x-shellscript", SCRIPT_NAME);
		if (newScript == null) {
			return;
		}

		try (OutputStream out = context.getContentResolver().openOutputStream(newScript.getUri())) {
			if (out != null) {
				out.write(SCRIPT_CONTENT.getBytes());
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to write install script", e);
		}
	}
}
