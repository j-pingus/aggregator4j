package com.github.jpingus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AggregatorConfiguration {
    private List<String> analysedPackages;
    private List<String> processings;
    private List<Function> functionList;
    private List<Class> classList;

    public AggregatorConfiguration() {
        this.functionList = new ArrayList<>();
        this.classList = new ArrayList<>();
    }

    public List<String> getAnalysedPackages() {
        return analysedPackages;
    }

    public void setAnalysedPackages(List<String> analysedPackages) {
        this.analysedPackages = analysedPackages;
    }

    public List<String> getProcessings() {
        return processings;
    }

    public void setProcessings(List<String> processings) {
        this.processings = processings;
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

    public void setProcessing(String processing) {
        this.processings = new ArrayList<>();
        this.processings.add(processing);
    }

    public boolean addFunction(Function function) {
        return functionList.add(function);
    }

    public boolean addClass(Class aClass) {
        return classList.add(aClass);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        AggregatorConfiguration that = (AggregatorConfiguration) o;

        if (!functionList.equals(that.functionList)) return false;
        if (!classList.equals(that.classList)) return false;
        return Objects.equals(analysedPackages, that.analysedPackages);
    }

    @Override
    public int hashCode() {
        int result = functionList.hashCode();
        result = 31 * result + classList.hashCode();
        result = 31 * result + (analysedPackages != null ? analysedPackages.hashCode() : 0);
        return result;
    }

    public void setAnalysedPackage(String analysedPackage) {
        this.analysedPackages = new ArrayList<>();
        this.analysedPackages.add(analysedPackage);
    }
}
