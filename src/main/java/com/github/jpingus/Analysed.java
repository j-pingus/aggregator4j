package com.github.jpingus;

import java.lang.reflect.Field;
import java.util.*;

class Analysed {
    CLASS_TYPE classType;
    String classContext;
    Collect classCollects[];
    Map<String, Collect[]> collects;
    Map<String, Execute[]> executes;
    Map<String, String> variables;
    List<String> otherFields;
    public Analysed(Class objectClass, Object object, AggregatorContext localContext) {
        Context cx = (Context) objectClass.getDeclaredAnnotation(Context.class);
        classContext = cx == null ? null : cx.value();
        classCollects = analyse((com.github.jpingus.Collect[]) objectClass.getDeclaredAnnotationsByType(com.github.jpingus.Collect.class));
        if (classCollects != null && classCollects.length == 0)
            classCollects = null;
        if (objectClass.isArray()) {
            classType = CLASS_TYPE.ARRAY;
        } else if (List.class.isAssignableFrom(objectClass)) {
            classType = CLASS_TYPE.LIST;
        } else if (object instanceof Map) {
            classType = CLASS_TYPE.MAP;
        } else if (Iterable.class.isAssignableFrom(objectClass)) {
            classType = CLASS_TYPE.ITERABLE;
        } else if (objectClass.isPrimitive()) {
            classType = CLASS_TYPE.IGNORABLE;
        } else if (objectClass.getPackage() != null
                && !objectClass.getPackage().getName().startsWith(localContext.getPackageStart())) {
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

    static List<Field> getFields(Class baseClass) {
        ArrayList<Field> ret = new ArrayList<>();
        while (baseClass != null && baseClass != Object.class) {
            Collections.addAll(ret, baseClass.getDeclaredFields());
            baseClass = baseClass.getSuperclass();
        }
        return ret;
    }

    private Execute[] analyse(com.github.jpingus.Execute[] executes) {
        Execute[] ret = new Execute[executes.length];
        for (int i = 0; i < executes.length; i++) {
            ret[i] = new Execute(executes[i]);
        }
        return ret;
    }

    private Collect[] analyse(com.github.jpingus.Collect[] collects) {
        Collect ret[] = new Collect[collects.length];
        for (int i = 0; i < collects.length; i++) {
            ret[i] = new Collect(collects[i]);
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

    public enum CLASS_TYPE {ARRAY, LIST, MAP, ITERABLE, IGNORABLE, PROCESSABLE}

    static class Execute {
        String jexl;
        String when;

        public Execute(com.github.jpingus.Execute execute) {
            this.jexl = execute.value();
            this.when = execute.when();
        }
    }

    static class Collect {
        String to;
        String what;
        String when;

        public Collect(com.github.jpingus.Collect collect) {
            this.to = collect.value();
            this.what = collect.what();
            this.when = collect.when();
        }
    }
}
