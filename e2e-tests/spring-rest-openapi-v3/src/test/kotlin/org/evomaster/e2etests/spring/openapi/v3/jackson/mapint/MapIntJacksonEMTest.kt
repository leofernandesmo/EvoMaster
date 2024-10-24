package org.evomaster.e2etests.spring.openapi.v3.jackson.mapint

import com.foo.rest.examples.spring.openapi.v3.jackson.mapint.MapIntJacksonController
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MapIntJacksonEMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(MapIntJacksonController())
        }
    }

    @Test
    fun basicEMTest() {
        runTestHandlingFlakyAndCompilation(
            "MapIntJacksonEM",
            500,
            500
        ) { args: List<String> ->

            setOption(args, "taintForceSelectionOfGenesWithSpecialization", "true")
            setOption(args, "discoveredInfoRewardedInFitness", "true")
            val solution = initAndRun(args)

            Assertions.assertTrue(solution.individuals.size >= 1)
            assertHasAtLeastOne(solution, HttpVerb.POST, 200, "/api/jackson/mapint", "Working")
        }
    }
}