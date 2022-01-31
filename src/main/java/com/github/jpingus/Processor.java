package com.github.jpingus;

import com.github.jpingus.model.ProcessTrace;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Processor {
    private static final Log LOGGER = LogFactory.getLog(Processor.class);
    private static final Pattern THIS_FIELD_PATTERN = Pattern.compile("this\\.(\\w+)");

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
        if (aggregatorContext.getPackageStarts() == null) {
            aggregatorContext.setPackageStarts(
                    Collections.singletonList(o.getClass().getPackage().getName()));
        }
        aggregatorContext.preProcess(o);
        process(prefix, o, aggregatorContext, executors);
        executors.values().stream()
                .flatMap(List::stream)
                .filter(Processor::notExecuted)
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
            if (!StringFunctions.isEmpty(analysed.getClassContext())) {
                localContext.setProcessTrace(localContext.getProcessTrace().traceContext(analysed.getClassContext()));
                executeContexts.values().stream()
                        .flatMap(Collection::stream)
                        .filter(Processor::notExecuted)
                        .filter(e -> e.formula.contains(analysed.getClassContext() + "."))
                        .forEach(e -> execute(e, localContext));
                localContext.cleanContext(analysed.getClassContext());
            }
            int i = 0;
            String key;
            switch (analysed.getClassType()) {
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
            analysed.getExecutes()
                    .forEach((field, executeList) -> executeList.forEach(execute -> {
                        if (applicable(o, execute.getWhen(), localContext)) {
                            addExecuteContext(executeContexts, prefix, field, execute.getJexl(), localContext);
                        }
                    }));
            for (String field : analysed.getVariables().keySet()) {
                localContext.addVariable(analysed.getVariables().get(field), get(o, field, localContext));
            }
            //First potentially seek deeper.
            for (String field : analysed.getOtherFields()) {
                process(prefix + "." + field, get(o, field, localContext), localContext,
                        executeContexts);
            }
            //Collect the fields
            for (String field : analysed.getCollects().keySet()) {
                String add = prefix + "." + field;
                Optional.ofNullable(executeContexts.get(add))
                        .orElse(Collections.emptyList()).stream().filter(Processor::notExecuted)
                        .forEach(e -> execute(e, localContext));
                if (!isNull(o, field, localContext)) {
                    for (com.github.jpingus.model.Collect collect : analysed.getCollects().get(field)) {
                        if (applicable(o, collect.getWhen(), localContext)) {
                            localContext.collect(evaluate(o, collect.getTo(), localContext), add);
                        }
                    }
                }
            }
            if (analysed.getClassCollects() != null) {
                for (com.github.jpingus.model.Collect collect : analysed.getClassCollects()) {
                    String formula = "(" + executeFieldsFromFormula(prefix, collect.getWhat(), executeContexts, localContext) + ")";

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

    private static boolean notExecuted(ExecuteContext executeContext) {
        return !executeContext.executed;
    }

    private static String executeFieldsFromFormula(String prefix, String formula, Map<String, List<ExecuteContext>> executeContexts, AggregatorContext localContext) {
        StringBuilder sb = new StringBuilder();
        Matcher m = THIS_FIELD_PATTERN.matcher(formula);
        int lastIndex = 0;
        while (m.find()) {
            sb.append(formula, lastIndex, m.start());
            String field = prefix + '.' + m.group(1);
            sb.append(field);
            Optional.ofNullable(executeContexts.get(field))
                    .orElse(new ArrayList<>()).stream().filter(Processor::notExecuted)
                    .forEach(e -> execute(e, localContext));
            lastIndex = m.end();
        }
        sb.append(formula.substring(lastIndex));
        return sb.toString();
    }

    private static void addExecuteContext(Map<String, List<ExecuteContext>> executeContexts, String prefix, String field, String jexl, AggregatorContext localContext) {
        String key = prefix + "." + field;
        List<ExecuteContext> list = executeContexts.getOrDefault(key, new ArrayList<>());
        String formula = executeFieldsFromFormula(prefix, jexl, executeContexts, localContext);
        list.add(new ExecuteContext(key, formula));
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
        if (StringFunctions.isEmpty(fieldName) || fieldName.contains("$"))
            return null;
        localContext.set("this", o);
        if (localContext.isDebug())
            LOGGER.debug("Get " + o.getClass() + " " + fieldName);
        return localContext.evaluate("this." + fieldName);

    }

    private static String evaluate(Object o, String value, AggregatorContext localContext) {
        if (StringFunctions.isEmpty(value))
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
        if (StringFunctions.isEmpty(when))
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


        ExecuteContext(String field, String formula) {
            this.field = field;
            this.formula = formula;
        }
    }


}
