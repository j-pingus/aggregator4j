package com.github.jpingus;

import com.github.jpingus.model.Collect;
import com.github.jpingus.model.Execute;

import java.lang.reflect.Field;
import java.util.*;

class Analysed {
    private CLASS_TYPE classType;
    private String classContext;
    private List<Collect> classCollects;
    private Map<String, List<Collect>> collects;
    private Map<String, List<Execute>> executes;
    private Map<String, String> variables;
    private List<String> otherFields;
    Analysed(Class objectClass, IgnorableClassDetector ignorableClassDetector) {
        Context cx = (Context) objectClass.getDeclaredAnnotation(Context.class);
        classContext = cx == null ? null : cx.value();
        classCollects = analyse(null,
                (com.github.jpingus.Collect[]) objectClass.getDeclaredAnnotationsByType(
                        com.github.jpingus.Collect.class));
        if (classCollects.size() == 0)
            classCollects = null;
        if (objectClass.isArray()) {
            classType = CLASS_TYPE.ARRAY;
        } else if (List.class.isAssignableFrom(objectClass)) {
            classType = CLASS_TYPE.LIST;
        } else if (Map.class.isAssignableFrom(objectClass)) {
            classType = CLASS_TYPE.MAP;
        } else if (Iterable.class.isAssignableFrom(objectClass)) {
            classType = CLASS_TYPE.ITERABLE;
        } else if (objectClass.isPrimitive()) {
            classType = CLASS_TYPE.IGNORABLE;
        } else if (ignorableClassDetector.canIgnore(objectClass)) {
            classType = CLASS_TYPE.IGNORABLE;
        } else {
            classType = CLASS_TYPE.PROCESSABLE;
            variables = new HashMap<>();
            executes = new HashMap<>();
            collects = new HashMap<>();
            otherFields = new ArrayList<>();
            for (Field f : getFields(objectClass)) {

                Variable variable = f.getDeclaredAnnotation(Variable.class);
                if (variable != null) {
                    variables.put(f.getName(), variable.value());
                }
                com.github.jpingus.Execute[] executors = f.getDeclaredAnnotationsByType(
                        com.github.jpingus.Execute.class);
                com.github.jpingus.Collect[] collectors = f.getDeclaredAnnotationsByType(
                        com.github.jpingus.Collect.class);
                String fieldName = sanitizeFieldName(f.getName());
                boolean executorsExist = executors != null && executors.length > 0;
                boolean collectorsExist = collectors != null && collectors.length > 0;
                if (executorsExist) {
                    executes.put(fieldName, analyse(fieldName, executors));
                }
                if (collectorsExist) {
                    collects.put(fieldName, analyse(fieldName, collectors));
                }
                if (!executorsExist && !collectorsExist) {
                    otherFields.add(fieldName);
                }
            }
        }
    }
    Analysed() {
        classCollects = new ArrayList<>();
        otherFields = new ArrayList<>();
        collects = new HashMap<>();
        executes = new HashMap<>();
        variables = new HashMap<>();
    }

    private static List<Field> getFields(Class baseClass) {
        ArrayList<Field> ret = new ArrayList<>();
        while (baseClass != null && baseClass != Object.class) {
            Collections.addAll(ret, baseClass.getDeclaredFields());
            baseClass = baseClass.getSuperclass();
        }
        return ret;
    }

    public String getClassContext() {
        return classContext;
    }

    public void setClassContext(String classContext) {
        this.classContext = classContext;
    }

    public List<Collect> getClassCollects() {
        return classCollects;
    }

    public void setClassCollects(List<Collect> classCollects) {
        this.classCollects = classCollects;
    }

    public Map<String, List<Collect>> getCollects() {
        return collects;
    }

    public void setCollects(Map<String, List<Collect>> collects) {
        this.collects = collects;
    }

    public Map<String, List<Execute>> getExecutes() {
        return executes;
    }

    public void setExecutes(Map<String, List<Execute>> executes) {
        this.executes = executes;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    public List<String> getOtherFields() {
        return otherFields;
    }

    public void setOtherFields(List<String> otherFields) {
        this.otherFields = otherFields;
    }

    public CLASS_TYPE getClassType() {
        return classType;
    }

    public void setClassType(CLASS_TYPE classType) {
        this.classType = classType;
    }

    @Override
    public String toString() {
        return "Analysed{" +
                "classType=" + classType +
                ", classContext='" + classContext + '\'' +
                ", classCollects=" + classCollects +
                ", collects=" + collects +
                ", executes=" + executes +
                ", variables=" + variables +
                ", otherFields=" + otherFields +
                '}';
    }

    private List<Execute> analyse(String field, com.github.jpingus.Execute[] executes) {
        List<Execute> executeList = new ArrayList<>();
        for (com.github.jpingus.Execute execute : executes) {
            executeList.add(new Execute(field, execute.value(), execute.when()));
        }
        return executeList;
    }

    private List<Collect> analyse(String field, com.github.jpingus.Collect[] collects) {
        List<Collect> ret = new ArrayList<>();
        for (com.github.jpingus.Collect collect : collects) {
            ret.add(new Collect(field, collect.what(), collect.value(), collect.when()));
        }
        return ret;
    }

    private String sanitizeFieldName(String name) {
        //TODO: also do for or and eq ne lt gt le ge div mod not null true false new var return
        if ("size".equals(name)) {
            return "'" + name + "'";
        }
        return name;
    }

    void addExecute(String field, String jexl, String when) {
        Execute ex = new Execute(field, jexl, when);
        field = sanitizeFieldName(field);
        if (!executes.containsKey(field)) {
            executes.put(field, new ArrayList<>());
        }
        executes.get(field).add(ex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Analysed analysed = (Analysed) o;

        if (classType != analysed.classType)
            return false;
        if (classContext != null ? !classContext.equals(analysed.classContext) : analysed.classContext != null)
            return false;
        if (classCollects != null ? !classCollects.equals(analysed.classCollects) : analysed.classCollects != null)
            return false;
        if (collects != null ? !collects.equals(analysed.collects) : analysed.collects != null)
            return false;
        if (executes != null ? !executes.equals(analysed.executes) : analysed.executes != null)
            return false;
        if (variables != null ? !variables.equals(analysed.variables) : analysed.variables != null)
            return false;
        return otherFields != null ? otherFields.equals(analysed.otherFields) : analysed.otherFields == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(classType, classContext, classCollects, collects, executes, variables, otherFields);
    }

    void addCollectField(String field, String to, String when) {
        field = sanitizeFieldName(field);
        Collect collect = new Collect(field, null, to, when);
        if (!collects.containsKey(field)) {
            collects.put(field, new ArrayList<>());
        }
        collects.get(field).add(collect);
    }

    void addCollectClass(String what, String to, String when) {
        classCollects.add(new Collect(null, what, to, when));
    }

    void addVariable(String field, String variable) {
        field = sanitizeFieldName(field);
        variables.put(field, variable);
    }

    void addOtherFields(Class clazz) {
        for (Field f : getFields(clazz)) {
            String fieldName = sanitizeFieldName(f.getName());
            if (!executes.containsKey(fieldName) && !collects.containsKey(fieldName))
                otherFields.add(fieldName);
        }
    }

    void prune() {
        if (classCollects.isEmpty())
            classCollects = null;
    }

    public enum CLASS_TYPE {ARRAY, LIST, MAP, ITERABLE, IGNORABLE, PROCESSABLE}

}
