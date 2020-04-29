package com.github.jpingus.model;

import java.util.Objects;

public class Variable {
    private String field;
    private String variable;

    public Variable() {
    }

    public Variable(String field, String variable) {
        this.field = field;
        this.variable = variable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable variable1 = (Variable) o;

        if (!Objects.equals(field, variable1.field)) return false;
        return Objects.equals(variable, variable1.variable);
    }

    @Override
    public int hashCode() {
        int result = field != null ? field.hashCode() : 0;
        result = 31 * result + (variable != null ? variable.hashCode() : 0);
        return result;
    }

    public String getVariable() {
        return variable;
    }

    public void setVariable(String variable) {
        this.variable = variable;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
