package com.onda.shared.pagination;

public class CursorPageable {

    private String cursor;
    private int limit = 20;

    public CursorPageable() {
    }

    public CursorPageable(String cursor, int limit) {
        this.cursor = cursor;
        this.limit = Math.min(Math.max(limit, 1), 50);
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.min(Math.max(limit, 1), 50);
    }

    public boolean hasCursor() {
        return cursor != null && !cursor.isEmpty();
    }
}
