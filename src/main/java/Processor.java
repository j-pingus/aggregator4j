import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class Processor {
    public static AggregatorContext process(Object o)throws Exception{
        return process(o, new AggregatorContext());
    }
    public static AggregatorContext process(Object o,AggregatorContext aggregatorContext) throws Exception {
        Map<String, String> executors = new HashMap<>();
        process("o", o, aggregatorContext, executors, aggregatorContext);
        aggregatorContext.set("o", o);
        JexlEngine jexl = new JexlBuilder().create();
        for (String field : executors.keySet()) {
            String formula = executors.get(field);
            System.out.println(field + "=" + formula);
            jexl.createExpression(field + "=" + formula).evaluate(aggregatorContext);
        }
        return aggregatorContext;
    }

    private static void process(String prefix, Object o, AggregatorContext aggregatorContext, Map<String, String> executorsMap, JexlContext localContext)
            throws Exception {
        if (o == null || o.getClass().isPrimitive())
            return;
        if (o.getClass().getPackage() != null && "java.lang".equals(o.getClass().getPackage().getName()))
            return;
        for (Field f : o.getClass().getDeclaredFields()) {
            Execute executors[] = f.getDeclaredAnnotationsByType(Execute.class);
            Collect collects[] = f.getDeclaredAnnotationsByType(Collect.class);
            if (executors != null && executors.length > 0) {
                for (Execute execute : executors) {
                    if (applicable(o, execute.when())) {
                        executorsMap.put(prefix + "." + f.getName(), execute.value());
                    }
                }
            } else if (collects != null && collects.length > 0) {
                if (f.get(o) != null) {
                    String add = prefix + "." + f.getName();
                    for (Collect collect : collects) {
                        if (applicable(o, collect.when())) {
                            aggregatorContext.addFormula(collect.value(), add);
                        }
                    }
                }
            } else {
                Object fieldValue = f.get(o);
                if (fieldValue != null) {
                    Class fieldClass = f.getType();
                    if (fieldClass.isArray()) {
                        int length = Array.getLength(fieldValue);
                        for (int i = 0; i < length; i++) {
                            process(prefix + "." + f.getName() + "[" + i + "]", Array.get(fieldValue, i), aggregatorContext, executorsMap,
                                    localContext);
                        }
                    } else if (List.class.isAssignableFrom(fieldClass)) {
                        @SuppressWarnings("rawtypes")
                        List l = (List) fieldValue;
                        for (int i = 0; i < l.size(); i++) {
                            process(prefix + "." + f.getName() + "[" + i + "]", l.get(i), aggregatorContext, executorsMap, localContext);
                        }
                        //Not working ... why?
                        //} else if (Map.class.isAssignableFrom(fieldClass)) {
                    } else if (fieldValue instanceof Map) {
                        @SuppressWarnings("rawtypes")
                        Map m = (Map) fieldValue;
                        String key = prefix.replaceAll("\\.", "_") + "_" + f.getName() + "_";
                        int i = 0;
                        for (Object mO : m.values()) {
                            String setKey = key + (i++);
                            localContext.set(setKey, mO);
                            process(setKey, mO, aggregatorContext, executorsMap, localContext);
                        }
                    } else if (Iterable.class.isAssignableFrom(fieldClass)) {
                        @SuppressWarnings("rawtypes")
                        Iterator it = ((Iterable) fieldValue).iterator();
                        int i = 0;
                        String key = prefix.replaceAll("\\.", "_") + "_" + f.getName() + "_";
                        while (it.hasNext()) {
                            String setKey = key + (i++);
                            Object iO = it.next();
                            localContext.set(setKey, iO);
                            process(setKey, iO, aggregatorContext, executorsMap, localContext);
                        }
                    } else {
                        process(prefix + "." + f.getName(), f.get(o), aggregatorContext, executorsMap, localContext);
                    }
                }
            }
        }
    }


    private static boolean applicable(Object o, String when) {
        if (when == null || "".equals(when)) return true;
        JexlContext localContext = new MapContext();
        localContext.set("o", o);
        JexlEngine jexl = new JexlBuilder().create();
        Boolean ret = new Boolean(jexl.createExpression(when.replaceAll("this\\.", "o.")).evaluate(localContext).toString());
        return ret;
    }

}