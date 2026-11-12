package app.fanela.tv;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Circuit outlines from res/raw/circuits.json, keyed by ESPN circuit id (F1-UFC-ARCHITECTURE §6.1). The file is built
 * offline by tools/circuits.py from bacinger/f1-circuits (GeoJSON, MIT licence, © Tomislav Bacinger): each track's
 * centre line projected flat, simplified to about 100 points and stored as integers 0..1000 (north up, the longer side
 * 1000). Cards draw it as a line in their own colours, so no image licence is involved. A circuit the file doesn't
 * list (a new venue, the Bahrain outer track) has no outline: the cards fall back to the flag. Stream E owns this file.
 */
final class Circuits {
    private Circuits() { }

    private static volatile Map<String, float[]> all;

    /** Normalised points x0,y0,x1,y1… in 0..1 (the longer side spans 0..1); null when the circuit has no outline. */
    static float[] outline(Context c, String espnCircuitId) {
        if (c == null || espnCircuitId == null || espnCircuitId.isEmpty()) return null;
        Map<String, float[]> m = all;
        if (m == null) {
            synchronized (Circuits.class) {
                if (all == null) all = load(c);
                m = all;
            }
        }
        return m.get(espnCircuitId);
    }

    private static Map<String, float[]> load(Context c) {
        try (InputStream in = c.getResources().openRawResource(R.raw.circuits)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            return parse(o.toString("UTF-8"));
        } catch (Exception e) {
            android.util.Log.w("Fanela", "circuits", e);
            return new HashMap<>();
        }
    }

    /** {"circuits": {"<espn id>": {"p": [x0, y0, x1, y1, …]}}} → id → points in 0..1. Pure (tests). */
    static Map<String, float[]> parse(String json) throws org.json.JSONException {
        Map<String, float[]> out = new HashMap<>();
        JSONObject cs = new JSONObject(json).getJSONObject("circuits");
        for (Iterator<String> it = cs.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONArray a = cs.getJSONObject(id).getJSONArray("p");
            if (a.length() < 6 || a.length() % 2 != 0) continue;
            float[] p = new float[a.length()];
            for (int i = 0; i < p.length; i++) p[i] = (float) (a.getDouble(i) / 1000.0);
            out.put(id, p);
        }
        return out;
    }

    /**
     * The points fitted (contain) and centred in the box (l, t, r, b), the aspect kept; null without points. Pure
     * arithmetic: unit-tested.
     */
    static float[] fit(float[] pts, float l, float t, float r, float b) {
        if (pts == null || pts.length < 6) return null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < pts.length; i += 2) {
            minX = Math.min(minX, pts[i]); maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]); maxY = Math.max(maxY, pts[i + 1]);
        }
        float w = Math.max(maxX - minX, 1e-6f), h = Math.max(maxY - minY, 1e-6f);
        float s = Math.min((r - l) / w, (b - t) / h);
        float ox = l + ((r - l) - s * w) / 2 - s * minX, oy = t + ((b - t) - s * h) / 2 - s * minY;
        float[] out = new float[pts.length];
        for (int i = 0; i + 1 < pts.length; i += 2) { out[i] = ox + s * pts[i]; out[i + 1] = oy + s * pts[i + 1]; }
        return out;
    }
}
