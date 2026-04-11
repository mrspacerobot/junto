package com.onda.shared.pagination;

import java.util.List;

public class CursorPage<T> {

    private List<T> data;
    private String nextCursor;

    public CursorPage(List<T> data, String nextCursor) {
        this.data = data;
        this.nextCursor = nextCursor;
    }

    public List<T> getData() {
        return data;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public boolean hasNext() {
        return nextCursor != null;
    }
}
