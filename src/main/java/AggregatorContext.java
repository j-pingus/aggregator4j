import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext.NamespaceFunctor, JexlContext {
    JexlEngine jexl = new JexlBuilder().create();
    Map<String, Object> registeredNamespaces;
    JexlContext localContext;
    Map<String, Aggregator> aggregators;

    public AggregatorContext() {
        this.localContext = new MapContext();
        this.aggregators = new HashMap<>();
        this.registeredNamespaces = new HashMap<>();
        register("aggregator",this);
    }

    public void register(String namespace, Object o) {
        registeredNamespaces.put(namespace, o);
    }

    public Object evaluate(String expression) {
        return jexl.createExpression(expression).evaluate(this);
    }

    public Object join(String s, String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            String formula = aggregators.get(aggregator).concatenate("+'" + s + "'+");
            Object ret = evaluate(formula);
            System.out.println("join of " + aggregator + ":" + formula + "=" + ret);
            return ret;
        } else {
            System.out.println("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;
    }

    public Double divide(Integer div, Integer div2) {
        System.out.println("divide");
        return div.doubleValue() / div2.doubleValue();
    }

    public Integer count(String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            int ret = aggregators.get(aggregator).count();
            System.out.println("count of " + aggregator + "=" + ret);
            return ret;
        } else {
            System.out.println("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;
    }

    public Object sum(String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            String formula = aggregators.get(aggregator).concatenate("+");
            Object ret = evaluate(formula);
            System.out.println("sum of " + aggregator + ":" + formula + "=" + ret);
            return ret;
        } else {
            System.out.println("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;
    }

    public Object avg(String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            Aggregator a = aggregators.get(aggregator);
            String formula = "(" + a.concatenate("+") + ")/" + a.count() + ".0";
            Object ret = evaluate(formula);
            System.out.println("avg of " + aggregator + ":" + formula + "=" + ret);
            return ret;
        } else {
            System.out.println("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;
    }

    protected void addFormula(String key, String formula) {
        if (!aggregators.containsKey(key))
            aggregators.put(key, new Aggregator());
        aggregators.get(key).append(formula);
    }

    @Override
    public Object resolveNamespace(String s) {
        Object ret = registeredNamespaces.get(s);
        return ret==null?this:ret;
    }

    @Override
    public Object get(String s) {
        return localContext.get(s);
    }

    @Override
    public void set(String s, Object o) {
        localContext.set(s, o);
    }

    @Override
    public boolean has(String s) {
        return localContext.has(s);
    }

    @Override
    public Object createFunctor(JexlContext jexlContext) {
        return this;
    }

    static class Aggregator {
        List<String> formulas;

        public Aggregator() {
            this.formulas = new ArrayList<>();
        }

        public void append(String formula) {
            this.formulas.add(formula);
        }

        public int count() {
            return formulas.size();
        }

        public String concatenate(String s) {
            return String.join(s, formulas);
        }
    }
}
