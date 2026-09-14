package com.saad.capvid.style;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists user-edited templates ("New custom template" in the Custom tab).
 * A custom template = an existing style id + color/font overrides + a new
 * display name. Stored as a JSON array in SharedPreferences (no DB needed).
 */
public class CustomTemplateManager {

    private static final String PREFS = "capvid_custom_templates";
    private static final String KEY_LIST = "templates_json";

    private final SharedPreferences prefs;

    public CustomTemplateManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<CaptionStyleDefinition> loadAll() {
        List<CaptionStyleDefinition> result = new ArrayList<>();
        String json = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int[] swatches = new int[4];
                JSONArray sw = o.getJSONArray("swatches");
                for (int s = 0; s < 4; s++) swatches[s] = sw.getInt(s);

                result.add(new CaptionStyleDefinition(
                        o.getString("id"),
                        o.getString("displayName"),
                        java.util.Collections.singletonList(CaptionStyleCategory.CUSTOM),
                        swatches,
                        o.getString("fontAsset"),
                        false,
                        CaptionStyleDefinition.PreviewTreatment.valueOf(o.getString("treatment")),
                        o.optBoolean("modern", false)
                ));
            }
        } catch (JSONException e) {
            // corrupted prefs shouldn't crash the picker — just show an empty Custom tab
            return new ArrayList<>();
        }
        return result;
    }

    public void save(CaptionStyleDefinition def) {
        List<CaptionStyleDefinition> current = loadAll();
        current.removeIf(d -> d.id.equals(def.id)); // overwrite if same id already saved
        current.add(def);
        persist(current);
    }

    public void delete(String id) {
        List<CaptionStyleDefinition> current = loadAll();
        current.removeIf(d -> d.id.equals(id));
        persist(current);
    }

    private void persist(List<CaptionStyleDefinition> list) {
        JSONArray arr = new JSONArray();
        try {
            for (CaptionStyleDefinition d : list) {
                JSONObject o = new JSONObject();
                o.put("id", d.id);
                o.put("displayName", d.displayName);
                JSONArray sw = new JSONArray();
                for (int c : d.swatchColors) sw.put(c);
                o.put("swatches", sw);
                o.put("fontAsset", d.fontAsset);
                o.put("treatment", d.treatment.name());
                o.put("modern", d.isModern);
                arr.put(o);
            }
        } catch (JSONException e) {
            return; // best-effort persistence, never crash the editor over this
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply();
    }
}
