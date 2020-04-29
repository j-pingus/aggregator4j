package com.github.jpingus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Map;

public class Business {
    public Map<String, GrandTotal> myGrandTotals;
    public Row2[] elements2;
    public ArrayList<Row2> elements3;
    public HashSet<Rows> elements4;
    public Dictionary<String, Rows> elements5;
    @Execute("sum('total2')")
    public Integer total2;
    @Execute("count('total2')*2")
    public Integer doubleCount;
    @Execute("avg('total2')")
    public double avg2;
    @Execute("my:rate(sum('total2'))")
    public double rate;
    @Execute("'['+join(',','All my ccm2 ids')+']'")
    public String ccm2;
    @Execute("avg('Big decimal')")
    public BigDecimal totalBig;
    @Execute("sum('total')")
    Integer total;
    Row[] elements;

    public Row[] getElements() {
        return elements;
    }

    public void setElements(Row[] elements) {
        this.elements = elements;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}