package com.saad.capvid.project;

import android.content.Context;
import android.content.SharedPreferences;

import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs the "Project" tab on the home screen. Same storage approach as
 * CustomTemplateManager (JSON array in SharedPreferences — no DB needed for
 * this data size). One Project per distinct source video: PreviewActivity
 * upserts by videoUri so reopening the same video resumes its saved state
 * instead of duplicating a project entry.
 */
public class ProjectManager {

    private static final String PREFS = "capvid_projects";
    private static final String KEY_LIST = "projects_json";

    private final SharedPreferences prefs;

    public ProjectManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Project> loadAll() {
        List<Project> result = new ArrayList<>();
        String json = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        result.sort((a, b) -> Long.compare(b.lastEditedMs, a.lastEditedMs));
        return result;
    }

    public Project findByVideoUri(String videoUri) {
        for (Project p : loadAll()) {
            if (p.videoUri.equals(videoUri)) return p;
        }
        return null;
    }

    public Project findById(String id) {
        for (Project p : loadAll()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    /** Insert or overwrite (matched by id). Call whenever meaningful editor state changes. */
    public void save(Project project) {
        List<Project> current = loadAll();
        current.removeIf(p -> p.id.equals(project.id));
        current.add(project);
        persist(current);
    }

    public void delete(String id) {
        List<Project> current = loadAll();
        current.removeIf(p -> p.id.equals(id));
        persist(current);
    }

    private void persist(List<Project> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Project p : list) arr.put(toJson(p));
        } catch (JSONException e) {
            return; // best-effort persistence, never crash the editor over this
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply();
    }

    private JSONObject toJson(Project p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("videoUri", p.videoUri);
        o.put("createdAtMs", p.createdAtMs);
        o.put("lastEditedMs", p.lastEditedMs);
        o.put("trimStartMs", p.trimStartMs);
        o.put("trimEndMs", p.trimEndMs);
        o.put("scaleFactor", p.scaleFactor);
        o.put("styleId", p.styleId);
        o.put("textSizeSp", (double) p.textSizeSp);
        o.put("bold", p.bold);
        o.put("italic", p.italic);
        o.put("posXFraction", (double) p.posXFraction);
        o.put("posYFraction", (double) p.posYFraction);
        o.put("options", p.options != null ? p.options.toJson() : new CaptionStyleOptions().toJson());

        JSONArray words = new JSONArray();
        for (CaptionWord w : p.words) {
            JSONObject wo = new JSONObject();
            wo.put("text", w.text);
            wo.put("startMs", w.startMs);
            wo.put("endMs", w.endMs);
            words.put(wo);
        }
        o.put("words", words);
        return o;
    }

    private Project fromJson(JSONObject o) throws JSONException {
        Project p = new Project();
        p.id = o.getString("id");
        p.name = o.getString("name");
        p.videoUri = o.getString("videoUri");
        p.createdAtMs = o.getLong("createdAtMs");
        p.lastEditedMs = o.getLong("lastEditedMs");
        p.trimStartMs = o.optLong("trimStartMs", 0);
        p.trimEndMs = o.optLong("trimEndMs", -1);
        p.scaleFactor = (float) o.optDouble("scaleFactor", 1.0);
        p.styleId = o.optString("styleId", "MINIMAL_FADE");
        p.textSizeSp = (float) o.optDouble("textSizeSp", 20.0);
        p.bold = o.optBoolean("bold", false);
        p.italic = o.optBoolean("italic", false);
        p.posXFraction = (float) o.optDouble("posXFraction", 0.5);
        p.posYFraction = (float) o.optDouble("posYFraction", 0.85);
        p.options = CaptionStyleOptions.fromJson(o.optJSONObject("options"));

        JSONArray words = o.optJSONArray("words");
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                JSONObject wo = words.getJSONObject(i);
                p.words.add(new CaptionWord(wo.getString("text"), wo.getLong("startMs"), wo.getLong("endMs")));
            }
        }
        return p;
    }
}
