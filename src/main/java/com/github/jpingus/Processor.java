package com.github.jpingus;

import com.github.jpingus.model.ProcessTrace;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.util.*;

import static com.github.jpingus.StringFunctions.isEmpty;

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
        return process(o, o.getClass().getSimpleName(), AggregatorContext.builder().build());
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
        Map<String, List<ExecuteContext>> executors = new HashMap<>();
        aggregatorContext.set(prefix, o);
        if (isEmpty(aggregatorContext.getPackageStart())) {
            aggregatorContext.setPackageStart(o.getClass().getPackage().getName());
        }
        aggregatorContext.preProcess(o);
        process(prefix, o, aggregatorContext, executors);
        executors.values().stream()
                .flatMap(List::stream)
                .filter(e -> !e.executed)
                .forEach(e -> execute(e, aggregatorContext));
        aggregatorContext.postProcess(o);
        return aggregatorContext;
    }

    private static void process(String prefix, Object o, AggregatorContext localContext,
                                Map<String, List<ExecuteContext>> executeContexts) {
        if (o == null)
            return;
        @SuppressWarnings("rawtypes")
        Class objectClass = o.getClass();
        Analysed analysed = localContext.getAnalysed(objectClass);
        ProcessTrace current = localContext.getProcessTrace();
        try {
            if (!isEmpty(analysed.classContext)) {
                localContext.setProcessTrace(localContext.getProcessTrace().traceContext(analysed.classContext));
                executeContexts.values().stream()
                        .flatMap(Collection::stream)
                        .filter(e -> !e.executed)
                        .filter(e -> e.formula.contains(analysed.classContext + "."))
                        .forEach(e -> execute(e, localContext));
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
            //Prepare the field execution
            analysed.executes
                    .forEach((field, executeList) -> executeList.forEach(execute -> {
                        if (applicable(o, execute.getWhen(), localContext)) {
                            addExecuteContext(executeContexts, prefix, field, execute.getJexl());
                        }
                    }));
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
                String add = prefix + "." + field;
                Optional.ofNullable(executeContexts.get(add))
                        .orElse(Collections.emptyList())
                        .forEach(e -> execute(e, localContext));
                if (!isNull(o, field, localContext)) {
                    for (com.github.jpingus.model.Collect collect : analysed.collects.get(field)) {
                        if (applicable(o, collect.getWhen(), localContext)) {
                            localContext.collect(evaluate(o, collect.getTo(), localContext), add);
                        }
                    }
                }
            }
            if (analysed.classCollects != null) {
                for (com.github.jpingus.model.Collect collect : analysed.classCollects) {
                    String formula = "(" + collect.getWhat().replaceAll("this\\.", prefix + ".") + ") ";
                    executeContexts.forEach((field, executes) -> {
                        if (formula.contains(field)) {
                            executes.stream()
                                    .filter(e -> !e.executed)
                                    .forEach(e -> execute(e, localContext));
                        }
                    });
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

    private static void addExecuteContext(Map<String, List<ExecuteContext>> executeContexts, String prefix, String field, String jexl) {
        String key = prefix + "." + field;
        List<ExecuteContext> list = executeContexts.getOrDefault(key, new ArrayList<>());
        list.add(new ExecuteContext(prefix, field, jexl));
        executeContexts.put(prefix + "." + field, list);
    }

    private static void execute(ExecuteContext e, AggregatorContext localContext) {
        localContext.execute(e.field, e.formula);
        e.executed = true;
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

    static class ExecuteContext {
        String field;
        String formula;
        boolean executed = false;


        ExecuteContext(String parent, String field, String formula) {
            this.field = parent + "." + field;
            this.formula = formula.replaceAll("this\\.", parent + ".");
        }
    }


}