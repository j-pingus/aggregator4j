import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Processor {
    public static void process(Object o) throws Exception {
        Map<String, Formula> aggregators = new HashMap<String, Formula>();
        JexlContext localContext = new MapContext();
        process("o", o, aggregators, localContext);
        localContext.set("o", o);
        JexlEngine jexl = new JexlBuilder().create();
        for (String aggregator : aggregators.keySet()) {
            Formula f = aggregators.get(aggregator);
            System.out.println(aggregator + ":" + f.field + "=" + f.formula);
            if (f.field == null)
                System.out.println("-->skipped, no destination field");
            else
                jexl.createExpression(f.field + "=" + f.formula).evaluate(localContext);
        }
    }

    private static void process(String prefix, Object o, Map<String, Formula> aggregators, JexlContext localContext)
            throws Exception {
        if (o == null || o.getClass().isPrimitive())
            return;
        if (o.getClass().getPackage() != null && "java.lang".equals(o.getClass().getPackage().getName()))
            return;
        for (Field f : o.getClass().getDeclaredFields()) {
            Aggregator a = f.getDeclaredAnnotation(Aggregator.class);
            Collect collects[] = f.getDeclaredAnnotationsByType(Collect.class);
            if (a != null) {
                if (applicable(o, a.when())) {
                    if (!aggregators.containsKey(a.value())) {
                        aggregators.put(a.value(), new Formula(null, null));
                    }
                    Formula formula = aggregators.get(a.value());
                    formula.field = prefix + "." + f.getName();
                }
            } else if (collects != null && collects.length > 0) {
                if (f.get(o) != null) {
                    String add = prefix + "." + f.getName();
                    for (Collect collect : collects) {
                        if (applicable(o, collect.when())) {
                            Formula formula = null;
                            if (!aggregators.containsKey(collect.value())) {
                                aggregators.put(collect.value(), new Formula(null, null));
                            }
                            formula = aggregators.get(collect.value());
                            if (formula.formula == null) {
                                formula.formula = add;
                            } else {
                                formula.formula += "+" + add;
                            }
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
                            process(prefix + "." + f.getName() + "[" + i + "]", Array.get(fieldValue, i), aggregators,
                                    localContext);
                        }
                    } else if (List.class.isAssignableFrom(fieldClass)) {
                        @SuppressWarnings("rawtypes")
                        List l = (List) fieldValue;
                        for (int i = 0; i < l.size(); i++) {
                            process(prefix + "." + f.getName() + "[" + i + "]", l.get(i), aggregators, localContext);
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
                            process(setKey, mO, aggregators, localContext);
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
                            process(setKey, iO, aggregators, localContext);
                        }
                    } else {
                        process(prefix + "." + f.getName(), f.get(o), aggregators, localContext);
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

    static class Formula {
        String field;
        String formula;

        public Formula(String field, String formula) {
            this.field = field;
            this.formula = formula;
        }
    }
}