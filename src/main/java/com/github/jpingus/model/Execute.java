package com.github.jpingus.model;

import java.util.Objects;

public class Execute {
    private String jexl;
    private String when;
    private String field;

    public Execute() {

    }

    public Execute(String field, String jexl, String when) {
        this.field = field;
        this.jexl = jexl;
        this.when = "".equals(when) ? null : when;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Execute execute = (Execute) o;

        if (!Objects.equals(jexl, execute.jexl)) return false;
        if (!Objects.equals(when, execute.when)) return false;
        return field.equals(execute.field);
    }

    @Override
    public int hashCode() {
        int result = jexl.hashCode();
        result = 31 * result + (when != null ? when.hashCode() : 0);
        result = 31 * result + field.hashCode();
        return result;
    }

    public String getJexl() {
        return jexl;
    }

    public void setJexl(String jexl) {
        this.jexl = jexl;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }


}
