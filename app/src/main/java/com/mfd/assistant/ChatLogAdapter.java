package com.mfd.assistant;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MFD - Mehmet Fatih DURSUN
 * Chat log adapter v4 — sohbet balonları
 */
public class ChatLogAdapter extends RecyclerView.Adapter<ChatLogAdapter.VH> {

    public enum Type { USER, MFD, SYS, TOOL, ERR }

    public static class Entry {
        public final Type   type;
        public final String sender;
        public final String text;
        public final String time;

        Entry(Type t, String s, String msg) {
            this.type   = t;
            this.sender = s;
            this.text   = msg;
            this.time   = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        }
    }

    public static Entry user(String msg) { return new Entry(Type.USER, "SİZ",  msg); }
    public static Entry mfd(String msg)  { return new Entry(Type.MFD,  "MFD",  msg); }
    public static Entry sys(String msg)  { return new Entry(Type.SYS,  "⚙",   msg); }
    public static Entry tool(String n, String r) { return new Entry(Type.TOOL, "🔧 " + n, r); }
    public static Entry err(String msg)  { return new Entry(Type.ERR,  "❌",   msg); }

    private final List<Entry> items = new ArrayList<>();

    public void add(Entry e) {
        items.add(e);
        notifyItemInserted(items.size() - 1);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final TextView tvSender;
        final TextView tvMessage;

        VH(View v) {
            super(v);
            root      = (LinearLayout) v;
            tvSender  = v.findViewById(R.id.tvSender);
            tvMessage = v.findViewById(R.id.tvMessage);
        }

        void bind(Entry e) {
            tvSender.setText(e.sender + "  " + e.time);

            // Truncate very long tool results for readability
            String displayText = e.text;
            if (e.type == Type.TOOL && displayText != null && displayText.length() > 180) {
                displayText = displayText.substring(0, 180) + "…";
            }
            tvMessage.setText(displayText);

            float density = itemView.getContext().getResources().getDisplayMetrics().density;
            int radius = (int)(14 * density);
            int padH   = (int)(12 * density);
            int padV   = (int)(8  * density);
            tvMessage.setPadding(padH, padV, padH, padV);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(radius);

            switch (e.type) {
                case USER:
                    root.setGravity(Gravity.END);
                    tvSender.setGravity(Gravity.END);
                    tvMessage.setTextColor(Color.parseColor("#0D0D0F"));
                    bg.setColor(Color.parseColor("#00C8A0"));
                    break;
                case MFD:
                    root.setGravity(Gravity.START);
                    tvSender.setGravity(Gravity.START);
                    tvMessage.setTextColor(Color.parseColor("#F0F0F5"));
                    bg.setColor(Color.parseColor("#1A1A2A"));
                    break;
                case ERR:
                    root.setGravity(Gravity.START);
                    tvSender.setGravity(Gravity.START);
                    tvMessage.setTextColor(Color.parseColor("#FF8899"));
                    bg.setColor(Color.parseColor("#2A1012"));
                    break;
                case TOOL:
                    root.setGravity(Gravity.START);
                    tvSender.setGravity(Gravity.START);
                    tvMessage.setTextColor(Color.parseColor("#88AAFF"));
                    bg.setColor(Color.parseColor("#131825"));
                    break;
                default: // SYS
                    root.setGravity(Gravity.CENTER);
                    tvSender.setGravity(Gravity.CENTER);
                    tvMessage.setTextColor(Color.parseColor("#666680"));
                    bg.setColor(Color.TRANSPARENT);
                    break;
            }
            tvMessage.setBackground(bg);
        }
    }
}
