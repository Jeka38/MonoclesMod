package eu.siacs.conversations.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TrackingHelper {
    // https://github.com/newhouse/url-tracking-stripper
    public static final List<String> TRACKING_PARAMETER = Collections.unmodifiableList(Arrays.asList(
            // https://en.wikipedia.org/wiki/UTM_parameters
            "icid", // Adobe
            "gclid", // Google
            "gclsrc", // Google ads
            "dclid", // DoubleClick (Google)
            "utm_source", // Google's Urchin Tracking Module
            "utm_medium", // Google's Urchin Tracking Module
            "utm_term", // Google's Urchin Tracking Module
            "utm_campaign", // Google's Urchin Tracking Module
            "utm_content", // Google's Urchin Tracking Module
            "utm_name", // Google's Urchin Tracking Module
            "utm_cid", // Google's Urchin Tracking Module
            "utm_reader", // Google's Urchin Tracking Module
            "utm_viz_id", // Google's Urchin Tracking Module
            "utm_pubreferrer", // Google's Urchin Tracking Module
            "utm_swu", // Google's Urchin Tracking Module
            "fbclid", // Facebook
            "igshid", // Instagram
            "mc_cid", // MailChimp
            "mc_eid", // MailChimp
            "mkt_tok", // Marketo
            "mc_cid", // MailChimp
            "mc_eid", // MailChimp
            "ncid", // unknown
            "ref", // unknown
            "sr_share", // Simple Research
            "vero_conv", // Vero
            "vero_id", // Vero
            "zanpid", // Zanox (Awin)
            "kclickid" // https://support.freespee.com/hc/en-us/articles/202577831-Kenshoo-integration
    ));
}
