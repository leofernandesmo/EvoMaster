package org.evomaster.e2etests.spring.examples.formparam;

import com.foo.rest.examples.spring.bodytypes.BodyTypesController;
import com.foo.rest.examples.spring.formparam.FormParamController;
import org.evomaster.core.problem.rest.HttpVerb;
import org.evomaster.core.problem.rest.RestIndividual;
import org.evomaster.core.search.Solution;
import org.evomaster.e2etests.spring.examples.SpringTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FormParamEMTest extends SpringTestBase {

    @BeforeAll
    public static void initClass() throws Exception {

        FormParamController controller = new FormParamController();
        SpringTestBase.initClass(controller);
    }


    @Test
    public void testRunEM() throws Throwable {

        runTestHandlingFlakyAndCompilation(
                "FormParamEM",
                "org.FormParamEM",
                100,
                (args) -> {
                    // Disable test suite splitting
                    args.add("--testSuiteSplitType");
                    args.add("NONE");

                    Solution<RestIndividual> solution = initAndRun(args);

                    assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/api/formparam", "OK");
                }
        );
    }
}