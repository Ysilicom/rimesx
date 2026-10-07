package org.scholay.rimes.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local persistent storage for clipboard history with pinning support. */
final class ClipboardStore {
    private static final String PREFS_NAME="rimes_clipboard";
    private static final String KEY_ITEMS="items";
    private static final int MAX_ITEMS=50;

    static final class Entry {
        final String text;
        boolean pinned;
        final long time;
        Entry(String text,boolean pinned,long time) {
            this.text=text; this.pinned=pinned; this.time=time;
        }
    }

    private final SharedPreferences prefs;
    private final List<Entry> entries=new ArrayList<>();

    ClipboardStore(Context context) {
        prefs=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
        load();
    }

    synchronized List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    synchronized void add(String text) {
        if(text==null || text.trim().isEmpty()) return;
        Entry existing=null;
        for(Entry e:entries) {
            if(e.text.equals(text)) { existing=e; break; }
        }
        if(existing!=null) {
            entries.remove(existing);
            entries.add(0,existing);
        } else {
            entries.add(0,new Entry(text,false,System.currentTimeMillis()));
        }
        trim();
        save();
    }

    synchronized void togglePin(String text) {
        for(Entry e:entries) {
            if(e.text.equals(text)) {
                e.pinned=!e.pinned;
                break;
            }
        }
        save();
    }

    synchronized void remove(String text) {
        entries.removeIf(e -> e.text.equals(text));
        save();
    }

    synchronized void clearUnpinned() {
        entries.removeIf(e -> !e.pinned);
        save();
    }

    private void trim() {
        int unpinnedCount=0;
        for(Entry e:entries) if(!e.pinned) unpinnedCount++;
        if(unpinnedCount>MAX_ITEMS) {
            for(int i=entries.size()-1;i>=0 && unpinnedCount>MAX_ITEMS;i--) {
                if(!entries.get(i).pinned) {
                    entries.remove(i);
                    unpinnedCount--;
                }
            }
        }
    }

    private void load() {
        entries.clear();
        String json=prefs.getString(KEY_ITEMS,"[]");
        try {
            JSONArray arr=new JSONArray(json);
            for(int i=0;i<arr.length();i++) {
                JSONObject obj=arr.getJSONObject(i);
                entries.add(new Entry(obj.getString("text"),obj.optBoolean("pinned",false),obj.optLong("time",0)));
            }
        } catch(Throwable ignored) {}
    }

    private void save() {
        try {
            JSONArray arr=new JSONArray();
            for(Entry e:entries) {
                JSONObject obj=new JSONObject();
                obj.put("text",e.text);
                obj.put("pinned",e.pinned);
                obj.put("time",e.time);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_ITEMS,arr.toString()).apply();
        } catch(Throwable ignored) {}
    }
}
