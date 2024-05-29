package org.evomaster.core.problem.rest.securityrestoracle

import bar.examples.it.spring.multipleendpoints.MultipleEndpointsController
import bar.examples.it.spring.simplesecuritydeleteput.SimpleSecurityDeletePutController
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.rest.IntegrationTestRestBase
import org.evomaster.core.problem.rest.RestSecurityOracle
import org.evomaster.core.problem.rest.param.QueryParam
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.gene.string.StringGene
import org.junit.Assert
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeAll

class RestSecurityOracleTest : IntegrationTestRestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            initClass(SimpleSecurityDeletePutController())
        }
    }

    @Test
    fun testHandleForbiddenDelete() {

        /*
            TODO

            - create test cases manually with Pir
            - they ll not be SampleType.SECURITY, so oracle not computed
            - call RestSecurityOracle directly on such created invididual
            - verify properties
         */

        val pirTest = getPirToRest()

        val action1Ind1 = pirTest.fromVerbPath("GET", "/api/endpoint2/1000")!!
        val action2Ind1 = pirTest.fromVerbPath("POST", "/api/endpoint1")!!
        val action3Ind1 = pirTest.fromVerbPath("PUT", "/api/endpoint3/2000")!!
        val action4Ind1 = pirTest.fromVerbPath("DELETE", "/api/endpoint4/1005")!!
        val action5Ind1 = pirTest.fromVerbPath("GET", "/api/endpoint5/setStatus/402")!!

        val g1 = StringGene("sampleString")
        g1.markAllAsInitialized()

        action1Ind1.addParam(QueryParam("param1", g1))

        val sampleInd = createIndividual(listOf(action1Ind1, action2Ind1, action3Ind1, action4Ind1, action5Ind1), SampleType.SECURITY)

        val testCovered = RestSecurityOracle.handleForbiddenDelete(sampleInd.individual, sampleInd.seeResults(), FitnessValue(0.0) )

        Assert.assertNull(testCovered)


    }
}