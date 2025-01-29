package l2ft.gameserver.utils;

import java.util.HashMap;
import java.util.Map;

public class ClassAbbreviations {
    private static final Map<Integer, String> CLASS_ABBREVIATIONS = new HashMap<>();
    static {
        CLASS_ABBREVIATIONS.put(88, "GL");
        CLASS_ABBREVIATIONS.put(89, "WL");
        CLASS_ABBREVIATIONS.put(90, "PA");
        CLASS_ABBREVIATIONS.put(91, "DA");
        CLASS_ABBREVIATIONS.put(92, "HE");
        CLASS_ABBREVIATIONS.put(93, "TH");
        CLASS_ABBREVIATIONS.put(94, "SC");
        CLASS_ABBREVIATIONS.put(95, "NE");
        CLASS_ABBREVIATIONS.put(96, "WK");
        CLASS_ABBREVIATIONS.put(97, "BP");
        CLASS_ABBREVIATIONS.put(98, "PP");
        CLASS_ABBREVIATIONS.put(99, "TK");
        CLASS_ABBREVIATIONS.put(100, "SWS");
        CLASS_ABBREVIATIONS.put(101, "PW");
        CLASS_ABBREVIATIONS.put(102, "SR");
        CLASS_ABBREVIATIONS.put(103, "SpS");
        CLASS_ABBREVIATIONS.put(104, "ES");
        CLASS_ABBREVIATIONS.put(105, "EE");
        CLASS_ABBREVIATIONS.put(106, "SK");
        CLASS_ABBREVIATIONS.put(107, "BD");
        CLASS_ABBREVIATIONS.put(108, "AW");
        CLASS_ABBREVIATIONS.put(109, "PR");
        CLASS_ABBREVIATIONS.put(110, "SH");
        CLASS_ABBREVIATIONS.put(111, "PS");
        CLASS_ABBREVIATIONS.put(112, "SE");
        CLASS_ABBREVIATIONS.put(113, "DS");
        CLASS_ABBREVIATIONS.put(114, "TY");
        CLASS_ABBREVIATIONS.put(115, "OL");
        CLASS_ABBREVIATIONS.put(116, "WC");
        CLASS_ABBREVIATIONS.put(117, "BH");
    }

    public static String getAbbrev(int classId) {
        String abbrev = CLASS_ABBREVIATIONS.get(classId);
        return abbrev != null ? abbrev : String.valueOf(classId);
    }
}
