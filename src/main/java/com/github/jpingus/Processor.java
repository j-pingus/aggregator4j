package com.github.jpingus;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.jpingus.model.ProcessTrace;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static com.github.jpingus.StringFunctions.isEmpty;
public class Processor {
    private static final Log LOGGER = LogFactory.getLog(Processor.class);

    static class ExecuteContext {
        String field;
        String formula;
        boolean executed = false;


        ExecuteContext(String parent, String field, String formula) {
            this.field = parent + "." + field;
            this.formula = formula.replaceAll("this\\.", parent + ".");
        }
    }

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
        List<ExecuteContext> executors = new ArrayList<>();
        aggregatorContext.set(prefix, o);
        if (isEmpty(aggregatorContext.getPackageStart())) {
            aggregatorContext.setPackageStart(o.getClass().getPackage().getName());
        }
        process(prefix, o, aggregatorContext, executors);
        for (ExecuteContext executeContext : executors) {
            if (!executeContext.executed) {
                aggregatorContext.execute(executeContext.field, executeContext.formula);
            }
        }
        return aggregatorContext;
    }

    private static void process(String prefix, Object o, AggregatorContext localContext,
                                List<ExecuteContext> executeContexts) {
        if (o == null)
            return;
        @SuppressWarnings("rawtypes")
        Class objectClass = o.getClass();
        Analysed analysed = localContext.getAnalysed(objectClass);
        ProcessTrace current = localContext.getProcessTrace();
        try {
            if (!isEmpty(analysed.classContext)) {
                localContext.setProcessTrace(localContext.getProcessTrace().addContext(analysed.classContext));
                for (ExecuteContext executeContext : executeContexts) {
                    if (executeContext.formula.contains(analysed.classContext + ".") && !executeContext.executed) {
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
                    for (com.github.jpingus.model.Collect collect : analysed.collects.get(field)) {
                        if (applicable(o, collect.getWhen(), localContext)) {
                            localContext.collect(evaluate(o, collect.getTo(), localContext), add);
                        }
                    }
                }
            }
            //Execute the fields
            for (String field : analysed.executes.keySet()) {
                for (com.github.jpingus.model.Execute execute : analysed.executes.get(field)) {
                    if (applicable(o, execute.getWhen(), localContext)) {
                        executeContexts.add(new ExecuteContext(prefix, field, execute.getJexl()));
                    }
                }
            }
            if (analysed.classCollects != null) {
                for (com.github.jpingus.model.Collect collect : analysed.classCollects) {
                    String formula = "(" + collect.getWhat().replaceAll("this\\.", prefix + ".") + ") ";
                    if (applicable(o, collect.getWhen(), localContext) && !isNull(formula, localContext)) {
                        localContext.collect(evaluate(o, collect.getTo(), localContext),
                                formula);
                    }
                }
            }
        } finally {
            localContext.setProcessTrace(current);
        }
    }


    private static boolean isNull(String formula, AggregatorContext localContext) {
        return localContext.evaluate(formula) == null;
    }

    private static Object get(Object o, String fieldName, AggregatorContext localContext) {
        if (isEmpty(fieldName) || fieldName.contains("$"))
            return null;
        localContext.set("this", o);
        if (localContext.isDebug())
            LOGGER.debug("Get " + o.getClass() + " " + fieldName);
        return localContext.evaluate("this." + fieldName);

    }

    private static String evaluate(Object o, String value, AggregatorContext localContext) {
        if (isEmpty(value))
            return null;
        if (!value.startsWith("eval:")) {
            return value;
        }
        localContext.set("this", o);
        Object evaluated = localContext.evaluate(value.substring(5));
        return evaluated == null ? "null" : evaluated.toString();
    }

    private static boolean isNull(Object o, String fieldName, AggregatorContext localContext) {
        return get(o, fieldName, localContext) == null;
    }

    private static boolean applicable(Object o, String when, AggregatorContext localContext) {
        if (isEmpty(when))
            return true;
        localContext.set("this", o);
        Object evaluated = localContext.evaluate(when);
        Boolean ret = evaluated == null ? Boolean.FALSE : Boolean.valueOf(evaluated.toString());
        if (localContext.isDebug())
            LOGGER.debug("applicable :" + when + " is " + ret);
        return ret;
    }


}