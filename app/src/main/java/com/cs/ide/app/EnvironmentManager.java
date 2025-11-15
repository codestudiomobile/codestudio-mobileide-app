package com.cs.ide.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import java.io.OutputStream;
public class EnvironmentManager {
    private static final String TAG = "EnvironmentManager";
    private static final String SCRIPT_NAME = "install_package.codex";
    private static final String SCRIPT_CONTENT = "#!/data/data/com.cs.ide/jniLibs/usr/bin/bash\n\n" +
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
    public static void setupEnvironment(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("codestudio", Context.MODE_PRIVATE);
        String uriString = prefs.getString("saf_uri", null);
        if (uriString == null)
            return;
        Uri safUri = Uri.parse(uriString);
        DocumentFile baseDir = DocumentFile.fromTreeUri(context, safUri);
        if (baseDir == null || !baseDir.exists())
            return;
        DocumentFile scripts = ensureDir(baseDir, "scripts");
        DocumentFile logs = ensureDir(baseDir, "logs");
        DocumentFile terminals = ensureDir(baseDir, "terminals");
        ensureScript(context, scripts);
    }
    private static DocumentFile ensureDir(DocumentFile parent, String name) {
        DocumentFile dir = parent.findFile(name);
        return (dir != null && dir.isDirectory()) ? dir : parent.createDirectory(name);
    }
    private static void ensureScript(Context context, DocumentFile scriptsDir) {
        DocumentFile script = scriptsDir.findFile(SCRIPT_NAME);
        if (script != null && script.isFile())
            return;
        DocumentFile newScript = scriptsDir.createFile("text/x-shellscript", SCRIPT_NAME);
        try (OutputStream out = context.getContentResolver().openOutputStream(newScript.getUri())) {
            out.write(SCRIPT_CONTENT.getBytes());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write install script", e);
        }
    }
}
