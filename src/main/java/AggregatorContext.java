import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class AggregatorContext implements JexlContext.NamespaceResolver, JexlContext {
    private static final Log LOGGER = LogFactory.getLog(AggregatorContext.class);
    private JexlEngine jexl = new JexlBuilder().create();
    private Map<String, Object> registeredNamespaces;
    private JexlContext localContext;
    private Map<String, Aggregator> aggregators;

    /**
     * Constructor :-)
     */
    public AggregatorContext() {
        this.localContext = new MapContext();
        this.aggregators = new HashMap<>();
        this.registeredNamespaces = new HashMap<>();
    }

    /**
     * Register an object (or a class) to a namespace.
     * Future call to the evaluator function will benefit from all public methods as functions in that namespace
     *
     * @param namespace
     * @param o
     */
    public void register(String namespace, Object o) {
        registeredNamespaces.put(namespace, o);
    }

    /**
     * Evaluate the expression against the context, additionally to the JEXL syntax you can use
     * - sum
     * - avg
     * - count
     * - join
     * see methods with same name in this class for more information
     *
     * @param expression
     * @return
     */
    public Object evaluate(String expression) {
        return jexl.createExpression(expression).evaluate(this);
    }

    /**
     * Joins all objects that have been collected in an aggregator into a string separated by separator
     *
     * @param separator
     * @param aggregator
     * @return
     */
    public Object join(String separator, String aggregator) {
        return aggregate(aggregator, "join", a -> a.concatenate("+'" + separator + "'+") );
    }

   /**
     * Count how many objects have been collected in an aggregator
     *
     * @param aggregator
     * @return
     */
    public Integer count(String aggregator) {
        if (aggregators.containsKey(aggregator)) {
            int ret = aggregators.get(aggregator).count();
            LOGGER.debug("count of " + aggregator + "=" + ret);
            return ret;
        } else {
            LOGGER.warn("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;
    }

    /**
     * Sum all objects collected in an aggregator (may be problematic if JEXL cannot sum those objects with "+" operand)
     *
     * @param aggregator
     * @return
     */
    public Object sum(String aggregator) {
        return aggregate(aggregator, "sum", a -> a.concatenate("+") );
    }

    /**
     * does sum/count for an aggregator, result is floating point (usually double)
     *
     * @param aggregator
     * @return
     */
    public Object avg(String aggregator) {
        return aggregate(aggregator, "avg", a -> "(" + a.concatenate("+") + ")/" + a.count() + ".0");
    }
    /**
     * return aggregated values As Array
     */
    public Object[] asArray(String aggregator){
        return (Object[])aggregate(aggregator, "asArray", a -> "[" + a.concatenate(",") + "]");
    }
    /**
     * return aggregated values as Set
     */
    public Set<Object> asSet(String aggregator){
    	return (Set<Object>)aggregate(aggregator, "asSet", a -> "{" + a.concatenate(",") + "}");
    }
    /**
     * all aggregators in this context
     * @return a list of aggregator names
     */
    public Set<String> aggregators(){
        return Collections.unmodifiableSet(aggregators.keySet());
    }
    public Object aggregate(String aggregator, String name, AggregatorJEXLBuilder expressionBuilder) {
        if (aggregators.containsKey(aggregator)) {
            Aggregator a = aggregators.get(aggregator);
            String expression = expressionBuilder.buildExpression(a);
            Object ret = evaluate(expression);
            LOGGER.debug(name + " of " + aggregator + ":" + expression + "=" + ret);
            return ret;
        } else {
            LOGGER.warn("Could not find aggregator with name '" + aggregator + "'");
        }
        return null;

    }

    /**
     * Used by processor to collect object references
     *
     * @param aggregator
     * @param objectReference
     */
    protected void addFormula(String aggregator, String objectReference) {
        if (!aggregators.containsKey(aggregator))
            aggregators.put(aggregator, new Aggregator());
        aggregators.get(aggregator).append(objectReference);
    }

    @Override
    public Object resolveNamespace(String s) {
        Object ret = registeredNamespaces.get(s);
        return ret == null ? this : ret;
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

    public interface AggregatorJEXLBuilder {
        String buildExpression(Aggregator a);
    }

    /**
     * Inner structure of the aggregator, subject to change...
     */
    private static class Aggregator {
        List<String> formulas;

        private Aggregator() {
            this.formulas = new ArrayList<>();
        }

        private void append(String formula) {
            this.formulas.add(formula);
        }

        private int count() {
            return formulas.size();
        }

        private String concatenate(String s) {
            return String.join(s, formulas);
        }

		public void clear() {
			formulas.clear();
			
		}
    }

	public void cleanContext(String value) {
		for(String key:aggregators.keySet()) {
			if(key.startsWith(value+".")) {
				aggregators.get(key).clear();
			}
		}
	}
}
