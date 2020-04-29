package com.github.jpingus.model;

public class Function {
    private String registerClass;
    private String namespace;

    public Function() {
    }

    public Function(String namespace, String registerClass) {
        this.namespace = namespace;
        this.registerClass = registerClass;
    }

    public String getRegisterClass() {
        return registerClass;
    }

    public void setRegisterClass(String registerClass) {
        this.registerClass = registerClass;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
