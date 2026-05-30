package com.cs.ide.app.editor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.cs.ide.app.execution.CommandFetcher;
import com.cs.ide.app.services.LanguageManagerService;
import com.itsaky.androidide.treesitter.TSLanguage;

import org.apache.commons.io.IOUtils;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.rosemoe.sora.editor.ts.LocalsCaptureSpec;
import io.github.rosemoe.sora.editor.ts.TsLanguage;
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec;
import io.github.rosemoe.sora.editor.ts.predicate.builtin.MatchPredicate;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import kotlin.Unit;

/**
 * Manages languages for Sora Editor.
 * Handles detection, dynamic fetching, and loading of language packs (Tree-sitter and TextMate).
 * Integrated with VS Code extensions support for syntax highlighting and snippets.
 */

/**
 * Manages language support for the Sora Editor, including syntax highlighting
 * via Tree-sitter and TextMate, as well as snippet provision from VS Code extensions.
 */
public class SoraLanguageManager {
	private static final String TAG = "SoraLanguageManager";
	private static final String PREFS_NAME = "LanguagePackPrefs";
	private static boolean textMateInitialized = false;
	private final Context context;
	private final CommandFetcher commandFetcher;

	/**
	 * Cache of loaded language engines.
	 */
	private final Map<String, Language> loadedLanguages = new HashMap<>();

	/**
	 * Maps language IDs to their respective snippet providers.
	 */
	private final Map<String, VSCodeSnippetProvider> languageSnippetProviders = new HashMap<>();

	/**
	 * Maps file extensions to TextMate scope names.
	 */
	private final Map<String, String> extensionToScopeMap = new HashMap<>();

	/**
	 * Maps file extensions to internal language identifiers.
	 */
	private final Map<String, String> extensionToLangIdMap = new HashMap<>();

	/**
	 * Maps language IDs to their configuration JSON file paths.
	 */
	private final Map<String, String> langIdToConfigPathMap = new HashMap<>();

	private volatile boolean isInitialized = false;

	public SoraLanguageManager(Context context) {
		this.context = context;
		this.commandFetcher = new CommandFetcher(context);
		new Thread(() -> {
			initTextMateIfNeeded();
			loadVSCodeExtensions();
			isInitialized = true;
		}).start();
	}

	private void initTextMateIfNeeded() {
		if (!textMateInitialized) {
			try {
				FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(context.getAssets()));

				// Load the default theme for TextMate
				ThemeRegistry.getInstance().loadTheme(
						IThemeSource.fromInputStream(
								context.getAssets().open("vscode_extensions/darkness.json"),
								"darkness.json",
								StandardCharsets.UTF_8
						)
				);

				textMateInitialized = true;
			} catch (Exception e) {
				Log.e(TAG, "Failed to initialize TextMate", e);
			}
		}
	}

	private void loadVSCodeExtensions() {
		try {
			String[] extensions = context.getAssets().list("vscode_extensions");
			if (extensions == null) return;

			for (String extDir : extensions) {
				String packageJsonPath = "vscode_extensions/" + extDir + "/package.json";
				try {
					String json = IOUtils.toString(context.getAssets().open(packageJsonPath), StandardCharsets.UTF_8);
					JSONObject root = new JSONObject(json);
					JSONObject contributes = root.optJSONObject("contributes");
					if (contributes == null) continue;

					// Register Grammars
					JSONArray grammars = contributes.optJSONArray("grammars");
					if (grammars != null) {
						for (int i = 0; i < grammars.length(); i++) {
							JSONObject g = grammars.getJSONObject(i);
							String scopeName = g.getString("scopeName");
							String path = g.getString("path");
							if (path.startsWith("./")) path = path.substring(2);
							String fullPath = "vscode_extensions/" + extDir + "/" + path;

							try {
								GrammarRegistry.getInstance().loadGrammar(
										DefaultGrammarDefinition.withGrammarSource(
												IGrammarSource.fromInputStream(context.getAssets().open(fullPath), scopeName + ".json", StandardCharsets.UTF_8),
												extDir,
												scopeName
										)
								);
								Log.d(TAG, "Loaded VSCode Grammar: " + scopeName);
							} catch (Exception e) {
								Log.e(TAG, "Error loading grammar: " + fullPath, e);
							}
						}
					}

					// Map Extensions to Scopes and Lang IDs
					JSONArray languages = contributes.optJSONArray("languages");
					if (languages != null) {
						for (int i = 0; i < languages.length(); i++) {
							JSONObject l = languages.getJSONObject(i);
							String id = l.getString("id");

							// Capture configuration path
							String config = l.optString("configuration");
							if (config != null) {
								if (config.startsWith("./")) config = config.substring(2);
								langIdToConfigPathMap.put(id, "vscode_extensions/" + extDir + "/" + config);
							}

							JSONArray exts = l.optJSONArray("extensions");
							if (exts != null) {
								String mainScope = findScopeForLanguageId(grammars, id);
								for (int j = 0; j < exts.length(); j++) {
									String ext = exts.getString(j).replace(".", "");
									extensionToLangIdMap.put(ext, id);
									if (mainScope != null) {
										extensionToScopeMap.put(ext, mainScope);
									}
								}
							}
						}
					}

					// Register Snippets
					JSONArray snippets = contributes.optJSONArray("snippets");
					if (snippets != null) {
						for (int i = 0; i < snippets.length(); i++) {
							JSONObject s = snippets.getJSONObject(i);
							String langId = s.getString("language");
							String path = s.getString("path");
							if (path.startsWith("./")) path = path.substring(2);
							String assetPath = "vscode_extensions/" + extDir + "/" + path;
							languageSnippetProviders.put(langId, new VSCodeSnippetProvider(context, assetPath));
						}
					}

				} catch (Exception e) {
					Log.e(TAG, "Error processing VSCode extension: " + extDir, e);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Error listing VSCode extensions", e);
		}
	}

	private String findScopeForLanguageId(JSONArray grammars, String langId) {
		if (grammars == null) return null;
		for (int i = 0; i < grammars.length(); i++) {
			try {
				JSONObject g = grammars.getJSONObject(i);
				if (g.has("language") && g.getString("language").equals(langId)) {
					return g.getString("scopeName");
				}
			} catch (Exception ignored) {
			}
		}
		// Fallback to searching by langId if scope name contains it
		for (int i = 0; i < grammars.length(); i++) {
			try {
				JSONObject g = grammars.getJSONObject(i);
				String scope = g.getString("scopeName");
				if (scope.endsWith("." + langId)) return scope;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public void applyLanguage(CodeEditor editor, String extension) {
		if (!isInitialized) {
			// If not initialized, try again in 500ms
			editor.postDelayed(() -> applyLanguage(editor, extension), 500);
			return;
		}
		try {
			ensureThemeApplied(editor);
		} catch (Exception e) {
			Log.e(TAG, "Failed to apply theme during language switch", e);
		}

		if (extension == null || extension.isEmpty()) {
			editor.setEditorLanguage(new EmptyLanguage());
			enableCompletion(editor);
			return;
		}

		String cleanExt = extension.replace(".", "");

		if (loadedLanguages.containsKey(cleanExt)) {
			Language lang = loadedLanguages.get(cleanExt);
			editor.setEditorLanguage(lang);
			attachSnippets(editor, cleanExt);
			enableCompletion(editor);
			return;
		}

		String scopeName = extensionToScopeMap.get(cleanExt);

		if (scopeName != null) {
			try {
				TextMateLanguage lang = TextMateLanguage.create(scopeName, true);
				Language wrapped = wrapAndConfigureLanguage(lang, cleanExt);
				loadedLanguages.put(cleanExt, wrapped);
				editor.setEditorLanguage(wrapped);
				attachSnippets(editor, cleanExt);
				enableCompletion(editor);
				return;
			} catch (Exception e) {
				Log.e(TAG, "Error applying TextMate language for " + extension, e);
			}
		}

		String langName = getLanguageNameFromExtension(cleanExt);
		if (langName == null) {
			editor.setEditorLanguage(new EmptyLanguage());
			enableCompletion(editor);
			return;
		}

		File langDir = new File(context.getFilesDir(), "languages/" + langName);
		if (langDir.exists()) {
			File libFile = new File(langDir, "libtree-sitter-" + langName + ".so");
			if (libFile.exists()) {
				loadAndApplyTreeSitter(editor, langName, cleanExt);
				enableCompletion(editor);
				return;
			}

			File[] files = langDir.listFiles();
			if (files != null) {
				for (File file : files) {
					String name = file.getName();
					if (name.endsWith(".tmLanguage") || name.endsWith(".tmLanguage.json") || name.endsWith(".plist")) {
						loadAndApplyTextMate(editor, langName, cleanExt, file);
						enableCompletion(editor);
						return;
					}
				}
			}
		}

		enableCompletion(editor);
		promptInstallLanguagePack(editor, langName, cleanExt);
	}

	private void enableCompletion(CodeEditor editor) {
		try {
			editor.getComponent(EditorAutoCompletion.class).setEnabled(true);
		} catch (Exception e) {
			Log.e(TAG, "Failed to enable completion", e);
		}
	}

	private void ensureThemeApplied(CodeEditor editor) {
		try {
			if (!(editor.getColorScheme() instanceof TextMateColorScheme)) {
				editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance()));
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to apply TextMate theme", e);
		}
	}

	private void loadAndApplyTreeSitter(CodeEditor editor, String langName, String extension) {
		try {
			File langDir = new File(context.getFilesDir(), "languages/" + langName);
			File libFile = new File(langDir, "libtree-sitter-" + langName + ".so");
			File highlightScm = new File(langDir, "highlights.scm");

			if (!libFile.exists() || !highlightScm.exists()) {
				editor.setEditorLanguage(new EmptyLanguage());
				return;
			}

			TSLanguage tsNativeLang = TSLanguage.loadLanguage(libFile.getAbsolutePath(), langName);
			if (tsNativeLang == null) {
				Log.e(TAG, "Failed to load native language: " + langName);
				editor.setEditorLanguage(new EmptyLanguage());
				return;
			}

			String highlightQuery = IOUtils.toString(new FileInputStream(highlightScm), StandardCharsets.UTF_8);

			TsLanguageSpec spec = new TsLanguageSpec(
					tsNativeLang,
					highlightQuery,
					"", "", "",
					new LocalsCaptureSpec(),
					Collections.singletonList(MatchPredicate.INSTANCE)
			);

			Language lang = new TsLanguage(spec, false, builder -> {
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.KEYWORD), "keyword");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.KEYWORD), "storage.type");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.KEYWORD), "storage.modifier");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.LITERAL), "string");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.LITERAL), "number");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.LITERAL), "constant");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.COMMENT), "comment");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME), "function");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME), "method");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.ANNOTATION), "type");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.ANNOTATION), "tag");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.OPERATOR), "operator");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.OPERATOR), "punctuation");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME), "variable");
				builder.applyTo(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME), "property");
				return Unit.INSTANCE;
			});

			loadedLanguages.put(extension, wrapAndConfigureLanguage(lang, extension));
			editor.setEditorLanguage(loadedLanguages.get(extension));
			attachSnippets(editor, extension);

		} catch (Exception e) {
			Log.e(TAG, "Error loading Tree-sitter language: " + langName, e);
			editor.setEditorLanguage(new EmptyLanguage());
		}
	}

	private void loadAndApplyTextMate(CodeEditor editor, String langName, String extension, File grammarFile) {
		try {
			String scope = getScopeNameForLanguage(langName);
			if (scope == null) scope = "source." + langName;

			GrammarRegistry.getInstance().loadGrammar(
					DefaultGrammarDefinition.withGrammarSource(IGrammarSource.fromFile(grammarFile), langName, scope)
			);

			ensureThemeApplied(editor);

			TextMateLanguage lang = TextMateLanguage.create(scope, true);
			loadedLanguages.put(extension, wrapAndConfigureLanguage(lang, extension));
			editor.setEditorLanguage(loadedLanguages.get(extension));
			attachSnippets(editor, extension);

			Log.d(TAG, "Loaded TextMate language for " + langName + " with scope " + scope);
		} catch (Exception e) {
			Log.e(TAG, "Error loading TextMate language: " + langName, e);
			editor.setEditorLanguage(new EmptyLanguage());
		}
	}

	private Language wrapAndConfigureLanguage(Language lang, String extension) {
		SoraLanguageWrapper wrapped = (lang instanceof SoraLanguageWrapper) ? (SoraLanguageWrapper) lang : new SoraLanguageWrapper(lang);

		String cleanExt = extension.replace(".", "");
		String langId = extensionToLangIdMap.get(cleanExt);

		if (langId != null) {
			// Apply configuration (symbol pairs)
			applyLanguageConfiguration(wrapped, langId);
		}

		return wrapped;
	}

	private void applyLanguageConfiguration(SoraLanguageWrapper wrapped, String langId) {
		String configPath = langIdToConfigPathMap.get(langId);
		if (configPath == null) return;

		try {
			String json = IOUtils.toString(context.getAssets().open(configPath), StandardCharsets.UTF_8);
			JSONObject root = new JSONObject(json);
			SymbolPairMatch pairs = new SymbolPairMatch.DefaultSymbolPairs();

			JSONArray autoClosing = root.optJSONArray("autoClosingPairs");
			if (autoClosing != null) {
				for (int i = 0; i < autoClosing.length(); i++) {
					Object item = autoClosing.get(i);
					if (item instanceof JSONObject pair) {
						String open = pair.optString("open");
						String close = pair.optString("close");
						if (open != null && close != null && !open.isEmpty() && !close.isEmpty()) {
							pairs.putPair(open, new SymbolPairMatch.SymbolPair(open, close));
						}
					}
				}
			}
			wrapped.setSymbolPairs(pairs);
		} catch (Exception e) {
			Log.e(TAG, "Error loading language configuration: " + configPath, e);
		}
	}

	private void attachSnippets(CodeEditor editor, String extension) {
		String cleanExt = extension.replace(".", "");
		String langId = extensionToLangIdMap.get(cleanExt);

		if (langId != null) {
			Language currentLang = editor.getEditorLanguage();
			if (currentLang instanceof SoraLanguageWrapper) {
				VSCodeSnippetProvider provider = languageSnippetProviders.get(langId);
				if (provider != null) {
					((SoraLanguageWrapper) currentLang).setAutoCompleteProvider(provider);
					Log.d(TAG, "Attached VSCodeSnippetProvider for " + langId);
				}
			}
		}
	}

	private void promptInstallLanguagePack(CodeEditor editor, String langName, String extension) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String prefKey = "asked_" + langName;
		if (!prefs.getBoolean(prefKey, false)) {
			prefs.edit().putBoolean(prefKey, true).apply();
			List<String> relatedLangs = getRelatedLanguages(langName);
			StringBuilder message = new StringBuilder("Do you want to install the language pack for " + langName + " to get code completion and highlighting?");
			if (!relatedLangs.isEmpty()) {
				message.append("\n\nAlso recommended: ");
				for (int i = 0; i < relatedLangs.size(); i++) {
					message.append(relatedLangs.get(i));
					if (i < relatedLangs.size() - 1) message.append(", ");
				}
			}
			new AlertDialog.Builder(context)
					.setTitle("Install Language Pack?")
					.setMessage(message.toString())
					.setPositiveButton("Install All", (dialog, which) -> {
						installLanguagePack(langName);
						for (String related : relatedLangs) installLanguagePack(related);
					})
					.setNeutralButton("Only " + langName, (dialog, which) -> installLanguagePack(langName))
					.setNegativeButton("Later", (dialog, which) -> editor.setEditorLanguage(new EmptyLanguage()))
					.show();
		} else {
			editor.setEditorLanguage(new EmptyLanguage());
		}
	}

	private List<String> getRelatedLanguages(String langName) {
		List<String> related = new ArrayList<>();
		if ("html".equals(langName)) {
			related.add("css");
			related.add("nodejs");
		} else if ("css".equals(langName) || "nodejs".equals(langName)) {
			related.add("html");
		}
		return related;
	}

	private void installLanguagePack(String langName) {
		String configJson = commandFetcher.loadConfigurationJson();
		if (configJson == null) return;
		try {
			JSONObject fullConfig = new JSONObject(configJson);
			JSONObject languages = fullConfig.optJSONObject("termux_programming_environment").optJSONObject("languages");
			if (languages == null) return;

			String url = null;
			for (String category : new String[]{"interpreted", "compiled", "shell_scripting", "web"}) {
				JSONObject catObj = languages.optJSONObject(category);
				if (catObj != null && catObj.has(langName)) {
					url = catObj.getJSONObject(langName).optString("suggestion_pack", "");
					if (!url.isEmpty()) break;
				}
			}

			if (url != null && !url.isEmpty()) {
				File langDir = new File(context.getFilesDir(), "languages/" + langName);
				langDir.mkdirs();
				String command = url.endsWith(".zip") ?
						"curl -L " + url + " -o " + langDir.getAbsolutePath() + "/pack.zip && unzip -o " + langDir.getAbsolutePath() + "/pack.zip -d " + langDir.getAbsolutePath() :
						"curl -L \"" + url + "\" -o " + langDir.getAbsolutePath() + "/" + url.substring(url.lastIndexOf("/") + 1);

				Intent intent = new Intent(context, LanguageManagerService.class);
				intent.setAction(LanguageManagerService.ACTION_INSTALL_PACKAGE);
				intent.putExtra(LanguageManagerService.EXTRA_PACKAGE_KEY, "lang_" + langName);
				intent.putExtra(LanguageManagerService.EXTRA_PACKAGE_NAME, langName);
				intent.putExtra(LanguageManagerService.EXTRA_COMMAND, command);
				context.startService(intent);
				Toast.makeText(context, "Installing " + langName + "…", Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			Log.e(TAG, "Error starting installation", e);
		}
	}

	private String getLanguageNameFromExtension(String extension) {
		String configJson = commandFetcher.loadConfigurationJson();
		if (configJson == null) return null;
		try {
			JSONObject languages = new JSONObject(configJson).optJSONObject("termux_programming_environment").optJSONObject("languages");
			for (String category : new String[]{"interpreted", "compiled", "shell_scripting", "web"}) {
				JSONObject catObj = languages.optJSONObject(category);
				if (catObj == null) continue;
				for (java.util.Iterator<String> it = catObj.keys(); it.hasNext(); ) {
					String key = it.next();
					if (extension.equalsIgnoreCase(catObj.getJSONObject(key).optString("extension")))
						return key;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private String getScopeNameForLanguage(String langName) {
		String configJson = commandFetcher.loadConfigurationJson();
		if (configJson == null) return null;
		try {
			JSONObject languages = new JSONObject(configJson).optJSONObject("termux_programming_environment").optJSONObject("languages");
			for (String category : new String[]{"interpreted", "compiled", "shell_scripting", "web"}) {
				JSONObject catObj = languages.optJSONObject(category);
				if (catObj != null && catObj.has(langName))
					return catObj.getJSONObject(langName).optString("scope_name", null);
			}
		} catch (Exception ignored) {
		}
		return null;
	}
}
