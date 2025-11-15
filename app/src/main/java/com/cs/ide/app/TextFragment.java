package com.cs.ide.app;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.cs.ide.R;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public class TextFragment extends Fragment implements TextWatcher {
    private static final String ARG_URI = "file_uri";
    private CodeView fileContent;
    private boolean isSaved = false;
    private Uri fileUri;
    @NonNull
    public static TextFragment newInstance(Uri uri) {
        TextFragment fragment = new TextFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileUri = getArguments().getParcelable(ARG_URI);
        }
    }
    public boolean isSaved() {
        return isSaved;
    }
    public void setSaved(boolean saved) {
        isSaved = saved;
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_text_code_studio, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fileContent = view.findViewById(R.id.fileContent);
        fileContent.addTextChangedListener(this);
        loadFileContent();
    }
    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }
    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }
    @Override
    public void afterTextChanged(Editable editable) {
        isSaved = false;
    }
    public byte[] getContents() {
        if (fileContent != null)
            return fileContent.getText().toString().getBytes(StandardCharsets.UTF_8);
        return new byte[0];
    }
    private boolean isTextFile(Uri uri) {
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null)
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        }
        if (mimeType == null) return false;
        return mimeType.startsWith("text/") || mimeType.equals("application/json") || mimeType.equals("application/xml");
    }
    private boolean isProbablyText(Uri uri) {
        final int SAMPLE = 1024;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            byte[] buf = new byte[SAMPLE];
            int read = is.read(buf);
            if (read <= 0) return false;
            int nonPrintable = 0;
            for (int i = 0; i < read; i++) {
                byte b = buf[i];
                if (b == 9 || b == 10 || b == 13) continue;
                if (b < 0x20 || b > 0x7E) nonPrintable++;
            }
            return ((double) nonPrintable / read) < 0.3;
        } catch (Exception e) {
            return false;
        }
    }
    private void loadFileContent() {
        if (fileUri == null || fileUri.equals(ViewPagerAdapter.UNTITLED_FILE_URI)) return;
        final int MAX_LINES_TO_READ_FOR_SAFETY = 50000;
        new Thread(() -> {
            boolean readable = isTextFile(fileUri) && isProbablyText(fileUri);
            requireActivity().runOnUiThread(() -> {
                if (!readable) {
                    Toast.makeText(getContext(), "Unsupported or non-text file", Toast.LENGTH_SHORT).show();
                    fileContent.setText("");
                    return;
                }
                fileContent.setText("");
            });
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192);
                    StringBuilder contentBuilder = new StringBuilder();
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        if (lineCount > 0) {
                            contentBuilder.append('\n');
                        }
                        contentBuilder.append(line);
                        lineCount++;
                        if (lineCount >= MAX_LINES_TO_READ_FOR_SAFETY) {
                            contentBuilder.append("\n... FILE TRUNCATED (MAX LINES REACHED) ...");
                            break;
                        }
                        if (lineCount % 1000 == 0) {
                            final String partialContent = contentBuilder.toString();
                            requireActivity().runOnUiThread(() -> {
                                fileContent.setText(partialContent);
                            });
                        }
                    }
                    requireActivity().runOnUiThread(() -> {
                        fileContent.setText(contentBuilder.toString());
                        isSaved = true;
                        fileContent.setSelection(0);
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    public void refreshContent() {
        loadFileContent();
    }
}
