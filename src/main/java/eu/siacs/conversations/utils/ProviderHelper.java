package eu.siacs.conversations.utils;

import com.google.gson.annotations.SerializedName;

public class ProviderHelper {

    @SerializedName("jid")
    public String jid;

    public final String get_jid() {
        return this.jid;
    }
}