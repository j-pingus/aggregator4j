package com.github.jpingus;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Processor {
    private static final Log LOGGER = LogFactory.getLog(Processor.class);

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
        Analysed analysed = localContext.getAnalysed(objectClass);
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
                localContext.addVariable(analysed.variables.get(field), get(o, field));
            }
            //First potentially seek deeper.
            for (String field : analysed.otherFields) {
                process(prefix + "." + field, get(o, field), localContext,
                        executeContexts);
            }
            //Collect the fields
            for (String field : analysed.collects.keySet()) {
                Object fieldValue = get(o,field);
                if (fieldValue != null) {
                    String add = prefix + "." + field;
                    for (Analysed.Collect collect : analysed.collects.get(field)) {
                        if (applicable(o, collect.when, localContext)) {
                            final String aggregatorName=evaluate(o, collect.to, localContext);
                            //localContext.collect(aggregatorName, add);
                            localContext.collectNg(aggregatorName, fieldValue);
                        }
                    }
                }
            }
            //Execute the fields
            for (String field : analysed.executes.keySet()) {
                for (Analysed.Execute execute : analysed.executes.get(field)) {
                    if (applicable(o, execute.when, localContext)) {
                        executeContexts.add(new ExecuteContext(prefix, field, execute.jexl));
                    }
                }
            }
            if (analysed.classCollects != null) {
                for (Analysed.Collect collect : analysed.classCollects) {
                    String formula = "(" + collect.what.replaceAll("this\\.", prefix + ".") + ") ";
                    if (applicable(o, collect.when, localContext) && !isNull(formula, localContext)) {
                        localContext.collect(evaluate(o, collect.to, localContext),
                                formula);
                    }
                }
            }
        } finally {
            if (analysed.classContext != null)
                localContext.endContext(analysed.classContext);
        }
    }

    private static boolean isNull(String formula, AggregatorContext localContext) {
        return localContext.evaluate(formula) == null;
    }

    private static Object get(Object o, String fieldName) {
        if (fieldName == null || "".equals(fieldName) || fieldName.contains("$"))
            return null;
        try {
            Field f = o.getClass().getDeclaredField(fieldName);
            if(!f.isAccessible())
                f.setAccessible(true);
            return f.get(o);
        } catch (IllegalAccessException|NoSuchFieldException e) {
            LOGGER.warn("Field not found:"+fieldName);
            return null;
        }
    }

    private static String evaluate(Object o, String value, AggregatorContext localContext) {
        if (value == null || "".equals(value))
            return null;
        if (!value.startsWith("eval:")) {
            return value;
        }
        localContext.set("this", o);
        Object evaluated = localContext.evaluate(value.substring(5));
        return evaluated == null ? "null" : evaluated.toString();
    }

    private static boolean isNull(Object o, String fieldName) {
        return get(o, fieldName) == null;
    }

    private static boolean applicable(Object o, String when, AggregatorContext localContext) {
        if (when == null || "".equals(when))
            return true;
        localContext.set("this", o);
        Object evaluated = localContext.evaluate(when);
        Boolean ret = evaluated == null ? Boolean.FALSE : new Boolean(evaluated.toString());
        if (localContext.isDebug())
            LOGGER.debug("applicable :" + when + " is " + ret);
        return ret;
    }

    static class ExecuteContext {
        String field;
        String formula;
        boolean executed = false;

        public ExecuteContext(String parent, String field, String formula) {
            this.field = parent + "." + field;
            this.formula = formula.replaceAll("this\\.", parent + ".");
        }
    }


}