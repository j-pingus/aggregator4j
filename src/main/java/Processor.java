import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
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
		return process(o, o.getClass().getSimpleName(), new AggregatorContext(true));
	}

	/**
	 * same as process. Use this method if you want to register custom methods in a
	 * namespace to your context.
	 *
	 * @param o
	 * @param aggregatorContext
	 * @return
	 */
	public static AggregatorContext process(Object o, String prefix, AggregatorContext aggregatorContext) {
			aggregatorContext.startProcess();
			try {
				List<ExecuteContext> executors = new ArrayList<>();
				try {
					aggregatorContext.set(prefix, o);
					process(prefix, o, aggregatorContext, executors);
					for (ExecuteContext executeContext : executors) {
						if (executeContext.executed == false) {
							aggregatorContext.execute(executeContext.field, executeContext.formula);
						}
					}
				} catch (IllegalAccessException e) {
					LOGGER.error("Could not process object " + o, e);
				}
				return aggregatorContext;
			} finally {
				aggregatorContext.endProcess();
			}
	}

	private static void process(String prefix, Object o, AggregatorContext localContext,
			List<ExecuteContext> executeContexts) throws IllegalAccessException {
		if (o == null)
			return;
		// Check if the object is a collection
		@SuppressWarnings("rawtypes")
		Class objectClass = o.getClass();
		@SuppressWarnings("unchecked")
		Context context = (Context) objectClass.getDeclaredAnnotation(Context.class);
		try {
			if (context != null) {
				localContext.startContext(context.value());
				for (ExecuteContext executeContext : executeContexts) {
					if (executeContext.formula.contains(context.value() + ".") && executeContext.executed == false) {
						localContext.execute(executeContext.field, executeContext.formula);
						executeContext.executed = true;
					}
				}
				localContext.cleanContext(context.value());
			}
			@SuppressWarnings("unchecked")
			Collect collectsClass[] = (Collect[]) objectClass.getDeclaredAnnotationsByType(Collect.class);
			if (collectsClass != null && collectsClass.length > 0) {
				for (Collect collect : collectsClass) {
					if (applicable(o, collect.when(), localContext)) {
						localContext.collect(evaluate(o, collect.value(), localContext),
								collect.what().replaceAll("this\\.", prefix + "."));
					}
				}
			}
			if (objectClass.isArray()) {
				int length = Array.getLength(o);
				for (int i = 0; i < length; i++) {
					process(prefix + "[" + i + "]", Array.get(o, i), localContext, executeContexts);
				}
			} else if (List.class.isAssignableFrom(objectClass)) {
				@SuppressWarnings("rawtypes")
				List l = (List) o;
				for (int i = 0; i < l.size(); i++) {
					process(prefix + "[" + i + "]", l.get(i), localContext, executeContexts);
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
					process(setKey, mO, localContext, executeContexts);
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
					process(setKey, iO, localContext, executeContexts);
				}
			} else {
				if (o.getClass().isPrimitive())
					return;
				if (o.getClass().getPackage() != null
						&& !o.getClass().getPackage().getName().startsWith("eu.europa.ec"))
					return;
				// Check the fields they can be Collectors, Executors or simple fields to
				// process
				for (Field f : getFields(o)) {
					Execute executors[] = f.getDeclaredAnnotationsByType(Execute.class);
					Collect collects[] = f.getDeclaredAnnotationsByType(Collect.class);
					Variable variable = f.getDeclaredAnnotation(Variable.class);
					if (executors != null && collects != null && executors.length > 0 && collects.length > 0) {
						throw new Error("Field " + f + " cannot be @Collect and @Execute at the same time");
					}
					if (variable != null) {
						localContext.addVariable(variable.value(), get(o, f.getName(), localContext));

					}
					if (executors != null && executors.length > 0) {
						for (Execute execute : executors) {
							if (applicable(o, execute.when(), localContext)) {
								executeContexts.add(new ExecuteContext(prefix, f.getName(), execute.value()));
							}
						}
					} else if (collects != null && collects.length > 0) {
						if (!isNull(o, f.getName(), localContext)) {
							String add = prefix + "." + f.getName();
							for (Collect collect : collects) {
								if (applicable(o, collect.when(), localContext)) {
									localContext.collect(evaluate(o, collect.value(), localContext), add);
								}
							}
						}
					} else {
						process(prefix + "." + f.getName(), get(o, f.getName(), localContext), localContext,
								executeContexts);
					}
				}
			}
		} finally {
			if (context != null)
				localContext.endContext(context.value());
		}
	}

	private static List<Field> getFields(Object o) {
		ArrayList<Field> ret = new ArrayList<>();
		@SuppressWarnings("rawtypes")
		Class baseClass = o.getClass();
		while (baseClass != null && baseClass != Object.class) {
			Collections.addAll(ret, baseClass.getDeclaredFields());
			baseClass = baseClass.getSuperclass();
		}
		return ret;
	}

	private static Object get(Object o, String fieldName, AggregatorContext localContext) {
		if (fieldName == null || "".equals(fieldName) || fieldName.contains("$"))
			return null;
		localContext.set("this", o);
		if (localContext.isDebug())
			LOGGER.debug("Get " + o.getClass() + " " + fieldName);
		return localContext.evaluate("this." + fieldName);

	}

	private static String evaluate(Object o, String value, AggregatorContext localContext) {
		if (value == null || "".equals(value))
			return null;
		if (!value.startsWith("eval:")) {
			return value;
		}
		localContext.set("this", o);
		return localContext.evaluate(value.substring(5)).toString();
	}

	private static boolean isNull(Object o, String fieldName, AggregatorContext localContext)
			throws IllegalAccessException {
		return get(o, fieldName, localContext) == null;
	}

	private static boolean applicable(Object o, String when, AggregatorContext localContext) {
		if (when == null || "".equals(when))
			return true;
		localContext.set("this", o);
		Boolean ret = new Boolean(localContext.evaluate(when).toString());
		if(localContext.isDebug())
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