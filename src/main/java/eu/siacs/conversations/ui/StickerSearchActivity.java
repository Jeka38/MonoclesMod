package eu.siacs.conversations.ui;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.monocles.mod.TlgrmStickerSearch;
import de.monocles.mod.TlgrmStickersAdapter;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityStickerSearchBinding;
import eu.siacs.conversations.utils.Compatibility;

public class StickerSearchActivity extends AppCompatActivity {

    private static final int REQUEST_STICKER_PERMISSION = 84;
    private ActivityStickerSearchBinding binding;
    private TlgrmStickerSearch search;
    private TlgrmStickersAdapter adapter;
    private File stickerDir;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStickerSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        search = new TlgrmStickerSearch();
        adapter = new TlgrmStickersAdapter(this);
        binding.results.setAdapter(adapter);
        stickerDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                + File.separator + APP_DIRECTORY + File.separator + "Stickers");
        if (!stickerDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            stickerDir.mkdirs();
        }

        binding.searchButton.setOnClickListener(v -> performSearch());
        binding.query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch();
                return true;
            }
            return false;
        });

        binding.results.setOnItemClickListener((parent, view, position, id) -> {
            if (!hasStoragePermission()) return;
            final TlgrmStickerSearch.StickerItem item = adapter.getItem(position);
            new Thread(() -> {
                try {
                    search.downloadDirectSticker(item.imageUrl, stickerDir);
                    runOnUiThread(() -> Toast.makeText(this, R.string.sticker_imported, Toast.LENGTH_SHORT).show());
                } catch (final IOException e) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.import_sticker_failed, Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
    }

    private void performSearch() {
        final String query = binding.query.getText() == null ? "" : binding.query.getText().toString().trim();
        if (query.isEmpty()) return;
        new Thread(() -> {
            try {
                final List<TlgrmStickerSearch.StickerItem> results = search.search(query);
                runOnUiThread(() -> adapter.setItems(results));
            } catch (final IOException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.import_sticker_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private boolean hasStoragePermission() {
        if (!Compatibility.runsThirtyThree()
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STICKER_PERMISSION);
            return false;
        } else if (Compatibility.runsThirtyThree()
                && checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_STICKER_PERMISSION);
            return false;
        }
        return true;
    }
}
