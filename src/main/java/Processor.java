import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Processor {
    public static void process(Object o) throws Exception {
        Map<String, Formula> aggregators = new HashMap<String, Formula>();
        process("o",o,aggregators);
        JexlContext localContext = new MapContext();
        localContext.set("o",o);
        JexlEngine jexl = new JexlBuilder().create();
        for(Formula f:aggregators.values()){
            jexl.createExpression(f.field+"="+f.formula).evaluate(localContext);
        }
    }

    private static void process(String prefix, Object o, Map<String, Formula> aggregators) throws Exception {
        for (Field f : o.getClass().getDeclaredFields()) {
            Aggregator a = f.getDeclaredAnnotation(Aggregator.class);
            Sum sums[] = f.getDeclaredAnnotationsByType(Sum.class);
            if (a != null) {
                if (!aggregators.containsKey(a.value())) {
                    aggregators.put(a.value(), new Formula(null, null));
                }
                Formula formula = aggregators.get(a.value());
                formula.field = prefix + "." + f.getName();
            } else if (sums != null && sums.length > 0) {
                for (Sum sum : sums) {
                    Formula formula = null;
                    if (!aggregators.containsKey(sum.value())) {
                        aggregators.put(sum.value(), new Formula(null, null));
                    }
                    formula = aggregators.get(sum.value());
                    String add = prefix + "." + f.getName();
                    if (formula.formula == null) {
                        formula.formula = add;
                    } else {
                        formula.formula += "+" + add;
                    }
                }
            } else {
                Object fieldValue = f.get(o);
                if (f.getType().isArray()) {
                    int length = Array.getLength(fieldValue);
                    for (int i = 0; i < length; i++) {
                        process(prefix + "." + f.getName() + "[" + i + "]", Array.get(fieldValue, i),aggregators);
                    }
                }
            }
        }
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
