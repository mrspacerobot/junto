package ch.junto.activity.entity;

import java.util.HashMap;

public class CustomFields extends HashMap<String, Object> {

    public String getString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    public Integer getInt(String key) {
        Object val = get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return null;
    }

    public Double getDouble(String key) {
        Object val = get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    public Boolean getBoolean(String key) {
        Object val = get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return null;
    }
}
