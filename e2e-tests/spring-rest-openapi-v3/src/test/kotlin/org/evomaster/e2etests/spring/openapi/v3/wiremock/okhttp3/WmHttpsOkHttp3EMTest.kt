package org.evomaster.e2etests.spring.openapi.v3.wiremock.okhttp3

import com.foo.rest.examples.spring.openapi.v3.wiremock.okhttp3.WmOkHttp3Controller
import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.e2etests.spring.openapi.v3.SpringTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


class WmHttpsOkHttp3EMTest : SpringTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {

            val config = EMConfig()
            config.instrumentMR_NET = true
            initClass(WmOkHttp3Controller(listOf("/api/wm/socketconnect/string","/api/wm/socketconnect/object")), config)
        }
    }


    @Test
    fun testRunEM() {

        runTestHandlingFlakyAndCompilation(
            "WmHttpsOkHttp3EM",
            "org.foo.WmHttpsOkHttp3EM",
            100,
            true,
            { args: MutableList<String> ->

                args.add("--externalServiceIPSelectionStrategy")
                args.add("USER")
                args.add("--externalServiceIP")
                args.add("127.0.0.82")

                val solution = initAndRun(args)

                assertTrue(solution.individuals.size >= 1)
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/sstring", "OK")
                assertHasAtLeastOne(solution, HttpVerb.GET, 200, "/api/wm/socketconnect/sstring", "Hello There")
                assertHasAtLeastOne(solution, HttpVerb.GET, 400, "/api/wm/socketconnect/sstring", null)
                assertHasAtLeastOne(solution, HttpVerb.GET, 500, "/api/wm/socketconnect/sstring", null)
            },
            3
        )
    }

}