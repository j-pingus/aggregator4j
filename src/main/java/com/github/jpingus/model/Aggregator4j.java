package com.github.jpingus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Aggregator4j {
    private String analysedPackage;
    private String processing;
    private List<Function> functionList;
    private List<Class> classList;

    public Aggregator4j() {
        this.functionList = new ArrayList<>();
        this.classList = new ArrayList<>();
    }

    public String getProcessing() {
        return processing;
    }

    public void setProcessing(String processing) {
        this.processing = processing;
    }

    public boolean addFunction(Function function) {
        return functionList.add(function);
    }

    public boolean addClass(Class aClass) {
        return classList.add(aClass);
    }

    public List<Function> getFunctionList() {
        return functionList;
    }

    public void setFunctionList(List<Function> functionList) {
        this.functionList = functionList;
    }

    public List<Class> getClassList() {
        return classList;
    }

    public void setClassList(List<Class> classList) {
        this.classList = classList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Aggregator4j that = (Aggregator4j) o;

        if (!functionList.equals(that.functionList)) return false;
        if (!classList.equals(that.classList)) return false;
        return Objects.equals(analysedPackage, that.analysedPackage);
    }

    @Override
    public int hashCode() {
        int result = functionList.hashCode();
        result = 31 * result + classList.hashCode();
        result = 31 * result + (analysedPackage != null ? analysedPackage.hashCode() : 0);
        return result;
    }

    public String getAnalysedPackage() {
        return analysedPackage;
    }

    public void setAnalysedPackage(String analysedPackage) {
        this.analysedPackage = analysedPackage;
    }
}
