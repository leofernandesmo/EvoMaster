package org.evomaster.core.search.gene.sql

import org.evomaster.core.output.OutputFormat
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.GeneUtils
import org.evomaster.core.search.gene.ObjectGene
import org.evomaster.core.search.impact.impactinfocollection.sql.SqlJsonGeneImpact
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.service.mutator.MutationWeightControl
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneSelectionInfo
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneSelectionStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SqlJSONGene(name: String, val objectGene: ObjectGene = ObjectGene(name, fields = listOf())) : Gene(name) {

    companion object{
        private val log: Logger = LoggerFactory.getLogger(SqlJSONGene::class.java)
    }

    init {
        objectGene.parent = this
    }

    override fun copy(): Gene = SqlJSONGene(
            name,
            objectGene = this.objectGene.copy() as ObjectGene)


    override fun randomize(randomness: Randomness, forceNewValue: Boolean, allGenes: List<Gene>) {
        objectGene.randomize(randomness, forceNewValue, allGenes)
    }

    override fun candidatesInternalGenes(randomness: Randomness, apc: AdaptiveParameterControl, allGenes: List<Gene>, selectionStrategy: SubsetGeneSelectionStrategy, enableAdaptiveGeneMutation: Boolean, additionalGeneMutationInfo: AdditionalGeneSelectionInfo?): List<Gene> {
        return if (objectGene.isMutable()) listOf(objectGene) else emptyList()
    }

    override fun adaptiveSelectSubset(internalGenes: List<Gene>, mwc: MutationWeightControl, additionalGeneMutationInfo: AdditionalGeneSelectionInfo): List<Pair<Gene, AdditionalGeneSelectionInfo?>> {
        if (additionalGeneMutationInfo.impact != null && additionalGeneMutationInfo.impact is SqlJsonGeneImpact){
            if (internalGenes.size != 1 || !internalGenes.contains(objectGene))
                throw IllegalStateException("mismatched input: the internalGenes should only contain objectGene")
            return listOf(objectGene to additionalGeneMutationInfo.copyFoInnerGene(additionalGeneMutationInfo.impact.geneImpact))
        }
        throw IllegalArgumentException("impact is null or not SqlJsonGeneImpact")
    }

    override fun mutate(randomness: Randomness, apc: AdaptiveParameterControl, mwc: MutationWeightControl, allGenes: List<Gene>, selectionStrategy: SubsetGeneSelectionStrategy, enableAdaptiveGeneMutation: Boolean, additionalGeneMutationInfo: AdditionalGeneSelectionInfo?): Boolean {
        // do nothing since the objectGene is not mutable
        return true
    }

    override fun getValueAsPrintableString(previousGenes: List<Gene>, mode: GeneUtils.EscapeMode?, targetFormat: OutputFormat?): String {
        val rawValue = objectGene.getValueAsPrintableString(previousGenes, GeneUtils.EscapeMode.JSON, targetFormat)
        //val rawValue = objectGene.getValueAsRawString()
        when {
            // TODO: refactor with StringGene.getValueAsPrintableString(()
            (targetFormat == null) -> return "\"$rawValue\""
            targetFormat.isKotlin() -> return "\"$rawValue\""
                    .replace("\\", "\\\\")
                    .replace("$", "\\$")
            else -> return "\"$rawValue\""
                    .replace("\\", "\\\\")
        }
    }

    override fun copyValueFrom(other: Gene) {
        if (other !is SqlJSONGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        this.objectGene.copyValueFrom(other.objectGene)
    }

    /**
     * Genes might contain a value that is also stored
     * in another gene of the same type.
     */
    override fun containsSameValueAs(other: Gene): Boolean {
        if (other !is SqlJSONGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        return this.objectGene.containsSameValueAs(other.objectGene)
    }

    override fun flatView(excludePredicate: (Gene) -> Boolean): List<Gene> {
        return if (excludePredicate(this)) listOf(this) else
            listOf(this).plus(objectGene.flatView(excludePredicate))
    }

    override fun archiveMutationUpdate(original: Gene, mutated: Gene, targetsEvaluated: Map<Int, EvaluatedMutation>, archiveMutator: ArchiveGeneMutator) {
        if (original !is SqlJSONGene){
            log.warn("original ({}) should be SqlJSONGene", original::class.java.simpleName)
            return
        }
        if (mutated !is SqlJSONGene){
            log.warn("mutated ({}) should be SqlJSONGene", mutated::class.java.simpleName)
            return
        }
        objectGene.archiveMutationUpdate(original.objectGene, mutated.objectGene, targetsEvaluated, archiveMutator)
    }

    override fun reachOptimal(targets: Set<Int>): Boolean {
        return objectGene.reachOptimal(targets)
    }

    override fun mutationWeight(): Double {
        return objectGene.mutationWeight()
    }
}