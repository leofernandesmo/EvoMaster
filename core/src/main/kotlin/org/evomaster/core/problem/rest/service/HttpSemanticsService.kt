package org.evomaster.core.problem.rest.service

import com.google.inject.Inject
import org.evomaster.core.problem.rest.*
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Solution
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.IdMapper
import org.evomaster.core.search.service.Randomness
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct

/**
 * Service class used to verify HTTP semantics properties.
 */
class HttpSemanticsService {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HttpSemanticsService::class.java)
    }

    /**
     * Archive including test cases
     */
    @Inject
    private lateinit var archive: Archive<RestIndividual>

    @Inject
    private lateinit var sampler: AbstractRestSampler

    @Inject
    private lateinit var randomness: Randomness

    @Inject
    private lateinit var fitness: RestFitness

    @Inject
    private lateinit var idMapper: IdMapper

    /**
     * All actions that can be defined from the OpenAPI schema
     */
    private lateinit var actionDefinitions: List<RestCallAction>

    /**
     * Individuals in the solution.
     * Derived from archive.
     */
    private lateinit var individualsInSolution: List<EvaluatedIndividual<RestIndividual>>

    //TODO quite a few code here seem duplicate for SecurityRest... to consider common abstract class

    @PostConstruct
    private fun postInit() {

        actionDefinitions = sampler.getActionDefinitions() as List<RestCallAction>

    }



    fun applyHttpSemanticsPhase(): Solution<RestIndividual>{

        individualsInSolution = this.archive.extractSolution().individuals

        addForHttpSemantics()

        return archive.extractSolution()
    }

    private fun addForHttpSemantics() {

//        – invalid location, leading to a 404 when doing a follow up GET
//        – PUT with different status from 2xx should have no side-effects. Can be verified with before and after GET. PATCH can be tricky
//        – PUT for X, and then GET on it, should return exactly X (eg, check no partial updates)
//        – PUT if creating, must get 201. That means a previous GET must return 404 (or at least not a 2xx) .
//        –  A repeated followup PUT with 201 on same endpoint should not return 201 (must enforce 200 or 204)
//        – JSON-Merge-Patch: partial update should not impact other fields. Can have GET, PATCH, and GET to verify it


        // – 2xx GET on K : follow by success 2xx DELETE, should then give 404 on GET k (adding up to 2 calls)
        deleteShouldDelete()
    }


    /**
     * Checking bugs like:
     * GET    /X 2xx
     * DELETE /X 2xx
     * GET    /X 2xx
     */
    private fun deleteShouldDelete() {

        val deleteOperations = RestIndividualSelectorUtils.getAllActionDefinitions(actionDefinitions, HttpVerb.DELETE)

        deleteOperations.forEach { del ->

            val successDelete = RestIndividualSelectorUtils.findIndividuals(
                individualsInSolution,
                HttpVerb.DELETE,
                del.path,
                statusGroup = StatusGroup.G_2xx
            )
            if(successDelete.isEmpty()){
                return@forEach
            }

            val suc = successDelete.minBy { it.individual.size() }
            val index = RestIndividualSelectorUtils.findIndexOfAction(
                suc,
                HttpVerb.DELETE,
                del.path,
                statusGroup = StatusGroup.G_2xx
                )
            val okDelete = RestIndividualBuilder.sliceAllCallsInIndividualAfterAction(suc.individual, index)

            val actions = okDelete.seeMainExecutableActions()

            val last = actions[actions.size - 1]

            //does it have a previous GET call on it in previous action?
            val hasPreviousGet = okDelete.size() > 1
                    && actions[actions.size - 2].let {
                        it.verb == HttpVerb.GET && it.path == del.path && it.usingSameResolvedPath(last)
            }

            if(!hasPreviousGet){
                TODO
            }
        }
    }

}