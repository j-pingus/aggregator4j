package com.github.jpingus;

import java.lang.reflect.Field;
import java.util.*;

class Analysed {
    CLASS_TYPE classType;
    String classContext;
    List<Collect> classCollects;
    Map<String, List<Collect>> collects;
    Map<String, List<Execute>> executes;
    Map<String, String> variables;
    List<String> otherFields;

    public Analysed(Class objectClass, String packageStart) {
        Context cx = (Context) objectClass.getDeclaredAnnotation(Context.class);
        classContext = cx == null ? null : cx.value();
        classCollects = analyse((com.github.jpingus.Collect[]) objectClass.getDeclaredAnnotationsByType(com.github.jpingus.Collect.class));
        if (classCollects != null && classCollects.size() == 0)
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
        } else if (objectClass.getPackage() != null
                && !objectClass.getPackage().getName().startsWith(packageStart)) {
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
                com.github.jpingus.Execute executors[] = f.getDeclaredAnnotationsByType(com.github.jpingus.Execute.class);
                com.github.jpingus.Collect collectors[] = f.getDeclaredAnnotationsByType(com.github.jpingus.Collect.class);
                String fieldName = sanitizeFieldName(f.getName());
                if (executors != null && executors.length > 0) {
                    executes.put(fieldName, analyse(executors));
                } else if (collectors != null && collectors.length > 0) {
                    collects.put(fieldName, analyse(collectors));
                } else {
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


    static List<Field> getFields(Class baseClass) {
        ArrayList<Field> ret = new ArrayList<>();
        while (baseClass != null && baseClass != Object.class) {
            Collections.addAll(ret, baseClass.getDeclaredFields());
            baseClass = baseClass.getSuperclass();
        }
        return ret;
    }

    private List<Execute> analyse(com.github.jpingus.Execute[] executes) {
        List<Execute> executeList = new ArrayList<>();
        for (com.github.jpingus.Execute execute : executes) {
            executeList.add(new Execute(execute.value(), execute.when()));
        }
        return executeList;
    }

    private List<Collect> analyse(com.github.jpingus.Collect[] collects) {
        List<Collect> ret = new ArrayList<>();
        for (com.github.jpingus.Collect collect : collects) {
            ret.add(new Collect(collect.value(), collect.what(), collect.when()));
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
        Execute ex = new Execute(jexl, when);
        field = sanitizeFieldName(field);
        if (!executes.containsKey(field)) {
            executes.put(field, new ArrayList<>());
        }
        executes.get(field).add(ex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Analysed analysed = (Analysed) o;

        if (classType != analysed.classType) return false;
        if (classContext != null ? !classContext.equals(analysed.classContext) : analysed.classContext != null)
            return false;
        if (classCollects != null ? !classCollects.equals(analysed.classCollects) : analysed.classCollects != null)
            return false;
        if (collects != null ? !collects.equals(analysed.collects) : analysed.collects != null) return false;
        if (executes != null ? !executes.equals(analysed.executes) : analysed.executes != null) return false;
        if (variables != null ? !variables.equals(analysed.variables) : analysed.variables != null) return false;
        return otherFields != null ? otherFields.equals(analysed.otherFields) : analysed.otherFields == null;
    }

    @Override
    public int hashCode() {
        int result = classType != null ? classType.hashCode() : 0;
        result = 31 * result + (classContext != null ? classContext.hashCode() : 0);
        result = 31 * result + (classCollects != null ? classCollects.hashCode() : 0);
        result = 31 * result + (collects != null ? collects.hashCode() : 0);
        result = 31 * result + (executes != null ? executes.hashCode() : 0);
        result = 31 * result + (variables != null ? variables.hashCode() : 0);
        result = 31 * result + (otherFields != null ? otherFields.hashCode() : 0);
        return result;
    }

    public void addCollectField(String field, String to, String when) {
        Collect collect = new Collect(to, "this", when);
        field = sanitizeFieldName(field);
        if (!collects.containsKey(field)) {
            collects.put(field, new ArrayList<>());
        }
        collects.get(field).add(collect);
    }

    public void addCollectClass(String what, String to, String when) {
        classCollects.add(new Collect(to, what, when));
    }

    public void addVariable(String field, String variable) {
        field = sanitizeFieldName(field);
        variables.put(field, variable);
    }

    public void addOtherFields(Class clazz) {
        for (Field f : getFields(clazz)) {
            String fieldName = sanitizeFieldName(f.getName());
            if (!executes.containsKey(fieldName) && !collects.containsKey(fieldName))
                otherFields.add(fieldName);
        }
    }

    public void prune() {
        if (classCollects.isEmpty()) classCollects = null;
    }

    public enum CLASS_TYPE {ARRAY, LIST, MAP, ITERABLE, IGNORABLE, PROCESSABLE}

    static class Execute {
        String jexl;
        String when;

        public Execute(String jexl, String when) {
            this.jexl = jexl;
            this.when = when;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Execute execute = (Execute) o;

            if (!jexl.equals(execute.jexl)) return false;
            return when != null ? when.equals(execute.when) : execute.when == null;
        }

        @Override
        public int hashCode() {
            int result = jexl.hashCode();
            result = 31 * result + (when != null ? when.hashCode() : 0);
            return result;
        }

    }

    static class Collect {
        String to;
        String what;
        String when;

        public Collect(String to, String what, String when) {
            this.to = to;
            this.what = what;
            this.when = when;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Collect collect = (Collect) o;

            if (!to.equals(collect.to)) return false;
            if (what != null ? !what.equals(collect.what) : collect.what != null) return false;
            return when != null ? when.equals(collect.when) : collect.when == null;
        }

        @Override
        public int hashCode() {
            int result = to.hashCode();
            result = 31 * result + (what != null ? what.hashCode() : 0);
            result = 31 * result + (when != null ? when.hashCode() : 0);
            return result;
        }


    }
}
