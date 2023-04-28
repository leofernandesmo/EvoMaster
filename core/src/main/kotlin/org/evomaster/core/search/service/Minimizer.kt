package org.evomaster.core.search.service

import com.google.inject.Inject
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.search.Individual

/**
 * Reduce/simplify the final test outputs
 */
class Minimizer<T: Individual> {

    @Inject
    private lateinit var archive: Archive<T>

    /**
     * Based on the tests in the archive, update the archive by having, for each target T,
     * the minimum number of actions needed to cover it.
     *
     * Using the same/similar kind of algorithm as explained in:
     *
     * "EvoSuite: On The Challenges of Test Case Generation in the Real World"
     */
    fun minimizeActionsPerCoveredTargetInArchive(){

        val current = archive.getCopyOfUniqueCoveringIndividuals()

        LoggingUtil.getInfoLogger().info("Starting to apply minimization phase on ${current.size} tests")

        val beforeCovered = archive.numberOfCoveredTargets()




        //TODO



    }
}