package com.github.jpingus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Class {
    private String classContext;
    private String className;
    private List<Execute> executeList;
    private List<Collect> collectList;
    private List<Variable> variableList;

    public Class(String className, String classContext) {
        this.className = className;
        this.classContext = classContext;
        this.executeList = new ArrayList<>();
        this.collectList = new ArrayList<>();
        this.variableList = new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Class aClass = (Class) o;

        if (!Objects.equals(classContext, aClass.classContext)) return false;
        if (!Objects.equals(className, aClass.className)) return false;
        if (!Objects.equals(executeList, aClass.executeList)) return false;
        if (!Objects.equals(collectList, aClass.collectList)) return false;
        return Objects.equals(variableList, aClass.variableList);
    }

    @Override
    public int hashCode() {
        int result = classContext != null ? classContext.hashCode() : 0;
        result = 31 * result + (className != null ? className.hashCode() : 0);
        result = 31 * result + (executeList != null ? executeList.hashCode() : 0);
        result = 31 * result + (collectList != null ? collectList.hashCode() : 0);
        result = 31 * result + (variableList != null ? variableList.hashCode() : 0);
        return result;
    }

    public List<Execute> getExecuteList() {
        return executeList;
    }

    public void setExecuteList(List<Execute> executeList) {
        this.executeList = executeList;
    }

    public List<Collect> getCollectList() {
        return collectList;
    }

    public void setCollectList(List<Collect> collectList) {
        this.collectList = collectList;
    }

    public List<Variable> getVariableList() {
        return variableList;
    }

    public void setVariableList(List<Variable> variableList) {
        this.variableList = variableList;
    }

    public String getClassContext() {
        return classContext;
    }

    public void setClassContext(String classContext) {
        this.classContext = classContext;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void addExecute(Execute execute) {
        this.executeList.add(execute);
    }

    public void addCollect(Collect collect) {
        this.collectList.add(collect);
    }

    public void addVariable(Variable variable) {
        this.variableList.add(variable);
    }
}
