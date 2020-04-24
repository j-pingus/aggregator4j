package com.github.jpingus.model;

import java.util.ArrayList;
import java.util.List;

public class ProcessTrace {
    public ProcessTrace addContext(String classContext) {
        return add(new ContextTrace(classContext));
    }

    enum TraceType {ROOT, EXECUTE, COLLECT, VARIABLE, CONTEXT}

    TraceType type = TraceType.ROOT;
    public List<ProcessTrace> children;

    public static class ContextTrace extends ProcessTrace {
        public ContextTrace(String name) {
            this();
            this.name = name;
        }

        public ContextTrace() {
            this.type = TraceType.CONTEXT;
        }

        String name;
    }

    public static class CollectTrace extends ProcessTrace {
        String aggregator;
        String reference;

        public CollectTrace() {
            type = TraceType.COLLECT;
        }

        public CollectTrace(String aggregator, String reference) {
            this();
            this.aggregator = aggregator;
            this.reference = reference;
        }
    }

    public static class VariableTrace extends ProcessTrace {
        String variable;
        String value;

        public VariableTrace() {
            this.type = TraceType.VARIABLE;
        }

        public VariableTrace(String variable, String value) {
            this();
            this.variable = variable;
            this.value = value;
        }

    }

    public static class ExecuteTrace extends ProcessTrace {
        String field;
        String formula;

        public ExecuteTrace() {
            type = TraceType.EXECUTE;
        }

        public ExecuteTrace(String field, String formula) {
            this();
            this.field = field;
            this.formula = formula;
        }
    }

    public ProcessTrace traceVariable(String name, Object value) {
        return add(new VariableTrace(name, value == null ? "null" : value.toString()));
    }

    public ProcessTrace traceCollect(String aggregator, String reference) {
        return add(new CollectTrace(aggregator, reference));
    }

    public ProcessTrace traceExecute(String field, String formula) {
        return add(new ExecuteTrace(field, formula));
    }

    private ProcessTrace add(ProcessTrace trace) {
        if (children == null)
            children = new ArrayList<>();
        children.add(trace);
        return trace;
    }
}
