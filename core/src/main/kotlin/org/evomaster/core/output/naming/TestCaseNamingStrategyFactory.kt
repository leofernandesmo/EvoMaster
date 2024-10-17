package org.evomaster.core.output.naming

import org.evomaster.core.EMConfig
import org.evomaster.core.problem.graphql.GraphQLIndividual
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rpc.RPCIndividual
import org.evomaster.core.problem.webfrontend.WebIndividual
import org.evomaster.core.search.Solution
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestCaseNamingStrategyFactory(
    private val namingStrategy: NamingStrategy,
    private val languageConventionFormatter: LanguageConventionFormatter,
) {

    constructor(config: EMConfig): this(config.namingStrategy, LanguageConventionFormatter(config.outputFormat))

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TestCaseNamingStrategyFactory::class.java)
    }

    fun create(solution: Solution<*>): TestCaseNamingStrategy {
        return when {
            namingStrategy.isNumbered() -> NamingHelperNumberedTestCaseNamingStrategy(solution)
            namingStrategy.isAction() -> actionBasedNamingStrategy(solution)
            else -> throw IllegalStateException("Unrecognized naming strategy $namingStrategy")
        }
    }

    private fun actionBasedNamingStrategy(solution: Solution<*>): NumberedTestCaseNamingStrategy {
        val individuals = solution.individuals
        return when {
            individuals.any { it.individual is RestIndividual } -> return RestActionTestCaseNamingStrategy(solution, languageConventionFormatter,)
            individuals.any { it.individual is GraphQLIndividual } -> return GraphQLActionTestCaseNamingStrategy(solution, languageConventionFormatter)
            individuals.any { it.individual is RPCIndividual } -> return RPCActionTestCaseNamingStrategy(solution, languageConventionFormatter)
            individuals.any { it.individual is WebIndividual } -> {
                log.warn("Web individuals do not have action based test case naming yet. Defaulting to Numbered strategy.")
                return NamingHelperNumberedTestCaseNamingStrategy(solution)
            }
            else -> throw IllegalStateException("Unrecognized test individuals with no action based naming strategy set.")
        }
    }

}
