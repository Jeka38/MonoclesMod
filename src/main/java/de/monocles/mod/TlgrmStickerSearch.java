package de.monocles.mod;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.utils.MimeUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TlgrmStickerSearch {

    private static final String BASE_URL = "https://tlgrm.ru";
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("(https?:\\\\?/\\\\?/[^\"'\\\\s>]+\\.(?:webp|png|jpg|jpeg))", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_IMAGE_URL_PATTERN = Pattern.compile("(?:src|data-src)=\"(/[^\"\\s>]+\\.(?:webp|png|jpg|jpeg))\"", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RESULTS = 80;

    private final OkHttpClient httpClient = new OkHttpClient();

    public static class StickerItem {
        public final String imageUrl;

        public StickerItem(@NonNull final String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    public List<StickerItem> search(final String query) throws IOException {
        final String encoded = Uri.encode(query == null ? "" : query.trim());
        final String[] urls = new String[] {
                BASE_URL + "/stickers?query=" + encoded,
                BASE_URL + "/stickers?search=" + encoded,
                BASE_URL + "/stickers/" + encoded,
                BASE_URL + "/stickers"
        };

        IOException firstError = null;
        for (final String url : urls) {
            try {
                final String html = fetch(url);
                final List<StickerItem> parsed = parse(html);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            } catch (final IOException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        if (firstError != null) {
            throw firstError;
        }
        return new ArrayList<>();
    }

    private String fetch(final String url) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Request failed with code " + response.code());
            }
            return response.body().string();
        }
    }

    private List<StickerItem> parse(final String html) {
        final Set<String> dedup = new LinkedHashSet<>();

        final Matcher absoluteMatcher = IMAGE_URL_PATTERN.matcher(html);
        while (absoluteMatcher.find() && dedup.size() < MAX_RESULTS) {
            final String found = absoluteMatcher.group(1).replace("\\/", "/");
            dedup.add(found);
        }

        final Matcher relativeMatcher = RELATIVE_IMAGE_URL_PATTERN.matcher(html);
        while (relativeMatcher.find() && dedup.size() < MAX_RESULTS) {
            final String found = relativeMatcher.group(1);
            dedup.add(BASE_URL + found);
        }

        final List<StickerItem> result = new ArrayList<>();
        for (final String url : dedup) {
            result.add(new StickerItem(url));
        }
        return result;
    }

    public DownloadResult downloadToCache(final StickerItem item, final File cacheDir) throws IOException {
        final Request request = new Request.Builder()
                .url(item.imageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Sticker download failed with code " + response.code());
            }
            String mime = response.header("content-type");
            if (mime != null && mime.contains(";")) {
                mime = mime.substring(0, mime.indexOf(';')).trim();
            }
            if (mime == null || !mime.startsWith("image/")) {
                final String lower = item.imageUrl.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".webp")) mime = "image/webp";
                else if (lower.endsWith(".png")) mime = "image/png";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mime = "image/jpeg";
                else mime = "image/webp";
            }
            String extension = MimeUtils.guessExtensionFromMimeType(mime);
            if (extension == null || extension.isEmpty()) {
                extension = "webp";
            }
            final File out = File.createTempFile("tlgrm_sticker_", "." + extension, cacheDir);
            try (FileOutputStream outputStream = new FileOutputStream(out)) {
                outputStream.write(body.bytes());
            }
            return new DownloadResult(out, mime);
        }
    }

    public static class DownloadResult {
        public final File file;
        public final String mime;

        public DownloadResult(@NonNull final File file, @NonNull final String mime) {
            this.file = file;
            this.mime = mime;
        }
    }
}
