package com.csmide.app.execution;

import android.content.Context;
import android.util.Log;

import com.csmide.app.models.LanguagePack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * CommandFetcher handles the loading and parsing of command configurations from
 * JSON files located in assets or shared preferences.
 */
public class CommandFetcher {
	private static final String TAG = "CommandFetcher";
	private static final String CONFIG_FILE_NAME = "commands.json";
	private static final String PREF_NAME = "CommandConfigPrefs";
	private static final String PREF_KEY_UPDATED_CONFIG = "updated_commands_json";

	public final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final Context context;

	/**
	 * Constructs a new CommandFetcher.
	 *
	 * @param context The application context.
	 */
	public CommandFetcher(Context context) {
		this.context = context.getApplicationContext();
	}

	/**
	 * Loads all language packs asynchronously.
	 *
	 * @return A Future containing a list of LanguagePack objects.
	 */
	public Future<List<LanguagePack>> loadAllLanguagePacksAsync() {
		// yet to complete
		return executorService.submit(() -> {
			List<LanguagePack> packs = new ArrayList<>();

			// Pre-fetch installed packages list from dpkg
			java.util.Set<String> installedPackages = getInstalledPackagesList();

			String configJson = loadConfigurationJson();
			if (configJson == null) return packs;
			try {
				JSONObject fullConfig = new JSONObject(configJson);
				JSONObject env = fullConfig.optJSONObject("termux_programming_environment");
				if (env == null) return packs;
				JSONObject languages = env.optJSONObject("languages");
				if (languages == null) return packs;

				String[] categories = {"interpreted", "compiled", "shell_scripting", "web", "tools"};
				for (String category : categories) {
					JSONObject catObj = languages.optJSONObject(category);
					if (catObj == null) continue;
					Iterator<String> langKeys = catObj.keys();
					while (langKeys.hasNext()) {
						String langKey = langKeys.next();
						JSONObject lang = catObj.getJSONObject(langKey);
						String name = capitalize(langKey);
						String pkgName = lang.optString("package", langKey);

						// Add runtime package if available
						String install = lang.optString("install", "");
						String uninstall = lang.optString("uninstall", "");
						String checkInstalled = lang.optString("check_installed", "");

						int status = installedPackages.contains(pkgName) ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE;

						LanguagePack runtimePack = null;
						if (!install.isEmpty()) {
							runtimePack = new LanguagePack(
									langKey,
									name + " Runtime",
									install,
									uninstall,
									checkInstalled,
									status,
									LanguagePack.TYPE_RUNTIME
							);
							packs.add(runtimePack);
						}

						// Add suggestion pack if available
						String suggestionUrl = lang.optString("suggestion_pack", "");
						if (!suggestionUrl.isEmpty()) {
							String langName = langKey.replace("_suggestions", "");
							File langDir = new File(context.getFilesDir(), "languages/" + langName);
							boolean suggestionInstalled = langDir.exists() && langDir.list() != null && langDir.list().length > 0;

							LanguagePack suggestionPack = new LanguagePack(
									langKey + "_suggestions",
									name + " Code Completion",
									"download:" + suggestionUrl,
									"uninstall_suggestion:" + langKey,
									"check_suggestion:" + langKey,
									suggestionInstalled ? LanguagePack.STATUS_INSTALLED : LanguagePack.STATUS_AVAILABLE,
									LanguagePack.TYPE_SUGGESTION
							);
							if (runtimePack != null) {
								runtimePack.companionKey = suggestionPack.key;
								suggestionPack.companionKey = runtimePack.key;
							}
							packs.add(suggestionPack);
						}
					}
				}
			} catch (JSONException e) {
				Log.e(TAG, "Error parsing configuration JSON for language list.", e);
			}
			return packs;
		});
	}

	private java.util.Set<String> getInstalledPackagesList() {
		java.util.Set<String> installed = new java.util.HashSet<>();
		try {
			String prefix = context.getFilesDir().getPath() + "/usr";
			ProcessBuilder pb = new ProcessBuilder(prefix + "/bin/sh", "-c", "dpkg-query -W -f='${Package} ${Status}\\n' | grep 'ok installed' | cut -d' ' -f1");
			pb.environment().put("PREFIX", prefix);
			pb.environment().put("LD_LIBRARY_PATH", prefix + "/lib");
			pb.environment().put("PATH", prefix + "/bin:" + System.getenv("PATH"));

			Process process = pb.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					installed.add(line.trim());
				}
			}
			process.waitFor();
		} catch (Exception e) {
			Log.e(TAG, "Failed to fetch installed packages list", e);
		}
		return installed;
	}

	/**
	 * Resolves the execution command for a given file.
	 *
	 * @param absoluteFilePath The absolute path of the file.
	 * @return The resolved command string, or null if not found or error.
	 */
	public String resolveCommandForFile(String absoluteFilePath, String internalOutputPath) {
		if (absoluteFilePath == null) return null;
		File file = new File(absoluteFilePath);
		String fileName = file.getName();
		String extension = "";
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
			extension = fileName.substring(i);
		} else {
			// For files like Makefile
			extension = fileName;
		}

		String configJson = loadConfigurationJson();
		if (configJson == null) return null;

		try {
			JSONObject fullConfig = new JSONObject(configJson);
			JSONObject env = fullConfig.optJSONObject("termux_programming_environment");
			if (env == null) return null;
			JSONObject languages = env.optJSONObject("languages");
			if (languages == null) return null;

			String cleanExt = extension.startsWith(".") || extension.equals("Makefile") ? extension : "." + extension;

			String[] categories = {"interpreted", "compiled", "shell_scripting", "web", "tools"};
			for (String category : categories) {
				JSONObject catObj = languages.optJSONObject(category);
				if (catObj == null) continue;
				Iterator<String> langKeys = catObj.keys();
				while (langKeys.hasNext()) {
					String langKey = langKeys.next();
					JSONObject lang = catObj.getJSONObject(langKey);
					String supportedExt = lang.optString("extension", "");
					if (!supportedExt.isEmpty() && cleanExt.equalsIgnoreCase(supportedExt)) {
						// Only use internal output path for compiled languages that aren't JVM-based (Java/Kotlin use their own ways)
						boolean useInternal = category.equals("compiled") && !langKey.equals("java") && !langKey.equals("kotlin");
						return buildCommand(lang, absoluteFilePath, useInternal ? internalOutputPath : null);
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error resolving command for file: " + absoluteFilePath, e);
		}
		return null;
	}

	private String buildCommand(JSONObject lang, String absoluteFilePath, String internalOutputPath) {
		try {
			String run = lang.optString("run", "");
			String compile = lang.optString("compile", "");

			if (run.isEmpty() && compile.isEmpty()) return null;

			File file = new File(absoluteFilePath);
			String fileName = file.getName();
			String fileNameWithoutExt = fileName;
			int dotIndex = fileName.lastIndexOf('.');
			if (dotIndex > 0) {
				fileNameWithoutExt = fileName.substring(0, dotIndex);
			}

			// Use relative paths (filename only) with single quotes for maximum shell safety.
			// We execute in the file's parent directory as cwd (handled by ExecutionManager).
			String qFile = "'" + fileName.replace("'", "'\\''") + "'";

			// Use absolute path for output if provided (to avoid noexec issues), otherwise relative.
			String qOutput;
			if (internalOutputPath != null && !internalOutputPath.isEmpty()) {
				qOutput = "'" + internalOutputPath.replace("'", "'\\''") + "'";
			} else {
				qOutput = "'" + fileNameWithoutExt.replace("'", "'\\''") + "'";
			}

			String qClassName = "'" + fileNameWithoutExt.replace("'", "'\\''") + "'";

			String command = "";
			if (!compile.isEmpty()) {
				command = compile.replace("{{file}}", qFile)
						.replace("{{output}}", qOutput);
				if (!run.isEmpty()) {
					String runTemplate = run;
					// If using absolute output path, remove ./ prefix from run template to avoid .//path error
					if (internalOutputPath != null && !internalOutputPath.isEmpty() && runTemplate.contains("./{{output}}")) {
						runTemplate = runTemplate.replace("./{{output}}", "{{output}}");
					}

					String runCmd = runTemplate.replace("{{file}}", qFile)
							.replace("{{output}}", qOutput)
							.replace("{{class_name}}", qClassName);

					// Prepend ./ for compiled executables if it's a relative path and not already there
					if (run.startsWith("./") && !runCmd.startsWith("./") && (internalOutputPath == null || internalOutputPath.isEmpty())) {
						runCmd = "./" + runCmd;
					}
					command += " && " + runCmd;
				}
			} else {
				command = run.replace("{{file}}", qFile)
						.replace("{{class_name}}", qClassName);
			}

			return command;
		} catch (Exception e) {
			Log.e(TAG, "Error building command", e);
			return null;
		}
	}

	/**
	 * Checks if a given extension is supported for execution.
	 *
	 * @param extension The file extension (including the dot).
	 * @return True if supported, false otherwise.
	 */
	public boolean isExtensionSupported(String extension) {
		if (extension == null || extension.isEmpty()) return false;
		String configJson = loadConfigurationJson();
		if (configJson == null) return false;
		try {
			JSONObject fullConfig = new JSONObject(configJson);
			JSONObject env = fullConfig.optJSONObject("termux_programming_environment");
			if (env == null) return false;
			JSONObject languages = env.optJSONObject("languages");
			if (languages == null) return false;

			String cleanExt = (extension.startsWith(".") || extension.equals("Makefile")) ? extension : "." + extension;

			String[] categories = {"interpreted", "compiled", "shell_scripting", "web", "tools"};
			for (String category : categories) {
				JSONObject catObj = languages.optJSONObject(category);
				if (catObj == null) continue;
				Iterator<String> langKeys = catObj.keys();
				while (langKeys.hasNext()) {
					JSONObject lang = catObj.getJSONObject(langKeys.next());
					String supportedExt = lang.optString("extension", "");
					if (!supportedExt.isEmpty() && cleanExt.equalsIgnoreCase(supportedExt)) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error checking extension support for " + extension, e);
		}
		return false;
	}

	/**
	 * Shuts down the executor service.
	 */
	public void shutdown() {
		executorService.shutdown();
	}

	// --- Private Helper Methods ---

	/**
	 * Loads the configuration JSON from shared preferences or assets.
	 *
	 * @return The JSON configuration string, or null if it cannot be loaded.
	 */
	public String loadConfigurationJson() {
		try {
			android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
			String updatedConfig = prefs.getString(PREF_KEY_UPDATED_CONFIG, null);
			if (updatedConfig != null) {
				return updatedConfig;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error reading from SharedPreferences", e);
		}

		try {
			InputStream is = context.getAssets().open(CONFIG_FILE_NAME);
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			reader.close();
			return sb.toString();
		} catch (IOException e) {
			Log.e(TAG, "Could not load " + CONFIG_FILE_NAME + " from assets.", e);
		}
		return null;
	}

	/**
	 * Capitalizes the first letter of a string.
	 *
	 * @param s The string to capitalize.
	 * @return The capitalized string.
	 */
	private String capitalize(String s) {
		if (s == null || s.length() == 0) {
			return "";
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}
}
