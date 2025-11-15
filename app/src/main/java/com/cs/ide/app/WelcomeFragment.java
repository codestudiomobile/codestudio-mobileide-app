package com.cs.ide.app;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.cs.ide.R;
public class WelcomeFragment extends Fragment {
    public static WelcomeFragment newInstance() {
        return new WelcomeFragment();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_welcome_code_studio, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView openFolder = view.findViewById(R.id.openFolderText);
        TextView openFiles = view.findViewById(R.id.openFilesText);
        TextView manageLanguages = view.findViewById(R.id.manageLanguagesSettings);
        TextView openEditor = view.findViewById(R.id.openEditorSettings);
        TextView openSettings = view.findViewById(R.id.openSettings);
        TextView openFileFromInternalStorage = view.findViewById(R.id.openFileFromInternalStorageText);
        TextView openNewTerminal = view.findViewById(R.id.openNewTerminal);
        openFolder.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDirectory();
            }
        });
        openFiles.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openLeftNavigation();
            }
        });
        manageLanguages.setOnClickListener(v -> {
            Intent intent = new Intent(view.getContext(), ManageLanguagesActivity.class);
            startActivity(intent);
        });
        openEditor.setOnClickListener(v -> {
            Intent intent = new Intent(view.getContext(), EditorActivity.class);
            startActivity(intent);
        });
        openSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSettings();
            }
        });
        openFileFromInternalStorage.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openFilePicker();
            }
        });
        openNewTerminal.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openNewTerminal();
            }
        });
    }
}
