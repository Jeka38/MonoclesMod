package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.bumptech.glide.Glide;

import java.util.HashMap;
import java.util.Map;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.CustomTab;
import eu.siacs.conversations.utils.MetaData;
import eu.siacs.conversations.utils.RichPreview;
import eu.siacs.conversations.utils.ThemeHelper;
import me.drakeet.support.toast.ToastCompat;


/**
 * Created by ponna on 16-01-2018.
 */

public class RichLinkView extends RelativeLayout {

    private View view;
    Context context;
    private MetaData meta;

    LinearLayout linearLayout;
    ImageView imageView;
    TextView textViewTitle;
    TextView textViewDesp;
    View quoteMessage;

    private String main_url;

    private boolean isDefaultClick = true;

    private RichPreview.RichLinkListener richLinkListener;

    public RichLinkView(Context context) {
        super(context);
        this.context = context;
    }

    public RichLinkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public RichLinkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    public RichLinkView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }


    public void initView(final boolean dataSaverDisabled, final int color) {
        if (findLinearLayoutChild() != null) {
            this.view = findLinearLayoutChild();
        } else {
            this.view = this;
            inflate(context, R.layout.link_layout, this);
        }
        linearLayout = findViewById(R.id.rich_link_card);
        imageView = findViewById(R.id.rich_link_image);
        textViewTitle = findViewById(R.id.rich_link_title);
        textViewTitle.setTextColor(color);
        textViewDesp = findViewById(R.id.rich_link_desp);
        textViewDesp.setTextColor(color);
        quoteMessage = findViewById(R.id.quote_message);
        quoteMessage.setBackgroundColor(color);
        imageView.setAdjustViewBounds(true);
        if (view != null && meta != null && meta.getImageurl() != null && !meta.getImageurl().equals("") && !meta.getImageurl().isEmpty() && imageView != null) {
            if (!dataSaverDisabled) {
                Glide.with(this).load(R.drawable.ic_web_grey600_48).into(imageView);
            } else {
                imageView.setVisibility(VISIBLE);
                Glide.with(this)
                        .load(meta.getImageurl())
                        .override(80,80)
                        .centerInside()
                        .placeholder(R.drawable.ic_web_grey600_48)
                        .error(R.drawable.ic_web_grey600_48)
                        .into(imageView);
            }
        } else {
            if (view != null && meta != null && imageView != null) {
                imageView.setVisibility(VISIBLE);
                Glide.with(this)
                    .load(R.drawable.ic_web_grey600_48)
                    .into(imageView);
            }
        }
        if (meta != null && (meta.getTitle().isEmpty() || meta.getTitle().equals(""))) {
            textViewTitle.setVisibility(VISIBLE);
            textViewTitle.setText(meta.getUrl());
        }
        if (meta != null && (meta.getDescription().isEmpty() || meta.getDescription().equals(""))) {
            textViewDesp.setVisibility(GONE);
        }

        linearLayout.setOnClickListener(view -> {
            if (isDefaultClick) {
                richLinkClicked();
            } else {
                if (richLinkListener != null) {
                    richLinkListener.onClicked(view, meta);
                } else {
                    richLinkClicked();
                }
            }
        });
    }

    private void richLinkClicked() {
        try {
            CustomTab.openTab(this.context, Uri.parse(main_url), ThemeHelper.isDark(ThemeHelper.find(this.context)));
        } catch (Exception e) {
            ToastCompat.makeText(this.context, R.string.no_application_found_to_open_link, ToastCompat.LENGTH_SHORT).show();
        }
    }

    public void setDefaultClickListener(boolean isDefault) {
        isDefaultClick = isDefault;
    }

    public void setClickListener(RichPreview.RichLinkListener richLinkListener1) {
        richLinkListener = richLinkListener1;
    }

    protected LinearLayout findLinearLayoutChild() {
        if (getChildCount() > 0 && getChildAt(0) instanceof LinearLayout) {
            return (LinearLayout) getChildAt(0);
        }
        return null;
    }

    /*
    public void setLinkFromMeta(MetaData metaData) {
        meta = metaData;
        initView(true);
    }
    */

    public MetaData getMetaData() {
        return meta;
    }

    private static Map<String, MetaData> linkMap = new HashMap<>();

    public void setLink(final String url, final String filename, final boolean dataSaverDisabled, final XmppConnectionService mXmppConnectionService, final int color, final RichPreview.ViewListener viewListener) {
        MetaData data = linkMap.get(url);
        if (data == null) {
            main_url = url;
            RichPreview richPreview = new RichPreview(new RichPreview.ResponseListener() {
                @Override
                public void onData(MetaData metaData) {
                    meta = metaData;
                    if (!meta.getTitle().isEmpty() || !meta.getTitle().equals("")) {
                        viewListener.onSuccess(true);
                        linkMap.put(url, metaData);
                    }
                    initView(dataSaverDisabled, color);
                }

                @Override
                public void onError(Exception e) {
                    viewListener.onError(e);
                }
            });
            richPreview.getPreview(url, filename, mXmppConnectionService);
        } else {
            main_url = url;
            meta = data;
            viewListener.onSuccess(true);
            initView(dataSaverDisabled, color);
        }
    }
}
