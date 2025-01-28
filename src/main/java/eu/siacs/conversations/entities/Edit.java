package eu.siacs.conversations.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Edit {

    private final String editedId;
    private final String serverMsgId;
    private String body;
    private final long timeSent;

    Edit(String editedId, String serverMsgId, String body, long timeSent) {
        this.editedId = editedId;
        this.serverMsgId = serverMsgId;
        this.body = body;
        this.timeSent = timeSent;
    }

    static String toJson(List<Edit> edits, boolean hidebody) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (Edit edit : edits) {
            jsonArray.put(edit.toJson(hidebody));
        }
        return jsonArray.toString();
    }

    static boolean wasPreviouslyEditedRemoteMsgId(List<Edit> edits, String remoteMsgId) {
        for (Edit edit : edits) {
            if (edit.editedId != null && edit.editedId.equals(remoteMsgId)) {
                return true;
            }
        }
        return false;
    }

    static boolean wasPreviouslyEditedServerMsgId(List<Edit> edits, String serverMsgId) {
        for (Edit edit : edits) {
            if (edit.serverMsgId != null && edit.serverMsgId.equals(serverMsgId)) {
                return true;
            }
        }
        return false;
    }

    private static Edit fromJson(JSONObject jsonObject) throws JSONException {
        String edited = jsonObject.has("edited_id") ? jsonObject.getString("edited_id") : null;
        String serverMsgId = jsonObject.has("server_msg_id") ? jsonObject.getString("server_msg_id") : null;
        String body = jsonObject.has("body") ? jsonObject.getString("body") : null;
        long timeSent = jsonObject.has("timeSent") ? jsonObject.getLong("timeSent") : null;
        return new Edit(edited, serverMsgId, body, timeSent);
    }

    static List<Edit> fromJson(String input) {
        final ArrayList<Edit> list = new ArrayList<>();
        if (input == null) {
            return list;
        }
        try {
            final JSONArray jsonArray = new JSONArray(input);
            for (int i = 0; i < jsonArray.length(); ++i) {
                list.add(fromJson(jsonArray.getJSONObject(i)));
            }
            return list;
        } catch (JSONException e) {
            return list;
        }
    }

    private JSONObject toJson(boolean hidebody) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("edited_id", editedId);
        jsonObject.put("server_msg_id", serverMsgId);
        jsonObject.put("body", hidebody ? "" : body);
        jsonObject.put("timeSent", timeSent);
        return jsonObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Edit edit = (Edit) o;

        if (editedId != null ? !editedId.equals(edit.editedId) : edit.editedId != null)
            return false;
        return serverMsgId != null ? serverMsgId.equals(edit.serverMsgId) : edit.serverMsgId == null;
    }

    @Override
    public int hashCode() {
        int result = editedId != null ? editedId.hashCode() : 0;
        result = 31 * result + (serverMsgId != null ? serverMsgId.hashCode() : 0);
        return result;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public String getBody() {
        return body;
    }

    public String getEditedId() { return editedId; }

    public long getTimeSent() {
        return timeSent;
    }
}