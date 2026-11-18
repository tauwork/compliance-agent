package com.pcmvp.rule.model;

/** 规则命中结果（由Drools规则RHS插入） */
public class RuleHit {

    private final String code;
    private final String name;
    private final String severity;
    private final int factIndex;

    public RuleHit(String code, String name, String severity, int factIndex) {
        this.code = code;
        this.name = name;
        this.severity = severity;
        this.factIndex = factIndex;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSeverity() { return severity; }
    public int getFactIndex() { return factIndex; }

    @Override
    public String toString() {
        return "RuleHit{" + code + " " + name + " severity=" + severity + " fact=" + factIndex + "}";
    }
}
