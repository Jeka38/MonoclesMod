package eu.siacs.conversations.ui.adapter;

import static eu.siacs.conversations.entities.Message.DELETED_MESSAGE_BODY;
import static eu.siacs.conversations.entities.Message.DELETED_MESSAGE_BODY_OLD;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.adapter.model.MessageLogModel;

public class MessageLogAdapter extends ArrayAdapter<MessageLogModel> implements View.OnClickListener {

    private final ArrayList<MessageLogModel> dataSet;
    Context mContext;

    // View lookup cache
    private static class ViewHolder {
        TextView txtLineNr;
        TextView txtBody;
        TextView txtTimeSent;
    }

    public MessageLogAdapter(ArrayList<MessageLogModel> data, Context context) {
        super(context, R.layout.message_log_item, data);
        this.dataSet = data;
        this.mContext = context;
    }

    @Override
    public void onClick(View v) {

    }

    private int lastPosition = -1;

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        MessageLogModel dataModel = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        ViewHolder viewHolder; // view lookup cache stored in tag

        final View result;

        if (convertView == null) {

            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.message_log_item, parent, false);
            viewHolder.txtLineNr = convertView.findViewById(R.id.nr);
            viewHolder.txtBody = convertView.findViewById(R.id.body);
            viewHolder.txtTimeSent = convertView.findViewById(R.id.timeSent);

            result = convertView;

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
            result = convertView;
        }

        Animation animation = AnimationUtils.loadAnimation(mContext, (position > lastPosition) ? R.anim.ufb : R.anim.dft);
        result.startAnimation(animation);
        lastPosition = position;
        viewHolder.txtLineNr.setText(String.valueOf(position + 1));
        viewHolder.txtBody.setText(preview(dataModel));
        viewHolder.txtTimeSent.setText(getTimeSentFormated(dataModel.getTimeSent()));
        // Return the completed view to render on screen
        return convertView;
    }

    private String getTimeSentFormated(long timeSent) {
        return android.text.format.DateFormat.getDateFormat(getContext()).format(new Date(timeSent)) + " " + android.text.format.DateFormat.getTimeFormat(getContext()).format(new Date(timeSent));
    }

    private String preview(MessageLogModel dataModel) {
        if (hasDeletedBody(dataModel.getBody())) {
            return getContext().getString(R.string.message_deleted);
        } else {
            return dataModel.getBody();
        }
    }

    public boolean hasDeletedBody(String message) {
        return message.trim().equals(DELETED_MESSAGE_BODY) || message.trim().equals(DELETED_MESSAGE_BODY_OLD);
    }
}