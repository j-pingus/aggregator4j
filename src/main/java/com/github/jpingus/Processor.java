package com.github.jpingus;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class Processor {
    private static final Log LOGGER = LogFactory.getLog(Processor.class);
    private static final Map<Class, Analysed> analysedCache = new HashMap<>();


    /**
     * Analyse recursively an object, collecting all the @Collects to aggregators,
     * then executes the @Executes Nulls will not be collected You cannot assume
     * wich order will be executed for the @Executes
     *
     * @param o the object to process
     * @return an Aggregator context to be used for programmatically accessing the
     * aggregators
     */
    public static AggregatorContext process(Object o) {
        return process(o, o.getClass().getSimpleName(), new AggregatorContext(true));
    }

    /**
     * same as process. Use this method if you want to register custom methods in a
     * namespace to your context.
     *
     * @param o                 the object to be processed
     * @param prefix            the prefix to use as a reference for o in the context
     * @param aggregatorContext a context to help processing the object
     * @return updated aggregator context
     */
    public static AggregatorContext process(Object o, String prefix, AggregatorContext aggregatorContext) {
        aggregatorContext.startProcess();
        try {
            List<ExecuteContext> executors = new ArrayList<>();
            aggregatorContext.set(prefix, o);
            if (aggregatorContext.getPackageStart() == null) {
                aggregatorContext.setPackageStart(o.getClass().getPackage().getName());
            }
            process(prefix, o, aggregatorContext, executors);
            for (ExecuteContext executeContext : executors) {
                if (executeContext.executed == false) {
                    aggregatorContext.execute(executeContext.field, executeContext.formula);
                }
            }
            return aggregatorContext;
        } finally {
            aggregatorContext.endProcess();
        }
    }

    private static void process(String prefix, Object o, AggregatorContext localContext,
                                List<ExecuteContext> executeContexts) {
        if (o == null)
            return;
        @SuppressWarnings("rawtypes")
        Class objectClass = o.getClass();
        Analysed analysed = analyse(objectClass, o, localContext);
        try {
            if (analysed.classContext != null) {
                localContext.startContext(analysed.classContext);
                for (ExecuteContext executeContext : executeContexts) {
                    if (executeContext.formula.contains(analysed.classContext + ".") && executeContext.executed == false) {
                        localContext.execute(executeContext.field, executeContext.formula);
                        executeContext.executed = true;
                    }
                }
                localContext.cleanContext(analysed.classContext);
            }
            int i = 0;
            String key;
            switch (analysed.classType) {
                case MAP:
                    Map m = (Map) o;
                    key = prefix.replaceAll("\\.", "_") + "_";
                    for (Object mO : m.values()) {
                        String setKey = key + (i++);
                        localContext.set(setKey, mO);
                        process(setKey, mO, localContext, executeContexts);
                    }
                    return;
                case ARRAY:
                    int length = Array.getLength(o);
                    for (i = 0; i < length; i++) {
                        process(prefix + "[" + i + "]", Array.get(o, i), localContext, executeContexts);
                    }
                    return;
                case LIST:
                    @SuppressWarnings("rawtypes")
                    List l = (List) o;
                    for (i = 0; i < l.size(); i++) {
                        process(prefix + "[" + i + "]", l.get(i), localContext, executeContexts);
                    }
                    return;
                case ITERABLE:
                    @SuppressWarnings("rawtypes")
                    Iterator it = ((Iterable) o).iterator();
                    key = prefix.replaceAll("\\.", "_") + "_";
                    while (it.hasNext()) {
                        String setKey = key + (i++);
                        Object iO = it.next();
                        localContext.set(setKey, iO);
                        process(setKey, iO, localContext, executeContexts);
                    }
                    return;
                case IGNORABLE:
                    return;

            }
            for (String field : analysed.variables.keySet()) {
                localContext.addVariable(analysed.variables.get(field), get(o, field, localContext));
            }
            //First potentially seek deeper.
            for (String field : analysed.otherFields) {
                process(prefix + "." + field, get(o, field, localContext), localContext,
                        executeContexts);
            }
            //Collect the fields
            for (String field : analysed.collects.keySet()) {
                if (!isNull(o, field, localContext)) {
                    String add = prefix + "." + field;
                    for (AnalysedCollect collect : analysed.collects.get(field)) {
                        if (applicable(o, collect.when, localContext)) {
                            localContext.collect(evaluate(o, collect.to, localContext), add);
                        }
                    }
                }
            }
            //Execute the fields
            for (String field : analysed.executes.keySet()) {
                for (AnalysedExecute execute : analysed.executes.get(field)) {
                    if (applicable(o, execute.when, localContext)) {
                        executeContexts.add(new ExecuteContext(prefix, field, execute.jexl));
                    }
                }
            }
            if (analysed.classCollects != null) {
                for (AnalysedCollect collect : analysed.classCollects) {
                    if (applicable(o, collect.when, localContext)) {
                        localContext.collect(evaluate(o, collect.to, localContext),
                                "(" + collect.what.replaceAll("this\\.", prefix + ".") + ") ");
                    }
                }
            }
        } finally {
            if (analysed.classContext != null)
                localContext.endContext(analysed.classContext);
        }
    }

    private static synchronized Analysed analyse(Class objectClass, Object o, AggregatorContext localContext) {
        if (!analysedCache.containsKey(objectClass)) {
            Analysed analysed = new Analysed(objectClass, o, localContext);
            analysedCache.put(objectClass, analysed);
        }
        return analysedCache.get(objectClass);
    }


    private static Object get(Object o, String fieldName, AggregatorContext localContext) {
        if (fieldName == null || "".equals(fieldName) || fieldName.contains("$"))
            return null;
        localContext.set("this", o);
        if (localContext.isDebug())
            LOGGER.debug("Get " + o.getClass() + " " + fieldName);
        return localContext.evaluate("this." + fieldName);

    }

    private static String evaluate(Object o, String value, AggregatorContext localContext) {
        if (value == null || "".equals(value))
            return null;
        if (!value.startsWith("eval:")) {
            return value;
        }
        localContext.set("this", o);
        return localContext.evaluate(value.substring(5)).toString();
    }

    private static boolean isNull(Object o, String fieldName, AggregatorContext localContext) {
        return get(o, fieldName, localContext) == null;
    }

    private static boolean applicable(Object o, String when, AggregatorContext localContext) {
        if (when == null || "".equals(when))
            return true;
        localContext.set("this", o);
        Boolean ret = new Boolean(localContext.evaluate(when).toString());
        if (localContext.isDebug())
            LOGGER.debug("applicable :" + when + " is " + ret);
        return ret;
    }

    private static class ExecuteContext {
        String field;
        String formula;
        boolean executed = false;

        public ExecuteContext(String parent, String field, String formula) {
            this.field = parent + "." + field;
            this.formula = formula.replaceAll("this\\.", parent + ".");
        }
    }

    static class AnalysedCollect {
        String to;
        String what;
        String when;

        public AnalysedCollect(Collect collect) {
            this.to = collect.value();
            this.what = collect.what();
            this.when = collect.when();
        }
    }

    static class AnalysedExecute {
        String jexl;
        String when;

        public AnalysedExecute(Execute execute) {
            this.jexl = execute.value();
            this.when = execute.when();
        }
    }

    static class Analysed {
        CLASS_TYPE classType;
        String classContext;
        AnalysedCollect classCollects[];
        Map<String, AnalysedCollect[]> collects;
        Map<String, AnalysedExecute[]> executes;
        Map<String, String> variables;
        List<String> otherFields;

        public Analysed(Class objectClass, Object object, AggregatorContext localContext) {
            Context cx = (Context) objectClass.getDeclaredAnnotation(Context.class);
            classContext = cx == null ? null : cx.value();
            classCollects = analyse((Collect[]) objectClass.getDeclaredAnnotationsByType(Collect.class));
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
                    Execute executors[] = f.getDeclaredAnnotationsByType(Execute.class);
                    Collect collectors[] = f.getDeclaredAnnotationsByType(Collect.class);
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

        private AnalysedExecute[] analyse(Execute[] executes) {
            AnalysedExecute[] ret = new AnalysedExecute[executes.length];
            for (int i = 0; i < executes.length; i++) {
                ret[i] = new AnalysedExecute(executes[i]);
            }
            return ret;
        }

        private AnalysedCollect[] analyse(Collect[] collects) {
            AnalysedCollect ret[] = new AnalysedCollect[collects.length];
            for (int i = 0; i < collects.length; i++) {
                ret[i] = new AnalysedCollect(collects[i]);
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
    }

}