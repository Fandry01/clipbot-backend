package com.example.clipbot_backend.util;

import java.util.Map;

public class RenderMeta {
    public static Integer intOf(Map<String, Object> m, String key){
        if(m == null) return null;
        Object v = m.get(key);
        if(v instanceof Number n) return n.intValue();
        try {return v != null ? Integer.parseInt(v.toString()) : null;}
        catch( Exception e ) { return null; }
    }
    public static String stringOf(Map<String, Object> m, String key){
        if(m == null) return null;
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
