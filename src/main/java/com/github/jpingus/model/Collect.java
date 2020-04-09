package com.github.jpingus.model;

import java.util.Objects;

public class Collect {
    private String field;
    private String what;
    private String to;
    private String when;

    public Collect(String field, String what, String to, String when) {
        this.field = field;
        this.to = to;
        this.what = "this".equals(what) ? null : what;
        this.when = "".equals(when) ? null : when;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getWhat() {
        return what;
    }

    public void setWhat(String what) {
        this.what = what;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Collect collect = (Collect) o;

        if (!Objects.equals(field, collect.field)) return false;
        if (!Objects.equals(what, collect.what)) return false;
        if (!Objects.equals(to, collect.to)) return false;
        return Objects.equals(when, collect.when);
    }

    @Override
    public int hashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (what != null ? what.hashCode() : 0);
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + (when != null ? when.hashCode() : 0);
        return result;
    }
}
