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
	private static final Log LOGGER = LogFactory.getLog(Processor.class);

	/**
	 * Analyse recursively an object, collecting all the @Collects to aggregators,
	 * then executes the @Executes Nulls will not be collected You cannot assume
	 * wich order will be executed for the @Executes
	 *
	 * @param o
	 * @return an Aggregator context to be used for programmatically accessing the
	 *         aggregators
	 */
	public static AggregatorContext process(Object o) {
		return process(o, new AggregatorContext());
	}

	/**
	 * same as process. Use this method if you want to register custom methods in a
	 * namespace to your context.
	 *
	 * @param o
	 * @param aggregatorContext
	 * @return
	 */
	public static AggregatorContext process(Object o, AggregatorContext aggregatorContext) {
		List<ExecuteContext> executors = new ArrayList<>();
		try {
			aggregatorContext.set("o", o);
			process("o", o, aggregatorContext, executors, aggregatorContext);
			for (ExecuteContext executeContext : executors) {
				if (executeContext.executed == false) {
					String expression = executeContext.field + "=" + executeContext.formula;
					LOGGER.debug("Evaluating :" + expression);
					aggregatorContext.evaluate(expression);
				}
			}
		} catch (IllegalAccessException e) {
			LOGGER.error("Could not process object " + o, e);
		}
		return aggregatorContext;
	}

	private static void process(String prefix, Object o, AggregatorContext aggregatorContext,
			List<ExecuteContext> executeContexts, JexlContext localContext) throws IllegalAccessException {
		if (o == null)
			return;
		// Check if the object is a collection
		@SuppressWarnings("rawtypes")
		Class objectClass = o.getClass();
		@SuppressWarnings("unchecked")
		Context context = (Context) objectClass.getDeclaredAnnotation(Context.class);
		if (context != null) {
			for (ExecuteContext executeContext : executeContexts) {
				if (executeContext.formula.contains(context.value() + ".") && executeContext.executed == false) {
					String expression = executeContext.field + "=" + executeContext.formula;
					LOGGER.debug("Evaluating :" + expression);
					aggregatorContext.evaluate(expression);
					executeContext.executed = true;
				}
			}
			aggregatorContext.cleanContext(context.value());
		}
		if (objectClass.isArray()) {
			int length = Array.getLength(o);
			for (int i = 0; i < length; i++) {
				process(prefix + "[" + i + "]", Array.get(o, i), aggregatorContext, executeContexts, localContext);
			}
		} else if (List.class.isAssignableFrom(objectClass)) {
			@SuppressWarnings("rawtypes")
			List l = (List) o;
			for (int i = 0; i < l.size(); i++) {
				process(prefix + "[" + i + "]", l.get(i), aggregatorContext, executeContexts, localContext);
			}
			// Not working ... why?
			// } else if (Map.class.isAssignableFrom(fieldClass)) {
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
			if (o.getClass().isPrimitive())
				return;
			if (o.getClass().getPackage() != null && !o.getClass().getPackage().getName()
					.startsWith("eu.europa.ec.eac.eforms.online.data.form.model"))
				return;
			// Check the fields they can be Collectors, Executors or simple fields to
			// process
			for (Field f : o.getClass().getDeclaredFields()) {
				Execute executors[] = f.getDeclaredAnnotationsByType(Execute.class);
				Collect collects[] = f.getDeclaredAnnotationsByType(Collect.class);
				if (executors != null && collects != null && executors.length > 0 && collects.length > 0) {
					throw new Error("Field " + f + " cannot be @Collect and @Execute at the same time");
				}
				if (executors != null && executors.length > 0) {
					for (Execute execute : executors) {
						if (applicable(o, execute.when())) {
							executeContexts.add(new ExecuteContext(prefix, f.getName(), execute.value()));
						}
					}
				} else if (collects != null && collects.length > 0) {
					if (!isNull(o, f.getName())) {
						String add = prefix + "." + f.getName();
						for (Collect collect : collects) {
							if (applicable(o, collect.when())) {
								aggregatorContext.addFormula(evaluate(o, collect.value()), add);
							}
						}
					}
				} else {
					process(prefix + "." + f.getName(), get(o, f.getName()), aggregatorContext, executeContexts,
							localContext);
				}
			}
		}
	}

	private static Object get(Object o, String fieldName) {
		if (fieldName == null || "".equals(fieldName) || fieldName.contains("$"))
			return null;
		JexlContext localContext = new MapContext();
		localContext.set("this", o);
		JexlEngine jexl = new JexlBuilder().create();
		LOGGER.debug("Get " + o.getClass() + " " + fieldName);
		return jexl.createExpression("this." + fieldName).evaluate(localContext);

	}

	private static String evaluate(Object o, String value) {
		if (value == null || "".equals(value))
			return null;
		if (!value.startsWith("eval:")) {
			return value;
		}
		JexlContext localContext = new MapContext();
		localContext.set("this", o);
		JexlEngine jexl = new JexlBuilder().create();
		return jexl.createExpression(value.substring(5)).evaluate(localContext).toString();
	}

	private static boolean isNull(Object o, String fieldName) throws IllegalAccessException {
		return get(o, fieldName) == null;
	}

	private static boolean applicable(Object o, String when) {
		if (when == null || "".equals(when))
			return true;
		JexlContext localContext = new MapContext();
		localContext.set("this", o);
		JexlEngine jexl = new JexlBuilder().create();
		Boolean ret = new Boolean(jexl.createExpression(when).evaluate(localContext).toString());
		LOGGER.debug("applicable :" + when + " is " + ret);
		return ret;
	}

	private static class ExecuteContext {
		String field;
		String formula;
		boolean executed = false;

		public ExecuteContext(String parent, String field, String formula) {
			this.field = parent + "." + field;
			this.formula = formula.replaceAll("this\\.", parent + ".");
		}
	}
}