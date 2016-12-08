package org.evomaster.clientJava.instrumentation.staticState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Methods of this class will be injected in the SUT to
 * keep track of what the tests do execute/cover.
 */
public class ExecutionTracer {

    /*
        Careful if you change the signature of any of the
        methods in this class, as they are injected in the
        bytecode instrumentation.
        Fortunately, unit tests should quickly find such
        type of issues.
     */

    /**
     * Identifier for line coverage objectives
     */
    public static final String LINE = "Line";

    /**
     * Key -> the unique id of the coverage objective
     * <br>
     * Value -> heuristic [0,1], where 1 means covered
     */
    private static final Map<String, Double> objectiveCoverage =
            new ConcurrentHashMap<>(65536);


    public static void resetState() {
        objectiveCoverage.clear();
    }

    public static Map<String, Double> getInternalReferenceToObjectiveCoverage() {
        return objectiveCoverage;
    }

    /**
     * @return the number of objectives that have been encountered
     * during the test execution
     */
    public static int getNumberOfObjectives() {
        return objectiveCoverage.size();
    }

    /**
     * Note: only the objectives encountered so far can have
     * been recorded. So, this is a relative value, not based
     * on the code of the whole SUT (just the parts executed so far).
     * Therefore, it is quite useless for binary values (ie 0 or 1),
     * like current implementation of basic line coverage.
     *
     * @param prefix used for string matching of which objectives types
     *               to consider, eg only lines or only branches.
     *               Use "" or {@code null} to pick up everything
     * @return
     */
    public static int getNumberOfNonCoveredObjectives(String prefix) {

        return (int) objectiveCoverage
                .entrySet().stream()
                .filter(e -> prefix == null || e.getKey().startsWith(prefix))
                .filter(e -> e.getValue() < 1)
                .count();
    }


    private static void updateObjective(String id, double value) {
        if (value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid value " + value + " out of range [0,1]");
        }
        objectiveCoverage.put(id, value);
        ObjectiveRecorder.update(id, value);
    }


    public static final String EXECUTED_LINE_METHOD_NAME = "executedLine";
    public static final String EXECUTED_LINE_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/String;I)V";

    /**
     * Report on the fact that a given line has been executed.
     */
    public static void executedLine(String className, String fullMethodName, int line) {

        String id = LINE + "_" + line + "_at_" + className + "::" + fullMethodName;
        updateObjective(id, 1d);
    }
}
