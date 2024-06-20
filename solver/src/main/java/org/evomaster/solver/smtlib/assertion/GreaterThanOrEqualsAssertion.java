package org.evomaster.solver.smtlib.assertion;

public class GreaterThanOrEqualsAssertion extends Assertion {
    private final String var1;
    private final String var2;

    public GreaterThanOrEqualsAssertion(String var1, String var2) {
        this.var1 = var1;
        this.var2 = var2;
    }

    @Override
    public String toString() {
        return "(>= " + var1 + " " + var2 + ")";
    }
}