import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Processor {
    private static final Log LOG = LogFactory.getLog(Processor.class);

    /**
     * Analyse recursively an object, collecting all the @Collects to aggregators, then executes the @Executes
     * Nulls will not be collected
     * You cannot assume wich order will be executed for the @Executes
     *
     * @param o
     * @return an Aggregator context to be used for programmatically accessing the aggregators
     * @throws Exception
     */
    public static AggregatorContext process(Object o) throws Exception {
        return process(o, new AggregatorContext());
    }

    /**
     * same as process.  Use this method if you want to register custom methods in a namespace to your context.
     *
     * @param o
     * @param aggregatorContext
     * @return
     * @throws Exception
     */
    public static AggregatorContext process(Object o, AggregatorContext aggregatorContext) throws Exception {
        List<ExecuteContext> executors = new ArrayList<>();
        process("o", o, aggregatorContext, executors, aggregatorContext);
        aggregatorContext.set("o", o);
        for (ExecuteContext executeContext : executors) {
            String expression = executeContext.field + "=" + executeContext.formula;
            LOG.debug(expression);
            aggregatorContext.evaluate(expression);
        }
        return aggregatorContext;
    }

    private static void process(String prefix, Object o, AggregatorContext aggregatorContext, List<ExecuteContext> executeContexts, JexlContext localContext)
            throws Exception {
        if (o == null || o.getClass().isPrimitive())
            return;
        if (o.getClass().getPackage() != null && "java.lang".equals(o.getClass().getPackage().getName()))
            return;
        //Check if the object is a collection
        Class objectClass = o.getClass();
        if (objectClass.isArray()) {
            int length = Array.getLength(o);
            for (int i = 0; i < length; i++) {
                process(prefix + "[" + i + "]", Array.get(o, i), aggregatorContext, executeContexts,
                        localContext);
            }
        } else if (List.class.isAssignableFrom(objectClass)) {
            @SuppressWarnings("rawtypes")
            List l = (List) o;
            for (int i = 0; i < l.size(); i++) {
                process(prefix + "[" + i + "]", l.get(i), aggregatorContext, executeContexts, localContext);
            }
            //Not working ... why?
            //} else if (Map.class.isAssignableFrom(fieldClass)) {
        } else if (o instanceof Map) {
            @SuppressWarnings("rawtypes")
            Map m = (Map) o;
            String key = prefix.replaceAll("\\.", "_") + "_";
            int i = 0;
            for (Object mO : m.values()) {
                String setKey = key + (i++);
                localContext.set(setKey, mO);
                process(setKey, mO, aggregatorContext, executeContexts, localContext);
            }
        } else if (Iterable.class.isAssignableFrom(objectClass)) {
            @SuppressWarnings("rawtypes")
            Iterator it = ((Iterable) o).iterator();
            int i = 0;
            String key = prefix.replaceAll("\\.", "_") + "_";
            while (it.hasNext()) {
                String setKey = key + (i++);
                Object iO = it.next();
                localContext.set(setKey, iO);
                process(setKey, iO, aggregatorContext, executeContexts, localContext);
            }
        } else {
            //Check the fields they can be Collectors, Executors or simple fields to process
            for (Field f : o.getClass().getDeclaredFields()) {
                Execute executors[] = f.getDeclaredAnnotationsByType(Execute.class);
                Collect collects[] = f.getDeclaredAnnotationsByType(Collect.class);
                if (executors != null && collects != null && executors.length > 0 && collects.length > 0) {
                    throw new Error("Field " + f + " cannot be @Collect and @Execute at the same time");
                }
                if (executors != null && executors.length > 0) {
                    for (Execute execute : executors) {
                        if (applicable(o, execute.when())) {
                            executeContexts.add(new ExecuteContext(prefix , f.getName(), execute.value()));
                        }
                    }
                } else if (collects != null && collects.length > 0) {
                    if (!isNull(f, o)) {
                        String add = prefix + "." + f.getName();
                        for (Collect collect : collects) {
                            if (applicable(o, collect.when())) {
                                aggregatorContext.addFormula(evaluate(o, collect.value()), add);
                            }
                        }
                    }
                } else {
                    process(prefix + "." + f.getName(), f.get(o), aggregatorContext, executeContexts, localContext);
                }
            }
        }
    }

    private static String evaluate(Object o, String value) {
        if (value == null || "".equals("value")) return null;
        if (!value.startsWith("eval:")) {
            return value;
        }
        JexlContext localContext = new MapContext();
        localContext.set("this", o);
        JexlEngine jexl = new JexlBuilder().create();
        return jexl.createExpression(value.substring(5)).evaluate(localContext).toString();
    }

    private static boolean isNull(Field f, Object o) throws IllegalAccessException {
        boolean accessible = f.isAccessible();
        try {
            f.setAccessible(true);
            LOG.debug("Accessing " + f + " accessible:" + accessible);
            return f.get(o) == null;
        } finally {
            f.setAccessible(accessible);
        }
    }

    private static boolean applicable(Object o, String when) {
        if (when == null || "".equals(when)) return true;
        JexlContext localContext = new MapContext();
        localContext.set("this", o);
        JexlEngine jexl = new JexlBuilder().create();
        Boolean ret = new Boolean(jexl.createExpression(when).evaluate(localContext).toString());
        LOG.debug("applicable :" + when + " is " + ret);
        return ret;
    }

    private static class ExecuteContext {
        String field;
        String formula;

        public ExecuteContext(String parent, String field, String formula) {
            this.field = parent + "." + field;
            this.formula = formula.replaceAll("this\\.", parent + ".");
        }
    }
}