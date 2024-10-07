package org.evomaster.e2etests.spring.openapi.v3.schemavalidation

import com.foo.rest.examples.spring.openapi.v3.schemavalidation.SchemaValidationApplication
import com.foo.rest.examples.spring.openapi.v3.schemavalidation.SchemaValidationController
import com.webfuzzing.commons.faults.FaultCategory
import org.evomaster.core.problem.enterprise.DetectedFaultUtils
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.evomaster.notinstrumented.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SchemaValidationEMTest : SpringTestBase(){

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(SchemaValidationController())
        }
    }


    @Test
    fun testRunEM_On_Invalid() {

        runTestHandlingFlakyAndCompilation(
                "SchemaValidationOnInvalidEM",
                10
        ) { args: MutableList<String> ->

            Constants.statusCodeToReturn = 204

            setOption(args, "schemaOracles", "true")

            val solution = initAndRun(args)

            assertTrue(solution.individuals.size >= 1)

            assertHasAtLeastOne(solution, HttpVerb.GET, 204, "/api/schemavalidation", null)

            val faults = DetectedFaultUtils.getDetectedFaultCategories(solution)
            assertEquals(1, faults.size)
            assertEquals(FaultCategory.SCHEMA_INVALID_RESPONSE, faults.first())
        }
    }


    @Test
    fun testRunEM_Off_Invalid() {

        runTestHandlingFlakyAndCompilation(
            "SchemaValidationOffInvalidEM",
            10
        ) { args: MutableList<String> ->

            Constants.statusCodeToReturn = 204

            setOption(args, "schemaOracles", "false")

            val solution = initAndRun(args)

            assertTrue(solution.individuals.size >= 1)

            assertHasAtLeastOne(solution, HttpVerb.GET, 204, "/api/schemavalidation", null)

            val faults = DetectedFaultUtils.getDetectedFaultCategories(solution)
            assertEquals(0, faults.size)
        }
    }


    @Test
    fun testRunEM_On_Valid() {

        runTestHandlingFlakyAndCompilation(
            "SchemaValidationOnValidEM",
            10
        ) { args: MutableList<String> ->

            Constants.statusCodeToReturn = 200

            setOption(args, "schemaOracles", "true")

            val solution = initAndRun(args)

            assertTrue(solution.individuals.size >= 1)

            assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/schemavalidation", null)

            val faults = DetectedFaultUtils.getDetectedFaults(solution)
            assertEquals(0, faults.size, "Faults: ${faults.joinToString(" | ")}")
        }
    }

}