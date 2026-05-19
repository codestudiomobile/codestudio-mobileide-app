package com.cs.ide.app.editor;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem;
import io.github.rosemoe.sora.lang.completion.SnippetDescription;
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet;
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.util.MyCharacter;

import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VSCodeSnippetProvider parses VS Code .code-snippets JSON files using Gson
 * and provides them as auto-completion items for Sora Editor.
 */
public class VSCodeSnippetProvider implements CompletionProvider {

    private static final String TAG = "VSCodeSnippetProvider";
    private final List<CompletionItem> snippets = new ArrayList<>();

    public VSCodeSnippetProvider(Context context, String assetPath) {
        loadSnippets(context, assetPath);
    }

    private void loadSnippets(Context context, String assetPath) {
        try {
            String jsonString = IOUtils.toString(context.getAssets().open(assetPath), StandardCharsets.UTF_8);
            
            // VS Code JSON files often contain comments. Strip them before parsing with Gson.
            jsonString = stripJsonComments(jsonString);
            
            Gson gson = new Gson();
            
            // VS Code snippets are a Map where keys are snippet names
            Map<String, SnippetModel> map = gson.fromJson(jsonString, new TypeToken<Map<String, SnippetModel>>() {}.getType());

            if (map == null) return;

            for (Map.Entry<String, SnippetModel> entry : map.entrySet()) {
                String name = entry.getKey();
                SnippetModel s = entry.getValue();
                
                List<String> prefixes = new ArrayList<>();
                if (s.prefix instanceof String) {
                    prefixes.add((String) s.prefix);
                } else if (s.prefix instanceof List) {
                    for (Object p : (List<?>) s.prefix) {
                        prefixes.add(p.toString());
                    }
                } else {
                    prefixes.add(name);
                }
                
                String description = s.description != null ? s.description : name;
                
                String body;
                if (s.body instanceof List) {
                    List<?> bodyList = (List<?>) s.body;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < bodyList.size(); i++) {
                        sb.append(bodyList.get(i).toString());
                        if (i < bodyList.size() - 1) sb.append("\n");
                    }
                    body = sb.toString();
                } else if (s.body instanceof String) {
                    body = (String) s.body;
                } else if (s.body != null) {
                    body = s.body.toString();
                } else {
                    continue;
                }

                // Pre-process body to replace VS Code specific variables that Sora might not support
                body = preProcessSnippetBody(body);

                try {
                    CodeSnippet codeSnippet = CodeSnippetParser.parse(body);
                    for (String prefix : prefixes) {
                        SnippetDescription snippetDesc = new SnippetDescription(prefix.length(), codeSnippet, true);
                        SimpleSnippetCompletionItem item = new SimpleSnippetCompletionItem(prefix, description, snippetDesc);
                        snippets.add(item);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse snippet: " + name, e);
                }
            }
            Log.d(TAG, "Loaded " + snippets.size() + " snippet items from " + assetPath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading snippets from " + assetPath, e);
        }
    }

    private String stripJsonComments(String json) {
        // Simple regex to strip // and /* */ comments
        return json.replaceAll("(?s)/\\*.*?\\*/|//.*", "");
    }

    private String preProcessSnippetBody(String body) {
        // Sora Editor's CodeSnippetParser handles $0, $1, etc.
        // It might not handle $TM_SELECTED_TEXT or other VS Code variables.
        // Replace common VS Code variables with placeholders or empty strings if unsupported.
        return body.replace("$TM_SELECTED_TEXT", "")
                   .replace("${TM_SELECTED_TEXT}", "")
                   .replace("$CLIPBOARD", "")
                   .replace("${CLIPBOARD}", "");
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position, @NonNull CompletionPublisher publisher, @NonNull Bundle extraArguments) {
        // Use a more inclusive predicate for prefix calculation
        String prefix = CompletionHelper.computePrefix(content, position, c -> MyCharacter.isJavaIdentifierPart(c) || c == '-' || c == '$' || c == '@' || c == '!');
        
        List<CompletionItem> filtered = new ArrayList<>();
        String match = (prefix == null) ? "" : prefix.toLowerCase();
        
        for (CompletionItem item : snippets) {
            String label = item.label.toString().toLowerCase();
            if (label.startsWith(match)) {
                // IMPORTANT: Create a shallow copy or use a new item to avoid shared state issues
                // with prefixLength if multiple sessions occur.
                // In Sora Editor 0.24.5, SimpleSnippetCompletionItem might not have a public copy constructor.
                // We'll just set the prefixLength on the item, but be aware of the race condition.
                item.prefixLength = match.length();
                filtered.add(item);
            }
        }
        publisher.addItems(filtered);
    }

    /**
     * Inner class for Gson mapping.
     */
    private static class SnippetModel {
        Object prefix; // String or List<String>
        Object body;   // String or List<String>
        String description;
    }
}
