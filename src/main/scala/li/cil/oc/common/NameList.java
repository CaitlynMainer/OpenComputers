package li.cil.oc.common;

import li.cil.oc.OpenComputers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads editable, comment-friendly name pools from the mod resources. */
public final class NameList {
    public static String[] load(String domain, String pool) {
        String path = "/assets/" + domain + "/names/" + pool + ".names";
        try (InputStream stream = NameList.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Resource not found: " + path);

            List<String> names = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int comment = line.indexOf('#');
                    if (comment >= 0) line = line.substring(0, comment);
                    line = line.trim();
                    if (!line.isEmpty()) names.add(line);
                }
            }
            return names.toArray(new String[0]);
        } catch (Throwable error) {
            OpenComputers.log().warn("Failed loading name list '" + pool + "'.", error);
            return new String[0];
        }
    }

    private NameList() {}
}
