package de.monocles.chat;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.databinding.DataBindingUtil;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Comparable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.EmojiSearchRowBinding;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;

public class EmojiSearch {
    protected final Set<Emoji> emoji = new TreeSet<>();

    public EmojiSearch(Context context) {
                /*      TODO: No emoji search needed since there already is an emoji keyboard
        try {
            final JSONArray data = new JSONArray(CharStreams.toString(new InputStreamReader(context.getResources().openRawResource(R.raw.emoji), "UTF-8")));
            for (int i = 0; i < data.length(); i++) {
                emoji.add(new Emoji(data.getJSONObject(i)));
            }
        } catch (final JSONException | IOException e) {
            throw new IllegalStateException("emoji.json invalid: " + e);
        }
                 */
    }

    public synchronized void addEmoji(final Emoji one) {
        emoji.add(one);
    }

    public synchronized List<Emoji> find(final String q) {
        final ResultPQ pq = new ResultPQ();
        for (Emoji e : emoji) {
            if (e.emoticonMatch(q)) {
                pq.addTopK(e, 999999, 999);
            }
            int shortcodeScore = e.shortcodes.isEmpty() ? 0 : Collections.max(Lists.transform(e.shortcodes, (shortcode) -> FuzzySearch.ratio(q, shortcode)));
            int tagScore = e.tags.isEmpty() ? 0 : Collections.max(Lists.transform(e.tags, (tag) -> FuzzySearch.ratio(q, tag))) - 2;
            pq.addTopK(e, Math.max(shortcodeScore, tagScore), 999);
        }

        for (BoundExtractedResult<Emoji> r : new ArrayList<>(pq)) {
            for (Emoji e : emoji) {
                if (e.shortcodeMatch(r.getReferent().uniquePart())) {
                    // hack see https://stackoverflow.com/questions/76880072/imagespan-with-emojicompat
                    e.shortcodes.clear();
                    e.shortcodes.addAll(r.getReferent().shortcodes);

                    pq.addTopK(e, r.getScore() - 1, 999);
                }
            }
        }

        List<Emoji> result = new ArrayList<>();
        for (int i = 0; i < 999; i++) {
            BoundExtractedResult<Emoji> e = pq.poll();
            if (e != null) result.add(e.getReferent());
        }
        Collections.reverse(result);
        return result;
    }

    public EmojiSearchAdapter makeAdapter(Activity context) {
        return new EmojiSearchAdapter(context);
    }

    public static class ResultPQ extends PriorityQueue<BoundExtractedResult<Emoji>> {
        public void addTopK(Emoji e, int score, int k) {
            BoundExtractedResult r = new BoundExtractedResult(e, null, score, 0);
            if (size() < k) {
                add(r);
            } else if (r.compareTo(peek()) > 0) {
                poll();
                add(r);
            }
        }
    }

    public static class Emoji implements Comparable<Emoji> {
        protected final String unicode;
        protected final int order;
        protected final List<String> tags = new ArrayList<>();
        protected final List<String> emoticon = new ArrayList<>();
        protected final List<String> shortcodes = new ArrayList<>();

        public Emoji(final String unicode, final int order) {
            this.unicode = unicode;
            this.order = order;
        }

        public Emoji(JSONObject o) throws JSONException {
            unicode = o.getString("unicode");
            order = o.getInt("order");
            final JSONArray rawTags = o.getJSONArray("tags");
            for (int i = 0; i < rawTags.length(); i++) {
                tags.add(rawTags.getString(i));
            }
            final JSONArray rawEmoticon = o.getJSONArray("emoticon");
            for (int i = 0; i < rawEmoticon.length(); i++) {
                emoticon.add(rawEmoticon.getString(i));
            }
            final JSONArray rawShortcodes = o.getJSONArray("shortcodes");
            for (int i = 0; i < rawShortcodes.length(); i++) {
                shortcodes.add(rawShortcodes.getString(i));
            }
        }

        public boolean emoticonMatch(final String q) {
            for (final String emote : emoticon) {
                if (emote.equals(q) || emote.equals(":" + q)) return true;
            }

            return false;
        }

        public boolean shortcodeMatch(final String q) {
            for (final String shortcode : shortcodes) {
                if (shortcode.equals(q)) return true;
            }

            return false;
        }

        public SpannableStringBuilder toInsert() {
            return new SpannableStringBuilder(unicode);
        }

        public String uniquePart() {
            return unicode;
        }

        @Override
        public int compareTo(Emoji o) {
            if (equals(o)) return 0;
            if (order == o.order) return uniquePart().compareTo(o.uniquePart());
            return order - o.order;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Emoji)) return false;

            return uniquePart().equals(((Emoji) o).uniquePart());
        }
    }

    public static class CustomEmoji extends Emoji {
        protected final String source;
        protected final Drawable icon;

        public CustomEmoji(final String shortcode, final String source, final Drawable icon, final String tag) {
            super(null, 999);
            shortcodes.add(shortcode);
            if (tag != null) tags.add(tag);
            this.source = source;
            this.icon = icon;
            if (icon == null) {
                throw new IllegalArgumentException("icon must not be null");
            }
        }

        public SpannableStringBuilder toInsert() {
            SpannableStringBuilder builder = new SpannableStringBuilder(":" + shortcodes.get(0) + ":");
            builder.setSpan(new InlineImageSpan(icon, source), 0, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }

        @Override
        public String uniquePart() {
            return source;
        }
    }

    public class EmojiSearchAdapter extends ArrayAdapter<Emoji> {
        ReplacingSerialSingleThreadExecutor executor = new ReplacingSerialSingleThreadExecutor("EmojiSearchAdapter");

        public EmojiSearchAdapter(Activity context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            EmojiSearchRowBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.emoji_search_row, parent, false);
            if (getItem(position) instanceof CustomEmoji) {
                binding.nonunicode.setText(getItem(position).toInsert());
                binding.nonunicode.setVisibility(View.VISIBLE);
                binding.unicode.setVisibility(View.GONE);
            } else {
                binding.unicode.setText(getItem(position).toInsert());
                binding.unicode.setVisibility(View.VISIBLE);
                binding.nonunicode.setVisibility(View.GONE);
            }
            binding.shortcode.setText(getItem(position).shortcodes.get(0));
            return binding.getRoot();
        }

        public void search(final String q) {
            executor.execute(() -> {
                final List<Emoji> results = find(q);
                ((Activity) getContext()).runOnUiThread(() -> {
                    clear();
                    addAll(results);
                    notifyDataSetChanged();
                });
            });
        }
    }
}
