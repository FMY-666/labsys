package com.labsys.dto;

import java.util.List;

public class PageVO<T> {
    private long total;
    private List<T> list;

    public PageVO(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }

    public long getTotal() { return total; }
    public List<T> getList() { return list; }
}
